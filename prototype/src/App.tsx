import { Routes, Route, Navigate } from 'react-router-dom';
import { AppLayout } from './components/AppLayout';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { HealthPage } from './pages/HealthPage';
import { HealthProductPage } from './pages/HealthProductPage';
import { RawEventsPage } from './pages/RawEventsPage';
import { ConsolePage } from './pages/ConsolePage';
import { EventCardPage } from './pages/EventCardPage';
import { SourcesPage } from './pages/SourcesPage';
import { SourceNewPage } from './pages/SourceNewPage';
import { SourceEditPage } from './pages/SourceEditPage';
import { RulesPage } from './pages/RulesPage';
import { RuleNewPage } from './pages/RuleNewPage';
import { RuleEditPage } from './pages/RuleEditPage';
import { AdminPage } from './pages/AdminPage';
import { AdminUsersPage } from './pages/AdminUsersPage';
import { AdminRolesPage } from './pages/AdminRolesPage';
import { AdminCiPage } from './pages/AdminCiPage';
import { AdminSearchFoldersPage } from './pages/AdminSearchFoldersPage';
import { SettingsPage } from './pages/SettingsPage';
import {
  DowntimePage,
  ReportsPage,
  AdminConsolesPage,
  SettingsNotificationsPage,
  SettingsIntegrationsPage,
} from './pages/PostMvpPages';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AppLayout />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/health" element={<HealthPage />} />
        <Route path="/health/:productId" element={<HealthProductPage />} />
        <Route path="/events/raw" element={<RawEventsPage />} />
        <Route path="/console" element={<ConsolePage />} />
        <Route path="/console/:eventId" element={<EventCardPage />} />
        <Route path="/sources" element={<SourcesPage />} />
        <Route path="/sources/new" element={<SourceNewPage />} />
        <Route path="/sources/:id" element={<SourceEditPage />} />
        <Route path="/rules" element={<RulesPage />} />
        <Route path="/rules/new" element={<RuleNewPage />} />
        <Route path="/rules/:id" element={<RuleEditPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/admin/users" element={<AdminUsersPage />} />
        <Route path="/admin/roles" element={<AdminRolesPage />} />
        <Route path="/admin/ci" element={<AdminCiPage />} />
        <Route path="/admin/search-folders" element={<AdminSearchFoldersPage />} />
        <Route path="/admin/consoles" element={<AdminConsolesPage />} />
        <Route path="/downtime" element={<DowntimePage />} />
        <Route path="/downtime/new" element={<DowntimePage />} />
        <Route path="/downtime/:id" element={<DowntimePage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/notifications" element={<SettingsNotificationsPage />} />
        <Route path="/settings/integrations" element={<SettingsIntegrationsPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
