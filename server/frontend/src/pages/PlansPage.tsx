import { useEffect, useState } from 'react'
import client from '../api/client'
import { TrainingPlan, ExerciseDefinition, User, PlanExercise } from '../types'
import { useAuth } from '../context/AuthContext'

export default function PlansPage() {
  const { user } = useAuth()
  const isCoach = user?.role !== 'athlete'
  const [plans, setPlans] = useState<TrainingPlan[]>([])
  const [athletes, setAthletes] = useState<User[]>([])
  const [exercises, setExercises] = useState<ExerciseDefinition[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [showModal, setShowModal] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  // Inline new exercise creation
  const [newExName, setNewExName] = useState('')
  const [creatingExFor, setCreatingExFor] = useState<number | null>(null)
  // Schedule plan to calendar
  const [schedulingPlan, setSchedulingPlan] = useState<TrainingPlan | null>(null)
  const [schedForm, setSchedForm] = useState({ athlete_id: '', date: '', time_slot: '', notes: '' })
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    assigned_to: '',
    is_template: false,
    exercises: [] as PlanExercise[],
  })

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    try {
      setIsLoading(true)
      const requests: Promise<any>[] = [client.get('/plans'), client.get('/exercises')]
      if (isCoach) requests.splice(1, 0, client.get('/users/athletes'))
      const results = await Promise.all(requests)
      setPlans(results[0].data)
      if (isCoach) {
        setAthletes(results[1].data)
        setExercises(results[2].data)
      } else {
        setExercises(results[1].data)
      }
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to load data')
    } finally {
      setIsLoading(false)
    }
  }

  const handleOpenModal = (plan?: TrainingPlan) => {
    if (plan) {
      setEditingId(plan.id)
      setFormData({
        name: plan.name,
        description: plan.description || '',
        assigned_to: plan.assigned_to?.toString() || '',
        is_template: plan.is_template,
        exercises: plan.exercises || [],
      })
    } else {
      setEditingId(null)
      setFormData({
        name: '',
        description: '',
        assigned_to: '',
        is_template: false,
        exercises: [],
      })
    }
    setShowModal(true)
  }

  const handleAddExercise = () => {
    const newExercise: PlanExercise = {
      exercise_id: 0,
      order_index: formData.exercises.length,
      sets: [{ set_number: 1, reps: 5, load_kg: 100, rest_seconds: 180 }],
    }
    setFormData({ ...formData, exercises: [...formData.exercises, newExercise] })
  }

  const handleCreateNewExercise = async (exIdx: number) => {
    if (!newExName.trim()) return
    try {
      const res = await client.post('/exercises', { name: newExName.trim(), category: 'compound' })
      const created: ExerciseDefinition = res.data
      setExercises(prev => [...prev, created])
      handleExerciseChange(exIdx, 'exercise_id', created.id)
      setNewExName('')
      setCreatingExFor(null)
    } catch (e) {
      console.error(e)
    }
  }

  const handleRemoveExercise = (index: number) => {
    setFormData({
      ...formData,
      exercises: formData.exercises.filter((_, i) => i !== index),
    })
  }

  const handleAddSet = (exerciseIndex: number) => {
    const newExercises = [...formData.exercises]
    const setNumber = (newExercises[exerciseIndex].sets?.length || 0) + 1
    newExercises[exerciseIndex].sets = [
      ...(newExercises[exerciseIndex].sets || []),
      {
        set_number: setNumber,
        reps: 5,
        load_kg: 100,
        rest_seconds: 180,
      },
    ]
    setFormData({ ...formData, exercises: newExercises })
  }

  const handleRemoveSet = (exerciseIndex: number, setIndex: number) => {
    const newExercises = [...formData.exercises]
    newExercises[exerciseIndex].sets = newExercises[exerciseIndex].sets?.filter(
      (_, i) => i !== setIndex
    )
    setFormData({ ...formData, exercises: newExercises })
  }

  const handleExerciseChange = (
    exerciseIndex: number,
    field: string,
    value: any
  ) => {
    const newExercises = [...formData.exercises]
    newExercises[exerciseIndex] = {
      ...newExercises[exerciseIndex],
      [field]: value,
    }
    setFormData({ ...formData, exercises: newExercises })
  }

  const handleSetChange = (
    exerciseIndex: number,
    setIndex: number,
    field: string,
    value: any
  ) => {
    const newExercises = [...formData.exercises]
    if (newExercises[exerciseIndex].sets) {
      newExercises[exerciseIndex].sets[setIndex] = {
        ...newExercises[exerciseIndex].sets[setIndex],
        [field]: field === 'rest_seconds' || field === 'reps' ? parseInt(value) : parseFloat(value),
      }
    }
    setFormData({ ...formData, exercises: newExercises })
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    try {
      const payload = {
        name: formData.name,
        description: formData.description,
        assigned_to: formData.assigned_to ? parseInt(formData.assigned_to) : null,
        is_template: formData.is_template,
        exercises: formData.exercises,
      }

      if (editingId) {
        await client.put(`/plans/${editingId}`, payload)
      } else {
        await client.post('/plans', payload)
      }
      setShowModal(false)
      loadData()
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to save plan')
    }
  }

  const handleSchedulePlan = async () => {
    if (!schedulingPlan || !schedForm.athlete_id || !schedForm.date) return
    try {
      await client.post('/calendar', {
        athlete_id: parseInt(schedForm.athlete_id),
        plan_id: schedulingPlan.id,
        date: schedForm.date,
        time_slot: schedForm.time_slot || null,
        title: schedulingPlan.name,
        notes: schedForm.notes || null,
      })
      setSchedulingPlan(null)
      setSchedForm({ athlete_id: '', date: '', time_slot: '', notes: '' })
    } catch (err: any) {
      setError(err.response?.data?.detail || 'Failed to schedule plan')
    }
  }

  const handleDelete = async (id: number) => {
    if (window.confirm('Are you sure you want to delete this plan?')) {
      try {
        await client.delete(`/plans/${id}`)
        loadData()
      } catch (err: any) {
        setError(err.response?.data?.detail || 'Failed to delete plan')
      }
    }
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-96">
        <div className="text-gray-400">Loading plans...</div>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-white">Training Plans</h1>
        <button
          onClick={() => handleOpenModal()}
          className="px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white rounded-lg font-medium transition-colors"
        >
          New Plan
        </button>
      </div>

      {error && (
        <div className="p-4 bg-red-900/30 border border-red-700 rounded text-red-200">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {plans.length === 0 ? (
          <div className="col-span-full text-center py-12 text-gray-400">
            No plans yet. Create one to get started.
          </div>
        ) : (
          plans.map((plan) => (
            <div
              key={plan.id}
              className="bg-gray-800 border border-gray-700 rounded-lg p-6 hover:border-gray-600 transition-colors"
            >
              <div className="flex items-start justify-between mb-4">
                <div>
                  <h3 className="text-lg font-semibold text-white">{plan.name}</h3>
                  {plan.description && (
                    <p className="text-sm text-gray-400 mt-1">{plan.description}</p>
                  )}
                </div>
                <span
                  className={`px-3 py-1 rounded text-xs font-medium whitespace-nowrap ${
                    plan.is_template
                      ? 'bg-blue-900/30 text-blue-200'
                      : 'bg-violet-900/30 text-violet-200'
                  }`}
                >
                  {plan.is_template ? 'Template' : 'Plan'}
                </span>
              </div>

              <div className="space-y-2 mb-4 text-sm text-gray-400">
                <p>Exercises: {plan.exercises?.length || 0}</p>
                {isCoach && plan.assigned_to && (
                  <p>Assigned to: {athletes.find(a => a.id === plan.assigned_to)?.username || 'Unknown'}</p>
                )}
              </div>

              <div className="flex gap-2 mt-4">
                {isCoach && (
                  <button
                    onClick={() => {
                      setSchedulingPlan(plan)
                      setSchedForm({
                        athlete_id: plan.assigned_to ? String(plan.assigned_to) : '',
                        date: new Date().toISOString().split('T')[0],
                        time_slot: '',
                        notes: '',
                      })
                    }}
                    className="flex-1 px-3 py-2 bg-violet-700 hover:bg-violet-600 text-white text-sm rounded transition-colors"
                  >
                    📅 Schedule
                  </button>
                )}
                <button
                  onClick={() => handleOpenModal(plan)}
                  className="px-3 py-2 bg-gray-700 hover:bg-gray-600 text-white text-sm rounded transition-colors"
                >
                  Edit
                </button>
                <button
                  onClick={() => handleDelete(plan.id)}
                  className="px-3 py-2 bg-red-900/30 hover:bg-red-900/50 text-red-200 text-sm rounded transition-colors"
                >
                  ✕
                </button>
              </div>
            </div>
          ))
        )}
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50 overflow-y-auto">
          <div className="bg-gray-800 rounded-lg p-6 w-full max-w-2xl border border-gray-700 my-8">
            <h2 className="text-xl font-bold text-white mb-6">
              {editingId ? 'Edit Plan' : 'Create New Plan'}
            </h2>

            <form onSubmit={handleSubmit} className="space-y-6">
              <div className={`grid gap-4 ${isCoach ? 'grid-cols-2' : 'grid-cols-1'}`}>
                <div>
                  <label className="block text-sm font-medium text-gray-300 mb-2">
                    Plan Name
                  </label>
                  <input
                    type="text"
                    value={formData.name}
                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                    className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white focus:outline-none focus:border-violet-600"
                    required
                  />
                </div>

                {isCoach && (
                  <div>
                    <label className="block text-sm font-medium text-gray-300 mb-2">
                      Przypisz do zawodnika
                    </label>
                    <select
                      value={formData.assigned_to}
                      onChange={(e) => setFormData({ ...formData, assigned_to: e.target.value })}
                      className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white focus:outline-none focus:border-violet-600"
                    >
                      <option value="">Brak</option>
                      <option value={user?.id ?? ''}>Ja ({user?.username})</option>
                      {athletes.map((athlete) => (
                        <option key={athlete.id} value={athlete.id}>
                          {athlete.username}
                        </option>
                      ))}
                    </select>
                  </div>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-300 mb-2">
                  Description
                </label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-4 py-2 bg-gray-700 border border-gray-600 rounded text-white focus:outline-none focus:border-violet-600 h-20 resize-none"
                />
              </div>

              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="is_template"
                  checked={formData.is_template}
                  onChange={(e) => setFormData({ ...formData, is_template: e.target.checked })}
                  className="w-4 h-4 rounded"
                />
                <label htmlFor="is_template" className="text-sm text-gray-300">
                  Save as template
                </label>
              </div>

              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-lg font-semibold text-white">Exercises</h3>
                  <button
                    type="button"
                    onClick={handleAddExercise}
                    className="px-3 py-1 bg-green-600 hover:bg-green-700 text-white text-sm rounded transition-colors"
                  >
                    Add Exercise
                  </button>
                </div>

                {formData.exercises.map((exercise, exIdx) => (
                  <div
                    key={exIdx}
                    className="bg-gray-700 rounded p-4 space-y-3"
                  >
                    <div className="flex items-end gap-2">
                      <div className="flex-1">
                        <label className="block text-xs font-medium text-gray-300 mb-1">Exercise</label>
                        {creatingExFor === exIdx ? (
                          <div className="flex gap-1">
                            <input
                              autoFocus
                              type="text"
                              value={newExName}
                              onChange={e => setNewExName(e.target.value)}
                              onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), handleCreateNewExercise(exIdx))}
                              placeholder="New exercise name..."
                              className="flex-1 px-3 py-2 bg-gray-600 border border-violet-500 rounded text-white text-sm focus:outline-none"
                            />
                            <button type="button" onClick={() => handleCreateNewExercise(exIdx)}
                              className="px-2 py-1 bg-violet-600 hover:bg-violet-700 text-white text-xs rounded">✓</button>
                            <button type="button" onClick={() => { setCreatingExFor(null); setNewExName('') }}
                              className="px-2 py-1 bg-gray-600 hover:bg-gray-500 text-white text-xs rounded">✕</button>
                          </div>
                        ) : (
                          <div className="flex gap-1">
                            <select
                              value={exercise.exercise_id || ''}
                              onChange={(e) => handleExerciseChange(exIdx, 'exercise_id', parseInt(e.target.value))}
                              className="flex-1 px-3 py-2 bg-gray-600 border border-gray-500 rounded text-white text-sm focus:outline-none focus:border-violet-600"
                            >
                              <option value="">Select exercise</option>
                              {exercises.map((ex) => (
                                <option key={ex.id} value={ex.id}>{ex.name}</option>
                              ))}
                            </select>
                            <button type="button" onClick={() => setCreatingExFor(exIdx)}
                              className="px-2 py-1 bg-gray-600 hover:bg-violet-700 text-violet-300 text-xs rounded whitespace-nowrap">
                              + New
                            </button>
                          </div>
                        )}
                      </div>
                      <button
                        type="button"
                        onClick={() => handleRemoveExercise(exIdx)}
                        className="px-3 py-2 bg-red-600 hover:bg-red-700 text-white text-sm rounded transition-colors"
                      >
                        Remove
                      </button>
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between">
                        <label className="block text-xs font-medium text-gray-300">Sets</label>
                        <button
                          type="button"
                          onClick={() => handleAddSet(exIdx)}
                          className="text-xs px-2 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded transition-colors"
                        >
                          Add Set
                        </button>
                      </div>

                      <table className="w-full text-xs text-gray-300">
                        <thead>
                          <tr className="border-b border-gray-600">
                            <th className="text-left py-1">Set</th>
                            <th className="text-left py-1">Reps</th>
                            <th className="text-left py-1">Load (kg)</th>
                            <th className="text-left py-1">Rest (s)</th>
                            <th></th>
                          </tr>
                        </thead>
                        <tbody>
                          {exercise.sets?.map((set, setIdx) => (
                            <tr key={setIdx} className="border-b border-gray-600">
                              <td className="py-1">{set.set_number}</td>
                              <td className="py-1">
                                <input
                                  type="number"
                                  value={set.reps}
                                  onChange={(e) =>
                                    handleSetChange(exIdx, setIdx, 'reps', e.target.value)
                                  }
                                  className="w-12 px-2 py-1 bg-gray-600 border border-gray-500 rounded text-white text-xs"
                                  min="1"
                                />
                              </td>
                              <td className="py-1">
                                <input
                                  type="number"
                                  value={set.load_kg}
                                  onChange={(e) =>
                                    handleSetChange(exIdx, setIdx, 'load_kg', e.target.value)
                                  }
                                  className="w-16 px-2 py-1 bg-gray-600 border border-gray-500 rounded text-white text-xs"
                                  step="0.5"
                                  min="0"
                                />
                              </td>
                              <td className="py-1">
                                <input
                                  type="number"
                                  value={set.rest_seconds}
                                  onChange={(e) =>
                                    handleSetChange(exIdx, setIdx, 'rest_seconds', e.target.value)
                                  }
                                  className="w-14 px-2 py-1 bg-gray-600 border border-gray-500 rounded text-white text-xs"
                                  min="0"
                                />
                              </td>
                              <td className="py-1">
                                <button
                                  type="button"
                                  onClick={() => handleRemoveSet(exIdx, setIdx)}
                                  className="text-red-400 hover:text-red-300 text-xs"
                                >
                                  ✕
                                </button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                ))}
              </div>

              <div className="flex gap-3 pt-4 border-t border-gray-700">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="flex-1 px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 px-4 py-2 bg-violet-600 hover:bg-violet-700 text-white rounded font-medium transition-colors"
                >
                  Save Plan
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Schedule Plan Modal */}
      {schedulingPlan && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50">
          <div className="bg-gray-800 rounded-lg p-6 w-full max-w-md border border-gray-700">
            <h3 className="text-lg font-bold text-white mb-1">Schedule Plan</h3>
            <p className="text-violet-400 text-sm mb-4">{schedulingPlan.name}</p>
            <div className="space-y-3">
              <div>
                <label className="text-xs text-gray-400 mb-1 block">Athlete</label>
                <select
                  value={schedForm.athlete_id}
                  onChange={e => setSchedForm({ ...schedForm, athlete_id: e.target.value })}
                  className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-white text-sm focus:outline-none focus:border-violet-600"
                >
                  <option value="">Wybierz zawodnika...</option>
                  <option value={user?.id ?? ''}>Ja ({user?.username})</option>
                  {athletes.map(a => <option key={a.id} value={a.id}>{a.username}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs text-gray-400 mb-1 block">Date</label>
                  <input type="date" value={schedForm.date}
                    onChange={e => setSchedForm({ ...schedForm, date: e.target.value })}
                    className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-white text-sm focus:outline-none focus:border-violet-600" />
                </div>
                <div>
                  <label className="text-xs text-gray-400 mb-1 block">Time (optional)</label>
                  <input type="time" value={schedForm.time_slot}
                    onChange={e => setSchedForm({ ...schedForm, time_slot: e.target.value })}
                    className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-white text-sm focus:outline-none focus:border-violet-600" />
                </div>
              </div>
              <div>
                <label className="text-xs text-gray-400 mb-1 block">Notes</label>
                <textarea value={schedForm.notes}
                  onChange={e => setSchedForm({ ...schedForm, notes: e.target.value })}
                  rows={2}
                  className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded text-white text-sm resize-none focus:outline-none focus:border-violet-600" />
              </div>
            </div>
            <div className="flex gap-2 mt-5">
              <button onClick={() => setSchedulingPlan(null)}
                className="flex-1 py-2 bg-gray-700 hover:bg-gray-600 text-white text-sm rounded">
                Cancel
              </button>
              <button onClick={handleSchedulePlan}
                disabled={!schedForm.athlete_id || !schedForm.date}
                className="flex-1 py-2 bg-violet-600 hover:bg-violet-700 disabled:bg-gray-600 text-white text-sm rounded font-medium">
                Add to Calendar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
