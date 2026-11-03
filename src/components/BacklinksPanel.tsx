import React, { useState } from 'react';
import type { BacklinkItem, OutgoingLinkItem } from '../lib/types';
import { Link2, ArrowUpRight, ChevronRight, ChevronDown } from 'lucide-react';

interface Props {
  backlinks: BacklinkItem[];
  outgoing: OutgoingLinkItem[];
}

export default function BacklinksPanel({ backlinks = [], outgoing = [] }: Props) {
  const [backlinksOpen, setBacklinksOpen] = useState(true);
  const [outgoingOpen, setOutgoingOpen] = useState(false);

  if (backlinks.length === 0 && outgoing.length === 0) return null;

  return (
    <div className="space-y-4 pt-4 border-t border-obs-border">
      {/* Linked Mentions (Backlinks) */}
      {backlinks.length > 0 && (
        <div className="space-y-2">
          <button
            onClick={() => setBacklinksOpen(!backlinksOpen)}
            className="flex w-full items-center justify-between text-xs font-semibold uppercase tracking-wider text-obs-text-muted hover:text-obs-text-primary"
          >
            <div className="flex items-center gap-1.5">
              <Link2 className="h-3.5 w-3.5 text-obs-accent" />
              <span>Linked Mentions ({backlinks.length})</span>
            </div>
            {backlinksOpen ? (
              <ChevronDown className="h-3 w-3" />
            ) : (
              <ChevronRight className="h-3 w-3" />
            )}
          </button>

          {backlinksOpen && (
            <div className="space-y-2 pt-1">
              {backlinks.map((bl, i) => (
                <a
                  key={i}
                  href={bl.sourceSlug}
                  className="group block rounded-md border border-obs-border bg-obs-surface/60 p-2 text-xs transition-colors hover:border-obs-accent/40 hover:bg-obs-hover"
                >
                  <div className="flex items-center justify-between gap-1 text-obs-text-primary font-medium group-hover:text-obs-accent">
                    <span className="truncate">{bl.sourceTitle}</span>
                    <ArrowUpRight className="h-3 w-3 shrink-0 opacity-50 group-hover:opacity-100" />
                  </div>
                  <div className="mt-0.5 text-[10px] text-obs-text-muted">
                    {bl.domainTitle}
                  </div>
                  {bl.contextSnippet && (
                    <div className="mt-1 line-clamp-2 text-[11px] text-obs-text-secondary bg-obs-canvas/80 p-1.5 rounded border border-obs-border/50 font-mono">
                      {bl.contextSnippet}
                    </div>
                  )}
                </a>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Outgoing Links */}
      {outgoing.length > 0 && (
        <div className="space-y-2">
          <button
            onClick={() => setOutgoingOpen(!outgoingOpen)}
            className="flex w-full items-center justify-between text-xs font-semibold uppercase tracking-wider text-obs-text-muted hover:text-obs-text-primary"
          >
            <div className="flex items-center gap-1.5">
              <ArrowUpRight className="h-3.5 w-3.5 text-obs-accent" />
              <span>Outgoing Links ({outgoing.length})</span>
            </div>
            {outgoingOpen ? (
              <ChevronDown className="h-3 w-3" />
            ) : (
              <ChevronRight className="h-3 w-3" />
            )}
          </button>

          {outgoingOpen && (
            <div className="space-y-1 pt-1">
              {outgoing.map((out, i) => (
                <a
                  key={i}
                  href={out.targetSlug}
                  className="flex items-center justify-between rounded px-2 py-1 text-xs text-obs-text-muted hover:bg-obs-hover hover:text-obs-accent transition-colors"
                >
                  <span className="truncate">{out.targetTitle}</span>
                  <ArrowUpRight className="h-3 w-3 shrink-0 opacity-40" />
                </a>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
