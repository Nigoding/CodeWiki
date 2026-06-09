"use strict";

const fs = require("fs");
const path = require("path");

function parseVueFile(filePath) {
  const source = fs.readFileSync(filePath, "utf8");
  const template = block(source, "template");
  const script = block(source, "script");
  return {
    source,
    template,
    script,
    imports: parseImports(script),
    registered_components: parseRegisteredComponents(script),
    template_components: parseTemplateComponents(template),
    template_events: parseTemplateEvents(template),
    lifecycle_hooks: parseLifecycleHooks(script),
    methods: parseMethods(script),
    watch: parseWatch(script)
  };
}

function block(source, name) {
  const match = new RegExp(`<${name}[^>]*>([\\s\\S]*?)<\\/${name}>`, "i").exec(source);
  return match ? match[1] : "";
}

function parseImports(script) {
  const imports = [];
  const named = /import\s*\{([^}]+)\}\s*from\s*['"]([^'"]+)['"]/g;
  for (const match of script.matchAll(named)) {
    for (const item of match[1].split(",")) {
      const [imported, local] = item.trim().split(/\s+as\s+/);
      if (imported) {
        imports.push({
          kind: "named",
          imported: imported.trim(),
          local: (local || imported).trim(),
          source: match[2]
        });
      }
    }
  }

  const namespace = /import\s+\*\s+as\s+([A-Za-z_$][\w$]*)\s+from\s*['"]([^'"]+)['"]/g;
  for (const match of script.matchAll(namespace)) {
    imports.push({ kind: "namespace", local: match[1], source: match[2] });
  }

  const defaults = /import\s+([A-Za-z_$][\w$]*)\s+from\s*['"]([^'"]+)['"]/g;
  for (const match of script.matchAll(defaults)) {
    if (match[0].includes("{") || match[0].includes("* as")) continue;
    imports.push({ kind: "default", local: match[1], source: match[2] });
  }

  return imports;
}

function parseRegisteredComponents(script) {
  const blockText = objectBlock(script, "components");
  if (!blockText) return [];
  const names = new Set();
  for (const match of blockText.matchAll(/(?:['"]?([A-Za-z][\w-]*)['"]?\s*:\s*)?([A-Za-z_$][\w$]*)/g)) {
    names.add(match[1] || match[2]);
  }
  return [...names];
}

function parseTemplateComponents(template) {
  const builtins = new Set([
    "div", "span", "p", "a", "ul", "li", "ol", "button", "input", "form", "label", "select", "option",
    "table", "thead", "tbody", "tr", "td", "th", "img", "section", "header", "footer", "main", "template"
  ]);
  const tags = new Set();
  for (const match of template.matchAll(/<([A-Za-z][\w-]*)\b/g)) {
    const tag = match[1];
    if (!builtins.has(tag.toLowerCase()) && (tag.includes("-") || /^[A-Z]/.test(tag))) {
      tags.add(tag);
    }
  }
  return [...tags];
}

function parseTemplateEvents(template) {
  const events = [];
  const regex = /(?:@|v-on:)([A-Za-z][\w:-]*)\s*=\s*"([^"]+)"/g;
  for (const match of template.matchAll(regex)) {
    const handler = match[2].split("(")[0].trim();
    events.push({ event: match[1], handler, raw: match[2] });
  }
  return events;
}

function parseLifecycleHooks(script) {
  const hooks = {};
  for (const name of ["created", "mounted", "beforeMount", "activated"]) {
    const body = propertyFunctionBody(script, name);
    if (body !== null) {
      hooks[name] = body;
    }
  }
  return hooks;
}

function parseMethods(script) {
  const methodsBlock = objectBlock(script, "methods");
  return methodsBlock ? functionProperties(methodsBlock) : {};
}

function parseWatch(script) {
  const watchBlock = objectBlock(script, "watch");
  return watchBlock ? functionProperties(watchBlock) : {};
}

function objectBlock(source, key) {
  const match = new RegExp(`${key}\\s*:\\s*\\{`).exec(source);
  if (!match) return null;
  const open = source.indexOf("{", match.index);
  const close = matchingBrace(source, open);
  return close > open ? source.slice(open + 1, close) : null;
}

function propertyFunctionBody(source, key) {
  const patterns = [
    new RegExp(`${key}\\s*\\([^)]*\\)\\s*\\{`),
    new RegExp(`${key}\\s*:\\s*(?:async\\s*)?function\\s*\\([^)]*\\)\\s*\\{`),
    new RegExp(`${key}\\s*:\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>\\s*\\{`)
  ];
  for (const pattern of patterns) {
    const match = pattern.exec(source);
    if (!match) continue;
    const open = source.indexOf("{", match.index);
    const close = matchingBrace(source, open);
    if (close > open) return source.slice(open + 1, close);
  }
  return null;
}

function functionProperties(source) {
  const functions = {};
  const regexes = [
    /(?:async\s+)?([A-Za-z_$][\w$]*)\s*\([^)]*\)\s*\{/g,
    /([A-Za-z_$][\w$]*)\s*:\s*(?:async\s*)?function\s*\([^)]*\)\s*\{/g,
    /([A-Za-z_$][\w$]*)\s*:\s*(?:async\s*)?\([^)]*\)\s*=>\s*\{/g
  ];
  for (const regex of regexes) {
    for (const match of source.matchAll(regex)) {
      const open = source.indexOf("{", match.index);
      const close = matchingBrace(source, open);
      if (close > open) {
        functions[match[1]] = source.slice(open + 1, close);
      }
    }
  }
  return functions;
}

function matchingBrace(source, open) {
  let depth = 0;
  let quote = null;
  for (let index = open; index < source.length; index += 1) {
    const char = source[index];
    const prev = source[index - 1];
    if (quote) {
      if (char === quote && prev !== "\\") quote = null;
      continue;
    }
    if (char === "'" || char === '"' || char === "`") {
      quote = char;
    } else if (char === "{") {
      depth += 1;
    } else if (char === "}") {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

function pascalToKebab(value) {
  return value.replace(/([a-z0-9])([A-Z])/g, "$1-$2").replace(/_/g, "-").toLowerCase();
}

function possibleTagNames(value) {
  return new Set([value, pascalToKebab(value), value.toLowerCase()]);
}

module.exports = {
  parseVueFile,
  possibleTagNames
};
