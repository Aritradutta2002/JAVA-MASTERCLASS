import path from 'node:path';
import type { DocMetadata } from './types';
import { getAllDocs } from './content';

let filePathToDocMap: Map<string, DocMetadata> | null = null;
let slugToDocMap: Map<string, DocMetadata> | null = null;
let fuzzyLookupMap: Map<string, DocMetadata> | null = null;
let directoryToDocMap: Map<string, DocMetadata> | null = null;

export function initializeLinkMaps(docs?: DocMetadata[]) {
  const allDocs = docs || getAllDocs();
  filePathToDocMap = new Map();
  slugToDocMap = new Map();
  fuzzyLookupMap = new Map();
  directoryToDocMap = new Map();

  for (const doc of allDocs) {
    const normPath = doc.filePath.replace(/\\/g, '/');
    filePathToDocMap.set(normPath, doc);
    filePathToDocMap.set(normPath.toLowerCase(), doc);
    slugToDocMap.set(doc.slug, doc);

    // Track directories
    const dir = path.dirname(normPath).replace(/\\/g, '/');
    if (path.basename(normPath).toLowerCase() === 'readme.md' || !directoryToDocMap.has(dir)) {
      directoryToDocMap.set(dir, doc);
      directoryToDocMap.set(dir.toLowerCase(), doc);
    }

    // Fuzzy lookups
    const baseName = path.basename(normPath).toLowerCase();
    const nameNoExt = baseName.replace(/\.(md|mdx)$/i, '');
    fuzzyLookupMap.set(baseName, doc);
    fuzzyLookupMap.set(nameNoExt, doc);

    // Strip numeric prefixes (e.g. 02_memoization.md -> memoization.md)
    const strippedPrefix = nameNoExt.replace(/^[0-9]+[-_.]/, '');
    fuzzyLookupMap.set(strippedPrefix, doc);
    fuzzyLookupMap.set(`${strippedPrefix}.md`, doc);

    const stripped = normPath.toLowerCase().replace(/[-_]/g, '');
    fuzzyLookupMap.set(stripped, doc);
  }
}

export function resolveInternalLink(currentDocPath: string, rawUrl: string): { url: string; isExternal: boolean; isAnchor: boolean } {
  if (!filePathToDocMap) {
    initializeLinkMaps();
  }

  const trimmed = rawUrl.trim();

  // External links
  if (/^(https?:|mailto:|tel:|javascript:)/i.test(trimmed)) {
    return { url: trimmed, isExternal: true, isAnchor: false };
  }

  // Anchor-only links
  if (trimmed.startsWith('#')) {
    return { url: trimmed, isExternal: false, isAnchor: true };
  }

  const [pathPart, anchorPart] = trimmed.split('#');
  const anchor = anchorPart ? `#${anchorPart}` : '';
  const currentDir = path.dirname(currentDocPath).replace(/\\/g, '/');

  // Strip trailing slashes
  const cleanPath = pathPart.replace(/\/+$/, '');
  const targetNorm = path.normalize(path.join(currentDir, cleanPath)).replace(/\\/g, '/');

  // Static practice schema asset
  if (cleanPath.endsWith('practice-schema.sql')) {
    return { url: '/database-caching/01-sql-databases/practice-schema.sql', isExternal: false, isAnchor: false };
  }

  // 1. Direct candidate matching
  const candidates = [
    targetNorm,
    targetNorm.toLowerCase(),
    `${targetNorm}.md`,
    `${targetNorm}.MD`,
    `${targetNorm}/README.md`,
    `${targetNorm}/README.MD`,
    `${targetNorm}/THEORY.MD`,
    `${targetNorm}/01_THEORY.MD`,
  ];

  for (const cand of candidates) {
    const found = filePathToDocMap!.get(cand);
    if (found) {
      return { url: `${found.slug}${anchor}`, isExternal: false, isAnchor: false };
    }
  }

  // 2. Directory map lookup
  if (directoryToDocMap!.has(targetNorm) || directoryToDocMap!.has(targetNorm.toLowerCase())) {
    const found = directoryToDocMap!.get(targetNorm) || directoryToDocMap!.get(targetNorm.toLowerCase())!;
    return { url: `${found.slug}${anchor}`, isExternal: false, isAnchor: false };
  }

  // 3. Known structural aliases and corrections
  // Checklist links
  if (/checklist\.md/i.test(cleanPath)) {
    return { url: `/checklist${anchor}`, isExternal: false, isAnchor: false };
  }

  // Graph doc links
  if (cleanPath.includes('ALGORITHM_INDEX')) {
    return { url: `/java-dsa/resources/graph/18-graph-interview-reference${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('ALGORITHM_COMPARISON')) {
    return { url: `/java-dsa/resources/graph/17-graph-problem-patterns${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('graph_interview_handbook')) {
    return { url: `/java-dsa/resources/graph${anchor}`, isExternal: false, isAnchor: false };
  }

  // DP missing links
  if (/patterns\.md$/i.test(cleanPath)) {
    return { url: `/java-dsa/resources/dynamic-programming/18-patterns-and-tricks${anchor}`, isExternal: false, isAnchor: false };
  }
  if (/mcm\.md$/i.test(cleanPath)) {
    return { url: `/java-dsa/resources/dynamic-programming/16-partition-dp${anchor}`, isExternal: false, isAnchor: false };
  }
  if (/dp_on_trees\.md$/i.test(cleanPath)) {
    return { url: `/java-dsa/resources/dynamic-programming/10-tree-dp${anchor}`, isExternal: false, isAnchor: false };
  }

  // Spring Boot Fundamentals aliases
  if (cleanPath.includes('03-Starters-Embedded-Servers')) {
    return { url: `/spring-boot/01-spring-boot-fundamentals/03-configuration-and-profiles${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('04-Profiles-Environment-Actuator')) {
    return { url: `/spring-boot/01-spring-boot-fundamentals/04-actuator-and-observability${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('05-Internal-Working-Flow')) {
    return { url: `/spring-boot/01-spring-boot-fundamentals/05-request-lifecycle-and-startup-flow${anchor}`, isExternal: false, isAnchor: false };
  }

  // Spring Security aliases
  if (cleanPath.includes('04-Password-Encoding-Sessions-CSRF-and-CORS')) {
    return { url: `/spring-boot/05-spring-security/04-cors-csrf-and-session-management${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('Database-Design-and-Optimization')) {
    return { url: `/spring-boot/11-database-design-optimization${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('Deployment-and-DevOps')) {
    return { url: `/spring-boot/14-deployment-devops${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('03-Authorization-Roles-Permissions-and-Method-Security')) {
    return { url: `/spring-boot/05-spring-security/03-authorization-roles-and-permissions${anchor}`, isExternal: false, isAnchor: false };
  }
  if (cleanPath.includes('05-Production-Security-Patterns-and-Best-Practices')) {
    return { url: `/spring-boot/05-spring-security/06-production-security-patterns${anchor}`, isExternal: false, isAnchor: false };
  }

  // LLD Preparation implementations
  if (currentDocPath.includes('12-LLD-Preparation') || targetNorm.includes('12-LLD-Preparation')) {
    if (cleanPath.endsWith('.java') || targetNorm.endsWith('.java')) {
      const match = cleanPath.match(/(?:implementations\/)(.+)$/i);
      const sub = match ? match[1] : path.basename(cleanPath);
      return { url: `/system-design/12-lld-preparation/implementations/${sub}`, isExternal: false, isAnchor: false };
    }
    if (cleanPath === './implementations/' || cleanPath === './implementations' || cleanPath === 'implementations/' || cleanPath === 'implementations') {
      return { url: `/system-design/12-lld-preparation#implementations`, isExternal: false, isAnchor: false };
    }
  }

  // JAVA_DSA links
  if (/^(?:\.\/)?JAVA_DSA\b/i.test(cleanPath) || cleanPath.startsWith('JAVA_DSA/')) {
    const sub = cleanPath.replace(/^(?:\.\/)?JAVA_DSA\/?/i, '').replace(/\/$/, '').toLowerCase();
    if (!sub || sub === 'dsa') return { url: '/java-dsa', isExternal: false, isAnchor: false };

    if (sub.startsWith('basics') || sub.startsWith('mathematics') || sub.startsWith('foundations')) {
      return { url: `/java-dsa/resources/foundations${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('array') || sub.startsWith('two_pointer') || sub.startsWith('sliding_window') || sub.startsWith('prefix_sum')) {
      return { url: `/java-dsa/resources/array${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('bit_manipulation') || sub.startsWith('bit-manipulation')) {
      return { url: `/java-dsa/resources/bit-manipulation${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('dynamic_programming') || sub.startsWith('dynamic-programming')) {
      return { url: `/java-dsa/resources/dynamic-programming${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('graph')) {
      return { url: `/java-dsa/resources/graph${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('string')) {
      return { url: `/java-dsa/resources/string${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('linkedlist')) {
      return { url: `/java-dsa/resources/linkedlist${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('stack') || sub.startsWith('queue') || sub.startsWith('monotonic')) {
      return { url: `/java-dsa/resources/stack${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('tree') || sub.startsWith('binary_trees') || sub.startsWith('binary_search_trees') || sub.startsWith('segment_tree')) {
      return { url: `/java-dsa/resources/tree${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('heap')) {
      return { url: `/java-dsa/resources/heap${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('search') || sub.startsWith('binary_search')) {
      return { url: `/java-dsa/resources/searching${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('sort')) {
      return { url: `/java-dsa/resources/sorting${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('backtrack')) {
      return { url: `/java-dsa/resources/backtracking${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('recur')) {
      return { url: `/java-dsa/resources/recursion${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('greedy')) {
      return { url: `/java-dsa/resources/greedy${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('hash')) {
      return { url: `/java-dsa/resources/hashing${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('trie')) {
      return { url: `/java-dsa/resources/trie${anchor}`, isExternal: false, isAnchor: false };
    }
    if (sub.startsWith('system_design')) {
      return { url: `/system-design${anchor}`, isExternal: false, isAnchor: false };
    }

    return { url: `/java-dsa${anchor}`, isExternal: false, isAnchor: false };
  }

  // 4. Fuzzy lookup by base filename & stripped prefix
  const base = path.basename(cleanPath).toLowerCase();
  const baseNoExt = base.replace(/\.(md|mdx)$/i, '');
  const fuzzy =
    fuzzyLookupMap!.get(base) ||
    fuzzyLookupMap!.get(baseNoExt) ||
    fuzzyLookupMap!.get(baseNoExt.replace(/^[0-9]+[-_.]/, ''));

  if (fuzzy) {
    return { url: `${fuzzy.slug}${anchor}`, isExternal: false, isAnchor: false };
  }

  // 5. If it starts with slash, check if it's already a valid slug
  if (trimmed.startsWith('/')) {
    return { url: trimmed, isExternal: false, isAnchor: false };
  }

  return { url: trimmed, isExternal: false, isAnchor: false };
}

export function transformMarkdownContent(content: string, currentDocPath: string): string {
  // 1. Transform [[Wiki Links]]
  let transformed = content.replace(/\[\[(.*?)\]\]/g, (match, inner) => {
    if (inner.includes('==') || inner.includes('!=') || inner.includes('], [') || /^\s*["\$\d]/.test(inner)) {
      return match;
    }
    const parts = inner.split('|');
    const target = parts[0].trim();
    const label = parts[1] ? parts[1].trim() : target;
    const resolved = resolveInternalLink(currentDocPath, target);
    return `[${label}](${resolved.url})`;
  });

  // 2. Transform Standard Markdown Links [Text](url)
  transformed = transformed.replace(/(?<!!)\[([^\]]+)\]\(([^)]+)\)/g, (match, text, rawUrl) => {
    const resolved = resolveInternalLink(currentDocPath, rawUrl);
    return `[${text}](${resolved.url})`;
  });

  return transformed;
}
