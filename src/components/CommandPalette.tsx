import React, { useEffect, useMemo, useRef, useState } from 'react';
import MiniSearch from 'minisearch';
import { createFocusTrap } from '../lib/focusTrap';
import type { SearchRecord } from '../lib/types';
import {
  ArrowRight,
  BookMarked,
  BookOpen,
  CheckSquare,
  Clock,
  Cloud,
  Code2,
  Compass,
  Container,
  CornerDownLeft,
  Cpu,
  Database,
  FileText,
  Layers,
  Library,
  Search,
  Sparkles,
  Tag,
  Waypoints,
  X,
} from 'lucide-react';
import { DOMAIN_COLORS } from '../lib/constants';

interface PaletteItem {
  id: string;
  title: string;
  subtitle: string;
  slug: string;
  group: 'Recent' | 'Actions' | 'Domains' | 'Topics';
  icon?: React.ComponentType<{ className?: string }>;
  domainBadge?: string;
}

const QUICK_ACTIONS = [
  { id: 'library', title: 'Browse Complete Library', subtitle: 'Explore all modules, chapters, and topics', href: '/library', icon: Library },
  { id: 'paths', title: 'Learning Paths & Syllabi', subtitle: 'Structured role-based study tracks', href: '/paths', icon: Waypoints },
  { id: 'search', title: 'Full-Text Search Engine', subtitle: 'Search inside article bodies with Pagefind', href: '/search', icon: Search },
  { id: 'graph', title: 'Interactive Knowledge Graph', subtitle: '2D network visualizer of connected concepts', href: '/graph', icon: Compass },
  { id: 'checklist', title: 'Mastery Study Checklist', subtitle: 'Interactive preparation checklist & progress tracker', href: '/checklist', icon: CheckSquare },
  { id: 'references', title: 'Quick References & Cheat Sheets', subtitle: 'Commands, configs, and architectural patterns', href: '/reference/quick-references', icon: BookMarked },
  { id: 'tags', title: 'Taxonomy & Topic Tags', subtitle: 'Explore knowledge grouped by technology tags', href: '/tags', icon: Tag },
  { id: 'overview', title: 'Start Here: Architecture Overview', subtitle: 'Foundations and study methodology', href: '/overview', icon: BookOpen },
];

const DOMAIN_ITEMS = [
  { id: 'core-java', title: 'Core Java Deep Dive', subtitle: 'JVM internals, concurrency, memory model, modern features', href: '/core-java', icon: Cpu, domain: 'core-java' },
  { id: 'spring-boot', title: 'Spring Boot & Microservices', subtitle: 'IoC, AOP, WebFlux, Cloud, security, production engineering', href: '/spring-boot', icon: Layers, domain: 'spring-boot' },
  { id: 'system-design', title: 'High-Scale System Design', subtitle: 'Distributed systems, availability, caching, messaging, Kafka', href: '/system-design', icon: Compass, domain: 'system-design' },
  { id: 'database-caching', title: 'Database & Caching Architecture', subtitle: 'PostgreSQL, Redis, indexing, replication, consistency models', href: '/database-caching', icon: Database, domain: 'database-caching' },
  { id: 'java-dsa', title: 'Java Data Structures & Algorithms', subtitle: 'Patterns, complexity analysis, tree, graph, dynamic programming', href: '/java-dsa', icon: Code2, domain: 'java-dsa' },
  { id: 'cloud-devops', title: 'Cloud, Observability & DevOps', subtitle: 'Kubernetes, AWS, Terraform, Prometheus, CI/CD pipelines', href: '/cloud-devops', icon: Cloud, domain: 'cloud-devops' },
  { id: 'docker-java-backend', title: 'Docker for Java Developers', subtitle: 'Containerization, multi-stage builds, JRE slimming, compose', href: '/docker-java-backend', icon: Container, domain: 'docker-java-backend' },
];

export default function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [records, setRecords] = useState<SearchRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [recents, setRecents] = useState<{ title: string; slug: string }[]>([]);
  
  const inputRef = useRef<HTMLInputElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const itemRefs = useRef<Record<number, HTMLDivElement | null>>({});

  const miniSearch = useMemo(
    () =>
      new MiniSearch<SearchRecord>({
        fields: ['title', 'category', 'headings', 'content', 'tags'],
        storeFields: ['title', 'slug', 'domain', 'domainTitle', 'category', 'content'],
        searchOptions: {
          boost: { title: 5, category: 2, headings: 2, tags: 1.5 },
          prefix: true,
          fuzzy: 0.2,
        },
      }),
    []
  );

  // Listen to open events from header search button or keyboard shortcuts
  useEffect(() => {
    const handleOpen = () => setOpen(true);
    window.addEventListener('obsidian:open-search', handleOpen);
    const triggerBtn = document.getElementById('open-command-palette-btn');
    triggerBtn?.addEventListener('click', handleOpen);
    return () => {
      window.removeEventListener('obsidian:open-search', handleOpen);
      triggerBtn?.removeEventListener('click', handleOpen);
    };
  }, []);

  // When opening, load recents and fetch search index if not loaded
  useEffect(() => {
    if (!open) return;
    try {
      const stored = JSON.parse(localStorage.getItem('obsidian-recent-pages') || '[]');
      setRecents(stored.slice(0, 4));
    } catch {
      setRecents([]);
    }

    if (records.length === 0 && !loading) {
      setLoading(true);
      setLoadError(false);
      fetch('/search-index.json')
        .then((res) => {
          if (!res.ok) throw new Error(`Search index returned ${res.status}`);
          return res.json();
        })
        .then((data: SearchRecord[]) => {
          setRecords(data);
          miniSearch.removeAll();
          miniSearch.addAll(data);
        })
        .catch((err) => {
          console.error('Failed to load search index', err);
          setLoadError(true);
        })
        .finally(() => setLoading(false));
    }

    const timer = window.setTimeout(() => inputRef.current?.focus(), 40);
    return () => window.clearTimeout(timer);
  }, [open, records.length, loading, miniSearch, reloadToken]);

  // Reset query and selection on open/query change
  useEffect(() => {
    if (!open) {
      setQuery('');
    }
    setSelectedIndex(0);
  }, [open, query]);

  // Grouped search results or default items
  const flatItems = useMemo<PaletteItem[]>(() => {
    const trimmed = query.trim().toLowerCase();

    if (!trimmed) {
      const result: PaletteItem[] = [];

      // 1. Recents
      recents.forEach((item) => {
        result.push({
          id: `recent-${item.slug}`,
          title: item.title,
          subtitle: 'Recently viewed topic',
          slug: item.slug,
          group: 'Recent',
          icon: Clock,
        });
      });

      // 2. Quick Actions
      QUICK_ACTIONS.forEach((action) => {
        result.push({
          id: action.id,
          title: action.title,
          subtitle: action.subtitle,
          slug: action.href,
          group: 'Actions',
          icon: action.icon,
        });
      });

      // 3. Curriculum Domains
      DOMAIN_ITEMS.forEach((domain) => {
        result.push({
          id: domain.id,
          title: domain.title,
          subtitle: domain.subtitle,
          slug: domain.href,
          group: 'Domains',
          icon: domain.icon,
          domainBadge: domain.domain,
        });
      });

      return result;
    }

    // When query is active:
    const result: PaletteItem[] = [];

    // Filter Quick Actions matching query
    QUICK_ACTIONS.filter(
      (a) => a.title.toLowerCase().includes(trimmed) || a.subtitle.toLowerCase().includes(trimmed)
    ).forEach((action) => {
      result.push({
        id: `action-${action.id}`,
        title: action.title,
        subtitle: action.subtitle,
        slug: action.href,
        group: 'Actions',
        icon: action.icon,
      });
    });

    // Filter Domains matching query
    DOMAIN_ITEMS.filter(
      (d) => d.title.toLowerCase().includes(trimmed) || d.subtitle.toLowerCase().includes(trimmed)
    ).forEach((domain) => {
      result.push({
        id: `domain-${domain.id}`,
        title: domain.title,
        subtitle: domain.subtitle,
        slug: domain.href,
        group: 'Domains',
        icon: domain.icon,
        domainBadge: domain.domain,
      });
    });

    // Search MiniSearch for matching documents
    if (records.length > 0) {
      const searchMatches = miniSearch.search(trimmed).slice(0, 16);
      searchMatches.forEach((match) => {
        const record = records.find((r) => r.id === match.id);
        result.push({
          id: `doc-${match.id}`,
          title: String(match.title ?? record?.title ?? 'Untitled'),
          subtitle: record ? `${record.domainTitle} · ${record.category}` : 'Topic',
          slug: String(match.slug ?? record?.slug ?? '/'),
          group: 'Topics',
          icon: FileText,
          domainBadge: record?.domain,
        });
      });
    }

    return result;
  }, [query, recents, records, miniSearch]);

  // Ensure selected index is within bounds
  useEffect(() => {
    if (selectedIndex >= flatItems.length) {
      setSelectedIndex(0);
    }
  }, [flatItems.length, selectedIndex]);

  // Scroll active item into view
  useEffect(() => {
    itemRefs.current[selectedIndex]?.scrollIntoView({
      block: 'nearest',
      behavior: 'smooth',
    });
  }, [selectedIndex]);

  // Trap focus inside the palette and restore it on close.
  useEffect(() => {
    if (!open || !dialogRef.current) return;
    const trap = createFocusTrap(dialogRef.current, {
      onEscape: () => setOpen(false),
      initialFocus: inputRef.current,
    });
    trap.activate();
    return () => trap.deactivate();
  }, [open]);

  const navigateTo = (slug: string) => {
    setOpen(false);
    window.location.href = slug;
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (flatItems.length ? (prev + 1) % flatItems.length : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (flatItems.length ? (prev - 1 + flatItems.length) % flatItems.length : 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (flatItems[selectedIndex]) {
        navigateTo(flatItems[selectedIndex].slug);
      }
    }
  };

  if (!open) return null;

  // Group items for rendering section headers
  let currentGroup = '';

  return (
    <div
      className="fixed inset-0 z-[80] flex items-start justify-center bg-black/70 p-3 pt-16 backdrop-blur-md sm:p-4 sm:pt-20 animate-fade-in"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) setOpen(false);
      }}
      role="presentation"
    >
      <div
        ref={dialogRef}
        className="w-full max-w-2xl overflow-hidden rounded-md border border-obs-border/80 bg-obs-sidebar/95 shadow-[0_25px_60px_-15px_rgba(0,0,0,0.7)] backdrop-blur-2xl animate-slide-up"
        role="dialog"
        aria-modal="true"
        aria-label="Command palette"
      >
        {/* Search Input Bar */}
        <div className="relative flex items-center gap-3 border-b border-obs-border/70 px-4 py-3.5 focus-within:border-obs-accent/60">
          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-sm border border-obs-border bg-obs-canvas-soft text-obs-text-secondary">
            <Search className="h-4 w-4" />
          </div>
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search chapters, concepts, systems, and commands…"
            className="flex-1 bg-transparent text-sm font-medium text-obs-text-primary placeholder:text-obs-text-muted focus:outline-none"
            role="combobox"
            aria-label="Search query"
            aria-controls="command-results"
            aria-expanded="true"
            aria-autocomplete="list"
            aria-activedescendant={flatItems[selectedIndex] ? `command-option-${selectedIndex}` : undefined}
          />
          {query ? (
            <button
              type="button"
              onClick={() => setQuery('')}
              className="flex h-6 w-6 items-center justify-center rounded-md text-obs-text-muted hover:bg-obs-surface hover:text-obs-text-primary transition-colors"
              aria-label="Clear search"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          ) : (
            <kbd className="hidden sm:inline-flex items-center rounded border border-obs-border/70 bg-obs-canvas-soft px-1.5 py-0.5 text-micro font-mono text-obs-text-muted">
              ESC
            </kbd>
          )}
        </div>

        {/* Results List */}
        <div
          id="command-results"
          className="obs-scrollbar max-h-[60vh] overflow-y-auto overscroll-contain p-2"
          role="listbox"
        >
          {loading && (
            <div className="space-y-2 p-2" aria-busy="true" aria-label="Indexing knowledge base">
              {[0, 1, 2, 3].map((item) => (
                <div key={item} className="flex items-center gap-3 rounded-md border border-obs-border/40 p-3">
                  <div className="skeleton h-8 w-8 shrink-0" />
                  <div className="min-w-0 flex-1 space-y-2">
                    <div className="skeleton h-3 w-2/3" />
                    <div className="skeleton h-2.5 w-1/2" />
                  </div>
                </div>
              ))}
            </div>
          )}

          {!loading && loadError && query.trim() && (
            <div className="px-4 py-10 text-center">
              <p className="text-sm font-semibold text-obs-text-primary">Search index unavailable</p>
              <p className="mt-1 text-xs text-obs-text-muted">
                The local index did not load. Retry, or use the full-text search page.
              </p>
              <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
                <button
                  type="button"
                  onClick={() => setReloadToken((value) => value + 1)}
                  className="btn btn-primary px-3 py-2 text-xs"
                >
                  Retry index
                </button>
                <a href="/search" className="btn btn-secondary px-3 py-2 text-xs">
                  Open search page
                </a>
              </div>
            </div>
          )}

          {!loading && !loadError && query.trim() && flatItems.length === 0 && (
            <div className="px-4 py-12 text-center">
              <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-sm border border-obs-border bg-obs-canvas-soft text-obs-text-muted mb-3">
                <Search className="h-5 w-5" />
              </div>
              <p className="text-sm font-semibold text-obs-text-primary">No results found for “{query}”</p>
              <p className="mt-1 text-xs text-obs-text-muted">
                Try searching for concepts like “Garbage Collection”, “Virtual Threads”, or “Kafka Partitioning”.
              </p>
              <a
                href={`/search?q=${encodeURIComponent(query)}`}
                className="mt-4 inline-flex items-center gap-1.5 rounded-sm border border-obs-accent/40 px-3 py-1.5 text-xs font-semibold text-obs-accent transition-colors hover:bg-obs-accent-subtle"
              >
                <span>Search full-text article contents</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </a>
            </div>
          )}

          {!loading && !loadError &&
            flatItems.map((item, index) => {
              const isSelected = index === selectedIndex;
              const Icon = item.icon || FileText;
              const showGroupHeader = item.group !== currentGroup;
              if (showGroupHeader) {
                currentGroup = item.group;
              }

              const domainMeta = item.domainBadge ? DOMAIN_COLORS[item.domainBadge] : null;

              return (
                <React.Fragment key={item.id}>
                  {showGroupHeader && (
                    <div className="px-3 pt-3 pb-1 text-micro font-bold uppercase tracking-[0.14em] text-obs-text-muted">
                      {item.group === 'Recent' && 'Recently Viewed'}
                      {item.group === 'Actions' && 'Quick Navigation & Tools'}
                      {item.group === 'Domains' && 'Curriculum Domains'}
                      {item.group === 'Topics' && 'Matching Topics & Chapters'}
                    </div>
                  )}

                  <div
                    ref={(el) => {
                      itemRefs.current[index] = el;
                    }}
                    id={`command-option-${index}`}
                    role="option"
                    aria-selected={isSelected}
                    tabIndex={-1}
                    onMouseEnter={() => setSelectedIndex(index)}
                    onClick={() => navigateTo(item.slug)}
                    className={`group relative flex cursor-pointer items-center gap-3 rounded-md px-3 py-2.5 transition-all duration-150 ${
                      isSelected
                        ? 'border border-obs-accent/40 bg-obs-accent-subtle text-obs-text-primary'
                        : 'border border-transparent text-obs-text-secondary hover:bg-obs-surface/60 hover:text-obs-text-primary'
                    }`}
                  >
                    {/* Selected item marker - color communicates state, not decoration */}
                    {isSelected && (
                      <span
                        className="absolute left-0 top-2 bottom-2 w-1 rounded-r-full bg-obs-accent"
                        aria-hidden="true"
                      />
                    )}

                    <div
                      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-sm border transition-all ${
                        isSelected
                          ? 'border-obs-accent/50 bg-obs-accent-subtle text-obs-accent'
                          : domainMeta
                            ? 'border-obs-border/70 text-obs-text-muted'
                            : 'border-obs-border/70 bg-obs-surface text-obs-text-muted'
                      }`}
                      style={
                        domainMeta && !isSelected
                          ? {
                              backgroundColor: domainMeta.bg,
                              borderColor: domainMeta.border,
                              color: domainMeta.text,
                            }
                          : undefined
                      }
                    >
                      <Icon className="h-4 w-4" />
                    </div>

                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2">
                        <span
                          className={`truncate text-sm ${
                            isSelected ? 'font-bold text-obs-text-primary' : 'font-medium text-obs-text-primary'
                          }`}
                        >
                          {item.title}
                        </span>
                        {item.group === 'Domains' && (
                          <span className="hidden sm:inline-flex rounded-sm border border-obs-border bg-obs-canvas-soft px-1.5 py-0.5 font-mono text-micro font-medium text-obs-text-muted">
                            Curriculum
                          </span>
                        )}
                      </div>
                      <span className="block truncate text-caption text-obs-text-muted">
                        {item.subtitle}
                      </span>
                    </div>

                    {/* Right Enter badge when selected */}
                    {isSelected ? (
                      <div className="flex shrink-0 items-center gap-1 rounded-sm border border-obs-accent/40 bg-obs-accent-subtle px-1.5 py-0.5 text-micro font-semibold text-obs-accent">
                        <span>Open</span>
                        <CornerDownLeft className="h-2.5 w-2.5" />
                      </div>
                    ) : (
                      <ArrowRight className="h-3.5 w-3.5 shrink-0 text-obs-text-muted opacity-0 group-hover:opacity-100 transition-opacity" />
                    )}
                  </div>
                </React.Fragment>
              );
            })}

          {!loading && !loadError && query.trim() && flatItems.length > 0 && (
            <a
              href={`/search?q=${encodeURIComponent(query)}`}
              className="mt-2 flex items-center justify-between rounded-md border border-obs-border/60 bg-obs-surface/40 px-3 py-2.5 text-caption font-semibold text-obs-accent hover:border-obs-accent/40 hover:bg-obs-surface transition-all"
            >
              <span className="flex items-center gap-2">
                <Sparkles className="h-3.5 w-3.5" />
                View all full-text matches in search page
              </span>
              <ArrowRight className="h-3.5 w-3.5" />
            </a>
          )}
        </div>

        {/* Footer info bar with Raycast keyboard hints */}
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-obs-border/70 bg-obs-canvas/60 px-4 py-2.5 text-caption text-obs-text-muted">
          <div className="flex items-center gap-3">
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-obs-border/70 bg-obs-canvas-soft px-1 text-micro font-mono">↑↓</kbd>
              Navigate
            </span>
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-obs-border/70 bg-obs-canvas-soft px-1 text-micro font-mono">↵</kbd>
              Select
            </span>
            <span className="inline-flex items-center gap-1">
              <kbd className="rounded border border-obs-border/70 bg-obs-canvas-soft px-1 text-micro font-mono">esc</kbd>
              Dismiss
            </span>
          </div>

          <a
            href="/search"
            className="flex items-center gap-1 font-semibold text-obs-accent hover:underline"
          >
            <Search className="h-3 w-3" />
            Full-text search engine
          </a>
        </div>
      </div>
    </div>
  );
}
