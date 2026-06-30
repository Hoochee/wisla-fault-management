import { Link } from 'react-router-dom';

const sections = [
  { title: 'Пользователи', path: '/admin/users', description: 'CRUD пользователей, привязка к ролям и командам', icon: '👥' },
  { title: 'Роли и полномочия', path: '/admin/roles', description: 'Матрица permissions: консоль, события, источники, правила', icon: '🔐' },
  { title: 'Реестр КЕ', path: '/admin/ci', description: 'Конфигурационные элементы, теги, связь с продуктами', icon: '🖥' },
  { title: 'Карты событий', path: '/admin/search-folders', description: 'Преднастроенные фильтры консоли', icon: '🗂' },
  { title: 'Консоли доступа', path: '/admin/consoles', description: 'Разграничение консолей по тенанту/группе', icon: '📺', postMvp: true },
];

export function AdminPage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-semibold text-white">Администрирование</h1>
        <p className="text-sm text-gray-500 mt-1">Управление пользователями, ролями и справочниками</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {sections.map((s) => (
          s.postMvp ? (
            <div key={s.path} className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-5 opacity-60">
              <div className="text-2xl mb-2">{s.icon}</div>
              <h2 className="text-sm font-medium text-white">{s.title}</h2>
              <p className="text-xs text-gray-500 mt-1">{s.description}</p>
              <span className="inline-block mt-2 text-[10px] text-amber-500">post-MVP</span>
            </div>
          ) : (
            <Link key={s.path} to={s.path} className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-5 hover:border-[#4a9eff]/50 transition-colors">
              <div className="text-2xl mb-2">{s.icon}</div>
              <h2 className="text-sm font-medium text-white">{s.title}</h2>
              <p className="text-xs text-gray-500 mt-1">{s.description}</p>
            </Link>
          )
        ))}
      </div>
    </div>
  );
}
