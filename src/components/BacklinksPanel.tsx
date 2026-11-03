import { useState } from 'react';
import type { BacklinkItem, OutgoingLinkItem } from '../lib/types';
import { ArrowUpRight, ChevronDown, ChevronRight, Link2 } from 'lucide-react';

interface Props {
  backlinks: BacklinkItem[];
  outgoing: OutgoingLinkItem[];
}

export default function BacklinksPanel({ backlinks = [], outgoing = [] }: Props) {
  const [backlinksOpen, setBacklinksOpen] = useState(true);
  const [outgoingOpen, setOutgoingOpen] = useState(false);

  if (backlinks.length === 0 && outgoing.length === 0) {
    return (
      <div className="space-y-2 border-t border-obs-border-subtle pt-4">
        <div className="flex items-center gap-1.5 text-[0.65rem] font-bold uppercase tracking-[0.14em] text-obs-text-muted">
          <Link2 className="h-3.5 w-3.5 text-obs-accent" />
          <span>Connections</span>
        </div>
        <p className="text-[0.72rem] leading-relaxed text-obs-text-muted">
          No linked mentions yet. Use the graph to explore nearby topics.
        </p>
        <a
          href="/graph"
          className="inline-flex items-center gap-1 text-[0.72rem] font-semibold text-obs-accent hover:underline"
        >
          Open knowledge graph <ArrowUpRight className="h-3 w-3" />
        </a>
      </div>
    );
  }

  return (
    <div className="space-y-4 border-t border-obs-border-subtle pt-4">
      {backlinks.length > 0 && (
        <section className="space-y-2" aria-label="Linked mentions">
          <button
            type="button"
            onClick={() => setBacklinksOpen((value) => !value)}
            aria-expanded={backlinksOpen}
            className="flex w-full items-center justify-between text-[0.65rem] font-bold uppercase tracking-[0.14em] text-obs-text-muted hover:text-obs-text-primary"
          >
            <span className="flex items-center gap-1.5">
              <Link2 className="h-3.5 w-3.5 text-obs-accent" />
              Linked mentions
              <span className="font-mono text-[0.6rem] text-obs-text-muted">({backlinks.length})</span>
            </span>
            {backlinksOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
          </button>

          {backlinksOpen && (
            <ul className="space-y-1.5">
              {backlinks.slice(0, 12).map((backlink, index) => (
                <li key={`${backlink.sourceSlug}-${index}`}>
                  <a
                    href={backlink.sourceSlug}
                    className="group block rounded-lg border border-obs-border-subtle bg-obs-surface/55 p-2 transition-colors hover:border-obs-accent/35 hover:bg-obs-hover"
                  >
                    <div className="flex items-center justify-between gap-1 text-[0.74rem] font-semibold text-obs-text-primary group-hover:text-obs-accent">
                      <span className="truncate">{backlink.sourceTitle}</span>
                      <ArrowUpRight className="h-3 w-3 shrink-0 opacity-40 group-hover:opacity-100" />
                    </div>
                    <div className="mt-0.5 text-[0.6rem] font-medium uppercase tracking-wider text-obs-text-muted">
                      {backlink.domainTitle}
                    </div>
                    {backlink.contextSnippet && (
                      <p className="mt-1 line-clamp-2 rounded border border-obs-border-subtle bg-obs-canvas/70 p-1.5 font-mono text-[0.62rem] leading-relaxed text-obs-text-muted">
                        {backlink.contextSnippet}
                      </p>
                    )}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {outgoing.length > 0 && (
        <section className="space-y-2" aria-label="Outgoing links">
          <button
            type="button"
            onClick={() => setOutgoingOpen((value) => !value)}
            aria-expanded={outgoingOpen}
            className="flex w-full items-center justify-between text-[0.65rem] font-bold uppercase tracking-[0.14em] text-obs-text-muted hover:text-obs-text-primary"
          >
            <span className="flex items-center gap-1.5">
              <ArrowUpRight className="h-3.5 w-3.5 text-obs-accent" />
              Outgoing links
              <span className="font-mono text-[0.6rem] text-obs-text-muted">({outgoing.length})</span>
            </span>
            {outgoingOpen ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
          </button>
          {outgoingOpen && (
            <ul className="space-y-0.5">
              {outgoing.slice(0, 15).map((item, index) => (
                <li key={`${item.targetSlug}-${index}`}>
                  <a
                    href={item.targetSlug}
                    className="flex items-center justify-between gap-2 rounded px-1.5 py-1 text-[0.73rem] text-obs-text-muted transition-colors hover:bg-obs-hover hover:text-obs-accent"
                  >
                    <span className="truncate">{item.targetTitle}</span>
                    <ArrowUpRight className="h-3 w-3 shrink-0 opacity-40" />
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}
