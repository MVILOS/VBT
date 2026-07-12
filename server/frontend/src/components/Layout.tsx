import { useState } from 'react'
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
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const menuItems = user?.role === 'admin' ? adminMenuItems : user?.role === 'athlete' ? athleteMenuItems : coachMenuItems

  const sidebarContent = (
    <>
      <div className="p-6 border-b border-gray-200 flex items-center justify-between">
        <h1 className="text-2xl font-bold text-violet-600">VBT Coach</h1>
        <button
          onClick={() => setMobileNavOpen(false)}
          className="md:hidden text-gray-500 text-xl leading-none"
          aria-label="Zamknij menu"
        >
          ✕
        </button>
      </div>

      <nav className="flex-1 p-4 space-y-2 overflow-y-auto">
        {menuItems.map((item) => {
          const isActive = location.pathname === item.path
          return (
            <Link
              key={item.path}
              to={item.path}
              onClick={() => setMobileNavOpen(false)}
              className={`flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                isActive
                  ? 'bg-violet-600 text-white'
                  : 'text-gray-700 hover:bg-gray-100'
              }`}
            >
              <span className="text-lg">{item.icon}</span>
              <span>{item.label}</span>
            </Link>
          )
        })}
      </nav>

      <div className="p-4 border-t border-gray-200">
        <button
          onClick={logout}
          className="w-full px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors text-sm font-medium"
        >
          Logout
        </button>
      </div>
    </>
  )

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex w-64 bg-white border-r border-gray-200 flex-col">
        {sidebarContent}
      </aside>

      {/* Mobile sidebar (off-canvas) */}
      {mobileNavOpen && (
        <div className="md:hidden fixed inset-0 z-40 flex">
          <div
            className="fixed inset-0 bg-black/40"
            onClick={() => setMobileNavOpen(false)}
          />
          <aside className="relative w-64 max-w-[80vw] bg-white border-r border-gray-200 flex flex-col z-50">
            {sidebarContent}
          </aside>
        </div>
      )}

      <div className="flex-1 flex flex-col min-w-0">
        <header className="bg-white border-b border-gray-200 px-4 md:px-6 py-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <button
              onClick={() => setMobileNavOpen(true)}
              className="md:hidden text-gray-700 text-2xl leading-none px-1"
              aria-label="Otwórz menu"
            >
              ☰
            </button>
            <h2 className="text-lg md:text-xl font-semibold text-gray-900 truncate">VBT Coach Platform</h2>
          </div>
          <div className="flex items-center gap-2 md:gap-4 shrink-0">
            <span className="hidden sm:inline text-gray-700 text-sm">{user?.username}</span>
            <span className="text-gray-500 text-xs bg-gray-100 px-3 py-1 rounded">
              {user?.role}
            </span>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-3 md:p-6 bg-gray-50">
          {children}
        </main>
      </div>
    </div>
  )
}
