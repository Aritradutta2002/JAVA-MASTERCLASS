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
  const [loadError, setLoadError] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  // Transform matrix for Pan & Zoom
  const transformRef = useRef({ x: 0, y: 0, k: 0.85 });
  const isDraggingRef = useRef(false);
  const dragStartRef = useRef({ x: 0, y: 0 });

  // Simulation nodes & edges stored in ref for 60fps animation
  const simNodesRef = useRef<SimNode[]>([]);
  const simEdgesRef = useRef<GraphEdge[]>([]);
  const animFrameIdRef = useRef<number | null>(null);
  const hoveredNodeRef = useRef<SimNode | null>(null);
  const redrawRef = useRef<() => void>(() => {});

  // Fetch graph data
  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setLoadError(false);
    fetch('/graph-data.json')
      .then((res) => {
        if (!res.ok) throw new Error(`Graph data returned ${res.status}`);
        return res.json();
      })
      .then((data: KnowledgeGraphData) => {
        if (cancelled) return;
        setGraphData(data);
        setIsLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        console.error('Failed to load graph data:', err);
        setLoadError(true);
        setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  useEffect(() => {
    hoveredNodeRef.current = hoveredNode;
    redrawRef.current();
  }, [hoveredNode]);

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
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const maxIterations = reduceMotion ? 0 : window.innerWidth < 768 ? 80 : 160;

    function tick() {
      const simNodes = simNodesRef.current;
      const simEdges = simEdgesRef.current;
      const nodeMap = new Map<string, SimNode>();
      simNodes.forEach((n) => nodeMap.set(n.id, n));

      if (iteration >= maxIterations) {
        draw();
        animFrameIdRef.current = null;
        return;
      }

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
      const activeHoveredNode = hoveredNodeRef.current;

      ctx.clearRect(0, 0, canvas.width, canvas.height);

      const isDark = document.documentElement.getAttribute('data-theme') !== 'light';
            const domainNodeColors: Record<string, string> = isDark ? { 'core-java':'#dbaf63','spring-boot':'#78d2b5','system-design':'#a2c0f7','database-caching':'#7ed5cb','java-dsa':'#f0a6a6','cloud-devops':'#91bdf2','docker-java-backend':'#b19fdc',root:'#b29afa' } : { 'core-java':'#a54806','spring-boot':'#087f5b','system-design':'#2563eb','database-caching':'#087f5b','java-dsa':'#c92a2a','cloud-devops':'#2563eb','docker-java-backend':'#5b2ce0',root:'#5b2ce0' };

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
      if (activeHoveredNode) {
        connectedNodeIds.add(activeHoveredNode.id);
        simEdges.forEach((e) => {
          if (e.source === activeHoveredNode.id) connectedNodeIds.add(e.target);
          if (e.target === activeHoveredNode.id) connectedNodeIds.add(e.source);
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

        const isEdgeActive = activeHoveredNode && (s.id === activeHoveredNode.id || t.id === activeHoveredNode.id);
        if (isEdgeActive) continue; // draw active in next pass

        ctx.beginPath();
        ctx.moveTo(s.x, s.y);
        ctx.lineTo(t.x, t.y);
        ctx.strokeStyle = defaultEdgeColor;
        ctx.lineWidth = 0.8;
        ctx.globalAlpha = activeHoveredNode ? 0.15 : 0.8;
        ctx.stroke();
      }

      // Draw active glowing edges
      if (activeHoveredNode) {
        const hoverColor = domainNodeColors[activeHoveredNode.domain] || '#6d3df5';
        for (const edge of simEdges) {
          const s = nodeMap.get(edge.source);
          const t = nodeMap.get(edge.target);
          if (!s || !t) continue;

          const isEdgeActive = s.id === activeHoveredNode.id || t.id === activeHoveredNode.id;
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
        const domainColor = domainNodeColors[node.domain] || '#6d3df5';
        const isHovered = activeHoveredNode?.id === node.id;
        const isConnected = activeHoveredNode ? connectedNodeIds.has(node.id) : true;
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

    redrawRef.current = draw;
    if (animFrameIdRef.current) cancelAnimationFrame(animFrameIdRef.current);
    animFrameIdRef.current = requestAnimationFrame(tick);

    return () => {
      if (animFrameIdRef.current) cancelAnimationFrame(animFrameIdRef.current);
    };
  }, [filteredData]);

  // Resize canvas to parent container
  useEffect(() => {
    function handleResize() {
      if (canvasRef.current && containerRef.current) {
        canvasRef.current.width = containerRef.current.clientWidth;
        canvasRef.current.height = containerRef.current.clientHeight;
        redrawRef.current();
      }
    }
    handleResize();
    const handleThemeChange = () => redrawRef.current();
    window.addEventListener('resize', handleResize);
    window.addEventListener('themechange', handleThemeChange);
    return () => {
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('themechange', handleThemeChange);
    };
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
      redrawRef.current();
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
    redrawRef.current();
  };

  const handleClick = () => {
    if (hoveredNode) {
      window.location.href = hoveredNode.slug;
    }
  };

  const zoomIn = () => {
    transformRef.current.k = Math.min(3.5, transformRef.current.k * 1.3);
    redrawRef.current();
  };
  const zoomOut = () => {
    transformRef.current.k = Math.max(0.15, transformRef.current.k * 0.75);
    redrawRef.current();
  };
  const resetZoom = () => {
    const width = containerRef.current?.clientWidth || 900;
    const height = containerRef.current?.clientHeight || 600;
    transformRef.current = { x: width / 2, y: height / 2, k: 0.85 };
    redrawRef.current();
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
    redrawRef.current();
  }, []);

  return (
    <div className="relative flex h-full min-h-0 w-full flex-col overflow-hidden rounded-md border border-obs-border/90 bg-obs-canvas shadow-obs-lg select-none">
      {/* Top Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-obs-border/80 bg-obs-sidebar/85 p-4 backdrop-blur-md z-10 lg:h-[156px] 2xl:h-[72px]">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-obs-accent/15 border border-obs-accent/30 text-obs-accent shadow-xs">
            <Compass className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm font-bold text-obs-text-primary tracking-tight">
                Knowledge Graph Explorer
              </h1>
              <span className="hidden sm:inline-flex items-center px-2 py-0.5 rounded-sm text-micro font-mono font-medium bg-obs-surface border border-obs-border text-obs-text-muted">
                Obsidian Engine
              </span>
            </div>
            <p className="text-caption text-obs-text-muted">
              {filteredData.nodes.length} Topics · {filteredData.edges.length} Cross-Document Relations
            </p>
          </div>
        </div>

        {/* Domain Filter Pills */}
        <div className="flex w-full flex-wrap items-center gap-1.5 text-xs lg:w-auto">
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
                aria-pressed={isSel}
                className={`rounded-sm px-2.5 py-1 text-xs transition-all flex items-center gap-1.5 border ${
                  isSel
                    ? 'bg-obs-text-primary text-obs-canvas font-semibold border-obs-text-primary'
                    : 'border-obs-border/70 bg-obs-surface text-obs-text-secondary hover:bg-obs-hover hover:text-obs-text-primary'
                }`}
              >
                {color && (
                  <span
                    className="h-1.5 w-1.5 rounded-sm"
                    style={{ backgroundColor: color.text }}
                  />
                )}
                <span>{label}</span>
                <span className={`text-micro font-mono ${isSel ? 'text-obs-canvas' : 'text-obs-text-muted'}`}>
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Search & View Mode */}
        <div className="flex w-full items-center gap-2 lg:w-auto">
          <div className="relative flex items-center">
            <Search className="absolute left-2.5 h-3.5 w-3.5 text-obs-text-muted pointer-events-none" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Filter graph nodes..."
              aria-label="Filter graph nodes"
              className="field w-36 md:w-48 rounded-sm border border-obs-border/80 bg-obs-surface/80 py-1 pl-8 pr-7 text-xs text-obs-text-primary placeholder:text-obs-text-muted focus:border-obs-accent focus:bg-obs-surface focus:outline-none transition-all"
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
            aria-pressed={viewMode === 'list'}
            className="flex h-7 items-center gap-1.5 rounded-sm border border-obs-border/80 bg-obs-surface px-2.5 text-xs font-medium text-obs-text-secondary hover:bg-obs-hover transition-colors shadow-xs"
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
        className="relative min-h-[18rem] w-full flex-1 cursor-grab overflow-hidden bg-obs-canvas active:cursor-grabbing lg:min-h-0 lg:h-[calc(100%-156px)] lg:flex-none 2xl:h-[calc(100%-72px)]"
      >
        {isLoading && (
          <div className="absolute inset-0 z-20 bg-obs-canvas/90 p-6" aria-busy="true" aria-label="Loading knowledge graph">
            <div className="grid h-full grid-cols-6 gap-3 opacity-70">
              {Array.from({ length: 24 }).map((_, index) => (
                <div
                  key={index}
                  className="skeleton self-center rounded-sm"
                  style={{ height: `${12 + (index % 5) * 6}px`, width: `${12 + (index % 5) * 6}px` }}
                />
              ))}
            </div>
            <div className="absolute inset-x-0 bottom-6 text-center text-xs text-obs-text-muted">
              Constructing multi-domain graph matrix…
            </div>
          </div>
        )}

        {!isLoading && loadError && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-3 bg-obs-canvas p-6 text-center">
            <p className="text-sm font-semibold text-obs-text-primary">Graph data could not load</p>
            <p className="max-w-md text-xs leading-relaxed text-obs-text-muted">
              The graph payload is unavailable. Retry the request or use the accessible list view after it loads.
            </p>
            <button
              type="button"
              onClick={() => setReloadToken((value) => value + 1)}
              className="btn btn-primary px-4 py-2 text-xs"
            >
              Retry graph
            </button>
          </div>
        )}

        {!isLoading && !loadError && filteredData.nodes.length === 0 && (
          <div className="absolute inset-0 z-20 flex flex-col items-center justify-center gap-3 bg-obs-canvas p-6 text-center">
            <p className="text-sm font-semibold text-obs-text-primary">No graph nodes match those filters</p>
            <p className="max-w-md text-xs leading-relaxed text-obs-text-muted">
              Clear the search or select all domains to restore the full knowledge mesh.
            </p>
            <button
              type="button"
              onClick={() => {
                setSearchQuery('');
                setSelectedDomain('all');
              }}
              className="btn btn-secondary px-4 py-2 text-xs"
            >
              Reset graph filters
            </button>
          </div>
        )}

        {!isLoading && !loadError && filteredData.nodes.length > 0 && (viewMode === 'canvas' ? (
          <canvas
            ref={canvasRef}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onWheel={handleWheel}
            onClick={handleClick}
            aria-label="Interactive knowledge graph canvas. Use the List View button for keyboard navigation."
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
                  className="flex items-center justify-between p-3.5 rounded-md border border-obs-border/70 bg-obs-surface/60 hover:border-obs-accent/50 hover:bg-obs-surface transition-all group shadow-xs hover:shadow-sm"
                >
                  <div className="flex items-center gap-3">
                    <span
                      className="h-2.5 w-2.5 rounded-sm shrink-0"
                      style={{ backgroundColor: domainColor.text }}
                    />
                    <div>
                      <div className="font-semibold text-xs sm:text-sm text-obs-text-primary group-hover:text-obs-accent transition-colors">
                        {node.title}
                      </div>
                      <div className="text-caption text-obs-text-muted mt-0.5 flex items-center gap-2">
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
        ))}

        {/* Zoom & Reset Floating Controls */}
        {viewMode === 'canvas' && (
          <div className="absolute bottom-5 right-5 flex flex-col gap-1.5 bg-obs-surface/85 backdrop-blur-md p-1.5 rounded-md border border-obs-border/80 shadow-obs-lg z-10">
            <button
              type="button"
              onClick={zoomIn}
              className="p-1.5 rounded-sm hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Zoom In (+)"
              aria-label="Zoom In"
            >
              <ZoomIn className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={zoomOut}
              className="p-1.5 rounded-sm hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Zoom Out (-)"
              aria-label="Zoom Out"
            >
              <ZoomOut className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={fitView}
              className="p-1.5 rounded-sm hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Fit to Screen"
              aria-label="Fit to Screen"
            >
              <Maximize2 className="h-4 w-4" />
            </button>
            <button
              type="button"
              onClick={resetZoom}
              className="p-1.5 rounded-sm hover:bg-obs-hover text-obs-text-secondary hover:text-obs-text-primary transition-colors"
              title="Reset View"
              aria-label="Reset View"
            >
              <RotateCcw className="h-4 w-4" />
            </button>
          </div>
        )}

        {/* Canvas Navigation Hint Pill */}
        {viewMode === 'canvas' && (
          <div className="absolute bottom-5 left-5 hidden md:flex items-center gap-3 px-3 py-1.5 rounded-sm bg-obs-surface/80 backdrop-blur-md border border-obs-border/70 text-caption text-obs-text-muted select-none pointer-events-none">
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
            className="fixed z-50 pointer-events-none rounded-md border border-obs-border bg-obs-surface/95 p-3.5 text-xs shadow-2xl backdrop-blur-xl transition-all animate-fade-in"
            style={{
              left: `${Math.min(window.innerWidth - 280, tooltipPos.x + 16)}px`,
              top: `${Math.min(window.innerHeight - 150, tooltipPos.y + 16)}px`,
              maxWidth: '260px',
            }}
          >
            {/* Top domain badge */}
            <div className="flex items-center gap-1.5">
              <span
                className="h-2 w-2 rounded-sm"
                style={{
                  backgroundColor: DOMAIN_COLORS[hoveredNode.domain]?.text || '#6d3df5',
                }}
              />
              <span
                className="text-micro font-semibold uppercase tracking-wider"
                style={{
                  color: DOMAIN_COLORS[hoveredNode.domain]?.text || '#6d3df5',
                }}
              >
                {hoveredNode.domainTitle}
              </span>
            </div>

            <div className="font-bold text-obs-text-primary text-xs leading-snug mt-1">
              {hoveredNode.title}
            </div>

            <div className="mt-2.5 grid grid-cols-2 gap-2 border-t border-obs-border/60 pt-2 text-micro">
              <div className="bg-obs-canvas/80 p-1.5 rounded border border-obs-border/40">
                <span className="text-obs-text-muted block">Backlinks</span>
                <span className="font-mono font-bold text-obs-text-primary">{hoveredNode.inDegree} incoming</span>
              </div>
              <div className="bg-obs-canvas/80 p-1.5 rounded border border-obs-border/40">
                <span className="text-obs-text-muted block">References</span>
                <span className="font-mono font-bold text-obs-text-primary">{hoveredNode.outDegree} outgoing</span>
              </div>
            </div>

            <div className="mt-2 text-micro text-obs-accent font-mono flex items-center justify-between">
              <span>Click to view chapter</span>
              <ArrowUpRight className="h-3 w-3" />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
