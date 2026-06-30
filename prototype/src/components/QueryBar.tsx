export function QueryBar() {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <div className="flex items-center gap-1 flex-1 min-w-[200px]">
        <button type="button" className="px-2 py-1 text-xs border border-[#3a3f4b] rounded text-gray-400 hover:text-white hover:border-[#4a9eff]">
          +
        </button>
        <div className="flex items-center gap-1 px-2 py-1 bg-[#1a1d23] border border-[#3a3f4b] rounded text-xs">
          <span className="text-gray-500">severity</span>
          <span className="text-gray-400">=</span>
          <span className="text-[#4a9eff]">critical</span>
          <button type="button" className="ml-1 text-gray-500 hover:text-white">×</button>
        </div>
        <span className="text-xs text-gray-500">AND</span>
        <div className="flex items-center gap-1 px-2 py-1 bg-[#1a1d23] border border-[#3a3f4b] rounded text-xs">
          <span className="text-gray-500">status</span>
          <span className="text-gray-400">≠</span>
          <span className="text-[#4a9eff]">closed</span>
          <button type="button" className="ml-1 text-gray-500 hover:text-white">×</button>
        </div>
      </div>
      <input
        type="text"
        placeholder="Поиск..."
        className="px-3 py-1.5 text-xs bg-[#1a1d23] border border-[#3a3f4b] rounded text-gray-300 placeholder-gray-500 w-48 focus:outline-none focus:border-[#4a9eff]"
      />
      <input
        type="datetime-local"
        className="px-2 py-1.5 text-xs bg-[#1a1d23] border border-[#3a3f4b] rounded text-gray-300 focus:outline-none focus:border-[#4a9eff]"
      />
      <button type="button" className="px-2 py-1.5 text-xs border border-[#3a3f4b] rounded text-gray-300 hover:bg-[#3a3f4b]/50" title="Обновить">
        ⟳
      </button>
    </div>
  );
}
