import { useState } from 'react';
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom';
import { currentUser } from '../data/mock';

interface NavItem {
  label: string;
  path?: string;
  icon: string;
  postMvp?: boolean;
  children?: { label: string; path: string; postMvp?: boolean }[];
}

const navigation: NavItem[] = [
  { label: 'Обзор', icon: '📊', children: [
    { label: 'Dashboard', path: '/' },
    { label: 'Здоровье продуктов', path: '/health' },
  ]},
  { label: 'Консоль событий', icon: '📋', path: '/console' },
  { label: 'Первичные события', icon: '📄', path: '/events/raw' },
  { label: 'Сбор данных', icon: '📥', children: [
    { label: 'Источники', path: '/sources' },
  ]},
  { label: 'Анализ и обработка', icon: '📐', children: [
    { label: 'Правила', path: '/rules' },
    { label: 'Downtime', path: '/downtime', postMvp: true },
  ]},
  { label: 'Администрирование', icon: '⚙', children: [
    { label: 'Хаб', path: '/admin' },
    { label: 'Пользователи', path: '/admin/users' },
    { label: 'Роли', path: '/admin/roles' },
    { label: 'КЕ', path: '/admin/ci' },
    { label: 'Карты событий', path: '/admin/search-folders' },
  ]},
  { label: 'Настройки', icon: '⚙', path: '/settings' },
  { label: 'Отчётность', icon: '📈', path: '/reports', postMvp: true },
];

const favorites = [
  { label: 'Активные события', path: '/console' },
  { label: 'Critical/Major', path: '/console?map=map-critical' },
];

export function AppLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({ Обзор: true });
  const location = useLocation();
  const navigate = useNavigate();

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/';
    return location.pathname === path || location.pathname.startsWith(path + '/');
  };

  const breadcrumbs = getBreadcrumbs(location.pathname);

  return (
    <div className="min-h-screen flex bg-[#1a1d23] text-gray-200">
      <aside className={`${collapsed ? 'w-14' : 'w-56'} shrink-0 bg-[#252830] border-r border-[#3a3f4b] flex flex-col transition-all duration-200`}>
        <div className="px-3 py-4 border-b border-[#3a3f4b]">
          {!collapsed ? (
            <Link to="/" className="flex items-center gap-2">
              <span className="w-8 h-8 rounded bg-[#4a9eff] flex items-center justify-center text-white font-bold text-sm">W</span>
              <div>
                <div className="text-sm font-semibold text-white">WISLA FM</div>
                <div className="text-[10px] text-gray-500">Fault Management</div>
              </div>
            </Link>
          ) : (
            <Link to="/" className="flex justify-center">
              <span className="w-8 h-8 rounded bg-[#4a9eff] flex items-center justify-center text-white font-bold text-sm">W</span>
            </Link>
          )}
        </div>

        {!collapsed && (
          <div className="px-3 py-2 border-b border-[#3a3f4b]">
            <button type="button" className="w-full text-left text-xs px-2 py-1.5 rounded bg-[#1a1d23] border border-[#3a3f4b] text-gray-300 hover:border-[#4a9eff]">
              NOC Центр ▼
            </button>
          </div>
        )}

        {!collapsed && (
          <div className="px-3 py-2 border-b border-[#3a3f4b]">
            <div className="text-[10px] text-gray-500 uppercase tracking-wide mb-1 px-1">★ Избранное</div>
            {favorites.map((f) => (
              <Link key={f.path} to={f.path} className="block px-2 py-1 text-xs text-gray-400 hover:text-[#4a9eff] truncate">
                {f.label}
              </Link>
            ))}
          </div>
        )}

        <nav className="flex-1 overflow-y-auto py-2">
          {navigation.map((item) => (
            <div key={item.label}>
              {item.children ? (
                <>
                  <button
                    type="button"
                    onClick={() => !collapsed && setExpanded((e) => ({ ...e, [item.label]: !e[item.label] }))}
                    className={`w-full flex items-center gap-2 px-3 py-2 text-sm hover:bg-[#3a3f4b]/30 ${collapsed ? 'justify-center' : ''}`}
                    title={collapsed ? item.label : undefined}
                  >
                    <span>{item.icon}</span>
                    {!collapsed && (
                      <>
                        <span className="flex-1 text-left text-gray-300">{item.label}</span>
                        <span className="text-gray-500 text-xs">{expanded[item.label] ? '▾' : '▸'}</span>
                      </>
                    )}
                  </button>
                  {!collapsed && expanded[item.label] && (
                    <div className="ml-4 border-l border-[#3a3f4b]">
                      {item.children.map((child) => (
                        <Link
                          key={child.path}
                          to={child.path}
                          className={`block px-3 py-1.5 text-xs ${
                            isActive(child.path)
                              ? 'text-[#4a9eff] bg-[#4a9eff]/10'
                              : 'text-gray-400 hover:text-white hover:bg-[#3a3f4b]/30'
                          }`}
                        >
                          {child.label}
                          {child.postMvp && <span className="ml-1 text-[9px] text-amber-500">[post-MVP]</span>}
                        </Link>
                      ))}
                    </div>
                  )}
                </>
              ) : (
                <Link
                  to={item.path!}
                  className={`flex items-center gap-2 px-3 py-2 text-sm ${
                    isActive(item.path!)
                      ? 'text-[#4a9eff] bg-[#4a9eff]/10 border-r-2 border-[#4a9eff]'
                      : 'text-gray-300 hover:bg-[#3a3f4b]/30'
                  } ${collapsed ? 'justify-center' : ''}`}
                  title={collapsed ? item.label : undefined}
                >
                  <span>{item.icon}</span>
                  {!collapsed && (
                    <>
                      <span>{item.label}</span>
                      {item.postMvp && <span className="text-[9px] text-amber-500">[post-MVP]</span>}
                    </>
                  )}
                </Link>
              )}
            </div>
          ))}
        </nav>

        <div className="border-t border-[#3a3f4b] p-2">
          {!collapsed && (
            <div className="flex items-center gap-2 px-2 py-1 mb-1">
              <span className="text-sm">👤</span>
              <div className="flex-1 min-w-0">
                <div className="text-xs text-white truncate">{currentUser.fullName}</div>
                <div className="text-[10px] text-gray-500">{currentUser.team}</div>
              </div>
            </div>
          )}
          <button
            type="button"
            onClick={() => setCollapsed(!collapsed)}
            className="w-full px-2 py-1.5 text-xs text-gray-400 hover:text-white hover:bg-[#3a3f4b]/30 rounded"
          >
            {collapsed ? '▶' : '◀ Свернуть'}
          </button>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0">
        <header className="h-12 shrink-0 bg-[#252830] border-b border-[#3a3f4b] flex items-center justify-between px-4">
          <div className="flex items-center gap-2 text-sm text-gray-400">
            {breadcrumbs.map((crumb, i) => (
              <span key={i} className="flex items-center gap-2">
                {i > 0 && <span className="text-gray-600">/</span>}
                {crumb.path ? (
                  <Link to={crumb.path} className="hover:text-[#4a9eff]">{crumb.label}</Link>
                ) : (
                  <span className="text-white">{crumb.label}</span>
                )}
              </span>
            ))}
          </div>
          <div className="flex items-center gap-3">
            <button type="button" className="text-gray-400 hover:text-white text-sm" title="Уведомления">🔔</button>
            <button
              type="button"
              onClick={() => navigate('/login')}
              className="text-xs text-gray-400 hover:text-[#4a9eff]"
            >
              Выход
            </button>
          </div>
        </header>

        <main className="flex-1 overflow-auto p-4">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function getBreadcrumbs(pathname: string): { label: string; path?: string }[] {
  const map: Record<string, { label: string; path?: string }[]> = {
    '/': [{ label: 'Dashboard' }],
    '/health': [{ label: 'Обзор', path: '/' }, { label: 'Здоровье продуктов' }],
    '/events/raw': [{ label: 'Первичные события' }],
    '/console': [{ label: 'Консоль событий' }],
    '/sources': [{ label: 'Источники' }],
    '/sources/new': [{ label: 'Источники', path: '/sources' }, { label: 'Новый источник' }],
    '/rules': [{ label: 'Правила' }],
    '/rules/new': [{ label: 'Правила', path: '/rules' }, { label: 'Новое правило' }],
    '/admin': [{ label: 'Администрирование' }],
    '/admin/users': [{ label: 'Админ', path: '/admin' }, { label: 'Пользователи' }],
    '/admin/roles': [{ label: 'Админ', path: '/admin' }, { label: 'Роли' }],
    '/admin/ci': [{ label: 'Админ', path: '/admin' }, { label: 'Реестр КЕ' }],
    '/admin/search-folders': [{ label: 'Админ', path: '/admin' }, { label: 'Карты событий' }],
    '/settings': [{ label: 'Настройки' }],
    '/downtime': [{ label: 'Downtime' }],
    '/reports': [{ label: 'Отчётность' }],
  };

  if (map[pathname]) return map[pathname];
  if (pathname.startsWith('/health/')) return [{ label: 'Здоровье', path: '/health' }, { label: pathname.split('/').pop()! }];
  if (pathname.startsWith('/console/')) return [{ label: 'Консоль', path: '/console' }, { label: 'Карточка события' }];
  if (pathname.startsWith('/sources/') && pathname !== '/sources/new') return [{ label: 'Источники', path: '/sources' }, { label: 'Редактирование' }];
  if (pathname.startsWith('/rules/') && pathname !== '/rules/new') return [{ label: 'Правила', path: '/rules' }, { label: 'Редактирование' }];
  return [{ label: pathname }];
}
