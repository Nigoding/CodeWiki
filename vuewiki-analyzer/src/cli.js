#!/usr/bin/env node
"use strict";

const { analyze } = require("./index");

function main(argv) {
  const [command, ...rest] = argv;
  if (!command || command === "--help" || command === "-h") {
    printHelp();
    return;
  }
  if (command !== "analyze") {
    fail(`Unknown command: ${command}`);
  }

  const parsed = parseAnalyzeArgs(rest);
  analyze(parsed)
    .then((outputDir) => {
      console.log("Analysis complete");
      console.log(`Output: ${outputDir}`);
    })
    .catch((error) => {
      console.error(`Analysis failed: ${error.message}`);
      if (parsed.verbose) {
        console.error(error.stack);
      }
      process.exit(1);
    });
}

function parseAnalyzeArgs(args) {
  let source = null;
  const options = {
    output: ".frontend-analysis",
    keepClone: false,
    submodules: "auto",
    verbose: false
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--help" || arg === "-h") {
      printAnalyzeHelp();
      process.exit(0);
    } else if (arg === "--output" || arg === "-o") {
      options.output = requireValue(args, ++index, arg);
    } else if (arg === "--keep-clone") {
      options.keepClone = true;
    } else if (arg === "--submodules") {
      options.submodules = requireValue(args, ++index, arg);
      if (!["none", "auto", "recursive"].includes(options.submodules)) {
        fail("--submodules must be one of: none, auto, recursive");
      }
    } else if (arg === "--verbose" || arg === "-v") {
      options.verbose = true;
    } else if (arg.startsWith("-")) {
      fail(`Unknown option: ${arg}`);
    } else if (!source) {
      source = arg;
    } else {
      fail(`Unexpected argument: ${arg}`);
    }
  }

  if (!source) {
    fail("Missing source path or Git URL.");
  }
  return { source, ...options };
}

function requireValue(args, index, option) {
  if (index >= args.length || args[index].startsWith("-")) {
    fail(`Missing value for ${option}`);
  }
  return args[index];
}

function printHelp() {
  console.log(`Usage:
  vuewiki analyze <repo-path-or-git-url> -o .frontend-analysis

Commands:
  analyze    Analyze a Vue2 frontend repository
`);
}

function printAnalyzeHelp() {
  console.log(`Usage:
  vuewiki analyze [options] <repo-path-or-git-url>

Options:
  -o, --output <dir>          Output directory. Default: .frontend-analysis
  --keep-clone                Keep cloned remote repository under <output>/_source
  --submodules <mode>         Git submodule mode: none, auto, recursive. Default: auto
  -v, --verbose               Print progress
  -h, --help                  Show help
`);
}

function fail(message) {
  console.error(message);
  process.exit(2);
}

main(process.argv.slice(2));
