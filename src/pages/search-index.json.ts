import type { APIRoute } from 'astro';
import { buildSearchIndex } from '../lib/searchIndex';

export const GET: APIRoute = async () => {
  const indexData = buildSearchIndex();
  return new Response(JSON.stringify(indexData), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Cache-Control': 'public, max-age=3600',
    },
  });
};
