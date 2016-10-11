/* @flow */

import type { CLIOptsType } from './cli';

const vm = require('vm');
const lumo = require('./lumo');

const cljsSrc = lumo.load('main.js');
// $FlowFixMe: we know for sure this file will exist.
const cljsScript = new vm.Script(cljsSrc, {});
const defaultCachePath = '.lumo_cache';

function newContext() {
  const context: Object = {
    module,
    require,
    process,
    console,
    LUMO_LOAD: lumo.load,
    LUMO_READ_CACHE: lumo.readCache,
    LUMO_READ_SOURCE: lumo.readSource,
  };

  context.global = context;
  const ctx = vm.createContext(context);
  cljsScript.runInContext(ctx);
  return ctx;
}

const defaultContext = newContext();

function evalInContext(code: string) {
  if (/\S/.test(code)) {
    // $FlowIssue: context can have globals
    defaultContext.lumo.repl.eval_source(code);
  }
}

function getCurrentNS(): string {
  // $FlowIssue: context can have globals
  return defaultContext.lumo.repl.get_current_ns();
}

function setRuntimeOpts(opts: CLIOptsType) {
  const autoCache = opts['auto-cache'];
  const { verbose, cache } = opts;
  const cachePath = cache || (autoCache ? defaultCachePath : null);

  // $FlowIssue: context can have globals
  defaultContext.lumo.repl.init(verbose, cachePath);
}

module.exports = {
  eval: evalInContext,
  currentNS: getCurrentNS,
  setRuntimeOpts,
};
