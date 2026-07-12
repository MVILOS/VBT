import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import client from '../api/client'

export default function LoginPage() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      await login(username, password)
      navigate('/')
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Nieprawidłowe dane logowania.')
    } finally {
      setIsLoading(false)
    }
  }

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      await client.post('/auth/register', { username, password })
      // Auto-login after register
      await login(username, password)
      navigate('/')
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Błąd rejestracji.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md bg-white rounded-lg shadow-lg p-8 border border-gray-200">
        <h1 className="text-3xl font-bold text-center text-violet-600 mb-6">VBT Coach</h1>

        {/* Tab switcher */}
        <div className="flex mb-6 border border-gray-200 rounded-lg overflow-hidden">
          {(['login', 'register'] as const).map(m => (
            <button
              key={m}
              onClick={() => { setMode(m); setError('') }}
              className={`flex-1 py-2 text-sm font-medium transition-colors ${
                mode === m ? 'bg-violet-600 text-white' : 'text-gray-500 hover:text-gray-800'
              }`}
            >
              {m === 'login' ? 'Logowanie' : 'Rejestracja'}
            </button>
          ))}
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-300 rounded text-red-700 text-sm">
            {error}
          </div>
        )}

        {mode === 'login' ? (
          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Nazwa użytkownika</label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="twoja_nazwa"
                className="w-full px-4 py-2 bg-gray-100 border border-gray-300 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:border-violet-400 focus:ring-1 focus:ring-violet-500"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Hasło</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full px-4 py-2 bg-gray-100 border border-gray-300 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:border-violet-400 focus:ring-1 focus:ring-violet-500"
                required
              />
            </div>
            <button
              type="submit"
              disabled={isLoading}
              className="w-full mt-2 px-4 py-2 bg-violet-600 hover:bg-violet-700 disabled:opacity-50 text-white font-semibold rounded-lg transition-colors"
            >
              {isLoading ? 'Logowanie...' : 'Zaloguj się'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister} className="space-y-4">
            <p className="text-xs text-gray-500 -mt-1">
              Rejestracja dostępna tylko dla zawodników. Trenerów dodaje administrator.
            </p>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Nazwa użytkownika</label>
              <input
                type="text"
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="twoja_nazwa"
                className="w-full px-4 py-2 bg-gray-100 border border-gray-300 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:border-violet-400 focus:ring-1 focus:ring-violet-500"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Hasło</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="min. 6 znaków"
                className="w-full px-4 py-2 bg-gray-100 border border-gray-300 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:border-violet-400 focus:ring-1 focus:ring-violet-500"
                required
                minLength={6}
              />
            </div>
            <button
              type="submit"
              disabled={isLoading}
              className="w-full mt-2 px-4 py-2 bg-violet-600 hover:bg-violet-700 disabled:opacity-50 text-white font-semibold rounded-lg transition-colors"
            >
              {isLoading ? 'Tworzenie konta...' : 'Utwórz konto'}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
