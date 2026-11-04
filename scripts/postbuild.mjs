#!/usr/bin/env node
/**
 * Post-build pipeline.
 *
 * 1. Generate the Pagefind full-text index over the static output.
 * 2. Report a short build summary.
 *
 * Pagefind must run after `astro build` because it consumes the rendered HTML.
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const dist = path.join(root, 'dist');

if (!fs.existsSync(dist)) {
  console.error('[postbuild] dist/ is missing. Run `astro build` first.');
  process.exit(1);
}

const binary = path.join(
  root,
  'node_modules',
  '.bin',
  process.platform === 'win32' ? 'pagefind.cmd' : 'pagefind'
);

if (!fs.existsSync(binary)) {
  console.warn(
    '[postbuild] Pagefind binary not found. Install it with `npm install pagefind --save-dev`.'
  );
} else {
  console.log('[postbuild] Building Pagefind index…');
  const result =
    process.platform === 'win32'
      ? spawnSync('cmd.exe', ['/c', 'npx pagefind --site dist --force-language en'], {
          stdio: 'inherit',
          cwd: root,
        })
      : spawnSync(binary, ['--site', 'dist', '--force-language', 'en'], {
          stdio: 'inherit',
          cwd: root,
        });

  if (result.status !== 0) {
    console.error('[postbuild] Pagefind failed.');
    process.exit(result.status ?? 1);
  }
}

function countFiles(dir, extension) {
  let total = 0;
  const walk = (current) => {
    for (const item of fs.readdirSync(current, { withFileTypes: true })) {
      const next = path.join(current, item.name);
      if (item.isDirectory()) walk(next);
      else if (item.name.endsWith(extension)) total += 1;
    }
  };
  walk(dir);
  return total;
}

const pages = countFiles(dist, '.html');

function readIndexedPageCount() {
  try {
    const entry = JSON.parse(
      fs.readFileSync(path.join(dist, 'pagefind', 'pagefind-entry.json'), 'utf-8')
    );
    return Object.values(entry.languages ?? {}).reduce(
      (total, language) => total + (language.page_count ?? 0),
      0
    );
  } catch {
    return 0;
  }
}

const indexedPages = readIndexedPageCount();

console.log('');
console.log('[postbuild] Done.');
console.log(`[postbuild] HTML pages: ${pages}`);
console.log(
  indexedPages > 0
    ? `[postbuild] Pagefind indexed pages: ${indexedPages}`
    : '[postbuild] Pagefind index not generated (search will fall back to the command palette).'
);
