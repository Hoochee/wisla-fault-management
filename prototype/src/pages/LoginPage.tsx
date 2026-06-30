import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

export function LoginPage() {
  const [login, setLogin] = useState('ivanov');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    navigate('/');
  };

  return (
    <div className="min-h-screen bg-[#1a1d23] flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 rounded-xl bg-[#4a9eff] text-white text-2xl font-bold mb-4">W</div>
          <h1 className="text-2xl font-semibold text-white">WISLA Fault Management</h1>
          <p className="text-gray-500 text-sm mt-1">Консоль оператора NOC</p>
        </div>

        <form onSubmit={handleSubmit} className="bg-[#252830] border border-[#3a3f4b] rounded-lg p-6 space-y-4">
          <div>
            <label className="block text-xs text-gray-400 mb-1">Логин</label>
            <input
              type="text"
              value={login}
              onChange={(e) => setLogin(e.target.value)}
              className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm focus:outline-none focus:border-[#4a9eff]"
            />
          </div>
          <div>
            <label className="block text-xs text-gray-400 mb-1">Пароль</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full px-3 py-2 bg-[#1a1d23] border border-[#3a3f4b] rounded text-white text-sm focus:outline-none focus:border-[#4a9eff]"
            />
          </div>
          <button
            type="submit"
            className="w-full py-2.5 bg-[#4a9eff] text-white rounded font-medium text-sm hover:bg-[#3a8eef] transition-colors"
          >
            Войти
          </button>
          <p className="text-[10px] text-gray-500 text-center">
            Локальная аутентификация · AD — post-MVP
          </p>
        </form>
      </div>
    </div>
  );
}
