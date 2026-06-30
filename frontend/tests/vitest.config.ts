import { defineConfig } from 'vitest/config';
import path from 'path';

const root = path.resolve(__dirname, '..');

export default defineConfig({
  root,
  test: {
    environment: 'jsdom',
    setupFiles: [path.resolve(__dirname, 'vitest.setup.ts')],
    include: ['tests/unit/**/*.test.ts'],
  },
  resolve: {
    alias: {
      '@app': path.resolve(root, 'src/app'),
    },
  },
});