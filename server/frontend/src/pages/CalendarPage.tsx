import { useEffect, useState } from 'react'
import { addDays, format, startOfWeek, isSameDay } from 'date-fns'
import client from '../api/client'
import { useAuth } from '../context/AuthContext'
import { CalendarEntry, User, TrainingPlan, SetOverride } from '../types'

const ATHLETE_COLORS = ['#7c3aed','#06b6d4','#ec4899','#f59e0b','#10b981','#6366f1','#f97316','#84cc16']

function getAthleteColor(athleteId: number) {
  return ATHLETE_COLORS[athleteId % ATHLETE_COLORS.length]
}

const STATUS_STYLE: Record<string, { bg: string; text: string; label: string }> = {
  scheduled: { bg: 'bg-blue-50', text: 'text-blue-700', label: 'Scheduled' },
  completed:  { bg: 'bg-green-50', text: 'text-green-700', label: 'Done' },
  skipped:    { bg: 'bg-red-50', text: 'text-red-700', label: 'Skipped' },
}

export default function CalendarPage() {
  const { user } = useAuth()
  const [entries, setEntries] = useState<CalendarEntry[]>([])
  const [athletes, setAthletes] = useState<User[]>([])
  const [plans, setPlans] = useState<TrainingPlan[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [filterAthlete, setFilterAthlete] = useState('all')
  const [weekStart, setWeekStart] = useState(() =>
    startOfWeek(new Date(), { weekStartsOn: 1 })
  )
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [formData, setFormData] = useState({
    athlete_id: '', plan_id: '', date: '', time_slot: '',
    title: '', notes: '', status: 'scheduled',
  })
  // Per-day overrides for exercises
  const [overrides, setOverrides] = useState<SetOverride[]>([])
  const [selectedPlanDetails, setSelectedPlanDetails] = useState<TrainingPlan | null>(null)

  useEffect(() => { loadData() }, [])

  const loadData = async () => {
    try {
      setIsLoading(true)
      const [eRes, aRes, pRes] = await Promise.all([
        client.get('/calendar'),
        client.get('/users/athletes'),
        client.get('/plans'),
      ])
      setEntries(eRes.data)
      setAthletes(aRes.data)
      setPlans(pRes.data)
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to load calendar')
    } finally {
      setIsLoading(false)
    }
  }

  const weekDays = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i))

  const filteredEntries = entries.filter(e =>
    filterAthlete === 'all' || e.athlete_id === parseInt(filterAthlete)
  )

  const entriesByDate = filteredEntries.reduce<Record<string, CalendarEntry[]>>((acc, e) => {
    acc[e.date] = [...(acc[e.date] ?? []), e]
    return acc
  }, {})

  const openAdd = (date: string) => {
    setEditingId(null)
    setFormData({ athlete_id: '', plan_id: '', date, time_slot: '', title: '', notes: '', status: 'scheduled' })
    setOverrides([])
    setSelectedPlanDetails(null)
    setShowModal(true)
  }

  const openEdit = (entry: CalendarEntry) => {
    setEditingId(entry.id)
    setFormData({
      athlete_id: String(entry.athlete_id),
      plan_id: entry.plan_id ? String(entry.plan_id) : '',
      date: entry.date,
      time_slot: entry.time_slot ?? '',
      title: entry.title,
      notes: entry.notes ?? '',
      status: entry.status,
    })
    // Load overrides from entry
    const parsed: SetOverride[] = entry.overrides_json ? JSON.parse(entry.overrides_json) : []
    setOverrides(parsed)
    // Load plan details if plan_id set
    if (entry.plan_id) {
      const plan = plans.find(p => p.id === entry.plan_id)
      if (plan) initOverridesFromPlan(plan, parsed)
      setSelectedPlanDetails(plan ?? null)
    } else {
      setSelectedPlanDetails(null)
    }
    setShowModal(true)
  }

  const initOverridesFromPlan = (plan: TrainingPlan, existingOverrides: SetOverride[]) => {
    // Build full set list from plan, merging with any existing overrides
    const allSets: SetOverride[] = []
    plan.exercises.forEach(ex => {
      ex.sets.forEach(set => {
        const existing = existingOverrides.find(o => o.exercise_id === ex.exercise_id && o.set_number === set.set_number)
        allSets.push({
          exercise_id: ex.exercise_id,
          exercise_name: ex.exercise?.name ?? `Exercise ${ex.exercise_id}`,
          set_number: set.set_number,
          reps: existing?.reps ?? set.reps,
          load_kg: existing?.load_kg ?? set.load_kg,
        })
      })
    })
    setOverrides(allSets)
  }

  const onPlanChange = (planId: string) => {
    setFormData(f => ({ ...f, plan_id: planId }))
    if (planId) {
      const plan = plans.find(p => p.id === parseInt(planId))
      setSelectedPlanDetails(plan ?? null)
      if (plan) initOverridesFromPlan(plan, [])
    } else {
      setSelectedPlanDetails(null)
      setOverrides([])
    }
  }

  const updateOverride = (idx: number, field: 'reps' | 'load_kg', value: number) => {
    setOverrides(prev => prev.map((o, i) => i === idx ? { ...o, [field]: value } : o))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const overridesStr = overrides.length > 0 ? JSON.stringify(overrides) : null
      const payload = {
        athlete_id: parseInt(formData.athlete_id),
        plan_id: formData.plan_id ? parseInt(formData.plan_id) : null,
        date: formData.date,
        time_slot: formData.time_slot || null,
        title: formData.title,
        notes: formData.notes || null,
        status: formData.status,
        overrides_json: overridesStr,
      }
      if (editingId) await client.put(`/calendar/${editingId}`, payload)
      else await client.post('/calendar', payload)
      setShowModal(false)
      loadData()
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to save')
    }
  }

  const handleDelete = async () => {
    if (!editingId || !window.confirm('Usunąć ten wpis?')) return
    try {
      await client.delete(`/calendar/${editingId}`)
      setShowModal(false)
      loadData()
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to delete')
    }
  }

  if (isLoading) return <div className="flex items-center justify-center h-96 text-gray-500">Loading...</div>

  return (
    <div className="flex gap-4 h-full">
      {/* Left sidebar */}
      <div className="w-48 flex-shrink-0">
        <div className="bg-white border border-gray-200 rounded-lg p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Athletes</h3>
          <button
            onClick={() => setFilterAthlete('all')}
            className={`w-full text-left px-3 py-2 rounded text-sm mb-1 transition-colors ${filterAthlete === 'all' ? 'bg-violet-600 text-white' : 'text-gray-500 hover:bg-gray-100'}`}
          >
            All Athletes
          </button>
          {user && (
            <button
              onClick={() => setFilterAthlete(String(user.id))}
              className={`w-full text-left px-3 py-2 rounded text-sm mb-1 transition-colors flex items-center gap-2 ${filterAthlete === String(user.id) ? 'bg-gray-100' : 'text-gray-500 hover:bg-gray-100'}`}
            >
              <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: getAthleteColor(user.id) }} />
              <span className="truncate">Ja ({user.username})</span>
            </button>
          )}
          {athletes.filter(a => a.id !== user?.id).map(a => (
            <button
              key={a.id}
              onClick={() => setFilterAthlete(String(a.id))}
              className={`w-full text-left px-3 py-2 rounded text-sm mb-1 transition-colors flex items-center gap-2 ${filterAthlete === String(a.id) ? 'bg-gray-100' : 'text-gray-500 hover:bg-gray-100'}`}
            >
              <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: getAthleteColor(a.id) }} />
              <span className="truncate">{a.username}</span>
            </button>
          ))}
        </div>
        <div className="bg-white border border-gray-200 rounded-lg p-4 mt-3">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Status</h3>
          {Object.entries(STATUS_STYLE).map(([key, val]) => (
            <div key={key} className="flex items-center gap-2 text-xs mb-1">
              <span className={`px-1.5 py-0.5 rounded text-xs ${val.bg} ${val.text}`}>{val.label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Main calendar */}
      <div className="flex-1 min-w-0">
        {error && <div className="mb-4 p-3 bg-red-50 border border-red-300 rounded text-red-700 text-sm">{error}</div>}

        <div className="flex items-center justify-between mb-4">
          <h1 className="text-2xl font-bold text-gray-900">Training Schedule</h1>
          <div className="flex items-center gap-3">
            <button onClick={() => setWeekStart(addDays(weekStart, -7))}
              className="px-3 py-1.5 bg-gray-100 hover:bg-gray-200 text-gray-900 rounded text-sm">← Prev</button>
            <span className="text-gray-700 font-medium">
              {format(weekStart, 'MMM d')} — {format(addDays(weekStart, 6), 'MMM d, yyyy')}
            </span>
            <button onClick={() => setWeekStart(addDays(weekStart, 7))}
              className="px-3 py-1.5 bg-gray-100 hover:bg-gray-200 text-gray-900 rounded text-sm">Next →</button>
            <button onClick={() => setWeekStart(startOfWeek(new Date(), { weekStartsOn: 1 }))}
              className="px-3 py-1.5 bg-violet-600 hover:bg-violet-700 text-white rounded text-sm">Today</button>
          </div>
        </div>

        <div className="grid grid-cols-7 gap-2">
          {weekDays.map(day => {
            const dateStr = format(day, 'yyyy-MM-dd')
            const dayEntries = entriesByDate[dateStr] ?? []
            const isToday = isSameDay(day, new Date())
            return (
              <div key={dateStr} className="flex flex-col min-h-48">
                <div className={`text-center py-2 mb-1 rounded-t-lg ${isToday ? 'bg-violet-600' : 'bg-white border border-gray-200'}`}>
                  <div className={`text-xs font-medium ${isToday ? 'text-violet-700' : 'text-gray-500'}`}>{format(day, 'EEE')}</div>
                  <div className={`text-lg font-bold ${isToday ? 'text-gray-900' : 'text-gray-800'}`}>{format(day, 'd')}</div>
                </div>
                <div className="flex-1 bg-white/80 border border-gray-200 rounded-b-lg p-1.5 space-y-1.5">
                  {dayEntries.map(entry => {
                    const athlete = athletes.find(a => a.id === entry.athlete_id)
                    const color = getAthleteColor(entry.athlete_id)
                    const status = STATUS_STYLE[entry.status] ?? STATUS_STYLE.scheduled
                    const hasOverrides = !!entry.overrides_json
                    return (
                      <div key={entry.id} onClick={() => openEdit(entry)}
                        className="cursor-pointer rounded p-1.5 border-l-2 bg-gray-100 hover:bg-gray-100 transition-colors"
                        style={{ borderLeftColor: color }}>
                        <div className="text-xs font-semibold text-gray-800 truncate">{entry.title}</div>
                        {athlete && <div className="text-xs truncate mt-0.5" style={{ color }}>{athlete.username}</div>}
                        {entry.time_slot && <div className="text-xs text-gray-500 mt-0.5">{entry.time_slot}</div>}
                        <div className="flex items-center gap-1 mt-1">
                          <span className={`text-xs px-1 py-0.5 rounded ${status.bg} ${status.text}`}>{status.label}</span>
                          {hasOverrides && <span className="text-xs px-1 py-0.5 rounded bg-orange-500/20 text-orange-300">edited</span>}
                        </div>
                      </div>
                    )
                  })}
                  <button onClick={() => openAdd(dateStr)}
                    className="w-full text-center text-xs text-gray-600 hover:text-violet-600 py-1 rounded hover:bg-gray-100 transition-colors">
                    + Add
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      </div>

      {/* Add/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-2xl border border-gray-200 max-h-[90vh] overflow-y-auto">
            <h2 className="text-xl font-bold text-gray-900 mb-4">
              {editingId ? 'Edytuj trening' : 'Dodaj trening'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-gray-500 mb-1 block">Zawodnik *</label>
                  <select value={formData.athlete_id} onChange={e => setFormData({...formData, athlete_id: e.target.value})}
                    className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm" required>
                    <option value="">Wybierz zawodnika</option>
                    {user && <option value={user.id}>Ja ({user.username})</option>}
                    {athletes.filter(a => a.id !== user?.id).map(a => <option key={a.id} value={a.id}>{a.username}</option>)}
                  </select>
                </div>
                <div>
                  <label className="text-xs text-gray-500 mb-1 block">Plan (opcjonalnie)</label>
                  <select value={formData.plan_id} onChange={e => onPlanChange(e.target.value)}
                    className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm">
                    <option value="">Brak</option>
                    {plans.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                  </select>
                </div>
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">Tytuł *</label>
                <input type="text" value={formData.title} onChange={e => setFormData({...formData, title: e.target.value})}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm" required />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-gray-500 mb-1 block">Data *</label>
                  <input type="date" value={formData.date} onChange={e => setFormData({...formData, date: e.target.value})}
                    className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm" required />
                </div>
                <div>
                  <label className="text-xs text-gray-500 mb-1 block">Godzina</label>
                  <input type="time" value={formData.time_slot} onChange={e => setFormData({...formData, time_slot: e.target.value})}
                    className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm" />
                </div>
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">Status</label>
                <select value={formData.status} onChange={e => setFormData({...formData, status: e.target.value})}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm">
                  <option value="scheduled">Zaplanowany</option>
                  <option value="completed">Wykonany</option>
                  <option value="skipped">Pominięty</option>
                </select>
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">Notatki</label>
                <textarea value={formData.notes} onChange={e => setFormData({...formData, notes: e.target.value})}
                  rows={2} className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm resize-none" />
              </div>

              {/* Per-day load/reps overrides */}
              {overrides.length > 0 && (
                <div>
                  <div className="flex items-center gap-2 mb-2">
                    <h3 className="text-sm font-semibold text-gray-700">Obciążenie / Powtórzenia na ten dzień</h3>
                    <span className="text-xs text-orange-400 bg-orange-400/10 px-2 py-0.5 rounded">override planu</span>
                  </div>
                  <div className="bg-gray-100 rounded-lg p-3 space-y-2">
                    {/* Group by exercise */}
                    {[...new Set(overrides.map(o => o.exercise_name ?? `Ex ${o.exercise_id}`))].map(exName => {
                      const exOverrides = overrides.filter(o => (o.exercise_name ?? `Ex ${o.exercise_id}`) === exName)
                      return (
                        <div key={exName}>
                          <p className="text-xs text-violet-700 font-medium mb-1">{exName}</p>
                          <div className="space-y-1">
                            {exOverrides.map((o, globalIdx) => {
                              const idx = overrides.findIndex(x => x.exercise_id === o.exercise_id && x.set_number === o.set_number)
                              return (
                                <div key={`${o.exercise_id}-${o.set_number}`} className="flex items-center gap-2">
                                  <span className="text-xs text-gray-500 w-12">Seria {o.set_number}</span>
                                  <div className="flex items-center gap-1">
                                    <label className="text-xs text-gray-500">Reps:</label>
                                    <input type="number" value={o.reps} min={1} max={30}
                                      onChange={e => updateOverride(idx, 'reps', parseInt(e.target.value) || 1)}
                                      className="w-16 px-2 py-1 bg-gray-100 border border-gray-300 rounded text-gray-900 text-xs text-center" />
                                  </div>
                                  <div className="flex items-center gap-1">
                                    <label className="text-xs text-gray-500">kg:</label>
                                    <input type="number" value={o.load_kg} min={0} step={2.5}
                                      onChange={e => updateOverride(idx, 'load_kg', parseFloat(e.target.value) || 0)}
                                      className="w-20 px-2 py-1 bg-gray-100 border border-gray-300 rounded text-gray-900 text-xs text-center" />
                                  </div>
                                </div>
                              )
                            })}
                          </div>
                        </div>
                      )
                    })}
                  </div>
                </div>
              )}

              <div className="flex gap-2 pt-2">
                {editingId && (
                  <button type="button" onClick={handleDelete}
                    className="px-3 py-2 bg-red-600 hover:bg-red-700 text-white text-sm rounded">
                    Usuń
                  </button>
                )}
                <button type="button" onClick={() => setShowModal(false)}
                  className="flex-1 py-2 bg-gray-100 hover:bg-gray-200 text-gray-900 text-sm rounded">
                  Anuluj
                </button>
                <button type="submit"
                  className="flex-1 py-2 bg-violet-600 hover:bg-violet-700 text-white text-sm rounded font-medium">
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
