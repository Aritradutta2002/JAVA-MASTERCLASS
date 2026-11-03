import { useEffect, useMemo, useRef, useState } from 'react';
import type { HeadingItem } from '../lib/types';
import { ListTree } from 'lucide-react';

interface Props {
  headings: HeadingItem[];
}

export default function TableOfContents({ headings }: Props) {
  const [activeSlug, setActiveSlug] = useState('');
  const linkRefs = useRef<Record<string, HTMLAnchorElement | null>>({});

  const filtered = useMemo(
    () => headings.filter((heading) => heading.depth >= 2 && heading.depth <= 4),
    [headings]
  );

  useEffect(() => {
    if (filtered.length === 0) return;
    const elements = filtered
      .map((heading) => document.getElementById(heading.slug))
      .filter((element): element is HTMLElement => Boolean(element));

    if (elements.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible[0]?.target.id) setActiveSlug(visible[0].target.id);
      },
      { rootMargin: '-88px 0% -68% 0%', threshold: [0, 1] }
    );

    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [filtered]);

  useEffect(() => {
    linkRefs.current[activeSlug]?.scrollIntoView({ block: 'nearest' });
  }, [activeSlug]);

  if (filtered.length === 0) return null;

  const minDepth = Math.min(...filtered.map((heading) => heading.depth));

  return (
    <nav aria-label="On this page" className="space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-[0.65rem] font-bold uppercase tracking-[0.14em] text-obs-text-muted">
          <ListTree className="h-3.5 w-3.5 text-obs-accent" />
          <span>On this page</span>
        </div>
        <span className="font-mono text-[0.6rem] text-obs-text-muted">{filtered.length}</span>
      </div>
      <ul className="space-y-0.5 border-l border-obs-border-subtle">
        {filtered.map((heading) => {
          const isActive = activeSlug === heading.slug;
          const indent = Math.max(0, heading.depth - minDepth);
          return (
            <li key={heading.slug}>
              <a
                ref={(element) => {
                  linkRefs.current[heading.slug] = element;
                }}
                href={`#${heading.slug}`}
                aria-current={isActive ? 'location' : undefined}
                style={{ paddingLeft: `${0.7 + indent * 0.65}rem` }}
                className={`-ml-px block break-words border-l py-1 pr-1 text-[0.73rem] leading-snug transition-colors ${
                  isActive
                    ? 'border-obs-accent font-semibold text-obs-accent'
                    : 'border-transparent text-obs-text-muted hover:border-obs-border hover:text-obs-text-primary'
                }`}
              >
                {heading.text}
              </a>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
