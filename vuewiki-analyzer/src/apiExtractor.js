"use strict";

const fs = require("fs");
const path = require("path");
const { relPath, walkFiles, slug, moduleIdForFile, uniqueId } = require("./utils");

const HTTP_METHODS = new Set(["get", "post", "put", "delete", "patch"]);

function extractApiDefinitions(repoPath, diagnostics) {
  const files = walkFiles(path.join(repoPath, "src"), (file) => file.endsWith(".js"));
  const apiCalls = {};
  const byFile = {};
  const usedIds = new Set();

  for (const file of files) {
    const source = fs.readFileSync(file, "utf8");
    const rel = relPath(repoPath, file);
    const moduleId = moduleIdForFile(repoPath, file);
    const defs = [
      ...extractRequestObjectCalls(source, file, rel, moduleId, usedIds),
      ...extractAxiosCalls(source, file, rel, moduleId, usedIds)
    ];
    if (defs.length) {
      byFile[path.resolve(file)] = defs;
    }
    for (const def of defs) {
      apiCalls[def.api_call_id] = def;
      if (def.confidence === "low") {
        diagnostics.push({
          level: "warning",
          code: "dynamic_api_path",
          path: rel,
          function_name: def.function_name,
          message: "API path or method is dynamic; backend mapping may be incomplete."
        });
      }
    }
  }

  return {
    api_calls: apiCalls,
    by_file: byFile
  };
}

function extractRequestObjectCalls(source, file, rel, moduleId, usedIds) {
  const defs = [];
  const regex = /\b(?:request|axios)\s*\(\s*\{([\s\S]*?)\}\s*\)/g;
  for (const match of source.matchAll(regex)) {
    const objectText = match[1];
    const pathValue = literalProperty(objectText, "url");
    const methodValue = literalProperty(objectText, "method") || "GET";
    const functionName = nearestFunctionName(source, match.index) || `api_${defs.length + 1}`;
    const confidence = pathValue.dynamic || !methodValue ? "low" : "high";
    defs.push(makeApiDef({
      moduleId,
      functionName,
      method: normalizeMethod(methodValue.value || methodValue),
      apiPath: pathValue.value,
      definedIn: rel,
      confidence,
      usedIds
    }));
  }
  return defs;
}

function extractAxiosCalls(source, file, rel, moduleId, usedIds) {
  const defs = [];
  const regex = /\b(?:axios|request)\.(get|post|put|delete|patch)\s*\(\s*([`'"])(.*?)\2/g;
  for (const match of source.matchAll(regex)) {
    const functionName = nearestFunctionName(source, match.index) || `api_${defs.length + 1}`;
    defs.push(makeApiDef({
      moduleId,
      functionName,
      method: normalizeMethod(match[1]),
      apiPath: match[3],
      definedIn: rel,
      confidence: "high",
      usedIds
    }));
  }
  return defs;
}

function makeApiDef({ moduleId, functionName, method, apiPath, definedIn, confidence, usedIds }) {
  const base = slug(`${moduleId}_${functionName}`);
  return {
    api_call_id: uniqueId(base, usedIds),
    module_id: moduleId,
    function_name: functionName,
    method,
    path: normalizePath(apiPath),
    defined_in: definedIn,
    confidence
  };
}

function literalProperty(objectText, property) {
  const literal = new RegExp(`${property}\\s*:\\s*([\\\`'"])(.*?)\\1`, "i").exec(objectText);
  if (literal) {
    return { value: literal[2], dynamic: false };
  }
  const dynamic = new RegExp(`${property}\\s*:\\s*([^,\\n]+)`, "i").exec(objectText);
  if (dynamic) {
    return { value: String(dynamic[1]).trim(), dynamic: true };
  }
  return { value: "", dynamic: true };
}

function nearestFunctionName(source, index) {
  const prefix = source.slice(Math.max(0, index - 1200), index);
  const patterns = [
    /(?:export\s+)?(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/g,
    /(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>/g,
    /(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?function\s*\(/g,
    /([A-Za-z_$][\w$]*)\s*:\s*(?:async\s*)?function\s*\(/g
  ];
  let best = null;
  for (const pattern of patterns) {
    for (const match of prefix.matchAll(pattern)) {
      best = match[1];
    }
  }
  return best;
}

function normalizeMethod(value) {
  const method = String(value || "GET").replace(/[`'"]/g, "").toUpperCase();
  return HTTP_METHODS.has(method.toLowerCase()) ? method : method;
}

function normalizePath(value) {
  const trimmed = String(value || "").trim();
  return trimmed.startsWith("/") ? trimmed : `/${trimmed}`;
}

module.exports = { extractApiDefinitions };
