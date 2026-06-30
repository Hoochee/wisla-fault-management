import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { CiHealthProfile, ConfigurationItem, InfluenceType, Severity } from '../../data/mock';
import { HealthBadge } from './HealthBadge';
import { HealthTimelineChart } from './HealthTimelineChart';
import { SeverityBadge } from '../SeverityBadge';

const influenceLabels: Record<InfluenceType, string> = {
  combo: 'Комбинированное',
  weighted: 'Взвешенное',
  critical: 'Критическое',
};

const influenceIcons: Record<InfluenceType, string> = {
  combo: '⊕',
  weighted: '⚖',
  critical: '⚠',
};

interface Props {
  ci: ConfigurationItem;
  health: CiHealthProfile;
}

export function CiHealthTab({ ci, health }: Props) {
  const [rcaOpen, setRcaOpen] = useState(false);
  const [damageThreshold, setDamageThreshold] = useState(30);
  const totalSignals = Object.values(health.signalsBySeverity).reduce((a, b) => a + b, 0);

  const rcaRows = [
    ...health.components.map((c) => ({
      name: c.name,
      type: 'Компонент',
      damage: c.damageFromComponents + c.damageFromInfluence,
      health: c.health,
      percent: c.healthPercent,
    })),
    ...health.dependents.map((d) => ({
      name: d.fqdn,
      type: 'Влияние',
      damage: 100 - d.healthPercent,
      health: d.health,
      percent: d.healthPercent,
    })),
  ].filter((r) => r.damage >= damageThreshold).sort((a, b) => b.damage - a.damage);

  return (
    <div className="space-y-4">
      {/* Текущее здоровье КЕ */}
      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-4">
            <div
              className="w-16 h-16 rounded-lg flex items-center justify-center text-2xl font-bold border-2"
              style={{
                borderColor: health.currentHealth === 'ok' ? '#22c55e'
                  : health.currentHealth === 'warning' ? '#eab308'
                  : health.currentHealth === 'major' ? '#ea580c'
                  : '#dc2626',
                backgroundColor: `${health.currentHealth === 'ok' ? '#22c55e' : health.currentHealth === 'warning' ? '#eab308' : health.currentHealth === 'major' ? '#ea580c' : '#dc2626'}20`,
                color: health.currentHealth === 'ok' ? '#22c55e' : health.currentHealth === 'warning' ? '#eab308' : health.currentHealth === 'major' ? '#ea580c' : '#dc2626',
              }}
            >
              {health.healthPercent}%
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">Текущее здоровье КЕ</div>
              <HealthBadge level={health.currentHealth} percent={health.healthPercent} />
              <div className="font-mono text-sm text-gray-300 mt-2">{ci.fqdn}</div>
              <div className="text-xs text-gray-500">{ci.system} · {ci.ciType}</div>
            </div>
          </div>
          <div className="flex gap-6 text-sm">
            <div>
              <div className="text-xs text-gray-500 mb-1">Мин. за сутки</div>
              <HealthBadge level={health.minToday} compact percent={health.minTodayPercent} />
            </div>
            <div>
              <div className="text-xs text-gray-500 mb-1">Макс. за сутки</div>
              <HealthBadge level={health.maxToday} compact percent={health.maxTodayPercent} />
            </div>
            <button
              type="button"
              onClick={() => setRcaOpen(!rcaOpen)}
              className={`px-3 py-1.5 text-xs rounded border transition-colors ${
                rcaOpen
                  ? 'bg-[#4a9eff]/20 border-[#4a9eff] text-[#4a9eff]'
                  : 'bg-[#1a1d23] border-[#3a3f4b] text-gray-300 hover:border-[#4a9eff]/50'
              }`}
            >
              RCA
            </button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-4">
        {/* Компоненты */}
        <div className="xl:col-span-2 bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
          <h3 className="text-sm font-medium text-white mb-3">Здоровье компонентов</h3>
          <table className="w-full text-sm">
            <thead>
              <tr className="text-xs text-gray-500 border-b border-[#3a3f4b]">
                <th className="text-left py-2 font-normal">Компонент</th>
                <th className="text-left py-2 font-normal">Здоровье</th>
                <th className="text-right py-2 font-normal">Ущерб от компонентов</th>
                <th className="text-right py-2 font-normal">Ущерб от влияния</th>
              </tr>
            </thead>
            <tbody>
              {health.components.map((c) => (
                <tr key={c.id} className="border-b border-[#3a3f4b]/50 hover:bg-[#3a3f4b]/20">
                  <td className="py-2.5 text-gray-300">{c.name}</td>
                  <td className="py-2.5">
                    <HealthBadge level={c.health} compact percent={c.healthPercent} />
                  </td>
                  <td className="py-2.5 text-right text-gray-400">{c.damageFromComponents}%</td>
                  <td className="py-2.5 text-right text-gray-400">{c.damageFromInfluence}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Сигналы */}
        <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-medium text-white">Сигналы</h3>
            <span className="text-lg font-bold text-white">{totalSignals}</span>
          </div>
          <div className="space-y-2">
            {(['fatal', 'critical', 'major', 'minor', 'warning', 'normal'] as Severity[]).map((s) => (
              <div key={s} className="flex items-center justify-between text-sm">
                <SeverityBadge severity={s} compact />
                <span className="text-gray-400">{health.signalsBySeverity[s]}</span>
              </div>
            ))}
          </div>
          <Link
            to={`/console?ci=${ci.id}`}
            className="mt-4 block text-center text-xs text-[#4a9eff] hover:underline"
          >
            Перейти к сигналам →
          </Link>
        </div>
      </div>

      {/* Зависимые КЕ */}
      {health.dependents.length > 0 && (
        <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
          <h3 className="text-sm font-medium text-white mb-3">Зависимые КЕ (влияние)</h3>
          <div className="space-y-2">
            {health.dependents.map((d) => (
              <div
                key={d.id}
                className="flex items-center gap-3 px-3 py-2 bg-[#1a1d23] rounded border border-[#3a3f4b] text-sm"
              >
                <span className="text-base" title={influenceLabels[d.influenceType]}>
                  {influenceIcons[d.influenceType]}
                </span>
                <div className="flex-1 min-w-0">
                  <div className="font-mono text-gray-300 truncate">{d.fqdn}</div>
                  {d.componentName && (
                    <div className="text-xs text-gray-500">Компонент: {d.componentName}</div>
                  )}
                </div>
                <span className="text-xs text-gray-500">{influenceLabels[d.influenceType]}</span>
                <HealthBadge level={d.health} compact percent={d.healthPercent} />
              </div>
            ))}
          </div>
        </div>
      )}

      <HealthTimelineChart timeline={health.timeline} />

      {/* RCA панель снизу */}
      {rcaOpen && (
        <div className="fixed inset-x-0 bottom-0 z-40 bg-[#1a1d23] border-t border-[#3a3f4b] shadow-2xl max-h-[50vh] flex flex-col">
          <div className="flex items-center justify-between px-4 py-3 border-b border-[#3a3f4b]">
            <h3 className="text-sm font-medium text-white">Root Cause Analysis (RCA)</h3>
            <button
              type="button"
              onClick={() => setRcaOpen(false)}
              className="text-gray-500 hover:text-white text-lg leading-none"
            >
              ×
            </button>
          </div>
          <div className="px-4 py-3 flex items-center gap-4 border-b border-[#3a3f4b]">
            <label className="text-xs text-gray-400">
              Порог ущерба (%)
              <input
                type="number"
                min={0}
                max={100}
                value={damageThreshold}
                onChange={(e) => setDamageThreshold(Number(e.target.value))}
                className="ml-2 w-16 px-2 py-1 bg-[#252830] border border-[#3a3f4b] rounded text-white text-xs"
              />
            </label>
            <button
              type="button"
              className="px-3 py-1 text-xs bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]"
            >
              Пересчитать
            </button>
          </div>
          <div className="flex-1 overflow-auto px-4 py-2">
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-[#1a1d23]">
                <tr className="text-xs text-gray-500 border-b border-[#3a3f4b]">
                  <th className="text-left py-2 font-normal">Источник</th>
                  <th className="text-left py-2 font-normal">Тип</th>
                  <th className="text-right py-2 font-normal">Ущерб</th>
                  <th className="text-left py-2 font-normal pl-4">Здоровье</th>
                </tr>
              </thead>
              <tbody>
                {rcaRows.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="py-4 text-center text-gray-500 text-sm">
                      Нет источников с ущербом ≥ {damageThreshold}%
                    </td>
                  </tr>
                ) : (
                  rcaRows.map((r, i) => (
                    <tr key={i} className="border-b border-[#3a3f4b]/50">
                      <td className="py-2 text-gray-300">{r.name}</td>
                      <td className="py-2 text-gray-500">{r.type}</td>
                      <td className="py-2 text-right text-[#dc2626]">{r.damage}%</td>
                      <td className="py-2 pl-4">
                        <HealthBadge level={r.health} compact percent={r.percent} />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
