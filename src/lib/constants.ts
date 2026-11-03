// Domain human-readable labels and presentation metadata.
export const DOMAIN_TITLES: Record<string, string> = {
  'core-java': 'Core Java',
  'spring-boot': 'Spring & Spring Boot',
  'system-design': 'System Design',
  'database-caching': 'Databases & Caching',
  'java-dsa': 'Data Structures & Algorithms',
  'cloud-devops': 'Cloud & DevOps',
  'docker-java-backend': 'Docker for Java Backend',
  'root': 'Start Here',
};

export const DOMAIN_ORDER = [
  'root',
  'core-java',
  'spring-boot',
  'system-design',
  'database-caching',
  'java-dsa',
  'cloud-devops',
  'docker-java-backend',
];

export const DOMAIN_DESCRIPTIONS: Record<string, string> = {
  'core-java':
    'OOP, the JVM and memory model, collections, generics, modern Java, concurrency, design patterns, and the language internals interviewers keep returning to.',
  'spring-boot':
    'Spring Boot fundamentals, Spring Core and AOP, MVC, JPA and Hibernate, Security, JWT, OAuth2, microservices, messaging, caching, and deployment.',
  'system-design':
    'A production-grade architecture curriculum: fundamentals, distributed systems, data, caching, messaging, APIs, security, observability, HLD case studies, and LLD.',
  'database-caching':
    'SQL engines, indexing, query planning, transactions, replication, sharding, NoSQL modeling, Redis, and cache correctness.',
  'java-dsa':
    'Foundations, data structures, algorithms, patterns, an 18-chapter graph masterclass, a 19-chapter dynamic-programming masterclass, and runnable Java implementations.',
  'cloud-devops':
    'A concise Cloud and DevOps syllabus that points to the canonical deployment, Kubernetes, CI/CD, and observability chapters in the Spring Boot track.',
  'docker-java-backend':
    'Docker and container fundamentals for Java backend services, with the canonical chapters maintained in the Spring Boot deployment track.',
  root: 'The repository overview, master study checklist, learning paths, and the entry points into the knowledge base.',
};

export const DOMAIN_COLORS: Record<
  string,
  { bg: string; text: string; border: string; hex: string }
> = {
  'core-java': {
    bg: 'rgba(245, 158, 11, 0.12)',
    text: '#fbbf24',
    border: 'rgba(245, 158, 11, 0.28)',
    hex: '#f59e0b',
  },
  'spring-boot': {
    bg: 'rgba(34, 197, 94, 0.12)',
    text: '#4ade80',
    border: 'rgba(34, 197, 94, 0.28)',
    hex: '#22c55e',
  },
  'system-design': {
    bg: 'rgba(96, 165, 250, 0.12)',
    text: '#93c5fd',
    border: 'rgba(96, 165, 250, 0.28)',
    hex: '#60a5fa',
  },
  'database-caching': {
    bg: 'rgba(45, 212, 191, 0.12)',
    text: '#5eead4',
    border: 'rgba(45, 212, 191, 0.28)',
    hex: '#2dd4bf',
  },
  'java-dsa': {
    bg: 'rgba(251, 113, 133, 0.12)',
    text: '#fda4af',
    border: 'rgba(251, 113, 133, 0.28)',
    hex: '#fb7185',
  },
  'cloud-devops': {
    bg: 'rgba(56, 189, 248, 0.12)',
    text: '#7dd3fc',
    border: 'rgba(56, 189, 248, 0.28)',
    hex: '#38bdf8',
  },
  'docker-java-backend': {
    bg: 'rgba(129, 140, 248, 0.12)',
    text: '#a5b4fc',
    border: 'rgba(129, 140, 248, 0.28)',
    hex: '#818cf8',
  },
  root: {
    bg: 'rgba(167, 139, 250, 0.12)',
    text: '#c4b5fd',
    border: 'rgba(167, 139, 250, 0.28)',
    hex: '#a78bfa',
  },
};

export const SITE = {
  name: 'Java Masterclass',
  shortName: 'Java Masterclass',
  tagline: 'A connected knowledge base for Java, Spring, system design, and algorithms.',
  description:
    'A premium, Obsidian-inspired knowledge base for Java backend engineering: Core Java internals, Spring Boot, system design, databases, caching, and data structures and algorithms.',
  github: 'https://github.com/',
};
