import { useState } from 'react';
import type { Event } from '../data/mock';
import { useEventStore } from '../context/EventStoreContext';

interface Props {
  event: Event;
  compact?: boolean;
  onAction?: () => void;
}

export function EventActionButtons({ event, compact, onAction }: Props) {
  const { takeToWork, closeEvent, addComment } = useEventStore();
  const [commentOpen, setCommentOpen] = useState(false);
  const [commentText, setCommentText] = useState('');

  const canTake = event.status === 'new' || event.status === 'deferred';
  const canClose = event.status !== 'closed' && event.status !== 'archived';

  const handleTake = () => {
    takeToWork(event.id);
    onAction?.();
  };

  const handleClose = () => {
    closeEvent(event.id);
    onAction?.();
  };

  const submitComment = () => {
    if (!commentText.trim()) return;
    addComment(event.id, commentText);
    setCommentText('');
    setCommentOpen(false);
    onAction?.();
  };

  const btn = compact ? 'px-2 py-1 text-[11px]' : 'px-3 py-1.5 text-xs';

  return (
    <div className="space-y-2">
      <div className={`flex flex-wrap gap-2 ${compact ? '' : 'mt-4 pt-3 border-t border-[#3a3f4b]'}`}>
        <button
          type="button"
          disabled={!canTake}
          onClick={handleTake}
          className={`${btn} bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef] disabled:opacity-40 disabled:cursor-not-allowed`}
        >
          Принять в работу
        </button>
        <button
          type="button"
          disabled={!canClose}
          onClick={handleClose}
          className={`${btn} border border-[#3a3f4b] text-gray-300 rounded hover:bg-[#3a3f4b]/50 disabled:opacity-40 disabled:cursor-not-allowed`}
        >
          Закрыть
        </button>
        <button
          type="button"
          onClick={() => setCommentOpen((v) => !v)}
          className={`${btn} border border-[#3a3f4b] text-gray-300 rounded hover:bg-[#3a3f4b]/50`}
        >
          Комментарий
        </button>
      </div>

      {commentOpen && (
        <div className="flex gap-2 items-start">
          <textarea
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            placeholder="Текст комментария…"
            rows={2}
            className="flex-1 text-xs bg-[#1a1d23] border border-[#3a3f4b] rounded px-2 py-1.5 text-gray-200 placeholder:text-gray-600 resize-none focus:outline-none focus:border-[#4a9eff]"
          />
          <button
            type="button"
            onClick={submitComment}
            className="px-2 py-1.5 text-xs bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]"
          >
            Добавить
          </button>
        </div>
      )}
    </div>
  );
}
