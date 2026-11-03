import { useState, useEffect } from 'react';
import { RotateCcw, Sparkles, BookOpen } from 'lucide-react';

const CHECKLIST_DATA: { domain: string; title: string; groups: { name: string; items: { id: string; title: string; slug: string }[] }[] }[] = [
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

export default function ChecklistTracker() {
  const [checkedState, setCheckedState] = useState<Record<string, boolean>>({});

  useEffect(() => {
    try {
      const saved = localStorage.getItem('obsidian-checklist-progress');
      if (saved) setCheckedState(JSON.parse(saved));
    } catch {}
  }, []);

  const toggleItem = (id: string) => {
    setCheckedState((prev) => {
      const next = { ...prev, [id]: !prev[id] };
      try {
        localStorage.setItem('obsidian-checklist-progress', JSON.stringify(next));
      } catch {}
      return next;
    });
  };

  const resetAll = () => {
    if (confirm('Are you sure you want to reset all preparation progress?')) {
      setCheckedState({});
      localStorage.removeItem('obsidian-checklist-progress');
    }
  };

  // Compute total progress
  let totalItems = 0;
  let totalChecked = 0;

  CHECKLIST_DATA.forEach((dom) => {
    dom.groups.forEach((g) => {
      g.items.forEach((item) => {
        totalItems++;
        if (checkedState[item.id]) totalChecked++;
      });
    });
  });

  const overallPercent = totalItems > 0 ? Math.round((totalChecked / totalItems) * 100) : 0;

  return (
    <div className="space-y-8 max-w-4xl mx-auto py-4">
      {/* Overview Banner */}
      <div className="rounded-xl border border-obs-border bg-obs-sidebar p-6 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 text-obs-accent font-semibold text-xs uppercase tracking-wider">
              <Sparkles className="h-4 w-4" />
              <span>Full-Stack Preparation Roadmap</span>
            </div>
            <h1 className="text-2xl font-bold text-obs-text-primary mt-1">
              Java Backend Interview Master Checklist
            </h1>
            <p className="text-sm text-obs-text-muted mt-1 max-w-2xl">
              Track your mastery across the core technical domains. Check off topics as you revise,
              and watch your readiness meter advance towards 100%.
            </p>
          </div>

          <button
            onClick={resetAll}
            className="flex items-center gap-1.5 rounded-md border border-obs-border bg-obs-surface px-3 py-1.5 text-xs text-obs-text-muted hover:text-rose-400 hover:border-rose-500/50 transition-colors"
          >
            <RotateCcw className="h-3.5 w-3.5" />
            <span>Reset Progress</span>
          </button>
        </div>

        {/* Global Progress Bar */}
        <div className="mt-6 space-y-2">
          <div className="flex justify-between text-xs font-medium text-obs-text-primary">
            <span>Overall Readiness</span>
            <span>{totalChecked} of {totalItems} Topics Completed ({overallPercent}%)</span>
          </div>
          <div className="h-2.5 w-full overflow-hidden rounded-full bg-obs-surface border border-obs-border">
            <div
              className="h-full bg-gradient-to-r from-obs-accent to-emerald-400 transition-all duration-500 ease-out"
              style={{ width: `${overallPercent}%` }}
            />
          </div>
        </div>
      </div>

      {/* Domain Sections */}
      <div className="space-y-6">
        {CHECKLIST_DATA.map((dom) => {
          let domTotal = 0;
          let domChecked = 0;
          dom.groups.forEach((g) => {
            g.items.forEach((it) => {
              domTotal++;
              if (checkedState[it.id]) domChecked++;
            });
          });
          const domPercent = domTotal > 0 ? Math.round((domChecked / domTotal) * 100) : 0;

          return (
            <div key={dom.domain} className="rounded-xl border border-obs-border bg-obs-surface overflow-hidden shadow-sm">
              {/* Domain Header */}
              <div className="p-4 border-b border-obs-border bg-obs-sidebar/50 flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <BookOpen className="h-4 w-4 text-obs-accent" />
                  <h2 className="font-bold text-sm text-obs-text-primary">{dom.title}</h2>
                </div>
                <div className="flex items-center gap-3">
                  <span className="text-xs text-obs-text-muted">
                    {domChecked}/{domTotal} ({domPercent}%)
                  </span>
                  <div className="w-24 h-2 rounded-full bg-obs-canvas border border-obs-border overflow-hidden">
                    <div
                      className="h-full bg-obs-accent transition-all duration-300"
                      style={{ width: `${domPercent}%` }}
                    />
                  </div>
                </div>
              </div>

              {/* Groups & Items */}
              <div className="p-4 space-y-4">
                {dom.groups.map((group) => (
                  <div key={group.name} className="space-y-2">
                    <div className="text-xs font-semibold text-obs-text-muted uppercase tracking-wider pl-1">
                      {group.name}
                    </div>
                    <div className="grid gap-1.5 sm:grid-cols-1">
                      {group.items.map((item) => {
                        const isDone = !!checkedState[item.id];
                        return (
                          <div
                            key={item.id}
                            className={`flex items-center justify-between p-2 rounded-lg border transition-colors ${
                              isDone
                                ? 'bg-obs-accent/10 border-obs-accent/30 text-obs-text-primary'
                                : 'bg-obs-sidebar/40 border-obs-border text-obs-text-secondary hover:border-obs-border-subtle'
                            }`}
                          >
                            <label className="flex items-center gap-3 cursor-pointer flex-1 mr-2 select-none">
                              <input
                                type="checkbox"
                                checked={isDone}
                                onChange={() => toggleItem(item.id)}
                                className="h-4 w-4 rounded accent-obs-accent cursor-pointer"
                              />
                              <span className={`text-xs ${isDone ? 'line-through text-obs-text-muted' : 'text-obs-text-primary font-medium'}`}>
                                {item.title}
                              </span>
                            </label>
                            <a
                              href={item.slug}
                              className="text-[11px] text-obs-accent hover:underline shrink-0 pl-2 font-mono"
                            >
                              Read Chapter →
                            </a>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
