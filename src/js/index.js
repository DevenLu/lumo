/* @flow */

import * as cli from './cli';
import * as lumo from './lumo';
import startREPL from './repl';
import * as util from './util';

import type { CLIOptsType } from './cli';

function processRuntimeOpts(opts: CLIOptsType): void {
  const { help, quiet, classpath } = opts;

  // if help, print help and bail
  if (help) {
    return cli.printHelp();
  }

  if (!quiet) {
    cli.printBanner();
  }

  // TODO: print classpath to stdout if `:verbose`
  if (classpath != null) {
    const cp = Array.isArray(classpath) ? classpath : [classpath];
    const srcPaths = util.srcPathsFromClasspathStrings(cp);

    lumo.setSourcePaths(srcPaths);
  }

  return startREPL(opts);
}

function main(): void {
  const cliOpts = cli.getCLIOpts();

  processRuntimeOpts(cliOpts);
}

main();
