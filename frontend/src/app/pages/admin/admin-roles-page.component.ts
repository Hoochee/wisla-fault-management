import { Component, inject, OnInit, signal } from '@angular/core';

import { RouterLink } from '@angular/router';

import { FormsModule } from '@angular/forms';

import { FmApiService } from '../../core/api/fm-api.service';

import { Role } from '../../core/api/api.models';

import { PageHeaderComponent } from '../../shared/page-header/page-header.component';



interface RoleForm {

  name: string;

  description: string;

  permissionsText: string;

}



@Component({

  selector: 'app-admin-roles',

  standalone: true,

  imports: [RouterLink, FormsModule, PageHeaderComponent],

  template: `

    <div class="page-top">

      <app-page-header title="Роли и полномочия" subtitle="Матрица доступа" />

      <button type="button" class="btn-primary" (click)="openCreate()">+ Добавить</button>

    </div>

    <table class="data-table">

      <thead><tr><th>Роль</th><th>Описание</th><th>Permissions</th><th></th></tr></thead>

      <tbody>

        @for (r of roles; track r.id) {

          <tr>

            <td><strong>{{ r.name }}</strong></td>

            <td>{{ r.description }}</td>

            <td class="perms">{{ r.permissions.join(', ') }}</td>

            <td><button type="button" class="link-btn" (click)="openEdit(r)">Изменить</button></td>

          </tr>

        }

      </tbody>

    </table>

    <a routerLink="/admin" class="back">← Администрирование</a>



    @if (modalOpen()) {

      <div class="overlay" (click)="closeModal()">

        <div class="modal" (click)="$event.stopPropagation()">

          <h2>{{ editingId() ? 'Редактирование роли' : 'Новая роль' }}</h2>

          <form (ngSubmit)="save()">

            <label>Название<input [(ngModel)]="form.name" name="name" required /></label>

            <label>Описание<textarea [(ngModel)]="form.description" name="description" rows="2"></textarea></label>

            <label>

              Полномочия (через запятую)

              <input [(ngModel)]="form.permissionsText" name="permissionsText" placeholder="events.read, admin.users" required />

            </label>

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

      .data-table th, .data-table td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); text-align: left; vertical-align: top; }

      .perms { font-size: 0.75rem; color: var(--text-muted); font-family: var(--font-mono); }

      .link-btn { background: none; border: none; color: var(--accent); cursor: pointer; font-size: 0.8125rem; }

      .back { color: var(--accent); text-decoration: none; font-size: 0.8125rem; }

      .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; z-index: 100; }

      .modal { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px; padding: 1.25rem; width: min(28rem, 92vw); }

      .modal h2 { margin: 0 0 1rem; font-size: 1rem; color: var(--text-primary); }

      label { display: block; margin-bottom: 0.75rem; font-size: 0.8125rem; color: var(--text-secondary); }

      input, textarea { display: block; width: 100%; margin-top: 0.25rem; padding: 0.5rem; border-radius: 6px; border: 1px solid var(--border); background: var(--bg-sidebar); color: var(--text-primary); }

      .error { color: #f44336; font-size: 0.8125rem; }

      .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }

      .btn { padding: 0.5rem 1rem; background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 6px; color: var(--text-secondary); cursor: pointer; }

    `,

  ],

})

export class AdminRolesPageComponent implements OnInit {

  private readonly api = inject(FmApiService);



  roles: Role[] = [];

  form: RoleForm = this.emptyForm();



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



  openEdit(r: Role): void {

    this.editingId.set(r.id);

    this.form = {

      name: r.name,

      description: r.description,

      permissionsText: r.permissions.join(', '),

    };

    this.error.set('');

    this.modalOpen.set(true);

  }



  closeModal(): void {

    this.modalOpen.set(false);

  }



  save(): void {

    const permissions = this.form.permissionsText

      .split(',')

      .map((p) => p.trim())

      .filter(Boolean);

    if (!permissions.length) {

      this.error.set('Укажите хотя бы одно полномочие');

      return;

    }

    this.saving.set(true);

    this.error.set('');

    const id = this.editingId();

    const body = { name: this.form.name, description: this.form.description, permissions };

    const done = {

      next: () => {

        this.saving.set(false);

        this.closeModal();

        this.reload();

      },

      error: () => {

        this.saving.set(false);

        this.error.set('Не удалось сохранить роль');

      },

    };

    if (id) {

      this.api.patchRole(id, body).subscribe(done);

    } else {

      this.api.createRole(body).subscribe(done);

    }

  }



  private reload(): void {

    this.api.listRoles().subscribe((r) => (this.roles = r));

  }



  private emptyForm(): RoleForm {

    return { name: '', description: '', permissionsText: '' };

  }

}


