import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { HeadingItem } from '../lib/types';
import { ArrowUp, BookOpen, Check, ListTree } from 'lucide-react';

interface Props {
  headings: HeadingItem[];
}

export default function TableOfContents({ headings }: Props) {
  const [activeSlug, setActiveSlug] = useState<string>('');
  const [readingProgress, setReadingProgress] = useState<number>(0);
  const linkRefs = useRef<Record<string, HTMLAnchorElement | null>>({});

  const filtered = useMemo(
    () => headings.filter((heading) => heading.depth >= 2 && heading.depth <= 4),
    [headings]
  );

  // Track active heading using IntersectionObserver with sticky header margin
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
        if (visible[0]?.target.id) {
          setActiveSlug(visible[0].target.id);
        }
      },
      { rootMargin: '-72px 0% -60% 0%', threshold: [0, 0.5, 1] }
    );

    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [filtered]);

  // Scroll active TOC item into view inside the right sidebar
  useEffect(() => {
    if (activeSlug && linkRefs.current[activeSlug]) {
      linkRefs.current[activeSlug]?.scrollIntoView({
        block: 'nearest',
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
      });
    }
  }, [activeSlug]);

  // Track document reading progress percentage
  useEffect(() => {
    const handleScroll = () => {
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;
      if (docHeight <= 0) {
        setReadingProgress(100);
        return;
      }
      const scrolled = window.scrollY;
      const progress = Math.min(100, Math.max(0, Math.round((scrolled / docHeight) * 100)));
      setReadingProgress(progress);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll();
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);
  if (filtered.length === 0) return null;

  const minDepth = Math.min(...filtered.map((heading) => heading.depth));

  const handleHeadingClick = (event: React.MouseEvent<HTMLAnchorElement>, slug: string) => {
    event.preventDefault();
    const element = document.getElementById(slug);
    if (!element) return;

    const headerOffset = 76;
    const elementPosition = element.getBoundingClientRect().top;
    const offsetPosition = elementPosition + window.pageYOffset - headerOffset;

    window.scrollTo({
      top: offsetPosition,
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',
    });

    history.pushState(null, '', `#${slug}`);
    setActiveSlug(slug);
  };

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <nav aria-label="Table of contents" className="flex flex-col select-none">
      {/* Header with Title and Section count */}
      <div className="flex items-center justify-between pb-2">
        <div className="flex items-center gap-2">
          <span className="flex h-5 w-5 items-center justify-center rounded-sm border border-obs-border bg-obs-canvas-soft text-obs-text-muted">
            <ListTree className="h-3 w-3" />
          </span>
          <span className="text-caption font-bold uppercase tracking-[0.14em] text-obs-text-muted">
            On This Page
          </span>
        </div>
        <span className="rounded-sm border border-obs-border/70 bg-obs-canvas-soft px-1.5 py-0.5 font-mono text-micro font-medium text-obs-text-muted">
          {filtered.length}
        </span>
      </div>

      {/* Reading Progress Indicator Bar */}
      <div className="mb-3.5 mt-1">
        <div className="flex items-center justify-between text-micro font-mono text-obs-text-muted">
          <span className="flex items-center gap-1 text-micro uppercase tracking-wider">
            <BookOpen className="h-2.5 w-2.5" />
            Reading Progress
          </span>
          <span className="font-semibold text-obs-text-primary">
            {readingProgress}%
          </span>
        </div>
        <div className="mt-1 h-1 w-full overflow-hidden rounded-sm bg-obs-border/50">
          <div
            className="h-full rounded-sm bg-obs-accent transition-[width] duration-150 ease-out"
            style={{ width: `${readingProgress}%` }}
            role="progressbar"
            aria-label="Reading progress"
            aria-valuenow={readingProgress}
            aria-valuemin={0}
            aria-valuemax={100}
          />
        </div>
      </div>

      {/* Hierarchical heading tree */}
      <div className="relative border-l border-obs-border/60 pl-0">
        <ul className="space-y-0.5">
          {filtered.map((heading) => {
            const isActive = activeSlug === heading.slug;
            const indentLevel = Math.max(0, heading.depth - minDepth);

            return (
              <li key={heading.slug} className="relative">
                {/* Active heading marker: state only */}
                {isActive && (
                  <span
                    className="absolute -left-[1.5px] top-1 bottom-1 w-[3px] rounded-sm bg-obs-accent transition-all duration-200"
                    aria-hidden="true"
                  />
                )}
                <a
                  ref={(element) => {
                    linkRefs.current[heading.slug] = element;
                  }}
                  href={`#${heading.slug}`}
                  onClick={(e) => handleHeadingClick(e, heading.slug)}
                  aria-current={isActive ? 'location' : undefined}
                  style={{
                    paddingLeft: `${0.75 + indentLevel * 0.7}rem`,
                  }}
                  title={heading.text}
                  className={`group flex items-center justify-between rounded-r-lg py-1.5 pr-2 text-caption leading-snug transition-colors duration-150 ${
                    isActive
                      ? 'bg-obs-accent/10 font-semibold text-obs-accent shadow-2xs'
                      : 'text-obs-text-muted hover:bg-obs-surface/60 hover:text-obs-text-primary'
                  }`}
                >
                  <span className="truncate">{heading.text}</span>
                  {isActive && (
                    <span className="ml-1 flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-sm bg-obs-accent-subtle text-obs-accent">
                      <Check className="h-2 w-2 stroke-[2.5]" />
                    </span>
                  )}
                </a>
              </li>
            );
          })}
        </ul>
      </div>

      {/* Back to Top shortcut button */}
      <div className="mt-4 border-t border-obs-border/50 pt-2.5">
        <button
          type="button"
          onClick={scrollToTop}
          className="flex w-full items-center justify-between rounded-sm px-2 py-1.5 text-caption font-medium text-obs-text-muted transition-all hover:bg-obs-surface/80 hover:text-obs-accent"
        >
          <span className="flex items-center gap-1.5">
            <ArrowUp className="h-3 w-3" />
            Scroll to top
          </span>
          <kbd className="rounded border border-obs-border/60 bg-obs-canvas-soft px-1 text-micro font-mono text-obs-text-muted">
            Top
          </kbd>
        </button>
      </div>
    </nav>
  );
}
