export interface HeadingItem {
  depth: number;
  text: string;
  slug: string;
}

export type DocKind =
  | 'article'
  | 'module'
  | 'domain'
  | 'reference'
  | 'standard'
  | 'checklist'
  | 'syllabus';

export interface DocMetadata {
  id: string;
  title: string;
  slug: string;
  filePath: string;
  domain: string;
  domainTitle: string;
  category: string;
  categoryTitle: string;
  module: string;
  moduleTitle: string;
  group?: string;
  groupTitle?: string;
  kind: DocKind;
  order: number;
  level?: string;
  interviewFrequency?: string;
  prerequisites?: string;
  lastUpdated?: string;
  wordCount: number;
  readingTimeMinutes: number;
  headings: HeadingItem[];
  tags: string[];
  summary: string;
  isEmpty: boolean;
  isGenerated?: boolean;
  hasMath: boolean;
  hasMermaid: boolean;
  hasCode: boolean;
  children?: DocMetadata[];
  relatedJavaFiles?: string[];
}

export interface GraphNode {
  id: string;
  title: string;
  slug: string;
  domain: string;
  domainTitle: string;
  degree: number;
  inDegree: number;
  outDegree: number;
}

export interface GraphEdge {
  source: string;
  target: string;
}

export interface BacklinkItem {
  sourceId: string;
  sourceTitle: string;
  sourceSlug: string;
  domain: string;
  domainTitle: string;
  contextSnippet: string;
  anchor?: string;
}

export interface OutgoingLinkItem {
  targetId: string;
  targetTitle: string;
  targetSlug: string;
  domain: string;
  domainTitle: string;
  anchor?: string;
}

export interface NavigationNode {
  id: string;
  title: string;
  slug?: string;
  domain?: string;
  type: 'domain' | 'group' | 'category' | 'doc' | 'link';
  children?: NavigationNode[];
  badge?: string;
  depth?: number;
  count?: number;
}

export interface SearchRecord {
  id: string;
  title: string;
  slug: string;
  domain: string;
  domainTitle: string;
  category: string;
  headings: string[];
  content: string;
  tags: string[];
}

export interface SiteStats {
  documents: number;
  words: number;
  readingMinutes: number;
  codeBlocks: number;
  mermaidDiagrams: number;
  domains: number;
  javaFiles: number;
}

export interface LandingPage {
  slug: string;
  title: string;
  domain: string;
  domainTitle: string;
  moduleTitle: string;
  summary: string;
  docs: DocMetadata[];
}
