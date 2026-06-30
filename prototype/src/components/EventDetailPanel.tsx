import { Link } from 'react-router-dom';
import type { Event } from '../data/mock';
import { getSourceName, getUserName } from '../data/mock';
import { EventActionButtons } from './EventActionButtons';
import { SeverityBadge } from './SeverityBadge';
import { StatusBadge } from './StatusBadge';

interface Props {
  event: Event;
  compact?: boolean;
  onEventUpdated?: () => void;
}

export function EventDetailPanel({ event, compact, onEventUpdated }: Props) {
  return (
    <div className={`bg-[#252830] border border-[#3a3f4b] rounded-lg ${compact ? 'p-3' : 'p-4'}`}>
      <div className="flex items-start justify-between gap-4 mb-3">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <StatusBadge status={event.status} />
            <SeverityBadge severity={event.severity} />
            {event.repeatCount > 1 && (
              <span className="text-xs text-gray-400">×{event.repeatCount}</span>
            )}
          </div>
          <h3 className={`font-medium text-white truncate ${compact ? 'text-sm' : 'text-base'}`}>
            {event.title}
          </h3>
        </div>
        <Link
          to={`/console/${event.id}`}
          className="text-xs text-[#4a9eff] hover:underline whitespace-nowrap"
        >
          Открыть карточку →
        </Link>
      </div>

      <div className={`grid gap-2 text-xs ${compact ? 'grid-cols-2' : 'grid-cols-2 md:grid-cols-4'}`}>
        <div>
          <span className="text-gray-500">ID</span>
          <p className="font-mono text-gray-300 truncate">{event.id}</p>
        </div>
        <div>
          <span className="text-gray-500">КЕ / FQDN</span>
          <p className="text-gray-300 truncate">{event.nodeFqdn}</p>
        </div>
        <div>
          <span className="text-gray-500">Система</span>
          <p className="text-gray-300">{event.systemName}</p>
        </div>
        <div>
          <span className="text-gray-500">Источник</span>
          <p className="text-gray-300 truncate">{getSourceName(event.sourceId)}</p>
        </div>
        <div>
          <span className="text-gray-500">Создано</span>
          <p className="font-mono text-gray-300">{formatDate(event.createdAt)}</p>
        </div>
        <div>
          <span className="text-gray-500">Исполнитель</span>
          <p className="text-gray-300">{getUserName(event.assignedUserId)}</p>
        </div>
        {event.itsmIncidentNumber && (
          <div>
            <span className="text-gray-500">ITSM</span>
            <p className="text-[#4a9eff]">{event.itsmIncidentNumber}</p>
          </div>
        )}
        {event.rootEventId && (
          <div>
            <span className="text-gray-500">Корневая причина</span>
            <Link to={`/console/${event.rootEventId}`} className="text-[#4a9eff] hover:underline font-mono">
              {event.rootEventId}
            </Link>
          </div>
        )}
      </div>

      {event.description && !compact && (
        <p className="mt-3 text-sm text-gray-400 border-t border-[#3a3f4b] pt-3">{event.description}</p>
      )}

      <EventActionButtons event={event} compact={compact} onAction={onEventUpdated} />    </div>
  );
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
