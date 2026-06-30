import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { AppLayoutComponent } from './layout/app-layout/app-layout.component';
import { LoginPageComponent } from './pages/login/login-page.component';
import { DashboardPageComponent } from './pages/dashboard/dashboard-page.component';
import { HealthPageComponent } from './pages/health/health-page.component';
import { HealthProductPageComponent } from './pages/health/health-product-page.component';
import { RawEventsPageComponent } from './pages/raw-events/raw-events-page.component';
import { ConsolePageComponent } from './pages/console/console-page.component';
import { EventCardPageComponent } from './pages/console/event-card-page.component';
import { SourcesPageComponent } from './pages/sources/sources-page.component';
import { SourceNewPageComponent } from './pages/sources/source-new-page.component';
import { SourceEditPageComponent } from './pages/sources/source-edit-page.component';
import { RulesPageComponent } from './pages/rules/rules-page.component';
import { RuleNewPageComponent } from './pages/rules/rule-new-page.component';
import { RuleEditPageComponent } from './pages/rules/rule-edit-page.component';
import { AdminPageComponent } from './pages/admin/admin-page.component';
import { AdminUsersPageComponent } from './pages/admin/admin-users-page.component';
import { AdminRolesPageComponent } from './pages/admin/admin-roles-page.component';
import { AdminCiPageComponent } from './pages/admin/admin-ci-page.component';
import { AdminSearchFoldersPageComponent } from './pages/admin/admin-search-folders-page.component';
import { SettingsPageComponent } from './pages/settings/settings-page.component';
import { PostMvpPlaceholderComponent } from './pages/post-mvp/post-mvp-placeholder.component';

export const routes: Routes = [
  { path: 'login', component: LoginPageComponent },
  {
    path: '',
    component: AppLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: DashboardPageComponent },
      { path: 'health', component: HealthPageComponent },
      { path: 'health/:productId', component: HealthProductPageComponent },
      { path: 'events/raw', component: RawEventsPageComponent },
      { path: 'console', component: ConsolePageComponent },
      { path: 'console/:eventId', component: EventCardPageComponent },
      { path: 'sources', component: SourcesPageComponent },
      { path: 'sources/new', component: SourceNewPageComponent },
      { path: 'sources/:id', component: SourceEditPageComponent },
      { path: 'rules', component: RulesPageComponent },
      { path: 'rules/new', component: RuleNewPageComponent },
      { path: 'rules/:id', component: RuleEditPageComponent },
      { path: 'admin', component: AdminPageComponent },
      { path: 'admin/users', component: AdminUsersPageComponent },
      { path: 'admin/roles', component: AdminRolesPageComponent },
      { path: 'admin/ci', component: AdminCiPageComponent },
      { path: 'admin/search-folders', component: AdminSearchFoldersPageComponent },
      { path: 'admin/consoles', component: PostMvpPlaceholderComponent, data: { title: 'Консоли доступа' } },
      { path: 'settings', component: SettingsPageComponent },
      { path: 'settings/notifications', component: PostMvpPlaceholderComponent, data: { title: 'Оповещения' } },
      { path: 'settings/integrations', component: PostMvpPlaceholderComponent, data: { title: 'Интеграции' } },
      { path: 'downtime', component: PostMvpPlaceholderComponent, data: { title: 'Downtime' } },
      { path: 'downtime/new', component: PostMvpPlaceholderComponent, data: { title: 'Новый DT' } },
      { path: 'downtime/:id', component: PostMvpPlaceholderComponent, data: { title: 'Карточка DT' } },
      { path: 'reports', component: PostMvpPlaceholderComponent, data: { title: 'Отчётность' } },
    ],
  },
  { path: '**', redirectTo: '' },
];
