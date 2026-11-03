// Domain human-readable labels
export const DOMAIN_TITLES: Record<string, string> = {
  'core-java': 'Core Java',
  'spring-boot': 'Spring Boot',
  'system-design': 'System Design',
  'database-caching': 'Database & Caching',
  'java-dsa': 'Data Structures & Algorithms',
  'cloud-devops': 'Cloud & DevOps',
  'docker-java-backend': 'Docker Java Backend',
  'root': 'Knowledge Hub',
};

// Domain brand colors (Obsidian accent variants)
export const DOMAIN_COLORS: Record<string, { bg: string; text: string; border: string; hex: string }> = {
  'core-java': { bg: 'rgba(167, 139, 250, 0.12)', text: '#c4b5fd', border: 'rgba(167, 139, 250, 0.3)', hex: '#a78bfa' },
  'spring-boot': { bg: 'rgba(52, 211, 153, 0.12)', text: '#6ee7b7', border: 'rgba(52, 211, 153, 0.3)', hex: '#34d399' },
  'system-design': { bg: 'rgba(56, 189, 248, 0.12)', text: '#7dd3fc', border: 'rgba(56, 189, 248, 0.3)', hex: '#38bdf8' },
  'database-caching': { bg: 'rgba(251, 191, 36, 0.12)', text: '#fde047', border: 'rgba(251, 191, 36, 0.3)', hex: '#fbbf24' },
  'java-dsa': { bg: 'rgba(244, 63, 94, 0.12)', text: '#fda4af', border: 'rgba(244, 63, 94, 0.3)', hex: '#f43f5e' },
  'cloud-devops': { bg: 'rgba(96, 165, 250, 0.12)', text: '#93c5fd', border: 'rgba(96, 165, 250, 0.3)', hex: '#60a5fa' },
  'docker-java-backend': { bg: 'rgba(129, 140, 248, 0.12)', text: '#a5b4fc', border: 'rgba(129, 140, 248, 0.3)', hex: '#818cf8' },
  'root': { bg: 'rgba(192, 132, 252, 0.12)', text: '#e9d5ff', border: 'rgba(192, 132, 252, 0.3)', hex: '#c084fc' },
};
