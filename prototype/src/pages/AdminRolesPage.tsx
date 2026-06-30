import { Link } from 'react-router-dom';
import { roles } from '../data/mock';

const allPermissions = ['console', 'events', 'sources', 'rules', 'admin', 'settings', 'downtime', 'reports'];

export function AdminRolesPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/admin" className="text-xs text-[#4a9eff] hover:underline">← Администрирование</Link>
          <h1 className="text-xl font-semibold text-white mt-1">Роли и полномочия</h1>
        </div>
        <button type="button" className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">+ Добавить роль</button>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-gray-500 uppercase border-b border-[#3a3f4b]">
              <th className="px-4 py-3 sticky left-0 bg-[#252830]">Роль</th>
              {allPermissions.map((p) => (
                <th key={p} className="px-3 py-3 text-center">{p}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {roles.map((role) => (
              <tr key={role.id} className="border-b border-[#3a3f4b]/50 hover:bg-[#3a3f4b]/20">
                <td className="px-4 py-3 sticky left-0 bg-[#252830]">
                  <div className="text-gray-300">{role.name}</div>
                  <div className="text-[10px] text-gray-500">{role.description}</div>
                </td>
                {allPermissions.map((p) => (
                  <td key={p} className="px-3 py-3 text-center">
                    {role.permissions.some((rp) => rp === p || rp.startsWith(p)) ? (
                      <span className="text-green-400">✓</span>
                    ) : (
                      <span className="text-gray-600">—</span>
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
