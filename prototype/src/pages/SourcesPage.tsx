import { Link } from 'react-router-dom';
import { sources } from '../data/mock';
import { StatusBadge } from '../components/StatusBadge';

const typeLabels: Record<string, string> = {
  push_rest: 'Push REST',
  push_snmp_trap: 'SNMP Trap',
  pull_etl: 'Pull ETL',
};

export function SourcesPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Источники</h1>
          <p className="text-sm text-gray-500 mt-1">Адаптеры приёма событий (Monq «Потоки данных»)</p>
        </div>
        <Link to="/sources/new" className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">
          + Создать
        </Link>
      </div>

      <div className="flex gap-2 text-xs">
        {['Все', 'Запущен', 'Остановлен', 'Push', 'Pull'].map((f) => (
          <button key={f} type="button" className={`px-3 py-1 rounded border ${f === 'Все' ? 'border-[#4a9eff] text-[#4a9eff] bg-[#4a9eff]/10' : 'border-[#3a3f4b] text-gray-400 hover:bg-[#3a3f4b]/30'}`}>
            {f}
          </button>
        ))}
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-gray-500 uppercase border-b border-[#3a3f4b]">
              <th className="px-4 py-3">Название</th>
              <th className="px-4 py-3">Тип</th>
              <th className="px-4 py-3">Статус</th>
              <th className="px-4 py-3">Последний успех</th>
              <th className="px-4 py-3">Версия</th>
              <th className="px-4 py-3">API-ключ</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody>
            {sources.map((s) => (
              <tr key={s.id} className="border-b border-[#3a3f4b]/50 hover:bg-[#3a3f4b]/20">
                <td className="px-4 py-3">
                  <Link to={`/sources/${s.id}`} className="text-[#4a9eff] hover:underline">{s.name}</Link>
                </td>
                <td className="px-4 py-3 text-gray-400">{typeLabels[s.type]}</td>
                <td className="px-4 py-3"><StatusBadge status={s.status} type="source" /></td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{formatDate(s.lastSuccessAt)}</td>
                <td className="px-4 py-3 text-gray-400">{s.adapterVersion}</td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{s.apiKey}</td>
                <td className="px-4 py-3">
                  <button type="button" className="text-xs text-gray-400 hover:text-white">
                    {s.status === 'active' ? '⏸' : '▶'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
