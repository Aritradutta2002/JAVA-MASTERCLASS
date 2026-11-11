import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { NavigationNode } from '../lib/types';
import {
  BookMarked,
  BookOpen,
  ChevronRight,
  ChevronsDownUp,
  ChevronsUpDown,
  Clock,
  Cloud,
  Code2,
  Compass,
  Container,
  Cpu,
  Database,
  FileText,
  Folder,
  FolderOpen,
  FolderTree,
  GitBranch,
  Layers,
  Library,
  PanelLeftClose,
  Pin,
  Search,
  Star,
  Tag,
  Trash2,
  Waypoints,
  X,
} from 'lucide-react';
import { DOMAIN_COLORS } from '../lib/constants';

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

const DOMAIN_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  'core-java': Cpu,
  'spring-boot': Layers,
  'system-design': Compass,
  'database-caching': Database,
  'java-dsa': Code2,
  'cloud-devops': Cloud,
  'docker-java-backend': Container,
  root: BookOpen,
};

const QUICK_LINKS = [
  { label: 'Library', href: '/library', icon: Library },
  { label: 'Paths', href: '/paths', icon: Waypoints },
  { label: 'Reference', href: '/reference/quick-references', icon: BookMarked },
  { label: 'Tags', href: '/tags', icon: Tag },
];

function normalizeSlug(value: string) {
  return value.replace(/\/+$/, '') || '/';
}

function findAncestors(nodes: NavigationNode[], target: string, trail: string[] = []): string[] | null {
  for (const node of nodes) {
    const nextTrail = [...trail, node.id];
    if (node.slug && normalizeSlug(node.slug) === target) return nextTrail;
    if (node.children) {
      const found = findAncestors(node.children, target, nextTrail);
      if (found) return found;
    }
  }
  return null;
}

function filterNodes(nodes: NavigationNode[], query: string): NavigationNode[] {
  const q = query.toLowerCase();
  const visit = (node: NavigationNode): NavigationNode | null => {
    const children = node.children
      ?.map(visit)
      .filter((child): child is NavigationNode => child !== null);
    const matches = node.title.toLowerCase().includes(q);
    if (matches || (children && children.length > 0)) {
      return { ...node, children };
    }
    return null;
  };
  return nodes.map(visit).filter((node): node is NavigationNode => node !== null);
}

export default function SidebarTree({ navigation, currentSlug }: Props) {
  const [filter, setFilter] = useState('');
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [pinned, setPinned] = useState<PinnedPage[]>([]);
  const [recents, setRecents] = useState<RecentPage[]>([]);
  const [showPinned, setShowPinned] = useState(true);
  const [showRecent, setShowRecent] = useState(true);
  const activeRef = useRef<HTMLAnchorElement>(null);
  const filterId = useId();
  const target = normalizeSlug(currentSlug);

  const allFolderIds = useMemo(() => {
    const ids: string[] = [];
    const walk = (nodes: NavigationNode[]) => {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          ids.push(node.id);
          walk(node.children);
        }
      }
    };
    walk(navigation);
    return ids;
  }, [navigation]);

  const totalTopics = useMemo(() => {
    let count = 0;
    const walk = (nodes: NavigationNode[]) => {
      for (const node of nodes) {
        if (node.type === 'doc') count++;
        if (node.children) walk(node.children);
      }
    };
    walk(navigation);
    return count;
  }, [navigation]);

  const allExpanded = useMemo(() => {
    if (allFolderIds.length === 0) return false;
    return allFolderIds.every((id) => expanded[id]);
  }, [allFolderIds, expanded]);

  const toggleAll = () => {
    if (allExpanded) {
      const ancestors = findAncestors(navigation, target) || [];
      const next: Record<string, boolean> = {};
      for (const id of ancestors) next[id] = true;
      for (const node of navigation) {
        if (node.type === 'domain') {
          next[node.id] = node.domain === 'core-java' || node.domain === 'root';
        }
      }
      setExpanded(next);
      try {
        localStorage.setItem('obsidian-sidebar-expanded', JSON.stringify(next));
      } catch (_) {}
    } else {
      const next: Record<string, boolean> = {};
      for (const id of allFolderIds) next[id] = true;
      setExpanded(next);
      try {
        localStorage.setItem('obsidian-sidebar-expanded', JSON.stringify(next));
      } catch (_) {}
    }
  };

  const handleFoldSidebar = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    window.dispatchEvent(
      new CustomEvent('obsidian:toggle-sidebar', { detail: { folded: true } })
    );
  };

  const loadStored = () => {
    try {
      setPinned(JSON.parse(localStorage.getItem('obsidian-pinned-pages') || '[]'));
    } catch {
      setPinned([]);
    }
    try {
      setRecents(JSON.parse(localStorage.getItem('obsidian-recent-pages') || '[]'));
    } catch {
      setRecents([]);
    }
  };

  useEffect(() => {
    loadStored();
    window.addEventListener('obsidian:pins-updated', loadStored);
    window.addEventListener('obsidian:recents-updated', loadStored);
    return () => {
      window.removeEventListener('obsidian:pins-updated', loadStored);
      window.removeEventListener('obsidian:recents-updated', loadStored);
    };
  }, []);

  useEffect(() => {
    let saved: Record<string, boolean> = {};
    try {
      saved = JSON.parse(localStorage.getItem('obsidian-sidebar-expanded') || '{}');
    } catch {
      saved = {};
    }
    const ancestors = findAncestors(navigation, target) || [];
    const seed: Record<string, boolean> = { ...saved };
    for (const id of ancestors) seed[id] = true;
    for (const node of navigation) {
      if (node.type === 'domain' && seed[node.id] === undefined) {
        seed[node.id] = node.domain === 'core-java' || node.domain === 'root';
      }
    }
    setExpanded(seed);
  }, [navigation, target]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      activeRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }, 80);
    return () => window.clearTimeout(timer);
  }, [target]);

  const toggle = (id: string, event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setExpanded((prev) => {
      const next = { ...prev, [id]: !prev[id] };
      try {
        localStorage.setItem('obsidian-sidebar-expanded', JSON.stringify(next));
      } catch {
        /* ignore quota errors */
      }
      return next;
    });
  };

  const unpin = (slug: string, event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    const updated = pinned.filter((item) => item.slug !== slug);
    setPinned(updated);
    try {
      localStorage.setItem('obsidian-pinned-pages', JSON.stringify(updated));
    } catch {
      /* ignore */
    }
    window.dispatchEvent(new CustomEvent('obsidian:pins-updated'));
  };

  const clearRecents = (event: React.MouseEvent) => {
    event.preventDefault();
    event.stopPropagation();
    setRecents([]);
    try {
      localStorage.removeItem('obsidian-recent-pages');
    } catch {
      /* ignore */
    }
    window.dispatchEvent(new CustomEvent('obsidian:recents-updated'));
  };

  const visibleNavigation = useMemo(
    () => (filter.trim() ? filterNodes(navigation, filter.trim()) : navigation),
    [navigation, filter]
  );

  const renderNode = (node: NavigationNode, depth: number): React.ReactNode => {
    const isOpen = filter.trim() ? true : expanded[node.id] === true;
    const hasChildren = Boolean(node.children && node.children.length > 0);
    const isActive = node.slug ? normalizeSlug(node.slug) === target : false;
    const isContainsActive = (() => {
      if (isActive) return true;
      let found = false;
      const walk = (child: NavigationNode) => {
        if (found) return;
        if (child.slug && normalizeSlug(child.slug) === target) {
          found = true;
          return;
        }
        child.children?.forEach(walk);
      };
      node.children?.forEach(walk);
      return found;
    })();

    if (depth === 0) {
      const DomainIcon = DOMAIN_ICONS[node.domain || 'root'] || BookOpen;
      const domainMeta = DOMAIN_COLORS[node.domain || 'root'];

      return (
        <div key={node.id} className="mt-3.5 first:mt-1">
          <div className="group flex items-center gap-1.5 rounded-lg px-1.5 py-1.5 transition-all hover:bg-obs-surface/80">
            <button
              type="button"
              onClick={(event) => toggle(node.id, event)}
              aria-expanded={isOpen}
              aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${node.title}`}
              className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md text-obs-text-muted hover:bg-obs-canvas-soft hover:text-obs-text-primary transition-colors"
            >
              {hasChildren ? (
                <ChevronRight
                  className={`h-3.5 w-3.5 transition-transform duration-200 ${
                    isOpen ? 'rotate-90 text-obs-text-primary' : 'text-obs-text-muted'
                  }`}
                />
              ) : (
                <span className="h-1.5 w-1.5 rounded-full bg-obs-border" />
              )}
            </button>
            <div
              className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md border"
              style={{
                backgroundColor: domainMeta?.bg || 'rgba(167, 139, 250, 0.1)',
                borderColor: domainMeta?.border || 'rgba(167, 139, 250, 0.25)',
                color: domainMeta?.text || '#a78bfa',
              }}
            >
              <DomainIcon className="h-3 w-3" />
            </div>
            {node.slug ? (
              <a
                href={node.slug}
                className={`min-w-0 flex-1 truncate text-[0.72rem] font-bold uppercase tracking-[0.12em] transition-colors ${
                  isContainsActive ? 'text-obs-accent' : 'text-obs-text-muted hover:text-obs-text-primary'
                }`}
                aria-current={isActive ? 'page' : undefined}
              >
                {node.title}
              </a>
            ) : (
              <span className="min-w-0 flex-1 truncate text-[0.72rem] font-bold uppercase tracking-[0.12em] text-obs-text-muted">
                {node.title}
              </span>
            )}
            {node.count !== undefined && (
              <span className="shrink-0 rounded-full border border-obs-border/70 bg-obs-canvas px-1.5 py-px font-mono text-[0.58rem] font-medium text-obs-text-muted">
                {node.count}
              </span>
            )}
          </div>
          {isOpen && hasChildren && (
            <div className="relative ml-3.5 mt-1 space-y-0.5 border-l border-obs-border/60 pl-2">
              {node.children!.map((child) => renderNode(child, depth + 1))}
            </div>
          )}
        </div>
      );
    }

    const isFolder = hasChildren;
    const isGroupOrCategory = node.type === 'group' || node.type === 'category';

    const row = (
      <div
        className={`group relative flex items-center gap-1 rounded-lg px-1.5 py-1 text-xs transition-all ${
          isActive
            ? 'bg-gradient-to-r from-obs-accent/20 to-obs-accent/5 font-medium text-obs-accent shadow-sm'
            : isContainsActive
              ? 'text-obs-text-primary hover:bg-obs-surface/70'
              : 'text-obs-text-secondary hover:bg-obs-surface/60 hover:text-obs-text-primary'
        }`}
        style={{ paddingLeft: `${Math.min(depth - 1, 6) * 0.4 + 0.35}rem` }}
      >
        {/* Active Left Indicator Bar */}
        {isActive && (
          <span
            className="absolute left-0 top-1 bottom-1 w-1 rounded-r-full bg-obs-accent shadow-[0_0_8px_var(--obs-accent)]"
            aria-hidden="true"
          />
        )}

        {hasChildren ? (
          <button
            type="button"
            onClick={(event) => toggle(node.id, event)}
            aria-expanded={isOpen}
            aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${node.title}`}
            className="flex h-5 w-4 shrink-0 items-center justify-center text-obs-text-muted hover:text-obs-text-primary transition-colors"
          >
            <ChevronRight
              className={`h-3 w-3 transition-transform duration-200 ${
                isOpen ? 'rotate-90 text-obs-text-primary' : ''
              }`}
            />
          </button>
        ) : (
          <span className="flex h-5 w-4 shrink-0 items-center justify-center">
            <span
              className={`h-1.5 w-1.5 rounded-full transition-colors ${
                isActive
                  ? 'bg-obs-accent shadow-[0_0_6px_var(--obs-accent)]'
                  : 'bg-obs-border group-hover:bg-obs-text-muted'
              }`}
            />
          </span>
        )}

        {node.slug ? (
          <a
            ref={isActive ? activeRef : undefined}
            href={node.slug}
            aria-current={isActive ? 'page' : undefined}
            className="flex min-w-0 flex-1 items-center gap-1.5 py-0.5 leading-snug"
            title={node.title}
          >
            {isFolder ? (
              isOpen ? (
                <FolderOpen className={`h-3.5 w-3.5 shrink-0 ${isActive ? 'text-obs-accent' : 'text-obs-text-muted group-hover:text-obs-text-secondary'}`} />
              ) : (
                <Folder className={`h-3.5 w-3.5 shrink-0 ${isActive ? 'text-obs-accent' : 'text-obs-text-muted group-hover:text-obs-text-secondary'}`} />
              )
            ) : (
              <FileText className={`h-3.5 w-3.5 shrink-0 ${isActive ? 'text-obs-accent' : 'text-obs-text-muted group-hover:text-obs-text-secondary'}`} />
            )}
            <span className={`truncate ${isGroupOrCategory ? 'font-semibold text-obs-text-primary' : 'font-normal'}`}>
              {node.title}
            </span>
          </a>
        ) : (
          <span className="flex min-w-0 flex-1 items-center gap-1.5 py-0.5 font-semibold text-obs-text-primary">
            {isOpen ? (
              <FolderOpen className="h-3.5 w-3.5 shrink-0 text-obs-text-muted" />
            ) : (
              <Folder className="h-3.5 w-3.5 shrink-0 text-obs-text-muted" />
            )}
            <span className="truncate">{node.title}</span>
          </span>
        )}

        {node.count !== undefined && node.count > 0 && depth === 1 && (
          <span
            className={`shrink-0 font-mono text-[0.6rem] ${
              isActive ? 'text-obs-accent' : 'text-obs-text-muted'
            }`}
          >
            {node.count}
          </span>
        )}
      </div>
    );

    return (
      <div key={node.id}>
        {row}
        {isOpen && hasChildren && (
          <div className="relative ml-2.5 space-y-0.5 border-l border-obs-border/50 pl-1.5">
            {node.children!.map((child) => renderNode(child, depth + 1))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="flex h-full flex-col text-sm bg-obs-sidebar/75 backdrop-blur-md select-none">
      {/* Top Header & Toolbar */}
      <div className="flex items-center justify-between border-b border-obs-border/70 bg-obs-sidebar/70 px-3 py-2.5 backdrop-blur-sm">
        <div className="flex items-center gap-2">
          <span className="flex h-5 w-5 items-center justify-center rounded-md bg-obs-accent/15 text-obs-accent shadow-sm">
            <FolderTree className="h-3 w-3" />
          </span>
          <span className="text-[0.68rem] font-bold uppercase tracking-[0.14em] text-obs-text-muted">
            Explorer
          </span>
          <span className="rounded-full border border-obs-border/60 bg-obs-surface px-1.5 py-px font-mono text-[0.58rem] font-medium text-obs-text-muted">
            {totalTopics}
          </span>
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={toggleAll}
            title={allExpanded ? 'Collapse all folders' : 'Expand all folders'}
            aria-label={allExpanded ? 'Collapse all folders' : 'Expand all folders'}
            className="flex h-6 w-6 items-center justify-center rounded-md text-obs-text-muted hover:bg-obs-surface hover:text-obs-text-primary transition-colors"
          >
            {allExpanded ? (
              <ChevronsDownUp className="h-3.5 w-3.5" />
            ) : (
              <ChevronsUpDown className="h-3.5 w-3.5" />
            )}
          </button>
          <button
            type="button"
            onClick={handleFoldSidebar}
            data-sidebar-fold-btn="true"
            title="Collapse sidebar ([ or Ctrl+B)"
            aria-label="Collapse navigation sidebar"
            className="sidebar-fold-btn hidden md:flex h-6 w-6 items-center justify-center rounded-md text-obs-text-muted hover:bg-obs-surface hover:text-obs-accent transition-colors"
          >
            <PanelLeftClose className="h-3.5 w-3.5" />
          </button>
        </div>
      </div>

      {/* Filter and Shortcuts */}
      <div className="border-b border-obs-border/70 bg-obs-sidebar/40 px-3 py-2.5">
        <label htmlFor={filterId} className="sr-only">
          Filter navigation
        </label>
        <div className="relative flex items-center">
          <Search className="pointer-events-none absolute left-2.5 h-3.5 w-3.5 text-obs-accent/70" />
          <input
            id={filterId}
            data-sidebar-filter="true"
            type="search"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="Filter topics… (Press /)"
            className="w-full rounded-lg border border-obs-border/80 bg-obs-canvas/80 py-1.5 pl-8 pr-12 text-xs text-obs-text-primary placeholder:text-obs-text-muted shadow-inner transition-all focus:border-obs-accent/60 focus:bg-obs-surface focus:outline-none focus:ring-2 focus:ring-obs-accent/20"
          />
          {filter ? (
            <button
              type="button"
              onClick={() => setFilter('')}
              aria-label="Clear filter"
              className="absolute right-2 rounded p-0.5 text-obs-text-muted hover:text-obs-rose transition-colors"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          ) : (
            <kbd className="pointer-events-none absolute right-2.5 hidden sm:inline-flex items-center rounded border border-obs-border/60 bg-obs-canvas-soft px-1 text-[0.6rem] font-mono text-obs-text-muted/70">
              /
            </kbd>
          )}
        </div>

        {!filter && (
          <nav aria-label="Knowledge base shortcuts" className="mt-2.5 grid grid-cols-4 gap-1">
            {QUICK_LINKS.map((link) => {
              const isLinkActive = target.startsWith(link.href);
              return (
                <a
                  key={link.href}
                  href={link.href}
                  className={`flex items-center justify-center gap-1 rounded-md border px-1.5 py-1 text-[0.65rem] font-semibold transition-all ${
                    isLinkActive
                      ? 'border-obs-accent/50 bg-obs-accent/15 text-obs-accent shadow-sm'
                      : 'border-obs-border/70 bg-obs-surface/60 text-obs-text-muted hover:border-obs-accent/40 hover:bg-obs-surface hover:text-obs-text-primary'
                  }`}
                >
                  <link.icon className={`h-3 w-3 shrink-0 ${isLinkActive ? 'text-obs-accent' : 'text-obs-text-muted'}`} />
                  <span>{link.label}</span>
                </a>
              );
            })}
          </nav>
        )}
      </div>

      {/* Main Navigation Tree */}
      <nav aria-label="Documentation" className="obs-scrollbar flex-1 overflow-y-auto overscroll-contain px-2 py-3">
        {pinned.length > 0 && !filter && (
          <section className="mb-3 rounded-xl border border-obs-gold/25 bg-obs-gold/5 p-2 shadow-sm" aria-label="Pinned pages">
            <button
              type="button"
              onClick={() => setShowPinned((value) => !value)}
              aria-expanded={showPinned}
              className="flex w-full items-center justify-between rounded-lg px-1.5 py-1 text-[0.65rem] font-bold uppercase tracking-[0.12em] text-obs-gold hover:bg-obs-gold/10 transition-colors"
            >
              <span className="flex items-center gap-1.5">
                <ChevronRight className={`h-3 w-3 transition-transform duration-200 ${showPinned ? 'rotate-90' : ''}`} />
                <Star className="h-3 w-3 fill-current" />
                Pinned Topics
              </span>
              <span className="font-mono text-[0.6rem] px-1.5 py-0.5 rounded-full bg-obs-gold/15 text-obs-gold">{pinned.length}</span>
            </button>
            {showPinned && (
              <div className="mt-1 space-y-0.5 pt-1 border-t border-obs-gold/15">
                {pinned.map((item) => (
                  <div key={item.slug} className="group flex items-center gap-1">
                    <a
                      href={item.slug}
                      className={`flex min-w-0 flex-1 items-center gap-1.5 rounded-md px-2 py-1 text-xs transition-colors ${
                        normalizeSlug(item.slug) === target
                          ? 'bg-obs-gold/20 text-obs-gold font-medium'
                          : 'text-obs-text-secondary hover:bg-obs-gold/10 hover:text-obs-text-primary'
                      }`}
                    >
                      <Pin className="h-3 w-3 shrink-0 text-obs-gold" />
                      <span className="truncate">{item.title}</span>
                    </a>
                    <button
                      type="button"
                      onClick={(event) => unpin(item.slug, event)}
                      aria-label={`Unpin ${item.title}`}
                      className="rounded p-1 text-obs-text-muted opacity-0 transition-all hover:text-obs-rose hover:bg-obs-rose/10 group-hover:opacity-100 focus-visible:opacity-100"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </section>
        )}

        {recents.length > 0 && !filter && (
          <section className="mb-3 rounded-xl border border-obs-border/70 bg-obs-surface/40 p-2 shadow-sm" aria-label="Recently viewed pages">
            <div className="flex w-full items-center justify-between rounded-lg px-1.5 py-1">
              <button
                type="button"
                onClick={() => setShowRecent((value) => !value)}
                aria-expanded={showRecent}
                className="flex items-center gap-1.5 text-[0.65rem] font-bold uppercase tracking-[0.12em] text-obs-text-muted hover:text-obs-text-primary transition-colors"
              >
                <ChevronRight className={`h-3 w-3 transition-transform duration-200 ${showRecent ? 'rotate-90' : ''}`} />
                <Clock className="h-3 w-3 text-obs-accent" />
                Recent
              </button>
              <button
                type="button"
                onClick={clearRecents}
                aria-label="Clear recent pages"
                title="Clear recent pages"
                className="rounded p-1 text-obs-text-muted hover:text-obs-rose hover:bg-obs-rose/10 transition-all"
              >
                <Trash2 className="h-2.5 w-2.5" />
              </button>
            </div>
            {showRecent && (
              <div className="mt-1 space-y-0.5 pt-1 border-t border-obs-border/50">
                {recents.slice(0, 5).map((item) => (
                  <a
                    key={item.slug}
                    href={item.slug}
                    className={`flex items-center gap-1.5 rounded-md px-2 py-1 text-xs transition-colors ${
                      normalizeSlug(item.slug) === target
                        ? 'bg-obs-accent/15 text-obs-accent font-medium'
                        : 'text-obs-text-secondary hover:bg-obs-surface hover:text-obs-text-primary'
                    }`}
                  >
                    <Clock className="h-2.5 w-2.5 shrink-0 text-obs-text-muted" />
                    <span className="truncate">{item.title}</span>
                  </a>
                ))}
              </div>
            )}
          </section>
        )}

        {filter && visibleNavigation.length === 0 && (
          <p className="px-2 py-6 text-center text-xs text-obs-text-muted">
            No topics match “{filter}”.
          </p>
        )}

        {visibleNavigation.map((node) => renderNode(node, 0))}
      </nav>

      {/* Footer info bar */}
      <div className="flex items-center justify-between border-t border-obs-border/80 bg-obs-sidebar/40 px-3 py-2 text-[0.65rem] text-obs-text-muted">
        <a href="/search" className="inline-flex items-center gap-1.5 hover:text-obs-accent transition-colors">
          <Search className="h-3 w-3 text-obs-accent/80" /> Search
        </a>
        <a href="/graph" className="inline-flex items-center gap-1.5 hover:text-obs-accent transition-colors">
          <GitBranch className="h-3 w-3 text-obs-accent/80" /> Graph
        </a>
        <span className="hidden xl:inline text-[0.6rem] text-obs-text-muted/70">
          Fold: <kbd className="font-mono bg-obs-canvas-soft px-1 rounded border border-obs-border/60">[</kbd>
        </span>
      </div>
    </div>
  );
}
