"use strict";

const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync, spawnSync } = require("child_process");

function resolveSource({ source, outputDir, keepClone, submodules }) {
  if (isRemote(source)) {
    return cloneRemote(source, outputDir, keepClone, submodules);
  }
  const repoPath = path.resolve(source);
  if (!fs.existsSync(repoPath) || !fs.statSync(repoPath).isDirectory()) {
    throw new Error(`Local repository path does not exist or is not a directory: ${repoPath}`);
  }
  const submoduleResult = handleSubmodules(repoPath, submodules, false);
  return {
    sourceType: "local",
    input: source,
    path: repoPath,
    repoName: path.basename(repoPath),
    cleanupPath: null,
    submodules: submoduleResult.submodules,
    diagnostics: submoduleResult.diagnostics
  };
}

function cleanupSource(resolved) {
  if (resolved.cleanupPath && fs.existsSync(resolved.cleanupPath)) {
    fs.rmSync(resolved.cleanupPath, { recursive: true, force: true });
  }
}

function readGitInfo(repoPath) {
  return {
    commit: gitOutput(repoPath, ["rev-parse", "HEAD"]),
    branch: gitOutput(repoPath, ["rev-parse", "--abbrev-ref", "HEAD"]),
    remote_url: gitOutput(repoPath, ["remote", "get-url", "origin"])
  };
}

function cloneRemote(source, outputDir, keepClone, submodules) {
  const repoName = repoNameFromUrl(source);
  let cloneDir;
  let cleanupPath = null;
  if (keepClone) {
    cloneDir = path.join(outputDir, "_source", repoName);
    fs.mkdirSync(path.dirname(cloneDir), { recursive: true });
    if (fs.existsSync(cloneDir)) {
      runGit(["-C", cloneDir, "fetch", "--depth", "1"], false);
    } else {
      runGit(["clone", "--depth", "1", source, cloneDir], true);
    }
  } else {
    cleanupPath = fs.mkdtempSync(path.join(os.tmpdir(), "vuewiki_"));
    cloneDir = path.join(cleanupPath, repoName);
    runGit(["clone", "--depth", "1", source, cloneDir], true);
  }

  const submoduleResult = handleSubmodules(cloneDir, submodules, submodules !== "none");
  return {
    sourceType: "remote",
    input: source,
    path: path.resolve(cloneDir),
    repoName,
    cleanupPath,
    submodules: submoduleResult.submodules,
    diagnostics: submoduleResult.diagnostics
  };
}

function handleSubmodules(repoPath, mode, allowInit) {
  const diagnostics = [];
  const gitmodules = path.join(repoPath, ".gitmodules");
  if (!fs.existsSync(gitmodules)) {
    return { submodules: [], diagnostics };
  }
  if (mode === "none") {
    diagnostics.push({
      level: "warning",
      code: "submodule_skipped",
      message: "Repository declares Git submodules, but submodule handling is disabled."
    });
    return { submodules: readSubmodules(repoPath), diagnostics };
  }
  if (allowInit) {
    diagnostics.push(...updateSubmodules(repoPath));
  } else {
    diagnostics.push({
      level: "warning",
      code: "submodule_init_required",
      message: "Local repository declares Git submodules. Initialize them before analysis when needed."
    });
  }
  const submodules = readSubmodules(repoPath);
  for (const item of submodules) {
    if (!item.initialized) {
      diagnostics.push({
        level: "warning",
        code: "submodule_missing",
        path: item.path,
        message: `Git submodule is not initialized: ${item.path}`
      });
    }
  }
  return { submodules, diagnostics };
}

function updateSubmodules(repoPath) {
  const diagnostics = [];
  const sync = runGit(["-C", repoPath, "submodule", "sync", "--recursive"], false);
  if (sync.status !== 0) {
    diagnostics.push(submoduleError("submodule_update_failed", sync));
    return diagnostics;
  }
  const shallow = runGit(["-C", repoPath, "submodule", "update", "--init", "--recursive", "--depth", "1"], false);
  if (shallow.status === 0) {
    return diagnostics;
  }
  diagnostics.push({
    level: "warning",
    code: "submodule_shallow_update_failed",
    message: "Shallow submodule update failed; retrying without --depth.",
    error: outputOf(shallow)
  });
  const full = runGit(["-C", repoPath, "submodule", "update", "--init", "--recursive"], false);
  if (full.status !== 0) {
    diagnostics.push(submoduleError("submodule_update_failed", full));
  }
  return diagnostics;
}

function readSubmodules(repoPath) {
  const defs = readGitmoduleDefs(repoPath);
  const statuses = readSubmoduleStatus(repoPath);
  return [...new Set([...Object.keys(defs), ...Object.keys(statuses)])].sort().map((subPath) => ({
    path: subPath,
    url: defs[subPath]?.url || null,
    name: defs[subPath]?.name || null,
    commit: statuses[subPath]?.commit || null,
    initialized: statuses[subPath]?.initialized || false,
    status: statuses[subPath]?.status || "unknown"
  }));
}

function readGitmoduleDefs(repoPath) {
  const gitmodules = path.join(repoPath, ".gitmodules");
  const result = spawnSync("git", ["config", "--file", gitmodules, "--get-regexp", "^submodule\\..*\\.(path|url)$"], {
    encoding: "utf8"
  });
  if (result.status !== 0) {
    return {};
  }
  const byName = {};
  for (const line of result.stdout.split(/\r?\n/)) {
    const match = line.match(/^submodule\.([^.]+)\.(path|url)\s+(.+)$/);
    if (!match) continue;
    byName[match[1]] ||= { name: match[1] };
    byName[match[1]][match[2]] = match[3].trim();
  }
  const byPath = {};
  for (const entry of Object.values(byName)) {
    if (entry.path) {
      byPath[entry.path] = entry;
    }
  }
  return byPath;
}

function readSubmoduleStatus(repoPath) {
  const result = runGit(["-C", repoPath, "submodule", "status", "--recursive"], false);
  if (result.status !== 0) {
    return {};
  }
  const statuses = {};
  for (const line of result.stdout.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const marker = line[0];
    const parts = line.slice(1).trim().split(/\s+/);
    if (parts.length < 2) continue;
    statuses[parts[1]] = {
      commit: parts[0],
      initialized: marker !== "-",
      status: marker === " " ? "ok" : marker === "-" ? "missing" : marker === "+" ? "commit_mismatch" : "unknown"
    };
  }
  return statuses;
}

function isRemote(value) {
  return /^(https?:\/\/|git@)/.test(value);
}

function repoNameFromUrl(url) {
  const tail = url.replace(/\/$/, "").split("/").pop().replace(/\.git$/, "");
  return tail.replace(/[^A-Za-z0-9_.-]+/g, "-") || "repository";
}

function gitOutput(repoPath, args) {
  try {
    return execFileSync("git", ["-C", repoPath, ...args], { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim() || null;
  } catch {
    return null;
  }
}

function runGit(args, check) {
  const result = spawnSync("git", args, { encoding: "utf8" });
  if (check && result.status !== 0) {
    throw new Error(outputOf(result) || `git ${args.join(" ")} failed`);
  }
  return result;
}

function submoduleError(code, result) {
  return {
    level: "error",
    code,
    message: "Failed to initialize or update Git submodules.",
    error: outputOf(result)
  };
}

function outputOf(result) {
  return (result.stderr || result.stdout || "").trim();
}

module.exports = { resolveSource, cleanupSource, readGitInfo };
