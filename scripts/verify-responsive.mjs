import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { chromium } from 'playwright-core';

const PORT = Number(process.env.PREVIEW_PORT || 4323);
const BASE_URL = process.env.PREVIEW_URL || `http://127.0.0.1:${PORT}`;
const routes = [
  '/',
  '/library',
  '/paths',
  '/checklist',
  '/graph',
  '/glossary',
  '/search',
  '/tags',
  '/index-all',
  '/404',
  '/core-java/01-core-java-oop/01-oop-foundations-and-4-pillars',
];
const viewports = [
  { width: 320, height: 568 },
  { width: 390, height: 844 },
  { width: 768, height: 1024 },
  { width: 1024, height: 768 },
  { width: 1440, height: 900 },
  { width: 1920, height: 1080 },
];

function findChromium() {
  if (process.env.CHROMIUM_PATH) return process.env.CHROMIUM_PATH;
  const roots = [
    path.join(os.homedir(), '.cache', 'ms-playwright'),
    path.join(os.homedir(), '.cache', 'chromium'),
  ];
  for (const root of roots) {
    if (!fs.existsSync(root)) continue;
    for (const entry of fs.readdirSync(root)) {
      if (!entry.startsWith('chromium-')) continue;
      const candidates = [
        path.join(root, entry, 'chrome-linux', 'chrome'),
        path.join(root, entry, 'chrome-linux-arm64', 'chrome'),
        path.join(root, entry, 'chrome-mac', 'Chromium.app', 'Contents', 'MacOS', 'Chromium'),
        path.join(root, entry, 'chrome-win', 'chrome.exe'),
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

const failures = [];
const consoleErrors = [];
const axePath = path.join(process.cwd(), 'node_modules', 'axe-core', 'axe.min.js');

const server = await startPreview();
const executablePath = findChromium();
if (!executablePath) {
  console.error('No Chromium executable found. Set CHROMIUM_PATH or install Playwright Chromium.');
  server?.kill('SIGTERM');
  process.exit(1);
}

const browser = await chromium.launch({
  executablePath,
  headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'],
});

async function checkPage(page, route, viewport) {
  const url = `${BASE_URL}${route}`;
  const pageErrors = [];
  const onConsole = (message) => {
    if (message.type() === 'error') pageErrors.push(message.text());
  };
  const onPageError = (error) => pageErrors.push(error.message);
  page.on('console', onConsole);
  page.on('pageerror', onPageError);
  try {
    await page.setViewportSize(viewport);
    await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(150);
    const metrics = await page.evaluate(() => ({
      viewport: window.innerWidth,
      documentWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
    }));
    if (metrics.documentWidth > metrics.viewport + 1 || metrics.bodyWidth > metrics.viewport + 1) {
      failures.push(
        `${route} at ${viewport.width}px overflows: document=${metrics.documentWidth}, body=${metrics.bodyWidth}, viewport=${metrics.viewport}`
      );
      const screenshot = path.join('/tmp', `responsive-${route.replace(/\W+/g, '-')}-${viewport.width}.png`);
      await page.screenshot({ path: screenshot, fullPage: true });
      failures.push(`screenshot: ${screenshot}`);
    }
    if (pageErrors.length > 0) {
      consoleErrors.push(`${route} at ${viewport.width}px: ${pageErrors.join(' | ')}`);
    }
    if (fs.existsSync(axePath)) {
      await page.addScriptTag({ path: axePath });
      const results = await page.evaluate(async () => {
        // eslint-disable-next-line no-undef
        return await axe.run(document, {
          runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
        });
      });
      const serious = results.violations.filter((violation) =>
        ['serious', 'critical'].includes(violation.impact)
      );
      for (const violation of serious) {
        failures.push(
          `${route} at ${viewport.width}px axe ${violation.impact}: ${violation.id} (${violation.nodes.length} nodes)`
        );
      }
    }
  } catch (error) {
    failures.push(`${route} at ${viewport.width}px failed: ${error.message}`);
  } finally {
    page.off('console', onConsole);
    page.off('pageerror', onPageError);
  }
}

try {
  const page = await browser.newPage();
  for (const route of routes) {
    for (const viewport of viewports) {
      await checkPage(page, route, viewport);
    }
  }

  // Command palette focus trap and Escape restore.
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle' });
  await page.click('#open-command-palette-btn');
  await page.waitForSelector('[role="dialog"][aria-label="Raycast command center"]');
  await page.waitForTimeout(150);
  for (let index = 0; index < 6; index += 1) await page.keyboard.press('Tab');
  const paletteFocusInside = await page.evaluate(() => {
    const dialog = document.querySelector('[role="dialog"][aria-label="Raycast command center"]');
    return Boolean(dialog && dialog.contains(document.activeElement));
  });
  if (!paletteFocusInside) failures.push('Command palette focus escaped the dialog');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(100);
  const paletteClosed = await page.evaluate(() => !document.querySelector('[role="dialog"][aria-label="Raycast command center"]'));
  if (!paletteClosed) failures.push('Command palette did not close on Escape');

  // Mobile drawer focus trap and Escape restore.
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle' });
  await page.click('#mobile-menu-btn');
  await page.waitForSelector('#mobile-drawer:not(.hidden)');
  await page.waitForTimeout(150);
  for (let index = 0; index < 8; index += 1) await page.keyboard.press('Tab');
  const drawerFocusInside = await page.evaluate(() => {
    const drawer = document.getElementById('mobile-drawer');
    return Boolean(drawer && drawer.contains(document.activeElement));
  });
  if (!drawerFocusInside) failures.push('Mobile drawer focus escaped the dialog');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(400);
  const drawerClosed = await page.evaluate(() => document.getElementById('mobile-drawer')?.classList.contains('hidden'));
  if (!drawerClosed) failures.push('Mobile drawer did not close on Escape');

  // Table sorting smoke test on a content page.
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto(`${BASE_URL}/core-java/01-core-java-oop/01-oop-foundations-and-4-pillars`, {
    waitUntil: 'networkidle',
  });
  const firstTable = page.locator('.obsidian-prose table').first();
  if ((await firstTable.count()) > 0) {
    await firstTable.scrollIntoViewIfNeeded();
    await page.waitForTimeout(250);
  }
  const sortButton = page.locator('.table-sort-btn').first();
  if ((await sortButton.count()) > 0) {
    await sortButton.click();
    const sortState = await page.evaluate(() => {
      const header = document.querySelector('.obsidian-prose th[aria-sort]');
      return header?.getAttribute('aria-sort');
    });
    if (!['ascending', 'descending'].includes(sortState || '')) {
      failures.push('Table sorting did not update aria-sort');
    }
  }
} finally {
  await browser.close();
  server?.kill('SIGTERM');
}

if (consoleErrors.length > 0) {
  console.log('\nConsole errors:');
  consoleErrors.forEach((error) => console.log(`- ${error}`));
}

if (failures.length > 0) {
  console.log('\nResponsive, accessibility, or interaction failures:');
  failures.forEach((failure) => console.log(`- ${failure}`));
  process.exit(1);
}

console.log(`Responsive verification passed across ${routes.length} routes and ${viewports.length} viewports.`);
