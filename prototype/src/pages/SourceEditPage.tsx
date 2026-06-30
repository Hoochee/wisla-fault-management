import { Link, useParams } from 'react-router-dom';
import { getSourceById } from '../data/mock';
import { StatusBadge } from '../components/StatusBadge';

const typeLabels: Record<string, string> = {
  push_rest: 'Push REST',
  push_snmp_trap: 'SNMP Trap',
  pull_etl: 'Pull ETL',
};

export function SourceEditPage() {
  const { id } = useParams();
  const source = getSourceById(id ?? '');

  if (!source) {
    return (
      <div className="text-center py-12">
        <p className="text-gray-400">Источник не найден</p>
        <Link to="/sources" className="text-[#4a9eff] text-sm mt-2 inline-block hover:underline">← Назад</Link>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-start justify-between">
        <div>
          <Link to="/sources" className="text-xs text-[#4a9eff] hover:underline">← Источники</Link>
          <h1 className="text-xl font-semibold text-white mt-1">{source.name}</h1>
          <div className="flex items-center gap-2 mt-2">
            <StatusBadge status={source.status} type="source" />
            <span className="text-xs text-gray-500">{typeLabels[source.type]}</span>
          </div>
        </div>
        <div className="flex gap-2">
          <button type="button" className="px-3 py-1.5 text-xs border border-[#3a3f4b] rounded text-gray-300 hover:bg-[#3a3f4b]/50">
            {source.status === 'active' ? 'Остановить' : 'Запустить'}
          </button>
          <button type="button" className="px-3 py-1.5 text-xs bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef]">
            Тест подключения
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Section title="Основная информация">
          <Field label="ID" value={source.id} mono />
          <Field label="Протокол" value={source.protocol} />
          <Field label="Версия адаптера" value={source.adapterVersion} />
          <Field label="Последний успех" value={new Date(source.lastSuccessAt).toLocaleString('ru-RU')} mono />
        </Section>

        <Section title="Получение данных">
          <Field label="Endpoint" value={source.endpoint} mono />
          <Field label="API-ключ" value={source.apiKey} mono />
          <Field label="Webhook URL" value={`https://fm.company.ru/api/v1/ingest?sourceKey=${source.apiKey}`} mono />
        </Section>

        <Section title="Пред-обработка">
          <p className="text-sm text-gray-400">Парсер JSON (встроенный)</p>
        </Section>

        <Section title="Пост-обработка">
          <p className="text-sm text-gray-400">Правила обработки → <Link to="/rules" className="text-[#4a9eff] hover:underline">Правила</Link></p>
        </Section>
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
        <h3 className="text-sm font-medium text-white mb-2">Heartbeat / статус передачи</h3>
        <div className="flex items-center gap-2 text-sm">
          <span className="w-2 h-2 rounded-full bg-green-500" />
          <span className="text-gray-300">Адаптер активен · последняя передача {formatRelative(source.lastSuccessAt)}</span>
        </div>
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
      <h3 className="text-sm font-medium text-white mb-3">{title}</h3>
      <div className="space-y-3">{children}</div>
    </div>
  );
}

function Field({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <div className="text-[10px] text-gray-500 uppercase">{label}</div>
      <div className={`text-sm text-gray-300 mt-0.5 break-all ${mono ? 'font-mono text-xs' : ''}`}>{value}</div>
    </div>
  );
}

function formatRelative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'только что';
  if (mins < 60) return `${mins} мин назад`;
  return `${Math.floor(mins / 60)} ч назад`;
}
