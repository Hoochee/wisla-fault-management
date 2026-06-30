import { Component, inject, OnInit, signal } from '@angular/core';

import { RouterLink } from '@angular/router';

import { FormsModule } from '@angular/forms';

import { FmApiService } from '../../core/api/fm-api.service';

import { Role, User, UserPatchRequest } from '../../core/api/api.models';

import { PageHeaderComponent } from '../../shared/page-header/page-header.component';



interface UserForm {

  login: string;

  fullName: string;

  email: string;

  password: string;

  team: string;

  roleIds: string[];

  active: boolean;

}



@Component({

  selector: 'app-admin-users',

  standalone: true,

  imports: [RouterLink, FormsModule, PageHeaderComponent],

  template: `

    <div class="page-top">

      <app-page-header title="Пользователи" subtitle="Управление учётными записями" />

      <button type="button" class="btn-primary" (click)="openCreate()">+ Добавить</button>

    </div>

    <table class="data-table">

      <thead><tr><th>Логин</th><th>ФИО</th><th>Email</th><th>Команда</th><th>Активен</th><th></th></tr></thead>

      <tbody>

        @for (u of users; track u.id) {

          <tr>

            <td class="mono">{{ u.login }}</td>

            <td>{{ u.fullName }}</td>

            <td>{{ u.email }}</td>

            <td>{{ u.team }}</td>

            <td>{{ u.active ? 'Да' : 'Нет' }}</td>

            <td class="actions">

              <button type="button" class="link-btn" (click)="openEdit(u)">Изменить</button>

              @if (u.active) {

                <button type="button" class="link-btn danger" (click)="deactivate(u)">Деактивировать</button>

              } @else {

                <button type="button" class="link-btn danger" (click)="deleteUser(u)">Удалить</button>

              }

            </td>

          </tr>

        }

      </tbody>

    </table>

    <a routerLink="/admin" class="back">← Администрирование</a>



    @if (modalOpen()) {

      <div class="overlay" (click)="closeModal()">

        <div class="modal" (click)="$event.stopPropagation()">

          <h2>{{ editingId() ? 'Редактирование пользователя' : 'Новый пользователь' }}</h2>

          <form (ngSubmit)="save()">

            @if (!editingId()) {

              <label>Логин<input [(ngModel)]="form.login" name="login" required /></label>

            }

            <label>ФИО<input [(ngModel)]="form.fullName" name="fullName" required /></label>

            <label>Email<input type="email" [(ngModel)]="form.email" name="email" required /></label>

            <label>Пароль<input type="password" [(ngModel)]="form.password" name="password" [required]="!editingId()" placeholder="{{ editingId() ? 'оставьте пустым, чтобы не менять' : '' }}" /></label>

            <label>Команда<input [(ngModel)]="form.team" name="team" /></label>

            <label>

              Роли

              <select multiple [(ngModel)]="form.roleIds" name="roleIds" required size="4">

                @for (r of roles; track r.id) {

                  <option [value]="r.id">{{ r.name }}</option>

                }

              </select>

            </label>

            <label class="checkbox"><input type="checkbox" [(ngModel)]="form.active" name="active" /> Активен</label>

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

      .mono { font-family: var(--font-mono); }

      .actions { white-space: nowrap; }

      .link-btn { background: none; border: none; color: var(--accent); cursor: pointer; font-size: 0.8125rem; margin-right: 0.5rem; }

      .link-btn.danger { color: #f44336; }

      .back { color: var(--accent); text-decoration: none; font-size: 0.8125rem; }

      .overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; z-index: 100; }

      .modal { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px; padding: 1.25rem; width: min(28rem, 92vw); max-height: 90vh; overflow: auto; }

      .modal h2 { margin: 0 0 1rem; font-size: 1rem; color: #fff; }

      label { display: block; margin-bottom: 0.75rem; font-size: 0.8125rem; color: var(--text-secondary); }

      input, select { display: block; width: 100%; margin-top: 0.25rem; padding: 0.5rem; border-radius: 6px; border: 1px solid var(--border); background: var(--bg-sidebar); color: var(--text-primary); }

      .checkbox { display: flex; align-items: center; gap: 0.5rem; }

      .checkbox input { width: auto; margin: 0; }

      .error { color: #f44336; font-size: 0.8125rem; }

      .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }

      .btn { padding: 0.5rem 1rem; background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 6px; color: var(--text-secondary); cursor: pointer; }

    `,

  ],

})

export class AdminUsersPageComponent implements OnInit {

  private readonly api = inject(FmApiService);



  users: User[] = [];

  roles: Role[] = [];

  form: UserForm = this.emptyForm();



  readonly modalOpen = signal(false);

  readonly editingId = signal<string | null>(null);

  readonly saving = signal(false);

  readonly error = signal('');



  ngOnInit(): void {

    this.reload();

    this.api.listRoles().subscribe((r) => (this.roles = r));

  }



  openCreate(): void {

    this.editingId.set(null);

    this.form = this.emptyForm();

    this.error.set('');

    this.modalOpen.set(true);

  }



  openEdit(u: User): void {

    this.editingId.set(u.id);

    this.form = {

      login: u.login,

      fullName: u.fullName,

      email: u.email,

      password: '',

      team: u.team ?? '',

      roleIds: [...u.roleIds],

      active: u.active,

    };

    this.error.set('');

    this.modalOpen.set(true);

  }



  closeModal(): void {

    this.modalOpen.set(false);

  }



  save(): void {

    this.saving.set(true);

    this.error.set('');

    const id = this.editingId();

    if (id) {

      const patch: UserPatchRequest = {

        fullName: this.form.fullName,

        email: this.form.email,

        team: this.form.team,

        roleIds: this.form.roleIds,

        active: this.form.active,

      };

      if (this.form.password) patch.password = this.form.password;

      this.api.patchUser(id, patch).subscribe({

        next: () => {

          this.saving.set(false);

          this.closeModal();

          this.reload();

        },

        error: () => {

          this.saving.set(false);

          this.error.set('Не удалось сохранить пользователя');

        },

      });

    } else {

      this.api

        .createUser({

          login: this.form.login,

          fullName: this.form.fullName,

          email: this.form.email,

          password: this.form.password,

          team: this.form.team,

          roleIds: this.form.roleIds,

          active: this.form.active,

        })

        .subscribe({

          next: () => {

            this.saving.set(false);

            this.closeModal();

            this.reload();

          },

          error: () => {

            this.saving.set(false);

            this.error.set('Не удалось создать пользователя');

          },

        });

    }

  }



  deactivate(u: User): void {

    if (!confirm(`Деактивировать пользователя ${u.login}?`)) return;

    this.api.patchUser(u.id, { active: false }).subscribe(() => this.reload());

  }



  deleteUser(u: User): void {

    if (!confirm(`Удалить пользователя ${u.login}?`)) return;

    this.api.deleteUser(u.id).subscribe(() => this.reload());

  }



  private reload(): void {

    this.api.listUsers().subscribe((u) => (this.users = u));

  }



  private emptyForm(): UserForm {

    return { login: '', fullName: '', email: '', password: '', team: '', roleIds: [], active: true };

  }

}


