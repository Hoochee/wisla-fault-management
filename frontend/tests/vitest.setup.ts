import 'zone.js';
import 'zone.js/testing';
import '@angular/compiler';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { afterEach } from 'vitest';
import { COMPILER_OPTIONS } from '@angular/core';
import { ResourceLoader } from '@angular/compiler';
import { getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting,
} from '@angular/platform-browser-dynamic/testing';

class VitestResourceLoader extends ResourceLoader {
  get(url: string): string {
    const filename = path.basename(url.replace(/\\/g, '/'));
    const direct = path.resolve('src/app/pages/health', filename);
    if (existsSync(direct)) {
      return readFileSync(direct, 'utf-8');
    }
    const found = findFile(path.resolve('src'), filename);
    if (found) {
      return readFileSync(found, 'utf-8');
    }
    throw new Error(`Failed to load ${url}`);
  }
}

function findFile(root: string, name: string): string | null {
  for (const entry of readdirSync(root, { withFileTypes: true })) {
    const full = path.join(root, entry.name);
    if (entry.isFile() && entry.name === name) return full;
    if (entry.isDirectory() && entry.name !== 'node_modules') {
      const nested = findFile(full, name);
      if (nested) return nested;
    }
  }
  return null;
}

getTestBed().initTestEnvironment(
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting([
    {
      provide: COMPILER_OPTIONS,
      useValue: {
        providers: [{ provide: ResourceLoader, useClass: VitestResourceLoader }],
      },
      multi: true,
    },
  ]),
);

afterEach(() => {
  getTestBed().resetTestingModule();
});
