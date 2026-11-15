import fs from 'node:fs';
import path from 'node:path';

function hexToRgb(hex) {
  let c = hex.replace('#', '');
  if (c.length === 3) c = c.split('').map((x) => x + x).join('');
  const num = parseInt(c, 16);
  return { r: (num >> 16) & 255, g: (num >> 8) & 255, b: num & 255 };
}

function relativeLuminance({ r, g, b }) {
  const [rs, gs, bs] = [r, g, b].map((v) => {
    const s = v / 255;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

function contrastRatio(hex1, hex2) {
  const l1 = relativeLuminance(hexToRgb(hex1));
  const l2 = relativeLuminance(hexToRgb(hex2));
  return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
}

function composite(hex, alpha, background) {
  const fg = hexToRgb(hex);
  const bg = hexToRgb(background);
  const channel = (value, base) => Math.round(value * alpha + base * (1 - alpha));
  return `#${[channel(fg.r, bg.r), channel(fg.g, bg.g), channel(fg.b, bg.b)]
    .map((value) => value.toString(16).padStart(2, '0'))
    .join('')}`;
}

const failures = [];
const warnings = [];

function requireContrast(label, foreground, background, minimum = 4.5) {
  const ratio = contrastRatio(foreground, background);
  if (ratio < minimum) {
    failures.push(`${label}: ${ratio.toFixed(2)}:1 (requires ${minimum}:1)`);
  }
  return ratio;
}

console.log('=== WCAG Contrast Verification ===\n');

const dark = {
  canvas: '#09090b',
  canvasSoft: '#121216',
  surface: '#18181f',
  hover: '#22222d',
  primary: '#f8fafc',
  secondary: '#b4b4c4',
  muted: '#8f8ea1',
  accent: '#a78bfa',
  accentHover: '#c4b5fd',
  accentSolid: '#7c3aed',
  accentSolidHover: '#6d28d9',
  successText: '#34d399',
  warningText: '#fbbf24',
  errorText: '#f87171',
  infoText: '#60a5fa',
  successSolid: '#047857',
  warningSolid: '#b45309',
  errorSolid: '#b91c1c',
  infoSolid: '#1d4ed8',
};

const light = {
  canvas: '#ffffff',
  canvasSoft: '#f8fafc',
  surface: '#ffffff',
  hover: '#f1f5f9',
  primary: '#09090b',
  secondary: '#334155',
  muted: '#5f6b7a',
  accent: '#6d28d9',
  accentHover: '#5b21b6',
  accentSolid: '#6d28d9',
  accentSolidHover: '#5b21b6',
  successText: '#065f46',
  warningText: '#92400e',
  errorText: '#991b1b',
  infoText: '#1e40af',
  successSolid: '#047857',
  warningSolid: '#b45309',
  errorSolid: '#b91c1c',
  infoSolid: '#1d4ed8',
};

for (const [themeName, theme] of Object.entries({ dark, light })) {
  console.log(`--- ${themeName[0].toUpperCase() + themeName.slice(1)} Mode ---`);
  for (const surfaceName of ['canvas', 'canvasSoft', 'surface', 'hover']) {
    for (const textName of ['primary', 'secondary', 'muted', 'accent', 'accentHover']) {
      const ratio = requireContrast(
        `${themeName} ${textName} on ${surfaceName}`,
        theme[textName],
        theme[surfaceName]
      );
      console.log(`[${themeName}] ${textName} on ${surfaceName}: ${ratio.toFixed(2)}:1`);
    }
  }
  for (const solidName of ['accentSolid', 'accentSolidHover', 'successSolid', 'warningSolid', 'errorSolid', 'infoSolid']) {
    const ratio = requireContrast(`${themeName} white on ${solidName}`, '#ffffff', theme[solidName]);
    console.log(`[${themeName}] white on ${solidName}: ${ratio.toFixed(2)}:1`);
  }
}

const domainPairs = {
  dark: {
    'core-java': ['#fbbf24', '#f59e0b', 0.12],
    'spring-boot': ['#4ade80', '#22c55e', 0.12],
    'system-design': ['#93c5fd', '#60a5fa', 0.12],
    'database-caching': ['#5eead4', '#2dd4bf', 0.12],
    'java-dsa': ['#fda4af', '#fb7185', 0.12],
    'cloud-devops': ['#7dd3fc', '#38bdf8', 0.12],
    'docker-java-backend': ['#a5b4fc', '#818cf8', 0.12],
    root: ['#c4b5fd', '#a78bfa', 0.12],
  },
  light: {
    'core-java': ['#92400e', '#f59e0b', 0.14],
    'spring-boot': ['#166534', '#22c55e', 0.12],
    'system-design': ['#1e40af', '#60a5fa', 0.12],
    'database-caching': ['#115e59', '#2dd4bf', 0.12],
    'java-dsa': ['#9f1239', '#fb7185', 0.12],
    'cloud-devops': ['#075985', '#38bdf8', 0.12],
    'docker-java-backend': ['#3730a3', '#818cf8', 0.12],
    root: ['#5b21b6', '#a78bfa', 0.12],
  },
};

for (const [themeName, entries] of Object.entries(domainPairs)) {
  const base = themeName === 'dark' ? '#18181f' : '#ffffff';
  for (const [domain, [text, tint, alpha]] of Object.entries(entries)) {
    const background = composite(tint, alpha, base);
    requireContrast(`${themeName} domain ${domain}`, text, background);
  }
}

if (failures.length === 0) {
  console.log('\nAll required text contrast pairs pass 4.5:1.\n');
} else {
  console.log('\nContrast failures:');
  failures.forEach((failure) => console.log(`- ${failure}`));
  console.log('');
}

console.log('=== Dist HTML Accessibility & Standards Verification ===\n');

const distDir = path.join(process.cwd(), 'dist');
if (!fs.existsSync(distDir)) {
  console.error('Dist directory does not exist! Build first.');
  process.exit(1);
}

function findHtmlFiles(dir) {
  const results = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) results.push(...findHtmlFiles(fullPath));
    else if (entry.name.endsWith('.html')) results.push(fullPath);
  }
  return results;
}

const htmlFiles = findHtmlFiles(distDir);
console.log(`Checking ${htmlFiles.length} generated HTML files...`);

let missingLangCount = 0;
let missingViewportCount = 0;
let missingSkipLinkCount = 0;
let buttonWithoutAriaOrText = 0;
let imgWithoutAlt = 0;
let duplicateIdPages = 0;
let multipleH1Pages = 0;
let unlabeledInputs = 0;
let missingAriaControls = 0;
let missingImageDimensions = 0;

for (const file of htmlFiles) {
  const content = fs.readFileSync(file, 'utf-8');
  if (!/<html[^>]*lang=["'][a-zA-Z-]+["']/i.test(content)) missingLangCount++;
  if (!/<meta[^>]*name=["']viewport["']/i.test(content)) missingViewportCount++;
  if (content.includes('<nav') && !content.includes('Skip to content') && !content.includes('Skip to main')) missingSkipLinkCount++;

  const ids = [...content.matchAll(/\sid=["']([^"']+)["']/g)].map((match) => match[1]);
  const seen = new Set();
  const duplicates = new Set();
  for (const id of ids) {
    if (seen.has(id)) duplicates.add(id);
    seen.add(id);
  }
  if (duplicates.size > 0) duplicateIdPages++;
  if ((content.match(/<h1\b/gi) || []).length > 1) multipleH1Pages++;

  for (const match of content.matchAll(/<button\b[^>]*>([\s\S]*?)<\/button>/gi)) {
    const button = match[0];
    const hasAria = /aria-label=["'][^"']+["']|aria-labelledby=["'][^"']+["']/i.test(button);
    const innerText = button.replace(/<[^>]+>/g, '').trim();
    if (!hasAria && innerText.length === 0) buttonWithoutAriaOrText++;
  }

  for (const match of content.matchAll(/<img\b[^>]*>/gi)) {
    const img = match[0];
    if (!/\balt=["'][^"']*["']/i.test(img)) imgWithoutAlt++;
    if (!/\bwidth=/.test(img) || !/\bheight=/.test(img)) missingImageDimensions++;
  }

  for (const match of content.matchAll(/<input\b([^>]*)>/gi)) {
    const attrs = match[1];
    const type = (attrs.match(/type=["']([^"']+)["']/) || [])[1] || 'text';
    if (['hidden', 'submit', 'button', 'reset'].includes(type)) continue;
    const id = (attrs.match(/\bid=["']([^"']+)["']/) || [])[1];
    const hasAria = /aria-label|aria-labelledby/.test(attrs);
    const hasLabel = id ? new RegExp(`<label[^>]*for=["']${id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}["']`).test(content) : false;
    if (!hasAria && !hasLabel) unlabeledInputs++;
  }

  for (const match of content.matchAll(/aria-controls=["']([^"']+)["']/g)) {
    if (!ids.includes(match[1])) missingAriaControls++;
  }
}

console.log(`Missing lang attribute: ${missingLangCount}`);
console.log(`Missing viewport meta tag: ${missingViewportCount}`);
console.log(`Missing skip-to-content on nav pages: ${missingSkipLinkCount}`);
console.log(`Buttons without text or aria-label: ${buttonWithoutAriaOrText}`);
console.log(`Images without alt attribute: ${imgWithoutAlt}`);
console.log(`Pages with duplicate IDs: ${duplicateIdPages}`);
console.log(`Pages with multiple H1s: ${multipleH1Pages}`);
console.log(`Unlabeled inputs: ${unlabeledInputs}`);
console.log(`Missing aria-controls targets: ${missingAriaControls}`);
console.log(`Images missing width/height: ${missingImageDimensions}`);

const staticFailures = [];
for (const [label, value] of Object.entries({
  'missing lang': missingLangCount,
  'missing viewport': missingViewportCount,
  'missing skip link': missingSkipLinkCount,
  'button without label': buttonWithoutAriaOrText,
  'image without alt': imgWithoutAlt,
  'duplicate IDs': duplicateIdPages,
  'multiple H1s': multipleH1Pages,
  'unlabeled inputs': unlabeledInputs,
  'missing aria-controls': missingAriaControls,
})) {
  if (value > 0) staticFailures.push(`${label}: ${value}`);
}
if (missingImageDimensions > 0) warnings.push(`images missing width/height: ${missingImageDimensions}`);

console.log('\n=== Source Pattern Verification ===\n');
const sourceFiles = [];
function walkSource(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) walkSource(fullPath);
    else if (/\.(astro|tsx|ts|css)$/.test(entry.name)) sourceFiles.push(fullPath);
  }
}
walkSource(path.join(process.cwd(), 'src'));

const forbidden = [
  ['spinner', /animate-spin/],
  ['32px radius', /rounded-3xl/],
  ['invalid base color', /bg-obs-base/],
  ['invalid surface-hover color', /bg-obs-surface-hover/],
  ['invalid 4.5 spacing', /[hw]-4\.5/],
  ['invalid 0.2 spacing', /py-0\.2/],
  ['opacity shadow', /shadow-obs-accent\//],
  ['serif UI heading', /font-serif/],
  ['arbitrary text size', /text-\[[^\]]+\]/],
  ['accent fill without solid token', /bg-obs-accent(?:\s+|\/)[^\n]*text-white/],
];

for (const [label, pattern] of forbidden) {
  const matches = sourceFiles.filter((file) => pattern.test(fs.readFileSync(file, 'utf-8')));
  if (matches.length > 0) {
    staticFailures.push(`${label}: ${matches.map((file) => path.relative(process.cwd(), file)).join(', ')}`);
  }
}

if (warnings.length > 0) {
  console.log('Warnings:');
  warnings.forEach((warning) => console.log(`- ${warning}`));
}

if (staticFailures.length > 0 || failures.length > 0) {
  console.log('\nVerification failures:');
  [...failures, ...staticFailures].forEach((failure) => console.log(`- ${failure}`));
  process.exit(1);
}

console.log('\nAll checked contrast, accessibility, and source-pattern standards PASSED!');
