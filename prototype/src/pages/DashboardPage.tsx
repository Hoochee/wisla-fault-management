import { Link } from 'react-router-dom';
import { countBySeverity, eventMaps, events, products } from '../data/mock';
import type { Severity } from '../data/mock';
import { SeverityBadge } from '../components/SeverityBadge';

const severityOrder: Severity[] = ['fatal', 'critical', 'major', 'minor', 'warning', 'normal'];

const severityLinks: Partial<Record<Severity, string>> = {
  critical: '/console?severity=critical',
  major: '/console?severity=major',
  minor: '/console?severity=minor',
  warning: '/console?severity=warning',
};

export function DashboardPage() {
  const counts = countBySeverity(severityOrder);
  const activeEvents = events.filter((e) => e.status !== 'closed' && e.status !== 'archived');

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-white">Оперативный центр</h1>
        <p className="text-sm text-gray-500 mt-1">Сводка активных событий и быстрый доступ</p>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
        {severityOrder.map((sev) => {
          const count = counts[sev];
          const link = severityLinks[sev] ?? '/console';
          return (
            <Link
              key={sev}
              to={link}
              className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4 hover:border-[#4a9eff]/50 transition-colors"
            >
              <SeverityBadge severity={sev} />
              <div className="text-3xl font-bold text-white mt-2">{count}</div>
              <div className="text-xs text-gray-500 mt-1">активных</div>
            </Link>
          );
        })}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-medium text-white">Карты событий</h2>
            <Link to="/admin/search-folders" className="text-xs text-[#4a9eff] hover:underline">Все карты →</Link>
          </div>
          <div className="space-y-2">
            {eventMaps.map((map) => (
              <Link
                key={map.id}
                to={`/console?map=${map.id}`}
                className="flex items-center justify-between px-3 py-2 rounded bg-[#1a1d23] border border-[#3a3f4b] hover:border-[#4a9eff]/50 text-sm"
              >
                <span className="text-gray-300">{map.name}</span>
                <span className="text-xs text-gray-500 font-mono">{map.query}</span>
              </Link>
            ))}
          </div>
        </div>

        <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-sm font-medium text-white">Здоровье продуктов</h2>
            <Link to="/health" className="text-xs text-[#4a9eff] hover:underline">Тепловая карта →</Link>
          </div>
          <div className="grid grid-cols-4 gap-2">
            {products.slice(0, 8).map((p) => (
              <Link
                key={p.id}
                to={`/health/${p.id}`}
                className="aspect-square rounded flex flex-col items-center justify-center p-2 text-center hover:ring-2 hover:ring-[#4a9eff]/50 transition-all"
                style={{
                  backgroundColor: getHeatColor(p.maxSeverity, p.activeEventCount),
                }}
                title={`${p.name}: ${p.activeEventCount} событий`}
              >
                <span className="text-[10px] text-white font-medium leading-tight">{p.name}</span>
                {p.activeEventCount > 0 && (
                  <span className="text-xs text-white/80 mt-1">{p.activeEventCount}</span>
                )}
              </Link>
            ))}
          </div>
        </div>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-medium text-white">Последние события</h2>
          <Link to="/console" className="text-xs text-[#4a9eff] hover:underline">Консоль →</Link>
        </div>
        <div className="space-y-1">
          {activeEvents.slice(0, 5).map((e) => (
            <Link
              key={e.id}
              to={`/console/${e.id}`}
              className="flex items-center gap-3 px-3 py-2 rounded hover:bg-[#3a3f4b]/30 text-sm"
            >
              <SeverityBadge severity={e.severity} compact />
              <span className="flex-1 truncate text-gray-300">{e.title}</span>
              <span className="text-xs text-gray-500 font-mono">{e.nodeFqdn}</span>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}

function getHeatColor(severity: Severity, count: number): string {
  const base: Record<Severity, string> = {
    fatal: '#dc2626',
    critical: '#dc2626',
    major: '#ea580c',
    minor: '#ca8a04',
    warning: '#eab308',
    normal: '#3b82f6',
  };
  if (count === 0) return '#2d3139';
  const opacity = Math.min(0.4 + count * 0.15, 1);
  const hex = base[severity];
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${opacity})`;
}
