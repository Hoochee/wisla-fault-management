import { Link, useParams } from 'react-router-dom';
import { getRuleById } from '../data/mock';
import { RuleBuilder } from '../components/RuleBuilder';

const typeLabels: Record<string, string> = {
  dedup: 'Дедупликация',
  threshold: 'Порог',
  problem_resolution: 'Problem–Resolution',
  correlation: 'Корреляция',
};

export function RuleEditPage() {
  const { id } = useParams();
  const rule = getRuleById(id ?? '');

  if (!rule) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-400">Правило не найдено</p>
        <Link to="/rules" className="text-[#4a9eff] text-sm mt-2 inline-block hover:underline">← Назад</Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/rules" className="text-xs text-[#4a9eff] hover:underline">← Правила</Link>
          <h1 className="text-xl font-semibold text-white mt-1">{rule.name}</h1>
          <p className="text-sm text-gray-500">{typeLabels[rule.ruleType]} · {rule.triggerType}</p>
        </div>
        <div className="flex gap-2">
          <button type="button" className="px-4 py-2 text-sm border border-[#3a3f4b] rounded text-gray-300">История версий</button>
          <button type="button" className="px-4 py-2 text-sm border border-[#3a3f4b] rounded text-gray-300">Сохранить</button>
          <button type="button" className={`px-4 py-2 text-sm rounded text-white ${rule.enabled ? 'bg-gray-600' : 'bg-green-600 hover:bg-green-500'}`}>
            {rule.enabled ? 'Деактивировать' : 'Активировать'}
          </button>
        </div>
      </div>

      <RuleBuilder
        ruleMeta={{
          name: rule.name,
          ruleType: rule.ruleType,
          description: rule.description,
        }}
        readOnlyMeta={(
          <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
            <h3 className="text-sm font-medium text-white mb-2">Метаданные</h3>
            <div className="space-y-2 text-xs">
              <div><span className="text-gray-500">ID:</span> <span className="font-mono text-gray-300">{rule.id}</span></div>
              <div><span className="text-gray-500">Статус:</span> <span className="text-gray-300">{rule.enabled ? 'Включено' : 'Выключено'}</span></div>
              <div><span className="text-gray-500">Согласование:</span> <span className="text-green-300">{rule.approvalStatus}</span></div>
              <div><span className="text-gray-500">Последний запуск:</span> <span className="font-mono text-gray-400">{new Date(rule.lastRunAt).toLocaleString('ru-RU')}</span></div>
            </div>
          </div>
        )}
      />
    </div>
  );
}
