import fs from 'node:fs';
import path from 'node:path';
import type { DocMetadata, SearchRecord } from './types';
import { getAllDocs } from './content';

export function buildSearchIndex(docs?: DocMetadata[], repoRoot: string = process.cwd()): SearchRecord[] {
  const allDocs = docs || getAllDocs(repoRoot);
  const records: SearchRecord[] = [];

  for (const doc of allDocs) {
    if (doc.isEmpty) continue;

    const fullPath = path.join(repoRoot, doc.filePath);
    let excerpt = '';
    if (fs.existsSync(fullPath)) {
      const content = fs.readFileSync(fullPath, 'utf-8');
      // strip markdown formatting and code blocks for clean search text
      const clean = content
        .replace(/```[\s\S]*?```/g, ' ')
        .replace(/`[^`]+`/g, ' ')
        .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
        .replace(/[#*>\-_|]/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
      excerpt = clean.substring(0, 300);
    }

    records.push({
      id: doc.slug,
      title: doc.title,
      slug: doc.slug,
      domain: doc.domain,
      domainTitle: doc.domainTitle,
      category: doc.categoryTitle,
      headings: doc.headings.map((h) => h.text),
      content: excerpt,
      tags: doc.tags,
    });
  }

  return records;
}
