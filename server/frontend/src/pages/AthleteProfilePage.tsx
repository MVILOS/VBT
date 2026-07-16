import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import client from '../api/client'
import { User, CalendarEntry, TrainingPlan, WorkoutSession } from '../types'
import SessionDetailModal from '../components/SessionDetailModal'

type Tab = 'calendar' | 'plans' | 'sessions'

interface NewEntryForm {
  date: string
  time_slot: string
  title: string
  notes: string
  plan_id: string
}

export default function AthleteProfilePage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const athleteId = parseInt(id!)

  const [athlete, setAthlete] = useState<User | null>(null)
  const [tab, setTab] = useState<Tab>('calendar')
  const [entries, setEntries] = useState<CalendarEntry[]>([])
  const [plans, setPlans] = useState<TrainingPlan[]>([])
  const [allPlans, setAllPlans] = useState<TrainingPlan[]>([])
  const [sessions, setSessions] = useState<WorkoutSession[]>([])
  const [isLoading, setIsLoading] = useState(true)

  // Calendar entry modal
  const [showEntryModal, setShowEntryModal] = useState(false)
  const [editingEntry, setEditingEntry] = useState<CalendarEntry | null>(null)
  const [entryForm, setEntryForm] = useState<NewEntryForm>({
    date: '', time_slot: '', title: '', notes: '', plan_id: '',
  })

  // Assign plan modal
  const [showAssignModal, setShowAssignModal] = useState(false)
  const [selectedPlanId, setSelectedPlanId] = useState('')

  // Session detail / edit modal
  const [detailSessionId, setDetailSessionId] = useState<number | null>(null)

  useEffect(() => {
    loadAll()
  }, [athleteId])

  const loadAll = async () => {
    setIsLoading(true)
    try {
      const [athleteRes, entriesRes, allPlansRes, sessionsRes] = await Promise.all([
        client.get(`/users/athletes/${athleteId}`),
        client.get(`/calendar?athlete_id=${athleteId}`),
        client.get('/plans'),
        client.get(`/sessions?athlete_id=${athleteId}`),
      ])
      setAthlete(athleteRes.data)
      setEntries(entriesRes.data)
      setAllPlans(allPlansRes.data)
      setPlans(allPlansRes.data.filter((p: TrainingPlan) => p.assigned_to === athleteId))
      setSessions(sessionsRes.data)
    } catch (e) {
      console.error(e)
    } finally {
      setIsLoading(false)
    }
  }

  // ─── Calendar helpers ────────────────────────────────────────────────
  const calendarEvents = entries.map(e => ({
    id: String(e.id),
    title: e.title || 'Training',
    date: e.date,
    backgroundColor:
      e.status === 'completed' ? '#16a34a' :
      e.status === 'skipped'   ? '#6b7280' : '#7c3aed',
    extendedProps: { entry: e },
  }))

  const openNewEntry = (date: string) => {
    setEditingEntry(null)
    setEntryForm({ date, time_slot: '', title: 'Training', notes: '', plan_id: '' })
    setShowEntryModal(true)
  }

  const openEditEntry = (entry: CalendarEntry) => {
    setEditingEntry(entry)
    setEntryForm({
      date: entry.date,
      time_slot: entry.time_slot || '',
      title: entry.title || '',
      notes: entry.notes || '',
      plan_id: entry.plan_id ? String(entry.plan_id) : '',
    })
    setShowEntryModal(true)
  }

  const saveEntry = async () => {
    const payload = {
      athlete_id: athleteId,
      date: entryForm.date,
      time_slot: entryForm.time_slot || null,
      title: entryForm.title,
      notes: entryForm.notes || null,
      plan_id: entryForm.plan_id ? parseInt(entryForm.plan_id) : null,
    }
    if (editingEntry) {
      await client.put(`/calendar/${editingEntry.id}`, payload)
    } else {
      await client.post('/calendar', payload)
    }
    setShowEntryModal(false)
    const res = await client.get(`/calendar?athlete_id=${athleteId}`)
    setEntries(res.data)
  }

  const deleteEntry = async (id: number) => {
    if (!confirm('Delete this training entry?')) return
    await client.delete(`/calendar/${id}`)
    setEntries(entries.filter(e => e.id !== id))
    setShowEntryModal(false)
  }

  const markStatus = async (id: number, status: string) => {
    await client.put(`/calendar/${id}`, { status })
    const res = await client.get(`/calendar?athlete_id=${athleteId}`)
    setEntries(res.data)
    setShowEntryModal(false)
  }

  // ─── Plan assign ─────────────────────────────────────────────────────
  const assignPlan = async () => {
    if (!selectedPlanId) return
    await client.post(`/plans/${selectedPlanId}/assign/${athleteId}`)
    setShowAssignModal(false)
    setSelectedPlanId('')
    loadAll()
  }

  if (isLoading) return (
    <div className="flex items-center justify-center h-96 text-gray-500">Loading...</div>
  )

  if (!athlete) return (
    <div className="text-red-600 p-8">Athlete not found.</div>
  )

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button onClick={() => navigate('/athletes')} className="text-gray-500 hover:text-gray-900 text-sm">
          ← Athletes
        </button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-gray-900">{athlete.username}</h1>
          <p className="text-gray-500 text-sm">{athlete.email}</p>
        </div>
        <span className={`px-3 py-1 rounded text-xs font-medium ${athlete.is_active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
          {athlete.is_active ? 'Active' : 'Inactive'}
        </span>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
        {[
          { label: 'Assigned Plans', value: plans.length },
          { label: 'Total Sessions', value: sessions.length },
          { label: 'Scheduled', value: entries.filter(e => e.status === 'scheduled').length },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-lg p-4 border border-gray-200">
            <p className="text-2xl font-bold text-gray-900">{s.value}</p>
            <p className="text-sm text-gray-500">{s.label}</p>
          </div>
        ))}
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-white p-1 rounded-lg w-fit border border-gray-200">
        {(['calendar', 'plans', 'sessions'] as Tab[]).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`px-4 py-2 rounded text-sm font-medium transition-colors capitalize ${
              tab === t ? 'bg-violet-600 text-white' : 'text-gray-500 hover:text-white'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {/* ── CALENDAR TAB ── */}
      {tab === 'calendar' && (
        <div className="bg-white rounded-lg border border-gray-200 p-4">
          <div className="fc-dark">
            <FullCalendar
              plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
              initialView="dayGridMonth"
              headerToolbar={{
                left: 'prev,next today',
                center: 'title',
                right: 'dayGridMonth,timeGridWeek',
              }}
              events={calendarEvents}
              dateClick={(info) => openNewEntry(info.dateStr)}
              eventClick={(info) => {
                const entry = info.event.extendedProps.entry as CalendarEntry
                openEditEntry(entry)
              }}
              height="auto"
            />
          </div>
          <p className="text-xs text-gray-500 mt-2">Click any day to add training · Click event to edit</p>
        </div>
      )}

      {/* ── PLANS TAB ── */}
      {tab === 'plans' && (
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <h2 className="text-lg font-semibold text-gray-900">Assigned Plans</h2>
            <button
              onClick={() => setShowAssignModal(true)}
              className="px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white text-sm rounded-lg transition-colors"
            >
              + Assign Plan
            </button>
          </div>
          {plans.length === 0 ? (
            <p className="text-gray-500 text-sm py-8 text-center">No plans assigned yet.</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {plans.map(plan => (
                <div key={plan.id} className="bg-white border border-gray-200 rounded-lg p-4">
                  <h3 className="font-semibold text-gray-900 mb-1">{plan.name}</h3>
                  {plan.description && <p className="text-sm text-gray-500 mb-2">{plan.description}</p>}
                  <p className="text-xs text-gray-500">{plan.exercises?.length || 0} exercises</p>
                  <button
                    onClick={() => {
                      // Add to calendar: open entry modal with this plan pre-selected
                      setTab('calendar')
                      const today = new Date().toISOString().split('T')[0]
                      setEditingEntry(null)
                      setEntryForm({ date: today, time_slot: '', title: plan.name, notes: '', plan_id: String(plan.id) })
                      setShowEntryModal(true)
                    }}
                    className="mt-3 w-full px-3 py-1.5 bg-violet-50 hover:bg-violet-100 text-violet-700 text-sm rounded transition-colors"
                  >
                    Schedule in Calendar
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* ── SESSIONS TAB ── */}
      {tab === 'sessions' && (
        <div className="space-y-3">
          <h2 className="text-lg font-semibold text-gray-900">Session History</h2>
          {sessions.length === 0 ? (
            <p className="text-gray-500 text-sm py-8 text-center">No sessions recorded yet.</p>
          ) : (
            sessions.map(s => (
              <button key={s.id} onClick={() => setDetailSessionId(s.id)}
                className="w-full text-left bg-white border border-gray-200 rounded-lg p-4 flex items-center justify-between hover:border-violet-400 transition-colors">
                <div>
                  <p className="text-gray-900 font-medium">{new Date(s.started_at).toLocaleDateString('pl-PL', { weekday: 'long', day: 'numeric', month: 'long' })}</p>
                  <p className="text-sm text-gray-500">
                    {s.reps?.length || 0} reps
                    {s.duration_seconds ? ` · ${Math.round(s.duration_seconds / 60)} min` : ''}
                  </p>
                </div>
                <span className="text-xs text-gray-500">{new Date(s.started_at).toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })} ›</span>
              </button>
            ))
          )}
        </div>
      )}

      {/* ── SESSION DETAIL / EDIT MODAL ── */}
      {detailSessionId != null && (
        <SessionDetailModal
          sessionId={detailSessionId}
          onClose={() => setDetailSessionId(null)}
          onChanged={loadAll}
        />
      )}

      {/* ── ENTRY MODAL ── */}
      {showEntryModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md border border-gray-200">
            <h3 className="text-lg font-bold text-gray-900 mb-4">
              {editingEntry ? 'Edit Training' : 'Add Training'}
            </h3>
            <div className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-gray-500 mb-1 block">Date</label>
                  <input type="date" value={entryForm.date}
                    onChange={e => setEntryForm({...entryForm, date: e.target.value})}
                    className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:outline-none focus:border-violet-600" />
                </div>
                <div>
                  <label className="text-xs text-gray-500 mb-1 block">Time (optional)</label>
                  <input type="time" value={entryForm.time_slot}
                    onChange={e => setEntryForm({...entryForm, time_slot: e.target.value})}
                    className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:outline-none focus:border-violet-600" />
                </div>
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">Title</label>
                <input type="text" value={entryForm.title}
                  onChange={e => setEntryForm({...entryForm, title: e.target.value})}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:outline-none focus:border-violet-600" />
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">Training Plan (optional)</label>
                <div className="flex gap-2">
                  <select value={entryForm.plan_id}
                    onChange={e => setEntryForm({...entryForm, plan_id: e.target.value})}
                    className="flex-1 px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:outline-none focus:border-violet-600">
                    <option value="">No plan</option>
                    {allPlans.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
                  </select>
                  {entryForm.plan_id && (
                    <button
                      type="button"
                      onClick={() => {
                        setShowEntryModal(false)
                        setTab('plans')
                        // scroll / highlight — for now just switch to plans tab
                      }}
                      className="px-3 py-2 bg-violet-50 hover:bg-violet-100 text-violet-700 text-xs rounded whitespace-nowrap transition-colors"
                    >
                      ✏️ Edit Plan
                    </button>
                  )}
                </div>
              </div>
              <div>
                <label className="text-xs text-gray-500 mb-1 block">Notes</label>
                <textarea value={entryForm.notes}
                  onChange={e => setEntryForm({...entryForm, notes: e.target.value})}
                  rows={2}
                  className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:outline-none focus:border-violet-600 resize-none" />
              </div>

              {editingEntry && (
                <div className="flex gap-2">
                  {['scheduled','completed','skipped'].map(s => (
                    <button key={s} onClick={() => markStatus(editingEntry.id, s)}
                      className={`flex-1 py-1 rounded text-xs capitalize transition-colors ${
                        editingEntry.status === s ? 'bg-violet-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                      }`}>
                      {s}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="flex gap-2 mt-5">
              {editingEntry && (
                <button onClick={() => deleteEntry(editingEntry.id)}
                  className="px-3 py-2 bg-red-50 hover:bg-red-100 text-red-700 text-sm rounded transition-colors">
                  Delete
                </button>
              )}
              <button onClick={() => setShowEntryModal(false)}
                className="flex-1 py-2 bg-gray-100 hover:bg-gray-200 text-gray-900 text-sm rounded transition-colors">
                Cancel
              </button>
              <button onClick={saveEntry}
                className="flex-1 py-2 bg-violet-600 hover:bg-violet-700 text-white text-sm rounded font-medium transition-colors">
                Save
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── ASSIGN PLAN MODAL ── */}
      {showAssignModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-sm border border-gray-200">
            <h3 className="text-lg font-bold text-gray-900 mb-4">Assign Plan to {athlete.username}</h3>
            <select value={selectedPlanId} onChange={e => setSelectedPlanId(e.target.value)}
              className="w-full px-3 py-2 bg-gray-100 border border-gray-300 rounded text-gray-900 mb-4 focus:outline-none focus:border-violet-600">
              <option value="">Select plan...</option>
              {allPlans.filter(p => p.assigned_to !== athleteId).map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
            <div className="flex gap-2">
              <button onClick={() => setShowAssignModal(false)}
                className="flex-1 py-2 bg-gray-100 hover:bg-gray-200 text-gray-900 text-sm rounded">Cancel</button>
              <button onClick={assignPlan} disabled={!selectedPlanId}
                className="flex-1 py-2 bg-violet-600 hover:bg-violet-700 disabled:bg-gray-200 text-white text-sm rounded font-medium">
                Assign
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
