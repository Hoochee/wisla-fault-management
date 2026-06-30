import { PostMvpPlaceholder } from '../components/PostMvpPlaceholder';

export function DowntimePage() {
  return <PostMvpPlaceholder title="Downtime (окна обслуживания)" />;
}

export function ReportsPage() {
  return <PostMvpPlaceholder title="Отчётность" description="KPI специалистов, экспорт CSV/Excel — post-MVP" />;
}

export function AdminConsolesPage() {
  return <PostMvpPlaceholder title="Консоли доступа" description="Разграничение консолей по тенанту/группе/роли — post-MVP" />;
}

export function SettingsNotificationsPage() {
  return <PostMvpPlaceholder title="Оповещения" description="Правила email/Telegram, графики дежурств — post-MVP" />;
}

export function SettingsIntegrationsPage() {
  return <PostMvpPlaceholder title="Интеграции" description="WISLA API, ITSM endpoints — post-MVP" />;
}
