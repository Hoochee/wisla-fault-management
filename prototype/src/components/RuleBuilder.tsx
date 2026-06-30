import { useCallback, useId, useState } from 'react';
import {
  RuleCanvas,
  defaultBlocks,
  defaultConnections,
  type BlockType,
  type RuleBlock,
  type RuleConnection,
} from './RuleCanvas';

const palette: { type: BlockType; label: string; defaultLabel: string; defaultConfig: Record<string, string> }[] = [
  { type: 'trigger', label: 'Событие потока', defaultLabel: 'Событие потока', defaultConfig: { triggerType: 'stream' } },
  { type: 'trigger', label: 'Событие FM', defaultLabel: 'Событие FM', defaultConfig: { triggerType: 'fm' } },
  { type: 'condition', label: 'Условие', defaultLabel: 'severity = critical', defaultConfig: { field: 'severity', operator: 'eq', value: 'critical' } },
  { type: 'switch', label: 'Switch', defaultLabel: 'Switch', defaultConfig: {} },
  { type: 'dedup', label: 'Дедупликация', defaultLabel: 'Дедупликация', defaultConfig: { key: 'source_id + title + ci_id' } },
  { type: 'threshold', label: 'Порог', defaultLabel: 'Порог: 5 за 10 мин', defaultConfig: { count: '5', windowMin: '10' } },
  { type: 'status', label: 'Смена статуса', defaultLabel: 'Смена статуса', defaultConfig: { status: 'in_progress' } },
];

const typeLabels: Record<BlockType, string> = {
  trigger: 'Триггер',
  condition: 'Условие',
  switch: 'Switch',
  dedup: 'Дедупликация',
  threshold: 'Порог',
  status: 'Смена статуса',
};

interface RuleMeta {
  name?: string;
  description?: string;
  ruleType?: string;
}

interface Props {
  ruleMeta?: RuleMeta;
  readOnlyMeta?: React.ReactNode;
  initialBlocks?: RuleBlock[];
  initialConnections?: RuleConnection[];
}

let nextBlockNum = 100;

export function RuleBuilder({ ruleMeta, readOnlyMeta, initialBlocks, initialConnections }: Props) {
  const [blocks, setBlocks] = useState<RuleBlock[]>(initialBlocks ?? defaultBlocks);
  const [connections, setConnections] = useState<RuleConnection[]>(initialConnections ?? defaultConnections);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const formId = useId();

  const selected = blocks.find((b) => b.id === selectedId) ?? null;

  const outgoing = connections.filter((c) => c.from === selectedId);
  const incoming = connections.filter((c) => c.to === selectedId);

  const handleMove = useCallback((id: string, x: number, y: number) => {
    setBlocks((prev) => prev.map((b) => (b.id === id ? { ...b, x, y } : b)));
  }, []);

  const handleConnect = useCallback((from: string, to: string) => {
    setConnections((prev) => {
      if (prev.some((c) => c.from === from && c.to === to)) return prev;
      return [...prev, { from, to }];
    });
  }, []);

  const handleDisconnect = useCallback((from: string, to: string) => {
    setConnections((prev) => prev.filter((c) => !(c.from === from && c.to === to)));
  }, []);

  const addBlock = (item: typeof palette[number]) => {
    const id = `b${nextBlockNum++}`;
    const offset = blocks.length * 24;
    const newBlock: RuleBlock = {
      id,
      type: item.type,
      label: item.defaultLabel,
      x: 40 + (offset % 200),
      y: 40 + (offset % 300),
      config: { ...item.defaultConfig },
    };
    setBlocks((prev) => [...prev, newBlock]);
    setSelectedId(id);
  };

  const updateBlock = (id: string, patch: Partial<RuleBlock>) => {
    setBlocks((prev) => prev.map((b) => (b.id === id ? { ...b, ...patch } : b)));
  };

  const updateConfig = (id: string, key: string, value: string) => {
    setBlocks((prev) =>
      prev.map((b) => (b.id === id ? { ...b, config: { ...b.config, [key]: value } } : b))
    );
  };

  const deleteBlock = (id: string) => {
    setBlocks((prev) => prev.filter((b) => b.id !== id));
    setConnections((prev) => prev.filter((c) => c.from !== id && c.to !== id));
    setSelectedId(null);
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
      <div className="lg:col-span-8">
        <RuleCanvas
          blocks={blocks}
          connections={connections}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onMove={handleMove}
          onConnect={handleConnect}
        />
      </div>

      <div className="lg:col-span-4 space-y-4">
        {readOnlyMeta}

        {ruleMeta && (
          <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
            <h3 className="text-sm font-medium text-white mb-3">Правило</h3>
            <div className="space-y-3 text-sm">
              <div>
                <label htmlFor={`${formId}-name`} className="text-xs text-gray-500">Название</label>
                <input
                  id={`${formId}-name`}
                  type="text"
                  defaultValue={ruleMeta.name}
                  className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                />
              </div>
              {ruleMeta.ruleType !== undefined && (
                <div>
                  <label htmlFor={`${formId}-type`} className="text-xs text-gray-500">Тип</label>
                  <select
                    id={`${formId}-type`}
                    defaultValue={ruleMeta.ruleType}
                    className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                  >
                    <option value="dedup">Дедупликация</option>
                    <option value="threshold">Порог</option>
                    <option value="problem_resolution">Problem–Resolution</option>
                    <option value="correlation">Корреляция</option>
                  </select>
                </div>
              )}
              {ruleMeta.description !== undefined && (
                <div>
                  <label htmlFor={`${formId}-desc`} className="text-xs text-gray-500">Описание</label>
                  <textarea
                    id={`${formId}-desc`}
                    defaultValue={ruleMeta.description}
                    rows={2}
                    className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs resize-none"
                  />
                </div>
              )}
            </div>
          </div>
        )}

        <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
          <h3 className="text-sm font-medium text-white mb-2">Палитра блоков</h3>
          <p className="text-[10px] text-gray-500 mb-2">Нажмите, чтобы добавить на холст</p>
          <div className="space-y-1">
            {palette.map((item, i) => (
              <button
                key={`${item.type}-${item.label}-${i}`}
                type="button"
                onClick={() => addBlock(item)}
                className="w-full text-left px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-gray-400 text-xs hover:border-[#4a9eff]/50 hover:text-gray-200 transition-colors"
              >
                + {item.label}
              </button>
            ))}
          </div>
        </div>

        <div className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-4">
          <h3 className="text-sm font-medium text-white mb-3">Свойства блока</h3>
          {!selected ? (
            <p className="text-xs text-gray-500">Выберите блок на холсте или добавьте из палитры</p>
          ) : (
            <div className="space-y-3 text-sm">
              <div>
                <span className="text-xs text-gray-500">Тип блока</span>
                <div className="text-gray-300 text-xs mt-0.5">{typeLabels[selected.type]}</div>
              </div>
              <div>
                <label htmlFor={`${formId}-block-label`} className="text-xs text-gray-500">Подпись на холсте</label>
                <input
                  id={`${formId}-block-label`}
                  type="text"
                  value={selected.label}
                  onChange={(e) => updateBlock(selected.id, { label: e.target.value })}
                  className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                />
              </div>

              {selected.type === 'trigger' && (
                <div>
                  <label htmlFor={`${formId}-trigger`} className="text-xs text-gray-500">Триггер</label>
                  <select
                    id={`${formId}-trigger`}
                    value={selected.config.triggerType ?? 'stream'}
                    onChange={(e) => {
                      const triggerType = e.target.value;
                      updateConfig(selected.id, 'triggerType', triggerType);
                      updateBlock(selected.id, {
                        label: triggerType === 'fm' ? 'Событие FM' : 'Событие потока',
                      });
                    }}
                    className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                  >
                    <option value="stream">Событие потока</option>
                    <option value="fm">Событие FM</option>
                  </select>
                </div>
              )}

              {selected.type === 'condition' && (
                <>
                  <div>
                    <label htmlFor={`${formId}-field`} className="text-xs text-gray-500">Поле</label>
                    <select
                      id={`${formId}-field`}
                      value={selected.config.field ?? 'severity'}
                      onChange={(e) => updateConfig(selected.id, 'field', e.target.value)}
                      className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                    >
                      <option value="severity">severity</option>
                      <option value="source_id">source_id</option>
                      <option value="ci_id">ci_id</option>
                      <option value="title">title</option>
                      <option value="status">status</option>
                    </select>
                  </div>
                  <div>
                    <label htmlFor={`${formId}-op`} className="text-xs text-gray-500">Оператор</label>
                    <select
                      id={`${formId}-op`}
                      value={selected.config.operator ?? 'eq'}
                      onChange={(e) => updateConfig(selected.id, 'operator', e.target.value)}
                      className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                    >
                      <option value="eq">Равно</option>
                      <option value="contains">Содержит</option>
                      <option value="gt">Больше</option>
                      <option value="in">В списке</option>
                    </select>
                  </div>
                  <div>
                    <label htmlFor={`${formId}-val`} className="text-xs text-gray-500">Значение</label>
                    <input
                      id={`${formId}-val`}
                      type="text"
                      value={selected.config.value ?? ''}
                      onChange={(e) => {
                        const value = e.target.value;
                        updateConfig(selected.id, 'value', value);
                        const field = selected.config.field ?? 'severity';
                        const op = selected.config.operator === 'eq' ? '=' : selected.config.operator ?? '=';
                        updateBlock(selected.id, { label: `${field} ${op} ${value}` });
                      }}
                      className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs font-mono"
                    />
                  </div>
                </>
              )}

              {selected.type === 'dedup' && (
                <div>
                  <label htmlFor={`${formId}-dedup-key`} className="text-xs text-gray-500">Ключ дедупликации</label>
                  <input
                    id={`${formId}-dedup-key`}
                    type="text"
                    value={selected.config.key ?? ''}
                    onChange={(e) => updateConfig(selected.id, 'key', e.target.value)}
                    className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs font-mono"
                  />
                </div>
              )}

              {selected.type === 'threshold' && (
                <>
                  <div>
                    <label htmlFor={`${formId}-count`} className="text-xs text-gray-500">Количество событий</label>
                    <input
                      id={`${formId}-count`}
                      type="number"
                      min={1}
                      value={selected.config.count ?? '5'}
                      onChange={(e) => {
                        updateConfig(selected.id, 'count', e.target.value);
                        const windowMin = selected.config.windowMin ?? '10';
                        updateBlock(selected.id, { label: `Порог: ${e.target.value} за ${windowMin} мин` });
                      }}
                      className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                    />
                  </div>
                  <div>
                    <label htmlFor={`${formId}-window`} className="text-xs text-gray-500">Окно (мин)</label>
                    <input
                      id={`${formId}-window`}
                      type="number"
                      min={1}
                      value={selected.config.windowMin ?? '10'}
                      onChange={(e) => {
                        updateConfig(selected.id, 'windowMin', e.target.value);
                        const count = selected.config.count ?? '5';
                        updateBlock(selected.id, { label: `Порог: ${count} за ${e.target.value} мин` });
                      }}
                      className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                    />
                  </div>
                </>
              )}

              {selected.type === 'status' && (
                <div>
                  <label htmlFor={`${formId}-status`} className="text-xs text-gray-500">Новый статус</label>
                  <select
                    id={`${formId}-status`}
                    value={selected.config.status ?? 'in_progress'}
                    onChange={(e) => {
                      updateConfig(selected.id, 'status', e.target.value);
                      const labels: Record<string, string> = {
                        in_progress: 'В работе',
                        closed: 'Закрыт',
                        deferred: 'Отложен',
                        maintenance: 'Обслуживание',
                      };
                      updateBlock(selected.id, { label: `Статус → ${labels[e.target.value] ?? e.target.value}` });
                    }}
                    className="w-full mt-1 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                  >
                    <option value="in_progress">В работе</option>
                    <option value="closed">Закрыт</option>
                    <option value="deferred">Отложен</option>
                    <option value="maintenance">Обслуживание</option>
                  </select>
                </div>
              )}

              {selected.type === 'switch' && (
                <p className="text-xs text-gray-500">Switch поддерживает несколько исходящих связей (ветки)</p>
              )}

              <div className="pt-2 border-t border-[#3a3f4b]">
                <h4 className="text-xs font-medium text-white mb-2">Связи</h4>
                <div className="space-y-2">
                  <div>
                    <span className="text-[10px] text-gray-500 uppercase">Исходящие →</span>
                    {outgoing.length === 0 ? (
                      <p className="text-xs text-gray-600 mt-1">Нет исходящих связей</p>
                    ) : (
                      <ul className="mt-1 space-y-1">
                        {outgoing.map((c) => {
                          const target = blocks.find((b) => b.id === c.to);
                          return (
                            <li key={`${c.from}-${c.to}`} className="flex items-center justify-between gap-2 text-xs">
                              <span className="text-gray-300 truncate">{target?.label ?? c.to}</span>
                              <button
                                type="button"
                                onClick={() => handleDisconnect(c.from, c.to)}
                                className="text-red-400 hover:text-red-300 shrink-0"
                                title="Удалить связь"
                              >
                                ×
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>
                  <div>
                    <span className="text-[10px] text-gray-500 uppercase">← Входящие</span>
                    {incoming.length === 0 ? (
                      <p className="text-xs text-gray-600 mt-1">Нет входящих связей</p>
                    ) : (
                      <ul className="mt-1 space-y-1">
                        {incoming.map((c) => {
                          const source = blocks.find((b) => b.id === c.from);
                          return (
                            <li key={`${c.from}-${c.to}`} className="flex items-center justify-between gap-2 text-xs">
                              <span className="text-gray-300 truncate">{source?.label ?? c.from}</span>
                              <button
                                type="button"
                                onClick={() => handleDisconnect(c.from, c.to)}
                                className="text-red-400 hover:text-red-300 shrink-0"
                                title="Удалить связь"
                              >
                                ×
                              </button>
                            </li>
                          );
                        })}
                      </ul>
                    )}
                  </div>
                  <div className="flex gap-1 pt-1">
                    <select
                      id={`${formId}-link-target`}
                      defaultValue=""
                      className="flex-1 min-w-0 px-2 py-1.5 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-xs"
                    >
                      <option value="" disabled>К блоку…</option>
                      {blocks.filter((b) => b.id !== selected.id).map((b) => (
                        <option key={b.id} value={b.id}>{b.label}</option>
                      ))}
                    </select>
                    <button
                      type="button"
                      onClick={() => {
                        const sel = document.getElementById(`${formId}-link-target`) as HTMLSelectElement;
                        const to = sel?.value;
                        if (to) {
                          handleConnect(selected.id, to);
                          sel.value = '';
                        }
                      }}
                      className="px-2 py-1.5 text-xs bg-[#4a9eff] text-white rounded hover:bg-[#3a8eef] shrink-0"
                    >
                      Связать →
                    </button>
                  </div>
                </div>
              </div>

              {selected.type !== 'trigger' && (
                <button
                  type="button"
                  onClick={() => deleteBlock(selected.id)}
                  className="w-full mt-2 px-3 py-1.5 text-xs text-red-400 border border-red-500/30 rounded hover:bg-red-500/10"
                >
                  Удалить блок
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
