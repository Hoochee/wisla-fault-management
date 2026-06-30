import type { EventStatus, SourceStatus } from '../data/mock';
import { statusLabels } from '../data/mock';

const eventStatusColors: Record<EventStatus, string> = {
  new: 'bg-blue-500/20 text-blue-300 border-blue-500/40',
  in_progress: 'bg-amber-500/20 text-amber-300 border-amber-500/40',
  closed: 'bg-gray-500/20 text-gray-400 border-gray-500/40',
  archived: 'bg-gray-600/20 text-gray-500 border-gray-600/40',
  maintenance: 'bg-purple-500/20 text-purple-300 border-purple-500/40',
  deferred: 'bg-orange-500/20 text-orange-300 border-orange-500/40',
};

const sourceStatusColors: Record<SourceStatus, string> = {
  active: 'bg-green-500/20 text-green-300 border-green-500/40',
  inactive: 'bg-gray-500/20 text-gray-400 border-gray-500/40',
  blocked: 'bg-red-500/20 text-red-300 border-red-500/40',
};

const sourceStatusLabels: Record<SourceStatus, string> = {
  active: 'Запущен',
  inactive: 'Остановлен',
  blocked: 'Заблокирован',
};

interface EventProps {
  status: EventStatus;
  type?: 'event';
}

interface SourceProps {
  status: SourceStatus;
  type: 'source';
}

type Props = EventProps | SourceProps;

export function StatusBadge(props: Props) {
  if ('type' in props && props.type === 'source') {
    return (
      <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${sourceStatusColors[props.status]}`}>
        {sourceStatusLabels[props.status]}
      </span>
    );
  }
  const status = props.status as EventStatus;
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border ${eventStatusColors[status]}`}>
      {statusLabels[status]}
    </span>
  );
}
