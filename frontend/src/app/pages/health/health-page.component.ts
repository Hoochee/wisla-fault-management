import { RouterLink } from '@angular/router';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { FmApiService } from '../../core/api/fm-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { ConfigurationItem, Event, ProductHealth, ProductHealthDetail, ProductHealthHistoryBucket, Severity } from '../../core/api/api.models';
import { CiHealthProfile } from '../../core/health/health.models';
import {
  buildProfiles,
  findWorstCi,
  getHealthPercentColor,
  percentToLevel,
  uniqueSites,
  uniqueTenants,
} from '../../core/health/health-profile.util';
import { CiSidebarComponent } from '../../shared/health/ci-sidebar.component';
import { MonqHealthGraphComponent } from '../../shared/health/monq-health-graph.component';
import { OperativeCenterPanelComponent } from '../../shared/health/operative-center-panel.component';
import { SeverityBadgeComponent } from '../../shared/severity-badge/severity-badge.component';

interface ProductForm {
  name: string;
  code: string;
  tenant: string;
  site: string;
  tagsText: string;
  ciIds: string[];
}

@Component({
  selector: 'app-health',
  standalone: true,
  imports: [RouterLink, FormsModule, CiSidebarComponent, OperativeCenterPanelComponent, SeverityBadgeComponent, MonqHealthGraphComponent],
  templateUrl: './health-page.component.html',
  styleUrl: './health-page.component.scss',
})
export class HealthPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  private readonly auth = inject(AuthService);

  products: ProductHealth[] = [];
  filteredProducts: ProductHealth[] = [];
  selectedProductId = '';
  selectedCiId = '';
  selectedDetail: ProductHealthDetail | null = null;
  historyByProduct: Record<string, ProductHealthHistoryBucket[]> = {};
  ciList: ConfigurationItem[] = [];
  events: Event[] = [];
  profiles: Record<string, CiHealthProfile> = {};
  tenantFilter = '';
  siteFilter = '';
  tenants: string[] = [];
  sites: string[] = [];
  readonly severities: Severity[] = ['fatal', 'critical', 'major', 'minor', 'warning', 'normal'];
  readonly isAdmin = this.auth.isAdmin;
  readonly percentToLevel = percentToLevel;
  readonly getHealthPercentColor = getHealthPercentColor;

  form: ProductForm = this.emptyForm();
  readonly modalOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly ciIdsTouched = signal(false);
  readonly allCis = signal<ConfigurationItem[]>([]);
  ciSearch = '';
  readonly deleteTarget = signal<ProductHealth | null>(null);
  readonly deleteBlocked = signal<ProductHealth | null>(null);

  readonly filteredCis = computed(() => {
    const q = this.ciSearch.trim().toLowerCase();
    if (!q) return this.allCis();
    return this.allCis().filter((ci) => ci.fqdn.toLowerCase().includes(q));
  });

  get selectedProduct(): ProductHealth | undefined {
    return this.products.find((p) => p.id === this.selectedProductId);
  }

  historyFor(productId: string): ProductHealthHistoryBucket[] {
    return this.historyByProduct[productId] ?? [];
  }

  ngOnInit(): void {
    if (!this.auth.currentUser()) {
      this.auth.loadCurrentUser().subscribe();
    }
    this.reloadHeatmap();
  }

  onTenantChange(value: string): void {
    this.tenantFilter = value;
    this.applyFilters();
    this.ensureSelection();
  }

  onSiteChange(value: string): void {
    this.siteFilter = value;
    this.applyFilters();
    this.ensureSelection();
  }

  selectProduct(id: string): void {
    this.selectedProductId = id;
    this.api.getProduct(id).subscribe((detail) => this.applyDetail(detail));
    this.loadHistory(id);
  }

  selectCi(id: string): void {
    this.selectedCiId = id;
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form = this.emptyForm();
    this.ciIdsTouched.set(true);
    this.formError.set('');
    this.ciSearch = '';
    this.loadCisForModal();
    this.modalOpen.set(true);
  }

  openEdit(product: ProductHealth, event: MouseEvent): void {
    event.stopPropagation();
    this.api.getProductAdmin(product.id).subscribe({
      next: (admin) => {
        this.editingId.set(admin.id);
        this.form = {
          name: admin.name,
          code: admin.code,
          tenant: admin.tenant,
          site: admin.site,
          tagsText: admin.tags.join(', '),
          ciIds: [...admin.ciIds],
        };
        this.ciIdsTouched.set(false);
        this.formError.set('');
        this.ciSearch = '';
        this.loadCisForModal();
        this.modalOpen.set(true);
      },
      error: () => this.formError.set('Не удалось загрузить продукт'),
    });
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  toggleCiId(ciId: string, checked: boolean): void {
    const set = new Set(this.form.ciIds);
    if (checked) set.add(ciId);
    else set.delete(ciId);
    this.form.ciIds = [...set];
    if (this.editingId()) this.ciIdsTouched.set(true);
  }

  isCiSelected(ciId: string): boolean {
    return this.form.ciIds.includes(ciId);
  }

  saveProduct(): void {
    const tags = this.form.tagsText
      .split(',')
      .map((t) => t.trim())
      .filter(Boolean);
    const body = {
      name: this.form.name.trim(),
      code: this.form.code.trim(),
      tenant: this.form.tenant.trim(),
      site: this.form.site.trim(),
      tags,
    };
    if (!body.name || !body.code || !body.tenant || !body.site) {
      this.formError.set('Заполните обязательные поля');
      return;
    }

    this.saving.set(true);
    this.formError.set('');
    const id = this.editingId();

    if (id) {
      const patch = this.ciIdsTouched() ? { ...body, ciIds: this.form.ciIds } : body;
      this.api.patchProduct(id, patch).subscribe({
        next: () => {
          this.saving.set(false);
          this.closeModal();
          this.reloadHeatmap(id);
        },
        error: (err: HttpErrorResponse) => {
          this.saving.set(false);
          this.formError.set(err.status === 409 ? 'Код продукта уже занят' : 'Не удалось сохранить продукт');
        },
      });
      return;
    }

    this.api.createProduct({ ...body, ciIds: this.form.ciIds }).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.closeModal();
        this.reloadHeatmap(created.id);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.formError.set(err.status === 409 ? 'Код продукта уже занят' : 'Не удалось создать продукт');
      },
    });
  }

  confirmDelete(product: ProductHealth, event: MouseEvent): void {
    event.stopPropagation();
    this.deleteTarget.set(product);
  }

  deleteProduct(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleteTarget.set(null);
    this.api.deleteProduct(target.id).subscribe({
      next: () => this.reloadHeatmap(),
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.deleteBlocked.set(target);
        }
      },
    });
  }

  reloadHeatmap(selectId?: string): void {
    this.api.getProducts().subscribe((products) => {
      this.products = products;
      this.tenants = uniqueTenants(products);
      this.sites = uniqueSites(products);
      this.applyFilters();
      const preferred = selectId ?? this.selectedProductId;
      if (preferred && this.filteredProducts.some((p) => p.id === preferred)) {
        this.selectProduct(preferred);
      } else if (this.filteredProducts.length) {
        this.selectProduct(this.filteredProducts[0].id);
      } else {
        this.selectedProductId = '';
        this.selectedDetail = null;
        this.ciList = [];
        this.events = [];
        this.profiles = {};
      }
    });
  }

  private loadCisForModal(): void {
    if (this.allCis().length) return;
    this.api.listConfigurationItems().subscribe((items) => this.allCis.set(items));
  }

  private applyFilters(): void {
    this.filteredProducts = this.products.filter((p) => {
      if (this.tenantFilter && p.tenant !== this.tenantFilter) return false;
      if (this.siteFilter && p.site !== this.siteFilter) return false;
      return true;
    });
  }

  private ensureSelection(): void {
    if (this.filteredProducts.length && !this.filteredProducts.some((p) => p.id === this.selectedProductId)) {
      this.selectProduct(this.filteredProducts[0].id);
    }
  }

  private applyDetail(detail: ProductHealthDetail): void {
    this.selectedDetail = detail;
    this.ciList = detail.configurationItems ?? [];
    this.events = detail.activeEvents ?? [];
    this.profiles = buildProfiles(this.ciList, this.events);
    const worst = findWorstCi(this.ciList, this.profiles);
    if (worst) this.selectedCiId = worst.id;
  }

  private loadHistory(productId: string): void {
    if (this.historyByProduct[productId]?.length) return;
    const to = new Date();
    const from = new Date(to);
    from.setHours(0, 0, 0, 0);
    this.api
      .getProductHistory(productId, {
        from: from.toISOString(),
        to: to.toISOString(),
        bucketMinutes: 15,
      })
      .subscribe({
        next: (buckets) => {
          this.historyByProduct = { ...this.historyByProduct, [productId]: buckets };
        },
        error: () => {
          this.historyByProduct = { ...this.historyByProduct, [productId]: [] };
        },
      });
  }

  private emptyForm(): ProductForm {
    return { name: '', code: '', tenant: '', site: '', tagsText: '', ciIds: [] };
  }
}
