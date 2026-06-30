import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FmApiService } from '../../core/api/fm-api.service';
import { EventMap } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-admin-search-folders',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent],
  template: `
    <app-page-header title="Карты событий" subtitle="Преднастроенные фильтры консоли" actionLabel="+ Создать" actionLink="/console" />
    <table class="data-table">
      <thead><tr><th>Название</th><th>Запрос</th><th>Тип</th><th>Колонки</th></tr></thead>
      <tbody>
        @for (m of maps; track m.id) {
          <tr>
            <td><a [routerLink]="['/console']" [queryParams]="{ mapId: m.id }">{{ m.name }}</a></td>
            <td class="mono">{{ m.query }}</td>
            <td>{{ m.isSystem ? 'Системная' : m.isPersonal ? 'Личная' : 'Общая' }}</td>
            <td>{{ m.columns.join(', ') }}</td>
          </tr>
        }
      </tbody>
    </table>
    <a routerLink="/admin" class="back">← Администрирование</a>
  `,
  styles: [
    `
      .data-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; margin-bottom: 1rem; }
      .data-table th, .data-table td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); text-align: left; }
      .mono { font-family: var(--font-mono); font-size: 0.75rem; }
      a { color: var(--accent); text-decoration: none; }
      .back { font-size: 0.8125rem; }
    `,
  ],
})
export class AdminSearchFoldersPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  maps: EventMap[] = [];

  ngOnInit(): void {
    this.api.listEventMaps().subscribe((m) => (this.maps = m));
  }
}
