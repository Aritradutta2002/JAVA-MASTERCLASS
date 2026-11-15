import type { APIRoute } from 'astro';
import { buildNavigationTree, getAllDocs } from '../lib/content';

export const GET: APIRoute = () => {
  const navigation = buildNavigationTree(getAllDocs());
  return new Response(JSON.stringify(navigation), {
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Cache-Control': 'public, max-age=3600',
    },
  });
};
