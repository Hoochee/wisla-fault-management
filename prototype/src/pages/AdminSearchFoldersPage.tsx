import { Link } from 'react-router-dom';
import { eventMaps } from '../data/mock';

export function AdminSearchFoldersPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/admin" className="text-xs text-[#4a9eff] hover:underline">← Администрирование</Link>
          <h1 className="text-xl font-semibold text-white mt-1">Карты событий</h1>
          <p className="text-sm text-gray-500 mt-1">Преднастроенные фильтры и колонки консоли</p>
        </div>
        <button type="button" className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">+ Создать карту</button>
      </div>

      <div className="space-y-3">
        {eventMaps.map((map) => (
          <div key={map.id} className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
            <div className="flex items-start justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h2 className={`text-sm font-medium ${map.unsaved ? 'text-amber-400' : 'text-white'}`}>
                    {map.name}{map.unsaved && ' *'}
                  </h2>
                  {map.isSystem && <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-300">системная</span>}
                  {map.isPersonal && <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-500/20 text-purple-300">личная</span>}
                </div>
                <p className="text-xs font-mono text-gray-500 mt-1">{map.query}</p>
              </div>
              <Link to={`/console?map=${map.id}`} className="text-xs text-[#4a9eff] hover:underline">Открыть в консоли →</Link>
            </div>
            <div className="mt-3 flex flex-wrap gap-1">
              {map.columns.map((col) => (
                <span key={col} className="px-2 py-0.5 text-[10px] rounded bg-[#1a1d23] border border-[#3a3f4b] text-gray-400">{col}</span>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
