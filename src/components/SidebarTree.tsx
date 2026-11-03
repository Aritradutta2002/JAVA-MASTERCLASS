import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import type { NavigationNode } from '../lib/types';
import {
  BookMarked,
  BookOpen,
  ChevronDown,
  ChevronRight,
  Clock,
  Code2,
  Compass,
  Container,
  Cpu,
  Database,
  FileText,
  Folder,
  Layers,
  Library,
  Pin,
  Search,
  Star,
  Tag,
  Trash2,
  Waypoints,
  X,
} from 'lucide-react';

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
  'cloud-devops': CloudIcon,
  'docker-java-backend': Container,
  root: BookOpen,
};

function CloudIcon({ className }: { className?: string }) {
  return <Compass className={className} />;
}

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
    // Open the domain level by default for first-time visitors.
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
  }, [target, expanded]);

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
      return (
        <div key={node.id} className="mt-3 first:mt-0">
          <div className="group flex items-center gap-1 rounded-md px-1 py-1 hover:bg-obs-hover/60">
            <button
              type="button"
              onClick={(event) => toggle(node.id, event)}
              aria-expanded={isOpen}
              aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${node.title}`}
              className="flex h-6 w-5 shrink-0 items-center justify-center rounded text-obs-text-muted hover:text-obs-text-primary"
            >
              {hasChildren ? (
                isOpen ? (
                  <ChevronDown className="h-3 w-3" />
                ) : (
                  <ChevronRight className="h-3 w-3" />
                )
              ) : (
                <span className="h-1 w-1 rounded-full bg-obs-border" />
              )}
            </button>
            <DomainIcon className="h-3.5 w-3.5 shrink-0 text-obs-accent/80" />
            {node.slug ? (
              <a
                href={node.slug}
                className={`min-w-0 flex-1 truncate text-[0.7rem] font-bold uppercase tracking-[0.12em] transition-colors ${
                  isContainsActive ? 'text-obs-accent' : 'text-obs-text-muted hover:text-obs-text-primary'
                }`}
                aria-current={isActive ? 'page' : undefined}
              >
                {node.title}
              </a>
            ) : (
              <span className="min-w-0 flex-1 truncate text-[0.7rem] font-bold uppercase tracking-[0.12em] text-obs-text-muted">
                {node.title}
              </span>
            )}
            {node.count !== undefined && (
              <span className="shrink-0 rounded-full border border-obs-border bg-obs-canvas px-1.5 py-px font-mono text-[0.6rem] text-obs-text-muted">
                {node.count}
              </span>
            )}
          </div>
          {isOpen && hasChildren && (
            <div className="ml-[0.72rem] mt-0.5 space-y-0.5 border-l border-obs-border-subtle pl-2">
              {node.children!.map((child) => renderNode(child, depth + 1))}
            </div>
          )}
        </div>
      );
    }

    const row = (
      <div
        className={`group flex items-center gap-1 rounded-md pr-1 transition-colors ${
          isActive
            ? 'bg-obs-accent text-[#0b0714] shadow-sm'
            : 'text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'
        }`}
        style={{ paddingLeft: `${Math.min(depth - 1, 5) * 0.35}rem` }}
      >
        {hasChildren ? (
          <button
            type="button"
            onClick={(event) => toggle(node.id, event)}
            aria-expanded={isOpen}
            aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${node.title}`}
            className={`flex h-6 w-4 shrink-0 items-center justify-center ${
              isActive ? 'text-[#0b0714]/70' : 'text-obs-text-muted'
            }`}
          >
            {isOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
          </button>
        ) : (
          <span className="flex h-6 w-4 shrink-0 items-center justify-center">
            <span
              className={`h-1 w-1 rounded-full ${
                isActive ? 'bg-[#0b0714]/50' : 'bg-obs-border'
              }`}
            />
          </span>
        )}

        {node.slug ? (
          <a
            ref={isActive ? activeRef : undefined}
            href={node.slug}
            aria-current={isActive ? 'page' : undefined}
            className={`flex min-w-0 flex-1 items-center gap-1.5 py-[0.32rem] text-[0.79rem] leading-snug ${
              node.type === 'group' || node.type === 'category' ? 'font-semibold' : 'font-normal'
            }`}
            title={node.title}
          >
            {hasChildren ? (
              <Folder className={`h-3 w-3 shrink-0 ${isActive ? 'text-[#0b0714]/70' : 'text-obs-text-muted'}`} />
            ) : (
              <FileText className={`h-3 w-3 shrink-0 ${isActive ? 'text-[#0b0714]/70' : 'text-obs-text-muted'}`} />
            )}
            <span className="truncate">{node.title}</span>
          </a>
        ) : (
          <span className="flex min-w-0 flex-1 items-center gap-1.5 py-[0.32rem] text-[0.79rem] font-semibold">
            <Folder className="h-3 w-3 shrink-0 text-obs-text-muted" />
            <span className="truncate">{node.title}</span>
          </span>
        )}

        {node.count !== undefined && node.count > 0 && depth === 1 && (
          <span
            className={`shrink-0 font-mono text-[0.6rem] ${
              isActive ? 'text-[#0b0714]/60' : 'text-obs-text-muted'
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
          <div className="ml-3 space-y-0.5 border-l border-obs-border-subtle pl-1.5">
            {node.children!.map((child) => renderNode(child, depth + 1))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="flex h-full flex-col text-sm">
      <div className="border-b border-obs-border px-3 py-3">
        <label htmlFor={filterId} className="sr-only">
          Filter navigation
        </label>
        <div className="relative flex items-center">
          <Search className="pointer-events-none absolute left-2.5 h-3.5 w-3.5 text-obs-text-muted" />
          <input
            id={filterId}
            type="search"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
            placeholder="Filter the knowledge base…"
            className="w-full rounded-lg border border-obs-border bg-obs-canvas py-1.5 pl-8 pr-7 text-xs text-obs-text-primary placeholder:text-obs-text-muted focus:border-obs-accent focus:outline-none"
          />
          {filter && (
            <button
              type="button"
              onClick={() => setFilter('')}
              aria-label="Clear filter"
              className="absolute right-1.5 rounded p-1 text-obs-text-muted hover:text-obs-text-primary"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        {!filter && (
          <nav aria-label="Knowledge base shortcuts" className="mt-2 flex flex-wrap gap-1">
            {QUICK_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="inline-flex items-center gap-1 rounded-md border border-obs-border bg-obs-surface px-2 py-1 text-[0.65rem] font-semibold text-obs-text-muted transition-colors hover:border-obs-accent/40 hover:text-obs-accent"
              >
                <link.icon className="h-3 w-3" />
                {link.label}
              </a>
            ))}
          </nav>
        )}
      </div>

      <nav aria-label="Documentation" className="flex-1 overflow-y-auto overscroll-contain px-2 py-3">
        {pinned.length > 0 && !filter && (
          <section className="mb-3 border-b border-obs-border-subtle pb-3" aria-label="Pinned pages">
            <button
              type="button"
              onClick={() => setShowPinned((value) => !value)}
              aria-expanded={showPinned}
              className="flex w-full items-center justify-between rounded px-1.5 py-1 text-[0.65rem] font-bold uppercase tracking-[0.12em] text-obs-gold hover:bg-obs-hover"
            >
              <span className="flex items-center gap-1.5">
                {showPinned ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                <Star className="h-3 w-3 fill-current" />
                Pinned
              </span>
              <span className="font-mono text-obs-text-muted">{pinned.length}</span>
            </button>
            {showPinned && (
              <div className="mt-1 space-y-0.5">
                {pinned.map((item) => (
                  <div key={item.slug} className="group flex items-center gap-1">
                    <a
                      href={item.slug}
                      className={`flex min-w-0 flex-1 items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors ${
                        normalizeSlug(item.slug) === target
                          ? 'bg-obs-accent/15 text-obs-accent'
                          : 'text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'
                      }`}
                    >
                      <Pin className="h-3 w-3 shrink-0 text-obs-gold" />
                      <span className="truncate">{item.title}</span>
                    </a>
                    <button
                      type="button"
                      onClick={(event) => unpin(item.slug, event)}
                      aria-label={`Unpin ${item.title}`}
                      className="rounded p-1 text-obs-text-muted opacity-0 transition-opacity hover:text-obs-rose group-hover:opacity-100 focus-visible:opacity-100"
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
          <section className="mb-3 border-b border-obs-border-subtle pb-3" aria-label="Recently viewed pages">
            <div className="flex w-full items-center justify-between rounded px-1.5 py-1">
              <button
                type="button"
                onClick={() => setShowRecent((value) => !value)}
                aria-expanded={showRecent}
                className="flex items-center gap-1.5 text-[0.65rem] font-bold uppercase tracking-[0.12em] text-obs-text-muted hover:text-obs-text-primary"
              >
                {showRecent ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
                <Clock className="h-3 w-3 text-obs-accent" />
                Recent
              </button>
              <button
                type="button"
                onClick={clearRecents}
                aria-label="Clear recent pages"
                className="rounded p-1 text-obs-text-muted hover:text-obs-rose"
              >
                <Trash2 className="h-2.5 w-2.5" />
              </button>
            </div>
            {showRecent && (
              <div className="mt-1 space-y-0.5">
                {recents.slice(0, 5).map((item) => (
                  <a
                    key={item.slug}
                    href={item.slug}
                    className={`flex items-center gap-1.5 rounded px-2 py-1 text-xs transition-colors ${
                      normalizeSlug(item.slug) === target
                        ? 'bg-obs-accent/15 text-obs-accent'
                        : 'text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'
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

      <div className="border-t border-obs-border px-3 py-2 text-[0.65rem] text-obs-text-muted">
        <a href="/search" className="inline-flex items-center gap-1 hover:text-obs-accent">
          <Search className="h-3 w-3" /> Full-text search
        </a>
      </div>
    </div>
  );
}
