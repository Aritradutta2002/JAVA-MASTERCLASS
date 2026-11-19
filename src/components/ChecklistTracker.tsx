import React, { useState, useEffect, useMemo, useRef } from 'react';
import { createFocusTrap } from '../lib/focusTrap';
import {
  RotateCcw,
  Sparkles,
  BookOpen,
  Check,
  CheckCircle2,
  Clock,
  Layers,
  Cpu,
  Compass,
  Database,
  Code2,
  Search,
  Filter,
  ArrowRight,
  TrendingUp,
  AlertCircle,
  X,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';
import { DOMAIN_COLORS } from '../lib/constants';

interface ChecklistItem {
  id: string;
  title: string;
  slug: string;
}

interface ChecklistGroup {
  name: string;
  items: ChecklistItem[];
}

interface ChecklistDomain {
  domain: string;
  title: string;
  groups: ChecklistGroup[];
}

const CHECKLIST_DATA: ChecklistDomain[] = [
  {
    domain: 'core-java',
    title: 'Core Java (18 Chapters)',
    groups: [
      {
        name: 'Fundamentals (Week 1)',
        items: [
          { id: 'cj-01', title: '01. Core Java OOP (13 deep chapters, 4 pillars, diamond problem, variance)', slug: '/core-java/01-core-java-oop' },
          { id: 'cj-02', title: '02. Java Memory Model (Stack, Heap, Metaspace, G1/ZGC, JIT)', slug: '/core-java/02-java-memory-model' },
          { id: 'cj-03', title: '03. Exception Handling (Hierarchy, try-with-resources, suppressed)', slug: '/core-java/03-exception-handling' },
          { id: 'cj-04', title: '04. Collections Framework (Lists, ConcurrentHashMap, Sets, Queues)', slug: '/core-java/04-collections-framework' },
          { id: 'cj-05', title: '05. Generics (Type erasure, PECS, bridge methods)', slug: '/core-java/05-generics' },
        ],
      },
      {
        name: 'Modern Java (Week 2)',
        items: [
          { id: 'cj-06', title: '06. Java 8 Features (Streams, Lambdas, Optional, CompletableFuture)', slug: '/core-java/06-java-8-features' },
          { id: 'cj-07', title: '07. Java 11/17/21 (Records, Sealed Classes, Pattern Matching, Virtual Threads)', slug: '/core-java/07-java-11-17-21-features' },
          { id: 'cj-08', title: '08. Multithreading & Concurrency (JMM, Locks, Atomics, False Sharing)', slug: '/core-java/08-multithreading-concurrency' },
        ],
      },
      {
        name: 'Design & Advanced Topics (Week 3-4)',
        items: [
          { id: 'cj-09', title: '09. GoF Design Patterns (All 23 patterns with production code)', slug: '/core-java/09-design-patterns' },
          { id: 'cj-10', title: '10. SOLID Principles (SRP, OCP, LSP, ISP, DIP with refactoring)', slug: '/core-java/10-solid-principles' },
          { id: 'cj-11', title: '11. DSA Quick Reference (Complexity ladder, canonical problems)', slug: '/core-java/11-dsa' },
          { id: 'cj-12', title: '12. String Internals (String pool, Compact Strings, intern)', slug: '/core-java/12-strings' },
          { id: 'cj-13', title: '13. Enums (EnumSet, EnumMap, constant bodies)', slug: '/core-java/13-enums' },
          { id: 'cj-14', title: '14. Reflection & Annotations (Dynamic Proxies, MethodHandles)', slug: '/core-java/14-reflection-annotations' },
          { id: 'cj-15', title: '15. IO & NIO (Channels, Buffers, Selectors, Memory-mapped files)', slug: '/core-java/15-io-nio' },
          { id: 'cj-16', title: '16. JDBC (Transactions, Isolation, Connection Pooling)', slug: '/core-java/16-jdbc' },
          { id: 'cj-17', title: '17. Serialization (serialVersionUID, custom serialization, attacks)', slug: '/core-java/17-serialization' },
          { id: 'cj-18', title: '18. Testing (JUnit 5, Mockito, TestNG, Flaky tests)', slug: '/core-java/18-testing' },
        ],
      },
    ],
  },
  {
    domain: 'spring-boot',
    title: 'Spring & Spring Boot (14 Modules)',
    groups: [
      {
        name: 'Fundamentals & Web (Weeks 1-3)',
        items: [
          { id: 'sb-01', title: '01. Spring Boot Fundamentals (Auto-config, starters, profiles, actuator)', slug: '/spring-boot/01-spring-boot-fundamentals' },
          { id: 'sb-02', title: '02. Spring Core Framework (IoC, DI, bean lifecycle, scopes, AOP)', slug: '/spring-boot/02-spring-core-framework' },
          { id: 'sb-03', title: '03. Spring MVC (DispatcherServlet, REST, validation, filters)', slug: '/spring-boot/03-spring-mvc' },
          { id: 'sb-04', title: '04. Spring Data JPA & Hibernate (ORM, N+1, Transactions, Caches)', slug: '/spring-boot/04-spring-data-jpa-hibernate' },
        ],
      },
      {
        name: 'Security & Distributed Architecture (Weeks 4-6)',
        items: [
          { id: 'sb-05', title: '05. Spring Security (Filter chains, Auth flows, CSRF, CORS)', slug: '/spring-boot/05-spring-security' },
          { id: 'sb-06', title: '06. JWT Authentication (Tokens, rotation, revocation, production)', slug: '/spring-boot/06-jwt-authentication' },
          { id: 'sb-07', title: '07. OAuth2 & OIDC (PKCE, Keycloak, Enterprise SSO)', slug: '/spring-boot/07-oauth2-openid-connect' },
          { id: 'sb-08', title: '08. Microservices with Spring Boot (Discovery, Gateway, Resilience4j)', slug: '/spring-boot/08-microservices-with-spring-boot' },
          { id: 'sb-09', title: '09. Messaging Systems (Kafka, RabbitMQ, DLQ, Idempotency)', slug: '/spring-boot/09-messaging-systems' },
          { id: 'sb-10', title: '10. Caching (Redis, Cache-Aside, Eviction strategies)', slug: '/spring-boot/10-caching' },
          { id: 'sb-11', title: '11. Database Design & Optimization (HikariCP, Locks, Deadlocks)', slug: '/spring-boot/11-database-design-optimization' },
          { id: 'sb-12', title: '12. System Design for Java Backend (URL shortener, Chat, E-commerce)', slug: '/spring-boot/12-system-design-for-java-backend' },
          { id: 'sb-13', title: '13. Testing (JUnit 5, Mockito, Testcontainers)', slug: '/spring-boot/13-testing' },
          { id: 'sb-14', title: '14. Deployment & DevOps (Docker, Kubernetes, CI/CD, Observability)', slug: '/spring-boot/14-deployment-devops' },
        ],
      },
    ],
  },
  {
    domain: 'system-design',
    title: 'System Design & High-Level Architecture (13 Modules · 123 Docs)',
    groups: [
      {
        name: 'Distributed Systems & Architecture Core',
        items: [
          { id: 'sd-01', title: '01. System Design Fundamentals (Latency, Throughput, Trade-offs)', slug: '/system-design/01-system-design-fundamentals' },
          { id: 'sd-02', title: '02. Distributed Systems (CAP theorem, PACELC, Consensus, 2PC/3PC)', slug: '/system-design/02-distributed-systems' },
          { id: 'sd-03', title: '03. Database Design (Sharding, Replication, Consistency)', slug: '/system-design/03-database-design' },
          { id: 'sd-04', title: '04. Caching (Write-through, Write-back, Cache Stampede)', slug: '/system-design/04-caching' },
          { id: 'sd-05', title: '05. Messaging & Event-Driven (Kafka, Outbox pattern, Saga)', slug: '/system-design/05-messaging-and-event-driven-systems' },
          { id: 'sd-06', title: '06. API & Communication (REST vs gRPC vs GraphQL, Rate Limiting)', slug: '/system-design/06-api-and-communication-design' },
          { id: 'sd-07', title: '07. Security Design (Threat modeling, OAuth2, DDoS, Secrets)', slug: '/system-design/07-security-design' },
          { id: 'sd-08', title: '08. Microservices Architecture (DDD, Strangler Fig, Service Mesh)', slug: '/system-design/08-microservices-architecture' },
          { id: 'sd-09', title: '09. Cloud & Infrastructure (Kubernetes, Multi-Region, CDN)', slug: '/system-design/09-cloud-and-infrastructure-design' },
          { id: 'sd-10', title: '10. Observability & Reliability (SLI/SLO/SLA, Chaos engineering)', slug: '/system-design/10-observability-and-reliability-engineering' },
        ],
      },
      {
        name: 'HLD Case Studies & LLD Preparation',
        items: [
          { id: 'sd-11', title: '11. 20 HLD Production Case Studies (Netflix, Uber, WhatsApp, YouTube, etc.)', slug: '/system-design/11-hld-case-studies' },
          { id: 'sd-12', title: '12. LLD Preparation (SOLID, UML + 8 Runnable Java implementations)', slug: '/system-design/12-lld-preparation' },
          { id: 'sd-13', title: '13. Interview Practice (45-min framework, capacity & failure drills)', slug: '/system-design/13-interview-practice' },
        ],
      },
    ],
  },
  {
    domain: 'database-caching',
    title: 'Database & Caching Masterclass (21 Chapters)',
    groups: [
      {
        name: 'SQL & NoSQL Deep Dive',
        items: [
          { id: 'db-01', title: '01. SQL Databases (11 chapters: Engines, B+ Trees, CTEs, MVCC, Scaling)', slug: '/database-caching/01-sql-databases' },
          { id: 'db-02', title: '02. NoSQL Databases (MongoDB, Cassandra, DynamoDB data modeling)', slug: '/database-caching/02-nosql-databases' },
        ],
      },
    ],
  },
  {
    domain: 'java-dsa',
    title: 'Data Structures & Algorithms',
    groups: [
      {
        name: 'DSA Foundations & In-Depth Resources',
        items: [
          { id: 'dsa-01', title: 'Graphs, Traversal & DSU (BFS, DFS, Dijkstra, MST, union-find)', slug: '/java-dsa/dsa/datastructures/graphs' },
          { id: 'dsa-02', title: 'Dynamic Programming (Memoization, Tabulation, Knapsack, LCS, LIS)', slug: '/java-dsa/dsa/dynamicprogramming' },
          { id: 'dsa-03', title: 'Sliding Window & Two Pointers (Fixed and dynamic windows)', slug: '/java-dsa/dsa/techniques/slidingwindow' },
          { id: 'dsa-04', title: 'Backtracking (Permutations, combinations, constraint search)', slug: '/java-dsa/dsa/algorithms/backtracking' },
          { id: 'dsa-05', title: 'Bit Manipulation (Masks, subsets, XOR tricks)', slug: '/java-dsa/dsa/techniques/bitmanipulation' },
          { id: 'dsa-06', title: 'Prefix Sums & Hashing Patterns', slug: '/java-dsa/dsa/techniques/prefixsum' },
        ],
      },
    ],
  },
];

const DOMAIN_ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  'core-java': Cpu,
  'spring-boot': Layers,
  'system-design': Compass,
  'database-caching': Database,
  'java-dsa': Code2,
};

export default function ChecklistTracker() {
  const [checkedState, setCheckedState] = useState<Record<string, boolean>>({});
  const [selectedDomain, setSelectedDomain] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<'all' | 'incomplete' | 'completed'>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [showResetConfirm, setShowResetConfirm] = useState<boolean>(false);
  const [collapsedDomains, setCollapsedDomains] = useState<Record<string, boolean>>({});
  const [lastToggledId, setLastToggledId] = useState<string | null>(null);
  const resetDialogRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    try {
      const saved = localStorage.getItem('obsidian-checklist-progress');
      if (saved) setCheckedState(JSON.parse(saved));
    } catch {}
  }, []);

  useEffect(() => {
    if (!showResetConfirm || !resetDialogRef.current) return;
    const trap = createFocusTrap(resetDialogRef.current, {
      onEscape: () => setShowResetConfirm(false),
    });
    trap.activate();
    return () => trap.deactivate();
  }, [showResetConfirm]);

  const toggleItem = (id: string) => {
    setCheckedState((prev) => {
      const next = { ...prev, [id]: !prev[id] };
      try {
        localStorage.setItem('obsidian-checklist-progress', JSON.stringify(next));
      } catch {}
      return next;
    });
    setLastToggledId(id);
    setTimeout(() => {
      setLastToggledId(null);
    }, 1200);
  };

  const markDomainAll = (domainObj: ChecklistDomain, checked: boolean) => {
    setCheckedState((prev) => {
      const next = { ...prev };
      domainObj.groups.forEach((g) => {
        g.items.forEach((item) => {
          next[item.id] = checked;
        });
      });
      try {
        localStorage.setItem('obsidian-checklist-progress', JSON.stringify(next));
      } catch {}
      return next;
    });
  };

  const handleResetConfirm = () => {
    setCheckedState({});
    try {
      localStorage.removeItem('obsidian-checklist-progress');
    } catch {}
    setShowResetConfirm(false);
    window.dispatchEvent(
      new CustomEvent('obsidian:status', { detail: { message: 'Study progress reset' } })
    );
  };

  const toggleDomainCollapse = (domain: string) => {
    setCollapsedDomains((prev) => ({ ...prev, [domain]: !prev[domain] }));
  };

  // Compute total stats
  const { totalItems, totalChecked, domainStats } = useMemo(() => {
    let total = 0;
    let checked = 0;
    const domStats: Record<string, { total: number; checked: number; percent: number }> = {};

    CHECKLIST_DATA.forEach((dom) => {
      let dTotal = 0;
      let dChecked = 0;
      dom.groups.forEach((g) => {
        g.items.forEach((item) => {
          total++;
          dTotal++;
          if (checkedState[item.id]) {
            checked++;
            dChecked++;
          }
        });
      });
      domStats[dom.domain] = {
        total: dTotal,
        checked: dChecked,
        percent: dTotal > 0 ? Math.round((dChecked / dTotal) * 100) : 0,
      };
    });

    return { totalItems: total, totalChecked: checked, domainStats: domStats };
  }, [checkedState]);

  const overallPercent = totalItems > 0 ? Math.round((totalChecked / totalItems) * 100) : 0;
  const remainingItems = totalItems - totalChecked;

  useEffect(() => {
    if (overallPercent === 100) {
      window.dispatchEvent(
        new CustomEvent('obsidian:status', {
          detail: { message: 'Checklist complete. Excellent work.' },
        })
      );
    }
  }, [overallPercent]);
  const estRemainingHours = Math.ceil((remainingItems * 35) / 60);

  // Readiness tier badge
  const readinessTier = useMemo(() => {
    if (overallPercent === 100) return { label: 'Complete Mastery', color: 'text-obs-success-text bg-obs-success-subtle border-obs-success/30' };
    if (overallPercent >= 75) return { label: 'Staff / Principal Ready', color: 'text-obs-accent-hover bg-obs-accent-subtle border-obs-accent/30' };
    if (overallPercent >= 50) return { label: 'Senior Candidate', color: 'text-obs-info-text bg-obs-info-subtle border-obs-info/30' };
    if (overallPercent >= 25) return { label: 'Mid-Level Foundation', color: 'text-obs-warning-text bg-obs-warning-subtle border-obs-warning/30' };
    return { label: 'Early Revision Phase', color: 'text-obs-accent-hover bg-obs-accent-subtle border-obs-accent/30' };
  }, [overallPercent]);

  // Filtered dataset
  const filteredData = useMemo(() => {
    return CHECKLIST_DATA.filter((dom) => {
      if (selectedDomain !== 'all' && dom.domain !== selectedDomain) return false;
      return true;
    }).map((dom) => {
      const filteredGroups = dom.groups.map((group) => {
        const filteredItems = group.items.filter((item) => {
          const isDone = !!checkedState[item.id];
          if (statusFilter === 'incomplete' && isDone) return false;
          if (statusFilter === 'completed' && !isDone) return false;
          if (searchQuery.trim()) {
            const q = searchQuery.toLowerCase();
            return item.title.toLowerCase().includes(q) || item.slug.toLowerCase().includes(q);
          }
          return true;
        });
        return { ...group, items: filteredItems };
      }).filter((group) => group.items.length > 0);

      return { ...dom, groups: filteredGroups };
    }).filter((dom) => dom.groups.length > 0);
  }, [selectedDomain, statusFilter, searchQuery, checkedState]);

  // SVG progress ring calculation
  const ringRadius = 40;
  const circumference = 2 * Math.PI * ringRadius;
  const strokeDashoffset = circumference - (overallPercent / 100) * circumference;

  return (
    <div className="space-y-6 mx-auto w-full py-2">
      {/* Stripe-Grade Overview Hero Dashboard */}
      <div className="relative overflow-hidden border-y border-obs-border py-6">
        {/* Ambient subtle glow background */}
        <div
          className="absolute right-0 -top-20 w-80 h-80 rounded-sm opacity-20 pointer-events-none blur-3xl"
          style={{
            background: 'radial-gradient(circle, var(--obs-accent) 0%, transparent 70%)',
          }}
        />

        <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-6 relative z-10">
          <div className="space-y-2 max-w-2xl">
            <div className="flex flex-wrap items-center gap-2">
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-sm text-xs font-semibold uppercase tracking-wider bg-obs-accent/15 border border-obs-accent/30 text-obs-accent">
                <Sparkles className="h-3.5 w-3.5" />
                <span>Assessment tracker</span>
              </span>
              <span className={`inline-flex items-center px-2.5 py-1 rounded-sm text-xs font-medium border ${readinessTier.color}`}>
                {readinessTier.label}
              </span>
            </div>

            <h2 className="text-2xl sm:text-3xl font-bold tracking-tight text-obs-text-primary">
              Review progress
            </h2>

            <p className="text-sm text-obs-text-muted leading-relaxed">
              Systematic revision checklist spanning 5 core technical pillars. Track your progress across
              distributed concurrency, high-performance database design, modern Spring architectures, and LLD.
            </p>
          </div>

          {/* Linear Progress Ring & Quick Stats */}
          <div className="flex items-center gap-6 bg-obs-canvas/80 border border-obs-border/80 p-4 rounded-md shadow-xs self-stretch lg:self-auto justify-around sm:justify-start">
            <div className="relative flex items-center justify-center shrink-0">
              <svg
                className="h-24 w-24 transform -rotate-90"
                role="img"
                aria-label={`Overall readiness ${overallPercent} percent`}
              >
                <circle
                  cx="48"
                  cy="48"
                  r={ringRadius}
                  stroke="currentColor"
                  strokeWidth="7"
                  className="text-obs-surface-hover"
                  fill="transparent"
                />
                <circle
                  cx="48"
                  cy="48"
                  r={ringRadius}
                  stroke="currentColor"
                  strokeWidth="7"
                  className="text-obs-accent transition-all duration-700 ease-out"
                  fill="transparent"
                  strokeDasharray={circumference}
                  strokeDashoffset={strokeDashoffset}
                  strokeLinecap="round"
                />
              </svg>
              <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
                <span className="text-xl font-bold font-mono text-obs-text-primary">
                  {overallPercent}%
                </span>
                <span className="text-micro uppercase tracking-wider text-obs-text-muted">
                  Readiness
                </span>
              </div>
            </div>

            <div className="space-y-1.5 text-xs">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-3.5 w-3.5 text-obs-success-text" />
                <span className="text-obs-text-secondary">Completed:</span>
                <span className="font-mono font-bold text-obs-text-primary">{totalChecked}/{totalItems}</span>
              </div>
              <div className="flex items-center gap-2">
                <Clock className="h-3.5 w-3.5 text-obs-warning-text" />
                <span className="text-obs-text-secondary">Est. Remaining:</span>
                <span className="font-mono font-semibold text-obs-text-primary">~{estRemainingHours} hrs</span>
              </div>
              <div className="flex items-center gap-2">
                <TrendingUp className="h-3.5 w-3.5 text-obs-info-text" />
                <span className="text-obs-text-secondary">Velocity:</span>
                <span className="font-mono font-medium text-obs-text-muted">
                  {totalChecked > 0 ? `${totalChecked} topics done` : 'Start below'}
                </span>
              </div>
            </div>
          </div>
        </div>

        {/* Global Multi-Segment Progress Bar */}
        <div className="mt-6 pt-5 border-t border-obs-border/70 space-y-2">
          <div className="flex items-center justify-between text-xs">
            <span className="font-medium text-obs-text-secondary">Overall Mastery Progress</span>
            <span className="font-mono font-semibold text-obs-text-primary">
              {totalChecked} of {totalItems} completed ({overallPercent}%)
            </span>
          </div>

          <div className="h-2.5 w-full overflow-hidden rounded-sm bg-obs-canvas border border-obs-border/70 flex">
            {CHECKLIST_DATA.map((dom) => {
              const dStat = domainStats[dom.domain] || { total: 0, checked: 0 };
              const widthPct = totalItems > 0 ? (dStat.checked / totalItems) * 100 : 0;
              const color = DOMAIN_COLORS[dom.domain]?.hex || '#6d3df5';
              if (widthPct === 0) return null;
              return (
                <div
                  key={dom.domain}
                  style={{ width: `${widthPct}%`, backgroundColor: color }}
                  className="h-full transition-all duration-500 ease-out"
                  title={`${dom.title}: ${dStat.checked} completed`}
                />
              );
            })}
          </div>
        </div>
      </div>

      {/* Control Filter Bar */}
      <div className="flex flex-col lg:flex-row items-stretch lg:items-center justify-between gap-3 bg-obs-sidebar/70 backdrop-blur-sm p-3 rounded-md border border-obs-border/80">
        {/* Domain Filter Pills */}
        <div className="flex min-w-0 max-w-full items-center gap-1 overflow-x-auto overscroll-x-contain pb-1 lg:pb-0 scrollbar-none">
          <button
            type="button"
            onClick={() => setSelectedDomain('all')}
            aria-pressed={selectedDomain === 'all'}
            className={`px-3 py-1.5 rounded-sm text-xs font-medium transition-all shrink-0 ${
              selectedDomain === 'all'
                ? 'bg-obs-accent-solid text-white shadow-xs font-semibold'
                : 'text-obs-text-secondary hover:bg-obs-surface hover:text-obs-text-primary'
            }`}
          >
            All Domains ({totalItems})
          </button>
          {CHECKLIST_DATA.map((dom) => {
            const isSel = selectedDomain === dom.domain;
            const stat = domainStats[dom.domain];
            const color = DOMAIN_COLORS[dom.domain] || DOMAIN_COLORS.root;
            return (
              <button
                key={dom.domain}
                type="button"
                onClick={() => setSelectedDomain(dom.domain)}
                aria-pressed={isSel}
                className={`px-2.5 py-1.5 rounded-sm text-xs font-medium transition-all shrink-0 flex items-center gap-1.5 border ${
                  isSel
                    ? 'bg-obs-surface text-obs-text-primary border-obs-accent shadow-xs font-semibold'
                    : 'border-transparent text-obs-text-secondary hover:bg-obs-surface/60 hover:text-obs-text-primary'
                }`}
              >
                <span className="h-2 w-2 rounded-sm" style={{ backgroundColor: color.hex }} />
                <span>{dom.title.split(' ')[0]}</span>
                <span className="text-micro font-mono text-obs-text-muted">
                  {stat?.checked}/{stat?.total}
                </span>
              </button>
            );
          })}
        </div>

        {/* Status Filter & Search */}
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <div className="relative flex-1 sm:w-48">
            <Search className="absolute left-2.5 top-2 h-3.5 w-3.5 text-obs-text-muted pointer-events-none" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search chapters..."
              aria-label="Search checklist topics"
              className="field w-full rounded-sm border border-obs-border/80 bg-obs-surface/80 py-1.5 pl-8 pr-2.5 text-xs text-obs-text-primary placeholder:text-obs-text-muted focus:border-obs-accent focus:bg-obs-surface focus:outline-none transition-all"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                className="absolute right-2 top-2 text-obs-text-muted hover:text-obs-text-primary"
                aria-label="Clear search query"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>

          <div className="flex items-center border border-obs-border/80 rounded-sm p-0.5 bg-obs-canvas text-xs">
            <button
              type="button"
              onClick={() => setStatusFilter('all')}
              className={`px-2 py-1 rounded-md text-caption font-medium transition-all ${
                statusFilter === 'all'
                  ? 'bg-obs-surface text-obs-text-primary shadow-xs font-semibold'
                  : 'text-obs-text-muted hover:text-obs-text-secondary'
              }`}
            >
              All
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter('incomplete')}
              className={`px-2 py-1 rounded-md text-caption font-medium transition-all ${
                statusFilter === 'incomplete'
                  ? 'bg-obs-surface text-obs-text-primary shadow-xs font-semibold'
                  : 'text-obs-text-muted hover:text-obs-text-secondary'
              }`}
            >
              Remaining
            </button>
            <button
              type="button"
              onClick={() => setStatusFilter('completed')}
              className={`px-2 py-1 rounded-md text-caption font-medium transition-all ${
                statusFilter === 'completed'
                  ? 'bg-obs-surface text-obs-text-primary shadow-xs font-semibold'
                  : 'text-obs-text-muted hover:text-obs-text-secondary'
              }`}
            >
              Done
            </button>
          </div>

          <button
            type="button"
            onClick={() => setShowResetConfirm(true)}
            className="p-1.5 rounded-sm border border-obs-border/80 bg-obs-surface text-obs-text-muted hover:text-obs-error-text hover:border-obs-error/40 transition-colors"
            title="Reset All Progress"
            aria-label="Reset All Progress"
          >
            <RotateCcw className="h-4 w-4" />
          </button>
        </div>
      </div>

      {/* Reset Confirmation Modal */}
      {showResetConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
          <div
            ref={resetDialogRef}
            className="w-full max-w-sm rounded-md border border-obs-border bg-obs-surface p-6 shadow-2xl space-y-4"
            role="dialog"
            aria-modal="true"
            aria-labelledby="reset-progress-title"
          >
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-obs-error-subtle text-obs-error-text border border-obs-error/20">
                <AlertCircle className="h-5 w-5" />
              </div>
              <div>
                <h3 id="reset-progress-title" className="font-bold text-sm text-obs-text-primary">Reset All Study Progress?</h3>
                <p className="text-xs text-obs-text-muted mt-1 leading-relaxed">
                  This will uncheck all {totalChecked} marked topics and clear your local saved state. This action cannot be undone.
                </p>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2.5 pt-2">
              <button
                type="button"
                onClick={() => setShowResetConfirm(false)}
                className="px-3 py-1.5 rounded-sm border border-obs-border text-xs font-medium text-obs-text-secondary hover:bg-obs-hover transition-colors"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleResetConfirm}
                className="px-3.5 py-1.5 rounded-sm bg-obs-error-solid hover:bg-obs-error-solid-hover text-white text-xs font-semibold transition-colors shadow-sm"
              >
                Yes, Reset All
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Domain Sections */}
      <div className="space-y-6">
        {filteredData.length === 0 ? (
          <div className="rounded-md border border-dashed border-obs-border p-12 text-center space-y-3">
            <Filter className="mx-auto h-8 w-8 text-obs-text-muted" />
            <h3 className="text-sm font-semibold text-obs-text-primary">No Matching Topics Found</h3>
            <p className="text-xs text-obs-text-muted max-w-sm mx-auto">
              No checklist items matched your search query or filter selection. Try clearing your filters.
            </p>
            <button
              type="button"
              onClick={() => {
                setSelectedDomain('all');
                setStatusFilter('all');
                setSearchQuery('');
              }}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-sm border border-obs-border bg-obs-surface text-xs font-medium text-obs-accent hover:bg-obs-hover"
            >
              Reset Filters
            </button>
          </div>
        ) : (
          filteredData.map((dom) => {
            const stat = domainStats[dom.domain] || { total: 0, checked: 0, percent: 0 };
            const isCollapsed = !!collapsedDomains[dom.domain];
            const IconComponent = DOMAIN_ICON_MAP[dom.domain] || BookOpen;
            const domainColor = DOMAIN_COLORS[dom.domain] || DOMAIN_COLORS.root;

            return (
              <div
                key={dom.domain}
                className="rounded-md border border-obs-border/80 bg-obs-surface/40 overflow-hidden shadow-sm transition-all duration-200 hover:border-obs-border"
              >
                {/* Domain Header */}
                <div className="p-4 sm:p-5 border-b border-obs-border/80 bg-obs-sidebar/70 flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <button
                      type="button"
                      onClick={() => toggleDomainCollapse(dom.domain)}
                      className="flex items-center gap-2.5 text-left group"
                    >
                      <div
                        className="flex h-9 w-9 items-center justify-center rounded-md border transition-transform duration-200 group-hover:scale-105"
                        style={{
                          backgroundColor: domainColor.bg,
                          color: domainColor.text,
                          borderColor: domainColor.border,
                        }}
                      >
                        <IconComponent className="h-5 w-5" />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h2 className="font-bold text-sm text-obs-text-primary group-hover:text-obs-accent transition-colors">
                            {dom.title}
                          </h2>
                          {isCollapsed ? (
                            <ChevronDown className="h-4 w-4 text-obs-text-muted" />
                          ) : (
                            <ChevronUp className="h-4 w-4 text-obs-text-muted" />
                          )}
                        </div>
                        <p className="text-caption text-obs-text-muted">
                          {stat.checked} of {stat.total} topics mastered
                        </p>
                      </div>
                    </button>
                  </div>

                  {/* Domain Quick Actions & Progress Pill */}
                  <div className="flex items-center gap-3">
                    <div className="flex items-center gap-2 bg-obs-canvas/80 px-2.5 py-1 rounded-sm border border-obs-border/70">
                      <span className="font-mono text-xs font-semibold text-obs-text-primary">
                        {stat.percent}%
                      </span>
                      <div className="w-20 h-2 rounded-sm bg-obs-surface overflow-hidden border border-obs-border/40">
                        <div
                          className="h-full transition-all duration-500 ease-out"
                          style={{
                            width: `${stat.percent}%`,
                            backgroundColor: domainColor.hex,
                          }}
                        />
                      </div>
                    </div>

                    <div className="flex items-center gap-1">
                      <button
                        type="button"
                        onClick={() => markDomainAll(dom, true)}
                        className="px-2 py-1 text-micro font-medium rounded border border-obs-border/60 bg-obs-surface hover:bg-obs-hover text-obs-text-secondary transition-colors"
                        title="Mark all items in this domain as completed"
                      >
                        All
                      </button>
                      <button
                        type="button"
                        onClick={() => markDomainAll(dom, false)}
                        className="px-2 py-1 text-micro font-medium rounded border border-obs-border/60 bg-obs-surface hover:bg-obs-hover text-obs-text-muted hover:text-obs-error-text transition-colors"
                        title="Clear all completed items in this domain"
                      >
                        Clear
                      </button>
                    </div>
                  </div>
                </div>

                {/* Groups & Items */}
                {!isCollapsed && (
                  <div className="p-4 sm:p-5 space-y-6">
                    {dom.groups.map((group) => (
                      <div key={group.name} className="space-y-2.5">
                        <div className="flex items-center justify-between text-caption font-semibold text-obs-text-muted uppercase tracking-wider pl-1">
                          <span>{group.name}</span>
                          <span className="text-micro font-mono text-obs-text-muted">
                            {group.items.filter((it) => checkedState[it.id]).length}/{group.items.length} done
                          </span>
                        </div>

                        <div className="grid gap-2">
                          {group.items.map((item) => {
                            const isDone = !!checkedState[item.id];
                            const isJustToggled = lastToggledId === item.id;

                            return (
                              <div
                                key={item.id}
                                className={`group relative flex items-center justify-between p-3 rounded-md border transition-all duration-200 select-none ${
                                  isDone
                                    ? 'bg-obs-accent/5 border-obs-accent/30 text-obs-text-primary'
                                    : 'bg-obs-surface/60 border-obs-border/70 hover:border-obs-border hover:bg-obs-surface text-obs-text-secondary'
                                } ${isJustToggled ? 'scale-[1.008] ring-1 ring-obs-accent' : ''}`}
                              >
                                {/* Linear-style Left Accent Bar for checked items */}
                                {isDone && (
                                  <div className="absolute left-0 top-2 bottom-2 w-1 rounded-r bg-obs-accent" />
                                )}

                                <div className="flex items-center gap-3.5 flex-1 min-w-0 pr-3">
                                  {/* Custom Checkbox Button with micro-feedback */}
                                  <button
                                    type="button"
                                    onClick={() => toggleItem(item.id)}
                                    aria-label={`Mark ${item.title} as ${isDone ? 'incomplete' : 'completed'}`}
                                    className={`relative flex h-5 w-5 shrink-0 items-center justify-center rounded-md border transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-obs-accent/50 ${
                                      isDone
                                        ? 'bg-obs-text-primary border-obs-text-primary text-obs-canvas'
                                        : 'border-obs-border/90 bg-obs-canvas hover:border-obs-accent hover:bg-obs-surface'
                                    }`}
                                  >
                                    {isDone && <Check className="h-3.5 w-3.5 stroke-[3] animate-fade-in" />}
                                  </button>

                                  <div
                                    onClick={() => toggleItem(item.id)}
                                    className="cursor-pointer flex-1 min-w-0"
                                  >
                                    <div className="flex items-center gap-2">
                                      <span
                                        className={`text-xs font-medium leading-relaxed transition-all ${
                                          isDone
                                            ? 'line-through text-obs-text-muted opacity-80'
                                            : 'text-obs-text-primary font-medium'
                                        }`}
                                      >
                                        {item.title}
                                      </span>
                                    </div>
                                  </div>
                                </div>

                                <div className="flex items-center gap-3 shrink-0">
                                  {/* Status badge */}
                                  <span
                                    className={`hidden sm:inline-flex items-center px-2 py-0.5 rounded text-micro font-mono font-medium transition-colors ${
                                      isDone
                                        ? 'bg-obs-success-subtle text-obs-success-text border border-obs-success/20'
                                        : 'bg-obs-canvas text-obs-text-muted border border-obs-border/50'
                                    }`}
                                  >
                                    {isDone ? 'Mastered' : 'To Revise'}
                                  </span>

                                  {/* Read link */}
                                  <a
                                    href={item.slug}
                                    className="inline-flex items-center gap-1 text-caption font-semibold text-obs-accent hover:underline group/link font-mono"
                                  >
                                    <span>Read</span>
                                    <ArrowRight className="h-3 w-3 transition-transform group-hover/link:translate-x-0.5" />
                                  </a>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}
