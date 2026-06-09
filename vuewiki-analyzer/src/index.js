"use strict";

const path = require("path");
const { resolveSource, cleanupSource, readGitInfo } = require("./sourceResolver");
const { loadOwlPages } = require("./owlConfig");
const { extractApiDefinitions } = require("./apiExtractor");
const { analyzePages } = require("./pageAnalyzer");
const { writeArtifacts } = require("./writer");
const { posixPath } = require("./utils");

async function analyze(options) {
  const outputDir = path.resolve(options.output);
  const resolved = resolveSource({
    source: options.source,
    outputDir,
    keepClone: options.keepClone,
    submodules: options.submodules
  });

  const diagnostics = [...resolved.diagnostics];
  try {
    const owl = loadOwlPages(resolved.path);
    diagnostics.push(...owl.diagnostics);

    const apiIndex = extractApiDefinitions(resolved.path, diagnostics);
    const pageResult = analyzePages(resolved.path, owl.modules, apiIndex, diagnostics, options.verbose);

    const modules = buildModuleTree(resolved.path, owl.modules, pageResult.pages, apiIndex);
    const analysis = {
      schema_version: "1.0",
      generated_at: new Date().toISOString(),
      source: {
        type: resolved.sourceType,
        input: resolved.input,
        resolved_path: resolved.path,
        repo_name: resolved.repoName,
        git: readGitInfo(resolved.path),
        submodules: resolved.submodules
      },
      framework: {
        name: "vue",
        version_major: 2
      },
      entry_config: {
        type: "owl",
        path: owl.configPath ? posixPath(path.relative(resolved.path, owl.configPath)) : null
      },
      artifacts: {
        module_tree: "module_tree.json",
        page_index: "page_index.json",
        component_index: "component_index.json",
        api_index: "api_index.json",
        page_flows: "page_flows.json",
        backend_api_usage: "backend_api_usage.json",
        modules_dir: "modules",
        pages_dir: "pages",
        components_dir: "components"
      },
      summary: {
        total_modules: Object.keys(modules.modules).length,
        total_pages: Object.keys(pageResult.pages.pages).length,
        total_components: Object.keys(pageResult.components.components).length,
        total_api_defs: Object.keys(apiIndex.api_calls).length,
        total_page_flows: pageResult.flows.flows.length,
        total_backend_api_usages: pageResult.backendUsages.backend_usages.length
      },
      diagnostics
    };

    writeArtifacts({
      outputDir,
      analysis,
      moduleTree: modules,
      pageIndex: pageResult.pages,
      componentIndex: pageResult.components,
      apiIndex,
      pageFlows: pageResult.flows,
      backendApiUsage: pageResult.backendUsages
    });
    return outputDir;
  } finally {
    if (!options.keepClone) {
      cleanupSource(resolved);
    }
  }
}

function buildModuleTree(repoPath, declaredModules, pageIndex, apiIndex) {
  const modules = {};
  for (const moduleDef of declaredModules) {
    modules[moduleDef.module_id] = {
      module_id: moduleDef.module_id,
      name: moduleDef.name,
      kind: moduleDef.kind,
      root_path: moduleDef.root_path,
      declared_in: moduleDef.declared_in,
      page_ids: [],
      api_call_ids: []
    };
  }

  for (const page of Object.values(pageIndex.pages)) {
    modules[page.module_id] ||= {
      module_id: page.module_id,
      name: page.module_id,
      kind: "business_module",
      root_path: `src/${page.module_id}`,
      declared_in: "derived",
      page_ids: [],
      api_call_ids: []
    };
    modules[page.module_id].page_ids.push(page.page_id);
  }

  for (const api of Object.values(apiIndex.api_calls)) {
    modules[api.module_id] ||= {
      module_id: api.module_id,
      name: api.module_id,
      kind: api.module_id === "common" ? "shared" : "business_module",
      root_path: `src/${api.module_id}`,
      declared_in: "derived",
      page_ids: [],
      api_call_ids: []
    };
    modules[api.module_id].api_call_ids.push(api.api_call_id);
  }

  for (const module of Object.values(modules)) {
    module.page_ids = [...new Set(module.page_ids)].sort();
    module.api_call_ids = [...new Set(module.api_call_ids)].sort();
  }

  return { modules };
}

module.exports = { analyze };
