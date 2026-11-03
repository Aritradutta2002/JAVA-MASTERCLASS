import React, { useState, useEffect, useRef, useMemo } from 'react';
import type { GraphNode, GraphEdge } from '../lib/types';
import type { KnowledgeGraphData } from '../lib/graphBuilder';
import { DOMAIN_COLORS, DOMAIN_TITLES } from '../lib/constants';
import { Compass, ZoomIn, ZoomOut, RotateCcw, List, Eye, Search, ExternalLink } from 'lucide-react';

interface SimNode extends GraphNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
}

export default function ObsidianGraph() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const [graphData, setGraphData] = useState<KnowledgeGraphData | null>(null);
  const [selectedDomain, setSelectedDomain] = useState<string>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [hoveredNode, setHoveredNode] = useState<SimNode | null>(null);
  const [tooltipPos, setTooltipPos] = useState<{ x: number; y: number } | null>(null);
  const [viewMode, setViewMode] = useState<'canvas' | 'list'>('canvas');
  const [isLoading, setIsLoading] = useState(true);

  // Transform matrix for Pan & Zoom
  const transformRef = useRef({ x: 0, y: 0, k: 1 });
  const isDraggingRef = useRef(false);
  const dragStartRef = useRef({ x: 0, y: 0 });

  // Simulation nodes & edges stored in ref for 60fps animation
  const simNodesRef = useRef<SimNode[]>([]);
  const simEdgesRef = useRef<GraphEdge[]>([]);
  const animFrameIdRef = useRef<number | null>(null);

  // Fetch graph data
  useEffect(() => {
    fetch('/graph-data.json')
      .then((res) => res.json())
      .then((data: KnowledgeGraphData) => {
        setGraphData(data);
        setIsLoading(false);
      })
      .catch((err) => {
        console.error('Failed to load graph data:', err);
        setIsLoading(false);
      });
  }, []);

  // Filter nodes & edges
  const filteredData = useMemo(() => {
    if (!graphData) return { nodes: [], edges: [] };

    let nodes = graphData.nodes;
    if (selectedDomain !== 'all') {
      nodes = nodes.filter((n) => n.domain === selectedDomain);
    }
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      nodes = nodes.filter((n) => n.title.toLowerCase().includes(q) || n.domainTitle.toLowerCase().includes(q));
    }

    const nodeIds = new Set(nodes.map((n) => n.id));
    const edges = graphData.edges.filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target));

    return { nodes, edges };
  }, [graphData, selectedDomain, searchQuery]);

  // Initialize node positions in a circular galaxy pattern
  useEffect(() => {
    if (!filteredData.nodes.length) return;

    const width = containerRef.current?.clientWidth || 900;
    const height = containerRef.current?.clientHeight || 600;

    transformRef.current = { x: width / 2, y: height / 2, k: 0.85 };

    const nodes: SimNode[] = filteredData.nodes.map((n, i) => {
      const angle = (i / filteredData.nodes.length) * 2 * Math.PI;
      const radiusDist = 120 + Math.random() * 280;
      return {
        ...n,
        x: Math.cos(angle) * radiusDist,
        y: Math.sin(angle) * radiusDist,
        vx: (Math.random() - 0.5) * 2,
        vy: (Math.random() - 0.5) * 2,
        radius: Math.max(4, Math.min(18, 4 + Math.sqrt(n.degree) * 2.2)),
      };
    });

    simNodesRef.current = nodes;
    simEdgesRef.current = filteredData.edges;

    // Simulation loop
    let iteration = 0;
    const maxIterations = 300;

    function tick() {
      const simNodes = simNodesRef.current;
      const simEdges = simEdgesRef.current;
      const nodeMap = new Map<string, SimNode>();
      simNodes.forEach((n) => nodeMap.set(n.id, n));

      if (iteration < maxIterations) {
        // Repulsion between nodes
        for (let i = 0; i < simNodes.length; i++) {
          for (let j = i + 1; j < simNodes.length; j++) {
            const a = simNodes[i];
            const b = simNodes[j];
            const dx = b.x - a.x;
            const dy = b.y - a.y;
            const distSq = dx * dx + dy * dy + 10;
            if (distSq < 150000) {
              const force = 300 / distSq;
              a.vx -= dx * force;
              a.vy -= dy * force;
              b.vx += dx * force;
              b.vy += dy * force;
            }
          }
        }

        // Attraction along edges
        for (const edge of simEdges) {
          const source = nodeMap.get(edge.source);
          const target = nodeMap.get(edge.target);
          if (source && target) {
            const dx = target.x - source.x;
            const dy = target.y - source.y;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            const desiredDist = 70;
            const force = (dist - desiredDist) * 0.015;
            source.vx += (dx / dist) * force;
            source.vy += (dy / dist) * force;
            target.vx -= (dx / dist) * force;
            target.vy -= (dy / dist) * force;
          }
        }

        // Centering gravity & dampening
        for (const n of simNodes) {
          n.vx -= n.x * 0.002;
          n.vy -= n.y * 0.002;
          n.vx *= 0.88;
          n.vy *= 0.88;
          n.x += n.vx;
          n.y += n.vy;
        }

        iteration++;
      }

      draw();
      animFrameIdRef.current = requestAnimationFrame(tick);
    }

    function draw() {
      const canvas = canvasRef.current;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      const { x: tx, y: ty, k } = transformRef.current;
      const simNodes = simNodesRef.current;
      const simEdges = simEdgesRef.current;

      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.save();
      ctx.translate(tx, ty);
      ctx.scale(k, k);

      // Connected nodes set for active hover
      const connectedNodeIds = new Set<string>();
      if (hoveredNode) {
        connectedNodeIds.add(hoveredNode.id);
        simEdges.forEach((e) => {
          if (e.source === hoveredNode.id) connectedNodeIds.add(e.target);
          if (e.target === hoveredNode.id) connectedNodeIds.add(e.source);
        });
      }

      // Draw edges
      const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
      const defaultEdgeColor = isDark ? 'rgba(255, 255, 255, 0.07)' : 'rgba(0, 0, 0, 0.08)';
      const activeEdgeColor = isDark ? 'rgba(167, 139, 250, 0.6)' : 'rgba(124, 58, 237, 0.6)';

      const nodeMap = new Map<string, SimNode>();
      simNodes.forEach((n) => nodeMap.set(n.id, n));

      for (const edge of simEdges) {
        const s = nodeMap.get(edge.source);
        const t = nodeMap.get(edge.target);
        if (!s || !t) continue;

        const isEdgeActive = hoveredNode && (s.id === hoveredNode.id || t.id === hoveredNode.id);

        ctx.beginPath();
        ctx.moveTo(s.x, s.y);
        ctx.lineTo(t.x, t.y);
        ctx.strokeStyle = isEdgeActive ? activeEdgeColor : defaultEdgeColor;
        ctx.lineWidth = isEdgeActive ? 1.8 : 0.8;
        ctx.stroke();
      }

      // Draw nodes
      for (const node of simNodes) {
        const domainColor = DOMAIN_COLORS[node.domain]?.hex || '#a78bfa';
        const isHovered = hoveredNode?.id === node.id;
        const isConnected = hoveredNode ? connectedNodeIds.has(node.id) : true;

        ctx.beginPath();
        ctx.arc(node.x, node.y, isHovered ? node.radius * 1.3 : node.radius, 0, 2 * Math.PI);
        ctx.fillStyle = domainColor;
        ctx.globalAlpha = isConnected ? 1 : 0.2;
        ctx.fill();

        if (isHovered || node.degree >= 15) {
          ctx.strokeStyle = isDark ? '#ffffff' : '#0f172a';
          ctx.lineWidth = 1.5;
          ctx.stroke();

          // Draw label
          ctx.font = `${isHovered ? '600 12px' : '500 10px'} Inter, sans-serif`;
          ctx.fillStyle = isDark ? '#f8fafc' : '#0f172a';
          ctx.textAlign = 'center';
          ctx.fillText(node.title, node.x, node.y + node.radius + 12);
        }
        ctx.globalAlpha = 1;
      }

      ctx.restore();
    }

    if (animFrameIdRef.current) cancelAnimationFrame(animFrameIdRef.current);
    animFrameIdRef.current = requestAnimationFrame(tick);

    return () => {
      if (animFrameIdRef.current) cancelAnimationFrame(animFrameIdRef.current);
    };
  }, [filteredData, hoveredNode]);

  // Resize canvas to parent container
  useEffect(() => {
    function handleResize() {
      if (canvasRef.current && containerRef.current) {
        canvasRef.current.width = containerRef.current.clientWidth;
        canvasRef.current.height = containerRef.current.clientHeight;
      }
    }
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Canvas Mouse Interactions (Pan, Zoom, Hover, Click)
  const handleMouseDown = (e: React.MouseEvent) => {
    isDraggingRef.current = true;
    dragStartRef.current = { x: e.clientX, y: e.clientY };
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    if (isDraggingRef.current) {
      const dx = e.clientX - dragStartRef.current.x;
      const dy = e.clientY - dragStartRef.current.y;
      dragStartRef.current = { x: e.clientX, y: e.clientY };
      transformRef.current.x += dx;
      transformRef.current.y += dy;
      return;
    }

    // Check hit testing for hover
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;

    const { x: tx, y: ty, k } = transformRef.current;
    const worldX = (mouseX - tx) / k;
    const worldY = (mouseY - ty) / k;

    let found: SimNode | null = null;
    for (const node of simNodesRef.current) {
      const dx = worldX - node.x;
      const dy = worldY - node.y;
      if (dx * dx + dy * dy <= (node.radius + 5) * (node.radius + 5)) {
        found = node;
        break;
      }
    }

    if (found !== hoveredNode) {
      setHoveredNode(found);
      if (found) {
        setTooltipPos({ x: e.clientX, y: e.clientY });
      } else {
        setTooltipPos(null);
      }
    }
  };

  const handleMouseUp = () => {
    isDraggingRef.current = false;
  };

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9;
    const newK = Math.max(0.2, Math.min(3, transformRef.current.k * zoomFactor));
    transformRef.current.k = newK;
  };

  const handleClick = (e: React.MouseEvent) => {
    if (hoveredNode) {
      window.location.href = hoveredNode.slug;
    }
  };

  const zoomIn = () => {
    transformRef.current.k = Math.min(3, transformRef.current.k * 1.25);
  };
  const zoomOut = () => {
    transformRef.current.k = Math.max(0.2, transformRef.current.k * 0.8);
  };
  const resetZoom = () => {
    const width = containerRef.current?.clientWidth || 900;
    const height = containerRef.current?.clientHeight || 600;
    transformRef.current = { x: width / 2, y: height / 2, k: 0.85 };
  };

  return (
    <div className="relative flex flex-col h-full w-full overflow-hidden bg-obs-canvas select-none rounded-xl border border-obs-border">
      {/* Top Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-4 border-b border-obs-border bg-obs-sidebar/80 backdrop-blur-md z-10">
        <div className="flex items-center gap-2">
          <div className="flex h-7 w-7 items-center justify-center rounded-md bg-obs-accent/20 border border-obs-accent/40 text-obs-accent">
            <Compass className="h-4 w-4" />
          </div>
          <div>
            <h1 className="text-sm font-bold text-obs-text-primary">Knowledge Graph Explorer</h1>
            <p className="text-[11px] text-obs-text-muted">
              {filteredData.nodes.length} Topics · {filteredData.edges.length} Cross-Document Connections
            </p>
          </div>
        </div>

        {/* Filter buttons */}
        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          {['all', 'core-java', 'spring-boot', 'system-design', 'database-caching', 'java-dsa'].map((dom) => {
            const isSel = selectedDomain === dom;
            const label = dom === 'all' ? 'All Domains' : DOMAIN_TITLES[dom] || dom;
            return (
              <button
                key={dom}
                onClick={() => setSelectedDomain(dom)}
                className={`rounded-md px-2.5 py-1 text-xs transition-colors ${
                  isSel
                    ? 'bg-obs-accent text-white font-medium'
                    : 'border border-obs-border bg-obs-surface text-obs-text-secondary hover:bg-obs-hover'
                }`}
              >
                {label}
              </button>
            );
          })}
        </div>

        {/* Search & View Mode */}
        <div className="flex items-center gap-2">
          <div className="relative flex items-center">
            <Search className="absolute left-2.5 h-3.5 w-3.5 text-obs-text-muted pointer-events-none" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Filter nodes..."
              className="w-40 md:w-52 rounded-md border border-obs-border bg-obs-surface py-1 pl-8 pr-3 text-xs text-obs-text-primary placeholder:text-obs-text-muted focus:border-obs-accent focus:outline-none"
            />
          </div>

          <button
            onClick={() => setViewMode(viewMode === 'canvas' ? 'list' : 'canvas')}
            className="flex h-7 items-center gap-1.5 rounded-md border border-obs-border bg-obs-surface px-2.5 text-xs text-obs-text-secondary hover:bg-obs-hover"
            title="Toggle Accessible List View"
          >
            <List className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{viewMode === 'canvas' ? 'List View' : 'Graph'}</span>
          </button>
        </div>
      </div>

      {/* Main Canvas Area */}
      <div
        ref={containerRef}
        className="relative flex-1 w-full h-[70vh] cursor-grab active:cursor-grabbing overflow-hidden"
      >
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center text-xs text-obs-text-muted">
            Constructing knowledge relationships...
          </div>
        )}

        {viewMode === 'canvas' ? (
          <canvas
            ref={canvasRef}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onWheel={handleWheel}
            onClick={handleClick}
            className="w-full h-full block"
          />
        ) : (
          /* Accessible Table/List View */
          <div className="h-full overflow-y-auto p-4 max-w-4xl mx-auto space-y-2">
            {filteredData.nodes.map((node) => (
              <a
                key={node.id}
                href={node.slug}
                className="flex items-center justify-between p-3 rounded-lg border border-obs-border bg-obs-surface hover:border-obs-accent/50 transition-colors group"
              >
                <div>
                  <div className="font-semibold text-sm text-obs-text-primary group-hover:text-obs-accent">
                    {node.title}
                  </div>
                  <div className="text-xs text-obs-text-muted mt-0.5">
                    {node.domainTitle} · {node.inDegree} incoming references · {node.outDegree} outgoing links
                  </div>
                </div>
                <ExternalLink className="h-4 w-4 text-obs-text-muted group-hover:text-obs-accent" />
              </a>
            ))}
          </div>
        )}

        {/* Zoom & Reset Floating Controls */}
        {viewMode === 'canvas' && (
          <div className="absolute bottom-4 right-4 flex flex-col gap-1.5 bg-obs-surface/80 backdrop-blur-md p-1.5 rounded-lg border border-obs-border shadow-lg">
            <button
              onClick={zoomIn}
              className="p-1.5 rounded hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary"
              title="Zoom In"
            >
              <ZoomIn className="h-4 w-4" />
            </button>
            <button
              onClick={zoomOut}
              className="p-1.5 rounded hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary"
              title="Zoom Out"
            >
              <ZoomOut className="h-4 w-4" />
            </button>
            <button
              onClick={resetZoom}
              className="p-1.5 rounded hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary"
              title="Reset View"
            >
              <RotateCcw className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Hover Tooltip */}
        {hoveredNode && tooltipPos && (
          <div
            className="fixed z-50 pointer-events-none rounded-lg border border-obs-border bg-obs-surface/95 p-3 text-xs shadow-2xl backdrop-blur-md transition-all animate-fade-in"
            style={{
              left: `${Math.min(window.innerWidth - 260, tooltipPos.x + 15)}px`,
              top: `${Math.min(window.innerHeight - 120, tooltipPos.y + 15)}px`,
              maxWidth: '240px',
            }}
          >
            <div className="font-bold text-obs-text-primary leading-snug">
              {hoveredNode.title}
            </div>
            <div className="mt-1 flex items-center gap-1.5 text-[11px] text-obs-accent font-medium">
              <span>{hoveredNode.domainTitle}</span>
            </div>
            <div className="mt-2 flex items-center justify-between text-[10px] text-obs-text-muted border-t border-obs-border/50 pt-1.5">
              <span>{hoveredNode.inDegree} Backlinks</span>
              <span>{hoveredNode.outDegree} Outgoing</span>
            </div>
            <div className="mt-1 text-[10px] text-obs-accent/80 font-mono">
              Click node to read document →
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
