import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getCi, getCiHealth, products } from '../data/mock';
import type { Severity } from '../data/mock';
import { CiSidebar, OperativeCenterPanel } from '../components/health/OperativeCenterPanel';
import { SeverityBadge } from '../components/SeverityBadge';

export function HealthPage() {
  const [selectedProductId, setSelectedProductId] = useState(products[0]?.id ?? '');
  const product = products.find((p) => p.id === selectedProductId) ?? products[0];
  const ciList = product?.ciIds.map((id) => getCi(id)).filter(Boolean) ?? [];
  const worstCi = ciList.reduce((worst, ci) => {
    if (!ci || !worst) return ci;
    const h = getCiHealth(ci.id);
    const w = getCiHealth(worst.id);
    return h.healthPercent < w.healthPercent ? ci : worst;
  }, ciList[0] ?? null);
  const [selectedCiId, setSelectedCiId] = useState(worstCi?.id ?? '');

  if (!product) return null;

  return (
    <div className="space-y-4 h-[calc(100vh-8rem)] flex flex-col">
      <div className="flex items-center justify-between shrink-0">
        <div>
          <h1 className="text-xl font-semibold text-white">Здоровье продуктов</h1>
          <p className="text-sm text-gray-500 mt-1">Оперативный центр · вкладка здоровья КЕ</p>
        </div>
        <div className="flex gap-2">
          <select className="px-3 py-1.5 text-xs bg-[#252830] border border-[#3a3f4b] rounded text-gray-300">
            <option>Все тенанты</option>
            <option>Москва</option>
            <option>СПб</option>
          </select>
          <select className="px-3 py-1.5 text-xs bg-[#252830] border border-[#3a3f4b] rounded text-gray-300">
            <option>Все площадки</option>
            <option>DC1</option>
            <option>DC2</option>
          </select>
        </div>
      </div>

      {/* Компактная тепловая карта */}
      <div className="shrink-0">
        <div className="flex items-center gap-4 text-xs text-gray-400 mb-2">
          <span>Обзор:</span>
          {(['fatal', 'critical', 'major', 'minor', 'warning', 'normal'] as Severity[]).map((s) => (
            <SeverityBadge key={s} severity={s} />
          ))}
        </div>
        <div className="flex gap-2 overflow-x-auto pb-1">
          {products.map((p) => (
            <button
              key={p.id}
              type="button"
              onClick={() => {
                setSelectedProductId(p.id);
                const cis = p.ciIds.map((id) => getCi(id)).filter(Boolean);
                const worst = cis.reduce((w, ci) => {
                  if (!ci || !w) return ci;
                  return getCiHealth(ci.id).healthPercent < getCiHealth(w.id).healthPercent ? ci : w;
                }, cis[0] ?? null);
                if (worst) setSelectedCiId(worst.id);
              }}
              className={`shrink-0 rounded-lg border overflow-hidden transition-all min-w-[140px] ${
                selectedProductId === p.id ? 'border-[#4a9eff] ring-1 ring-[#4a9eff]/40' : 'border-[#3a3f4b] hover:border-[#4a9eff]/50'
              }`}
              style={{ backgroundColor: getHeatColor(p.maxSeverity, p.activeEventCount) }}
            >
              <div className="p-3 text-left">
                <div className="text-xs font-medium text-white truncate">{p.name}</div>
                <div className="flex items-center justify-between mt-2">
                  <SeverityBadge severity={p.maxSeverity} compact />
                  <span className="text-sm font-bold text-white">{p.activeEventCount}</span>
                </div>
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Оперативный центр */}
      <div className="flex flex-1 min-h-0 gap-4 border border-[#3a3f4b] rounded-lg p-4 bg-[#1a1d23]">
        <CiSidebar
          product={product}
          ciList={ciList as NonNullable<typeof ciList[number]>[]}
          selectedCiId={selectedCiId}
          onSelectCi={setSelectedCiId}
        />
        <div className="flex-1 min-w-0 overflow-auto">
          <OperativeCenterPanel
            product={product}
            ciList={ciList as NonNullable<typeof ciList[number]>[]}
            selectedCiId={selectedCiId}
            onSelectCi={setSelectedCiId}
            defaultTab="health"
          />
        </div>
      </div>

      <div className="shrink-0 text-xs text-gray-600">
        <Link to={`/health/${product.id}`} className="text-[#4a9eff] hover:underline">
          Открыть продукт «{product.name}» в отдельном представлении →
        </Link>
      </div>
    </div>
  );
}

function getHeatColor(severity: Severity, count: number): string {
  const base: Record<Severity, string> = {
    fatal: '#dc2626', critical: '#dc2626', major: '#ea580c',
    minor: '#ca8a04', warning: '#eab308', normal: '#3b82f6',
  };
  if (count === 0) return '#252830';
  const opacity = Math.min(0.5 + count * 0.12, 1);
  const hex = base[severity];
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${opacity})`;
}
