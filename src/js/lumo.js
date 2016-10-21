/* @flow */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const JSZip = require('jszip');

let nexeres;
let sourcePaths = [''];

if (!__DEV__) {
  // $FlowExpectedError: only exists in the Nexe bundle.
  nexeres = require('nexeres'); // eslint-disable-line
}

export function load(filename: string): ?string {
  try {
    if (__DEV__) {
      return fs.readFileSync(`./target/${filename}`, 'utf8');
    }
    const gzipped = nexeres.get(filename);

    return zlib.inflateSync(gzipped).toString();
  } catch (_) {
    return null;
  }
}

// TODO: cache JARs that we know have a given file / path
export function readSource(filename: string): ?string {
  for (const srcPath of sourcePaths) {
    try {
      if (srcPath.endsWith('.jar')) {
        const data = fs.readFileSync(srcPath);
        const zip = new JSZip().load(data);
        const source = zip.file(filename);

        if (source != null) {
          return source.asText();
        }
      }

      return fs.readFileSync(path.join(srcPath, filename), 'utf8');
    } catch (_) {} // eslint-disable-line no-empty
  }
  return null;
}

export function readCache(filename: string): ?string {
  try {
    return fs.readFileSync(filename, 'utf8');
  } catch (_) {
    return null;
  }
}

export function writeCache(filename: string, source: string): ?Error {
  try {
    return fs.writeFileSync(filename, source, 'utf8');
  } catch (e) {
    return e;
  }
}

export function loadUpstreamForeignLibs(): string[] {
  const ret = [];

  for (const srcPath of sourcePaths) {
    if (srcPath.endsWith('.jar')) {
      const data = fs.readFileSync(srcPath);
      const zip = new JSZip().load(data);
      const source = zip.file('deps.cljs');

      if (source != null) {
        ret.push(source.asText());
      }
    }
  }
  return ret;
}

export function addSourcePaths(srcPaths: string[]): void {
  sourcePaths.push(...srcPaths);
}
