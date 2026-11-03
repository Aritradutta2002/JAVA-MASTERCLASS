import React, { useState, useEffect, useRef, useMemo } from 'react';
import MiniSearch from 'minisearch';
import type { SearchRecord } from '../lib/types';
import { Search, Compass, Eye, Moon, Sun, ArrowRight, ArrowLeft, CornerDownLeft, X, BookOpen, CheckSquare, Share2, Tag, List } from 'lucide-react';

export default function CommandPalette() {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [records, setRecords] = useState<SearchRecord[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);
  const [isLoading, setIsLoading] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // Initialize MiniSearch instance
  const miniSearch = useMemo(() => {
    return new MiniSearch({
      fields: ['title', 'category', 'headings', 'content', 'tags'],
      storeFields: ['title', 'slug', 'domain', 'domainTitle', 'category', 'content'],
      searchOptions: {
        boost: { title: 4, category: 2, headings: 1.5 },
        prefix: true,
        fuzzy: 0.2,
      },
    });
  }, []);

  // Fetch search index lazily on first open
  useEffect(() => {
    if (isOpen && records.length === 0 && !isLoading) {
      setIsLoading(true);
      fetch('/search-index.json')
        .then((res) => res.json())
        .then((data: SearchRecord[]) => {
          setRecords(data);
          miniSearch.addAll(data);
          setIsLoading(false);
        })
        .catch((err) => {
          console.error('Failed to load search index:', err);
          setIsLoading(false);
        });
    }
  }, [isOpen, records.length, isLoading, miniSearch]);

  // Global keydown listener for Cmd+K / Ctrl+K
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setIsOpen((prev) => !prev);
      } else if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    };

    const searchBtn = document.getElementById('open-command-palette-btn');
    if (searchBtn) {
      searchBtn.addEventListener('click', () => setIsOpen(true));
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen]);

  // Auto-focus input when opened
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => inputRef.current?.focus(), 50);
      setSelectedIndex(0);
    } else {
      setQuery('');
    }
  }, [isOpen]);

  // Static quick actions
  const defaultActions = useMemo(
    () => [
      { id: 'act-graph', title: 'Open Knowledge Graph Explorer', desc: 'Interactive 2D concept relationship visualizer', icon: Compass, action: () => (window.location.href = '/graph') },
      { id: 'act-checklist', title: 'Open Master Study Checklist', desc: '12-16 week Java backend preparation tracker', icon: CheckSquare, action: () => (window.location.href = '/checklist') },
      { id: 'act-tags', title: 'Browse All Taxonomy Tags', desc: 'Explore topics categorized by technical tags and clusters', icon: Tag, action: () => (window.location.href = '/tags') },
      { id: 'act-index', title: 'Complete Topic Index (A-Z)', desc: 'Alphabetical directory of all 433 masterclass topics', icon: List, action: () => (window.location.href = '/index-all') },
      { id: 'act-cj', title: 'Jump to Core Java', desc: '18 modules covering OOP, JMM, Concurrency, and Design Patterns', icon: BookOpen, action: () => (window.location.href = '/core-java') },
      { id: 'act-sb', title: 'Jump to Spring Boot', desc: '14 modules covering Spring MVC, JPA, Security, Microservices', icon: BookOpen, action: () => (window.location.href = '/spring-boot') },
      { id: 'act-sd', title: 'Jump to System Design', desc: 'Distributed Systems, 20 HLD case studies, and 8 LLD problems', icon: BookOpen, action: () => (window.location.href = '/system-design') },
      { id: 'act-db', title: 'Jump to Database & Caching', desc: 'SQL & NoSQL internals, query optimization, Redis', icon: BookOpen, action: () => (window.location.href = '/database-caching') },
      { id: 'act-dsa', title: 'Jump to Java DSA', desc: 'Foundations, Graph masterclass, DP, and 621 Java solutions', icon: BookOpen, action: () => (window.location.href = '/java-dsa') },
      {
        id: 'act-copy',
        title: 'Copy Current Page URL',
        desc: 'Copy full URL of current page to clipboard',
        icon: Share2,
        action: async () => {
          try {
            await navigator.clipboard.writeText(window.location.href);
          } catch (e) {}
          setIsOpen(false);
        },
      },
      {
        id: 'act-back',
        title: 'Go Back in History',
        desc: 'Return to previous page in reading history',
        icon: ArrowLeft,
        action: () => {
          window.history.back();
          setIsOpen(false);
        },
      },
      {
        id: 'act-forward',
        title: 'Go Forward in History',
        desc: 'Navigate forward in reading history',
        icon: ArrowRight,
        action: () => {
          window.history.forward();
          setIsOpen(false);
        },
      },
      {
        id: 'act-theme',
        title: 'Toggle Dark / Light Theme',
        desc: 'Switch between Obsidian velvety dark and paper light themes',
        icon: Moon,
        action: () => {
          const current = document.documentElement.getAttribute('data-theme');
          const next = current === 'light' ? 'dark' : 'light';
          document.documentElement.setAttribute('data-theme', next);
          localStorage.setItem('theme', next);
          setIsOpen(false);
        },
      },
      {
        id: 'act-zen',
        title: 'Toggle Zen / Focus Reading Mode',
        desc: 'Hide sidebars and center prose for distraction-free reading',
        icon: Eye,
        action: () => {
          document.body.classList.toggle('zen-mode');
          setIsOpen(false);
        },
      },
    ],
    []
  );

  // Search results
  const searchResults = useMemo(() => {
    if (!query.trim()) return [];
    if (records.length === 0) return [];

    const results = miniSearch.search(query);
    return results.slice(0, 15).map((r) => {
      const full = records.find((rec) => rec.id === r.id);
      return {
        id: r.id,
        title: full?.title || r.title,
        slug: full?.slug || r.slug,
        domain: full?.domain || r.domain,
        domainTitle: full?.domainTitle || r.domainTitle,
        category: full?.category || r.category,
        content: full?.content || '',
      };
    });
  }, [query, records, miniSearch]);

  const items = query.trim() ? searchResults : defaultActions;

  // Handle keyboard navigation inside palette
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev + 1) % items.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (prev - 1 + items.length) % items.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const current = items[selectedIndex];
      if (current) {
        if ('action' in current && typeof current.action === 'function') {
          current.action();
        } else if ('slug' in current) {
          window.location.href = current.slug;
        }
      }
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/70 p-4 pt-[12vh] backdrop-blur-sm animate-fade-in">
      <div
        className="w-full max-w-2xl overflow-hidden rounded-xl border border-obs-border bg-obs-sidebar shadow-2xl transition-all"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Search Input Bar */}
        <div className="relative flex items-center border-b border-obs-border px-4 py-3">
          <Search className="h-5 w-5 text-obs-accent shrink-0" />
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelectedIndex(0);
            }}
            onKeyDown={handleKeyDown}
            placeholder="Search all 433 docs, headings, or run a command..."
            className="ml-3 flex-1 bg-transparent text-sm text-obs-text-primary placeholder:text-obs-text-muted focus:outline-none"
          />
          {query && (
            <button
              onClick={() => setQuery('')}
              className="text-obs-text-muted hover:text-obs-text-primary p-1"
            >
              <X className="h-4 w-4" />
            </button>
          )}
          <kbd className="ml-2 rounded border border-obs-border bg-obs-canvas px-1.5 py-0.5 text-[10px] font-mono text-obs-text-muted">
            ESC
          </kbd>
        </div>

        {/* Results List */}
        <div
          ref={listRef}
          className="max-h-[60vh] overflow-y-auto p-2 space-y-1"
        >
          {isLoading && (
            <div className="py-8 text-center text-xs text-obs-text-muted">
              Loading knowledge index...
            </div>
          )}

          {!isLoading && query.trim() && searchResults.length === 0 && (
            <div className="py-10 text-center text-sm text-obs-text-muted">
              No matching topics found for "{query}".
            </div>
          )}

          {!query.trim() && (
            <div className="px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-obs-text-muted">
              Quick Navigation & Commands
            </div>
          )}

          {items.map((item, idx) => {
            const isSelected = idx === selectedIndex;
            const isAction = 'action' in item;

            return (
              <div
                key={item.id}
                onMouseEnter={() => setSelectedIndex(idx)}
                onClick={() => {
                  if (isAction && typeof item.action === 'function') {
                    item.action();
                  } else if ('slug' in item) {
                    window.location.href = item.slug;
                  }
                }}
                className={`flex cursor-pointer items-center justify-between rounded-lg px-3 py-2.5 text-xs transition-colors ${
                  isSelected
                    ? 'bg-obs-accent/15 border border-obs-accent/40 text-obs-text-primary'
                    : 'text-obs-text-secondary hover:bg-obs-hover'
                }`}
              >
                <div className="flex items-center gap-3 truncate">
                  <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-md border ${
                    isSelected ? 'border-obs-accent bg-obs-accent/20 text-obs-accent' : 'border-obs-border bg-obs-surface text-obs-text-muted'
                  }`}>
                    {isAction && item.icon ? (
                      <item.icon className="h-4 w-4" />
                    ) : (
                      <BookOpen className="h-4 w-4" />
                    )}
                  </div>
                  <div className="truncate">
                    <div className="font-medium text-obs-text-primary truncate">
                      {item.title}
                    </div>
                    <div className="text-[11px] text-obs-text-muted truncate">
                      {isAction ? item.desc : `${item.domainTitle} · ${item.category}`}
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 pl-2 shrink-0">
                  {isSelected && (
                    <CornerDownLeft className="h-3.5 w-3.5 text-obs-accent" />
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer shortcuts hint */}
        <div className="flex items-center justify-between border-t border-obs-border bg-obs-canvas/50 px-4 py-2 text-[11px] text-obs-text-muted">
          <div className="flex items-center gap-3">
            <span><kbd className="font-mono">↑↓</kbd> Navigate</span>
            <span><kbd className="font-mono">↵</kbd> Open</span>
            <span><kbd className="font-mono">ESC</kbd> Close</span>
          </div>
          <div>Obsidian Knowledge Engine</div>
        </div>
      </div>
    </div>
  );
}
