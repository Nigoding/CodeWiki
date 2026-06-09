"use strict";

const fs = require("fs");
const path = require("path");
const { parseVueFile, possibleTagNames } = require("./vueParser");
const { relPath, walkFiles, slug, uniqueId } = require("./utils");

function analyzePages(repoPath, modules, apiIndex, diagnostics, verbose) {
  const pages = { pages: {} };
  const components = { components: {} };
  const flows = { flows: [] };
  const backendUsages = { backend_usages: [] };
  const usedComponentIds = new Set();
  const usedFlowIds = new Set();

  for (const moduleDef of modules.filter((item) => item.kind === "business_module")) {
    for (const pageDef of moduleDef.pages) {
      const pageId = slug(`${moduleDef.module_id}_${pageDef.page_name}`);
      const entry = locatePageEntry(repoPath, moduleDef.module_id, pageDef.page_name);
      if (!entry) {
        diagnostics.push({
          level: "warning",
          code: "page_entry_not_found",
          module_id: moduleDef.module_id,
          page_name: pageDef.page_name,
          message: `Page entry app.vue was not found for ${moduleDef.module_id}/${pageDef.page_name}.`
        });
        pages.pages[pageId] = basePage(pageId, moduleDef, pageDef, null);
        continue;
      }

      if (verbose) {
        console.log(`page ${pageId}: ${relPath(repoPath, entry)}`);
      }

      const graph = buildComponentGraph(repoPath, entry, pageId, moduleDef.module_id, diagnostics, usedComponentIds);
      for (const component of Object.values(graph.components)) {
        components.components[component.component_id] = component;
      }

      const pageFlows = extractPageFlows({
        repoPath,
        pageId,
        moduleId: moduleDef.module_id,
        pageTitle: pageDef.title,
        bizId: pageDef.biz_id,
        graph,
        apiIndex,
        usedFlowIds
      });
      flows.flows.push(...pageFlows);
      for (const flow of pageFlows) {
        for (const step of flow.steps) {
          if (step.type === "api_call") {
            backendUsages.backend_usages.push({
              method: step.method,
              path: step.path,
              frontend_module_id: moduleDef.module_id,
              page_id: pageId,
              page_title: pageDef.title,
              component_id: flow.component_id,
              component_file: flow.component_file,
              trigger: flow.trigger.name,
              api_call_id: step.api_call_id,
              confidence: flow.confidence
            });
          }
        }
      }

      pages.pages[pageId] = {
        ...basePage(pageId, moduleDef, pageDef, relPath(repoPath, entry)),
        component_ids: Object.keys(graph.components),
        flow_ids: pageFlows.map((flow) => flow.flow_id),
        component_edges: graph.edges
      };
    }
  }

  return { pages, components, flows, backendUsages };
}

function basePage(pageId, moduleDef, pageDef, entryFile) {
  return {
    page_id: pageId,
    module_id: moduleDef.module_id,
    page_name: pageDef.page_name,
    title: pageDef.title,
    biz_id: pageDef.biz_id,
    declared_in: "owl.config.js",
    entry_file: entryFile,
    component_ids: [],
    flow_ids: [],
    component_edges: []
  };
}

function locatePageEntry(repoPath, moduleId, pageName) {
  const base = path.join(repoPath, "src", moduleId, pageName);
  const candidates = [
    path.join(base, "app.vue"),
    path.join(base, "App.vue"),
    path.join(base, "index.vue"),
    path.join(repoPath, "src", moduleId, `${pageName}.vue`)
  ];
  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) return candidate;
  }
  const nested = walkFiles(base, (file) => file.endsWith(".vue"));
  return nested[0] || null;
}

function buildComponentGraph(repoPath, entryFile, pageId, moduleId, diagnostics, usedComponentIds) {
  const components = {};
  const edges = [];
  const visited = new Set();

  function visit(file, role, parent, tag) {
    const resolved = path.resolve(file);
    if (visited.has(resolved)) return;
    visited.add(resolved);

    let parsed;
    try {
      parsed = parseVueFile(resolved);
    } catch (error) {
      diagnostics.push({
        level: "warning",
        code: "vue_component_parse_failed",
        path: relPath(repoPath, resolved),
        message: "Failed to parse Vue component.",
        error: error.message
      });
      return;
    }

    const componentId = uniqueId(slug(`${pageId}_${path.basename(resolved, ".vue")}`), usedComponentIds);
    const rel = relPath(repoPath, resolved);
    components[componentId] = {
      component_id: componentId,
      page_id: pageId,
      module_id: moduleId,
      file_path: rel,
      role,
      imports: parsed.imports,
      registered_components: parsed.registered_components,
      template_components: parsed.template_components,
      template_events: parsed.template_events.map((event) => `${event.event}:${event.handler}`),
      lifecycle_hooks: Object.keys(parsed.lifecycle_hooks),
      method_names: Object.keys(parsed.methods),
      watch_names: Object.keys(parsed.watch),
      api_call_ids: [],
      artifact_path: `components/${componentId}.json`,
      _parsed: parsed,
      _abs_path: resolved
    };

    if (parent) {
      edges.push({
        from: parent.rel,
        to: rel,
        kind: "registered_component",
        tag
      });
    }

    const children = resolveChildComponents(repoPath, resolved, parsed);
    for (const child of children) {
      visit(child.file, "child", { rel }, child.tag);
    }
  }

  visit(entryFile, "entry", null, null);
  return { components, edges };
}

function resolveChildComponents(repoPath, file, parsed) {
  const byLocal = {};
  for (const item of parsed.imports) {
    const target = resolveImport(repoPath, file, item.source, [".vue"]);
    if (target) {
      byLocal[item.local] = target;
    }
  }

  const usedTags = new Set(parsed.template_components.flatMap((tag) => [tag, tag.toLowerCase()]));
  const children = [];
  for (const localName of parsed.registered_components) {
    const target = byLocal[localName];
    if (!target) continue;
    const names = possibleTagNames(localName);
    const tag = [...names].find((name) => usedTags.has(name) || usedTags.has(name.toLowerCase())) || localName;
    children.push({ file: target, tag });
  }
  return children;
}

function extractPageFlows({ repoPath, pageId, moduleId, pageTitle, bizId, graph, apiIndex, usedFlowIds }) {
  const flows = [];
  const apiByFile = apiIndex.by_file || {};

  for (const component of Object.values(graph.components)) {
    const parsed = component._parsed;
    const apiImports = mapApiImports(repoPath, component._abs_path, parsed.imports, apiByFile);
    const methodApiCache = {};
    for (const [methodName, body] of Object.entries(parsed.methods)) {
      methodApiCache[methodName] = callsInBody(body, apiImports);
    }

    for (const [hookName, body] of Object.entries(parsed.lifecycle_hooks)) {
      const steps = callsInBody(body, apiImports, methodApiCache);
      addFlow(flows, usedFlowIds, {
        pageId,
        moduleId,
        pageTitle,
        bizId,
        component,
        trigger: { type: "lifecycle", name: hookName },
        steps,
        confidence: "high"
      });
    }

    for (const event of parsed.template_events) {
      const steps = methodApiCache[event.handler] || [];
      addFlow(flows, usedFlowIds, {
        pageId,
        moduleId,
        pageTitle,
        bizId,
        component,
        trigger: { type: "event_handler", name: event.handler, event: event.event },
        steps,
        confidence: component.role === "entry" ? "medium" : "medium"
      });
    }

    for (const [watchName, body] of Object.entries(parsed.watch)) {
      const steps = callsInBody(body, apiImports, methodApiCache);
      addFlow(flows, usedFlowIds, {
        pageId,
        moduleId,
        pageTitle,
        bizId,
        component,
        trigger: { type: "watch", name: watchName },
        steps,
        confidence: "medium"
      });
    }

    component.api_call_ids = [...new Set(flows
      .filter((flow) => flow.component_id === component.component_id)
      .flatMap((flow) => flow.steps.map((step) => step.api_call_id)))].sort();

    delete component._parsed;
    delete component._abs_path;
  }
  return flows.filter((flow) => flow.steps.length > 0);
}

function addFlow(flows, usedFlowIds, { pageId, moduleId, pageTitle, bizId, component, trigger, steps, confidence }) {
  if (!steps.length) return;
  const flowId = uniqueId(slug(`${pageId}_${path.basename(component.file_path, ".vue")}_${trigger.name}`), usedFlowIds);
  flows.push({
    flow_id: flowId,
    module_id: moduleId,
    page_id: pageId,
    page_title: pageTitle,
    biz_id: bizId,
    component_id: component.component_id,
    component_file: component.file_path,
    component_role: component.role,
    trigger,
    steps: steps.map((step, index) => ({ order: index + 1, ...step })),
    confidence
  });
}

function mapApiImports(repoPath, componentFile, imports, apiByFile) {
  const direct = {};
  const objectImports = {};
  for (const item of imports) {
    const file = resolveImport(repoPath, componentFile, item.source, [".js"]);
    if (!file) continue;
    const defs = apiByFile[path.resolve(file)] || [];
    if (item.kind === "named") {
      const api = defs.find((def) => def.function_name === item.imported);
      if (api) direct[item.local] = api;
    } else {
      objectImports[item.local] = defs;
    }
  }
  return { direct, objectImports };
}

function callsInBody(body, apiImports, methodApiCache = {}) {
  const calls = [];
  for (const [local, api] of Object.entries(apiImports.direct)) {
    for (const match of body.matchAll(new RegExp(`\\b${escapeRegex(local)}\\s*\\(`, "g"))) {
      calls.push({ index: match.index, api });
    }
  }
  for (const [local, defs] of Object.entries(apiImports.objectImports)) {
    for (const api of defs) {
      const regex = new RegExp(`\\b${escapeRegex(local)}\\.${escapeRegex(api.function_name)}\\s*\\(`, "g");
      for (const match of body.matchAll(regex)) {
        calls.push({ index: match.index, api });
      }
    }
  }
  for (const [method, methodCalls] of Object.entries(methodApiCache)) {
    const regex = new RegExp(`\\b(?:this\\.)?${escapeRegex(method)}\\s*\\(`, "g");
    for (const match of body.matchAll(regex)) {
      for (const item of methodCalls) {
        calls.push({ index: match.index + item.order / 1000, api: item._api || item });
      }
    }
  }
  return calls.sort((a, b) => a.index - b.index).map((item) => ({
    type: "api_call",
    api_call_id: item.api.api_call_id,
    function_name: item.api.function_name,
    method: item.api.method,
    path: item.api.path,
    confidence: item.api.confidence,
    _api: item.api
  })).map((item) => {
    const copy = { ...item };
    delete copy._api;
    return copy;
  });
}

function resolveImport(repoPath, fromFile, source, extensions) {
  if (!source.startsWith(".") && !source.startsWith("@/")) {
    return null;
  }
  const base = source.startsWith("@/")
    ? path.join(repoPath, "src", source.slice(2))
    : path.resolve(path.dirname(fromFile), source);
  const candidates = [];
  for (const ext of extensions) {
    candidates.push(base.endsWith(ext) ? base : `${base}${ext}`);
    candidates.push(path.join(base, `index${ext}`));
    candidates.push(path.join(base, `app${ext}`));
    candidates.push(path.join(base, `App${ext}`));
  }
  return candidates.find((candidate) => fs.existsSync(candidate)) || null;
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

module.exports = { analyzePages };
