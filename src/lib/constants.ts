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
    bg: 'var(--domain-core-java-bg)',
    text: 'var(--domain-core-java-text)',
    border: 'var(--domain-core-java-border)',
    hex: '#a54806',
  },
  'spring-boot': {
    bg: 'var(--domain-spring-boot-bg)',
    text: 'var(--domain-spring-boot-text)',
    border: 'var(--domain-spring-boot-border)',
    hex: '#087f5b',
  },
  'system-design': {
    bg: 'var(--domain-system-design-bg)',
    text: 'var(--domain-system-design-text)',
    border: 'var(--domain-system-design-border)',
    hex: '#2563eb',
  },
  'database-caching': {
    bg: 'var(--domain-database-caching-bg)',
    text: 'var(--domain-database-caching-text)',
    border: 'var(--domain-database-caching-border)',
    hex: '#087f5b',
  },
  'java-dsa': {
    bg: 'var(--domain-java-dsa-bg)',
    text: 'var(--domain-java-dsa-text)',
    border: 'var(--domain-java-dsa-border)',
    hex: '#c92a2a',
  },
  'cloud-devops': {
    bg: 'var(--domain-cloud-devops-bg)',
    text: 'var(--domain-cloud-devops-text)',
    border: 'var(--domain-cloud-devops-border)',
    hex: '#1e67aa',
  },
  'docker-java-backend': {
    bg: 'var(--domain-docker-java-backend-bg)',
    text: 'var(--domain-docker-java-backend-text)',
    border: 'var(--domain-docker-java-backend-border)',
    hex: '#4e3a9d',
  },
  root: {
    bg: 'var(--domain-root-bg)',
    text: 'var(--domain-root-text)',
    border: 'var(--domain-root-border)',
    hex: '#6d3df5',
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
