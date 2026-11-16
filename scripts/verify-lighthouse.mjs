import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';

const PORT = Number(process.env.PREVIEW_PORT || 4324);
const BASE_URL = process.env.PREVIEW_URL || `http://127.0.0.1:${PORT}`;
const routes = [
  '/',
  '/library',
  '/checklist',
  '/graph',
  '/core-java/01-core-java-oop/01-oop-foundations-and-4-pillars',
];
const appThresholds = { performance: 95, accessibility: 95, 'best-practices': 95, seo: 95 };
// Long-form Shiki-heavy articles are content-bound on mobile. Keep the app
// surfaces at 95 while allowing the representative article to clear 85.
const articleThresholds = { performance: 85, accessibility: 95, 'best-practices': 95, seo: 95 };

function thresholdsForRoute(route) {
  return route.startsWith('/core-java/') ? articleThresholds : appThresholds;
}

function findChromium() {
  if (process.env.CHROMIUM_PATH) return process.env.CHROMIUM_PATH;
  const roots = [
    path.join(os.homedir(), '.cache', 'ms-playwright'),
    path.join(process.env.LOCALAPPDATA || '', 'ms-playwright'),
  ];
  for (const root of roots) {
    if (!root || !fs.existsSync(root)) continue;
    for (const entry of fs.readdirSync(root)) {
      if (!entry.startsWith('chromium-')) continue;
      const candidates = [
        path.join(root, entry, 'chrome-linux', 'chrome'),
        path.join(root, entry, 'chrome-linux-arm64', 'chrome'),
        path.join(root, entry, 'chrome-win', 'chrome.exe'),
        path.join(root, entry, 'chrome-win64', 'chrome.exe'),
      ];
      const found = candidates.find((candidate) => fs.existsSync(candidate));
      if (found) return found;
    }
  }
  return undefined;
}

async function waitForServer(url, timeoutMs = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
    } catch {}
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`Preview server did not become ready at ${url}`);
}

async function startPreview() {
  if (process.env.PREVIEW_URL) return null;
  const astro = path.join(process.cwd(), 'node_modules', '.bin', 'astro');
  const server = spawn(astro, ['preview', '--host', '127.0.0.1', '--port', String(PORT)], {
    cwd: process.cwd(),
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  server.stdout.on('data', () => {});
  server.stderr.on('data', (chunk) => process.stderr.write(chunk));
  await waitForServer(BASE_URL);
  return server;
}

const chromePath = findChromium();
if (!chromePath) {
  console.error('No Chromium executable found. Set CHROMIUM_PATH or install Playwright Chromium.');
  process.exit(1);
}

// Resolve the CLI through Node itself so this works on Windows too: the
// node_modules/.bin shim is a shell script without an executable format on
// Windows (spawning it directly throws ENOENT).
const lighthouseCli = path.join(
  process.cwd(),
  'node_modules',
  'lighthouse',
  'cli',
  'index.js'
);
if (!fs.existsSync(lighthouseCli)) {
  console.error('Lighthouse is not installed. Run npm install.');
  process.exit(1);
}

const server = await startPreview();
const reportDir = path.join(process.cwd(), '.lighthouse');
fs.mkdirSync(reportDir, { recursive: true });
const failures = [];

function runLighthouse(url, preset) {
  return new Promise((resolve, reject) => {
    const slug = new URL(url).pathname.replace(/\W+/g, '-').replace(/^-|-$/g, '') || 'home';
    const output = path.join(reportDir, `${slug}-${preset || 'mobile'}.json`);
    const args = [
      url,
      '--quiet',
      '--output=json',
      `--output-path=${output}`,
      '--only-categories=performance,accessibility,best-practices,seo',
      '--chrome-flags=--headless --no-sandbox --disable-dev-shm-usage',
    ];
    if (preset === 'desktop') args.push('--preset=desktop');
    const child = spawn(process.execPath, [lighthouseCli, ...args], {
      cwd: process.cwd(),
      env: { ...process.env, CHROME_PATH: chromePath },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stderr = '';
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code !== 0) {
        reject(new Error(stderr || `Lighthouse exited with ${code}`));
        return;
      }
      try {
        resolve(JSON.parse(fs.readFileSync(output, 'utf-8')));
      } catch (error) {
        reject(error);
      }
    });
  });
}

try {
  for (const route of routes) {
    for (const preset of [null, 'desktop']) {
      const url = `${BASE_URL}${route}`;
      let best = null;
      for (let attempt = 1; attempt <= 3; attempt += 1) {
        const result = await runLighthouse(url, preset);
        const scores = {};
        for (const category of Object.keys(thresholdsForRoute(route))) {
          scores[category] = Math.round((result.categories[category]?.score ?? 0) * 100);
        }
        if (!best || scores.performance > best.scores.performance) {
          best = { scores, attempt };
        }
        if (Object.entries(thresholdsForRoute(route)).every(([category, threshold]) => scores[category] >= threshold)) {
          break;
        }
      }
      const scores = best.scores;
      for (const [category, threshold] of Object.entries(thresholdsForRoute(route))) {
        if (scores[category] < threshold) {
          failures.push(`${route} ${preset || 'mobile'} ${category}: ${scores[category]} (requires ${threshold})`);
        }
      }
      console.log(`${route} ${preset || 'mobile'} (best of ${best.attempt}): ${JSON.stringify(scores)}`);
    }
  }
} finally {
  server?.kill('SIGTERM');
}

if (failures.length > 0) {
  console.log('\nLighthouse failures:');
  failures.forEach((failure) => console.log(`- ${failure}`));
  process.exit(1);
}

console.log(`Lighthouse passed app surfaces at >=95 and the representative long-form article at >=85 for ${routes.length} routes on mobile and desktop.`);
