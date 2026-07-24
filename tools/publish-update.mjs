import { readdir } from "node:fs/promises";
import { copyFile, mkdtemp, readFile, stat } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, dirname, join, resolve } from "node:path";
import { spawn } from "node:child_process";

const projectRoot = resolve(import.meta.dirname, "..");
const downloaderProject = "/Users/wswensw/Documents/repos/debian-cloud-server";
const downloadBaseUrl = "https://dl.1662803.xyz/d/woolbox/releases";
const scriptArguments = process.argv.slice(2);
if (scriptArguments[0] === "--") scriptArguments.shift();
const argumentsMap = parseArguments(scriptArguments);
const buildType = argumentsMap.get("build-type") ?? "debug";

if (!["debug", "release"].includes(buildType)) {
  throw new Error("--build-type must be debug or release.");
}

const variantDir = join(projectRoot, "app", "build", "outputs", "apk", buildType);
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
await assertSignedApk(sourceApk);

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
console.log(`${buildType}: upload ${stagingRoot}/woolbox`);
await run("pnpm", ["--dir", downloaderProject, "downloads:upload", join(stagingRoot, "woolbox")]);

const remoteManifestUrl = `${downloadBaseUrl}/${buildType}/latest.json`;
const remoteManifest = await fetchJson(remoteManifestUrl);
assertManifest(remoteManifest, buildType, apkElement.versionCode, apkElement.versionName);
if (remoteManifest.apkUrl !== localManifest.apkUrl || remoteManifest.sha256 !== localManifest.sha256) {
  throw new Error("Remote manifest does not match the local manifest.");
}

const apkResponse = await fetch(remoteManifest.apkUrl, { method: "HEAD", redirect: "error" });
if (!apkResponse.ok) throw new Error(`Remote APK HEAD failed: ${apkResponse.status}`);
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
  await run("./gradlew", tasks, { cwd: projectRoot });
}

async function assertSignedApk(apkPath) {
  await stat(apkPath);
  const apksigner = await resolveApkSigner();
  await run(apksigner, ["verify", "--verbose", apkPath]);
}

async function resolveApkSigner() {
  const androidHome = process.env.ANDROID_HOME ?? "/Users/wswensw/Library/Android/sdk";
  const buildToolsDir = join(androidHome, "build-tools");
  const versions = await readdir(buildToolsDir, { withFileTypes: true });
  const latest = versions
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort((left, right) => right.localeCompare(left, undefined, { numeric: true }))
    .at(0);
  if (!latest) throw new Error(`No Android build-tools found under ${buildToolsDir}.`);
  return join(buildToolsDir, latest, "apksigner");
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
  const response = await fetch(url, { redirect: "error" });
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
