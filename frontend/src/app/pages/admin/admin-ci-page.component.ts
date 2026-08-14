import { Component, inject, OnInit, signal } from '@angular/core';

import { RouterLink } from '@angular/router';

import { FormsModule } from '@angular/forms';

import { FmApiService } from '../../core/api/fm-api.service';

import { ConfigurationItem } from '../../core/api/api.models';

import { PageHeaderComponent } from '../../shared/page-header/page-header.component';



interface CiForm {

  fqdn: string;

  ciType: string;

  system: string;

  subsystem: string;

  tagsText: string;

}



@Component({

  selector: 'app-admin-ci',

  standalone: true,

  imports: [RouterLink, FormsModule, PageHeaderComponent],

  template: `

    <div class="page-top">

      <app-page-header title="Реестр КЕ" subtitle="Конфигурационные элементы" />

      <button type="button" class="btn-primary" (click)="openCreate()">+ Добавить</button>

    </div>

    <table class="data-table">

      <thead><tr><th>FQDN</th><th>Тип</th><th>Система</th><th>Подсистема</th><th>Теги</th><th></th></tr></thead>

      <tbody>

        @for (ci of items; track ci.id) {

          <tr>

            <td class="mono">{{ ci.fqdn }}</td>

            <td>{{ ci.ciType }}</td>

            <td>{{ ci.system }}</td>

            <td>{{ ci.subsystem }}</td>

            <td>{{ ci.tags.join(', ') }}</td>

            <td class="actions">

              <button type="button" class="link-btn" (click)="openEdit(ci)">Изменить</button>

              <button type="button" class="link-btn danger" (click)="deleteCi(ci)">Удалить</button>

            </td>

          </tr>

        }

      </tbody>

    </table>

    <a routerLink="/admin" class="back">← Администрирование</a>



    @if (modalOpen()) {

      <div class="overlay" (click)="closeModal()">

        <div class="modal" (click)="$event.stopPropagation()">

          <h2>{{ editingId() ? 'Редактирование КЕ' : 'Новый КЕ' }}</h2>

          <form (ngSubmit)="save()">

            <label>FQDN<input [(ngModel)]="form.fqdn" name="fqdn" required /></label>

            <label>Тип<input [(ngModel)]="form.ciType" name="ciType" required /></label>

            <label>Система<input [(ngModel)]="form.system" name="system" required /></label>

            <label>Подсистема<input [(ngModel)]="form.subsystem" name="subsystem" /></label>

            <label>Теги (через запятую)<input [(ngModel)]="form.tagsText" name="tagsText" /></label>

            @if (error()) {

              <p class="error">{{ error() }}</p>

            }

            <div class="modal-actions">

              <button type="button" class="btn" (click)="closeModal()">Отмена</button>

              <button type="submit" class="btn-primary" [disabled]="saving()">Сохранить</button>

            </div>

          </form>

        </div>

      </div>

    }

  `,

  styles: [

    `

      .page-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }

      .page-top app-page-header { flex: 1; }

      .btn-primary { padding: 0.5rem 1rem; background: var(--accent); color: #fff; border: none; border-radius: 6px; font-size: 0.875rem; cursor: pointer; white-space: nowrap; }

      .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

      .data-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; margin-bottom: 1rem; }

      .data-table th, .data-table td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); text-align: left; }

      .mono { font-family: var(--font-mono); font-size: 0.75rem; }

      .actions { white-space: nowrap; }

      .link-btn { background: none; border: none; color: var(--accent); cursor: pointer; font-size: 0.8125rem; margin-right: 0.5rem; }

      .link-btn.danger { color: #f44336; }

      .back { color: var(--accent); text-decoration: none; font-size: 0.8125rem; }

      .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; z-index: 100; }

      .modal { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px; padding: 1.25rem; width: min(28rem, 92vw); }

      .modal h2 { margin: 0 0 1rem; font-size: 1rem; color: var(--text-primary); }

      label { display: block; margin-bottom: 0.75rem; font-size: 0.8125rem; color: var(--text-secondary); }

      input { display: block; width: 100%; margin-top: 0.25rem; padding: 0.5rem; border-radius: 6px; border: 1px solid var(--border); background: var(--bg-sidebar); color: var(--text-primary); }

      .error { color: #f44336; font-size: 0.8125rem; }

      .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }

      .btn { padding: 0.5rem 1rem; background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 6px; color: var(--text-secondary); cursor: pointer; }

    `,

  ],

})

export class AdminCiPageComponent implements OnInit {

  private readonly api = inject(FmApiService);



  items: ConfigurationItem[] = [];

  form: CiForm = this.emptyForm();



  readonly modalOpen = signal(false);

  readonly editingId = signal<string | null>(null);

  readonly saving = signal(false);

  readonly error = signal('');



  ngOnInit(): void {

    this.reload();

  }



  openCreate(): void {

    this.editingId.set(null);

    this.form = this.emptyForm();

    this.error.set('');

    this.modalOpen.set(true);

  }



  openEdit(ci: ConfigurationItem): void {

    this.editingId.set(ci.id);

    this.form = {

      fqdn: ci.fqdn,

      ciType: ci.ciType,

      system: ci.system,

      subsystem: ci.subsystem ?? '',

      tagsText: ci.tags.join(', '),

    };

    this.error.set('');

    this.modalOpen.set(true);

  }



  closeModal(): void {

    this.modalOpen.set(false);

  }



  save(): void {

    const tags = this.form.tagsText

      .split(',')

      .map((t) => t.trim())

      .filter(Boolean);

    const body = {

      fqdn: this.form.fqdn,

      ciType: this.form.ciType,

      system: this.form.system,

      subsystem: this.form.subsystem || undefined,

      tags,

    };

    this.saving.set(true);

    this.error.set('');

    const id = this.editingId();

    const done = {

      next: () => {

        this.saving.set(false);

        this.closeModal();

        this.reload();

      },

      error: () => {

        this.saving.set(false);

        this.error.set('Не удалось сохранить КЕ');

      },

    };

    if (id) {

      this.api.patchConfigurationItem(id, body).subscribe(done);

    } else {

      this.api.createConfigurationItem(body).subscribe(done);

    }

  }



  deleteCi(ci: ConfigurationItem): void {

    if (!confirm(`Удалить КЕ ${ci.fqdn}?`)) return;

    this.api.deleteConfigurationItem(ci.id).subscribe(() => this.reload());

  }



  private reload(): void {

    this.api.listConfigurationItems().subscribe((i) => (this.items = i));

  }



  private emptyForm(): CiForm {

    return { fqdn: '', ciType: '', system: '', subsystem: '', tagsText: '' };

  }

}


