import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

const coachMenuItems = [
  { label: 'Dashboard', path: '/', icon: '📊' },
  { label: 'Harmonogram', path: '/schedule', icon: '📅' },
  { label: 'Zawodnicy', path: '/athletes', icon: '👥' },
  { label: 'Plany', path: '/plans', icon: '📋' },
  { label: 'Analityka', path: '/analytics', icon: '📈' },
]

const athleteMenuItems = [
  { label: 'Dashboard', path: '/', icon: '📊' },
  { label: 'Moje Plany', path: '/plans', icon: '📋' },
  { label: 'Moje Statystyki', path: '/analytics', icon: '📈' },
]

const adminMenuItems = [
  { label: 'Dashboard', path: '/', icon: '📊' },
  { label: 'Harmonogram', path: '/schedule', icon: '📅' },
  { label: 'Zawodnicy', path: '/athletes', icon: '👥' },
  { label: 'Plany', path: '/plans', icon: '📋' },
  { label: 'Analityka', path: '/analytics', icon: '📈' },
  { label: 'Administracja', path: '/admin', icon: '⚙️' },
]

export function Layout({ children }: { children: React.ReactNode }) {
  const location = useLocation()
  const { user, logout } = useAuth()
  const menuItems = user?.role === 'admin' ? adminMenuItems : user?.role === 'athlete' ? athleteMenuItems : coachMenuItems

  return (
    <div className="flex h-screen bg-gray-900">
      <aside className="w-64 bg-gray-800 border-r border-gray-700 flex flex-col">
        <div className="p-6 border-b border-gray-700">
          <h1 className="text-2xl font-bold text-violet-600">VBT Coach</h1>
        </div>

        <nav className="flex-1 p-4 space-y-2">
          {menuItems.map((item) => {
            const isActive = location.pathname === item.path
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                  isActive
                    ? 'bg-violet-600 text-white'
                    : 'text-gray-300 hover:bg-gray-700'
                }`}
              >
                <span className="text-lg">{item.icon}</span>
                <span>{item.label}</span>
              </Link>
            )
          })}
        </nav>

        <div className="p-4 border-t border-gray-700">
          <button
            onClick={logout}
            className="w-full px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors text-sm font-medium"
          >
            Logout
          </button>
        </div>
      </aside>

      <div className="flex-1 flex flex-col">
        <header className="bg-gray-800 border-b border-gray-700 px-6 py-4 flex items-center justify-between">
          <h2 className="text-xl font-semibold text-gray-100">VBT Coach Platform</h2>
          <div className="flex items-center gap-4">
            <span className="text-gray-300 text-sm">{user?.username}</span>
            <span className="text-gray-500 text-xs bg-gray-700 px-3 py-1 rounded">
              {user?.role}
            </span>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-6 bg-gray-900">
          {children}
        </main>
      </div>
    </div>
  )
}
