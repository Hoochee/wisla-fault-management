import { Component, computed, ElementRef, inject, OnInit, signal, ViewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { FmApiService } from '../../core/api/fm-api.service';
import { AuthService } from '../../core/auth/auth.service';
import { formatApiError } from '../../core/api/api-error';
import {
  ConfigurationItem,
  Event,
  ProductAdmin,
  ProductComponentAdmin,
  ProductComponentPatch,
  ProductHealthDetail,
  ProductHealthHistoryBucket,
} from '../../core/api/api.models';
import { CiHealthProfile } from '../../core/health/health.models';
import { hasSankeyGraph } from '../../core/health/health-snapshot.mapper';
import { buildProfiles, findWorstCi, getHealthPercentColor, percentToLevel } from '../../core/health/health-profile.util';
import { ComponentWeightEditorComponent } from '../../shared/health/component-weight-editor.component';
import { HealthBadgeComponent } from '../../shared/health/health-badge.component';
import { HealthHistoryHeatmapComponent } from '../../shared/health/health-history-heatmap.component';
import { MonqHealthGraphComponent } from '../../shared/health/monq-health-graph.component';
import { OperativeCenterPanelComponent } from '../../shared/health/operative-center-panel.component';
import { SeverityBadgeComponent } from '../../shared/severity-badge/severity-badge.component';

interface ProductForm {
  name: string;
  code: string;
  tenant: string;
  site: string;
  tagsText: string;
}

@Component({
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
  templateUrl: './health-product-page.component.html',
  styleUrl: './health-product-page.component.scss',
})
export class HealthProductPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  product: ProductHealthDetail | null = null;
  productAdmin: ProductAdmin | null = null;
  ciList: ConfigurationItem[] = [];
  events: Event[] = [];
  profiles: Record<string, CiHealthProfile> = {};
  selectedCiId = '';
  productId = '';
  history: ProductHealthHistoryBucket[] = [];
  historyError = false;
  graphError = false;
  readonly percentToLevel = percentToLevel;
  readonly getHealthPercentColor = getHealthPercentColor;
  readonly hasSankeyGraph = hasSankeyGraph;

  readonly isAdmin = this.auth.isAdmin;
  form: ProductForm = this.emptyForm();
  readonly editModalOpen = signal(false);
  readonly ciPickerOpen = signal(false);
  readonly saving = signal(false);
  readonly formError = signal('');
  readonly allCis = signal<ConfigurationItem[]>([]);
  ciSearch = '';
  pickerCiIds: string[] = [];
  readonly deleteTarget = signal(false);
  readonly deleteBlocked = signal(false);
  readonly weightError = signal('');
  readonly weightModalOpen = signal(false);
  @ViewChild('operative') operativeEl?: ElementRef<HTMLElement>;

  readonly filteredCis = computed(() => {
    const q = this.ciSearch.trim().toLowerCase();
    if (!q) return this.allCis();
    return this.allCis().filter((ci) => ci.fqdn.toLowerCase().includes(q));
  });

  ngOnInit(): void {
    this.productId = this.route.snapshot.paramMap.get('productId')!;
    const boot = () => this.reloadCard();
    if (this.auth.currentUser()) {
      boot();
    } else {
      this.auth.loadCurrentUser().subscribe(() => boot());
    }
  }

  selectCi(id: string): void {
    this.selectedCiId = id;
    this.operativeEl?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  openEditProduct(): void {
    if (!this.productAdmin) return;
    this.form = {
      name: this.productAdmin.name,
      code: this.productAdmin.code,
      tenant: this.productAdmin.tenant,
      site: this.productAdmin.site,
      tagsText: this.productAdmin.tags.join(', '),
    };
    this.formError.set('');
    this.editModalOpen.set(true);
  }

  closeEditModal(): void {
    this.editModalOpen.set(false);
  }

  saveProductAttributes(): void {
    if (!this.productAdmin) return;
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
    this.api.patchProduct(this.productAdmin.id, body).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.productAdmin = updated;
        this.closeEditModal();
        this.reloadCard();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.formError.set(err.status === 409 ? 'Код продукта уже занят' : 'Не удалось сохранить продукт');
      },
    });
  }

  openCiPicker(): void {
    this.pickerCiIds = this.productAdmin ? [...this.productAdmin.ciIds] : this.ciList.map((ci) => ci.id);
    this.ciSearch = '';
    this.formError.set('');
    if (!this.allCis().length) {
      this.api.listConfigurationItems().subscribe((items) => this.allCis.set(items));
    }
    this.ciPickerOpen.set(true);
  }

  closeCiPicker(): void {
    this.ciPickerOpen.set(false);
  }

  togglePickerCi(ciId: string, checked: boolean): void {
    const set = new Set(this.pickerCiIds);
    if (checked) set.add(ciId);
    else set.delete(ciId);
    this.pickerCiIds = [...set];
  }

  isPickerCiSelected(ciId: string): boolean {
    return this.pickerCiIds.includes(ciId);
  }

  saveCiLinks(): void {
    if (!this.productAdmin) return;
    this.saving.set(true);
    this.formError.set('');
    this.api.patchProduct(this.productAdmin.id, { ciIds: this.pickerCiIds }).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.productAdmin = updated;
        this.closeCiPicker();
        this.reloadCard();
      },
      error: () => {
        this.saving.set(false);
        this.formError.set('Не удалось обновить состав продукта');
      },
    });
  }

  unlinkCi(ciId: string): void {
    if (!this.productAdmin) return;
    const ciIds = this.productAdmin.ciIds.filter((id) => id !== ciId);
    this.api.patchProduct(this.productAdmin.id, { ciIds }).subscribe({
      next: (updated) => {
        this.productAdmin = updated;
        this.reloadCard();
      },
    });
  }

  confirmDeleteProduct(): void {
    this.deleteTarget.set(true);
  }

  deleteProduct(): void {
    if (!this.productAdmin) return;
    this.deleteTarget.set(false);
    this.api.deleteProduct(this.productAdmin.id).subscribe({
      next: () => {
        void this.router.navigate(['/health']);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.deleteBlocked.set(true);
        }
      },
    });
  }

  get editorComponents(): ProductComponentAdmin[] {
    if (this.productAdmin?.components?.length) {
      return this.productAdmin.components;
    }
    return (this.product?.components ?? []).map((c) => ({
      id: c.code,
      code: c.code,
      name: c.name,
      weight: c.weight ?? 0,
      influenceType: c.influenceType ?? 'weighted',
      criticalThreshold: 100,
      ciIds: c.ciIds ?? [],
    }));
  }

  /** Admin slots overlay snapshot rows so «Вес» stays in sync after PATCH. */
  get displayComponents() {
    const snapshot = this.product?.components ?? [];
    const admin = this.productAdmin?.components ?? [];
    if (!admin.length) return snapshot;
    const byCode = new Map(snapshot.map((c) => [c.code, c]));
    return admin.map((a) => {
      const s = byCode.get(a.code);
      return {
        code: a.code,
        name: a.name,
        healthPercent: s?.healthPercent ?? 100,
        damagePercent: s?.damagePercent ?? 0,
        weight: a.weight,
        influenceType: a.influenceType,
        ciIds: a.ciIds,
      };
    });
  }

  openWeightModal(): void {
    this.weightError.set('');
    this.weightModalOpen.set(true);
  }

  closeWeightModal(): void {
    this.weightModalOpen.set(false);
  }

  saveComponentWeights(components: ProductComponentPatch[]): void {
    if (!this.product) return;
    this.saving.set(true);
    this.weightError.set('');
    this.api.patchProduct(this.product.id, { components }).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.productAdmin = updated;
        this.closeWeightModal();
        this.reloadCard();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.weightError.set(formatApiError(err, 'Не удалось сохранить компоненты'));
      },
    });
  }

  private reloadCard(): void {
    this.api.getProduct(this.productId).subscribe({
      next: (detail) => this.applyDetail(detail),
      error: () => {
        this.product = null;
        this.graphError = true;
      },
    });
    this.loadHistory();
    if (this.isAdmin()) {
      this.api.getProductAdmin(this.productId).subscribe({
        next: (admin) => (this.productAdmin = admin),
        error: () => (this.productAdmin = null),
      });
    }
  }

  private loadHistory(): void {
    const to = new Date();
    const from = new Date(to);
    from.setHours(0, 0, 0, 0);
    this.historyError = false;
    this.api
      .getProductHistory(this.productId, {
        from: from.toISOString(),
        to: to.toISOString(),
        bucketMinutes: 15,
      })
      .subscribe({
        next: (buckets) => {
          this.history = buckets;
        },
        error: () => {
          this.history = [];
          this.historyError = true;
        },
      });
  }

  private applyDetail(detail: ProductHealthDetail): void {
    this.product = detail;
    this.ciList = detail.configurationItems ?? [];
    this.events = detail.activeEvents ?? [];
    this.graphError = !hasSankeyGraph(detail.sankey);
    this.profiles = buildProfiles(this.ciList, this.events);
    const worst = findWorstCi(this.ciList, this.profiles);
    if (worst) this.selectedCiId = worst.id;
  }

  private emptyForm(): ProductForm {
    return { name: '', code: '', tenant: '', site: '', tagsText: '' };
  }
}
