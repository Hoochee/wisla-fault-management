import { Link } from 'react-router-dom';
import { roles, users } from '../data/mock';

export function AdminUsersPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/admin" className="text-xs text-[#4a9eff] hover:underline">← Администрирование</Link>
          <h1 className="text-xl font-semibold text-white mt-1">Пользователи</h1>
        </div>
        <button type="button" className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">+ Добавить</button>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-gray-500 uppercase border-b border-[#3a3f4b]">
              <th className="px-4 py-3">Логин</th>
              <th className="px-4 py-3">ФИО</th>
              <th className="px-4 py-3">Email</th>
              <th className="px-4 py-3">Команда</th>
              <th className="px-4 py-3">Роли</th>
              <th className="px-4 py-3">Статус</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-b border-[#3a3f4b]/50 hover:bg-[#3a3f4b]/20">
                <td className="px-4 py-3 font-mono text-[#4a9eff]">{u.login}</td>
                <td className="px-4 py-3 text-gray-300">{u.fullName}</td>
                <td className="px-4 py-3 text-gray-400">{u.email}</td>
                <td className="px-4 py-3 text-gray-400">{u.team}</td>
                <td className="px-4 py-3 text-xs text-gray-400">
                  {u.roleIds.map((rid) => roles.find((r) => r.id === rid)?.name).join(', ')}
                </td>
                <td className="px-4 py-3">
                  <span className={`text-xs ${u.active ? 'text-green-400' : 'text-gray-500'}`}>
                    {u.active ? 'Активен' : 'Неактивен'}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="text-xs text-gray-500">Провиженинг AD — post-MVP</p>
    </div>
  );
}
