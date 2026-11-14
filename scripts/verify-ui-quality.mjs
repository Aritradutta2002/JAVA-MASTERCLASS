import fs from 'node:fs';
import path from 'node:path';

function hexToRgb(hex) {
  let c = hex.replace('#', '');
  if (c.length === 3) {
    c = c.split('').map(x => x + x).join('');
  }
  const num = parseInt(c, 16);
  return {
    r: (num >> 16) & 255,
    g: (num >> 8) & 255,
    b: num & 255,
  };
}

function relativeLuminance({ r, g, b }) {
  const [rs, gs, bs] = [r, g, b].map(v => {
    const s = v / 255;
    return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

function contrastRatio(hex1, hex2) {
  const l1 = relativeLuminance(hexToRgb(hex1));
  const l2 = relativeLuminance(hexToRgb(hex2));
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

console.log('=== WCAG Contrast Verification ===\n');

const darkBg = {
  canvas: '#09090b',
  canvasSoft: '#121216',
  surface: '#18181f',
  hover: '#22222d',
};

const darkFg = {
  primary: '#f8fafc',
  secondary: '#b4b4c4',
  muted: '#888799',
  accent: '#a78bfa',
  accentDefault: '#8b5cf6',
  successText: '#34d399',
  warningText: '#fbbf24',
  errorText: '#f87171',
  infoText: '#60a5fa',
};

console.log('--- Dark Mode Contrast Check ---');
let allContrastPassed = true;

for (const [bgName, bgHex] of Object.entries(darkBg)) {
  for (const [fgName, fgHex] of Object.entries(darkFg)) {
    const ratio = contrastRatio(fgHex, bgHex);
    let level = 'FAIL';
    if (ratio >= 7.0) level = 'AAA (Normal)';
    else if (ratio >= 4.5) level = 'AA (Normal) / AAA (Large)';
    else if (ratio >= 3.0) level = 'AA (Large / UI)';
    else allContrastPassed = false;

    console.log(`[Dark] ${fgName} (${fgHex}) on ${bgName} (${bgHex}): ${ratio.toFixed(2)}:1 -> ${level}`);
  }
}

const lightBg = {
  canvas: '#ffffff',
  canvasSoft: '#f8fafc',
  surfaceHover: '#f1f5f9',
};

const lightFg = {
  primary: '#09090b',
  secondary: '#334155',
  muted: '#64748b',
  accent: '#6d28d9',
  accentHover: '#5b21b6',
  successText: '#065f46',
  warningText: '#92400e',
  errorText: '#991b1b',
  infoText: '#1e40af',
};

console.log('\n--- Light Mode Contrast Check ---');
for (const [bgName, bgHex] of Object.entries(lightBg)) {
  for (const [fgName, fgHex] of Object.entries(lightFg)) {
    const ratio = contrastRatio(fgHex, bgHex);
    let level = 'FAIL';
    if (ratio >= 7.0) level = 'AAA (Normal)';
    else if (ratio >= 4.5) level = 'AA (Normal) / AAA (Large)';
    else if (ratio >= 3.0) level = 'AA (Large / UI)';
    else allContrastPassed = false;

    console.log(`[Light] ${fgName} (${fgHex}) on ${bgName} (${bgHex}): ${ratio.toFixed(2)}:1 -> ${level}`);
  }
}

console.log(`\nOverall Color Contrast Status: ${allContrastPassed ? 'ALL PASSED (>= 4.5:1 / 3:1)' : 'SOME FAILED'}\n`);

// Accessibility and HTML Structure checks on dist
console.log('=== Dist HTML Accessibility & Standards Verification ===\n');

const distDir = path.join(process.cwd(), 'dist');
if (!fs.existsSync(distDir)) {
  console.error('Dist directory does not exist! Build first.');
  process.exit(1);
}

function findHtmlFiles(dir) {
  let results = [];
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name);
    if (f.isDirectory()) {
      results.push(...findHtmlFiles(p));
    } else if (f.name.endsWith('.html')) {
      results.push(p);
    }
  }
  return results;
}

const htmlFiles = findHtmlFiles(distDir);
console.log(`Checking ${htmlFiles.length} generated HTML files for accessibility & layout integrity...`);

let missingLangCount = 0;
let missingViewportCount = 0;
let missingSkipLinkCount = 0;
let buttonWithoutAriaOrText = 0;
let imgWithoutAlt = 0;

for (const file of htmlFiles) {
  const content = fs.readFileSync(file, 'utf-8');

  // Check <html lang="...">
  if (!/<html[^>]*lang=["'][a-zA-Z-]+["']/i.test(content)) {
    missingLangCount++;
  }

  // Check <meta name="viewport"
  if (!/<meta[^>]*name=["']viewport["']/i.test(content)) {
    missingViewportCount++;
  }

  // Check Skip link (on full page layouts)
  if (content.includes('<nav') && !content.includes('skip-to-content') && !content.includes('Skip to content') && !content.includes('Skip to main')) {
    missingSkipLinkCount++;
  }

  // Check buttons for accessible text / aria
  const buttonMatches = content.match(/<button\b[^>]*>([\s\S]*?)<\/button>/gi) || [];
  for (const b of buttonMatches) {
    const hasAriaLabel = /aria-label=["'][^"']+["']/i.test(b);
    const hasAriaLabelledby = /aria-labelledby=["'][^"']+["']/i.test(b);
    const innerText = b.replace(/<[^>]+>/g, '').trim();
    if (!hasAriaLabel && !hasAriaLabelledby && innerText.length === 0) {
      buttonWithoutAriaOrText++;
    }
  }

  // Check images for alt attribute
  const imgMatches = content.match(/<img\b[^>]*>/gi) || [];
  for (const img of imgMatches) {
    if (!/\balt=["'][^"']*["']/i.test(img)) {
      imgWithoutAlt++;
    }
  }
}

console.log(`Missing lang attribute: ${missingLangCount}`);
console.log(`Missing viewport meta tag: ${missingViewportCount}`);
console.log(`Missing skip-to-content on nav pages: ${missingSkipLinkCount}`);
console.log(`Buttons without text or aria-label: ${buttonWithoutAriaOrText}`);
console.log(`Images without alt attribute: ${imgWithoutAlt}`);

if (buttonWithoutAriaOrText > 0 || imgWithoutAlt > 0 || missingLangCount > 0 || missingViewportCount > 0) {
  console.log('\nAccessibility issues detected that need attention.');
} else {
  console.log('\nAll checked HTML accessibility & structural standards PASSED!');
}
