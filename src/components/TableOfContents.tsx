import React, { useState, useEffect } from 'react';
import type { HeadingItem } from '../lib/types';
import { AlignLeft } from 'lucide-react';

interface Props {
  headings: HeadingItem[];
}

export default function TableOfContents({ headings }: Props) {
  const [activeSlug, setActiveSlug] = useState<string>('');

  const filteredHeadings = headings.filter(h => h.depth >= 2 && h.depth <= 3);

  useEffect(() => {
    if (filteredHeadings.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveSlug(entry.target.id);
            break;
          }
        }
      },
      {
        rootMargin: '-80px 0% -70% 0%',
        threshold: 0,
      }
    );

    filteredHeadings.forEach((h) => {
      const el = document.getElementById(h.slug);
      if (el) observer.observe(el);
    });

    return () => observer.disconnect();
  }, [headings]);

  if (filteredHeadings.length === 0) return null;

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-obs-text-muted">
        <AlignLeft className="h-3.5 w-3.5 text-obs-accent" />
        <span>On This Page</span>
      </div>
      <nav className="space-y-1 text-xs">
        {filteredHeadings.map((h) => {
          const isActive = activeSlug === h.slug;
          return (
            <a
              key={h.slug}
              href={`#${h.slug}`}
              className={`block truncate py-1 transition-colors ${
                h.depth === 3 ? 'pl-3' : 'pl-0'
              } ${
                isActive
                  ? 'text-obs-accent font-medium border-l-2 border-obs-accent -ml-2 pl-2'
                  : 'text-obs-text-muted hover:text-obs-text-primary'
              }`}
            >
              {h.text}
            </a>
          );
        })}
      </nav>
    </div>
  );
}
