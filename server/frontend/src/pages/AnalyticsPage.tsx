import { useEffect, useState } from 'react'
import {
  BarChart, Bar, LineChart, Line,
  XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, Cell,
} from 'recharts'
import client from '../api/client'
import { useAuth } from '../context/AuthContext'
import { User } from '../types'

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

interface Exercise { id: number; name: string; category: string | null; mvt: number | null }
interface SessionInfo { id: number; date: string; notes?: string }
interface RepData {
  rep_id: number; exercise_name: string; set_number: number; rep_number: number
  mean_velocity: number; peak_velocity: number; load_kg: number; power_watts?: number; label: string
}
interface VelocityPoint { date: string; mean_velocity: number; load_kg: number; exercise_name: string }
interface OrmPoint { date: string; estimated_1rm: number; load_kg: number }
interface FatigueSet { set_number: number; reps: number; mean_velocity: number; peak_velocity: number; load_kg: number }
interface FatigueExercise {
  exercise_id: number; exercise_name: string; sets: FatigueSet[]
  fatigue_index_pct: number; velocity_drop_ms: number; best_set: number; readiness_zone: string
}
interface WeeklyDay { date: string; sessions: number; total_reps: number; mean_velocity: number; total_volume_kg: number }
interface WeeklyLoad {
  week: string; training_days: number; week_mean_velocity: number
  week_total_reps: number; week_total_volume_kg: number; weekly_fatigue_pct: number; days: WeeklyDay[]
}
interface WeekComparison {
  week: string; sessions: number; total_reps: number; mean_velocity: number; max_velocity: number
  mean_peak_velocity: number; mean_load_kg: number; max_load_kg: number
  total_volume_kg: number; best_estimated_1rm: number | null
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers & constants
// ─────────────────────────────────────────────────────────────────────────────

const TABS = ['Sesja', 'Trendy', 'Zmęczenie', 'Tygodnie'] as const
type Tab = typeof TABS[number]

const COLORS = ['#7c3aed', '#06b6d4', '#ec4899', '#f59e0b', '#10b981', '#f97316']

function velColor(v: number) {
  if (v >= 1.0) return '#22c55e'
  if (v >= 0.75) return '#86efac'
  if (v >= 0.5) return '#fbbf24'
  if (v >= 0.35) return '#f97316'
  return '#ef4444'
}

function categoryOrder(cat: string | null) {
  return { olympic: 0, strength: 1, ballistic: 2, auxiliary: 3 }[cat ?? ''] ?? 4
}

function categoryLabel(cat: string | null) {
  return { olympic: 'Olimpijskie', strength: 'Siłowe', ballistic: 'Balistyczne', auxiliary: 'Pomocnicze' }[cat ?? ''] ?? 'Inne'
}

function readinessColor(zone: string) {
  return { optimal: '#22c55e', moderate: '#fbbf24', high: '#f97316', overreached: '#ef4444' }[zone] ?? '#9ca3af'
}

function readinessLabel(zone: string) {
  return { optimal: 'Optymalne', moderate: 'Umiarkowane', high: 'Wysokie', overreached: 'Przekroczone' }[zone] ?? zone
}

const tooltipStyle = {
  contentStyle: { backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: '6px' },
  labelStyle: { color: '#e5e7eb', fontWeight: 600 },
  itemStyle: { color: '#e5e7eb' },
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom tooltips
// ─────────────────────────────────────────────────────────────────────────────

function BarTooltip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null
  const d = payload[0].payload as RepData
  return (
    <div style={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: 6, padding: '8px 12px' }}>
      <p style={{ color: '#e5e7eb', fontWeight: 600, marginBottom: 4 }}>{label}</p>
      <p style={{ color: '#e5e7eb' }}>{payload[0].name}: <span style={{ color: payload[0].fill }}>{typeof payload[0].value === 'number' ? payload[0].value.toFixed(3) : payload[0].value}</span></p>
      {d?.load_kg != null && <p style={{ color: '#9ca3af', fontSize: 12 }}>Load: {d.load_kg} kg</p>}
      {d?.set_number != null && <p style={{ color: '#9ca3af', fontSize: 12 }}>Set {d.set_number} · Rep {d.rep_number}</p>}
    </div>
  )
}

function LineTooltip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null
  return (
    <div style={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: 6, padding: '8px 12px' }}>
      <p style={{ color: '#e5e7eb', fontWeight: 600, marginBottom: 4 }}>{label}</p>
      {payload.map((p: any, i: number) => (
        <p key={i} style={{ color: p.stroke ?? p.fill, fontSize: 13 }}>
          {p.name}: {typeof p.value === 'number' ? p.value.toFixed(3) : p.value}
        </p>
      ))}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────────────────────────

function StatCard({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="bg-gray-100 rounded-lg p-3 text-center">
      <div className="text-xl font-bold text-violet-600">{value}</div>
      <div className="text-xs text-gray-500 mt-1">{label}</div>
      {sub && <div className="text-xs text-gray-500 mt-0.5">{sub}</div>}
    </div>
  )
}

function ExerciseSelect({ exercises, value, onChange, placeholder = 'Wybierz ćwiczenie' }: {
  exercises: Exercise[]; value: number | ''; onChange: (id: number) => void; placeholder?: string
}) {
  const sorted = [...exercises].sort((a, b) => categoryOrder(a.category) - categoryOrder(b.category) || a.name.localeCompare(b.name))
  const groups = sorted.reduce((acc, ex) => {
    const g = categoryLabel(ex.category)
    if (!acc[g]) acc[g] = []
    acc[g].push(ex)
    return acc
  }, {} as Record<string, Exercise[]>)

  return (
    <select
      value={value}
      onChange={e => onChange(Number(e.target.value))}
      className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm"
    >
      <option value="">{placeholder}</option>
      {Object.entries(groups).map(([group, list]) => (
        <optgroup key={group} label={group}>
          {list.map(ex => (
            <option key={ex.id} value={ex.id}>
              {ex.name}{ex.mvt != null ? ` (MVT ${ex.mvt})` : ''}
            </option>
          ))}
        </optgroup>
      ))}
    </select>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Main component
// ─────────────────────────────────────────────────────────────────────────────

export default function AnalyticsPage() {
  const { user } = useAuth()
  const isAthlete = user?.role === 'athlete'
  const myId = String(user?.id ?? '')

  const [activeTab, setActiveTab] = useState<Tab>('Sesja')
  const [athletes, setAthletes] = useState<User[]>([])
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [selectedAthlete, setSelectedAthlete] = useState(myId)

  // ── Tab: Sesja ──────────────────────────────────────────────────────────────
  const [sessionList, setSessionList] = useState<SessionInfo[]>([])
  const [detailSession, setDetailSession] = useState<number | null>(null)
  const [sessionExercises, setSessionExercises] = useState<string[]>([])
  const [detailExercise, setDetailExercise] = useState('')
  const [repData, setRepData] = useState<RepData[]>([])
  const [metric, setMetric] = useState<'mean_velocity' | 'peak_velocity' | 'power_watts'>('mean_velocity')
  const [showPxlCxl, setShowPxlCxl] = useState(false)
  const [stressType, setStressType] = useState<'PXL' | 'CXL'>('PXL')

  // ── Tab: Compare (Overlay) ──────────────────────────────────────────────────
  const [overlayAllSessions, setOverlayAllSessions] = useState<SessionInfo[]>([])
  const [selectedOverlay, setSelectedOverlay] = useState<Set<number>>(new Set())
  const [overlayExercise, setOverlayExercise] = useState('')
  const [overlaySessionExercises, setOverlaySessionExercises] = useState<string[]>([])
  const [overlayData, setOverlayData] = useState<any[]>([])
  const [overlayMetric, setOverlayMetric] = useState<'mean_velocity' | 'peak_velocity' | 'power_watts'>('mean_velocity')

  // ── Tab: Export ─────────────────────────────────────────────────────────────
  const [exportFrom, setExportFrom] = useState('')
  const [exportTo, setExportTo] = useState('')
  const [exporting, setExporting] = useState(false)

  // ── Tab: Trendy ─────────────────────────────────────────────────────────────
  const [trendExercise, setTrendExercise] = useState<number | ''>('')
  const [trendDays, setTrendDays] = useState(90)
  const [velocityTrend, setVelocityTrend] = useState<VelocityPoint[]>([])
  const [ormProgress, setOrmProgress] = useState<OrmPoint[]>([])
  const [trendLoading, setTrendLoading] = useState(false)

  // ── Tab: Zmęczenie ──────────────────────────────────────────────────────────
  const [fatigueSessionList, setFatigueSessionList] = useState<SessionInfo[]>([])
  const [fatigueSession, setFatigueSession] = useState<number | null>(null)
  const [fatigueData, setFatigueData] = useState<FatigueExercise[]>([])
  const [fatigueLoading, setFatigueLoading] = useState(false)

  // ── Tab: Tygodnie ───────────────────────────────────────────────────────────
  const [weeksCount, setWeeksCount] = useState(8)
  const [weeklyLoad, setWeeklyLoad] = useState<WeeklyLoad[]>([])
  const [expandedWeek, setExpandedWeek] = useState<string | null>(null)
  const [compExercise, setCompExercise] = useState<number | ''>('')
  const [weekComparison, setWeekComparison] = useState<WeekComparison[]>([])
  const [weeksLoading, setWeeksLoading] = useState(false)

  // ─── Initial load ───────────────────────────────────────────────────────────
  useEffect(() => {
    const init = async () => {
      try {
        const [exRes] = await Promise.all([client.get('/exercises')])
        setExercises(exRes.data)

        if (!isAthlete) {
          const athRes = await client.get('/users/athletes')
          setAthletes(athRes.data)
        }
      } catch { /* ignore */ }
      setIsLoading(false)
    }
    init()
  }, [])

  // ─── Load sessions when athlete changes ─────────────────────────────────────
  useEffect(() => {
    if (!selectedAthlete) return
    loadSessions(selectedAthlete)
    loadFatigueSessions(selectedAthlete)
    setDetailSession(null); setRepData([]); setSessionExercises([])
    setFatigueSession(null); setFatigueData([])
  }, [selectedAthlete])

  const loadSessions = async (athleteId: string) => {
    try {
      const r = await client.get('/analytics/sessions-list', { params: { athlete_id: athleteId, days: 180 } })
      const sessions: SessionInfo[] = r.data
      setSessionList(sessions)
      setOverlayAllSessions(sessions)
      if (sessions[0] && !detailSession) {
        setDetailSession(sessions[0].id)
        await loadSessionDetail(sessions[0].id)
      }
    } catch { /* ignore */ }
  }

  const loadFatigueSessions = async (athleteId: string) => {
    try {
      const r = await client.get('/analytics/sessions-list', { params: { athlete_id: athleteId, days: 180 } })
      setFatigueSessionList(r.data)
    } catch { /* ignore */ }
  }

  const loadSessionDetail = async (sessionId: number) => {
    try {
      const r = await client.get('/analytics/session-detail', { params: { session_id: sessionId } })
      const allReps: RepData[] = r.data
      const exs = [...new Set(allReps.map(d => d.exercise_name))]
      setSessionExercises(exs)
      const ex = exs[0] ?? ''
      setDetailExercise(ex)
      setRepData(ex ? allReps.filter(d => d.exercise_name === ex) : [])
    } catch { /* ignore */ }
  }

  // ─── Tab: Trendy handlers ────────────────────────────────────────────────────
  const loadTrend = async (exerciseId: number, days: number) => {
    setTrendLoading(true)
    try {
      const athleteParam = isAthlete ? undefined : (selectedAthlete || myId)
      const [velRes, ormRes] = await Promise.all([
        client.get('/analytics/velocity-trend', { params: { athlete_id: athleteParam, exercise_id: exerciseId, days } }),
        client.get('/analytics/1rm-progress', { params: { athlete_id: athleteParam, exercise_id: exerciseId } }),
      ])
      setVelocityTrend(velRes.data)
      setOrmProgress(ormRes.data)
    } catch { /* ignore */ }
    setTrendLoading(false)
  }

  const handleTrendExercise = (id: number) => {
    setTrendExercise(id)
    loadTrend(id, trendDays)
  }

  const handleTrendDays = (days: number) => {
    setTrendDays(days)
    if (trendExercise) loadTrend(trendExercise as number, days)
  }

  // ─── Tab: Zmęczenie handlers ─────────────────────────────────────────────────
  const loadFatigue = async (sessionId: number) => {
    setFatigueLoading(true)
    try {
      const athleteParam = isAthlete ? undefined : (selectedAthlete || myId)
      const r = await client.get('/analytics/fatigue-index', {
        params: { session_id: sessionId, athlete_id: athleteParam }
      })
      setFatigueData(r.data)
    } catch { /* ignore */ }
    setFatigueLoading(false)
  }

  const handleFatigueSession = (sessionId: number) => {
    setFatigueSession(sessionId)
    loadFatigue(sessionId)
  }

  // ─── Tab: Tygodnie handlers ──────────────────────────────────────────────────
  const loadWeekly = async (weeks: number, exerciseId?: number) => {
    setWeeksLoading(true)
    try {
      const athleteParam = isAthlete ? undefined : (selectedAthlete || myId)
      const [loadRes] = await Promise.all([
        client.get('/analytics/weekly-load', { params: { athlete_id: athleteParam, weeks } }),
      ])
      setWeeklyLoad(loadRes.data)

      if (exerciseId) {
        const compRes = await client.get('/analytics/week-comparison', {
          params: { exercise_id: exerciseId, athlete_id: athleteParam, weeks }
        })
        setWeekComparison(compRes.data)
      }
    } catch { /* ignore */ }
    setWeeksLoading(false)
  }

  const handleCompExercise = async (id: number) => {
    setCompExercise(id)
    setWeeksLoading(true)
    try {
      const athleteParam = isAthlete ? undefined : (selectedAthlete || myId)
      const r = await client.get('/analytics/week-comparison', {
        params: { exercise_id: id, athlete_id: athleteParam, weeks: weeksCount }
      })
      setWeekComparison(r.data)
    } catch { /* ignore */ }
    setWeeksLoading(false)
  }

  // Load weekly on tab switch
  useEffect(() => {
    if (activeTab === 'Tygodnie' && selectedAthlete) {
      loadWeekly(weeksCount, compExercise as number || undefined)
    }
  }, [activeTab, selectedAthlete])

  // ─── Overlay logic ───────────────────────────────────────────────────────────
  useEffect(() => {
    if (selectedOverlay.size === 0) { setOverlaySessionExercises([]); setOverlayExercise(''); setOverlayData([]); return }
    const firstSid = Array.from(selectedOverlay)[0]
    client.get('/analytics/session-detail', { params: { session_id: firstSid } }).then(r => {
      const exs = [...new Set((r.data as RepData[]).map(d => d.exercise_name))]
      setOverlaySessionExercises(exs)
      if (!overlayExercise && exs[0]) setOverlayExercise(exs[0])
    })
  }, [selectedOverlay])

  useEffect(() => {
    if (selectedOverlay.size === 0 || !overlayExercise) { setOverlayData([]); return }
    Promise.all(
      Array.from(selectedOverlay).map(sid =>
        client.get('/analytics/session-detail', { params: { session_id: sid } })
          .then(r => ({ sid, reps: (r.data as RepData[]).filter(d => d.exercise_name === overlayExercise) }))
      )
    ).then(results => {
      const allLabels = [...new Set(results.flatMap(r => r.reps.map(d => d.label)))]
      const chartData = allLabels.map(label => {
        const point: any = { label }
        results.forEach(({ sid, reps }) => {
          const rep = reps.find(d => d.label === label)
          const session = overlayAllSessions.find(s => s.id === sid)
          const key = session?.date ?? String(sid)
          point[key] = overlayMetric === 'power_watts' ? rep?.power_watts ?? null
            : overlayMetric === 'peak_velocity' ? rep?.peak_velocity ?? null
            : rep?.mean_velocity ?? null
          point[key + '_load'] = rep?.load_kg ?? null
        })
        return point
      })
      setOverlayData(chartData)
    })
  }, [selectedOverlay, overlayExercise, overlayMetric])

  // ─── PXL/CXL ────────────────────────────────────────────────────────────────
  const pxlCxlData = repData.length > 0 ? (() => {
    const sets = [...new Set(repData.map(r => r.set_number))].sort()
    return sets.map(s => {
      const sr = repData.filter(r => r.set_number === s)
      const first = sr[0]?.mean_velocity ?? 0
      const last = sr[sr.length - 1]?.mean_velocity ?? 0
      const avg = sr.reduce((sum, r) => sum + r.mean_velocity, 0) / sr.length
      const pxl = first > 0 ? ((first - last) / first) * 100 : 0
      const cxl = first > 0 ? (avg / first) * 100 : 100
      return { label: `Seria ${s}`, pxl: Math.round(pxl * 10) / 10, cxl: Math.round(cxl * 10) / 10, reps: sr.length }
    })
  })() : []

  const handleExport = async () => {
    setExporting(true)
    try {
      const resp = await client.get('/analytics/export-csv', {
        params: { athlete_id: selectedAthlete || undefined, date_from: exportFrom || undefined, date_to: exportTo || undefined },
        responseType: 'blob',
      })
      const url = window.URL.createObjectURL(new Blob([resp.data]))
      const a = document.createElement('a'); a.href = url; a.download = 'vbt_export.csv'; a.click()
      window.URL.revokeObjectURL(url)
    } finally { setExporting(false) }
  }

  if (isLoading) return <div className="flex items-center justify-center h-96 text-gray-500">Ładowanie...</div>

  // ─── Athlete selector (shared top bar) ──────────────────────────────────────
  const metricLabel = metric === 'mean_velocity' ? 'Mean Velocity (m/s)' : metric === 'peak_velocity' ? 'Peak Velocity (m/s)' : 'Power (W)'

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-bold text-gray-900">{isAthlete ? 'Moje Statystyki' : 'Analityka'}</h1>
        {!isAthlete && (
          <div className="flex items-center gap-2">
            <label className="text-sm text-gray-500">Zawodnik:</label>
            <select
              value={selectedAthlete}
              onChange={e => setSelectedAthlete(e.target.value)}
              className="px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm"
            >
              <option value={myId}>Ja ({user?.username})</option>
              {athletes.map(a => <option key={a.id} value={a.id}>{a.username}</option>)}
            </select>
          </div>
        )}
      </div>

      {/* ── Tabs ── */}
      <div className="flex border-b border-gray-200">
        {TABS.map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-5 py-3 text-sm font-medium transition-colors border-b-2 -mb-px ${
              activeTab === tab
                ? 'border-violet-400 text-violet-600'
                : 'border-transparent text-gray-500 hover:text-gray-800'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* ══════════════════════════════════════════════════════════════════════ */}
      {/* TAB: SESJA                                                           */}
      {/* ══════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'Sesja' && (
        <div className="space-y-6">

          {/* Session Detail */}
          <div className="bg-white border border-gray-200 rounded-lg p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Szczegóły Sesji</h2>
            <div className="flex flex-wrap gap-4 mb-4">
              <div className="flex-1 min-w-36">
                <label className="block text-xs text-gray-500 mb-1">① Sesja</label>
                <select
                  value={detailSession ?? ''}
                  onChange={e => { setDetailSession(Number(e.target.value)); loadSessionDetail(Number(e.target.value)) }}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm"
                  disabled={sessionList.length === 0}
                >
                  {sessionList.length === 0
                    ? <option>Brak sesji</option>
                    : sessionList.map(s => <option key={s.id} value={s.id}>{s.date}{s.notes ? ` — ${s.notes}` : ''}</option>)
                  }
                </select>
              </div>
              <div className="flex-1 min-w-36">
                <label className="block text-xs text-gray-500 mb-1">② Ćwiczenie</label>
                <select
                  value={detailExercise}
                  onChange={async e => {
                    setDetailExercise(e.target.value)
                    if (!detailSession) return
                    const r = await client.get('/analytics/session-detail', { params: { session_id: detailSession } })
                    setRepData((r.data as RepData[]).filter(d => d.exercise_name === e.target.value))
                  }}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm"
                  disabled={sessionExercises.length === 0}
                >
                  {sessionExercises.length === 0
                    ? <option>Wybierz sesję</option>
                    : sessionExercises.map(ex => <option key={ex} value={ex}>{ex}</option>)
                  }
                </select>
              </div>
              <div className="flex-1 min-w-36">
                <label className="block text-xs text-gray-500 mb-1">③ Metryka</label>
                <select value={metric} onChange={e => setMetric(e.target.value as any)}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm">
                  <option value="mean_velocity">Mean Velocity (m/s)</option>
                  <option value="peak_velocity">Peak Velocity (m/s)</option>
                  <option value="power_watts">Power (W)</option>
                </select>
              </div>
            </div>

            {repData.length > 0 ? (
              <>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
                  <StatCard label="Powtórzenia" value={repData.length} />
                  <StatCard label="Serie" value={Math.max(...repData.map(r => r.set_number))} />
                  <StatCard label="Śr. Prędkość" value={(repData.reduce((s, r) => s + r.mean_velocity, 0) / repData.length).toFixed(2) + ' m/s'} />
                  <StatCard label="Obciążenie" value={repData[0]?.load_kg + ' kg'} />
                </div>
                <ResponsiveContainer width="100%" height={260}>
                  <BarChart data={repData} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                    <XAxis dataKey="label" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 11 }} />
                    <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} />
                    <Tooltip content={<BarTooltip />} />
                    <Bar dataKey={metric} name={metricLabel} radius={[4, 4, 0, 0]}>
                      {repData.map((entry, i) => (
                        <Cell key={i} fill={metric === 'power_watts' ? '#7c3aed' : velColor(entry.mean_velocity)} />
                      ))}
                    </Bar>
                  </BarChart>
                </ResponsiveContainer>
                <div className="flex gap-4 mt-2 flex-wrap">
                  {[...new Set(repData.map(r => r.set_number))].map(s => (
                    <span key={s} className="text-xs text-gray-500">
                      Set {s}: {repData.filter(r => r.set_number === s).length} reps @ {repData.find(r => r.set_number === s)?.load_kg}kg
                    </span>
                  ))}
                </div>

                {/* PXL/CXL */}
                <div className="mt-4 border-t border-gray-200 pt-4">
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-3">
                      <button
                        onClick={() => setShowPxlCxl(!showPxlCxl)}
                        className={`relative w-10 h-5 rounded-full transition-colors ${showPxlCxl ? 'bg-violet-600' : 'bg-gray-200'}`}
                      >
                        <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full transition-transform ${showPxlCxl ? 'translate-x-5' : 'translate-x-0.5'}`} />
                      </button>
                      <span className="text-sm text-gray-700">Monitoring obciążenia treningowego (PXL/CXL)</span>
                    </div>
                    {showPxlCxl && (
                      <div className="flex gap-2">
                        {(['PXL', 'CXL'] as const).map(t => (
                          <button key={t} onClick={() => setStressType(t)}
                            className={`px-3 py-1 text-xs font-semibold rounded ${stressType === t ? 'bg-violet-600 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>
                            {t}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                  {showPxlCxl && pxlCxlData.length > 0 && (
                    <div>
                      <p className="text-xs text-gray-500 mb-2">
                        {stressType === 'PXL'
                          ? 'PXL — utrata prędkości w serii (% względem 1. powtórzenia). Wyższy = większe zmęczenie.'
                          : 'CXL — średnia prędkość serii jako % 1. powtórzenia. Niższy = większe zmęczenie.'}
                      </p>
                      <ResponsiveContainer width="100%" height={180}>
                        <BarChart data={pxlCxlData}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                          <XAxis dataKey="label" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 11 }} />
                          <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} unit="%" domain={[0, 100]} />
                          <Tooltip {...tooltipStyle} />
                          <Bar dataKey={stressType === 'PXL' ? 'pxl' : 'cxl'} name={stressType} radius={[4, 4, 0, 0]}>
                            {pxlCxlData.map((entry, i) => {
                              const val = stressType === 'PXL' ? entry.pxl : entry.cxl
                              const color = stressType === 'PXL'
                                ? (val < 10 ? '#22c55e' : val < 20 ? '#fbbf24' : '#ef4444')
                                : (val > 90 ? '#22c55e' : val > 80 ? '#fbbf24' : '#ef4444')
                              return <Cell key={i} fill={color} />
                            })}
                          </Bar>
                        </BarChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </div>
              </>
            ) : (
              <div className="text-center py-10 text-gray-500">
                {sessionList.length === 0 ? 'Brak sesji — najpierw wykonaj trening.' : 'Brak danych dla wybranej sesji.'}
              </div>
            )}
          </div>

          {/* Overlay / Compare */}
          <div className="bg-white border border-gray-200 rounded-lg p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Porównanie sesji (Overlay)</h2>
            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm text-gray-500 mb-1">Ćwiczenie</label>
                <select value={overlayExercise} onChange={e => setOverlayExercise(e.target.value)}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm"
                  disabled={overlaySessionExercises.length === 0}>
                  {overlaySessionExercises.length === 0
                    ? <option>Wybierz sesje do porównania</option>
                    : overlaySessionExercises.map(ex => <option key={ex} value={ex}>{ex}</option>)
                  }
                </select>
              </div>
              <div>
                <label className="block text-sm text-gray-500 mb-1">Metryka</label>
                <select value={overlayMetric} onChange={e => setOverlayMetric(e.target.value as any)}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm">
                  <option value="mean_velocity">Mean Velocity (m/s)</option>
                  <option value="peak_velocity">Peak Velocity (m/s)</option>
                  <option value="power_watts">Power (W)</option>
                </select>
              </div>
            </div>
            <div className="mb-4">
              <label className="block text-sm text-gray-500 mb-2">Zaznacz sesje:</label>
              <div className="max-h-40 overflow-y-auto space-y-1 pr-1">
                {overlayAllSessions.map((s, i) => (
                  <label key={s.id} className={`flex items-center gap-3 px-3 py-2 rounded cursor-pointer transition-colors ${selectedOverlay.has(s.id) ? 'bg-gray-100' : 'hover:bg-gray-100'}`}>
                    <input type="checkbox" checked={selectedOverlay.has(s.id)}
                      onChange={() => { const ns = new Set(selectedOverlay); ns.has(s.id) ? ns.delete(s.id) : ns.add(s.id); setSelectedOverlay(ns) }}
                      className="w-4 h-4 accent-violet-600" />
                    <span className="w-3 h-3 rounded-full flex-shrink-0"
                      style={{ backgroundColor: selectedOverlay.has(s.id) ? COLORS[Array.from(selectedOverlay).indexOf(s.id) % COLORS.length] : '#4b5563' }} />
                    <span className="text-sm text-gray-700">{s.date}</span>
                    {s.notes && <span className="text-xs text-gray-500 truncate">— {s.notes}</span>}
                  </label>
                ))}
              </div>
            </div>
            {overlayData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={overlayData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                  <XAxis dataKey="label" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 11 }} />
                  <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} />
                  <Tooltip content={<LineTooltip />} />
                  <Legend wrapperStyle={{ color: '#e5e7eb' }} />
                  {Array.from(selectedOverlay).map((sid, i) => {
                    const s = overlayAllSessions.find(x => x.id === sid)
                    return <Line key={sid} type="monotone" dataKey={s?.date ?? String(sid)}
                      stroke={COLORS[i % COLORS.length]} strokeWidth={2} dot={{ r: 3 }} connectNulls />
                  })}
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="text-center py-8 text-gray-500">Zaznacz 2+ sesje żeby porównać</div>
            )}
          </div>

          {/* Export CSV */}
          <div className="bg-white border border-gray-200 rounded-lg p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-4">Eksport danych (CSV)</h2>
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 items-end">
              <div>
                <label className="block text-sm text-gray-500 mb-1">Od</label>
                <input type="date" value={exportFrom} onChange={e => setExportFrom(e.target.value)}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm" />
              </div>
              <div>
                <label className="block text-sm text-gray-500 mb-1">Do</label>
                <input type="date" value={exportTo} onChange={e => setExportTo(e.target.value)}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm" />
              </div>
              <button onClick={handleExport} disabled={exporting || !selectedAthlete}
                className="px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white rounded font-medium disabled:opacity-50">
                {exporting ? 'Eksportowanie...' : '⬇ Pobierz CSV'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════════ */}
      {/* TAB: TRENDY                                                          */}
      {/* ══════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'Trendy' && (
        <div className="space-y-6">
          {/* Controls */}
          <div className="bg-white border border-gray-200 rounded-lg p-6">
            <div className="flex flex-wrap gap-4 items-end mb-6">
              <div className="flex-1 min-w-48">
                <label className="block text-xs text-gray-500 mb-1">Ćwiczenie</label>
                <ExerciseSelect
                  exercises={exercises}
                  value={trendExercise}
                  onChange={handleTrendExercise}
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">Zakres</label>
                <div className="flex gap-1">
                  {[30, 60, 90, 180].map(d => (
                    <button key={d} onClick={() => handleTrendDays(d)}
                      className={`px-3 py-2 text-sm rounded ${trendDays === d ? 'bg-violet-600 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>
                      {d}d
                    </button>
                  ))}
                </div>
              </div>
            </div>

            {trendLoading && <div className="text-center py-8 text-gray-500">Ładowanie...</div>}

            {!trendLoading && !trendExercise && (
              <div className="text-center py-10 text-gray-500">Wybierz ćwiczenie żeby zobaczyć trendy</div>
            )}

            {!trendLoading && trendExercise && (
              <>
                {/* Velocity Trend */}
                <div className="mb-8">
                  <h3 className="text-lg font-semibold text-gray-900 mb-1">Trend Prędkości</h3>
                  <p className="text-xs text-gray-500 mb-4">Średnia prędkość powtórzeń w czasie. Rosnący trend = wzrost gotowości / adaptacja.</p>
                  {velocityTrend.length > 0 ? (
                    <>
                      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-4">
                        <StatCard label="Pomiarów" value={velocityTrend.length} />
                        <StatCard
                          label="Śr. prędkość"
                          value={(velocityTrend.reduce((s, r) => s + r.mean_velocity, 0) / velocityTrend.length).toFixed(3) + ' m/s'}
                        />
                        <StatCard
                          label="Max prędkość"
                          value={Math.max(...velocityTrend.map(r => r.mean_velocity)).toFixed(3) + ' m/s'}
                        />
                      </div>
                      <ResponsiveContainer width="100%" height={240}>
                        <LineChart data={velocityTrend} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                          <XAxis dataKey="date" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                          <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} domain={['auto', 'auto']} />
                          <Tooltip content={<LineTooltip />} />
                          <Line type="monotone" dataKey="mean_velocity" name="Mean Velocity (m/s)"
                            stroke="#7c3aed" strokeWidth={2} dot={{ r: 3, fill: '#7c3aed' }} />
                        </LineChart>
                      </ResponsiveContainer>
                    </>
                  ) : (
                    <div className="text-center py-8 text-gray-500">Brak danych dla wybranego ćwiczenia i zakresu dat</div>
                  )}
                </div>

                {/* 1RM Progress */}
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-1">Postęp 1RM</h3>
                  <p className="text-xs text-gray-500 mb-4">Estymowany 1RM obliczany na podstawie profilu siła-prędkość (F-V). Rosnący trend = wzrost siły.</p>
                  {ormProgress.length > 0 ? (
                    <>
                      <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-4">
                        <StatCard label="Pomiarów" value={ormProgress.length} />
                        <StatCard label="Ostatni 1RM" value={ormProgress[ormProgress.length - 1]?.estimated_1rm.toFixed(1) + ' kg'} />
                        <StatCard
                          label="Wzrost"
                          value={ormProgress.length >= 2
                            ? ((ormProgress[ormProgress.length - 1].estimated_1rm - ormProgress[0].estimated_1rm) >= 0 ? '+' : '') +
                              (ormProgress[ormProgress.length - 1].estimated_1rm - ormProgress[0].estimated_1rm).toFixed(1) + ' kg'
                            : '—'}
                        />
                      </div>
                      <ResponsiveContainer width="100%" height={240}>
                        <LineChart data={ormProgress} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                          <XAxis dataKey="date" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                          <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} domain={['auto', 'auto']} />
                          <Tooltip content={<LineTooltip />} />
                          <Line type="monotone" dataKey="estimated_1rm" name="Estymowany 1RM (kg)"
                            stroke="#f59e0b" strokeWidth={2} dot={{ r: 3, fill: '#f59e0b' }} />
                        </LineChart>
                      </ResponsiveContainer>
                    </>
                  ) : (
                    <div className="text-center py-8 text-gray-500">Brak danych 1RM dla wybranego ćwiczenia</div>
                  )}
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════════ */}
      {/* TAB: ZMĘCZENIE                                                       */}
      {/* ══════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'Zmęczenie' && (
        <div className="space-y-6">
          <div className="bg-white border border-gray-200 rounded-lg p-6">
            <h2 className="text-xl font-bold text-gray-900 mb-1">Indeks Zmęczenia</h2>
            <p className="text-xs text-gray-500 mb-4">
              FI% = (1 − V_ostatnia_seria / V_pierwsza_seria) × 100.
              Pokazuje nagromadzone zmęczenie wewnątrztreningowe per ćwiczenie.
            </p>

            {/* Session selector */}
            <div className="mb-6">
              <label className="block text-xs text-gray-500 mb-1">Sesja treningowa</label>
              <select
                value={fatigueSession ?? ''}
                onChange={e => handleFatigueSession(Number(e.target.value))}
                className="w-full max-w-sm px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm"
                disabled={fatigueSessionList.length === 0}
              >
                {fatigueSessionList.length === 0
                  ? <option>Brak sesji</option>
                  : <option value="">Wybierz sesję</option>
                }
                {fatigueSessionList.map(s => (
                  <option key={s.id} value={s.id}>{s.date}{s.notes ? ` — ${s.notes}` : ''}</option>
                ))}
              </select>
            </div>

            {fatigueLoading && <div className="text-center py-8 text-gray-500">Ładowanie...</div>}

            {!fatigueLoading && !fatigueSession && (
              <div className="text-center py-10 text-gray-500">Wybierz sesję żeby zobaczyć indeks zmęczenia</div>
            )}

            {!fatigueLoading && fatigueSession && fatigueData.length === 0 && (
              <div className="text-center py-10 text-gray-500">Brak danych dla tej sesji</div>
            )}

            {!fatigueLoading && fatigueData.length > 0 && (
              <div className="space-y-6">
                {fatigueData.map(ex => (
                  <div key={ex.exercise_id} className="border border-gray-200 rounded-lg p-4">
                    {/* Header */}
                    <div className="flex flex-wrap items-start justify-between gap-3 mb-4">
                      <div>
                        <h3 className="text-base font-semibold text-gray-900">{ex.exercise_name}</h3>
                        <p className="text-xs text-gray-500 mt-0.5">
                          Najlepsza seria: #{ex.best_set} · Spadek prędkości: {ex.velocity_drop_ms.toFixed(3)} m/s
                        </p>
                      </div>
                      <div className="flex items-center gap-3">
                        <div className="text-right">
                          <div className="text-2xl font-bold" style={{ color: readinessColor(ex.readiness_zone) }}>
                            {ex.fatigue_index_pct.toFixed(1)}%
                          </div>
                          <div className="text-xs text-gray-500">Fatigue Index</div>
                        </div>
                        <span className="px-3 py-1 rounded-full text-xs font-semibold"
                          style={{ backgroundColor: readinessColor(ex.readiness_zone) + '33', color: readinessColor(ex.readiness_zone) }}>
                          {readinessLabel(ex.readiness_zone)}
                        </span>
                      </div>
                    </div>

                    {/* Per-set bar chart */}
                    <ResponsiveContainer width="100%" height={180}>
                      <BarChart data={ex.sets} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                        <XAxis dataKey="set_number" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 11 }}
                          tickFormatter={v => `Seria ${v}`} />
                        <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} domain={['auto', 'auto']} />
                        <Tooltip
                          contentStyle={{ backgroundColor: '#1f2937', border: '1px solid #374151', borderRadius: 6 }}
                          labelFormatter={v => `Seria ${v}`}
                          formatter={(val: any, name: string) => [typeof val === 'number' ? val.toFixed(3) : val, name]}
                          labelStyle={{ color: '#e5e7eb' }} itemStyle={{ color: '#e5e7eb' }}
                        />
                        <Bar dataKey="mean_velocity" name="Mean Velocity (m/s)" radius={[4, 4, 0, 0]}>
                          {ex.sets.map((s, i) => (
                            <Cell
                              key={i}
                              fill={s.set_number === ex.best_set ? '#06b6d4' : velColor(s.mean_velocity)}
                            />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>

                    {/* Per-set table */}
                    <div className="mt-3 overflow-x-auto">
                      <table className="w-full text-xs text-gray-700">
                        <thead>
                          <tr className="border-b border-gray-200">
                            <th className="text-left py-1 pr-3 text-gray-500">Seria</th>
                            <th className="text-right py-1 pr-3 text-gray-500">Reps</th>
                            <th className="text-right py-1 pr-3 text-gray-500">Mean V (m/s)</th>
                            <th className="text-right py-1 pr-3 text-gray-500">Peak V (m/s)</th>
                            <th className="text-right py-1 text-gray-500">Obciążenie (kg)</th>
                          </tr>
                        </thead>
                        <tbody>
                          {ex.sets.map(s => (
                            <tr key={s.set_number} className={`border-b border-gray-200 ${s.set_number === ex.best_set ? 'text-cyan-400' : ''}`}>
                              <td className="py-1 pr-3">
                                {s.set_number}{s.set_number === ex.best_set && <span className="ml-1 text-cyan-500">★</span>}
                              </td>
                              <td className="text-right py-1 pr-3">{s.reps}</td>
                              <td className="text-right py-1 pr-3" style={{ color: velColor(s.mean_velocity) }}>
                                {s.mean_velocity.toFixed(3)}
                              </td>
                              <td className="text-right py-1 pr-3">{s.peak_velocity.toFixed(3)}</td>
                              <td className="text-right py-1">{s.load_kg}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ))}

                {/* FI% legenda */}
                <div className="bg-gray-100 rounded-lg p-4">
                  <p className="text-xs text-gray-500 font-semibold mb-2">Strefy zmęczenia (FI%):</p>
                  <div className="flex flex-wrap gap-4 text-xs">
                    {[
                      { zone: 'optimal', label: 'Optymalne (<5%)', desc: 'Bardzo niskie zmęczenie, pełna gotowość' },
                      { zone: 'moderate', label: 'Umiarkowane (5–10%)', desc: 'Normalne zmęczenie, kontynuuj plan' },
                      { zone: 'high', label: 'Wysokie (10–20%)', desc: 'Akumulacja — typowe dla fazy volumetrycznej' },
                      { zone: 'overreached', label: 'Przekroczone (>20%)', desc: 'Rozważ przerwanie lub zmniejszenie obciążenia' },
                    ].map(z => (
                      <div key={z.zone} className="flex items-start gap-2">
                        <span className="w-2.5 h-2.5 rounded-full mt-0.5 flex-shrink-0" style={{ backgroundColor: readinessColor(z.zone) }} />
                        <div>
                          <span className="font-medium" style={{ color: readinessColor(z.zone) }}>{z.label}</span>
                          <p className="text-gray-500">{z.desc}</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════════ */}
      {/* TAB: TYGODNIE                                                        */}
      {/* ══════════════════════════════════════════════════════════════════════ */}
      {activeTab === 'Tygodnie' && (
        <div className="space-y-6">

          {/* Controls */}
          <div className="flex flex-wrap gap-4 items-center">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Zakres tygodni</label>
              <div className="flex gap-1">
                {[4, 8, 12, 16].map(w => (
                  <button key={w} onClick={() => { setWeeksCount(w); loadWeekly(w, compExercise as number || undefined) }}
                    className={`px-3 py-2 text-sm rounded ${weeksCount === w ? 'bg-violet-600 text-white' : 'bg-gray-100 text-gray-500 hover:bg-gray-200'}`}>
                    {w}T
                  </button>
                ))}
              </div>
            </div>
            <button onClick={() => loadWeekly(weeksCount, compExercise as number || undefined)}
              className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-900 rounded text-sm mt-4">
              Odśwież
            </button>
          </div>

          {weeksLoading && <div className="text-center py-8 text-gray-500">Ładowanie...</div>}

          {!weeksLoading && (
            <>
              {/* Weekly Load overview */}
              <div className="bg-white border border-gray-200 rounded-lg p-6">
                <h2 className="text-xl font-bold text-gray-900 mb-1">Obciążenie Tygodniowe</h2>
                <p className="text-xs text-gray-500 mb-4">
                  Prędkość i objętość per tydzień. Zmęczenie tygodniowe = spadek prędkości od pierwszego do ostatniego dnia treningowego tygodnia.
                </p>

                {weeklyLoad.length === 0 ? (
                  <div className="text-center py-10 text-gray-500">Brak danych treningowych w tym zakresie</div>
                ) : (
                  <>
                    {/* Summary stats */}
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
                      <StatCard label="Tygodni z danymi" value={weeklyLoad.length} />
                      <StatCard label="Śr. dni/tydzień" value={(weeklyLoad.reduce((s, w) => s + w.training_days, 0) / weeklyLoad.length).toFixed(1)} />
                      <StatCard label="Śr. prędkość" value={(weeklyLoad.reduce((s, w) => s + w.week_mean_velocity, 0) / weeklyLoad.length).toFixed(3) + ' m/s'} />
                      <StatCard label="Śr. objętość/tydz." value={(weeklyLoad.reduce((s, w) => s + w.week_total_volume_kg, 0) / weeklyLoad.length).toFixed(0) + ' kg'} />
                    </div>

                    {/* Velocity trend chart */}
                    <h3 className="text-sm font-semibold text-gray-700 mb-2">Prędkość per tydzień</h3>
                    <ResponsiveContainer width="100%" height={200}>
                      <LineChart data={weeklyLoad} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                        <XAxis dataKey="week" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                        <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} domain={['auto', 'auto']} />
                        <Tooltip content={<LineTooltip />} />
                        <Line type="monotone" dataKey="week_mean_velocity" name="Śr. prędkość (m/s)"
                          stroke="#7c3aed" strokeWidth={2} dot={{ r: 4, fill: '#7c3aed' }} />
                      </LineChart>
                    </ResponsiveContainer>

                    {/* Volume chart */}
                    <h3 className="text-sm font-semibold text-gray-700 mt-4 mb-2">Objętość treningowa (kg) per tydzień</h3>
                    <ResponsiveContainer width="100%" height={180}>
                      <BarChart data={weeklyLoad} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                        <XAxis dataKey="week" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                        <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} />
                        <Tooltip {...tooltipStyle} />
                        <Bar dataKey="week_total_volume_kg" name="Objętość (kg)" radius={[4, 4, 0, 0]}>
                          {weeklyLoad.map((w, i) => (
                            <Cell key={i} fill={w.weekly_fatigue_pct > 20 ? '#ef4444' : w.weekly_fatigue_pct > 10 ? '#f97316' : w.weekly_fatigue_pct > 5 ? '#fbbf24' : '#22c55e'} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>

                    {/* Weekly summary table */}
                    <div className="mt-4 overflow-x-auto">
                      <table className="w-full text-xs text-gray-700">
                        <thead>
                          <tr className="border-b border-gray-200">
                            <th className="text-left py-2 pr-3 text-gray-500">Tydzień</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Dni</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Reps</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Śr. V (m/s)</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Objętość (kg)</th>
                            <th className="text-right py-2 text-gray-500">Zmęczenie tyg.</th>
                          </tr>
                        </thead>
                        <tbody>
                          {weeklyLoad.map(w => (
                            <>
                              <tr key={w.week}
                                className="border-b border-gray-200 hover:bg-gray-100 cursor-pointer"
                                onClick={() => setExpandedWeek(expandedWeek === w.week ? null : w.week)}>
                                <td className="py-2 pr-3 font-medium">{w.week} <span className="text-gray-500">{expandedWeek === w.week ? '▲' : '▼'}</span></td>
                                <td className="text-right py-2 pr-3">{w.training_days}</td>
                                <td className="text-right py-2 pr-3">{w.week_total_reps}</td>
                                <td className="text-right py-2 pr-3" style={{ color: velColor(w.week_mean_velocity) }}>
                                  {w.week_mean_velocity.toFixed(3)}
                                </td>
                                <td className="text-right py-2 pr-3">{w.week_total_volume_kg.toFixed(0)}</td>
                                <td className="text-right py-2">
                                  <span style={{ color: w.weekly_fatigue_pct > 20 ? '#ef4444' : w.weekly_fatigue_pct > 10 ? '#f97316' : w.weekly_fatigue_pct > 5 ? '#fbbf24' : '#22c55e' }}>
                                    {w.weekly_fatigue_pct > 0 ? w.weekly_fatigue_pct.toFixed(1) + '%' : '—'}
                                  </span>
                                </td>
                              </tr>
                              {expandedWeek === w.week && w.days.map(d => (
                                <tr key={d.date} className="bg-gray-100 border-b border-gray-200">
                                  <td className="py-1.5 pr-3 pl-4 text-gray-500">{d.date}</td>
                                  <td className="text-right py-1.5 pr-3 text-gray-500">{d.sessions} sesj.</td>
                                  <td className="text-right py-1.5 pr-3 text-gray-500">{d.total_reps}</td>
                                  <td className="text-right py-1.5 pr-3" style={{ color: velColor(d.mean_velocity) }}>
                                    {d.mean_velocity.toFixed(3)}
                                  </td>
                                  <td className="text-right py-1.5 pr-3 text-gray-500">{d.total_volume_kg.toFixed(0)}</td>
                                  <td></td>
                                </tr>
                              ))}
                            </>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                )}
              </div>

              {/* Week Comparison per exercise */}
              <div className="bg-white border border-gray-200 rounded-lg p-6">
                <h2 className="text-xl font-bold text-gray-900 mb-1">Porównanie Tygodni — Ćwiczenie</h2>
                <p className="text-xs text-gray-500 mb-4">
                  Jak ewoluuje prędkość i obciążenie w kolejnych tygodniach cyklu dla wybranego ćwiczenia.
                  Wysoka prędkość + duże obciążenie = adaptacja. Niska prędkość + rosnąca objętość = akumulacja zmęczenia.
                </p>
                <div className="mb-4 max-w-sm">
                  <label className="block text-xs text-gray-500 mb-1">Ćwiczenie</label>
                  <ExerciseSelect exercises={exercises} value={compExercise} onChange={handleCompExercise} />
                </div>

                {!compExercise && (
                  <div className="text-center py-8 text-gray-500">Wybierz ćwiczenie żeby porównać tygodnie</div>
                )}

                {compExercise && weekComparison.length === 0 && (
                  <div className="text-center py-8 text-gray-500">Brak danych dla tego ćwiczenia w wybranym zakresie</div>
                )}

                {compExercise && weekComparison.length > 0 && (
                  <>
                    {/* Dual line: mean + max velocity */}
                    <h3 className="text-sm font-semibold text-gray-700 mb-2">Prędkość tygodniowa</h3>
                    <ResponsiveContainer width="100%" height={220}>
                      <LineChart data={weekComparison} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                        <XAxis dataKey="week" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                        <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} domain={['auto', 'auto']} />
                        <Tooltip content={<LineTooltip />} />
                        <Legend wrapperStyle={{ color: '#e5e7eb' }} />
                        <Line type="monotone" dataKey="mean_velocity" name="Śr. prędkość (m/s)"
                          stroke="#7c3aed" strokeWidth={2} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="max_velocity" name="Max prędkość (m/s)"
                          stroke="#06b6d4" strokeWidth={2} dot={{ r: 3 }} strokeDasharray="4 2" />
                      </LineChart>
                    </ResponsiveContainer>

                    {/* Load chart */}
                    <h3 className="text-sm font-semibold text-gray-700 mt-4 mb-2">Obciążenie (kg)</h3>
                    <ResponsiveContainer width="100%" height={180}>
                      <BarChart data={weekComparison} margin={{ top: 5, right: 20, left: 0, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#374151" />
                        <XAxis dataKey="week" stroke="#9ca3af" tick={{ fill: '#9ca3af', fontSize: 10 }} />
                        <YAxis stroke="#9ca3af" tick={{ fill: '#9ca3af' }} />
                        <Tooltip {...tooltipStyle} />
                        <Legend wrapperStyle={{ color: '#e5e7eb' }} />
                        <Bar dataKey="mean_load_kg" name="Śr. obciążenie (kg)" fill="#7c3aed" radius={[4, 4, 0, 0]} />
                        <Bar dataKey="max_load_kg" name="Max obciążenie (kg)" fill="#06b6d4" radius={[4, 4, 0, 0]} />
                      </BarChart>
                    </ResponsiveContainer>

                    {/* Week comparison table */}
                    <div className="mt-4 overflow-x-auto">
                      <table className="w-full text-xs text-gray-700">
                        <thead>
                          <tr className="border-b border-gray-200">
                            <th className="text-left py-2 pr-3 text-gray-500">Tydzień</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Sesje</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Reps</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Śr. V</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Max V</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Śr. kg</th>
                            <th className="text-right py-2 pr-3 text-gray-500">Max kg</th>
                            <th className="text-right py-2 text-gray-500">1RM est.</th>
                          </tr>
                        </thead>
                        <tbody>
                          {weekComparison.map(w => (
                            <tr key={w.week} className="border-b border-gray-200 hover:bg-gray-100">
                              <td className="py-2 pr-3 font-medium">{w.week}</td>
                              <td className="text-right py-2 pr-3">{w.sessions}</td>
                              <td className="text-right py-2 pr-3">{w.total_reps}</td>
                              <td className="text-right py-2 pr-3" style={{ color: velColor(w.mean_velocity) }}>
                                {w.mean_velocity.toFixed(3)}
                              </td>
                              <td className="text-right py-2 pr-3" style={{ color: velColor(w.max_velocity) }}>
                                {w.max_velocity.toFixed(3)}
                              </td>
                              <td className="text-right py-2 pr-3">{w.mean_load_kg.toFixed(1)}</td>
                              <td className="text-right py-2 pr-3">{w.max_load_kg.toFixed(1)}</td>
                              <td className="text-right py-2">{w.best_estimated_1rm != null ? w.best_estimated_1rm.toFixed(1) + ' kg' : '—'}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </>
                )}
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}
