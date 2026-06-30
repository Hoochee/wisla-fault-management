import type { ReactNode } from 'react';

export interface Column<T> {
  key: string;
  header: string;
  width?: string;
  render: (row: T) => ReactNode;
}

interface Props<T> {
  columns: Column<T>[];
  data: T[];
  selectedId?: string;
  onRowClick?: (row: T) => void;
  onRowDoubleClick?: (row: T) => void;
  rowId: (row: T) => string;
  emptyMessage?: string;
}

export function DataTable<T>({ columns, data, selectedId, onRowClick, onRowDoubleClick, rowId, emptyMessage = 'Нет данных' }: Props<T>) {
  if (data.length === 0) {
    return (
      <div className="flex items-center justify-center py-12 text-gray-500 text-sm">
        {emptyMessage}
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-[#3a3f4b] text-left text-gray-400 text-xs uppercase tracking-wide">
            {columns.map((col) => (
              <th key={col.key} className="px-3 py-2 font-medium whitespace-nowrap" style={col.width ? { width: col.width } : undefined}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => {
            const id = rowId(row);
            const selected = selectedId === id;
            return (
              <tr
                key={id}
                onClick={() => onRowClick?.(row)}
                onDoubleClick={() => onRowDoubleClick?.(row)}
                className={`border-b border-[#3a3f4b]/50 cursor-pointer transition-colors ${
                  selected ? 'bg-[#4a9eff]/10' : 'hover:bg-[#3a3f4b]/30'
                }`}
              >
                {columns.map((col) => (
                  <td key={col.key} className="px-3 py-2 whitespace-nowrap">
                    {col.render(row)}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
