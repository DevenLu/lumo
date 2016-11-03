/* @flow */

import startCLI, * as cli from '../cli';
import cljs from '../cljs';

jest.mock('../cljs');
jest.mock('../version', () => 'X.X.X');
jest.mock('../lumo', () => ({
  load: jest.fn((path: string) => path), // eslint-disable-line arrow-parens
}));

const originalArgv = process.argv;
const originalStdoutWrite = process.stdout.write;

beforeEach(() => {
  process.stdout.write = jest.fn();
});

afterEach(() => {
  process.stdout.write = originalStdoutWrite;
});

afterEach(() => {
  Object.defineProperty(process, 'argv', {
    value: originalArgv,
  });
  jest.resetModules();
  cljs.mockClear();
});

describe('getCliOpts', () => {
  it('parses single dash properties when they appear together', () => {
    Object.defineProperty(process, 'argv', {
      value: ['', '', '-vK'],
    });
    startCLI();
    const [[parsedOpts]] = cljs.mock.calls;

    expect(parsedOpts.verbose).toBe(true);
    expect(parsedOpts.v).toBe(true);
    expect(parsedOpts.K).toBe(true);
    expect(parsedOpts['auto-cache']).toBe(true);
  });

  it('parses single dash properties when they appear together', () => {
    const args = '-e :foo -e :bar';
    Object.defineProperty(process, 'argv', {
      value: ['', ''].concat(args.split(' ')),
    });

    startCLI();
    const [[parsedOpts]] = cljs.mock.calls;

    expect(parsedOpts.verbose).toBe(false);
    expect(parsedOpts.eval).toBeInstanceOf(Array);
    expect(parsedOpts.eval).toEqual([':foo', ':bar']);
    expect(parsedOpts.e).toEqual(parsedOpts.eval);
  });
});

describe('print Functions', () => {
  describe('printBanner', () => {
    it('should print the banner to stdout', () => {
      Object.defineProperty(process, 'argv', {
        value: ['', ''],
      });

      startCLI();
      expect(process.stdout.write).toBeCalledWith(`Lumo X.X.X
ClojureScript clojurescript-version
 Exit: Control+D or :cljs/quit or exit
`);
    });
  });

  describe('printHelp', () => {
    it('should print the help text to stdout', () => {
      Object.defineProperty(process, 'argv', {
        value: ['', '', '-h'],
      });

      startCLI();
      expect(process.stdout.write).toBeCalledWith(`Lumo X.X.X
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
    -s, --static-fns         Generate static dispatch function calls

  main options:
    -r, --repl               Run a repl
    path                     Run a script from a file or resource
    -h, -?, --help           Print this help message and exit
    -l, --legal              Show legal info (licenses and copyrights)

  The init options may be repeated and mixed freely, but must appear before
  any main option.

  Paths may be absolute or relative in the filesystem.
`);
    });
  });
});
