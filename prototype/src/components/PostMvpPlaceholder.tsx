import { Link } from 'react-router-dom';

interface Props {
  title: string;
  description?: string;
}

export function PostMvpPlaceholder({ title, description = 'Функциональность запланирована после MVP' }: Props) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="w-16 h-16 rounded-full bg-[#252830] border border-[#3a3f4b] flex items-center justify-center mb-4">
        <span className="text-2xl">🚧</span>
      </div>
      <h2 className="text-xl font-medium text-white mb-2">{title}</h2>
      <p className="text-gray-400 text-sm mb-1">{description}</p>
      <span className="inline-block mt-2 px-3 py-1 text-xs rounded-full bg-amber-500/20 text-amber-300 border border-amber-500/30">
        post-MVP
      </span>
      <Link to="/" className="mt-6 text-sm text-[#4a9eff] hover:underline">
        ← Вернуться на Dashboard
      </Link>
    </div>
  );
}
