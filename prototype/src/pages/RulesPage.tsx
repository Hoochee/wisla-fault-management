import { Link } from 'react-router-dom';
import { rules } from '../data/mock';

const typeLabels: Record<string, string> = {
  dedup: 'Дедупликация',
  threshold: 'Порог',
  problem_resolution: 'Problem–Resolution',
  correlation: 'Корреляция',
};

export function RulesPage() {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-white">Правила обработки</h1>
          <p className="text-sm text-gray-500 mt-1">Бизнес-процессы обработки событий (Monq БП)</p>
        </div>
        <Link to="/rules/new" className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">
          + Создать правило
        </Link>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-gray-500 uppercase border-b border-[#3a3f4b]">
              <th className="px-4 py-3">Название</th>
              <th className="px-4 py-3">Тип</th>
              <th className="px-4 py-3">Триггер</th>
              <th className="px-4 py-3">Вкл</th>
              <th className="px-4 py-3">Согласование</th>
              <th className="px-4 py-3">Последний запуск</th>
            </tr>
          </thead>
          <tbody>
            {rules.map((r) => (
              <tr key={r.id} className="border-b border-[#3a3f4b]/50 hover:bg-[#3a3f4b]/20">
                <td className="px-4 py-3">
                  <Link to={`/rules/${r.id}`} className="text-[#4a9eff] hover:underline">{r.name}</Link>
                  <p className="text-xs text-gray-500 mt-0.5">{r.description}</p>
                </td>
                <td className="px-4 py-3 text-gray-400">{typeLabels[r.ruleType]}</td>
                <td className="px-4 py-3 text-gray-400 text-xs">{r.triggerType}</td>
                <td className="px-4 py-3">
                  <span className={`inline-block w-8 h-4 rounded-full ${r.enabled ? 'bg-green-500' : 'bg-gray-600'} relative`}>
                    <span className={`absolute top-0.5 w-3 h-3 rounded-full bg-white transition-all ${r.enabled ? 'right-0.5' : 'left-0.5'}`} />
                  </span>
                </td>
                <td className="px-4 py-3">
                  <ApprovalBadge status={r.approvalStatus} />
                </td>
                <td className="px-4 py-3 font-mono text-xs text-gray-500">{formatDate(r.lastRunAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function ApprovalBadge({ status }: { status: string }) {
  const styles: Record<string, string> = {
    approved: 'bg-green-500/20 text-green-300',
    pending: 'bg-amber-500/20 text-amber-300',
    draft: 'bg-gray-500/20 text-gray-400',
  };
  const labels: Record<string, string> = { approved: 'Утверждено', pending: 'На согласовании', draft: 'Черновик' };
  return <span className={`px-2 py-0.5 text-xs rounded ${styles[status]}`}>{labels[status]}</span>;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
}
