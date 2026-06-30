import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';

export function SourceNewPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [testResult, setTestResult] = useState<'idle' | 'success' | 'fail'>('idle');

  const handleTest = () => setTestResult('success');
  const handleSave = () => navigate('/sources');

  return (
    <div className="max-w-2xl space-y-6">
      <div>
        <Link to="/sources" className="text-xs text-[#4a9eff] hover:underline">← Источники</Link>
        <h1 className="text-xl font-semibold text-white mt-1">Новый источник</h1>
      </div>

      <div className="flex gap-2 text-xs">
        {['Тип', 'Параметры', 'Webhook', 'Тест', 'Сохранение'].map((s, i) => (
          <span key={s} className={`px-3 py-1 rounded ${i + 1 === step ? 'bg-[#4a9eff]/20 text-[#4a9eff] border border-[#4a9eff]/50' : 'text-gray-500'}`}>
            {i + 1}. {s}
          </span>
        ))}
      </div>

      <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-6 space-y-4">
        {step === 1 && (
          <>
            <label className="block text-sm text-gray-400">Тип источника</label>
            <select className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm">
              <option>Push REST</option>
              <option>SNMP Trap (post-MVP)</option>
              <option>Pull ETL (post-MVP)</option>
            </select>
          </>
        )}
        {step >= 2 && (
          <>
            <div>
              <label className="block text-xs text-gray-400 mb-1">Название</label>
              <input type="text" defaultValue="Новый Push REST источник" className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm" />
            </div>
            <div>
              <label className="block text-xs text-gray-400 mb-1">Endpoint адаптера</label>
              <input type="text" defaultValue="https://adapter.company.ru/new-source" className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm font-mono" />
            </div>
          </>
        )}
        {step >= 3 && (
          <div>
            <label className="block text-xs text-gray-400 mb-1">Webhook URL модуля</label>
            <input type="text" readOnly value="https://fm.company.ru/api/v1/ingest?sourceKey=NEW-KEY-XXXX" className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-gray-400 text-sm font-mono" />
          </div>
        )}
        {step >= 4 && (
          <div className="text-center py-4">
            <button type="button" onClick={handleTest} className="px-4 py-2 text-sm border border-[#3a3f4b] rounded text-gray-300 hover:bg-[#3a3f4b]/50">
              Тест подключения
            </button>
            {testResult === 'success' && (
              <p className="mt-3 text-sm text-green-400">✓ Probe-событие успешно доставлено</p>
            )}
          </div>
        )}
      </div>

      <div className="flex gap-2">
        {step > 1 && (
          <button type="button" onClick={() => setStep(step - 1)} className="px-4 py-2 text-sm border border-[#3a3f4b] rounded text-gray-300">
            Назад
          </button>
        )}
        {step < 5 ? (
          <button type="button" onClick={() => setStep(step + 1)} className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded">
            Далее
          </button>
        ) : (
          <button type="button" onClick={handleSave} className="px-4 py-2 text-sm bg-[#4a9eff] text-white rounded">
            Сохранить
          </button>
        )}
      </div>
    </div>
  );
}
