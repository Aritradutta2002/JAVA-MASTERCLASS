import fs from 'node:fs';
import path from 'node:path';
import DOMPurify from 'dompurify';

DOMPurify.sanitize = (s) => s;
DOMPurify.addHook = () => {};

const mermaidModule = await import('mermaid');
const mermaid = mermaidModule.default;

mermaid.initialize({
  startOnLoad: false,
  securityLevel: 'loose',
});

const ROOT_DIR = process.cwd();
const IGNORED_DIRS = new Set(['node_modules', '.git', 'dist', '.astro']);

function findMarkdownFiles(dir) {
  const results = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.isDirectory()) {
      if (!IGNORED_DIRS.has(entry.name)) {
        results.push(...findMarkdownFiles(path.join(dir, entry.name)));
      }
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      results.push(path.join(dir, entry.name));
    }
  }
  return results;
}

function extractMermaidBlocks(content) {
  // Stash 4+ backtick outer fences so authoring docs examples are preserved
  const stashedOuter = [];
  const withoutOuter = content.replace(/^(`{4,})[^\n]*\n[\s\S]*?^\1\s*$/gm, (match) => {
    stashedOuter.push(match);
    return `<!--STASHED_OUTER_${stashedOuter.length - 1}-->`;
  });

  const blocks = [];
  const regex = /^```mermaid[^\n]*\n([\s\S]*?)^```\s*$/gim;
  let match;
  while ((match = regex.exec(withoutOuter)) !== null) {
    const code = match[1].trim();
    const line = content.substring(0, match.index).split('\n').length;
    blocks.push({ code, line });
  }
  return blocks;
}

async function verifyAll() {
  const files = findMarkdownFiles(ROOT_DIR);
  console.log(`Scanning ${files.length} markdown files...`);

  let totalDiagrams = 0;
  let passedDiagrams = 0;
  const failures = [];

  for (const file of files) {
    const relPath = path.relative(ROOT_DIR, file);
    const content = fs.readFileSync(file, 'utf-8');
    const diagrams = extractMermaidBlocks(content);

    for (const { code, line } of diagrams) {
      totalDiagrams++;
      try {
        await mermaid.parse(code);
        passedDiagrams++;
      } catch (err) {
        failures.push({
          file: relPath,
          line,
          error: err.message || String(err),
          code: code.split('\n').slice(0, 10).join('\n'),
        });
      }
    }
  }

  console.log(`\n========================================`);
  console.log(`Total diagrams: ${totalDiagrams}`);
  console.log(`Passed: ${passedDiagrams}`);
  console.log(`Failed: ${failures.length}`);
  console.log(`========================================\n`);

  if (failures.length > 0) {
    console.error(`Found ${failures.length} diagram syntax errors:`);
    for (const f of failures) {
      console.error(`\n[FAIL] ${f.file}:${f.line}`);
      console.error(`Error: ${f.error}`);
      console.error(`Snippet:\n${f.code}\n...`);
    }
    process.exit(1);
  } else {
    console.log('All Mermaid diagrams parsed successfully without any syntax errors!');
  }
}

verifyAll().catch((err) => {
  console.error('Verification script error:', err);
  process.exit(1);
});
