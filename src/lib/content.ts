import fs from 'node:fs';
import path from 'node:path';
import GithubSlugger from 'github-slugger';
import type {
  DocKind,
  DocMetadata,
  HeadingItem,
  LandingPage,
  NavigationNode,
  SiteStats,
} from './types';
import {
  DOMAIN_COLORS,
  DOMAIN_DESCRIPTIONS,
  DOMAIN_ORDER,
  DOMAIN_TITLES,
} from './constants';

export { DOMAIN_TITLES, DOMAIN_COLORS, DOMAIN_DESCRIPTIONS };

const CONTENT_DOMAINS = [
  'CORE_JAVA',
  'SPRING_BOOT',
  'SYSTEM_DESIGN',
  'DATABASE_CACHING',
  'JAVA_DSA',
  'CLOUD_DEVOPS',
  'DOCKER_JAVA_BACKEND',
];

const ROOT_FILES = ['README.md', 'MASTER_CHECKLIST.md'];

const headingSlugger = new GithubSlugger();

export function slugify(value: string): string {
  const slugger = new GithubSlugger();
  return slugger.slug(value.replace(/<[^>]+>/g, '').trim()) || 'section';
}

export function filePathToSlug(relPath: string): string {
  const norm = relPath.replace(/\\/g, '/');
  const parts = norm.split('/');
  const lowerParts = parts.map((p) =>
    p.replace(/[^a-zA-Z0-9_\-.]/g, '-').toLowerCase()
  );
  const filename = lowerParts[lowerParts.length - 1];
  const nameNoExt = filename.replace(/\.(md|mdx)$/i, '');

  if (parts.length === 1) {
    if (filename === 'readme.md') return '/overview';
    if (filename === 'master_checklist.md' || filename === 'master-checklist.md')
      return '/master-checklist';
    return `/${nameNoExt}`;
  }

  const domain = lowerParts[0].replace(/_/g, '-');

  if (parts.length === 2) {
    if (filename === 'readme.md') return `/${domain}`;
    return `/${domain}/${nameNoExt}`;
  }

  const folderPath = lowerParts.slice(1, -1).join('/').replace(/_/g, '-');
  if (/^(readme|theory)\.mdx?$/i.test(filename) && folderPath) {
    return `/${domain}/${folderPath}`;
  }
  if (/^0?\d+_theory\.mdx?$/i.test(filename) && folderPath) {
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

function prettifySegment(segment: string): string {
  return segment
    .replace(/^[0-9]+[-_.]\s*/, '')
    .replace(/[-_]/g, ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function extractHeadings(markdown: string): HeadingItem[] {
  headingSlugger.reset();
  const headings: HeadingItem[] = [];
  const lines = markdown.split('\n');
  let inCodeBlock = false;

  for (const line of lines) {
    if (line.trim().startsWith('```') || line.trim().startsWith('~~~')) {
      inCodeBlock = !inCodeBlock;
      continue;
    }
    if (inCodeBlock) continue;

    const match = line.match(/^(#{1,6})\s+(.+)$/);
    if (match) {
      const depth = match[1].length;
      let rawText = match[2].trim();
      rawText = rawText.replace(/\s+#+$/, '');
      rawText = rawText.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
      rawText = rawText.replace(/<[^>]+>/g, '');
      const plainText = rawText
        .replace(/[\p{Extended_Pictographic}\uFE0F]/gu, '')
        .replace(/\s+/g, ' ')
        .trim();
      if (/^table of contents$/i.test(plainText)) continue;
      const slug = headingSlugger.slug(rawText);
      headings.push({ depth, text: rawText, slug });
    }
  }

  return headings;
}

function stripMarkdown(value: string): string {
  return value
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/~~~[\s\S]*?~~~/g, ' ')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/<[^>]+>/g, ' ')
    .replace(/^[>\s]*\[![A-Z]+\][^\n]*/gm, ' ')
    .replace(/[#*_>|]/g, ' ')
    .replace(/^[-+]\s+\[[ xX]\]\s+/, '')
    .replace(/^[-+]\s+/, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function extractSummary(content: string, title: string): string {
  const withoutFrontmatter = content.replace(/^---[\s\S]*?---\s*/m, '');
  const lines = withoutFrontmatter.split('\n');
  const paragraphs: string[] = [];
  let current: string[] = [];
  let inCode = false;

  const flush = () => {
    if (current.length) {
      paragraphs.push(current.join(' '));
      current = [];
    }
  };

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('```') || trimmed.startsWith('~~~')) {
      inCode = !inCode;
      continue;
    }
    if (inCode) continue;
    if (/^#{1,6}\s+/.test(trimmed)) {
      flush();
      continue;
    }
    if (!trimmed) {
      flush();
      continue;
    }
    if (
      /^(\||[-*_]{3,}|<|>|!\[|\*\*Level:|\*\*Interview|\*\*Prerequisite|\*\*Last Updated:|---$|[-*+]\s+\[[ xX]\]\s+)/.test(
        trimmed
      )
    ) {
      continue;
    }
    current.push(trimmed);
  }
  flush();

  let candidate = '';
  for (const paragraph of paragraphs) {
    const clean = stripMarkdown(paragraph);
    if (clean.length >= 40) {
      candidate = clean;
      break;
    }
  }
  if (!candidate) return '';
  let collapsed = candidate;
  if (candidate.length > 220) {
    const slice = candidate.slice(0, 220);
    const boundary = slice.lastIndexOf(' ');
    collapsed = `${slice.slice(0, boundary > 140 ? boundary : 220).trimEnd()}…`;
  }
  return collapsed === title ? '' : collapsed;
}

function parseOrder(segment: string): number {
  const match = segment.match(/^(\d+)/);
  return match ? Number(match[1]) : 9999;
}

const TAG_RULES: Array<{ pattern: RegExp; tags: string[] }> = [
  { pattern: /oop|object-oriented|pillar|inheritance|polymorphism|encapsulation|abstraction/i, tags: ['oop'] },
  { pattern: /memory|jvm|garbage|gc|heap|stack|classloader/i, tags: ['jvm', 'memory'] },
  { pattern: /collection|arraylist|linkedlist|hashmap|hashset|treemap/i, tags: ['collections'] },
  { pattern: /generic|wildcard|variance/i, tags: ['generics'] },
  { pattern: /stream|lambda|functional|optional/i, tags: ['streams'] },
  { pattern: /thread|concurren|executor|lock|atomic|virtual thread|completablefuture/i, tags: ['concurrency'] },
  { pattern: /pattern|solid|design principle/i, tags: ['design-patterns'] },
  { pattern: /exception|try-with-resources|error/i, tags: ['exceptions'] },
  { pattern: /string|immutab|intern/i, tags: ['strings'] },
  { pattern: /enum/i, tags: ['enums'] },
  { pattern: /reflection|annotation|proxy/i, tags: ['reflection'] },
  { pattern: /io|nio|file|channel|buffer/i, tags: ['io-nio'] },
  { pattern: /jdbc|connection pool|datasource/i, tags: ['jdbc'] },
  { pattern: /serializ/i, tags: ['serialization'] },
  { pattern: /test|junit|mockito|testng|assertion/i, tags: ['testing'] },
  { pattern: /spring core|ioc|bean|dependency injection|aop|applicationcontext/i, tags: ['spring-core'] },
  { pattern: /spring mvc|dispatcherservlet|controller|rest/i, tags: ['spring-mvc'] },
  { pattern: /jpa|hibernate|entity|persistence|n\+1|transaction/i, tags: ['spring-data', 'transactions'] },
  { pattern: /security|authentication|authorization|csrf|cors/i, tags: ['spring-security'] },
  { pattern: /jwt|token|signature/i, tags: ['jwt'] },
  { pattern: /oauth|oidc|openid|sso|keycloak/i, tags: ['oauth2'] },
  { pattern: /microservice|service discovery|gateway|resilience|circuit breaker/i, tags: ['microservices'] },
  { pattern: /kafka|rabbit|messaging|queue|event|outbox|cqrs/i, tags: ['messaging'] },
  { pattern: /cache|redis|eviction|stampede/i, tags: ['caching', 'redis'] },
  { pattern: /sql|query|join|index|b\+?tree|explain|postgres|mysql/i, tags: ['sql', 'indexing'] },
  { pattern: /nosql|mongodb|cassandra|dynamodb|document|column-family/i, tags: ['nosql'] },
  { pattern: /transaction|acid|mvcc|isolation/i, tags: ['transactions'] },
  { pattern: /docker|container|image|dockerfile/i, tags: ['docker'] },
  { pattern: /kubernetes|k8s|pod|helm/i, tags: ['kubernetes'] },
  { pattern: /aws|azure|gcp|cloud|terraform|iac/i, tags: ['cloud'] },
  { pattern: /observab|monitor|metric|trace|logging|slo|incident|reliability/i, tags: ['observability'] },
  { pattern: /system design|scalab|availability|consistency|cap|sharding|partition/i, tags: ['system-design'] },
  { pattern: /hld|case study|url shortener|rate limiter|netflix|uber/i, tags: ['hld', 'system-design'] },
  { pattern: /lld|low-level|uml|domain model/i, tags: ['lld'] },
  { pattern: /graph|bfs|dfs|dijkstra|topological|union find|spanning tree/i, tags: ['dsa', 'graphs'] },
  { pattern: /dynamic programming|memoization|tabulation|knapsack|lcs|lis/i, tags: ['dsa', 'dynamic-programming'] },
  { pattern: /array|two pointer|sliding window|prefix sum/i, tags: ['dsa', 'arrays'] },
  { pattern: /tree|bst|trie|heap|priority queue/i, tags: ['dsa', 'trees'] },
  { pattern: /backtrack|recursion|greedy|binary search|sorting|bit manipulation|hash/i, tags: ['dsa'] },
  { pattern: /interview|question|mock|checklist/i, tags: ['interview'] },
];

function deriveTags(relPath: string, title: string, domain: string, category: string): string[] {
  const tags = new Set<string>();
  tags.add(domain);
  if (category) {
    const categoryTag = category
      .toLowerCase()
      .replace(/^[0-9]+[-_.]\s*/, '')
      .replace(/_/g, '-')
      .replace(/[^a-z0-9-]/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-|-$/g, '');
    if (
      categoryTag &&
      categoryTag !== domain &&
      !['resources', 'general', 'start-here'].includes(categoryTag)
    ) {
      tags.add(categoryTag);
    }
  }
  const haystack = `${relPath} ${title}`;
  for (const rule of TAG_RULES) {
    if (rule.pattern.test(haystack)) {
      for (const tag of rule.tags) tags.add(tag);
    }
  }
  return Array.from(tags).slice(0, 10);
}

function detectKind(
  relPath: string,
  domain: string,
  filename: string,
  content: string
): DocKind {
  const lower = filename.toLowerCase();
  if (lower === 'master_checklist.md') return 'checklist';
  if (lower === 'readme.md' && domain === 'root') return 'domain';
  if (lower === 'standard.md') return 'standard';
  if (
    lower.includes('quick-reference') ||
    lower.includes('curriculum') ||
    lower.includes('learning-paths') ||
    lower.includes('glossary') ||
    lower.includes('interview-checklist')
  ) {
    return 'reference';
  }
  if (lower === 'readme.md' || lower === 'theory.md' || /^0?\d+_theory\.md$/i.test(lower)) {
    if (domain === 'cloud-devops' || domain === 'docker-java-backend') return 'syllabus';
    if (relPath.split('/').length === 2) return 'domain';
    return 'module';
  }
  if (content.trim().length === 0) return 'article';
  return 'article';
}

export function extractMetadataFromContent(
  content: string,
  filePath: string
): Partial<DocMetadata> {
  const lines = content.split('\n');
  let title = '';
  let level = '';
  let interviewFrequency = '';
  let prerequisites = '';
  let lastUpdated = '';

  for (let i = 0; i < Math.min(lines.length, 60); i++) {
    const line = lines[i].trim();
    if (!title && line.startsWith('# ')) {
      title = line.substring(2).trim();
      title = title
      .replace(/<[^>]+>/g, '')
      .replace(/[*`]/g, '')
      .replace(/\s+/g, ' ')
      .trim();
    }
    if (line.includes('**Level:**')) {
      const match = line.match(/\*\*Level:\*\*\s*([^\n>]+)/);
      if (match) level = match[1].trim();
    }
    if (line.includes('**Interview Frequency:**')) {
      const match = line.match(/\*\*Interview Frequency:\*\*\s*([^\n>]+)/);
      if (match) interviewFrequency = match[1].trim();
    }
    if (line.includes('**Prerequisite')) {
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
let cachedNavigation: NavigationNode[] | null = null;
let cachedLandings: LandingPage[] | null = null;

function walkMarkdown(dir: string, repoRoot: string, out: string[]) {
  const fullDir = path.join(repoRoot, dir);
  if (!fs.existsSync(fullDir)) return;
  const items = fs.readdirSync(fullDir, { withFileTypes: true });
  for (const item of items) {
    if (item.name.startsWith('.')) continue;
    const fullPath = path.join(fullDir, item.name);
    if (item.isDirectory()) {
      walkMarkdown(path.join(dir, item.name), repoRoot, out);
    } else if (/\.(md|mdx)$/i.test(item.name)) {
      out.push(path.relative(repoRoot, fullPath).replace(/\\/g, '/'));
    }
  }
}

function collectJavaFiles(repoRoot: string): string[] {
  const out: string[] = [];
  const roots = [
    'JAVA_DSA',
    'SYSTEM_DESIGN',
    'SPRING_BOOT',
    'CORE_JAVA',
    'DATABASE_CACHING',
  ];
  function walk(dir: string) {
    const fullDir = path.join(repoRoot, dir);
    if (!fs.existsSync(fullDir)) return;
    for (const item of fs.readdirSync(fullDir, { withFileTypes: true })) {
      if (item.name.startsWith('.') || item.name === 'node_modules') continue;
      const fullPath = path.join(fullDir, item.name);
      if (item.isDirectory()) walk(path.join(dir, item.name));
      else if (item.name.endsWith('.java')) out.push(path.relative(repoRoot, fullPath));
    }
  }
  for (const root of roots) walk(root);
  return out;
}

export function getAllDocs(repoRoot: string = process.cwd()): DocMetadata[] {
  if (cachedAllDocs) return cachedAllDocs;

  const mdFiles: string[] = [];
  for (const dir of CONTENT_DOMAINS) walkMarkdown(dir, repoRoot, mdFiles);
  for (const rootFile of ROOT_FILES) {
    if (fs.existsSync(path.join(repoRoot, rootFile))) mdFiles.push(rootFile);
  }

  const docs: DocMetadata[] = [];
  const seenSlugs = new Map<string, string>();

  for (const relPath of mdFiles.sort((a, b) => a.localeCompare(b, undefined, { numeric: true }))) {
    const fullPath = path.join(repoRoot, relPath);
    const content = fs.readFileSync(fullPath, 'utf-8');
    const norm = relPath.replace(/\\/g, '/');
    const parts = norm.split('/');
    const filename = path.basename(norm);
    const domainKey = parts.length === 1 ? 'root' : parts[0].toLowerCase().replace(/_/g, '-');
    const domainTitle = DOMAIN_TITLES[domainKey] || parts[0];
    const category = parts.length > 2 ? parts[1] : parts.length === 2 ? parts[0] : 'General';
    const categoryTitle = prettifySegment(category);
    const moduleSegment = parts.length > 2 ? parts[1] : parts.length === 2 ? parts[0] : 'Start Here';
    const moduleTitle = prettifySegment(moduleSegment);
    const groupSegment = parts.length > 4 ? parts[2] : parts.length > 3 ? parts[2] : undefined;
    const meta = extractMetadataFromContent(content, norm);
    const headings = extractHeadings(content);
    const slug = filePathToSlug(norm);

    if (seenSlugs.has(slug)) {
      throw new Error(
        `Duplicate content slug "${slug}" generated by:\n  - ${seenSlugs.get(slug)}\n  - ${norm}`
      );
    }
    seenSlugs.set(slug, norm);

    const tags = deriveTags(norm, meta.title || filename, domainKey, category);
    const kind = detectKind(norm, domainKey, filename, content);
    const summary = extractSummary(content, meta.title || '');

    const relatedJavaFiles: string[] = [];
    if (norm.includes('12-LLD-Preparation')) {
      const lldImplDir = path.join(repoRoot, 'SYSTEM_DESIGN/12-LLD-Preparation/implementations');
      if (fs.existsSync(lldImplDir)) {
        const base = path.basename(norm, '.md').toLowerCase();
        for (const item of fs.readdirSync(lldImplDir)) {
          if (
            base.includes(item.toLowerCase()) ||
            item.toLowerCase().includes(base.replace(/^[0-9]+-/, ''))
          ) {
            const javaDir = path.join(lldImplDir, item);
            if (fs.statSync(javaDir).isDirectory()) {
              for (const jf of fs.readdirSync(javaDir).filter((f) => f.endsWith('.java'))) {
                relatedJavaFiles.push(path.relative(repoRoot, path.join(javaDir, jf)));
              }
            }
          }
        }
      }
    }

    docs.push({
      id: norm,
      title: meta.title || formatTitleFromFilename(filename),
      slug,
      filePath: norm,
      domain: domainKey,
      domainTitle,
      category,
      categoryTitle,
      module: moduleSegment,
      moduleTitle,
      group: groupSegment,
      groupTitle: groupSegment ? prettifySegment(groupSegment) : undefined,
      kind,
      order: parseOrder(filename),
      level: meta.level,
      interviewFrequency: meta.interviewFrequency,
      prerequisites: meta.prerequisites,
      lastUpdated: meta.lastUpdated,
      wordCount: meta.wordCount || 0,
      readingTimeMinutes: meta.readingTimeMinutes || 1,
      headings,
      tags,
      summary,
      isEmpty: content.trim().length === 0,
      hasMath: /\$\$[\s\S]+?\$\$|(^|[^\\$])\$[^$\n]+\$/.test(content),
      hasMermaid: /```mermaid/i.test(content),
      hasCode: /```/.test(content),
      relatedJavaFiles,
    });
  }

  cachedAllDocs = docs;
  return docs;
}

interface MutableNavNode extends NavigationNode {
  children?: MutableNavNode[];
  orderKey: string;
  doc?: DocMetadata;
  segment?: string;
  domainKey: string;
  segments: string[];
}

function generatedLandingSlug(domain: string, segments: string[]): string {
  return `/${domain}/${segments
    .map((segment) => segment.toLowerCase().replace(/[^a-z0-9_\-.]/g, '-').replace(/^-|-$/g, ''))
    .join('/')}`.replace(/\/+/g, '/');
}

export function buildNavigationTree(docs?: DocMetadata[]): NavigationNode[] {
  if (!docs && cachedNavigation) return cachedNavigation;
  const allDocs = docs || getAllDocs();
  const domMap = new Map<string, MutableNavNode>();
  const landings: LandingPage[] = [];

  function ensureNode(domain: string, segments: string[]): MutableNavNode {
    const key = `${domain}:${segments.join('/')}`;
    const existing = domMap.get(key);
    if (existing) return existing;

    const parentSegments = segments.slice(0, -1);
    const segment = segments[segments.length - 1];
    const parent =
      segments.length === 0 ? null : ensureNode(domain, parentSegments);
    const node: MutableNavNode = {
      id: key,
      title: segment ? prettifySegment(segment) : DOMAIN_TITLES[domain] || domain,
      domain,
      domainKey: domain,
      type: segments.length === 0 ? 'domain' : 'group',
      children: [],
      segment,
      segments,
      slug:
        segments.length === 0
          ? domain === 'root'
            ? '/'
            : `/${domain}`
          : undefined,
      orderKey: segments.length === 0 ? '' : `${String(parseOrder(segment)).padStart(4, '0')}-${segment.toLowerCase()}`,
    };
    domMap.set(key, node);
    if (parent) parent.children!.push(node);
    return node;
  }

  for (const domain of DOMAIN_ORDER) {
    if (!allDocs.some((doc) => doc.domain === domain)) continue;
    ensureNode(domain, []);
  }

  for (const doc of allDocs) {
    const parts = doc.filePath.split('/');
    const relative = parts.length > 1 ? parts.slice(1) : [];
    const filename = relative[relative.length - 1] || '';
    const isIndex = /^(readme|theory)\.mdx?$/i.test(filename) || /^0?\d+_theory\.mdx?$/i.test(filename);
    let nodeSegments: string[];
    if (doc.domain === 'root') {
      nodeSegments = [doc.slug.replace(/^\//, '')];
    } else if (isIndex) {
      nodeSegments = relative.slice(0, -1);
    } else {
      nodeSegments = [
        ...relative.slice(0, -1),
        filename.replace(/\.(md|mdx)$/i, ''),
      ];
    }
    const node = ensureNode(doc.domain, nodeSegments);
    node.doc = doc;
    if (!isIndex || doc.domain === 'root') node.title = doc.title;
    node.slug = doc.slug;
    node.type = 'doc';
    node.badge = doc.level;
  }

  // Convert mutable nodes to public nodes, attach generated landing slugs and counts.
  function finalize(node: MutableNavNode, depth: number, isDomain = false): NavigationNode {
    const children = (node.children || [])
      .filter((child) => child.doc || (child.children && child.children.length > 0))
      .sort((a, b) => a.orderKey.localeCompare(b.orderKey, undefined, { numeric: true }))
      .map((child) => finalize(child, depth + 1));

    let slug = node.slug;
    const descendantDocs = collectDocs(node);
    if (!slug && !isDomain && descendantDocs.length > 0) {
      slug = generatedLandingSlug(node.domainKey, node.segments);
      landings.push({
        slug,
        title: node.title,
        domain: node.domainKey,
        domainTitle: DOMAIN_TITLES[node.domainKey] || node.domainKey,
        moduleTitle: node.title,
        summary: `Browse ${descendantDocs.length} topics in ${node.title}.`,
        docs: descendantDocs,
      });
    }

    return {
      id: node.id,
      title: node.title,
      slug,
      domain: node.domain,
      type: isDomain
        ? 'domain'
        : children.length > 0
          ? node.doc
            ? 'category'
            : 'group'
          : 'doc',
      children: children.length > 0 ? children : undefined,
      badge: isDomain ? String(descendantDocs.length) : node.badge,
      depth,
      count: descendantDocs.length,
    };
  }

  function collectDocs(node: MutableNavNode): DocMetadata[] {
    const out: DocMetadata[] = [];
    if (node.doc) out.push(node.doc);
    for (const child of node.children || []) out.push(...collectDocs(child));
    return out;
  }

  const roots: NavigationNode[] = [];
  for (const domain of DOMAIN_ORDER) {
    const node = domMap.get(`${domain}:`);
    if (!node) continue;
    roots.push(finalize(node, 0, true));
  }

  cachedLandings = landings;
  if (!docs) cachedNavigation = roots;
  return roots;
}

export function getLandingPages(docs?: DocMetadata[]): LandingPage[] {
  if (!docs && cachedLandings) return cachedLandings;
  buildNavigationTree(docs);
  return cachedLandings || [];
}

export function getAllTags(docs?: DocMetadata[]): Array<{ tag: string; count: number; docs: DocMetadata[] }> {
  const allDocs = docs || getAllDocs();
  const map = new Map<string, DocMetadata[]>();
  for (const doc of allDocs) {
    for (const tag of doc.tags) {
      if (!map.has(tag)) map.set(tag, []);
      map.get(tag)!.push(doc);
    }
  }
  return Array.from(map.entries())
    .map(([tag, items]) => ({ tag, count: items.length, docs: items }))
    .filter((entry) => entry.count > 1)
    .sort((a, b) => b.count - a.count || a.tag.localeCompare(b.tag));
}

let cachedStats: SiteStats | null = null;

export function getStats(docs?: DocMetadata[]): SiteStats {
  if (!docs && cachedStats) return cachedStats;
  const allDocs = docs || getAllDocs();
  let codeBlocks = 0;
  let mermaidDiagrams = 0;
  for (const doc of allDocs) {
    const content = fs.readFileSync(path.join(process.cwd(), doc.filePath), 'utf-8');
    const fences = (content.match(/^\s*(?:```|~~~)/gm) || []).length;
    codeBlocks += Math.floor(fences / 2);
    mermaidDiagrams += (content.match(/^\s*```mermaid/gim) || []).length;
  }
  const stats: SiteStats = {
    documents: allDocs.length,
    words: allDocs.reduce((sum, doc) => sum + doc.wordCount, 0),
    readingMinutes: allDocs.reduce((sum, doc) => sum + doc.readingTimeMinutes, 0),
    codeBlocks,
    mermaidDiagrams,
    domains: new Set(allDocs.map((doc) => doc.domain)).size,
    javaFiles: collectJavaFiles(process.cwd()).length,
  };
  if (!docs) cachedStats = stats;
  return stats;
}

export function getRelatedDocs(
  doc: DocMetadata,
  docs?: DocMetadata[],
  limit = 6
): DocMetadata[] {
  const allDocs = docs || getAllDocs();
  const scored = allDocs
    .filter((candidate) => candidate.slug !== doc.slug)
    .map((candidate) => {
      let score = 0;
      const sharedTags = candidate.tags.filter((tag) => doc.tags.includes(tag));
      score += sharedTags.length * 2;
      if (candidate.module === doc.module && candidate.domain === doc.domain) score += 2;
      if (candidate.domain === doc.domain) score += 1;
      const titleWords = new Set(
        doc.title.toLowerCase().split(/\W+/).filter((word) => word.length > 3)
      );
      const candidateWords = candidate.title.toLowerCase().split(/\W+/);
      score += candidateWords.filter((word) => titleWords.has(word)).length;
      return { candidate, score };
    })
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score || a.candidate.title.localeCompare(b.candidate.title));

  return scored.slice(0, limit).map((entry) => entry.candidate);
}

export function getDocBySlug(slug: string, docs?: DocMetadata[]): DocMetadata | undefined {
  return (docs || getAllDocs()).find((doc) => doc.slug === slug);
}

export function clearContentCache() {
  cachedAllDocs = null;
  cachedNavigation = null;
  cachedLandings = null;
  cachedStats = null;
}
