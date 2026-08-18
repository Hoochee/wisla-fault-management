import { readFileSync } from 'node:fs';
import path from 'node:path';
import {
  Component,
  EventEmitter,
  Input,
  Output,
  ɵcompileComponent as compileComponent,
} from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, provideRouter, RouterLink } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ConfigurationItem, ProductHealthDetail, User } from '../../src/app/core/api/api.models';
import { FmApiService } from '../../src/app/core/api/fm-api.service';
import { AuthService } from '../../src/app/core/auth/auth.service';
import { CiHealthProfile } from '../../src/app/core/health/health.models';
import { HealthProductPageComponent } from '../../src/app/pages/health/health-product-page.component';
import { CiHealthTabComponent } from '../../src/app/shared/health/ci-health-tab.component';
import { CiSidebarComponent } from '../../src/app/shared/health/ci-sidebar.component';
import { ComponentWeightEditorComponent } from '../../src/app/shared/health/component-weight-editor.component';
import { HealthBadgeComponent } from '../../src/app/shared/health/health-badge.component';
import { HealthHistoryHeatmapComponent } from '../../src/app/shared/health/health-history-heatmap.component';
import { MonqHealthGraphComponent } from '../../src/app/shared/health/monq-health-graph.component';
import { OperativeCenterPanelComponent } from '../../src/app/shared/health/operative-center-panel.component';
import { SeverityBadgeComponent } from '../../src/app/shared/severity-badge/severity-badge.component';

const PAGE_TEMPLATE = readFileSync(
  path.resolve('src/app/pages/health/health-product-page.component.html'),
  'utf-8',
);
const PAGE_STYLES = readFileSync(
  path.resolve('src/app/pages/health/health-product-page.component.scss'),
  'utf-8',
);

const PRODUCT_ID = '550e8400-e29b-41d4-a716-446655440000';
const CI_ID = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

const CI: ConfigurationItem = {
  id: CI_ID,
  fqdn: 'app-a.wisla.local',
  ciType: 'host',
  system: 'demo',
  subsystem: 'web',
  software: '',
  products: [],
  tags: [],
};

const HEALTH: CiHealthProfile = {
  ciId: CI_ID,
  currentHealth: 'ok',
  healthPercent: 100,
  minToday: 'ok',
  minTodayPercent: 100,
  maxToday: 'ok',
  maxTodayPercent: 100,
  components: [],
  dependents: [],
  signalsBySeverity: { fatal: 0, critical: 0, major: 0, minor: 0, warning: 0, normal: 0 },
  timeline: [],
};

@Component({ selector: 'app-monq-health-graph', standalone: true, template: '' })
class MonqHealthGraphStub {
  @Input() product: unknown;
}

@Component({ selector: 'app-health-history-heatmap', standalone: true, template: '' })
class HeatmapStub {
  @Input() buckets: unknown;
}

@Component({ selector: 'app-ci-sidebar', standalone: true, template: '' })
class CiSidebarStub {
  @Input() product: unknown;
  @Input() ciList: unknown;
  @Input() selectedCiId: unknown;
  @Input() profiles: unknown;
  @Output() selectCi = new EventEmitter<string>();
}

@Component({ selector: 'app-operative-center-panel', standalone: true, template: '' })
class OperativeStub {
  @Input() product: unknown;
  @Input() ciList: unknown;
  @Input() selectedCiId: unknown;
  @Input() profiles: unknown;
  @Input() events: unknown;
  @Input() defaultTab: unknown;
}

function compileHealthProductPage(): void {
  compileComponent(HealthProductPageComponent, {
    selector: 'app-health-product',
    standalone: true,
    imports: [
      RouterLink,
      FormsModule,
      CiSidebarComponent,
      OperativeCenterPanelComponent,
      HealthBadgeComponent,
      SeverityBadgeComponent,
      MonqHealthGraphComponent,
      HealthHistoryHeatmapComponent,
      ComponentWeightEditorComponent,
    ],
    template: PAGE_TEMPLATE,
    styles: [PAGE_STYLES],
  });
}

const USER: User = {
  id: 'u1',
  login: 'tester',
  fullName: 'Tester',
  email: 't@example.com',
  roleIds: [],
  team: 'ops',
  active: true,
};

function productDetail(): ProductHealthDetail {
  return {
    id: PRODUCT_ID,
    name: 'Gift Shop',
    tenant: 'demo',
    site: 'lab',
    maxSeverity: 'normal',
    activeEventCount: 0,
    ciIds: [CI_ID],
    tags: [],
    healthPercent: 100,
    damagePercent: 0,
    configurationItems: [CI],
    activeEvents: [],
  };
}

describe('Health console links use productId', () => {
  it('CiHealthTabComponent «Перейти к сигналам» uses queryParams.productId, not ci', async () => {
    await TestBed.configureTestingModule({
      imports: [CiHealthTabComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(CiHealthTabComponent);
    fixture.componentInstance.ci = CI;
    fixture.componentInstance.health = HEALTH;
    fixture.componentInstance.productId = PRODUCT_ID;
    fixture.detectChanges();

    const link = Array.from(fixture.nativeElement.querySelectorAll('a')).find((anchor) =>
      anchor.textContent?.includes('Перейти к сигналам'),
    ) as HTMLAnchorElement | undefined;
    expect(link).toBeTruthy();
    const href = link!.getAttribute('href') ?? '';
    expect(href).toContain(`productId=${PRODUCT_ID}`);
    expect(href).not.toMatch(/[?&]ci=/);
  });

  it.each([true, false])(
    'HealthProductPageComponent header «В консоль» uses { productId } (admin=%s)',
    async (admin) => {
      compileHealthProductPage();
      const isAdmin = signal(admin);
      const currentUser = signal<User | null>(USER);

      await TestBed.configureTestingModule({
        imports: [HealthProductPageComponent],
        providers: [
          provideRouter([]),
          {
            provide: FmApiService,
            useValue: {
              getProduct: vi.fn().mockReturnValue(of(productDetail())),
              getProductHistory: vi.fn().mockReturnValue(of([])),
              getProductAdmin: vi.fn().mockReturnValue(of(null)),
            },
          },
          {
            provide: AuthService,
            useValue: { isAdmin, currentUser, loadCurrentUser: vi.fn() },
          },
          {
            provide: ActivatedRoute,
            useValue: { snapshot: { paramMap: { get: () => PRODUCT_ID } } },
          },
        ],
      })
        .overrideComponent(HealthProductPageComponent, {
          remove: {
            imports: [
              MonqHealthGraphComponent,
              HealthHistoryHeatmapComponent,
              CiSidebarComponent,
              OperativeCenterPanelComponent,
            ],
          },
          add: {
            imports: [MonqHealthGraphStub, HeatmapStub, CiSidebarStub, OperativeStub],
          },
        })
        .compileComponents();

      const fixture = TestBed.createComponent(HealthProductPageComponent);
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;

      const link = Array.from(el.querySelectorAll('a')).find((anchor) =>
        anchor.textContent?.trim() === 'В консоль',
      ) as HTMLAnchorElement | undefined;
      expect(link).toBeTruthy();
      const href = link!.getAttribute('href') ?? '';
      expect(href).toContain(`productId=${PRODUCT_ID}`);
    },
  );
});

describe('OperativeCenterPanelComponent CI-scoped console links', () => {
  async function setupPanel(defaultTab: 'signals' | 'events') {
    await TestBed.configureTestingModule({
      imports: [OperativeCenterPanelComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(OperativeCenterPanelComponent);
    fixture.componentInstance.product = productDetail();
    fixture.componentInstance.ciList = [CI];
    fixture.componentInstance.selectedCiId = CI_ID;
    fixture.componentInstance.profiles = { [CI_ID]: HEALTH };
    fixture.componentInstance.events = [];
    fixture.componentInstance.defaultTab = defaultTab;
    fixture.detectChanges();
    return fixture;
  }

  it('«Открыть в консоли →» uses queryParams.ciId of the selected CI', async () => {
    const fixture = await setupPanel('signals');
    const link = Array.from(fixture.nativeElement.querySelectorAll('a')).find((anchor) =>
      anchor.textContent?.includes('Открыть в консоли'),
    ) as HTMLAnchorElement | undefined;
    expect(link).toBeTruthy();
    const href = link!.getAttribute('href') ?? '';
    expect(href).toContain(`ciId=${CI_ID}`);
    expect(href).not.toMatch(/[?&]ci=/);
    expect(href).not.toContain(`productId=`);
  });

  it('«Консоль →» uses queryParams.ciId of the selected CI', async () => {
    const fixture = await setupPanel('events');
    const link = Array.from(fixture.nativeElement.querySelectorAll('a')).find((anchor) =>
      anchor.textContent?.trim() === 'Консоль →',
    ) as HTMLAnchorElement | undefined;
    expect(link).toBeTruthy();
    const href = link!.getAttribute('href') ?? '';
    expect(href).toContain(`ciId=${CI_ID}`);
    expect(href).not.toMatch(/[?&]ci=/);
    expect(href).not.toContain(`productId=`);
  });
});
