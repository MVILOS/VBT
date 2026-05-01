import { useEffect, useState } from 'react'
import {
  LineChart, Line, BarChart, Bar,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import client from '../api/client'
import { useAuth } from '../context/AuthContext'
import { User, ExerciseDefinition } from '../types'
import { formatDistanceToNow } from 'date-fns'

const tooltipStyle = {
  contentStyle: { backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: 6 },
  labelStyle: { color: '#e5e7eb', fontWeight: 600 },
  itemStyle: { color: '#e5e7eb' },
}

interface DashboardStats { total_athletes: number; active_plans: number; sessions_this_week: number }
interface SessionRow { id: number; athlete_name: string; started_at: string; duration_seconds?: number; reps_count: number }
interface VelocityPoint { date: string; mean_velocity: number; load_kg: number; exercise_name: string }
interface VolumePoint { week: string; total_reps: number; total_volume_kg: number }

export default function Dashboard() {
  const { user } = useAuth()
  const isCoach = user?.role !== 'athlete'

  const [stats, setStats] = useState<DashboardStats | null>(null)
  const [sessions, setSessions] = useState<SessionRow[]>([])
  const [athletes, setAthletes] = useState<User[]>([])
  const [exercises, setExercises] = useState<ExerciseDefinition[]>([])
  const [velocityData, setVelocityData] = useState<{date:string; avg:number}[]>([])
  const [volumeData, setVolumeData] = useState<VolumePoint[]>([])
  const [selectedAthlete, setSelectedAthlete] = useState('')
  const [selectedExercise, setSelectedExercise] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    loadBase()
  }, [])

  const loadBase = async () => {
    try {
      setIsLoading(true)
      const requests: Promise<any>[] = [
        client.get('/dashboard/stats'),
        client.get('/dashboard/recent-sessions?limit=8'),
        client.get('/exercises'),
      ]
      if (isCoach) requests.push(client.get('/users/athletes'))
      const results = await Promise.all(requests)
      setStats(results[0].data)
      setSessions(results[1].data)
      setExercises(results[2].data)
      const firstEx = results[2].data[0]?.id
      if (isCoach) {
        const athl = results[3].data as User[]
        setAthletes(athl)
        const firstAth = athl[0]?.id
        if (firstAth) setSelectedAthlete(String(firstAth))
        if (firstEx) setSelectedExercise(String(firstEx))
        await loadCharts(String(firstAth ?? ''), String(firstEx ?? ''))
      } else {
        if (firstEx) setSelectedExercise(String(firstEx))
        await loadCharts(String(user?.id ?? ''), String(firstEx ?? ''))
      }
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to load dashboard')
    } finally {
      setIsLoading(false)
    }
  }

  const loadCharts = async (athleteId: string, exerciseId: string) => {
    if (!athleteId || !exerciseId) return
    try {
      const [vRes, volRes] = await Promise.all([
        client.get('/analytics/velocity-trend', { params: { athlete_id: athleteId, exercise_id: exerciseId, days: 30 } }),
        client.get('/analytics/volume', { params: { athlete_id: athleteId, days: 90 } }),
      ])
      // Aggregate velocity by date (average per day)
      const byDate: Record<string, number[]> = {}
      ;(vRes.data as VelocityPoint[]).forEach(p => {
        byDate[p.date] = [...(byDate[p.date] ?? []), p.mean_velocity]
      })
      setVelocityData(
        Object.entries(byDate).map(([date, vals]) => ({
          date: date.slice(5), // MM-DD
          avg: parseFloat((vals.reduce((s, v) => s + v, 0) / vals.length).toFixed(3)),
        })).sort((a, b) => a.date.localeCompare(b.date))
      )
      setVolumeData(vRes.data.length ? volRes.data : [])
    } catch {}
  }

  const handleAthleteChange = async (id: string) => {
    setSelectedAthlete(id)
    await loadCharts(id, selectedExercise)
  }

  const handleExerciseChange = async (id: string) => {
    setSelectedExercise(id)
    await loadCharts(selectedAthlete || String(user?.id ?? ''), id)
  }

  if (isLoading) return <div className="flex items-center justify-center h-96 text-gray-400">Loading dashboard...</div>

  const statCards = isCoach
    ? [
        { label: 'Athletes', val: stats?.total_athletes ?? 0, icon: '👥', color: 'text-cyan-400' },
        { label: 'Active Plans', val: stats?.active_plans ?? 0, icon: '📋', color: 'text-violet-400' },
        { label: 'Sessions This Week', val: stats?.sessions_this_week ?? 0, icon: '🏋️', color: 'text-green-400' },
      ]
    : [
        { label: 'Assigned Plans', val: stats?.active_plans ?? 0, icon: '📋', color: 'text-violet-400' },
        { label: 'Sessions This Week', val: stats?.sessions_this_week ?? 0, icon: '🏋️', color: 'text-green-400' },
        { label: 'Total Sessions', val: sessions.length, icon: '📊', color: 'text-cyan-400' },
      ]

  return (
    <div className="space-y-6">
      <h1 className="text-3xl font-bold text-white">{isCoach ? 'Coach Dashboard' : 'My Dashboard'}</h1>

      {error && <div className="p-4 bg-red-900/30 border border-red-700 rounded text-red-200">{error}</div>}

      {/* Stats cards */}
      <div className="grid grid-cols-3 gap-4">
        {statCards.map(s => (
          <div key={s.label} className="bg-gray-800 border border-gray-700 rounded-lg p-6">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-gray-400 text-sm font-medium">{s.label}</p>
                <p className={`text-4xl font-bold mt-2 ${s.color}`}>{s.val}</p>
              </div>
              <div className="text-4xl opacity-60">{s.icon}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Chart controls */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
        <div className="flex items-center gap-4 mb-5">
          <h2 className="text-xl font-bold text-white flex-1">Velocity Trend (last 30 days)</h2>
          {isCoach && (
            <div>
              <label className="text-xs text-gray-400 mr-2">Athlete</label>
              <select value={selectedAthlete} onChange={e => handleAthleteChange(e.target.value)}
                className="px-3 py-1.5 bg-gray-700 border border-gray-600 rounded text-white text-sm">
                {athletes.map(a => <option key={a.id} value={a.id}>{a.username}</option>)}
              </select>
            </div>
          )}
          <div>
            <label className="text-xs text-gray-400 mr-2">Exercise</label>
            <select value={selectedExercise} onChange={e => handleExerciseChange(e.target.value)}
              className="px-3 py-1.5 bg-gray-700 border border-gray-600 rounded text-white text-sm">
              {exercises.map(e => <option key={e.id} value={e.id}>{e.name}</option>)}
            </select>
          </div>
        </div>

        {velocityData.length > 0 ? (
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={velocityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
              <XAxis dataKey="date" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 11 }} />
              <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} unit=" m/s" />
              <Tooltip {...tooltipStyle} formatter={(v: any) => [v + ' m/s', 'Avg Velocity']} />
              <Line type="monotone" dataKey="avg" stroke="#7c3aed" strokeWidth={2}
                dot={{ r: 3, fill: '#7c3aed' }} activeDot={{ r: 5 }} />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div className="text-center py-12 text-gray-500">
            No velocity data for this selection. Complete a workout session first.
          </div>
        )}
      </div>

      {/* Weekly volume */}
      {volumeData.length > 0 && (
        <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
          <h2 className="text-xl font-bold text-white mb-5">Weekly Volume (last 12 weeks)</h2>
          <div className="grid grid-cols-2 gap-6">
            <div>
              <p className="text-sm text-gray-400 mb-3">Total Reps per Week</p>
              <ResponsiveContainer width="100%" height={160}>
                <BarChart data={volumeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                  <XAxis dataKey="week" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                  <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                  <Tooltip {...tooltipStyle} />
                  <Bar dataKey="total_reps" name="Reps" fill="#7c3aed" radius={[3,3,0,0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div>
              <p className="text-sm text-gray-400 mb-3">Total Volume (kg) per Week</p>
              <ResponsiveContainer width="100%" height={160}>
                <BarChart data={volumeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                  <XAxis dataKey="week" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                  <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                  <Tooltip {...tooltipStyle} />
                  <Bar dataKey="total_volume_kg" name="Volume (kg)" fill="#06b6d4" radius={[3,3,0,0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      )}

      {/* Recent sessions */}
      <div className="bg-gray-800 border border-gray-700 rounded-lg p-6">
        <h2 className="text-xl font-bold text-white mb-4">Recent Sessions</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-700">
                {isCoach && <th className="text-left py-3 px-4 text-gray-400 font-medium">Athlete</th>}
                <th className="text-left py-3 px-4 text-gray-400 font-medium">When</th>
                <th className="text-left py-3 px-4 text-gray-400 font-medium">Duration</th>
                <th className="text-left py-3 px-4 text-gray-400 font-medium">Reps</th>
              </tr>
            </thead>
            <tbody>
              {sessions.length === 0 ? (
                <tr><td colSpan={isCoach ? 4 : 3} className="py-4 px-4 text-center text-gray-400">No sessions yet</td></tr>
              ) : (
                sessions.map(s => (
                  <tr key={s.id} className="border-b border-gray-700 hover:bg-gray-700/50">
                    {isCoach && <td className="py-3 px-4 text-gray-200">{s.athlete_name}</td>}
                    <td className="py-3 px-4 text-gray-400">
                      {formatDistanceToNow(new Date(s.started_at), { addSuffix: true })}
                    </td>
                    <td className="py-3 px-4 text-gray-400">
                      {s.duration_seconds ? `${Math.round(s.duration_seconds / 60)}m` : '—'}
                    </td>
                    <td className="py-3 px-4 text-violet-400 font-semibold">{s.reps_count}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
