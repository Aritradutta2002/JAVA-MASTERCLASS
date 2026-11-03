import React, { useEffect, useMemo, useRef, useState } from 'react';
import MiniSearch from 'minisearch';
import type { SearchRecord } from '../lib/types';
import {
  ArrowRight,
  BookOpen,
  CheckSquare,
  Compass,
  CornerDownLeft,
  FileText,
  Library,
  Search,
  Tag,
  Waypoints,
  X,
} from 'lucide-react';

interface ResultItem {
  id: string;
  title: string;
  subtitle: string;
  slug: string;
}

const QUICK_ACTIONS = [
  { id: 'library', title: 'Browse the complete library', subtitle: 'Every chapter, module, and reference', href: '/library', icon: Library },
  { id: 'paths', title: 'Learning paths', subtitle: 'Structured study plans by role and timeline', href: '/paths', icon: Waypoints },
  { id: 'search', title: 'Full-text search', subtitle: 'Search inside every chapter with Pagefind', href: '/search', icon: Search },
  { id: 'graph', title: 'Knowledge graph', subtitle: 'Explore connections between concepts', href: '/graph', icon: Compass },
  { id: 'checklist', title: 'Study checklist', subtitle: 'Track progress across the curriculum', href: '/checklist', icon: CheckSquare },
  { id: 'tags', title: 'Tags and concepts', subtitle: 'Browse the taxonomy', href: '/tags', icon: Tag },
  { id: 'overview', title: 'Repository overview', subtitle: 'Start here', href: '/overview', icon: BookOpen },
];

export default function CommandPalette() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [records, setRecords] = useState<SearchRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [selected, setSelected] = useState(0);
  const [recent, setRecent] = useState<{ title: string; slug: string }[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

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

  useEffect(() => {
    const openHandler = () => setOpen(true);
    window.addEventListener('obsidian:open-search', openHandler);
    const button = document.getElementById('open-command-palette-btn');
    button?.addEventListener('click', openHandler);
    return () => {
      window.removeEventListener('obsidian:open-search', openHandler);
      button?.removeEventListener('click', openHandler);
    };
  }, []);

  useEffect(() => {
    if (!open) return;
    try {
      setRecent(JSON.parse(localStorage.getItem('obsidian-recent-pages') || '[]').slice(0, 4));
    } catch {
      setRecent([]);
    }
    if (records.length === 0 && !loading) {
      setLoading(true);
      fetch('/search-index.json')
        .then((response) => response.json())
        .then((data: SearchRecord[]) => {
          setRecords(data);
          miniSearch.removeAll();
          miniSearch.addAll(data);
        })
        .catch((error) => console.error('Failed to load search index', error))
        .finally(() => setLoading(false));
    }
    const timer = window.setTimeout(() => inputRef.current?.focus(), 30);
    return () => window.clearTimeout(timer);
  }, [open, records.length, loading, miniSearch]);

  useEffect(() => {
    if (!open) setQuery('');
    setSelected(0);
  }, [open, query]);

  const searchResults = useMemo<ResultItem[]>(() => {
    if (!query.trim() || records.length === 0) return [];
    return miniSearch.search(query).slice(0, 18).map((result) => {
      const record = records.find((item) => item.id === result.id);
      return {
        id: result.id,
        title: String(result.title ?? record?.title ?? 'Untitled'),
        subtitle: record ? `${record.domainTitle} · ${record.category}` : '',
        slug: String(result.slug ?? record?.slug ?? '/'),
      };
    });
  }, [query, records, miniSearch]);

  const items: ResultItem[] = query.trim()
    ? searchResults
    : [
        ...recent.map((item) => ({
          id: `recent-${item.slug}`,
          title: item.title,
          subtitle: 'Recently viewed',
          slug: item.slug,
        })),
        ...QUICK_ACTIONS.map((action) => ({
          id: action.id,
          title: action.title,
          subtitle: action.subtitle,
          slug: action.href,
        })),
      ];

  useEffect(() => {
    if (selected >= items.length) setSelected(0);
  }, [items.length, selected]);

  useEffect(() => {
    if (!open) return;
    const handler = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [open]);

  const go = (slug: string) => {
    setOpen(false);
    window.location.href = slug;
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setSelected((value) => (items.length ? (value + 1) % items.length : 0));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setSelected((value) => (items.length ? (value - 1 + items.length) % items.length : 0));
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (items[selected]) go(items[selected].slug);
    }
  };

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[80] flex items-start justify-center bg-black/70 p-4 pt-[10vh] backdrop-blur-sm animate-fade-in"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) setOpen(false);
      }}
      role="presentation"
    >
      <div
        className="w-full max-w-2xl overflow-hidden rounded-2xl border border-obs-border bg-obs-sidebar shadow-2xl animate-slide-up"
        role="dialog"
        aria-modal="true"
        aria-label="Search the knowledge base"
      >
        <div className="flex items-center gap-3 border-b border-obs-border px-4 py-3.5">
          <Search className="h-4 w-4 shrink-0 text-obs-accent" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Search chapters, concepts, and commands…"
            className="flex-1 bg-transparent text-sm text-obs-text-primary placeholder:text-obs-text-muted focus:outline-none"
            aria-label="Search query"
            aria-controls="command-results"
            aria-autocomplete="list"
          />
          {query ? (
            <button
              type="button"
              onClick={() => setQuery('')}
              className="rounded p-1 text-obs-text-muted hover:text-obs-text-primary"
              aria-label="Clear search"
            >
              <X className="h-4 w-4" />
            </button>
          ) : (
            <kbd className="text-[0.62rem]">ESC</kbd>
          )}
        </div>

        <div id="command-results" ref={listRef} className="max-h-[62vh] overflow-y-auto p-2" role="listbox">
          {loading && (
            <p className="px-3 py-8 text-center text-xs text-obs-text-muted">Loading the index…</p>
          )}

          {!loading && query.trim() && searchResults.length === 0 && (
            <div className="px-3 py-10 text-center">
              <p className="text-sm font-medium text-obs-text-primary">No matching topics</p>
              <p className="mt-1 text-xs text-obs-text-muted">
                Try a broader term such as “JVM”, “Kafka”, or “dynamic programming”.
              </p>
              <a href={`/search?q=${encodeURIComponent(query)}`} className="mt-3 inline-block text-xs font-semibold text-obs-accent hover:underline">
                Search inside full text →
              </a>
            </div>
          )}

          {!loading && !query.trim() && (
            <p className="px-3 pb-1 pt-2 text-[0.62rem] font-bold uppercase tracking-[0.14em] text-obs-text-muted">
              Jump to
            </p>
          )}

          {items.map((item, index) => {
            const isSelected = index === selected;
            const quick = QUICK_ACTIONS.find((action) => action.id === item.id);
            const Icon = quick?.icon ?? FileText;
            return (
              <div
                key={item.id}
                role="option"
                aria-selected={isSelected}
                tabIndex={-1}
                onMouseEnter={() => setSelected(index)}
                onClick={() => go(item.slug)}
                className={`flex cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 transition-colors ${
                  isSelected
                    ? 'border border-obs-accent/40 bg-obs-accent/10'
                    : 'border border-transparent hover:bg-obs-hover'
                }`}
              >
                <span
                  className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border ${
                    isSelected
                      ? 'border-obs-accent/40 bg-obs-accent/15 text-obs-accent'
                      : 'border-obs-border bg-obs-surface text-obs-text-muted'
                  }`}
                >
                  <Icon className="h-4 w-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-[0.84rem] font-semibold text-obs-text-primary">
                    {item.title}
                  </span>
                  <span className="block truncate text-[0.68rem] text-obs-text-muted">{item.subtitle}</span>
                </span>
                {isSelected && <CornerDownLeft className="h-3.5 w-3.5 shrink-0 text-obs-accent" />}
              </div>
            );
          })}

          {!loading && query.trim() && searchResults.length > 0 && (
            <a
              href={`/search?q=${encodeURIComponent(query)}`}
              className="mt-1 flex items-center justify-between rounded-lg px-3 py-2 text-[0.72rem] font-semibold text-obs-accent hover:bg-obs-hover"
            >
              See all full-text results
              <ArrowRight className="h-3.5 w-3.5" />
            </a>
          )}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-obs-border bg-obs-canvas/50 px-4 py-2 text-[0.62rem] text-obs-text-muted">
          <div className="flex items-center gap-3">
            <span><kbd>↑↓</kbd> navigate</span>
            <span><kbd>↵</kbd> open</span>
            <span><kbd>esc</kbd> close</span>
          </div>
          <a href="/search" className="font-semibold text-obs-text-muted hover:text-obs-accent">
            Full-text search
          </a>
        </div>
      </div>
    </div>
  );
}
