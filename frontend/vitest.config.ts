import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config.ts';

// Spec 11: frontend line coverage >= 70 % on src/features and src/api (gate 6).
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      passWithNoTests: false,
      reporters: ['default', 'junit'],
      outputFile: { junit: '../build/reports/vitest-junit.xml' },
      coverage: {
        provider: 'v8',
        include: ['src/features/**', 'src/api/**'],
        reporter: ['text-summary', 'json-summary', 'html'],
        thresholds: {
          lines: 70,
        },
      },
    },
  }),
);
