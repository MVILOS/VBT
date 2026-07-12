import { useEffect, useState } from 'react'
import client from '../api/client'
import { User } from '../types'

interface EditState {
  userId: number
  username: string
  password: string
  role: string
  coachId: string
  isActive: boolean
}

const ROLE_LABELS: Record<string, string> = {
  admin: 'Administrator',
  coach: 'Trener',
  athlete: 'Zawodnik',
}

const ROLE_COLORS: Record<string, string> = {
  admin: 'text-red-600 bg-red-50 border-red-300',
  coach: 'text-violet-600 bg-violet-50 border-violet-400',
  athlete: 'text-cyan-400 bg-cyan-900/20 border-cyan-700',
}

export default function AdminPage() {
  const [users, setUsers] = useState<User[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const [editState, setEditState] = useState<EditState | null>(null)
  const [showEditPassword, setShowEditPassword] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  const [showCreatePassword, setShowCreatePassword] = useState(false)
  const [newUser, setNewUser] = useState({ username: '', password: '', role: 'athlete', coachId: '' })
  const [filterRole, setFilterRole] = useState<string>('all')
  const [search, setSearch] = useState('')

  const loadUsers = async () => {
    try {
      const r = await client.get('/admin/users')
      setUsers(r.data)
    } catch {
      setError('Błąd ładowania użytkowników')
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => { loadUsers() }, [])

  const notify = (msg: string) => {
    setSuccess(msg)
    setTimeout(() => setSuccess(''), 3000)
  }

  const startEdit = (u: User) => {
    setEditState({
      userId: u.id,
      username: u.username,
      password: '',
      role: u.role,
      coachId: u.coach_id ? String(u.coach_id) : '',
      isActive: u.is_active,
    })
    setShowEditPassword(false)
  }

  const saveEdit = async () => {
    if (!editState) return
    try {
      const body: any = {
        username: editState.username,
        role: editState.role,
        is_active: editState.isActive,
        coach_id: editState.coachId ? parseInt(editState.coachId) : null,
      }
      if (editState.password.trim()) body.password = editState.password
      await client.put(`/admin/users/${editState.userId}`, body)
      setEditState(null)
      await loadUsers()
      notify('Użytkownik zaktualizowany')
    } catch (e: any) {
      setError(e.response?.data?.detail || 'Błąd zapisu')
    }
  }

  const deleteUser = async (userId: number, username: string) => {
    if (!confirm(`Usunąć użytkownika "${username}"? Tej operacji nie można cofnąć.`)) return
    try {
      await client.delete(`/admin/users/${userId}`)
      await loadUsers()
      notify(`Użytkownik ${username} usunięty`)
    } catch (e: any) {
      setError(e.response?.data?.detail || 'Błąd usuwania')
    }
  }

  const createUser = async () => {
    try {
      const body: any = {
        username: newUser.username,
        password: newUser.password,
        role: newUser.role,
        coach_id: newUser.coachId ? parseInt(newUser.coachId) : null,
      }
      await client.post('/admin/users', body)
      setNewUser({ username: '', password: '', role: 'athlete', coachId: '' })
      setShowCreate(false)
      setShowCreatePassword(false)
      await loadUsers()
      notify('Użytkownik utworzony')
    } catch (e: any) {
      setError(e.response?.data?.detail || 'Błąd tworzenia')
    }
  }

  const coaches = users.filter(u => u.role === 'coach' || u.role === 'admin')

  const filtered = users.filter(u => {
    const matchRole = filterRole === 'all' || u.role === filterRole
    const matchSearch = !search || u.username.toLowerCase().includes(search.toLowerCase())
    return matchRole && matchSearch
  })

  if (isLoading) return <div className="flex items-center justify-center h-96 text-gray-500">Ładowanie...</div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">Administracja</h1>
        <button
          onClick={() => setShowCreate(!showCreate)}
          className="px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white rounded-lg font-medium text-sm"
        >
          + Nowy użytkownik
        </button>
      </div>

      {/* Notifications */}
      {error && (
        <div className="p-3 bg-red-50 border border-red-300 rounded-lg text-red-700 text-sm flex justify-between">
          {error}
          <button onClick={() => setError('')} className="text-red-600 hover:text-red-700">✕</button>
        </div>
      )}
      {success && (
        <div className="p-3 bg-green-50 border border-green-700 rounded-lg text-green-700 text-sm">
          ✓ {success}
        </div>
      )}

      {/* Create user panel */}
      {showCreate && (
        <div className="bg-white border border-gray-200 rounded-lg p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Utwórz nowego użytkownika</h2>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Nazwa użytkownika</label>
              <input type="text" value={newUser.username} onChange={e => setNewUser({ ...newUser, username: e.target.value })}
                className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none" />
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Hasło</label>
              <div className="relative">
                <input
                  type={showCreatePassword ? 'text' : 'password'}
                  value={newUser.password}
                  onChange={e => setNewUser({ ...newUser, password: e.target.value })}
                  className="w-full px-3 py-2 pr-10 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none"
                />
                <button type="button" onClick={() => setShowCreatePassword(!showCreatePassword)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-800 text-xs">
                  {showCreatePassword ? 'Ukryj' : 'Pokaż'}
                </button>
              </div>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Rola</label>
              <select value={newUser.role} onChange={e => setNewUser({ ...newUser, role: e.target.value })}
                className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none">
                <option value="athlete">Zawodnik</option>
                <option value="coach">Trener</option>
                <option value="admin">Administrator</option>
              </select>
            </div>
            {newUser.role === 'athlete' && (
              <div>
                <label className="block text-xs text-gray-500 mb-1">Przypisz do trenera</label>
                <select value={newUser.coachId} onChange={e => setNewUser({ ...newUser, coachId: e.target.value })}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none">
                  <option value="">— brak —</option>
                  {coaches.map(c => <option key={c.id} value={c.id}>{c.username}</option>)}
                </select>
              </div>
            )}
          </div>
          <div className="flex gap-3 mt-4">
            <button onClick={createUser}
              disabled={!newUser.username || !newUser.password}
              className="px-4 py-2 bg-violet-600 hover:bg-violet-700 disabled:opacity-50 text-white rounded font-medium text-sm">
              Utwórz
            </button>
            <button onClick={() => setShowCreate(false)} className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded text-sm">
              Anuluj
            </button>
          </div>
        </div>
      )}

      {/* Stats */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        {(['admin', 'coach', 'athlete'] as const).map(r => (
          <div key={r} className={`rounded-lg p-4 border ${ROLE_COLORS[r]}`}>
            <div className="text-2xl font-bold">{users.filter(u => u.role === r).length}</div>
            <div className="text-sm mt-1">{ROLE_LABELS[r]}ów</div>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex gap-3 items-center">
        <input
          type="text"
          placeholder="Szukaj po nazwie użytkownika..."
          value={search}
          onChange={e => setSearch(e.target.value)}
          className="flex-1 px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none placeholder-gray-500"
        />
        <div className="flex gap-1">
          {['all', 'admin', 'coach', 'athlete'].map(r => (
            <button key={r} onClick={() => setFilterRole(r)}
              className={`px-3 py-2 text-xs font-medium rounded transition-colors ${
                filterRole === r ? 'bg-violet-600 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'
              }`}>
              {r === 'all' ? 'Wszyscy' : ROLE_LABELS[r]}
            </button>
          ))}
        </div>
      </div>

      {/* Users table */}
      <div className="bg-white border border-gray-200 rounded-lg overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-gray-200 bg-gray-100">
              <th className="text-left py-3 px-4 text-xs text-gray-500 font-semibold">UŻYTKOWNIK</th>
              <th className="text-left py-3 px-4 text-xs text-gray-500 font-semibold">ROLA</th>
              <th className="text-left py-3 px-4 text-xs text-gray-500 font-semibold">TRENER</th>
              <th className="text-left py-3 px-4 text-xs text-gray-500 font-semibold">STATUS</th>
              <th className="text-right py-3 px-4 text-xs text-gray-500 font-semibold">AKCJE</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(u => (
              <tr key={u.id} className="border-b border-gray-200 hover:bg-gray-100">
                <td className="py-3 px-4">
                  <div className="font-medium text-gray-900 text-sm">{u.username}</div>
                  <div className="text-xs text-gray-500">ID: {u.id}</div>
                </td>
                <td className="py-3 px-4">
                  <span className={`px-2 py-0.5 rounded text-xs font-medium border ${ROLE_COLORS[u.role]}`}>
                    {ROLE_LABELS[u.role] ?? u.role}
                  </span>
                </td>
                <td className="py-3 px-4 text-gray-500 text-sm">
                  {u.coach_id ? (users.find(c => c.id === u.coach_id)?.username ?? `#${u.coach_id}`) : '—'}
                </td>
                <td className="py-3 px-4">
                  <span className={`px-2 py-0.5 rounded text-xs font-medium ${u.is_active ? 'text-green-600 bg-green-50' : 'text-red-600 bg-red-50'}`}>
                    {u.is_active ? 'Aktywny' : 'Nieaktywny'}
                  </span>
                </td>
                <td className="py-3 px-4 text-right">
                  <button onClick={() => startEdit(u)}
                    className="px-3 py-1 text-xs bg-gray-100 hover:bg-gray-200 text-gray-800 rounded mr-2">
                    Edytuj
                  </button>
                  <button onClick={() => deleteUser(u.id, u.username)}
                    className="px-3 py-1 text-xs bg-red-50 hover:bg-red-100 text-red-700 rounded">
                    Usuń
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && (
          <div className="text-center py-8 text-gray-500 text-sm">Brak użytkowników spełniających kryteria</div>
        )}
      </div>

      {/* Edit modal */}
      {editState && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="bg-white border border-gray-200 rounded-lg p-6 w-full max-w-md">
            <h2 className="text-lg font-semibold text-gray-900 mb-4">Edytuj: {users.find(u => u.id === editState.userId)?.username}</h2>
            <div className="space-y-3">
              <div>
                <label className="block text-xs text-gray-500 mb-1">Nazwa użytkownika</label>
                <input type="text" value={editState.username} onChange={e => setEditState({ ...editState, username: e.target.value })}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">Nowe hasło (zostaw puste żeby nie zmieniać)</label>
                <div className="relative">
                  <input
                    type={showEditPassword ? 'text' : 'password'}
                    value={editState.password}
                    onChange={e => setEditState({ ...editState, password: e.target.value })}
                    placeholder="••••••••"
                    className="w-full px-3 py-2 pr-16 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none placeholder-gray-500"
                  />
                  <button type="button" onClick={() => setShowEditPassword(!showEditPassword)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-500 hover:text-gray-800 text-xs">
                    {showEditPassword ? 'Ukryj' : 'Pokaż'}
                  </button>
                </div>
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">Rola</label>
                <select value={editState.role} onChange={e => setEditState({ ...editState, role: e.target.value })}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none">
                  <option value="athlete">Zawodnik</option>
                  <option value="coach">Trener</option>
                  <option value="admin">Administrator</option>
                </select>
              </div>
              {editState.role === 'athlete' && (
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Przypisz do trenera</label>
                  <select value={editState.coachId} onChange={e => setEditState({ ...editState, coachId: e.target.value })}
                    className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:border-violet-400 outline-none">
                    <option value="">— brak —</option>
                    {coaches.map(c => <option key={c.id} value={c.id}>{c.username}</option>)}
                  </select>
                </div>
              )}
              <div className="flex items-center gap-3 pt-1">
                <label className="relative inline-flex items-center cursor-pointer">
                  <input type="checkbox" checked={editState.isActive} onChange={e => setEditState({ ...editState, isActive: e.target.checked })} className="sr-only" />
                  <div className={`w-10 h-5 rounded-full transition-colors ${editState.isActive ? 'bg-green-600' : 'bg-gray-200'}`}>
                    <div className={`absolute top-0.5 w-4 h-4 bg-white rounded-full transition-transform ${editState.isActive ? 'translate-x-5' : 'translate-x-0.5'}`} />
                  </div>
                </label>
                <span className="text-sm text-gray-700">Konto aktywne</span>
              </div>
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={saveEdit} className="px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white rounded font-medium text-sm">
                Zapisz
              </button>
              <button onClick={() => setEditState(null)} className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded text-sm">
                Anuluj
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
