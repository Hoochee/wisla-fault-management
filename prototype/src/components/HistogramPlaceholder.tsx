const bars = [12, 28, 45, 62, 38, 55, 72, 48, 33, 20, 15, 8, 22, 40, 58, 35, 18, 10, 5, 3];

export function HistogramPlaceholder() {
  const max = Math.max(...bars);
  return (
    <div className="h-16 flex items-end gap-px px-2 py-1 bg-[#1a1d23] border border-[#3a3f4b] rounded">
      {bars.map((h, i) => (
        <div
          key={i}
          className="flex-1 bg-[#4a9eff]/60 hover:bg-[#4a9eff] rounded-t transition-colors cursor-pointer min-w-0"
          style={{ height: `${(h / max) * 100}%` }}
          title={`${h} событий`}
        />
      ))}
    </div>
  );
}
