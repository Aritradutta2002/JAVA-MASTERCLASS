import { useState, useMemo } from 'react';
import type { BacklinkItem, OutgoingLinkItem } from '../lib/types';
import { DOMAIN_COLORS } from '../lib/constants';
import {
  ArrowUpRight,
  ArrowDownLeft,
  ChevronDown,
  ChevronRight,
  Link2,
  Network,
  Search,
} from 'lucide-react';

interface Props {
  backlinks?: BacklinkItem[];
  outgoing?: OutgoingLinkItem[];
}

/** Strip inline Markdown so backlink context reads as prose, not source. */
function cleanSnippet(value: string) {
  return value
    .replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/\[\[([^\]]+)\]\]/g, '$1')
    .replace(/`{1,3}/g, '')
    .replace(/\*\*|__|~~/g, '')
    .replace(/(^|\s)[*_]([^*_]+)[*_](?=\s|$)/g, '$1$2')
    .replace(/^\s{0,3}#{1,6}\s+/g, '')
    .replace(/^\s{0,3}[-*+]\s+/g, '')
    .replace(/^\s{0,3}>\s?/g, '')
    .replace(/\s*\|\s*/g, ' · ')
    .replace(/^\s*·\s*|\s*·\s*$/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

export default function BacklinksPanel({ backlinks = [], outgoing = [] }: Props) {
  const [activeTab, setActiveTab] = useState<'all' | 'backlinks' | 'outgoing'>('all');
  const [filterQuery, setFilterQuery] = useState('');
  const [backlinksOpen, setBacklinksOpen] = useState(true);
  const [outgoingOpen, setOutgoingOpen] = useState(true);

  const totalConnections = backlinks.length + outgoing.length;

  // Filter links based on query
  const filteredBacklinks = useMemo(() => {
    if (!filterQuery.trim()) return backlinks;
    const q = filterQuery.toLowerCase();
    return backlinks.filter(
      (b) =>
        b.sourceTitle.toLowerCase().includes(q) ||
        b.domainTitle.toLowerCase().includes(q) ||
        (b.contextSnippet && b.contextSnippet.toLowerCase().includes(q))
    );
  }, [backlinks, filterQuery]);

  const filteredOutgoing = useMemo(() => {
    if (!filterQuery.trim()) return outgoing;
    const q = filterQuery.toLowerCase();
    return outgoing.filter(
      (o) =>
        o.targetTitle.toLowerCase().includes(q) ||
        o.domainTitle.toLowerCase().includes(q)
    );
  }, [outgoing, filterQuery]);

  if (totalConnections === 0) {
    return (
      <div className="rounded-md border border-obs-border/70 bg-obs-surface/40 p-4 text-center space-y-2.5">
        <div className="mx-auto flex h-9 w-9 items-center justify-center rounded-sm border border-obs-border bg-obs-canvas-soft text-obs-text-muted">
          <Network className="h-4 w-4" />
        </div>
        <div>
          <div className="text-xs font-semibold text-obs-text-primary">No Direct Connections</div>
          <p className="text-caption leading-relaxed text-obs-text-muted mt-0.5">
            This document stands standalone. Explore related patterns across the curriculum in the graph view.
          </p>
        </div>
        <a
          href="/graph"
          className="inline-flex items-center gap-1.5 rounded-sm border border-obs-border bg-obs-surface px-3 py-1.5 text-xs font-medium text-obs-accent hover:border-obs-accent/40 hover:bg-obs-hover transition-colors shadow-xs"
        >
          <span>Open Knowledge Graph</span>
          <ArrowUpRight className="h-3.5 w-3.5" />
        </a>
      </div>
    );
  }

  return (
    <div className="space-y-3 border-t border-obs-border/80 pt-4">
      {/* Header */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <Link2 className="h-3.5 w-3.5 shrink-0 text-obs-text-muted" />
          <span className="technical-label truncate text-obs-text-muted">Knowledge Links</span>
          <span className="font-mono text-micro tabular-nums text-obs-text-muted">{totalConnections}</span>
        </div>

        <a
          href="/graph"
          aria-label="Open knowledge graph"
          title="Open knowledge graph"
          className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-sm text-obs-text-muted transition-colors hover:bg-obs-hover hover:text-obs-text-primary"
        >
          <ArrowUpRight className="h-3.5 w-3.5" />
        </a>
      </div>

      {/* Tabs & filter */}
      {totalConnections > 4 && (
        <div className="space-y-2">
          <div role="group" aria-label="Filter link direction" className="flex gap-3 border-b border-obs-border/70">
            {([
              ['all', 'All', totalConnections],
              ['backlinks', 'In', backlinks.length],
              ['outgoing', 'Out', outgoing.length],
            ] as const).map(([value, label, count]) => (
              <button
                key={value}
                type="button"
                onClick={() => setActiveTab(value)}
                aria-pressed={activeTab === value}
                className={`-mb-px flex items-center gap-1 border-b px-0.5 pb-1.5 text-caption transition-colors ${
                  activeTab === value
                    ? 'border-obs-accent font-semibold text-obs-text-primary'
                    : 'border-transparent text-obs-text-muted hover:text-obs-text-secondary'
                }`}
              >
                {label}
                <span className="font-mono text-micro tabular-nums">{count}</span>
              </button>
            ))}
          </div>

          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-2 h-3 w-3 text-obs-text-muted" />
            <input
              type="text"
              value={filterQuery}
              onChange={(e) => setFilterQuery(e.target.value)}
              placeholder="Filter relations…"
              aria-label="Filter knowledge links"
              className="field w-full rounded-sm border border-obs-border/70 bg-obs-surface/60 py-1 pl-7 pr-2.5 text-caption text-obs-text-primary placeholder:text-obs-text-muted transition-colors focus:border-obs-accent/70 focus:bg-obs-surface focus:outline-none"
            />
          </div>
        </div>
      )}

      {/* Incoming Backlinks Section */}
      {(activeTab === 'all' || activeTab === 'backlinks') && backlinks.length > 0 && (
        <section className="space-y-2" aria-label="Referenced By">
          <button
            type="button"
            onClick={() => setBacklinksOpen((v) => !v)}
            aria-expanded={backlinksOpen}
            className="flex w-full items-center justify-between text-caption font-semibold text-obs-text-secondary hover:text-obs-text-primary py-0.5 select-none transition-colors group"
          >
            <span className="flex items-center gap-1.5">
              <ArrowDownLeft className="h-3.5 w-3.5 text-obs-success-text" />
              <span>Backlinks ({filteredBacklinks.length})</span>
            </span>
            <span className="text-obs-text-muted group-hover:text-obs-text-secondary transition-colors">
              {backlinksOpen ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
            </span>
          </button>

          {backlinksOpen &&
            (filteredBacklinks.length === 0 ? (
              <p className="px-1 py-1 text-caption italic text-obs-text-muted">
                No matching backlinks.
              </p>
            ) : (
              <ul className="animate-fade-in divide-y divide-obs-border/60 overflow-hidden rounded-sm border border-obs-border/70 bg-obs-surface/30">
                {filteredBacklinks.slice(0, 10).map((backlink, index) => {
                  const domainColor = DOMAIN_COLORS[backlink.domain] || DOMAIN_COLORS.root;
                  const snippet = backlink.contextSnippet
                    ? cleanSnippet(backlink.contextSnippet)
                    : '';
                  return (
                    <li key={`backlink-${backlink.sourceSlug}-${index}`}>
                      <a
                        href={backlink.sourceSlug}
                        title={backlink.sourceTitle}
                        className="group block px-2.5 py-2 transition-colors hover:bg-obs-surface"
                      >
                        <div className="flex items-start gap-1.5">
                          <span
                            className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-sm"
                            style={{ backgroundColor: domainColor.hex }}
                            aria-hidden="true"
                          />
                          <span className="line-clamp-2 min-w-0 flex-1 text-xs font-medium leading-snug text-obs-text-primary">
                            {backlink.sourceTitle}
                          </span>
                          <ArrowUpRight className="mt-0.5 h-3 w-3 shrink-0 text-obs-text-muted transition-colors group-hover:text-obs-text-primary" />
                        </div>

                        <div className="mt-1 pl-3 text-micro text-obs-text-muted">
                          {backlink.domainTitle}
                        </div>

                        {snippet && (
                          <p className="ml-3 mt-1.5 line-clamp-2 border-l border-obs-border pl-2 text-caption leading-snug text-obs-text-muted">
                            {snippet}
                          </p>
                        )}
                      </a>
                    </li>
                  );
                })}
              </ul>
            ))}
        </section>
      )}

      {/* Outgoing References Section */}
      {(activeTab === 'all' || activeTab === 'outgoing') && outgoing.length > 0 && (
        <section className="space-y-2" aria-label="Outgoing References">
          <button
            type="button"
            onClick={() => setOutgoingOpen((v) => !v)}
            aria-expanded={outgoingOpen}
            className="flex w-full items-center justify-between text-caption font-semibold text-obs-text-secondary hover:text-obs-text-primary py-0.5 select-none transition-colors group"
          >
            <span className="flex items-center gap-1.5">
              <ArrowUpRight className="h-3.5 w-3.5 text-obs-info-text" />
              <span>Outgoing Links ({filteredOutgoing.length})</span>
            </span>
            <span className="text-obs-text-muted group-hover:text-obs-text-secondary transition-colors">
              {outgoingOpen ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
            </span>
          </button>

          {outgoingOpen &&
            (filteredOutgoing.length === 0 ? (
              <p className="px-1 py-1 text-caption italic text-obs-text-muted">
                No matching outgoing links.
              </p>
            ) : (
              <ul className="animate-fade-in divide-y divide-obs-border/60 overflow-hidden rounded-sm border border-obs-border/70 bg-obs-surface/30">
                {filteredOutgoing.slice(0, 12).map((item, index) => {
                  const domainColor = DOMAIN_COLORS[item.domain] || DOMAIN_COLORS.root;
                  return (
                    <li key={`outgoing-${item.targetSlug}-${index}`}>
                      <a
                        href={item.targetSlug}
                        title={item.targetTitle}
                        className="group flex items-center gap-2 px-2.5 py-1.5 text-xs transition-colors hover:bg-obs-surface"
                      >
                        <span
                          className="h-1.5 w-1.5 shrink-0 rounded-sm"
                          style={{ backgroundColor: domainColor.hex }}
                          aria-hidden="true"
                        />
                        <span className="min-w-0 flex-1 truncate text-obs-text-secondary transition-colors group-hover:text-obs-text-primary">
                          {item.targetTitle}
                        </span>
                        <ArrowUpRight className="h-3 w-3 shrink-0 text-obs-text-muted opacity-0 transition-opacity group-hover:opacity-100" />
                      </a>
                    </li>
                  );
                })}
              </ul>
            ))}
        </section>
      )}
    </div>
  );
}
