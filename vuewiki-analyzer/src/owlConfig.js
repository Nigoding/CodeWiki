"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

function loadOwlPages(repoPath) {
  const diagnostics = [];
  const configPath = path.join(repoPath, "owl.config.js");
  if (!fs.existsSync(configPath)) {
    diagnostics.push({
      level: "error",
      code: "owl_config_not_found",
      message: "owl.config.js was not found; module and page declarations cannot be read."
    });
    return { configPath: null, modules: [], diagnostics };
  }

  let config = null;
  try {
    config = executeCommonJsConfig(configPath);
  } catch (error) {
    diagnostics.push({
      level: "error",
      code: "owl_config_parse_failed",
      path: "owl.config.js",
      message: "Failed to evaluate owl.config.js.",
      error: error.message
    });
    return { configPath, modules: [], diagnostics };
  }

  const pages = config?.bundlerConfig?.pages;
  if (!pages || typeof pages !== "object") {
    diagnostics.push({
      level: "error",
      code: "owl_pages_not_found",
      path: "owl.config.js",
      message: "bundlerConfig.pages was not found in owl.config.js."
    });
    return { configPath, modules: [], diagnostics };
  }

  const modules = [];
  for (const [moduleId, pageDefs] of Object.entries(pages)) {
    const modulePages = [];
    for (const [pageName, pageConfig] of Object.entries(pageDefs || {})) {
      modulePages.push({
        page_name: pageName,
        title: pageConfig?.title || pageName,
        biz_id: pageConfig?.bizId || pageConfig?.bizID || null,
        raw: pageConfig || {}
      });
    }
    modules.push({
      module_id: moduleId,
      name: moduleId,
      kind: "business_module",
      root_path: `src/${moduleId}`,
      declared_in: "owl.config.js",
      pages: modulePages
    });
  }

  const commonPath = path.join(repoPath, "src", "common");
  if (fs.existsSync(commonPath)) {
    modules.push({
      module_id: "common",
      name: "common",
      kind: "shared",
      root_path: "src/common",
      declared_in: "src",
      pages: []
    });
  }

  return { configPath, modules, diagnostics };
}

function executeCommonJsConfig(configPath) {
  const code = fs.readFileSync(configPath, "utf8");
  const sandbox = {
    module: { exports: {} },
    exports: {},
    require,
    process: { env: {} },
    __dirname: path.dirname(configPath),
    __filename: configPath,
    console
  };
  vm.runInNewContext(code, sandbox, { filename: configPath, timeout: 3000 });
  return sandbox.module.exports;
}

module.exports = { loadOwlPages };
