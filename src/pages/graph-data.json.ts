import type { APIRoute } from 'astro';
import { buildKnowledgeGraph } from '../lib/graphBuilder';

export const GET: APIRoute = async () => {
  const graph = buildKnowledgeGraph();
  return new Response(JSON.stringify(graph), {
    status: 200,
    headers: {
      'Content-Type': 'application/json',
      'Cache-Control': 'public, max-age=3600',
    },
  });
};
