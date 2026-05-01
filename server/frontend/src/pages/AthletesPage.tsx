import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import { User } from '../types'
import { formatDistanceToNow } from 'date-fns'

export default function AthletesPage() {
  const navigate = useNavigate()
  const [athletes, setAthletes] = useState<User[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [showPassword, setShowPassword] = useState(false)
  const [formData, setFormData] = useState({ username: '', password: '' })

  useEffect(() => { loadAthletes() }, [])

  const loadAthletes = async () => {
    try {
      setIsLoading(true)
      const response = await client.get('/users/athletes')
      setAthletes(response.data)
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Błąd ładowania zawodników')
    } finally {
      setIsLoading(false)
    }
  }

  const handleOpenModal = (athlete?: User) => {
    if (athlete) {
      setEditingId(athlete.id)
      setFormData({ username: athlete.username, password: '' })
    } else {
      setEditingId(null)
      setFormData({ username: '', password: '' })
    }
    setShowPassword(false)
    setShowModal(true)
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      if (editingId) {
        const body: any = { username: formData.username }
        if (formData.password.trim()) body.password = formData.password
        await client.put(`/users/athletes/${editingId}`, body)
      } else {
        await client.post('/users/athletes', {
          username: formData.username,
          password: formData.password,
        })
      }
      setShowModal(false)
      loadAthletes()
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Błąd zapisu')
    }
  }

  const handleDelete = async (id: number) => {
    if (window.confirm('Na pewno usunąć tego zawodnika?')) {
      try {
        await client.delete(`/users/athletes/${id}`)
        loadAthletes()
      } catch (err: any) {
        setError(err.response?.data?.detail || 'Błąd usuwania')
      }
    }
  }

  if (isLoading) {
    return <div className="flex items-center justify-center h-96 text-gray-400">Ładowanie zawodników...</div>
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-white">Zawodnicy</h1>
        <button
          onClick={() => handleOpenModal()}
          className="px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white rounded-lg font-medium transition-colors"
        >
          Dodaj zawodnika
        </button>
      </div>

      {error && (
        <div className="p-4 bg-red-900/30 border border-red-700 rounded text-red-200 flex justify-between">
          {error}
          <button onClick={() => setError('')} className="text-red-400 hover:text-red-200">✕</button>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {athletes.length === 0 ? (
          <div className="col-span-full text-center py-12 text-gray-400">
            Brak zawodników. Dodaj pierwszego.
          </div>
        ) : (
          athletes.map((athlete) => (
            <div
              key={athlete.id}
              className="bg-gray-800 border border-gray-700 rounded-lg p-6 hover:border-violet-700 transition-colors cursor-pointer"
              onClick={() => navigate(`/athletes/${athlete.id}`)}
            >
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h3 className="text-lg font-semibold text-white">{athlete.username}</h3>
                </div>
                <span className={`px-3 py-1 rounded text-xs font-medium ${
                  athlete.is_active ? 'bg-green-900/30 text-green-200' : 'bg-gray-700 text-gray-300'
                }`}>
                  {athlete.is_active ? 'Aktywny' : 'Nieaktywny'}
                </span>
              </div>

              <p className="text-xs text-gray-500 mb-4">
                Dołączył {formatDistanceToNow(new Date(athlete.created_at), { addSuffix: true })}
              </p>

              <div className="flex gap-2">
                <button
                  onClick={(e) => { e.stopPropagation(); handleOpenModal(athlete) }}
                  className="flex-1 px-3 py-2 bg-gray-700 hover:bg-gray-600 text-white text-sm rounded transition-colors"
                >
                  Edytuj
                </button>
                <button
                  onClick={(e) => { e.stopPropagation(); handleDelete(athlete.id) }}
                  className="flex-1 px-3 py-2 bg-red-900/30 hover:bg-red-900/50 text-red-200 text-sm rounded transition-colors"
                >
                  Usuń
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
          <div className="bg-gray-800 rounded-lg p-6 w-full max-w-md border border-gray-700">
            <h2 className="text-xl font-bold text-white mb-4">
              {editingId ? 'Edytuj zawodnika' : 'Dodaj zawodnika'}
            </h2>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">Nazwa użytkownika</label>
                <input
                  type="text"
                  value={formData.username}
                  onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                  className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white focus:outline-none focus:border-violet-600"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  {editingId ? 'Nowe hasło (zostaw puste żeby nie zmieniać)' : 'Hasło'}
                </label>
                <div className="relative">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    value={formData.password}
                    onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                    placeholder={editingId ? '••••••••' : 'min. 6 znaków'}
                    className="w-full px-4 py-2 pr-16 bg-gray-700 border border-gray-600 rounded text-white focus:outline-none focus:border-violet-600 placeholder-gray-500"
                    required={!editingId}
                    minLength={editingId ? undefined : 6}
                  />
                  <button type="button" onClick={() => setShowPassword(!showPassword)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-200 text-xs">
                    {showPassword ? 'Ukryj' : 'Pokaż'}
                  </button>
                </div>
              </div>

              <div className="flex gap-3 pt-2">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="flex-1 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded transition-colors"
                >
                  Anuluj
                </button>
                <button
                  type="submit"
                  className="flex-1 px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white rounded font-medium transition-colors"
                >
                  Zapisz
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
