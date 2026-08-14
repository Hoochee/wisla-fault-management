import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent],
  template: `
    <app-page-header title="Администрирование" subtitle="Управление пользователями, ролями и конфигурацией" />
    <div class="hub-grid">
      @for (item of items; track item.path) {
        <a [routerLink]="item.path" class="hub-card">
          <span class="icon">{{ item.icon }}</span>
          <span class="title">{{ item.title }}</span>
          <span class="desc">{{ item.desc }}</span>
        </a>
      }
    </div>
  `,
  styles: [
    `
      .hub-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 1rem; }
      .hub-card { display: flex; flex-direction: column; gap: 0.5rem; padding: 1.25rem; background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; text-decoration: none; transition: border-color 0.15s; }
      .hub-card:hover { border-color: var(--accent); }
      .icon { font-size: 1.5rem; }
      .title { color: var(--text-primary); font-weight: 500; font-size: 0.9375rem; }
      .desc { color: var(--text-muted); font-size: 0.75rem; }
    `,
  ],
})
export class AdminPageComponent {
  readonly items = [
    { path: '/admin/users', icon: '👤', title: 'Пользователи', desc: 'CRUD, роли, команды' },
    { path: '/admin/roles', icon: '🔐', title: 'Роли и полномочия', desc: 'Матрица permissions' },
    { path: '/admin/ci', icon: '🖥', title: 'Реестр КЕ', desc: 'Конфигурационные элементы' },
    { path: '/admin/search-folders', icon: '🗂', title: 'Карты событий', desc: 'Сохранённые фильтры консоли' },
  ];
}
