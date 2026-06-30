import { eventMaps } from '../data/mock';

interface Props {
  selectedId?: string;
  onSelect?: (id: string) => void;
}

export function EventMapsSidebar({ selectedId = 'map-active', onSelect }: Props) {
  return (
    <div className="w-48 shrink-0 border-r border-[#3a3f4b] bg-[#252830] flex flex-col">
      <div className="px-3 py-2 border-b border-[#3a3f4b] flex items-center justify-between">
        <span className="text-xs font-medium text-gray-400 uppercase tracking-wide">Карты</span>
        <button type="button" className="text-[#4a9eff] text-xs hover:underline">+</button>
      </div>
      <ul className="flex-1 overflow-y-auto py-1">
        {eventMaps.map((map) => (
          <li key={map.id}>
            <button
              type="button"
              onClick={() => onSelect?.(map.id)}
              className={`w-full text-left px-3 py-2 text-sm transition-colors ${
                selectedId === map.id
                  ? 'bg-[#4a9eff]/15 text-[#4a9eff] border-r-2 border-[#4a9eff]'
                  : 'text-gray-300 hover:bg-[#3a3f4b]/30'
              }`}
            >
              <span className={map.unsaved ? 'text-amber-400' : ''}>
                {map.name}
                {map.unsaved && ' *'}
              </span>
              {map.isSystem && (
                <span className="block text-[10px] text-gray-500 mt-0.5">системная</span>
              )}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
