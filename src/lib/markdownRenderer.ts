import { createMarkdownProcessor } from '@astrojs/markdown-remark';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import rehypeSlug from 'rehype-slug';
import { transformMarkdownContent } from './linkResolver';

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
      rehypePlugins: [rehypeKatex, rehypeSlug],
    });
  }
  return processorPromise;
}

export async function renderMarkdown(content: string, docFilePath: string): Promise<string> {
  // 1. Transform relative links & wiki links
  const resolvedContent = transformMarkdownContent(content, docFilePath);

  // 2. Pre-process callouts: > [!NOTE] -> custom syntax or blockquote
  const calloutProcessed = resolvedContent.replace(
    /^>\s*\[!([A-Z]+)\]\s*(.*)$/gm,
    (_, type, title) => `> **[${type}]** ${title}`
  );

  // 3. Render markdown via Astro processor
  const processor = await getMarkdownProcessor();
  const result = await processor.render(calloutProcessed);
  let html = result.code;

  // 4. Wrap tables for responsive horizontal scrolling
  html = html.replace(/<table>/g, '<div class="table-wrapper"><table>');
  html = html.replace(/<\/table>/g, '</table></div>');

  // 5. Enhance mermaid diagram pre blocks
  html = html.replace(
    /<pre class="[^"]*"[^>]*data-language="mermaid"[^>]*><code>([\s\S]*?)<\/code><\/pre>/g,
    (_, code) => {
      // Decode HTML entities in mermaid code
      const decoded = code
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&amp;/g, '&')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'");
      return `<div class="mermaid-container my-6 p-4 rounded-xl border border-obs-border bg-obs-sidebar/80 overflow-x-auto"><div class="mermaid">${decoded}</div></div>`;
    }
  );

  return html;
}
