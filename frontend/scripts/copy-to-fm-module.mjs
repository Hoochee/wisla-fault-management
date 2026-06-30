import { cpSync, mkdirSync, rmSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const frontendRoot = resolve(__dirname, '..');
const distDir = join(frontendRoot, 'dist', 'frontend', 'browser');
const targetDir = resolve(frontendRoot, '..', 'backend', 'fm-module', 'src', 'main', 'resources', 'static');

if (!existsSync(distDir)) {
  console.error('Build output not found. Run `npm run build` first.');
  process.exit(1);
}

if (existsSync(targetDir)) {
  rmSync(targetDir, { recursive: true, force: true });
}
mkdirSync(targetDir, { recursive: true });
cpSync(distDir, targetDir, { recursive: true });
console.log(`Copied ${distDir} → ${targetDir}`);
