import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { ConfigurationItem, ProductHealth } from '../../data/mock';
import { events, getCiHealth } from '../../data/mock';
import { CiHealthTab } from './CiHealthTab';
import { HealthBadge } from './HealthBadge';
import { SeverityBadge } from '../SeverityBadge';
import { StatusBadge } from '../StatusBadge';

type Tab = 'signals' | 'health' | 'params' | 'events';

interface Props {
  product: ProductHealth;
  ciList: ConfigurationItem[];
  selectedCiId: string;
  onSelectCi: (ciId: string) => void;
  defaultTab?: Tab;
}

const tabs: { id: Tab; label: string }[] = [
  { id: 'signals', label: 'Сигналы' },
  { id: 'health', label: 'Здоровье' },
  { id: 'params', label: 'Параметры' },
  { id: 'events', label: 'События' },
];

export function OperativeCenterPanel({ product, ciList, selectedCiId, onSelectCi, defaultTab = 'health' }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>(defaultTab);
  const selectedCi = ciList.find((c) => c.id === selectedCiId) ?? ciList[0];
  const health = selectedCi ? getCiHealth(selectedCi.id) : null;
  const ciEvents = events.filter(
    (e) => e.ciId === selectedCi?.id && e.status !== 'closed' && e.status !== 'archived'
  );
  const totalSignals = health
    ? Object.values(health.signalsBySeverity).reduce((a, b) => a + b, 0)
    : 0;

  if (!selectedCi || !health) {
    return <p className="text-gray-500 text-sm">Выберите КЕ</p>;
  }

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Заголовок оперативного центра */}
      <div className="flex items-start justify-between mb-4">
        <div>
          <div className="text-xs text-gray-500">Оперативный центр · {product.name}</div>
          <h2 className="text-lg font-semibold text-white mt-0.5 font-mono">{selectedCi.fqdn}</h2>
          <div className="text-xs text-gray-500 mt-0.5">{selectedCi.system} · {selectedCi.ciType}</div>
        </div>
        <HealthBadge level={health.currentHealth} percent={health.healthPercent} />
      </div>

      {/* Вкладки */}
      <div className="flex border-b border-[#3a3f4b] mb-4">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-sm border-b-2 -mb-px transition-colors ${
              activeTab === tab.id
                ? 'border-[#4a9eff] text-[#4a9eff]'
                : 'border-transparent text-gray-500 hover:text-gray-300'
            }`}
          >
            {tab.label}
            {tab.id === 'signals' && totalSignals > 0 && (
              <span className="ml-1.5 px-1.5 py-0.5 text-[10px] rounded bg-[#dc2626] text-white">
                {totalSignals}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Контент вкладок */}
      <div className="flex-1 overflow-auto min-h-0">
        {activeTab === 'health' && <CiHealthTab ci={selectedCi} health={health} />}
        {activeTab === 'signals' && (
          <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
            <p className="text-sm text-gray-400 mb-4">
              Активные сигналы по КЕ — {totalSignals} шт.
            </p>
            <div className="space-y-2 mb-4">
              {ciEvents.map((e) => (
                <Link
                  key={e.id}
                  to={`/console/${e.id}`}
                  className="flex items-center gap-2 px-3 py-2 rounded hover:bg-[#3a3f4b]/30 text-sm border border-[#3a3f4b]"
                >
                  <SeverityBadge severity={e.severity} compact />
                  <span className="flex-1 truncate text-gray-300">{e.title}</span>
                  <StatusBadge status={e.status} />
                </Link>
              ))}
              {ciEvents.length === 0 && (
                <p className="text-sm text-gray-500">Нет активных сигналов</p>
              )}
            </div>
            <Link to={`/console?ci=${selectedCi.id}`} className="text-xs text-[#4a9eff] hover:underline">
              Открыть в консоли →
            </Link>
          </div>
        )}
        {activeTab === 'params' && (
          <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
            <h3 className="text-sm font-medium text-white mb-3">Параметры КЕ</h3>
            <dl className="grid grid-cols-2 gap-3 text-sm">
              {[
                ['FQDN', selectedCi.fqdn],
                ['Тип', selectedCi.ciType],
                ['Система', selectedCi.system],
                ['Подсистема', selectedCi.subsystem],
                ['ПО', selectedCi.software],
                ['Тенант', product.tenant],
                ['Площадка', product.site],
              ].map(([k, v]) => (
                <div key={k}>
                  <dt className="text-xs text-gray-500">{k}</dt>
                  <dd className="text-gray-300 mt-0.5">{v}</dd>
                </div>
              ))}
            </dl>
            <div className="mt-4 flex flex-wrap gap-1">
              {[...selectedCi.tags, ...product.tags].map((tag) => (
                <span key={tag} className="px-2 py-0.5 text-xs rounded bg-[#3a3f4b]/50 text-gray-400">{tag}</span>
              ))}
            </div>
          </div>
        )}
        {activeTab === 'events' && (
          <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-medium text-white">События КЕ</h3>
              <Link to={`/console?ci=${selectedCi.id}`} className="text-xs text-[#4a9eff] hover:underline">
                Консоль →
              </Link>
            </div>
            <div className="space-y-1">
              {events.filter((e) => e.ciId === selectedCi.id).slice(0, 10).map((e) => (
                <Link
                  key={e.id}
                  to={`/console/${e.id}`}
                  className="flex items-center gap-2 px-3 py-2 rounded hover:bg-[#3a3f4b]/30 text-sm"
                >
                  <StatusBadge status={e.status} />
                  <SeverityBadge severity={e.severity} compact />
                  <span className="flex-1 truncate text-gray-300">{e.title}</span>
                  <span className="text-xs text-gray-600">{e.createdAt.slice(0, 16)}</span>
                </Link>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

interface CiSidebarProps {
  product: ProductHealth;
  ciList: ConfigurationItem[];
  selectedCiId: string;
  onSelectCi: (ciId: string) => void;
}

export function CiSidebar({ product, ciList, selectedCiId, onSelectCi }: CiSidebarProps) {
  return (
    <div className="w-56 shrink-0 border-r border-[#3a3f4b] pr-4">
      <div className="text-xs text-gray-500 mb-2">{product.name}</div>
      <div className="space-y-1">
        {ciList.map((ci) => {
          const h = getCiHealth(ci.id);
          return (
            <button
              key={ci.id}
              type="button"
              onClick={() => onSelectCi(ci.id)}
              className={`w-full text-left px-2 py-2 rounded text-sm transition-colors ${
                selectedCiId === ci.id
                  ? 'bg-[#4a9eff]/15 border border-[#4a9eff]/40 text-white'
                  : 'hover:bg-[#3a3f4b]/30 text-gray-400 border border-transparent'
              }`}
            >
              <div className="font-mono text-xs truncate">{ci.fqdn}</div>
              <div className="mt-1">
                <HealthBadge level={h.currentHealth} compact percent={h.healthPercent} />
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
