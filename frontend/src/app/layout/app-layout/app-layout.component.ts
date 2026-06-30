import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { PushToastService } from '../../core/notifications/push-toast.service';
import { PushToastComponent } from '../push-toast/push-toast.component';

interface NavItem {
  label: string;
  path?: string;
  icon: string;
  postMvp?: boolean;
  children?: { label: string; path: string; postMvp?: boolean }[];
}

const NAVIGATION: NavItem[] = [
  {
    label: 'Обзор',
    icon: '📊',
    children: [
      { label: 'Dashboard', path: '/' },
      { label: 'Здоровье продуктов', path: '/health' },
    ],
  },
  { label: 'Консоль событий', icon: '📋', path: '/console' },
  { label: 'Первичные события', icon: '📄', path: '/events/raw' },
  {
    label: 'Сбор данных',
    icon: '📥',
    children: [{ label: 'Источники', path: '/sources' }],
  },
  {
    label: 'Анализ и обработка',
    icon: '📐',
    children: [
      { label: 'Правила', path: '/rules' },
      { label: 'Downtime', path: '/downtime', postMvp: true },
    ],
  },
  {
    label: 'Администрирование',
    icon: '⚙',
    children: [
      { label: 'Хаб', path: '/admin' },
      { label: 'Пользователи', path: '/admin/users' },
      { label: 'Роли', path: '/admin/roles' },
      { label: 'КЕ', path: '/admin/ci' },
      { label: 'Карты событий', path: '/admin/search-folders' },
    ],
  },
  { label: 'Настройки', icon: '⚙', path: '/settings' },
  { label: 'Отчётность', icon: '📈', path: '/reports', postMvp: true },
];

const FAVORITES = [
  { label: 'Активные события', path: '/console' },
  { label: 'Critical/Major', path: '/console' },
];

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, PushToastComponent],
  templateUrl: './app-layout.component.html',
  styleUrl: './app-layout.component.scss',
})
export class AppLayoutComponent implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly pushToast = inject(PushToastService);

  readonly navigation = NAVIGATION;
  readonly favorites = FAVORITES;
  readonly collapsed = signal(false);
  readonly expanded = signal<Record<string, boolean>>({ Обзор: true });

  readonly user = this.auth.currentUser;

  ngOnInit(): void {
    this.pushToast.start();
  }

  ngOnDestroy(): void {
    this.pushToast.stop();
  }

  toggleCollapsed(): void {
    this.collapsed.update((v) => !v);
  }

  toggleSection(label: string): void {
    this.expanded.update((e) => ({ ...e, [label]: !e[label] }));
  }

  isExpanded(label: string): boolean {
    return !!this.expanded()[label];
  }

  logout(): void {
    this.auth.logout();
  }

  breadcrumbs(): { label: string; path?: string }[] {
    const url = this.router.url.split('?')[0];
    const crumbs: { label: string; path?: string }[] = [{ label: 'WISLA FM', path: '/' }];
    const map: Record<string, string> = {
      '/': 'Dashboard',
      '/health': 'Здоровье продуктов',
      '/events/raw': 'Первичные события',
      '/console': 'Консоль событий',
      '/sources': 'Источники',
      '/rules': 'Правила',
      '/admin': 'Администрирование',
      '/admin/users': 'Пользователи',
      '/admin/roles': 'Роли',
      '/admin/ci': 'Реестр КЕ',
      '/admin/search-folders': 'Карты событий',
      '/settings': 'Настройки',
    };
    for (const [path, label] of Object.entries(map)) {
      if (url === path || (path !== '/' && url.startsWith(path))) {
        crumbs.push({ label });
        break;
      }
    }
    return crumbs;
  }
}
