import React, { useState, useEffect, useMemo, useRef } from 'react';
import type { NavigationNode } from '../lib/types';
import { ChevronRight, ChevronDown, Folder, FileText, Search, X, Star, Clock, Trash2 } from 'lucide-react';

interface Props {
  navigation: NavigationNode[];
  currentSlug: string;
}

interface PinnedPage {
  title: string;
  slug: string;
  domain?: string;
}

interface RecentPage {
  title: string;
  slug: string;
}

export default function SidebarTree({ navigation, currentSlug }: Props) {
  const [filter, setFilter] = useState('');
  const [expandedNodes, setExpandedNodes] = useState<Record<string, boolean>>({});
  const [pinnedPages, setPinnedPages] = useState<PinnedPage[]>([]);
  const [recentPages, setRecentPages] = useState<RecentPage[]>([]);
  const [showPinned, setShowPinned] = useState(true);
  const [showRecent, setShowRecent] = useState(true);
  const activeLinkRef = useRef<HTMLAnchorElement>(null);

  // Load pinned and recent notes from localStorage
  const loadPinsAndRecents = () => {
    try {
      const pins = JSON.parse(localStorage.getItem('obsidian-pinned-pages') || '[]');
      setPinnedPages(pins);
    } catch {}
    try {
      const recents = JSON.parse(localStorage.getItem('obsidian-recent-pages') || '[]');
      setRecentPages(recents);
    } catch {}
  };

  useEffect(() => {
    loadPinsAndRecents();
    window.addEventListener('obsidian:pins-updated', loadPinsAndRecents);
    window.addEventListener('obsidian:recents-updated', loadPinsAndRecents);
    return () => {
      window.removeEventListener('obsidian:pins-updated', loadPinsAndRecents);
      window.removeEventListener('obsidian:recents-updated', loadPinsAndRecents);
    };
  }, []);

  // Initialize expanded nodes from localStorage or auto-expand the active path
  useEffect(() => {
    try {
      const saved = localStorage.getItem('obsidian-sidebar-expanded');
      const initial = saved ? JSON.parse(saved) : {};

      // Auto-expand any category containing the current page
      navigation.forEach((domainNode) => {
        domainNode.children?.forEach((catNode) => {
          const hasActiveChild = catNode.children?.some((docNode) => docNode.slug === currentSlug);
          if (hasActiveChild || catNode.slug === currentSlug) {
            initial[catNode.id] = true;
            initial[domainNode.id] = true;
          }
        });
      });

      setExpandedNodes(initial);
    } catch {
      // fallback
    }
  }, [currentSlug, navigation]);

  // Scroll active item into view smoothly
  useEffect(() => {
    if (activeLinkRef.current) {
      activeLinkRef.current.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }
  }, [currentSlug, expandedNodes]);

  const toggleExpand = (id: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setExpandedNodes((prev) => {
      const next = { ...prev, [id]: !prev[id] };
      try {
        localStorage.setItem('obsidian-sidebar-expanded', JSON.stringify(next));
      } catch {}
      return next;
    });
  };

  const unpinPage = (slug: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      const updated = pinnedPages.filter((p) => p.slug !== slug);
      localStorage.setItem('obsidian-pinned-pages', JSON.stringify(updated));
      setPinnedPages(updated);
      window.dispatchEvent(new CustomEvent('obsidian:pins-updated'));
    } catch {}
  };

  const clearRecents = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      localStorage.removeItem('obsidian-recent-pages');
      setRecentPages([]);
      window.dispatchEvent(new CustomEvent('obsidian:recents-updated'));
    } catch {}
  };

  // Filter tree when searching
  const filteredNav = useMemo(() => {
    if (!filter.trim()) return navigation;
    const query = filter.toLowerCase();

    function filterNode(node: NavigationNode): NavigationNode | null {
      const matchesTitle = node.title.toLowerCase().includes(query);
      if (node.type === 'doc') {
        return matchesTitle ? node : null;
      }

      const matchingChildren = (node.children || [])
        .map(filterNode)
        .filter((n): n is NavigationNode => n !== null);

      if (matchingChildren.length > 0 || matchesTitle) {
        return {
          ...node,
          children: matchingChildren,
        };
      }
      return null;
    }

    return navigation
      .map(filterNode)
      .filter((n): n is NavigationNode => n !== null);
  }, [navigation, filter]);

  return (
    <div className="flex h-full flex-col text-sm text-obs-text-secondary select-none">
      {/* Search Filter Header */}
      <div className="p-3 border-b border-obs-border">
        <div className="relative flex items-center">
          <Search className="absolute left-2.5 h-3.5 w-3.5 text-obs-text-muted pointer-events-none" />
          <input
            type="text"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter files & topics..."
            className="w-full rounded-md border border-obs-border bg-obs-canvas py-1.5 pl-8 pr-7 text-xs text-obs-text-primary placeholder:text-obs-text-muted focus:border-obs-accent focus:outline-none transition-colors"
          />
          {filter && (
            <button
              onClick={() => setFilter('')}
              className="absolute right-2 text-obs-text-muted hover:text-obs-text-primary"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Navigation Scroll Area */}
      <div className="flex-1 overflow-y-auto px-2 py-3 space-y-4">
        {/* Pinned Notes Section */}
        {pinnedPages.length > 0 && !filter && (
          <div className="space-y-1 pb-2 border-b border-obs-border/40">
            <button
              onClick={() => setShowPinned(!showPinned)}
              className="flex w-full items-center justify-between px-2 py-1 text-xs font-bold uppercase tracking-wider text-amber-400 hover:text-amber-300 rounded hover:bg-obs-hover transition-colors"
            >
              <div className="flex items-center gap-1.5 truncate">
                {showPinned ? <ChevronDown className="h-3 w-3 shrink-0" /> : <ChevronRight className="h-3 w-3 shrink-0" />}
                <Star className="h-3 w-3 fill-amber-400 text-amber-400 shrink-0" />
                <span className="truncate">Pinned Notes</span>
              </div>
              <span className="rounded-full bg-amber-400/10 px-1.5 py-0.2 text-[10px] text-amber-400 border border-amber-400/30">
                {pinnedPages.length}
              </span>
            </button>
            {showPinned && (
              <div className="pl-2 space-y-0.5 border-l border-amber-400/30 ml-2">
                {pinnedPages.map((pin) => {
                  const isActive = pin.slug === currentSlug;
                  return (
                    <div key={pin.slug} className="group flex items-center justify-between rounded px-2 py-1 text-xs hover:bg-obs-hover transition-colors">
                      <a
                        href={pin.slug}
                        className={`flex items-center gap-1.5 truncate flex-1 ${isActive ? 'text-obs-accent font-medium' : 'text-obs-text-secondary hover:text-obs-text-primary'}`}
                      >
                        <FileText className="h-3 w-3 shrink-0 text-amber-400/70" />
                        <span className="truncate">{pin.title}</span>
                      </a>
                      <button
                        onClick={(e) => unpinPage(pin.slug, e)}
                        title="Unpin"
                        className="opacity-0 group-hover:opacity-100 text-obs-text-muted hover:text-obs-text-primary p-0.5 transition-opacity"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Recently Viewed Section */}
        {recentPages.length > 0 && !filter && (
          <div className="space-y-1 pb-2 border-b border-obs-border/40">
            <div className="flex w-full items-center justify-between px-2 py-1 text-xs font-bold uppercase tracking-wider text-obs-text-muted">
              <button
                onClick={() => setShowRecent(!showRecent)}
                className="flex items-center gap-1.5 truncate hover:text-obs-text-primary flex-1 text-left"
              >
                {showRecent ? <ChevronDown className="h-3 w-3 shrink-0" /> : <ChevronRight className="h-3 w-3 shrink-0" />}
                <Clock className="h-3 w-3 text-obs-accent shrink-0" />
                <span className="truncate">Recent Notes</span>
              </button>
              <div className="flex items-center gap-1.5">
                <button
                  onClick={clearRecents}
                  title="Clear recent history"
                  className="hover:text-rose-400 text-obs-text-muted p-0.5 transition-colors"
                >
                  <Trash2 className="h-2.5 w-2.5" />
                </button>
                <span className="rounded-full bg-obs-surface px-1.5 py-0.2 text-[10px] text-obs-text-muted border border-obs-border">
                  {recentPages.length}
                </span>
              </div>
            </div>
            {showRecent && (
              <div className="pl-2 space-y-0.5 border-l border-obs-border/40 ml-2">
                {recentPages.slice(0, 5).map((rec) => {
                  const isActive = rec.slug === currentSlug;
                  return (
                    <a
                      key={rec.slug}
                      href={rec.slug}
                      className={`flex items-center gap-1.5 rounded px-2 py-1 text-xs truncate transition-colors ${isActive ? 'bg-obs-accent/15 text-obs-accent font-medium' : 'text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'}`}
                    >
                      <Clock className="h-2.5 w-2.5 shrink-0 text-obs-text-muted" />
                      <span className="truncate">{rec.title}</span>
                    </a>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* Main Navigation Tree */}
        {filteredNav.map((domainNode) => {
          const isDomainExpanded = expandedNodes[domainNode.id] !== false; // default open

          return (
            <div key={domainNode.id} className="space-y-1">
              {/* Domain Header */}
              <button
                onClick={(e) => toggleExpand(domainNode.id, e)}
                className="flex w-full items-center justify-between px-2 py-1 text-xs font-bold uppercase tracking-wider text-obs-text-muted hover:text-obs-text-primary rounded hover:bg-obs-hover transition-colors"
              >
                <div className="flex items-center gap-1.5 truncate">
                  {isDomainExpanded ? (
                    <ChevronDown className="h-3 w-3 shrink-0" />
                  ) : (
                    <ChevronRight className="h-3 w-3 shrink-0" />
                  )}
                  <span className="truncate">{domainNode.title}</span>
                </div>
                {domainNode.badge && (
                  <span className="rounded-full bg-obs-surface px-1.5 py-0.2 text-[10px] font-normal text-obs-text-muted border border-obs-border">
                    {domainNode.badge}
                  </span>
                )}
              </button>

              {/* Categories */}
              {isDomainExpanded && (
                <div className="pl-1 space-y-0.5 border-l border-obs-border/50 ml-2">
                  {domainNode.children?.map((catNode) => {
                    const isCatExpanded = !!expandedNodes[catNode.id] || !!filter;
                    const isCatActive = catNode.slug === currentSlug;

                    return (
                      <div key={catNode.id} className="space-y-0.5">
                        {/* Category item */}
                        <div
                          className={`group flex items-center justify-between rounded px-2 py-1 text-xs transition-colors ${
                            isCatActive
                              ? 'bg-obs-accent/15 text-obs-accent font-medium'
                              : 'text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'
                          }`}
                        >
                          <div
                            onClick={(e) => toggleExpand(catNode.id, e)}
                            className="flex flex-1 items-center gap-1.5 cursor-pointer truncate"
                          >
                            {isCatExpanded ? (
                              <ChevronDown className="h-3 w-3 shrink-0 text-obs-text-muted group-hover:text-obs-text-primary" />
                            ) : (
                              <ChevronRight className="h-3 w-3 shrink-0 text-obs-text-muted group-hover:text-obs-text-primary" />
                            )}
                            <Folder className="h-3.5 w-3.5 shrink-0 text-obs-text-muted" />
                            {catNode.slug ? (
                              <a
                                href={catNode.slug}
                                onClick={(e) => e.stopPropagation()}
                                className="truncate hover:underline"
                              >
                                {catNode.title}
                              </a>
                            ) : (
                              <span className="truncate">{catNode.title}</span>
                            )}
                          </div>
                          {catNode.badge && (
                            <span className="text-[10px] text-obs-text-muted pl-1">
                              {catNode.badge}
                            </span>
                          )}
                        </div>

                        {/* Chapter Documents */}
                        {isCatExpanded && catNode.children && (
                          <div className="pl-4 space-y-0.5 border-l border-obs-border/30 ml-2">
                            {catNode.children.map((docNode) => {
                              const isActive = docNode.slug === currentSlug;

                              return (
                                <a
                                  key={docNode.id}
                                  ref={isActive ? activeLinkRef : null}
                                  href={docNode.slug}
                                  className={`flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-all ${
                                    isActive
                                      ? 'bg-obs-accent text-white font-medium shadow-sm'
                                      : 'text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'
                                  }`}
                                >
                                  <FileText className={`h-3 w-3 shrink-0 ${isActive ? 'text-white' : 'text-obs-text-muted'}`} />
                                  <span className="truncate">{docNode.title}</span>
                                </a>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
