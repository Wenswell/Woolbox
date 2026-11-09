import { constants as fsConstants } from "node:fs";
import { access, copyFile, mkdtemp, readFile, readdir, stat } from "node:fs/promises";
import { homedir, tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import { spawn } from "node:child_process";

const projectRoot = resolve(import.meta.dirname, "..");
const repository = "Wenswell/Woolbox";
const downloadBaseUrl = "https://github.com/Wenswell/Woolbox/releases/download";
const androidHome = join(homedir(), "Library", "Android", "sdk");
const javaHome = "/Applications/Android Studio.app/Contents/jbr/Contents/Home";
const androidBuildEnvironment = {
  ...process.env,
  ANDROID_HOME: androidHome,
  JAVA_HOME: javaHome,
};
const scriptArguments = process.argv.slice(2);
if (scriptArguments[0] === "--") scriptArguments.shift();
const argumentsMap = parseArguments(scriptArguments);
const buildType = argumentsMap.get("build-type") ?? "debug";

if (!["debug", "release"].includes(buildType)) {
  throw new Error("--build-type must be debug or release.");
}

const variantDir = join(projectRoot, "app", "build", "outputs", "apk", buildType);
const apksigner = await resolveApkSigner();
await assertExecutable(join(javaHome, "bin", "java"), "Android Studio JBR");
await run("gh", ["auth", "status"]);
await run("gh", ["repo", "view", repository]);
await buildVariant(buildType);

const metadataPath = argumentsMap.get("metadata")
  ? resolve(projectRoot, argumentsMap.get("metadata"))
  : join(variantDir, "output-metadata.json");
const metadata = JSON.parse(await readFile(metadataPath, "utf8"));
const apkElement = metadata.elements?.[0];
if (!apkElement?.outputFile) throw new Error(`No APK output in ${metadataPath}.`);

const sourceApk = argumentsMap.get("apk")
  ? resolve(projectRoot, argumentsMap.get("apk"))
  : join(dirname(metadataPath), apkElement.outputFile);
await assertSignedApk(sourceApk, apksigner);

const publishedApkName = `woolbox-${apkElement.versionName}${buildType === "debug" ? "-debug" : ""}.apk`;
const stagingRoot = await mkdtemp(join(tmpdir(), "woolbox-update-"));
const channelDir = join(stagingRoot, "woolbox", "releases", buildType);
const stagedApk = join(channelDir, publishedApkName);
const stagedManifest = join(channelDir, "latest.json");

await run("mkdir", ["-p", channelDir]);
await copyFile(sourceApk, stagedApk);
await run("node", [
  "tools/create-update-manifest.mjs",
  stagedApk,
  buildType,
  stagedManifest,
  metadataPath,
], { cwd: projectRoot });

const localManifest = JSON.parse(await readFile(stagedManifest, "utf8"));
assertManifest(localManifest, buildType, apkElement.versionCode, apkElement.versionName);

console.log(`${buildType}: APK ${sourceApk}`);
console.log(`${buildType}: manifest ${stagedManifest}`);
// The channel release must exist before publishing. Keep versioned APKs intact.
await run("gh", ["release", "view", buildType, "--repo", repository]);
await run("gh", ["release", "upload", buildType, stagedApk, "--repo", repository]);
await run("gh", ["release", "upload", buildType, stagedManifest, "--clobber", "--repo", repository]);

const remoteManifestUrl = `${downloadBaseUrl}/${buildType}/latest.json`;
const remoteManifest = await fetchJson(remoteManifestUrl);
assertManifest(remoteManifest, buildType, apkElement.versionCode, apkElement.versionName);
if (remoteManifest.apkUrl !== localManifest.apkUrl || remoteManifest.sha256 !== localManifest.sha256 || remoteManifest.sizeBytes !== localManifest.sizeBytes) {
  throw new Error("Remote manifest does not match the local manifest.");
}

const apkResponse = await fetch(remoteManifest.apkUrl, { method: "HEAD", redirect: "follow" });
if (apkResponse.status !== 200) throw new Error(`Remote APK HEAD failed: ${apkResponse.status}`);
const contentLength = Number(apkResponse.headers.get("content-length"));
if (!Number.isSafeInteger(contentLength) || contentLength !== remoteManifest.sizeBytes) {
  throw new Error(`Remote APK size mismatch: ${contentLength} != ${remoteManifest.sizeBytes}`);
}

console.log(`${buildType}: remote manifest ${remoteManifestUrl}`);
console.log(`${buildType}: remote APK ${remoteManifest.apkUrl} (${contentLength} bytes)`);

async function buildVariant(type) {
  const tasks = type === "debug"
    ? [":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"]
    : [":app:assembleRelease"];
  await run("./gradlew", tasks, { cwd: projectRoot, env: androidBuildEnvironment });
}

async function assertSignedApk(apkPath, signerPath) {
  await stat(apkPath);
  await run(signerPath, ["verify", "--verbose", apkPath], { env: androidBuildEnvironment });
}

async function resolveApkSigner() {
  const buildToolsDir = join(androidHome, "build-tools");
  const versions = await readdir(buildToolsDir, { withFileTypes: true }).catch(() => {
    throw new Error(`Android build-tools directory is missing: ${buildToolsDir}`);
  });
  const latest = versions
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort((left, right) => right.localeCompare(left, undefined, { numeric: true }))
    .at(0);
  if (!latest) throw new Error(`No Android build-tools found under ${buildToolsDir}.`);
  const signerPath = join(buildToolsDir, latest, "apksigner");
  await assertExecutable(signerPath, "Android APK signer");
  return signerPath;
}

async function assertExecutable(path, label) {
  await access(path, fsConstants.X_OK).catch(() => {
    throw new Error(`${label} is missing or not executable: ${path}`);
  });
}

function assertManifest(manifest, expectedBuildType, expectedVersionCode, expectedVersionName) {
  if (manifest.packageName !== "com.wswensw.woolbox") {
    throw new Error(`Unexpected packageName: ${manifest.packageName}`);
  }
  if (manifest.buildType !== expectedBuildType) {
    throw new Error(`Unexpected buildType: ${manifest.buildType}`);
  }
  if (manifest.versionCode !== expectedVersionCode || manifest.versionName !== expectedVersionName) {
    throw new Error("Remote manifest version does not match APK metadata.");
  }
  const debugSuffix = expectedBuildType === "debug" ? "-debug" : "";
  const expectedApkUrl = `${downloadBaseUrl}/${expectedBuildType}/woolbox-${expectedVersionName}${debugSuffix}.apk`;
  if (manifest.apkUrl !== expectedApkUrl) {
    throw new Error(`Unexpected APK URL: ${manifest.apkUrl}`);
  }
  if (!/^[a-f0-9]{64}$/.test(manifest.sha256)) throw new Error("Invalid manifest SHA-256.");
  if (!Number.isSafeInteger(manifest.sizeBytes) || manifest.sizeBytes <= 0) {
    throw new Error("Invalid manifest sizeBytes.");
  }
}

async function fetchJson(url) {
  const response = await fetch(url, { redirect: "follow" });
  if (!response.ok) throw new Error(`Fetch failed: ${url} ${response.status}`);
  return response.json();
}

function parseArguments(argumentsList) {
  const values = new Map();
  for (let index = 0; index < argumentsList.length; index += 1) {
    const name = argumentsList[index];
    if (!name.startsWith("--")) throw new Error(`Unexpected argument: ${name}`);
    const value = argumentsList[index + 1];
    if (!value || value.startsWith("--")) throw new Error(`Missing value for ${name}`);
    values.set(name.slice(2), value);
    index += 1;
  }
  return values;
}

function run(command, argumentsList, options = {}) {
  return new Promise((resolvePromise, rejectPromise) => {
    const child = spawn(command, argumentsList, {
      cwd: options.cwd,
      env: options.env,
      stdio: "inherit",
    });
    child.on("error", rejectPromise);
    child.on("close", (code) => {
      if (code === 0) {
        resolvePromise();
      } else {
        rejectPromise(new Error(`${basename(command)} failed with exit code ${code}.`));
      }
    });
  });
}
