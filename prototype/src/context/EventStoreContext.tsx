import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import {
  actionLogs as initialLogs,
  currentUser,
  events as initialEvents,
  type Event,
  type EventActionLog,
} from '../data/mock';

interface EventStoreValue {
  events: Event[];
  getEventById: (id: string) => Event | undefined;
  getActionLogsForEvent: (eventId: string) => EventActionLog[];
  takeToWork: (eventId: string) => void;
  closeEvent: (eventId: string) => void;
  addComment: (eventId: string, text: string) => void;
}

const EventStoreContext = createContext<EventStoreValue | null>(null);

function nowIso(): string {
  return new Date().toISOString();
}

function nextLogId(logs: EventActionLog[]): string {
  const n = logs.length + 1;
  return `log-${String(n).padStart(3, '0')}`;
}

export function EventStoreProvider({ children }: { children: ReactNode }) {
  const [events, setEvents] = useState<Event[]>(() => initialEvents.map((e) => ({ ...e })));
  const [logs, setLogs] = useState<EventActionLog[]>(() => [...initialLogs]);

  const getEventById = useCallback((id: string) => events.find((e) => e.id === id), [events]);

  const getActionLogsForEvent = useCallback(
    (eventId: string) =>
      [...logs.filter((l) => l.eventId === eventId)].sort(
        (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime(),
      ),
    [logs],
  );

  const appendLog = useCallback((entry: Omit<EventActionLog, 'id'>) => {
    setLogs((prev) => [...prev, { ...entry, id: nextLogId(prev) }]);
  }, []);

  const takeToWork = useCallback(
    (eventId: string) => {
      const event = events.find((e) => e.id === eventId);
      if (!event || event.status === 'in_progress' || event.status === 'closed' || event.status === 'archived') {
        return;
      }
      const ts = nowIso();
      setEvents((prev) =>
        prev.map((e) =>
          e.id === eventId
            ? {
                ...e,
                status: 'in_progress' as const,
                assignedUserId: currentUser.id,
                takenAt: ts,
                itsmIncidentNumber: e.itsmIncidentNumber ?? `INC-${new Date().getFullYear()}-${String(Math.floor(Math.random() * 900000) + 100000)}`,
              }
            : e,
        ),
      );
      appendLog({
        eventId,
        action: 'Принятие в работу',
        userName: currentUser.fullName,
        timestamp: ts,
        details: 'Статус изменён: Новый → В работе',
      });
    },
    [events, appendLog],
  );

  const closeEvent = useCallback(
    (eventId: string) => {
      const event = events.find((e) => e.id === eventId);
      if (!event || event.status === 'closed' || event.status === 'archived') {
        return;
      }
      const ts = nowIso();
      setEvents((prev) =>
        prev.map((e) =>
          e.id === eventId ? { ...e, status: 'closed' as const, closedAt: ts } : e,
        ),
      );
      appendLog({
        eventId,
        action: 'Закрытие',
        userName: currentUser.fullName,
        timestamp: ts,
        details: 'Статус изменён → Закрыт',
      });
    },
    [events, appendLog],
  );

  const addComment = useCallback(
    (eventId: string, text: string) => {
      const trimmed = text.trim();
      if (!trimmed || !events.some((e) => e.id === eventId)) return;
      appendLog({
        eventId,
        action: 'Комментарий',
        userName: currentUser.fullName,
        timestamp: nowIso(),
        details: trimmed,
      });
    },
    [events, appendLog],
  );

  const value = useMemo(
    () => ({
      events,
      getEventById,
      getActionLogsForEvent,
      takeToWork,
      closeEvent,
      addComment,
    }),
    [events, getEventById, getActionLogsForEvent, takeToWork, closeEvent, addComment],
  );

  return <EventStoreContext.Provider value={value}>{children}</EventStoreContext.Provider>;
}

export function useEventStore(): EventStoreValue {
  const ctx = useContext(EventStoreContext);
  if (!ctx) throw new Error('useEventStore must be used within EventStoreProvider');
  return ctx;
}
