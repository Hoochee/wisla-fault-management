import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getCi, getSourceName, getUserName } from '../data/mock';
import { useEventStore } from '../context/EventStoreContext';
import { EventActionButtons } from '../components/EventActionButtons';
import { SeverityBadge } from '../components/SeverityBadge';
import { StatusBadge } from '../components/StatusBadge';

type Tab = 'attributes' | 'journal' | 'relations';

export function EventCardPage() {
  const { eventId } = useParams();
  const { getEventById, getActionLogsForEvent } = useEventStore();
  const event = getEventById(eventId ?? '');
  const [tab, setTab] = useState<Tab>('attributes');
  const [jsonMode, setJsonMode] = useState(false);

  if (!event) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-400">Событие не найдено</p>
        <Link to="/console" className="text-[#4a9eff] text-sm mt-2 inline-block hover:underline">← Консоль</Link>
      </div>
    );
  }

  const ci = getCi(event.ciId);
  const logs = getActionLogsForEvent(event.id);
  const rootEvent = event.rootEventId ? getEventById(event.rootEventId) : undefined;
  const childEvents = (event.childEventIds ?? []).map((id) => getEventById(id)).filter(Boolean);

  const attributes = [
    { key: 'id', type: 'UUID', value: event.id },
    { key: 'status', type: 'string', value: event.status },
    { key: 'severity', type: 'string', value: event.severity },
    { key: 'title', type: 'string', value: event.title },
    { key: 'description', type: 'string', value: event.description },
    { key: 'ci_id', type: 'UUID', value: event.ciId },
    { key: 'node_fqdn', type: 'string', value: event.nodeFqdn },
    { key: 'system_name', type: 'string', value: event.systemName },
    { key: 'source_id', type: 'UUID', value: event.sourceId },
    { key: 'repeat_count', type: 'number', value: String(event.repeatCount) },
    { key: 'created_at', type: 'date', value: event.createdAt },
    { key: 'assigned_user_id', type: 'UUID', value: event.assignedUserId ?? '' },
    { key: 'itsm_incident_number', type: 'string', value: event.itsmIncidentNumber ?? '' },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between">
        <div>
          <Link to="/console" className="text-xs text-[#4a9eff] hover:underline">← Консоль событий</Link>
          <div className="flex items-center gap-2 mt-2">
            <StatusBadge status={event.status} />
            <SeverityBadge severity={event.severity} />
            {event.repeatCount > 1 && <span className="text-sm text-gray-400">×{event.repeatCount}</span>}
          </div>
          <h1 className="text-xl font-semibold text-white mt-1">{event.title}</h1>
          <p className="text-sm text-gray-500 font-mono mt-1">{event.id}</p>
        </div>
        <EventActionButtons event={event} onAction={() => setTab('journal')} />
      </div>

      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
        <InfoBlock label="КЕ / FQDN" value={event.nodeFqdn} mono />
        <InfoBlock label="Система" value={event.systemName} />
        <InfoBlock label="Источник" value={getSourceName(event.sourceId)} />
        <InfoBlock label="Исполнитель" value={getUserName(event.assignedUserId)} />
        <InfoBlock label="Создано" value={formatDate(event.createdAt)} mono />
        {event.itsmIncidentNumber && <InfoBlock label="ITSM" value={event.itsmIncidentNumber} accent />}
      </div>

      <div className="border-b border-[#3a3f4b]">
        <div className="flex gap-1">
          {([
            ['attributes', 'Атрибуты'],
            ['journal', 'Журнал действий'],
            ['relations', 'Связи'],
          ] as [Tab, string][]).map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => setTab(key)}
              className={`px-4 py-2 text-sm border-b-2 transition-colors ${
                tab === key ? 'border-[#4a9eff] text-[#4a9eff]' : 'border-transparent text-gray-400 hover:text-white'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
        {tab === 'attributes' && (
          <>
            <div className="flex justify-end mb-3">
              <button
                type="button"
                onClick={() => setJsonMode(!jsonMode)}
                className="text-xs text-[#4a9eff] hover:underline"
              >
                {jsonMode ? 'Таблица' : 'JSON'}
              </button>
            </div>
            {jsonMode ? (
              <pre className="text-xs font-mono text-gray-300 bg-[#1a1d23] p-4 rounded overflow-auto">
                {JSON.stringify(Object.fromEntries(attributes.map((a) => [a.key, a.value])), null, 2)}
              </pre>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-gray-500 border-b border-[#3a3f4b]">
                    <th className="pb-2 pr-4">Ключ</th>
                    <th className="pb-2 pr-4">Тип</th>
                    <th className="pb-2">Значение</th>
                  </tr>
                </thead>
                <tbody>
                  {attributes.map((a) => (
                    <tr key={a.key} className="border-b border-[#3a3f4b]/50">
                      <td className="py-2 pr-4 font-mono text-gray-400">{a.key}</td>
                      <td className="py-2 pr-4 text-gray-500">{a.type}</td>
                      <td className="py-2 text-gray-300">{a.value || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </>
        )}

        {tab === 'journal' && (
          <div className="space-y-3">
            {logs.length === 0 ? (
              <p className="text-sm text-gray-500">Журнал пуст</p>
            ) : (
              logs.map((log) => (
                <div key={log.id} className="flex gap-3 text-sm border-l-2 border-[#4a9eff]/30 pl-3">
                  <span className="font-mono text-xs text-gray-500 whitespace-nowrap">{formatDate(log.timestamp)}</span>
                  <div>
                    <span className="text-white font-medium">{log.action}</span>
                    <span className="text-gray-500"> — {log.userName}</span>
                    <p className="text-gray-400 text-xs mt-0.5">{log.details}</p>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {tab === 'relations' && (
          <div className="space-y-4">
            {rootEvent && (
              <div>
                <h3 className="text-xs text-gray-500 uppercase mb-2">Корневая причина</h3>
                <Link to={`/console/${rootEvent.id}`} className="block px-3 py-2 bg-[#1a1d23] rounded border border-[#3a3f4b] hover:border-[#4a9eff]/50">
                  <SeverityBadge severity={rootEvent.severity} />
                  <span className="ml-2 text-gray-300">{rootEvent.title}</span>
                </Link>
              </div>
            )}
            {childEvents.length > 0 && (
              <div>
                <h3 className="text-xs text-gray-500 uppercase mb-2">Дочерние события</h3>
                <div className="space-y-1">
                  {childEvents.map((child) => child && (
                    <Link key={child.id} to={`/console/${child.id}`} className="flex items-center gap-2 px-3 py-2 bg-[#1a1d23] rounded border border-[#3a3f4b] hover:border-[#4a9eff]/50 text-sm">
                      <SeverityBadge severity={child.severity} compact />
                      <span className="text-gray-300">{child.title}</span>
                    </Link>
                  ))}
                </div>
              </div>
            )}
            {ci && (
              <div>
                <h3 className="text-xs text-gray-500 uppercase mb-2">КЕ</h3>
                <div className="px-3 py-2 bg-[#1a1d23] rounded border border-[#3a3f4b] text-sm">
                  <span className="font-mono text-gray-300">{ci.fqdn}</span>
                  <span className="text-gray-500 ml-2">{ci.system}</span>
                </div>
              </div>
            )}
            {!rootEvent && childEvents.length === 0 && (
              <p className="text-sm text-gray-500">Нет связанных событий</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function InfoBlock({ label, value, mono, accent }: { label: string; value: string; mono?: boolean; accent?: boolean }) {
  return (
    <div className="bg-[#252830] border border-[#3a3f4b] rounded px-3 py-2">
      <div className="text-[10px] text-gray-500 uppercase">{label}</div>
      <div className={`text-sm mt-0.5 truncate ${mono ? 'font-mono' : ''} ${accent ? 'text-[#4a9eff]' : 'text-gray-300'}`}>{value}</div>
    </div>
  );
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU');
}
