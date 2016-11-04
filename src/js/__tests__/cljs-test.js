/* @flow */

import startCLJS, * as cljs from '../cljs';
import startREPL from '../repl';

const vm = require('vm');

jest.mock('../repl');

jest.mock('../lumo', () => ({
  load: () => '',
}));

jest.mock('vm');

const ctx = {
  cljs: {
    nodejs: {
      enable_util_print_BANG_: () => {},
    },
  },
  lumo: {
    repl: {
      init: () => {},
      set_ns: () => {},
      execute: () => {},
      is_readable_QMARK_: () => true,
      get_current_ns: () => 'cljs.user',
    },
  },
};

vm.createContext.mockImplementation(() => ctx);

describe('startClojureScriptEngine', () => {
  const nextTick = process.nextTick;

  beforeEach(() => {
    startREPL.mockClear();
  });

  it('should start a REPL if opts.repl is true', () => {
    startCLJS({
      repl: true,
      _: [],
      scripts: [],
    });

    expect(startREPL).toHaveBeenCalled();
  });

  it('returns undefined if opts.repl is false', () => {
    const ret = startCLJS({
      repl: false,
      _: [],
      scripts: [],
    });

    expect(startREPL).not.toHaveBeenCalled();
    expect(ret).toBeUndefined();

    startREPL.mockClear();

    const ret2 = startCLJS({
      repl: false,
      _: [],
      scripts: [['text', ':foo'], ['path', 'foo.cljs']],
    });

    expect(startREPL).not.toHaveBeenCalled();
    expect(ret2).toBeUndefined();
  });

  it('calls `executeScript` and bails if there\'s a main opt', () => {
    startCLJS({
      repl: false,
      _: ['foo.cljs'],
      scripts: [],
    });

    expect(startREPL).not.toHaveBeenCalled();
  });

  it('doesn\'t init the CLJS engine if it already started', () => {
    startCLJS({
      repl: true,
      _: [],
      // scripts will init the ClojureScript engine
      scripts: [['text', ':foo'], ['path', 'foo.cljs']],
    });

    expect(startREPL).toHaveBeenCalled();
  });

  describe('in development', () => {
    beforeEach(() => {
      vm.createContext.mockClear();
      // eslint-disable-next-line arrow-parens
      process.nextTick = jest.fn((f: Function) => f());
    });

    afterEach(() => {
      process.nextTick = nextTick;
    });

    it('creates and returns a vm context', () => {
      startCLJS({
        repl: true,
        _: [],
        scripts: [],
      });

      expect(vm.createContext).toHaveBeenCalled();
      expect(vm.createContext.mock.calls.length).toBe(1);
    });
  });

  describe('in production', () => {
    let startClojureScriptEngine;

    beforeEach(() => {
      jest.resetModules();
      // eslint-disable-next-line arrow-parens
      process.nextTick = jest.fn((f: Function) => f());

      Object.assign(global, {
        initialize: jest.fn(),
        __DEV__: false,
      }, ctx);
      // eslint-disable-next-line global-require
      startClojureScriptEngine = require('../cljs').default;
    });

    afterEach(() => {
      for (const key of Object.keys(ctx).concat(['initialize'])) {
        Reflect.deleteProperty(global, key);
      }
      __DEV__ = true;
      process.nextTick = nextTick;
    });

    it('calls the global initialize function', () => {
      startClojureScriptEngine({
        repl: true,
        _: [],
        scripts: [],
      });

      // eslint-disable-next-line no-undef
      expect(initialize).toHaveBeenCalled();
    });
  });
});

describe('isReadable', () => {
  it('calls into the CLJS context', () => {
    expect(cljs.isReadable('()')).toBe(true);
  });
});

describe('getCurrentNamespace', () => {
  it('calls into the CLJS context', () => {
    expect(cljs.getCurrentNamespace()).toBe('cljs.user');
  });
});
