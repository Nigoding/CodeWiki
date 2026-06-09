"use strict";

const fs = require("fs");
const path = require("path");

function posixPath(value) {
  return value.split(path.sep).join("/");
}

function relPath(repoPath, filePath) {
  return posixPath(path.relative(repoPath, filePath));
}

function walkFiles(root, predicate, results = []) {
  if (!fs.existsSync(root)) {
    return results;
  }
  const ignored = new Set(["node_modules", ".git", "dist", "build", "logs", "coverage"]);
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    if (ignored.has(entry.name)) {
      continue;
    }
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) {
      walkFiles(full, predicate, results);
    } else if (entry.isFile() && predicate(full)) {
      results.push(full);
    }
  }
  return results;
}

function slug(value) {
  return String(value || "item").toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "") || "item";
}

function moduleIdForFile(repoPath, filePath) {
  const rel = relPath(repoPath, filePath);
  const parts = rel.split("/");
  if (parts[0] === "src" && parts[1]) {
    return parts[1];
  }
  return "root";
}

function uniqueId(base, used) {
  let candidate = base;
  let index = 2;
  while (used.has(candidate)) {
    candidate = `${base}_${index}`;
    index += 1;
  }
  used.add(candidate);
  return candidate;
}

module.exports = { posixPath, relPath, walkFiles, slug, moduleIdForFile, uniqueId };
