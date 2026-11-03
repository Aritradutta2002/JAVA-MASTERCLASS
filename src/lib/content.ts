import fs from 'node:fs';
import path from 'node:path';
import type { DocMetadata, HeadingItem, NavigationNode } from './types';
import { DOMAIN_TITLES, DOMAIN_COLORS } from './constants';

export { DOMAIN_TITLES, DOMAIN_COLORS };

function createSlugger() {
  const occurrences: Record<string, number> = {};
  return {
    reset() {
      for (const k in occurrences) delete occurrences[k];
    },
    slug(value: string) {
      let result = value
        .toLowerCase()
        .trim()
        .replace(/<[^>]+>/g, '')
        .replace(/[\s\t\n\r]+/g, '-')
        .replace(/[^\p{L}\p{N}\p{M}\-—_]/gu, '')
        .replace(/-+/g, '-')
        .replace(/^-|-$/g, '');
      const original = result || 'section';
      if (occurrences[original] !== undefined) {
        occurrences[original]++;
        result = `${original}-${occurrences[original]}`;
      } else {
        occurrences[original] = 0;
        result = original;
      }
      return result;
    },
  };
}

const slugger = createSlugger();

export function filePathToSlug(relPath: string): string {
  const norm = relPath.replace(/\\/g, '/');
  const parts = norm.split('/');
  const lowerParts = parts.map((p) => p.replace(/[^a-zA-Z0-9_\-\.]/g, '-').toLowerCase());
  const filename = lowerParts[lowerParts.length - 1];
  const nameNoExt = filename.replace(/\.(md|mdx)$/i, '');

  if (parts.length === 1) {
    if (filename === 'readme.md') return '/overview';
    if (filename === 'master_checklist.md' || filename === 'master-checklist.md') return '/checklist';
    return `/${nameNoExt}`;
  }

  const domain = lowerParts[0].replace(/_/g, '-');

  if (parts.length === 2) {
    if (filename === 'readme.md') return `/${domain}`;
    return `/${domain}/${nameNoExt}`;
  }

  const folderPath = lowerParts.slice(1, -1).join('/').replace(/_/g, '-');
  if (['readme.md', 'theory.md'].includes(filename) && folderPath) {
    return `/${domain}/${folderPath}`;
  }

  return `/${domain}/${folderPath}/${nameNoExt}`.replace(/_/g, '-');
}

export function formatTitleFromFilename(name: string): string {
  const clean = name.replace(/\.(md|mdx)$/i, '');
  return clean
    .replace(/^[0-9]+[-_.]\s*/, '')
    .replace(/[-_]/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function extractHeadings(markdown: string): HeadingItem[] {
  slugger.reset();
  const headings: HeadingItem[] = [];
  const lines = markdown.split('\n');
  let inCodeBlock = false;

  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      inCodeBlock = !inCodeBlock;
      continue;
    }
    if (inCodeBlock) continue;

    const match = line.match(/^(#{1,6})\s+(.+)$/);
    if (match) {
      const depth = match[1].length;
      let rawText = match[2].trim();
      // clean trailing # or markdown links
      rawText = rawText.replace(/\s+#+$/, '');
      rawText = rawText.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
      rawText = rawText.replace(/<[^>]+>/g, '');
      const slug = slugger.slug(rawText);
      headings.push({ depth, text: rawText, slug });
    }
  }

  return headings;
}

export function extractMetadataFromContent(content: string, filePath: string): Partial<DocMetadata> {
  const lines = content.split('\n');
  let title = '';
  let level = '';
  let interviewFrequency = '';
  let prerequisites = '';
  let lastUpdated = '';

  for (let i = 0; i < Math.min(lines.length, 50); i++) {
    const line = lines[i].trim();
    if (!title && line.startsWith('# ')) {
      title = line.substring(2).trim();
      title = title.replace(/<[^>]+>/g, '').trim();
    }
    if (line.includes('**Level:**')) {
      const match = line.match(/\*\*Level:\*\*\s*([^\n>]+)/);
      if (match) level = match[1].trim();
    }
    if (line.includes('**Interview Frequency:**')) {
      const match = line.match(/\*\*Interview Frequency:\*\*\s*([^\n>]+)/);
      if (match) interviewFrequency = match[1].trim();
    }
    if (line.includes('**Prerequisite') || line.includes('**Prerequisites:')) {
      const match = line.match(/\*\*Prerequisites?:\*\*\s*([^\n>]+)/);
      if (match) prerequisites = match[1].trim();
    }
    if (line.includes('**Last Updated:**')) {
      const match = line.match(/\*\*Last Updated:\*\*\s*([^\n>]+)/);
      if (match) lastUpdated = match[1].trim();
    }
  }

  if (!title) {
    title = formatTitleFromFilename(path.basename(filePath));
  }

  const wordCount = content.trim().split(/\s+/).filter(Boolean).length;
  const readingTimeMinutes = Math.max(1, Math.ceil(wordCount / 200));

  return {
    title,
    level,
    interviewFrequency,
    prerequisites,
    lastUpdated,
    wordCount,
    readingTimeMinutes,
  };
}

let cachedAllDocs: DocMetadata[] | null = null;

export function getAllDocs(repoRoot: string = process.cwd()): DocMetadata[] {
  if (cachedAllDocs) return cachedAllDocs;

  const mdFiles: string[] = [];
  const searchDirs = [
    'CORE_JAVA',
    'SPRING_BOOT',
    'SYSTEM_DESIGN',
    'DATABASE_CACHING',
    'JAVA_DSA',
    'CLOUD_DEVOPS',
    'DOCKER_JAVA_BACKEND',
  ];

  for (const dir of searchDirs) {
    const fullDir = path.join(repoRoot, dir);
    if (!fs.existsSync(fullDir)) continue;

    function walk(current: string) {
      const items = fs.readdirSync(current, { withFileTypes: true });
      for (const item of items) {
        if (item.name.startsWith('.')) continue;
        const fullPath = path.join(current, item.name);
        if (item.isDirectory()) {
          walk(fullPath);
        } else if (/\.(md|mdx)$/i.test(item.name)) {
          mdFiles.push(path.relative(repoRoot, fullPath));
        }
      }
    }
    walk(fullDir);
  }

  // Root files
  for (const rootFile of ['README.md', 'MASTER_CHECKLIST.md']) {
    const fullRootFile = path.join(repoRoot, rootFile);
    if (fs.existsSync(fullRootFile)) {
      mdFiles.push(rootFile);
    }
  }

  const docs: DocMetadata[] = [];

  for (const relPath of mdFiles) {
    const fullPath = path.join(repoRoot, relPath);
    const content = fs.readFileSync(fullPath, 'utf-8');
    const isEmpty = content.trim().length === 0;
    const norm = relPath.replace(/\\/g, '/');
    const parts = norm.split('/');
    const domainKey = parts.length === 1 ? 'root' : parts[0].toLowerCase().replace(/_/g, '-');
    const domainTitle = DOMAIN_TITLES[domainKey] || parts[0];
    const category = parts.length > 2 ? parts[1] : (parts.length === 2 ? parts[0] : 'General');
    const categoryTitle = category.replace(/^[0-9]+[-_.]\s*/, '').replace(/[-_]/g, ' ');

    const meta = extractMetadataFromContent(content, norm);
    const headings = extractHeadings(content);
    const slug = filePathToSlug(norm);

    // Extract tags
    const tags = new Set<string>();
    tags.add(domainKey);
    if (category) tags.add(category.toLowerCase());
    if (meta.level) tags.add(meta.level.toLowerCase().split(/[^a-z0-9]/)[0]);

    // Check for associated Java implementation
    const relatedJavaFiles: string[] = [];
    if (norm.includes('12-LLD-Preparation')) {
      const lldImplDir = path.join(repoRoot, 'SYSTEM_DESIGN/12-LLD-Preparation/implementations');
      if (fs.existsSync(lldImplDir)) {
        // match by name e.g. elevator -> elevator/ElevatorSystem.java
        const base = path.basename(norm, '.md').toLowerCase();
        for (const item of fs.readdirSync(lldImplDir)) {
          if (base.includes(item.toLowerCase()) || item.toLowerCase().includes(base.replace(/^[0-9]+-/, ''))) {
            const javaDir = path.join(lldImplDir, item);
            if (fs.statSync(javaDir).isDirectory()) {
              const jFiles = fs.readdirSync(javaDir).filter(f => f.endsWith('.java'));
              for (const jf of jFiles) {
                relatedJavaFiles.push(path.relative(repoRoot, path.join(javaDir, jf)));
              }
            }
          }
        }
      }
    }

    docs.push({
      id: norm,
      title: meta.title || formatTitleFromFilename(path.basename(norm)),
      slug,
      filePath: norm,
      domain: domainKey,
      domainTitle,
      category,
      categoryTitle,
      order: 0,
      level: meta.level,
      interviewFrequency: meta.interviewFrequency,
      prerequisites: meta.prerequisites,
      lastUpdated: meta.lastUpdated,
      wordCount: meta.wordCount || 0,
      readingTimeMinutes: meta.readingTimeMinutes || 1,
      headings,
      tags: Array.from(tags),
      isEmpty,
      relatedJavaFiles,
    });
  }

  // Sort docs logically by domain, category, filename
  docs.sort((a, b) => a.filePath.localeCompare(b.filePath));

  cachedAllDocs = docs;
  return docs;
}

export function buildNavigationTree(docs: DocMetadata[]): NavigationNode[] {
  const domainMap = new Map<string, Map<string, DocMetadata[]>>();

  for (const doc of docs) {
    if (!domainMap.has(doc.domain)) {
      domainMap.set(doc.domain, new Map());
    }
    const catMap = domainMap.get(doc.domain)!;
    if (!catMap.has(doc.category)) {
      catMap.set(doc.category, []);
    }
    catMap.get(doc.category)!.push(doc);
  }

  const rootNodes: NavigationNode[] = [];

  const domainOrder = [
    'root',
    'core-java',
    'spring-boot',
    'system-design',
    'database-caching',
    'java-dsa',
    'cloud-devops',
    'docker-java-backend',
  ];

  for (const dom of domainOrder) {
    if (!domainMap.has(dom)) continue;
    const catMap = domainMap.get(dom)!;
    const domTitle = DOMAIN_TITLES[dom] || dom;

    const categoryNodes: NavigationNode[] = [];
    let totalDocsInDomain = 0;

    for (const [catName, catDocs] of catMap.entries()) {
      totalDocsInDomain += catDocs.length;
      const docChildren: NavigationNode[] = catDocs.map((d) => ({
        id: d.id,
        title: d.title,
        slug: d.slug,
        domain: d.domain,
        type: 'doc',
        badge: d.level,
      }));

      // Find if this category has a main landing README
      const landingDoc = catDocs.find(d => path.basename(d.filePath).toLowerCase() === 'readme.md');

      categoryNodes.push({
        id: `${dom}:${catName}`,
        title: catDocs[0]?.categoryTitle || catName,
        slug: landingDoc?.slug,
        domain: dom,
        type: 'category',
        children: docChildren,
        badge: `${catDocs.length}`,
      });
    }

    rootNodes.push({
      id: dom,
      title: domTitle,
      domain: dom,
      type: 'domain',
      children: categoryNodes,
      badge: `${totalDocsInDomain}`,
    });
  }

  return rootNodes;
}
