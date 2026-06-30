import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getCi, getCiHealth, getProductById } from '../data/mock';
import { CiSidebar, OperativeCenterPanel } from '../components/health/OperativeCenterPanel';
import { HealthBadge } from '../components/health/HealthBadge';
import { SeverityBadge } from '../components/SeverityBadge';

export function HealthProductPage() {
  const { productId } = useParams();
  const product = getProductById(productId ?? '');
  const ciList = product?.ciIds.map((id) => getCi(id)).filter(Boolean) ?? [];
  const worstCi = ciList.reduce((worst, ci) => {
    if (!ci || !worst) return ci;
    const h = getCiHealth(ci.id);
    const w = getCiHealth(worst.id);
    return h.healthPercent < w.healthPercent ? ci : worst;
  }, ciList[0] ?? null);
  const [selectedCiId, setSelectedCiId] = useState(worstCi?.id ?? '');

  if (!product) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-400">Продукт не найден</p>
        <Link to="/health" className="text-[#4a9eff] text-sm mt-2 inline-block hover:underline">← Назад</Link>
      </div>
    );
  }

  const typedCiList = ciList as NonNullable<typeof ciList[number]>[];

  return (
    <div className="space-y-4 h-[calc(100vh-8rem)] flex flex-col">
      <div className="flex items-start justify-between shrink-0">
        <div>
          <Link to="/health" className="text-xs text-[#4a9eff] hover:underline">← Здоровье продуктов</Link>
          <h1 className="text-xl font-semibold text-white mt-1">{product.name}</h1>
          <p className="text-sm text-gray-500">{product.tenant} · {product.site}</p>
        </div>
        <div className="text-right">
          <SeverityBadge severity={product.maxSeverity} />
          <div className="text-2xl font-bold text-white mt-1">{product.activeEventCount}</div>
          <div className="text-xs text-gray-500">активных событий</div>
        </div>
      </div>

      {/* Сводка по КЕ продукта */}
      <div className="flex gap-2 shrink-0 overflow-x-auto pb-1">
        {typedCiList.map((ci) => {
          const h = getCiHealth(ci.id);
          return (
            <button
              key={ci.id}
              type="button"
              onClick={() => setSelectedCiId(ci.id)}
              className={`shrink-0 px-3 py-2 rounded border text-left transition-colors ${
                selectedCiId === ci.id
                  ? 'border-[#4a9eff] bg-[#4a9eff]/10'
                  : 'border-[#3a3f4b] bg-[#252830] hover:border-[#4a9eff]/50'
              }`}
            >
              <div className="font-mono text-xs text-gray-300 truncate max-w-[180px]">{ci.fqdn}</div>
              <div className="mt-1">
                <HealthBadge level={h.currentHealth} compact percent={h.healthPercent} />
              </div>
            </button>
          );
        })}
      </div>

      <div className="flex flex-1 min-h-0 gap-4 border border-[#3a3f4b] rounded-lg p-4 bg-[#1a1d23]">
        <CiSidebar
          product={product}
          ciList={typedCiList}
          selectedCiId={selectedCiId}
          onSelectCi={setSelectedCiId}
        />
        <div className="flex-1 min-w-0 overflow-auto">
          <OperativeCenterPanel
            product={product}
            ciList={typedCiList}
            selectedCiId={selectedCiId}
            onSelectCi={setSelectedCiId}
            defaultTab="health"
          />
        </div>
      </div>

      <div className="shrink-0 flex flex-wrap gap-1">
        {product.tags.map((tag) => (
          <span key={tag} className="px-2 py-0.5 text-xs rounded bg-[#3a3f4b]/50 text-gray-400">{tag}</span>
        ))}
      </div>
    </div>
  );
}
