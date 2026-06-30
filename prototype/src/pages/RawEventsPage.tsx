import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { rawEvents, getSourceName, getUserName } from '../data/mock';
import type { Event } from '../data/mock';
import { DataTable } from '../components/DataTable';
import { EventMapsSidebar } from '../components/EventMapsSidebar';
import { HistogramPlaceholder } from '../components/HistogramPlaceholder';
import { QueryBar } from '../components/QueryBar';
import { SeverityBadge } from '../components/SeverityBadge';
import { StatusBadge } from '../components/StatusBadge';

export function RawEventsPage() {
  const [selectedMap, setSelectedMap] = useState('map-active');
  const navigate = useNavigate();

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
      <div className="px-4 py-3 border-b border-[#3a3f4b] bg-[#252830]">
        <h1 className="text-lg font-semibold text-white">Первичные события</h1>
        <p className="text-xs text-gray-500">Сырые события до дедупликации (Monq «События и логи»)</p>
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
            <span>{rawEvents.length} записей · пакет 500</span>
            <div className="flex gap-2">
              <button type="button" className="px-2 py-1 border border-[#3a3f4b] rounded hover:bg-[#3a3f4b]/30">Таблица</button>
              <button type="button" className="px-2 py-1 border border-[#3a3f4b] rounded text-gray-600">Список</button>
              <button type="button" className="px-2 py-1 border border-[#3a3f4b] rounded hover:bg-[#3a3f4b]/30">⚙</button>
            </div>
          </div>

          <div className="flex-1 overflow-auto">
            <DataTable
              columns={columns}
              data={rawEvents}
              rowId={(e) => e.id}
              onRowDoubleClick={(e) => navigate(`/console/${e.id.replace('raw-', '')}`)}
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
