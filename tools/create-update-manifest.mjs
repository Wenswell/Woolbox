import { createHash } from "node:crypto";
import { readFile, stat, writeFile } from "node:fs/promises";
import { basename, dirname, join } from "node:path";

const [apkPath, buildType, outputPath, metadataPathArgument] = process.argv.slice(2);
const downloadBaseUrl = "https://dl.1662803.xyz/d/woolbox/releases";
const versionNamePattern = /^\d+\.\d+\.\d+$/;

if (!apkPath || !outputPath || !["debug", "release"].includes(buildType)) {
  throw new Error("Usage: node tools/create-update-manifest.mjs <apk-path> <debug|release> <output-json-path> [output-metadata.json]");
}

const metadataPath = metadataPathArgument ?? join(dirname(apkPath), "output-metadata.json");
const [apkBytes, apkStats, metadataBody] = await Promise.all([
  readFile(apkPath),
  stat(apkPath),
  readFile(metadataPath, "utf8"),
]);
const metadata = JSON.parse(metadataBody);
const matchingElement = metadata.elements.find((element) => element.outputFile === basename(apkPath));
const apkElement = matchingElement ?? (metadata.elements.length === 1 ? metadata.elements[0] : null);

if (!apkElement) throw new Error(`Unable to find ${basename(apkPath)} in ${metadataPath}.`);
if (metadata.applicationId !== "com.wswensw.woolbox") {
  throw new Error(`Unexpected packageName: ${metadata.applicationId}`);
}
if (metadata.variantName !== buildType) {
  throw new Error(`Build type mismatch: metadata=${metadata.variantName}, requested=${buildType}`);
}
if (!Number.isSafeInteger(apkElement.versionCode) || apkElement.versionCode <= 0) {
  throw new Error(`Invalid versionCode: ${apkElement.versionCode}`);
}
if (!versionNamePattern.test(apkElement.versionName)) {
  throw new Error(`Invalid versionName: ${apkElement.versionName}`);
}
if (apkStats.size <= 0) throw new Error("APK is empty.");

const publishedApkName = `woolbox-${apkElement.versionName}${buildType === "debug" ? "-debug" : ""}.apk`;
const manifest = {
  packageName: metadata.applicationId,
  buildType,
  versionCode: apkElement.versionCode,
  versionName: apkElement.versionName,
  apkUrl: `${downloadBaseUrl}/${buildType}/${publishedApkName}`,
  sha256: createHash("sha256").update(apkBytes).digest("hex"),
  sizeBytes: apkStats.size,
};

await writeFile(outputPath, `${JSON.stringify(manifest, null, 2)}\n`);
