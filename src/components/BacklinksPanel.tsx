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
      <div className="rounded-xl border border-obs-border/70 bg-obs-surface/40 p-4 text-center space-y-2.5">
        <div className="mx-auto flex h-9 w-9 items-center justify-center rounded-lg bg-obs-accent/10 border border-obs-accent/20 text-obs-accent">
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
          className="inline-flex items-center gap-1.5 rounded-lg border border-obs-border bg-obs-surface px-3 py-1.5 text-xs font-medium text-obs-accent hover:border-obs-accent/40 hover:bg-obs-hover transition-colors shadow-xs"
        >
          <span>Open Knowledge Graph</span>
          <ArrowUpRight className="h-3.5 w-3.5" />
        </a>
      </div>
    );
  }

  return (
    <div className="space-y-3.5 border-t border-obs-border/80 pt-4">
      {/* Header bar */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="flex h-5 w-5 items-center justify-center rounded bg-obs-accent/15 text-obs-accent">
            <Link2 className="h-3 w-3" />
          </div>
          <span className="text-caption font-bold uppercase tracking-wider text-obs-text-primary">
            Knowledge Links
          </span>
          <span className="rounded-full bg-obs-surface border border-obs-border/70 px-1.5 py-0.5 font-mono text-micro font-semibold text-obs-text-muted">
            {totalConnections}
          </span>
        </div>

        <a
          href="/graph"
          className="flex items-center gap-1 text-caption font-medium text-obs-text-muted hover:text-obs-accent transition-colors group"
          title="Explore in full interactive graph"
        >
          <span>Graph</span>
          <ArrowUpRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
        </a>
      </div>

      {/* Tabs & Search if multiple connections */}
      {totalConnections > 4 && (
        <div className="space-y-2">
          {/* Segmented Tab Control */}
          <div className="grid grid-cols-3 gap-1 rounded-lg border border-obs-border/70 bg-obs-canvas/80 p-0.5 text-caption font-medium">
            <button
              type="button"
              onClick={() => setActiveTab('all')}
              className={`rounded-md py-1 transition-all ${
                activeTab === 'all'
                  ? 'bg-obs-surface text-obs-text-primary shadow-xs font-semibold'
                  : 'text-obs-text-muted hover:text-obs-text-secondary'
              }`}
            >
              All ({totalConnections})
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('backlinks')}
              className={`rounded-md py-1 transition-all ${
                activeTab === 'backlinks'
                  ? 'bg-obs-surface text-obs-text-primary shadow-xs font-semibold'
                  : 'text-obs-text-muted hover:text-obs-text-secondary'
              }`}
            >
              In ({backlinks.length})
            </button>
            <button
              type="button"
              onClick={() => setActiveTab('outgoing')}
              className={`rounded-md py-1 transition-all ${
                activeTab === 'outgoing'
                  ? 'bg-obs-surface text-obs-text-primary shadow-xs font-semibold'
                  : 'text-obs-text-muted hover:text-obs-text-secondary'
              }`}
            >
              Out ({outgoing.length})
            </button>
          </div>

          {/* Quick Filter */}
          <div className="relative">
            <Search className="absolute left-2.5 top-2 h-3 w-3 text-obs-text-muted pointer-events-none" />
            <input
              type="text"
              value={filterQuery}
              onChange={(e) => setFilterQuery(e.target.value)}
              placeholder="Filter relations..."
              aria-label="Filter knowledge links"
              className="field w-full rounded-md border border-obs-border/70 bg-obs-surface/60 py-1 pl-7 pr-2.5 text-caption text-obs-text-primary placeholder:text-obs-text-muted focus:border-obs-accent/70 focus:bg-obs-surface focus:outline-none transition-all"
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

          {backlinksOpen && (
            <ul className="space-y-2 animate-fade-in">
              {filteredBacklinks.length === 0 ? (
                <li className="text-caption text-obs-text-muted italic px-2 py-1">
                  No matching backlinks found.
                </li>
              ) : (
                filteredBacklinks.slice(0, 10).map((backlink, index) => {
                  const domainColor = DOMAIN_COLORS[backlink.domain] || DOMAIN_COLORS.root;
                  return (
                    <li key={`backlink-${backlink.sourceSlug}-${index}`}>
                      <a
                        href={backlink.sourceSlug}
                        className="group relative block rounded-xl border border-obs-border/70 bg-obs-surface/40 hover:bg-obs-surface/85 hover:border-obs-accent/40 p-2.5 transition-all duration-200 shadow-xs hover:shadow-sm overflow-hidden"
                      >
                        {/* Linear-style top highlight line on hover */}
                        <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-obs-accent/40 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

                        <div className="flex items-start justify-between gap-2">
                          <div className="font-medium text-xs text-obs-text-primary group-hover:text-obs-accent leading-snug transition-colors">
                            {backlink.sourceTitle}
                          </div>
                          <ArrowUpRight className="h-3.5 w-3.5 shrink-0 text-obs-text-muted opacity-50 group-hover:opacity-100 group-hover:text-obs-accent group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-all" />
                        </div>

                        <div className="mt-1.5 flex items-center gap-1.5">
                          <span
                            className="inline-flex items-center px-1.5 py-0.5 rounded text-micro font-medium border"
                            style={{
                              backgroundColor: domainColor.bg,
                              color: domainColor.text,
                              borderColor: domainColor.border,
                            }}
                          >
                            {backlink.domainTitle}
                          </span>
                        </div>

                        {backlink.contextSnippet && (
                          <div className="mt-2 rounded-lg border-l-2 border-l-emerald-500/50 border border-obs-border/40 bg-obs-canvas/80 p-2 font-mono text-caption leading-relaxed text-obs-text-muted line-clamp-3 group-hover:text-obs-text-secondary transition-colors">
                            {backlink.contextSnippet}
                          </div>
                        )}
                      </a>
                    </li>
                  );
                })
              )}
            </ul>
          )}
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

          {outgoingOpen && (
            <ul className="space-y-1.5 animate-fade-in">
              {filteredOutgoing.length === 0 ? (
                <li className="text-caption text-obs-text-muted italic px-2 py-1">
                  No matching outgoing links found.
                </li>
              ) : (
                filteredOutgoing.slice(0, 12).map((item, index) => {
                  const domainColor = DOMAIN_COLORS[item.domain] || DOMAIN_COLORS.root;
                  return (
                    <li key={`outgoing-${item.targetSlug}-${index}`}>
                      <a
                        href={item.targetSlug}
                        className="group flex items-center justify-between gap-2 rounded-lg border border-obs-border/50 bg-obs-surface/30 hover:bg-obs-surface/80 hover:border-obs-accent/30 px-2.5 py-1.5 transition-all text-xs"
                      >
                        <div className="flex items-center gap-2 min-w-0">
                          <span
                            className="h-1.5 w-1.5 rounded-full shrink-0"
                            style={{ backgroundColor: domainColor.hex }}
                          />
                          <span className="truncate text-obs-text-secondary group-hover:text-obs-accent transition-colors">
                            {item.targetTitle}
                          </span>
                        </div>
                        <ArrowUpRight className="h-3 w-3 shrink-0 text-obs-text-muted opacity-40 group-hover:opacity-100 group-hover:text-obs-accent group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-all" />
                      </a>
                    </li>
                  );
                })
              )}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}
