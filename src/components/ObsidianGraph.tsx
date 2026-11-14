import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import type { GraphNode, GraphEdge } from '../lib/types';
import type { KnowledgeGraphData } from '../lib/graphBuilder';
import { DOMAIN_COLORS, DOMAIN_TITLES } from '../lib/constants';
import {
  Compass,
  ZoomIn,
  ZoomOut,
  RotateCcw,
  Maximize2,
  List,
  Search,
  ExternalLink,
  Sparkles,
  ArrowUpRight,
  X,
} from 'lucide-react';

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
  const transformRef = useRef({ x: 0, y: 0, k: 0.85 });
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
      nodes = nodes.filter(
        (n) => n.title.toLowerCase().includes(q) || n.domainTitle.toLowerCase().includes(q)
      );
    }

    const nodeIds = new Set(nodes.map((n) => n.id));
    const edges = graphData.edges.filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target));

    return { nodes, edges };
  }, [graphData, selectedDomain, searchQuery]);

  // Compute domain node counts
  const domainCounts = useMemo(() => {
    if (!graphData) return {};
    const counts: Record<string, number> = {};
    graphData.nodes.forEach((n) => {
      counts[n.domain] = (counts[n.domain] || 0) + 1;
    });
    return counts;
  }, [graphData]);

  // Initialize node positions in a circular galaxy pattern
  useEffect(() => {
    if (!filteredData.nodes.length) return;

    const width = containerRef.current?.clientWidth || 900;
    const height = containerRef.current?.clientHeight || 600;

    transformRef.current = { x: width / 2, y: height / 2, k: 0.85 };

    const nodes: SimNode[] = filteredData.nodes.map((n, i) => {
      const angle = (i / filteredData.nodes.length) * 2 * Math.PI;
      const radiusDist = 130 + Math.random() * 320;
      return {
        ...n,
        x: Math.cos(angle) * radiusDist,
        y: Math.sin(angle) * radiusDist,
        vx: (Math.random() - 0.5) * 2,
        vy: (Math.random() - 0.5) * 2,
        radius: Math.max(5, Math.min(20, 5 + Math.sqrt(n.degree) * 2.3)),
      };
    });

    simNodesRef.current = nodes;
    simEdgesRef.current = filteredData.edges;

    // Simulation loop
    let iteration = 0;
    const maxIterations = 320;

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
            const distSq = dx * dx + dy * dy + 15;
            if (distSq < 180000) {
              const force = 320 / distSq;
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
            const desiredDist = 80;
            const force = (dist - desiredDist) * 0.016;
            source.vx += (dx / dist) * force;
            source.vy += (dy / dist) * force;
            target.vx -= (dx / dist) * force;
            target.vy -= (dy / dist) * force;
          }
        }

        // Centering gravity & dampening
        for (const n of simNodes) {
          n.vx -= n.x * 0.0022;
          n.vy -= n.y * 0.0022;
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

      const isDark = document.documentElement.getAttribute('data-theme') !== 'light';

      ctx.save();
      ctx.translate(tx, ty);
      ctx.scale(k, k);

      // 1. Draw subtle infinite dot grid
      const gridSize = 50;
      const startX = Math.floor((-tx / k) / gridSize) * gridSize;
      const endX = Math.ceil(((canvas.width - tx) / k) / gridSize) * gridSize;
      const startY = Math.floor((-ty / k) / gridSize) * gridSize;
      const endY = Math.ceil(((canvas.height - ty) / k) / gridSize) * gridSize;

      ctx.fillStyle = isDark ? 'rgba(255, 255, 255, 0.04)' : 'rgba(0, 0, 0, 0.05)';
      const dotRadius = Math.max(0.7, 1.2 / k);
      for (let x = startX; x <= endX; x += gridSize) {
        for (let y = startY; y <= endY; y += gridSize) {
          ctx.beginPath();
          ctx.arc(x, y, dotRadius, 0, 2 * Math.PI);
          ctx.fill();
        }
      }

      // 2. Connected nodes set for active hover
      const connectedNodeIds = new Set<string>();
      if (hoveredNode) {
        connectedNodeIds.add(hoveredNode.id);
        simEdges.forEach((e) => {
          if (e.source === hoveredNode.id) connectedNodeIds.add(e.target);
          if (e.target === hoveredNode.id) connectedNodeIds.add(e.source);
        });
      }

      const defaultEdgeColor = isDark ? 'rgba(255, 255, 255, 0.08)' : 'rgba(0, 0, 0, 0.09)';
      const nodeMap = new Map<string, SimNode>();
      simNodes.forEach((n) => nodeMap.set(n.id, n));

      // 3. Draw edges (passive first, active on top with glow)
      for (const edge of simEdges) {
        const s = nodeMap.get(edge.source);
        const t = nodeMap.get(edge.target);
        if (!s || !t) continue;

        const isEdgeActive = hoveredNode && (s.id === hoveredNode.id || t.id === hoveredNode.id);
        if (isEdgeActive) continue; // draw active in next pass

        ctx.beginPath();
        ctx.moveTo(s.x, s.y);
        ctx.lineTo(t.x, t.y);
        ctx.strokeStyle = defaultEdgeColor;
        ctx.lineWidth = 0.8;
        ctx.globalAlpha = hoveredNode ? 0.15 : 0.8;
        ctx.stroke();
      }

      // Draw active glowing edges
      if (hoveredNode) {
        const hoverColor = DOMAIN_COLORS[hoveredNode.domain]?.hex || '#a78bfa';
        for (const edge of simEdges) {
          const s = nodeMap.get(edge.source);
          const t = nodeMap.get(edge.target);
          if (!s || !t) continue;

          const isEdgeActive = s.id === hoveredNode.id || t.id === hoveredNode.id;
          if (!isEdgeActive) continue;

          ctx.save();
          ctx.beginPath();
          ctx.moveTo(s.x, s.y);
          ctx.lineTo(t.x, t.y);
          ctx.strokeStyle = hoverColor;
          ctx.lineWidth = 2.2;
          ctx.shadowColor = hoverColor;
          ctx.shadowBlur = 12;
          ctx.globalAlpha = 1;
          ctx.stroke();
          ctx.restore();
        }
      }

      // 4. Draw nodes
      for (const node of simNodes) {
        const domainColor = DOMAIN_COLORS[node.domain]?.hex || '#a78bfa';
        const isHovered = hoveredNode?.id === node.id;
        const isConnected = hoveredNode ? connectedNodeIds.has(node.id) : true;
        const currentRadius = isHovered ? node.radius * 1.35 : node.radius;

        ctx.save();
        ctx.globalAlpha = isConnected ? 1 : 0.18;

        // Glowing outer halo for hub nodes or hovered node
        if (isHovered || (isConnected && node.degree >= 12)) {
          ctx.shadowColor = domainColor;
          ctx.shadowBlur = isHovered ? 24 : 12;

          ctx.beginPath();
          ctx.arc(node.x, node.y, currentRadius + (isHovered ? 4 : 2), 0, 2 * Math.PI);
          ctx.strokeStyle = isHovered ? '#ffffff' : `${domainColor}66`;
          ctx.lineWidth = isHovered ? 2 : 1;
          ctx.stroke();
        }

        // Inner node body
        ctx.beginPath();
        ctx.arc(node.x, node.y, currentRadius, 0, 2 * Math.PI);
        ctx.fillStyle = domainColor;
        ctx.fill();

        // Node rim stroke
        ctx.strokeStyle = isHovered
          ? (isDark ? '#ffffff' : '#0f172a')
          : (isDark ? 'rgba(255, 255, 255, 0.4)' : 'rgba(0, 0, 0, 0.25)');
        ctx.lineWidth = isHovered ? 2 : 1;
        ctx.stroke();
        ctx.restore();

        // 5. Draw node labels with polished pill background
        const shouldShowLabel = isHovered || (node.degree >= 14 && k >= 0.75) || k >= 1.5;
        if (shouldShowLabel && isConnected) {
          ctx.save();
          const text = node.title;
          ctx.font = `${isHovered ? '600 12px' : '500 10.5px'} Inter, sans-serif`;
          const metrics = ctx.measureText(text);
          const padX = 6;
          const padY = 3;
          const textH = isHovered ? 13 : 11;
          const badgeW = metrics.width + padX * 2;
          const badgeH = textH + padY * 2;
          const badgeX = node.x - badgeW / 2;
          const badgeY = node.y + currentRadius + (isHovered ? 10 : 6);

          // Pill background
          ctx.fillStyle = isDark ? 'rgba(15, 17, 23, 0.88)' : 'rgba(255, 255, 255, 0.92)';
          ctx.strokeStyle = isHovered ? domainColor : (isDark ? 'rgba(255, 255, 255, 0.12)' : 'rgba(0, 0, 0, 0.12)');
          ctx.lineWidth = 1;

          ctx.beginPath();
          if (typeof ctx.roundRect === 'function') {
            ctx.roundRect(badgeX, badgeY, badgeW, badgeH, 4);
          } else {
            ctx.rect(badgeX, badgeY, badgeW, badgeH);
          }
          ctx.fill();
          ctx.stroke();

          // Pill text
          ctx.fillStyle = isHovered ? (isDark ? '#ffffff' : '#0f172a') : (isDark ? '#e2e8f0' : '#1e293b');
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText(text, node.x, badgeY + badgeH / 2);
          ctx.restore();
        }
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

    // Hit testing for hover
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
      if (dx * dx + dy * dy <= (node.radius + 6) * (node.radius + 6)) {
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
    const zoomFactor = e.deltaY < 0 ? 1.12 : 0.88;
    const newK = Math.max(0.15, Math.min(3.5, transformRef.current.k * zoomFactor));
    transformRef.current.k = newK;
  };

  const handleClick = () => {
    if (hoveredNode) {
      window.location.href = hoveredNode.slug;
    }
  };

  const zoomIn = () => {
    transformRef.current.k = Math.min(3.5, transformRef.current.k * 1.3);
  };
  const zoomOut = () => {
    transformRef.current.k = Math.max(0.15, transformRef.current.k * 0.75);
  };
  const resetZoom = () => {
    const width = containerRef.current?.clientWidth || 900;
    const height = containerRef.current?.clientHeight || 600;
    transformRef.current = { x: width / 2, y: height / 2, k: 0.85 };
  };

  const fitView = useCallback(() => {
    const simNodes = simNodesRef.current;
    if (!simNodes.length || !containerRef.current) return;

    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    simNodes.forEach((n) => {
      minX = Math.min(minX, n.x - n.radius);
      maxX = Math.max(maxX, n.x + n.radius);
      minY = Math.min(minY, n.y - n.radius);
      maxY = Math.max(maxY, n.y + n.radius);
    });

    const w = containerRef.current.clientWidth;
    const h = containerRef.current.clientHeight;
    const graphW = maxX - minX + 100;
    const graphH = maxY - minY + 100;

    const scale = Math.max(0.2, Math.min(2.0, Math.min(w / graphW, h / graphH) * 0.9));
    const centerX = (minX + maxX) / 2;
    const centerY = (minY + maxY) / 2;

    transformRef.current = {
      x: w / 2 - centerX * scale,
      y: h / 2 - centerY * scale,
      k: scale,
    };
  }, []);

  return (
    <div className="relative flex flex-col h-full w-full overflow-hidden bg-obs-canvas select-none rounded-2xl border border-obs-border/90 shadow-obs-lg">
      {/* Top Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 p-4 border-b border-obs-border/80 bg-obs-sidebar/85 backdrop-blur-md z-10">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-obs-accent/15 border border-obs-accent/30 text-obs-accent shadow-xs">
            <Compass className="h-4.5 w-4.5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm font-bold text-obs-text-primary tracking-tight">
                Knowledge Graph Explorer
              </h1>
              <span className="hidden sm:inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-mono font-medium bg-obs-surface border border-obs-border text-obs-text-muted">
                Obsidian Engine
              </span>
            </div>
            <p className="text-[11px] text-obs-text-muted">
              {filteredData.nodes.length} Topics · {filteredData.edges.length} Cross-Document Relations
            </p>
          </div>
        </div>

        {/* Domain Filter Pills */}
        <div className="flex flex-wrap items-center gap-1.5 text-xs">
          {['all', 'core-java', 'spring-boot', 'system-design', 'database-caching', 'java-dsa'].map((dom) => {
            const isSel = selectedDomain === dom;
            const label = dom === 'all' ? 'All Domains' : DOMAIN_TITLES[dom] || dom;
            const count = dom === 'all' ? graphData?.nodes.length || 0 : domainCounts[dom] || 0;
            const color = dom === 'all' ? null : DOMAIN_COLORS[dom];

            return (
              <button
                key={dom}
                type="button"
                onClick={() => setSelectedDomain(dom)}
                className={`rounded-lg px-2.5 py-1 text-xs transition-all flex items-center gap-1.5 border ${
                  isSel
                    ? 'bg-obs-accent text-white font-semibold border-obs-accent shadow-xs'
                    : 'border-obs-border/70 bg-obs-surface text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'
                }`}
              >
                {color && (
                  <span
                    className="h-1.5 w-1.5 rounded-full"
                    style={{ backgroundColor: color.hex }}
                  />
                )}
                <span>{label}</span>
                <span className={`text-[10px] font-mono ${isSel ? 'text-white/80' : 'text-obs-text-muted'}`}>
                  {count}
                </span>
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
              placeholder="Filter graph nodes..."
              className="w-36 md:w-48 rounded-lg border border-obs-border/80 bg-obs-surface/80 py-1 pl-8 pr-7 text-xs text-obs-text-primary placeholder:text-obs-text-muted focus:border-obs-accent focus:bg-obs-surface focus:outline-none transition-all"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-2 text-obs-text-muted hover:text-obs-text-primary"
                aria-label="Clear graph search query"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>

          <button
            type="button"
            onClick={() => setViewMode(viewMode === 'canvas' ? 'list' : 'canvas')}
            className="flex h-7 items-center gap-1.5 rounded-lg border border-obs-border/80 bg-obs-surface px-2.5 text-xs font-medium text-obs-text-secondary hover:bg-obs-hover transition-colors shadow-xs"
            title="Toggle Accessible List View"
          >
            {viewMode === 'canvas' ? <List className="h-3.5 w-3.5" /> : <Compass className="h-3.5 w-3.5" />}
            <span className="hidden sm:inline">{viewMode === 'canvas' ? 'List View' : 'Graph View'}</span>
          </button>
        </div>
      </div>

      {/* Main Canvas Area */}
      <div
        ref={containerRef}
        className="relative flex-1 w-full h-[72vh] cursor-grab active:cursor-grabbing overflow-hidden bg-obs-canvas"
      >
        {isLoading && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-xs text-obs-text-muted bg-obs-canvas/80 backdrop-blur-xs z-20">
            <Sparkles className="h-5 w-5 animate-pulse text-obs-accent" />
            <span>Constructing multi-domain graph matrix...</span>
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
          <div className="h-full overflow-y-auto p-4 sm:p-6 max-w-4xl mx-auto space-y-2.5">
            <div className="text-xs font-semibold uppercase tracking-wider text-obs-text-muted px-1">
              Showing {filteredData.nodes.length} connected topics
            </div>
            {filteredData.nodes.map((node) => {
              const domainColor = DOMAIN_COLORS[node.domain] || DOMAIN_COLORS.root;
              return (
                <a
                  key={node.id}
                  href={node.slug}
                  className="flex items-center justify-between p-3.5 rounded-xl border border-obs-border/70 bg-obs-surface/60 hover:border-obs-accent/50 hover:bg-obs-surface transition-all group shadow-xs hover:shadow-sm"
                >
                  <div className="flex items-center gap-3">
                    <span
                      className="h-2.5 w-2.5 rounded-full shrink-0"
                      style={{ backgroundColor: domainColor.hex }}
                    />
                    <div>
                      <div className="font-semibold text-xs sm:text-sm text-obs-text-primary group-hover:text-obs-accent transition-colors">
                        {node.title}
                      </div>
                      <div className="text-[11px] text-obs-text-muted mt-0.5 flex items-center gap-2">
                        <span style={{ color: domainColor.text }}>{node.domainTitle}</span>
                        <span>·</span>
                        <span>{node.inDegree} incoming references</span>
                        <span>·</span>
                        <span>{node.outDegree} outgoing links</span>
                      </div>
                    </div>
                  </div>
                  <ExternalLink className="h-4 w-4 text-obs-text-muted group-hover:text-obs-accent group-hover:translate-x-0.5 transition-all" />
                </a>
              );
            })}
          </div>
        )}

        {/* Zoom & Reset Floating Controls */}
        {viewMode === 'canvas' && (
          <div className="absolute bottom-5 right-5 flex flex-col gap-1.5 bg-obs-surface/85 backdrop-blur-md p-1.5 rounded-xl border border-obs-border/80 shadow-obs-lg z-10">
            <button
              type="button"
              onClick={zoomIn}
              className="p-1.5 rounded-lg hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Zoom In (+)"
              aria-label="Zoom In"
            >
              <ZoomIn className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={zoomOut}
              className="p-1.5 rounded-lg hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Zoom Out (-)"
              aria-label="Zoom Out"
            >
              <ZoomOut className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={fitView}
              className="p-1.5 rounded-lg hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Fit to Screen"
              aria-label="Fit to Screen"
            >
              <Maximize2 className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={resetZoom}
              className="p-1.5 rounded-lg hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Reset View"
              aria-label="Reset View"
            >
              <RotateCcw className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Canvas Navigation Hint Pill */}
        {viewMode === 'canvas' && (
          <div className="absolute bottom-5 left-5 hidden md:flex items-center gap-3 px-3 py-1.5 rounded-lg bg-obs-surface/80 backdrop-blur-md border border-obs-border/70 text-[11px] text-obs-text-muted select-none pointer-events-none">
            <span>Drag to pan</span>
            <span>·</span>
            <span>Scroll to zoom</span>
            <span>·</span>
            <span>Click node to open</span>
          </div>
        )}

        {/* Hover Tooltip Card */}
        {hoveredNode && tooltipPos && (
          <div
            className="fixed z-50 pointer-events-none rounded-xl border border-obs-border bg-obs-surface/95 p-3.5 text-xs shadow-2xl backdrop-blur-xl transition-all animate-fade-in"
            style={{
              left: `${Math.min(window.innerWidth - 280, tooltipPos.x + 16)}px`,
              top: `${Math.min(window.innerHeight - 150, tooltipPos.y + 16)}px`,
              maxWidth: '260px',
            }}
          >
            {/* Top domain badge */}
            <div className="flex items-center gap-1.5">
              <span
                className="h-2 w-2 rounded-full"
                style={{
                  backgroundColor: DOMAIN_COLORS[hoveredNode.domain]?.hex || '#a78bfa',
                }}
              />
              <span
                className="text-[10px] font-semibold uppercase tracking-wider"
                style={{
                  color: DOMAIN_COLORS[hoveredNode.domain]?.text || '#a78bfa',
                }}
              >
                {hoveredNode.domainTitle}
              </span>
            </div>

            <div className="font-bold text-obs-text-primary text-xs leading-snug mt-1">
              {hoveredNode.title}
            </div>

            <div className="mt-2.5 grid grid-cols-2 gap-2 border-t border-obs-border/60 pt-2 text-[10px]">
              <div className="bg-obs-canvas/80 p-1.5 rounded border border-obs-border/40">
                <span className="text-obs-text-muted block">Backlinks</span>
                <span className="font-mono font-bold text-obs-text-primary">{hoveredNode.inDegree} incoming</span>
              </div>
              <div className="bg-obs-canvas/80 p-1.5 rounded border border-obs-border/40">
                <span className="text-obs-text-muted block">References</span>
                <span className="font-mono font-bold text-obs-text-primary">{hoveredNode.outDegree} outgoing</span>
              </div>
            </div>

            <div className="mt-2 text-[10px] text-obs-accent font-mono flex items-center justify-between">
              <span>Click to view chapter</span>
              <ArrowUpRight className="h-3 w-3" />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
