import { settings } from '../data/mock';

export function SettingsPage() {
  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-xl font-semibold text-white">Настройки</h1>
        <p className="text-sm text-gray-500 mt-1">Общие параметры модуля и профиль</p>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-6 space-y-5">
        <h2 className="text-sm font-medium text-white border-b border-[#3a3f4b] pb-2">Системные</h2>

        <div>
          <label className="block text-xs text-gray-400 mb-1">Часовой пояс</label>
          <select defaultValue={settings.timezone} className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm">
            <option value="Europe/Moscow">Europe/Moscow (UTC+3)</option>
            <option value="Europe/Kaliningrad">Europe/Kaliningrad (UTC+2)</option>
          </select>
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1">Интервал polling консоли (сек)</label>
          <input type="number" defaultValue={settings.pollingIntervalSec} className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm" />
          <p className="text-[10px] text-gray-500 mt-1">Диапазон: 5 – 300 сек</p>
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1">Автоархив закрытых (дней)</label>
          <input type="number" defaultValue={settings.autoArchiveDays} className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm" />
        </div>

        <div>
          <label className="block text-xs text-gray-400 mb-1">Интервал повтора при рецидиве (мин)</label>
          <input type="number" defaultValue={settings.repeatIntervalMin} className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm" />
        </div>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-6 space-y-4 opacity-60">
        <h2 className="text-sm font-medium text-white border-b border-[#3a3f4b] pb-2">Интеграции <span className="text-amber-500 text-xs">post-MVP</span></h2>
        <label className="flex items-center gap-2 text-sm text-gray-400">
          <input type="checkbox" disabled checked={settings.wislaIntegration} className="rounded" />
          WISLA API
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-400">
          <input type="checkbox" disabled checked={settings.itsmIntegration} className="rounded" />
          ITSM endpoints
        </label>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-6 space-y-4">
        <h2 className="text-sm font-medium text-white border-b border-[#3a3f4b] pb-2">Профиль</h2>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Отображаемое имя</label>
          <input type="text" defaultValue="Иванов А.С." className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm" />
        </div>
        <div>
          <label className="block text-xs text-gray-400 mb-1">Email</label>
          <input type="email" defaultValue="ivanov@company.ru" className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm" />
        </div>
      </div>

      <button type="button" className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">
        Сохранить
      </button>
    </div>
  );
}
