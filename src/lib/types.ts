export interface HeadingItem {
  depth: number;
  text: string;
  slug: string;
}

export interface DocMetadata {
  id: string;                    // unique canonical ID, e.g. "core-java/01-core-java-oop/01-oop-foundations-and-4-pillars"
  title: string;                 // e.g. "OOP Foundations and the 4 Pillars"
  slug: string;                  // e.g. "/core-java/01-core-java-oop/01-oop-foundations-and-4-pillars"
  filePath: string;              // relative to repo root, e.g. "CORE_JAVA/01-Core-Java-OOP/01-OOP-Foundations-and-4-Pillars.md"
  domain: string;                // e.g. "core-java", "spring-boot", "system-design", etc.
  domainTitle: string;           // e.g. "Core Java", "Spring Boot", "System Design"
  category: string;              // e.g. "01-Core-Java-OOP"
  categoryTitle: string;         // e.g. "01 · Core Java OOP"
  order: number;                 // ordering within category
  level?: string;                // "Beginner", "Intermediate", "Advanced", "Expert"
  interviewFrequency?: string;   // "★★★★★"
  prerequisites?: string;        // prerequisites text or links
  lastUpdated?: string;          // e.g. "2026"
  wordCount: number;
  readingTimeMinutes: number;
  headings: HeadingItem[];
  tags: string[];
  isEmpty: boolean;
  relatedJavaFiles?: string[];   // e.g. implementations in LLD or DSA
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
}

export interface OutgoingLinkItem {
  targetId: string;
  targetTitle: string;
  targetSlug: string;
  domain: string;
}

export interface NavigationNode {
  id: string;
  title: string;
  slug?: string;
  domain?: string;
  type: 'domain' | 'category' | 'doc';
  children?: NavigationNode[];
  badge?: string;
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
