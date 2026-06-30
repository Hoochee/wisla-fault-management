import { Link } from 'react-router-dom';
import { configurationItems } from '../data/mock';

export function AdminCiPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/admin" className="text-xs text-[#4a9eff] hover:underline">← Администрирование</Link>
          <h1 className="text-xl font-semibold text-white mt-1">Реестр КЕ</h1>
          <p className="text-sm text-gray-500 mt-1">Конфигурационные элементы (mock seed)</p>
        </div>
        <button type="button" className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">+ Добавить КЕ</button>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-gray-500 uppercase border-b border-[#3a3f4b]">
              <th className="px-4 py-3">FQDN</th>
              <th className="px-4 py-3">Тип</th>
              <th className="px-4 py-3">Система</th>
              <th className="px-4 py-3">Подсистема</th>
              <th className="px-4 py-3">ПО</th>
              <th className="px-4 py-3">Продукты</th>
              <th className="px-4 py-3">Теги</th>
            </tr>
          </thead>
          <tbody>
            {configurationItems.map((ci) => (
              <tr key={ci.id} className="border-b border-[#3a3f4b]/50 hover:bg-[#3a3f4b]/20">
                <td className="px-4 py-3 font-mono text-xs text-[#4a9eff]">{ci.fqdn}</td>
                <td className="px-4 py-3 text-gray-400">{ci.ciType}</td>
                <td className="px-4 py-3 text-gray-300">{ci.system}</td>
                <td className="px-4 py-3 text-gray-400">{ci.subsystem}</td>
                <td className="px-4 py-3 text-gray-400 text-xs">{ci.software}</td>
                <td className="px-4 py-3 text-xs text-gray-400">{ci.products.join(', ')}</td>
                <td className="px-4 py-3">
                  <div className="flex flex-wrap gap-1">
                    {ci.tags.map((t) => (
                      <span key={t} className="px-1.5 py-0.5 text-[10px] rounded bg-[#3a3f4b]/50 text-gray-400">{t}</span>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-gray-500">Импорт из CMDB — post-MVP</p>
    </div>
  );
}
