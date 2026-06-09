"use strict";

const fs = require("fs");
const path = require("path");

function writeArtifacts({ outputDir, analysis, moduleTree, pageIndex, componentIndex, apiIndex, pageFlows, backendApiUsage }) {
  prepare(outputDir);
  writeJson(path.join(outputDir, "analysis.json"), analysis);
  writeJson(path.join(outputDir, "module_tree.json"), moduleTree);
  writeJson(path.join(outputDir, "page_index.json"), pageIndex);
  writeJson(path.join(outputDir, "component_index.json"), stripInternal(componentIndex));
  writeJson(path.join(outputDir, "api_index.json"), stripByFile(apiIndex));
  writeJson(path.join(outputDir, "page_flows.json"), pageFlows);
  writeJson(path.join(outputDir, "backend_api_usage.json"), backendApiUsage);

  for (const module of Object.values(moduleTree.modules)) {
    writeJson(path.join(outputDir, "modules", `${module.module_id}.json`), module);
  }
  for (const page of Object.values(pageIndex.pages)) {
    writeJson(path.join(outputDir, "pages", `${page.page_id}.json`), page);
  }
  for (const component of Object.values(componentIndex.components)) {
    writeJson(path.join(outputDir, "components", `${component.component_id}.json`), stripInternalValue(component));
  }
}

function prepare(outputDir) {
  fs.mkdirSync(outputDir, { recursive: true });
  for (const name of ["modules", "pages", "components"]) {
    fs.rmSync(path.join(outputDir, name), { recursive: true, force: true });
    fs.mkdirSync(path.join(outputDir, name), { recursive: true });
  }
  for (const file of [
    "analysis.json",
    "module_tree.json",
    "page_index.json",
    "component_index.json",
    "api_index.json",
    "page_flows.json",
    "backend_api_usage.json"
  ]) {
    fs.rmSync(path.join(outputDir, file), { force: true });
  }
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function stripByFile(apiIndex) {
  return { api_calls: apiIndex.api_calls };
}

function stripInternal(componentIndex) {
  return { components: Object.fromEntries(Object.entries(componentIndex.components).map(([key, value]) => [key, stripInternalValue(value)])) };
}

function stripInternalValue(value) {
  if (Array.isArray(value)) {
    return value.map(stripInternalValue);
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value)
      .filter(([key]) => !key.startsWith("_"))
      .map(([key, item]) => [key, stripInternalValue(item)]));
  }
  return value;
}

module.exports = { writeArtifacts };
