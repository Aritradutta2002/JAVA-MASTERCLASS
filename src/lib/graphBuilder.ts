import fs from 'node:fs';
import path from 'node:path';
import type { DocMetadata, GraphNode, GraphEdge, BacklinkItem, OutgoingLinkItem } from './types';
import { getAllDocs } from './content';
import { resolveInternalLink, initializeLinkMaps } from './linkResolver';

export interface KnowledgeGraphData {
  nodes: GraphNode[];
  edges: GraphEdge[];
  backlinks: Record<string, BacklinkItem[]>;
  outgoing: Record<string, OutgoingLinkItem[]>;
}

let cachedGraph: KnowledgeGraphData | null = null;

export function buildKnowledgeGraph(docs?: DocMetadata[], repoRoot: string = process.cwd()): KnowledgeGraphData {
  if (cachedGraph) return cachedGraph;

  const allDocs = docs || getAllDocs(repoRoot);
  initializeLinkMaps(allDocs);

  const slugToDoc = new Map<string, DocMetadata>();
  for (const d of allDocs) {
    slugToDoc.set(d.slug, d);
  }

  const inDegreeMap = new Map<string, number>();
  const outDegreeMap = new Map<string, number>();
  const edges: GraphEdge[] = [];
  const edgeSet = new Set<string>();

  const backlinks: Record<string, BacklinkItem[]> = {};
  const outgoing: Record<string, OutgoingLinkItem[]> = {};

  for (const d of allDocs) {
    inDegreeMap.set(d.slug, 0);
    outDegreeMap.set(d.slug, 0);
    backlinks[d.slug] = [];
    outgoing[d.slug] = [];
  }

  const linkRegex = /(?<!!)\[([^\]]+)\]\(([^)]+)\)/g;

  for (const doc of allDocs) {
    const fullPath = path.join(repoRoot, doc.filePath);
    if (!fs.existsSync(fullPath)) continue;
    const content = fs.readFileSync(fullPath, 'utf-8');

    const lines = content.split('\n');
    const seenTargetsForThisDoc = new Set<string>();

    for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
      const line = lines[lineIndex];
      let match;
      while ((match = linkRegex.exec(line)) !== null) {
        const [, , rawUrl] = match;
        const resolved = resolveInternalLink(doc.filePath, rawUrl);
        if (resolved.isExternal) continue;

        // strip anchor to get target document slug
        const targetSlug = resolved.url.split('#')[0];
        if (!targetSlug || targetSlug === doc.slug) continue;

        const targetDoc = slugToDoc.get(targetSlug);
        if (!targetDoc) continue;

        // Record Edge
        const edgeKey = `${doc.slug}->${targetSlug}`;
        if (!edgeSet.has(edgeKey)) {
          edgeSet.add(edgeKey);
          edges.push({ source: doc.slug, target: targetSlug });
          outDegreeMap.set(doc.slug, (outDegreeMap.get(doc.slug) || 0) + 1);
          inDegreeMap.set(targetSlug, (inDegreeMap.get(targetSlug) || 0) + 1);
        }

        // Record Outgoing Link
        if (!seenTargetsForThisDoc.has(targetSlug)) {
          seenTargetsForThisDoc.add(targetSlug);
          outgoing[doc.slug].push({
            targetId: targetDoc.id,
            targetTitle: targetDoc.title,
            targetSlug: targetDoc.slug,
            domain: targetDoc.domain,
            domainTitle: targetDoc.domainTitle,
          });

          // Record Backlink with surrounding context snippet
          let snippet = line.trim();
          if (snippet.length > 160) {
            snippet = snippet.substring(0, 157) + '...';
          }
          backlinks[targetSlug].push({
            sourceId: doc.id,
            sourceTitle: doc.title,
            sourceSlug: doc.slug,
            domain: doc.domain,
            domainTitle: doc.domainTitle,
            contextSnippet: snippet,
          });
        }
      }
    }
  }

  const nodes: GraphNode[] = allDocs.map((doc) => {
    const inDeg = inDegreeMap.get(doc.slug) || 0;
    const outDeg = outDegreeMap.get(doc.slug) || 0;
    return {
      id: doc.slug,
      title: doc.title,
      slug: doc.slug,
      domain: doc.domain,
      domainTitle: doc.domainTitle,
      degree: inDeg + outDeg,
      inDegree: inDeg,
      outDegree: outDeg,
    };
  });

  cachedGraph = { nodes, edges, backlinks, outgoing };
  return cachedGraph;
}
