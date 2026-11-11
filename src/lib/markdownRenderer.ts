import { createMarkdownProcessor } from '@astrojs/markdown-remark';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import rehypeSlug from 'rehype-slug';
import rehypeAutolinkHeadings from 'rehype-autolink-headings';
import { transformMarkdownContent } from './linkResolver';

const CALLOUT_LABELS: Record<string, string> = {
  NOTE: 'Note',
  TIP: 'Tip',
  HINT: 'Hint',
  IMPORTANT: 'Important',
  WARNING: 'Warning',
  CAUTION: 'Caution',
  DANGER: 'Danger',
  ERROR: 'Error',
  BUG: 'Bug',
  EXAMPLE: 'Example',
  QUOTE: 'Quote',
  CITE: 'Cite',
  ABSTRACT: 'Abstract',
  SUMMARY: 'Summary',
  TODO: 'To do',
  SUCCESS: 'Success',
  CHECK: 'Check',
  DONE: 'Done',
  QUESTION: 'Question',
  HELP: 'Help',
  FAQ: 'FAQ',
  FAILURE: 'Failure',
  FAIL: 'Fail',
  MISSING: 'Missing',
};

let processorPromise: ReturnType<typeof createMarkdownProcessor> | null = null;

export async function getMarkdownProcessor() {
  if (!processorPromise) {
    processorPromise = createMarkdownProcessor({
      syntaxHighlight: 'shiki',
      shikiConfig: {
        themes: {
          dark: 'one-dark-pro',
          light: 'github-light',
        },
        wrap: true,
      },
      remarkPlugins: [remarkMath],
      rehypePlugins: [
        rehypeKatex,
        rehypeSlug,
        [
          rehypeAutolinkHeadings,
          {
            behavior: 'append',
            test: ['h2', 'h3', 'h4'],
            properties: {
              className: ['heading-anchor'],
              ariaLabel: 'Copy link to this section',
            },
            content: {
              type: 'element',
              tagName: 'span',
              properties: { className: ['heading-anchor-icon'], ariaHidden: 'true' },
              children: [{ type: 'text', value: '#' }],
            },
          },
        ],
      ],
    });
  }
  return processorPromise;
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function preprocessCallouts(markdown: string): string {
  return markdown.replace(
    /^>\s*\[!([A-Za-z]+)\]\s*(.*)$/gm,
    (_, rawType: string, title: string) => {
      const type = rawType.toUpperCase();
      const label = CALLOUT_LABELS[type] || rawType;
      const safeTitle = title.trim();
      return `> <span class="callout-label" data-callout="${type.toLowerCase()}">${label}</span>${safeTitle ? ` **${safeTitle}**` : ''}`;
    }
  );
}

interface ExtractedDiagram {
  source: string;
}

function extractMermaid(markdown: string): { markdown: string; diagrams: ExtractedDiagram[] } {
  // Stash outer code fences with 4+ backticks so nested ```mermaid examples in authoring docs are not stripped
  const stashedOuter: string[] = [];
  const withoutOuter = markdown.replace(/^(`{4,})[^\n]*\n[\s\S]*?^\1\s*$/gm, (match) => {
    const idx = stashedOuter.length;
    stashedOuter.push(match);
    return `<!--STASHED_OUTER_FENCE_${idx}-->`;
  });

  const diagrams: ExtractedDiagram[] = [];
  const markdownWithout = withoutOuter.replace(
    /^```mermaid[^\n]*\n([\s\S]*?)^```\s*$/gim,
    (_, source: string) => {
      const index = diagrams.length;
      diagrams.push({ source: source.replace(/\s+$/, '') });
      return `<div data-mermaid-placeholder="${index}"></div>`;
    }
  );

  const restoredMarkdown = markdownWithout.replace(/<!--STASHED_OUTER_FENCE_(\d+)-->/g, (_, idx) => {
    return stashedOuter[Number(idx)] || '';
  });

  return { markdown: restoredMarkdown, diagrams };
}

function sanitizeHtml(html: string): string {
  return html
    .replace(/<script\b[\s\S]*?<\/script>/gi, '')
    .replace(/<iframe\b[\s\S]*?<\/iframe>/gi, '')
    .replace(/<object\b[\s\S]*?<\/object>/gi, '')
    .replace(/<embed\b[^>]*>/gi, '')
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    .replace(/\s(href|src)\s*=\s*("|')\s*javascript:[^"']*\2/gi, ' $1="#"');
}

function wrapTables(html: string): string {
  return html
    .replace(
      /<table>/g,
      '<div class="table-scroll" tabindex="0" role="region" aria-label="Scrollable data table"><table>'
    )
    .replace(/<\/table>/g, '</table></div>');
}

function enhanceExternalLinks(html: string): string {
  return html.replace(/<a\s+([^>]*href="https?:\/\/[^"]+"[^>]*)>/gi, (_match, attrs: string) => {
    let next = attrs;
    if (!/\btarget=/.test(next)) next += ' target="_blank"';
    if (!/\brel=/.test(next)) next += ' rel="noopener noreferrer"';
    if (!/\bclass=/.test(next)) next += ' class="external-link"';
    return `<a ${next}>`;
  });
}

function injectDiagrams(html: string, diagrams: ExtractedDiagram[]): string {
  return html.replace(/<div data-mermaid-placeholder="(\d+)"><\/div>/g, (_, rawIndex: string) => {
    const diagram = diagrams[Number(rawIndex)];
    if (!diagram) return '';
    return `<figure class="mermaid-figure">
  <div class="mermaid-container">
    <div class="mermaid" data-mermaid-source="${escapeHtml(diagram.source)}">${escapeHtml(diagram.source)}</div>
  </div>
  <details class="mermaid-source" data-pagefind-ignore>
    <summary>Diagram source</summary>
    <pre><code class="language-mermaid">${escapeHtml(diagram.source)}</code></pre>
  </details>
</figure>`;
  });
}

function enhanceImages(html: string): string {
  return html.replace(/<img\s+([^>]*?)>/gi, (_match, attrs: string) => {
    let next = attrs;
    if (!/\bloading=/.test(next)) next += ' loading="lazy"';
    if (!/\bdecoding=/.test(next)) next += ' decoding="async"';
    return `<img ${next}>`;
  });
}

function annotateCodeBlocks(html: string): string {
  // Add an accessible label to each highlighted block. The visible header is
  // added by the lightweight client enhancer.
  return html.replace(
    /<pre\s+([^>]*?)data-language="([^"]+)"([^>]*)>/gi,
    (match, before: string, language: string, after: string) => {
      if (/\bdata-code-language=/.test(before + after)) return match;
      return `<pre ${before}data-language="${language}" data-code-language="${language}"${after}>`;
    }
  );
}

function stripLeadingTitle(markdown: string): string {
  const lines = markdown.split('\n');
  let index = 0;
  while (index < lines.length && lines[index].trim() === '') index += 1;
  if (index < lines.length && /^#\s+/.test(lines[index].trim())) {
    lines.splice(index, 1);
    // Drop one blank line left behind by the removed heading.
    if (lines[index] !== undefined && lines[index].trim() === '') lines.splice(index, 1);
  }
  return lines.join('\n');
}

export async function renderMarkdown(
  content: string,
  docFilePath: string,
  options: { stripTitle?: boolean } = {}
): Promise<string> {
  const withTitleHandled = options.stripTitle === false ? content : stripLeadingTitle(content);
  const withLinks = transformMarkdownContent(withTitleHandled, docFilePath);
  const withCallouts = preprocessCallouts(withLinks);
  const { markdown, diagrams } = extractMermaid(withCallouts);

  const processor = await getMarkdownProcessor();
  const result = await processor.render(markdown);
  let html = result.code;

  html = sanitizeHtml(html);
  html = wrapTables(html);
  html = enhanceExternalLinks(html);
  html = enhanceImages(html);
  html = annotateCodeBlocks(html);
  html = injectDiagrams(html, diagrams);

  return html;
}
