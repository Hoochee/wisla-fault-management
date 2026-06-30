import { Link, useNavigate } from 'react-router-dom';
import { RuleBuilder } from '../components/RuleBuilder';

export function RuleNewPage() {
  const navigate = useNavigate();

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <Link to="/rules" className="text-xs text-[#4a9eff] hover:underline">← Правила</Link>
          <h1 className="text-xl font-semibold text-white mt-1">Новое правило</h1>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={() => navigate('/rules')} className="px-4 py-2 text-sm border border-[#3a3f4b] rounded text-gray-300">
            Сохранить
          </button>
          <button type="button" className="px-4 py-2 text-sm bg-green-600 text-white rounded hover:bg-green-500">
            Активировать
          </button>
        </div>
      </div>

      <RuleBuilder
        ruleMeta={{
          name: 'Новое правило дедупликации',
          ruleType: 'dedup',
          description: '',
        }}
        initialBlocks={[
          { id: 'b1', type: 'trigger', label: 'Событие потока', x: 200, y: 40, config: { triggerType: 'stream' } },
        ]}
        initialConnections={[]}
      />
    </div>
  );
}
