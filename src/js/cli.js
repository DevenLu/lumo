/* flow */

const lumo = require('./lumo');
const minimist = require('minimist');
const version = require('./version');

export type CLIOptsType = {
  _: string[],
  [key:string]: boolean | string,
};

function getClojureScriptVersionString(): string {
  return `ClojureScript ${lumo.load('clojurescript-version')}`;
}

function getVersionString(): string {
  return `Lumo ${version}`;
}

function printBanner(): void {
  process.stdout.write(`${getVersionString()}
${getClojureScriptVersionString()}
 Exit: Control+D or :cljs/quit
`);
}

function printHelp(): void {
  process.stdout.write(`${getVersionString()}
Usage:  lumo [init-opt*] [main-opt] [arg*]

  With no options or args, runs an interactive Read-Eval-Print Loop

  init options:
    -i, --init path          Load a file or resource
    -e, --eval string        Evaluate expressions in string; print non-nil values
    -c cp, --classpath cp    Use colon-delimited cp for source directories and
                             JARs
    -K, --auto-cache         Create and use .planck_cache dir for cache
    -k, --cache path         If dir exists at path, use it for cache
    -q, --quiet              Quiet mode; doesn't print the banner initially
    -v, --verbose            Emit verbose diagnostic output
    -d, --dumb-terminal      Disable line editing / VT100 terminal control

  main options:
    -m, --main ns-name       Call the -main function from a namespace with args
    -r, --repl               Run a repl
    path                     Run a script from a file or resource
    -                        Run a script from standard input
    -h, -?, --help           Print this help message and exit
    -l, --legal              Show legal info (licenses and copyrights)

  The init options may be repeated and mixed freely, but must appear before
  any main option.

  Paths may be absolute or relative in the filesystem.
`);
}

function getCLIOpts(): CLIOptsType {
  return minimist(process.argv.slice(2), {
    boolean: ['verbose', 'help', 'repl', 'auto-cache', 'quiet', 'dumb-terminal'],
    string: ['eval', 'cache', 'classpath'],
    alias: {
      c: 'classpath',
      v: 'verbose',
      h: 'help',
      '?': 'help',
      e: 'eval',
      r: 'repl',
      K: 'auto-cache',
      k: 'cache',
      q: 'quiet',
      d: 'dumb-terminal',
    },
  });
}

module.exports = {
  getCLIOpts,
  printHelp,
  getVersionString,
  getClojureScriptVersionString,
  printBanner,
};
