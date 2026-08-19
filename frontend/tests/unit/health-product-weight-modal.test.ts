import { readFileSync } from 'node:fs';
import path from 'node:path';
import {
  Component,
  Input,
  ɵcompileComponent as compileComponent,
} from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, provideRouter, RouterLink } from '@angular/router';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { FmApiService } from '../../src/app/core/api/fm-api.service';
import { AuthService } from '../../src/app/core/auth/auth.service';
import { ConfigurationItem, ProductAdmin, ProductHealthDetail, ProductPatchRequest, User } from '../../src/app/core/api/api.models';
import { HealthProductPageComponent } from '../../src/app/pages/health/health-product-page.component';
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

@Component({ selector: 'app-monq-health-graph', standalone: true, template: '' })
class MonqHealthGraphStub {
  @Input() product: unknown;
}

@Component({ selector: 'app-health-history-heatmap', standalone: true, template: '' })
class HeatmapStub {
  @Input() buckets: unknown;
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

const PRODUCT_ID = 'prod-1';

function productDetail(weight = 50): ProductHealthDetail {
  return {
    id: PRODUCT_ID,
    name: 'Gift Shop',
    tenant: 'demo',
    site: 'lab',
    maxSeverity: 'normal',
    activeEventCount: 0,
    ciIds: ['ci-1'],
    tags: [],
    healthPercent: 100,
    damagePercent: 0,
    components: [
      {
        code: 'slot-a',
        name: 'Slot A',
        healthPercent: 100,
        damagePercent: 0,
        weight,
        influenceType: 'weighted',
        ciIds: ['ci-1'],
      },
    ],
    configurationItems: [
      {
        id: 'ci-1',
        fqdn: 'demo-server.wisla.local',
        ciType: 'host',
        system: 'demo',
        subsystem: 'web',
        software: '',
        products: [],
        tags: [],
      },
    ],
    activeEvents: [],
  };
}

function productAdmin(weight = 50): ProductAdmin {
  return {
    id: PRODUCT_ID,
    name: 'Gift Shop',
    code: 'gift-shop',
    tenant: 'demo',
    site: 'lab',
    tags: [],
    ciIds: ['ci-1'],
    components: [
      {
        id: 'c1',
        code: 'slot-a',
        name: 'Slot A',
        weight,
        influenceType: 'weighted',
        criticalThreshold: 100,
        ciIds: ['ci-1'],
      },
    ],
  };
}

function buttonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as
    | HTMLButtonElement
    | undefined;
}

function componentsWeightHeader(root: HTMLElement): string[] {
  return Array.from(root.querySelectorAll('.components-card th')).map((th) => th.textContent?.trim() ?? '');
}

async function setup(opts: { admin: boolean }) {
  compileHealthProductPage();
  const isAdmin = signal(opts.admin);
  const currentUser = signal<User | null>(USER);
  const detail = productDetail();
  const admin = productAdmin();

  const api = {
    getProduct: vi.fn().mockReturnValue(of(detail)),
    getProductHistory: vi.fn().mockReturnValue(of([])),
    getProductAdmin: vi.fn().mockReturnValue(of(admin)),
    patchProduct: vi.fn().mockImplementation((_id: string, patch: ProductPatchRequest) => {
      const nextWeight = patch.components?.[0]?.weight ?? admin.components![0].weight;
      return of(productAdmin(nextWeight));
    }),
  };

  await TestBed.configureTestingModule({
    imports: [HealthProductPageComponent],
    providers: [
      provideRouter([]),
      { provide: FmApiService, useValue: api },
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
          OperativeCenterPanelComponent,
        ],
      },
      add: {
        imports: [MonqHealthGraphStub, HeatmapStub, OperativeStub],
      },
    })
    .compileComponents();

  const fixture = TestBed.createComponent(HealthProductPageComponent);
  fixture.detectChanges();
  return { fixture, api, el: fixture.nativeElement as HTMLElement };
}

describe('HealthProductPageComponent weight modal', () => {
  it('hides the inline weight editor for admin while the modal is closed', async () => {
    const { el } = await setup({ admin: true });

    expect(buttonByText(el, 'Веса компонентов')).toBeTruthy();
    expect(el.querySelector('app-component-weight-editor')).toBeNull();
    expect(componentsWeightHeader(el)).toContain('Вес');
    expect(el.querySelector('.page > .tags')).toBeNull();
    expect(el.querySelector('app-ci-sidebar')).toBeNull();
    expect(el.querySelector('.ci-strip .ci-chip')).toBeTruthy();
  });

  it('does not show the weight button or editor for a specialist', async () => {
    const { el } = await setup({ admin: false });

    expect(buttonByText(el, 'Веса компонентов')).toBeUndefined();
    expect(el.querySelector('app-component-weight-editor')).toBeNull();
    expect(componentsWeightHeader(el)).toContain('Вес');
  });

  it('opens the modal for admin, PATCHes components on save, and closes the editor', async () => {
    const { fixture, api, el } = await setup({ admin: true });

    buttonByText(el, 'Веса компонентов')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(el.querySelector('.overlay')).toBeTruthy();
    const editor = el.querySelector('app-component-weight-editor');
    expect(editor).toBeTruthy();

    const input = editor!.querySelector('input[type="number"]') as HTMLInputElement;
    input.value = '30';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();

    buttonByText(el, 'Сохранить')!.click();
    fixture.detectChanges();

    expect(api.patchProduct).toHaveBeenCalledTimes(1);
    const patch = api.patchProduct.mock.calls[0][1] as ProductPatchRequest;
    expect(patch.components?.[0]).toMatchObject({ code: 'slot-a', weight: 30 });
    expect(el.querySelector('app-component-weight-editor')).toBeNull();
    expect(el.querySelector('.overlay')).toBeNull();
  });

  it('cancels without PATCH and reopens last persisted weights', async () => {
    const { fixture, api, el } = await setup({ admin: true });

    buttonByText(el, 'Веса компонентов')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const input = el.querySelector('app-component-weight-editor input[type="number"]') as HTMLInputElement;
    expect(input.value).toBe('50');
    input.value = '99';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();

    buttonByText(el, 'Отмена')!.click();
    fixture.detectChanges();

    expect(api.patchProduct).not.toHaveBeenCalled();
    expect(el.querySelector('app-component-weight-editor')).toBeNull();

    buttonByText(el, 'Веса компонентов')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const reopened = el.querySelector('app-component-weight-editor input[type="number"]') as HTMLInputElement;
    expect(reopened.value).toBe('50');

    (el.querySelector('.overlay') as HTMLElement).click();
    fixture.detectChanges();

    expect(api.patchProduct).not.toHaveBeenCalled();
    expect(el.querySelector('app-component-weight-editor')).toBeNull();
  });
});

describe('HealthProductPageComponent composition spoiler', () => {
  it('keeps «Состав продукта» collapsed by default and shows the CI table after expand', async () => {
    const { fixture, el } = await setup({ admin: true });
    const details = el.querySelector('section.composition details') as HTMLDetailsElement;

    expect(details).toBeTruthy();
    expect(details.open).toBe(false);
    expect(el.querySelector('section.composition h2')?.textContent?.trim()).toBe('Состав продукта');
    expect(buttonByText(el, '+ Добавить КЕ')).toBeTruthy();

    details.querySelector('summary')!.click();
    fixture.detectChanges();

    expect(details.open).toBe(true);
    const headers = Array.from(el.querySelectorAll('section.composition th')).map((th) => th.textContent?.trim());
    expect(headers).toEqual(expect.arrayContaining(['FQDN', 'Тип', 'Система', 'Подсистема']));
    expect(el.querySelector('section.composition tbody')?.textContent).toContain('demo-server.wisla.local');
    expect(buttonByText(el, 'Отвязать')).toBeTruthy();
  });

  it('does not show add or unlink CI actions for a specialist after expand', async () => {
    const { fixture, el } = await setup({ admin: false });
    const details = el.querySelector('section.composition details') as HTMLDetailsElement;

    details.querySelector('summary')!.click();
    fixture.detectChanges();

    expect(details.open).toBe(true);
    expect(buttonByText(el, '+ Добавить КЕ')).toBeUndefined();
    expect(buttonByText(el, 'Отвязать')).toBeUndefined();
    expect(el.querySelector('section.composition tbody')?.textContent).toContain('demo-server.wisla.local');
  });
});

describe('HealthProductPageComponent CI strip', () => {
  it('selectCi sets selectedCiId, marks the chip, and scrolls the operative panel', async () => {
    const { fixture, el } = await setup({ admin: false });
    const cmp = fixture.componentInstance;
    const second: ConfigurationItem = {
      id: 'ci-2',
      fqdn: 'giftshop-catalog.demo',
      ciType: 'service',
      system: 'demo',
      subsystem: 'web',
      software: '',
      products: [],
      tags: [],
    };
    cmp.ciList = [...cmp.ciList, second];
    fixture.detectChanges();

    const operative = el.querySelector('.operative') as HTMLElement;
    expect(operative).toBeTruthy();
    expect(cmp.operativeEl?.nativeElement).toBe(operative);
    const scroll = vi.fn();
    operative.scrollIntoView = scroll;

    cmp.selectCi('ci-2');
    fixture.detectChanges();

    expect(cmp.selectedCiId).toBe('ci-2');
    expect(el.querySelector('.ci-chip.selected')?.textContent).toContain('giftshop-catalog.demo');
    expect(scroll).toHaveBeenCalledWith({ behavior: 'smooth', block: 'nearest' });
  });
});
