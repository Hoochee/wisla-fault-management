import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getSourceName, getUserName } from '../data/mock';
import type { Event } from '../data/mock';
import { useEventStore } from '../context/EventStoreContext';
import { DataTable } from '../components/DataTable';
import { EventDetailPanel } from '../components/EventDetailPanel';
import { EventMapsSidebar } from '../components/EventMapsSidebar';
import { HistogramPlaceholder } from '../components/HistogramPlaceholder';
import { QueryBar } from '../components/QueryBar';
import { SeverityBadge } from '../components/SeverityBadge';
import { StatusBadge } from '../components/StatusBadge';

export function ConsolePage() {
  const { events, getEventById } = useEventStore();
  const [selectedMap, setSelectedMap] = useState('map-active');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const navigate = useNavigate();

  const selectedEvent = selectedId ? getEventById(selectedId) ?? null : null;

  const filteredEvents = useMemo(() => events.filter((e) => {
    if (selectedMap === 'map-active') return e.status !== 'closed' && e.status !== 'archived';
    if (selectedMap === 'map-closed') return e.status === 'closed';
    return true;
  }), [events, selectedMap]);

  const columns = [
    { key: 'status', header: 'Статус', render: (e: Event) => <StatusBadge status={e.status} /> },
    { key: 'severity', header: 'Критичность', render: (e: Event) => <SeverityBadge severity={e.severity} /> },
    { key: 'title', header: 'Заголовок', width: '280px', render: (e: Event) => <span className="text-gray-300">{e.title}</span> },
    { key: 'fqdn', header: 'КЕ/FQDN', render: (e: Event) => <span className="font-mono text-xs text-gray-400">{e.nodeFqdn}</span> },
    { key: 'system', header: 'Система', render: (e: Event) => <span className="text-gray-400">{e.systemName}</span> },
    { key: 'source', header: 'Источник', render: (e: Event) => <span className="text-gray-400 text-xs">{getSourceName(e.sourceId)}</span> },
    { key: 'created', header: 'Создано', render: (e: Event) => <span className="font-mono text-xs text-gray-500">{formatDate(e.createdAt)}</span> },
    { key: 'assigned', header: 'Исполнитель', render: (e: Event) => <span className="text-gray-400 text-xs">{getUserName(e.assignedUserId)}</span> },
    { key: 'repeat', header: 'Повторы', render: (e: Event) => <span className="text-gray-400">{e.repeatCount}</span> },
  ];

  return (
    <div className="flex flex-col h-[calc(100vh-5rem)] -m-4">
      <div className="px-4 py-3 border-b border-[#3a3f4b] bg-[#252830] flex items-center justify-between">
        <div>
          <h1 className="text-lg font-semibold text-white">Консоль событий</h1>
          <p className="text-xs text-gray-500">Обработанные события FM · polling 60 с</p>
        </div>
        <select className="px-2 py-1 text-xs bg-[#1a1d23] border border-[#3a3f4b] rounded text-gray-300">
          <option>Обновление: 60 с</option>
          <option>5 с</option>
          <option>5 мин</option>
        </select>
      </div>

      <div className="flex flex-1 min-h-0">
        <EventMapsSidebar selectedId={selectedMap} onSelect={setSelectedMap} />

        <div className="flex-1 flex flex-col min-w-0">
          <div className="px-4 py-2 border-b border-[#3a3f4b] bg-[#252830]">
            <QueryBar />
          </div>

          <div className="px-4 py-2 border-b border-[#3a3f4b]">
            <HistogramPlaceholder />
          </div>

          <div className="flex items-center justify-between px-4 py-2 border-b border-[#3a3f4b] text-xs text-gray-400">
            <span>{filteredEvents.length} записей</span>
            <div className="flex gap-2">
              <button type="button" className="px-2 py-1 border border-[#3a3f4b] rounded bg-[#4a9eff]/10 text-[#4a9eff]">Таблица</button>
              <button type="button" className="px-2 py-1 border border-[#3a3f4b] rounded hover:bg-[#3a3f4b]/30">Список</button>
              <button type="button" className="px-2 py-1 border border-[#3a3f4b] rounded hover:bg-[#3a3f4b]/30">⚙ Колонки</button>
            </div>
          </div>

          <div className={`overflow-auto ${selectedEvent ? 'flex-[2]' : 'flex-1'}`}>
            <DataTable
              columns={columns}
              data={filteredEvents}
              selectedId={selectedEvent?.id}
              rowId={(e) => e.id}
              onRowClick={(e) => setSelectedId(e.id)}
              onRowDoubleClick={(e) => navigate(`/console/${e.id}`)}
            />
          </div>

          {selectedEvent && (
            <div className="flex-1 border-t border-[#3a3f4b] p-3 bg-[#1a1d23] overflow-auto min-h-[180px]">
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-gray-500 uppercase tracking-wide">Карточка события</span>
                <button type="button" onClick={() => setSelectedId(null)} className="text-xs text-gray-500 hover:text-white">✕</button>
              </div>
              <EventDetailPanel event={selectedEvent} compact />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
