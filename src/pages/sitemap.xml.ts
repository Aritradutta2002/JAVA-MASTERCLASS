import type { APIRoute } from 'astro';
import { getAllDocs } from '../lib/content';

export const GET: APIRoute = async () => {
  const docs = getAllDocs();
  const staticRoutes = ['/', '/graph', '/checklist', '/index-all', '/java-dsa', '/tags'];
  const allSlugs = [...staticRoutes, ...docs.map(d => d.slug)];

  const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${allSlugs
  .map(
    (slug) => `  <url>
    <loc>https://java-masterclass.dev${slug}</loc>
    <changefreq>weekly</changefreq>
    <priority>${slug === '/' ? '1.0' : slug.startsWith('/core-java') || slug.startsWith('/spring-boot') || slug.startsWith('/system-design') ? '0.8' : '0.6'}</priority>
  </url>`
  )
  .join('\n')}
</urlset>`;

  return new Response(xml, {
    status: 200,
    headers: {
      'Content-Type': 'application/xml; charset=utf-8',
      'Cache-Control': 'public, max-age=86400',
    },
  });
};
