import { useEffect, useMemo, useState } from 'react'
import client from '../api/client'
import { RepResult, WorkoutSession } from '../types'
import { estimate1rmFromVelocity } from '../utils/estimate1rm'

interface Props {
  sessionId: number
  onClose: () => void
  /** Wołane po każdej zmianie utrwalonej na serwerze (odświeżenie listy sesji). */
  onChanged?: () => void
}

interface SetGroup {
  setNumber: number
  loadKg: number
  exerciseName: string
  reps: RepResult[]
}

/**
 * Szczegóły treningu z edycją serii - parytet z mobilnym SessionDetailViewModel:
 * korekta ciężaru serii, rozdzielanie/łączenie serii, usuwanie powtórzeń oraz
 * usuwanie całego treningu (z dwustopniowym potwierdzeniem). Wszystko na istniejących
 * endpointach PATCH/DELETE /sessions/{id}/reps/{repId} i DELETE /sessions/{id}.
 */
export default function SessionDetailModal({ sessionId, onClose, onChanged }: Props) {
  const [session, setSession] = useState<WorkoutSession | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)

  // Lokalny stan edycji ciężaru per seria (klucz = setNumber)
  const [loadEdits, setLoadEdits] = useState<Record<number, string>>({})
  // Stan panelu rozdzielania per seria: numer powtórzenia i nowy ciężar
  const [splitFor, setSplitFor] = useState<number | null>(null)
  const [splitFromRep, setSplitFromRep] = useState('2')
  const [splitLoad, setSplitLoad] = useState('')

  const fetchSession = async () => {
    try {
      const res = await client.get<WorkoutSession>(`/sessions/${sessionId}`)
      setSession(res.data)
      const edits: Record<number, string> = {}
      groupBySet(res.data.reps).forEach(g => { edits[g.setNumber] = String(g.loadKg) })
      setLoadEdits(edits)
    } catch (e) {
      setError('Nie udało się wczytać treningu.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchSession()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId])

  const sets = useMemo(() => (session ? groupBySet(session.reps) : []), [session])

  const refresh = async () => {
    await fetchSession()
    onChanged?.()
  }

  const withSaving = async (fn: () => Promise<void>, errMsg: string) => {
    setSaving(true)
    setError(null)
    try {
      await fn()
      await refresh()
    } catch (e) {
      setError(errMsg)
    } finally {
      setSaving(false)
    }
  }

  // Korekta ciężaru całej serii - PATCH każdego powtórzenia (load + przeliczone 1RM).
  const updateSetWeight = (setNumber: number) => {
    const newLoad = parseFloat(loadEdits[setNumber])
    if (isNaN(newLoad) || newLoad < 0) { setError('Podaj poprawny ciężar.'); return }
    const reps = sets.find(s => s.setNumber === setNumber)?.reps ?? []
    withSaving(async () => {
      await Promise.all(reps.map(rep =>
        client.patch(`/sessions/${sessionId}/reps/${rep.id}`, {
          load_kg: newLoad,
          estimated_1rm: estimate1rmFromVelocity(newLoad, rep.mean_velocity),
        })
      ))
    }, 'Błąd zapisu ciężaru serii.')
  }

  // Scal serię z poprzednią - przenumeruj jej powtórzenia pod poprzednią serię.
  const mergeWithPrevious = (setNumber: number) => {
    if (setNumber <= 1) return
    const target = setNumber - 1
    const repsToMove = [...(sets.find(s => s.setNumber === setNumber)?.reps ?? [])].sort((a, b) => a.rep_number - b.rep_number)
    const startRep = Math.max(0, ...(sets.find(s => s.setNumber === target)?.reps.map(r => r.rep_number) ?? [0])) + 1
    withSaving(async () => {
      await Promise.all(repsToMove.map((rep, i) =>
        client.patch(`/sessions/${sessionId}/reps/${rep.id}`, {
          set_number: target,
          rep_number: startRep + i,
        })
      ))
    }, 'Błąd scalania serii.')
  }

  // Rozdziel serię - powtórzenia od wybranego numeru w górę idą do nowej serii.
  const applySplit = (setNumber: number) => {
    const fromRep = parseInt(splitFromRep)
    const newLoad = parseFloat(splitLoad)
    if (isNaN(fromRep) || fromRep < 1) { setError('Podaj numer powtórzenia.'); return }
    if (isNaN(newLoad) || newLoad < 0) { setError('Podaj ciężar nowej serii.'); return }
    const repsToMove = [...(sets.find(s => s.setNumber === setNumber)?.reps ?? [])]
      .filter(r => r.rep_number >= fromRep)
      .sort((a, b) => a.rep_number - b.rep_number)
    if (repsToMove.length === 0) { setError('Brak powtórzeń do rozdzielenia.'); return }
    const newSet = Math.max(...sets.map(s => s.setNumber)) + 1
    withSaving(async () => {
      await Promise.all(repsToMove.map((rep, i) =>
        client.patch(`/sessions/${sessionId}/reps/${rep.id}`, {
          set_number: newSet,
          rep_number: i + 1,
          load_kg: newLoad,
          estimated_1rm: estimate1rmFromVelocity(newLoad, rep.mean_velocity),
        })
      ))
      setSplitFor(null)
    }, 'Błąd rozdzielania serii.')
  }

  const deleteRep = (repId: number) => {
    withSaving(async () => {
      await client.delete(`/sessions/${sessionId}/reps/${repId}`)
    }, 'Błąd usuwania powtórzenia.')
  }

  const deleteSession = async () => {
    setSaving(true)
    setError(null)
    try {
      await client.delete(`/sessions/${sessionId}`)
      onChanged?.()
      onClose()
    } catch (e) {
      setError('Błąd usuwania treningu.')
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/60 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-lg w-full max-w-2xl border border-gray-200 max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-gray-200">
          <div>
            <h3 className="text-lg font-bold text-gray-900">Szczegóły treningu</h3>
            {session && (
              <p className="text-sm text-gray-500">
                {new Date(session.started_at).toLocaleString('pl-PL', { dateStyle: 'long', timeStyle: 'short' })}
              </p>
            )}
          </div>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-700 text-2xl leading-none">×</button>
        </div>

        <div className="p-5 overflow-y-auto">
          {error && <div className="mb-3 px-3 py-2 bg-red-50 text-red-700 text-sm rounded">{error}</div>}
          {loading ? (
            <p className="text-gray-500 text-sm py-8 text-center">Ładowanie…</p>
          ) : sets.length === 0 ? (
            <p className="text-gray-500 text-sm py-8 text-center">Brak powtórzeń w tym treningu.</p>
          ) : (
            <div className="space-y-4">
              {sets.map(set => (
                <div key={set.setNumber} className="border border-gray-200 rounded-lg p-4">
                  <div className="flex items-center justify-between mb-2 flex-wrap gap-2">
                    <div className="font-semibold text-gray-900">
                      Seria {set.setNumber}
                      <span className="text-gray-500 font-normal"> · {set.exerciseName}</span>
                    </div>
                    <div className="flex items-center gap-2">
                      {set.setNumber > 1 && (
                        <button disabled={saving} onClick={() => mergeWithPrevious(set.setNumber)}
                          className="px-2.5 py-1.5 text-xs rounded bg-gray-100 hover:bg-gray-200 text-gray-700 disabled:opacity-50">
                          ⭱ Połącz z poprzednią
                        </button>
                      )}
                      <button disabled={saving}
                        onClick={() => { setSplitFor(splitFor === set.setNumber ? null : set.setNumber); setSplitLoad(String(set.loadKg)) }}
                        className="px-2.5 py-1.5 text-xs rounded bg-gray-100 hover:bg-gray-200 text-gray-700 disabled:opacity-50">
                        �somesplit ✂ Rozdziel
                      </button>
                    </div>
                  </div>

                  {/* Edycja ciężaru serii */}
                  <div className="flex items-center gap-2 mb-3">
                    <label className="text-xs text-gray-500">Ciężar (kg)</label>
                    <input type="number" step="0.5" value={loadEdits[set.setNumber] ?? ''}
                      onChange={e => setLoadEdits({ ...loadEdits, [set.setNumber]: e.target.value })}
                      className="w-24 px-2 py-1 bg-gray-100 border border-gray-300 rounded text-gray-900 text-sm focus:outline-none focus:border-violet-600" />
                    <button disabled={saving || parseFloat(loadEdits[set.setNumber]) === set.loadKg}
                      onClick={() => updateSetWeight(set.setNumber)}
                      className="px-3 py-1 text-xs rounded bg-violet-600 hover:bg-violet-700 text-white font-medium disabled:opacity-40">
                      Zapisz ciężar
                    </button>
                  </div>

                  {/* Panel rozdzielania */}
                  {splitFor === set.setNumber && (
                    <div className="mb-3 p-3 bg-violet-50 rounded flex items-end gap-2 flex-wrap">
                      <div>
                        <label className="text-xs text-gray-500 block mb-1">Od powtórzenia</label>
                        <input type="number" min={1} value={splitFromRep}
                          onChange={e => setSplitFromRep(e.target.value)}
                          className="w-20 px-2 py-1 bg-white border border-gray-300 rounded text-gray-900 text-sm" />
                      </div>
                      <div>
                        <label className="text-xs text-gray-500 block mb-1">Nowy ciężar (kg)</label>
                        <input type="number" step="0.5" value={splitLoad}
                          onChange={e => setSplitLoad(e.target.value)}
                          className="w-24 px-2 py-1 bg-white border border-gray-300 rounded text-gray-900 text-sm" />
                      </div>
                      <button disabled={saving} onClick={() => applySplit(set.setNumber)}
                        className="px-3 py-1.5 text-xs rounded bg-violet-600 hover:bg-violet-700 text-white font-medium disabled:opacity-50">
                        Rozdziel serię
                      </button>
                    </div>
                  )}

                  {/* Powtórzenia */}
                  <div className="overflow-x-auto">
                    <table className="w-full text-sm">
                      <thead>
                        <tr className="text-left text-gray-500 text-xs">
                          <th className="py-1 pr-3">#</th>
                          <th className="py-1 pr-3">Śr. v (m/s)</th>
                          <th className="py-1 pr-3">Vmax (m/s)</th>
                          <th className="py-1 pr-3">Moc (W)</th>
                          <th className="py-1 pr-3">1RM (kg)</th>
                          <th className="py-1"></th>
                        </tr>
                      </thead>
                      <tbody>
                        {set.reps.map(rep => (
                          <tr key={rep.id} className="border-t border-gray-100">
                            <td className="py-1 pr-3 text-gray-900">{rep.rep_number}</td>
                            <td className="py-1 pr-3 text-gray-700">{rep.mean_velocity.toFixed(2)}</td>
                            <td className="py-1 pr-3 text-gray-700">{rep.peak_velocity.toFixed(2)}</td>
                            <td className="py-1 pr-3 text-gray-700">{rep.power_watts != null ? Math.round(rep.power_watts) : '—'}</td>
                            <td className="py-1 pr-3 text-gray-700">{rep.estimated_1rm != null ? Math.round(rep.estimated_1rm) : '—'}</td>
                            <td className="py-1 text-right">
                              <button disabled={saving} onClick={() => deleteRep(rep.id)}
                                className="text-red-500 hover:text-red-700 disabled:opacity-40" title="Usuń powtórzenie">×</button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Stopka: usuwanie całego treningu z zabezpieczeniem */}
        <div className="p-5 border-t border-gray-200 flex items-center justify-between gap-2">
          {confirmDelete ? (
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-sm text-red-700">Usunąć cały trening? Tej operacji nie można cofnąć.</span>
              <button disabled={saving} onClick={deleteSession}
                className="px-3 py-2 text-sm rounded bg-red-600 hover:bg-red-700 text-white font-medium disabled:opacity-50">
                Tak, usuń
              </button>
              <button disabled={saving} onClick={() => setConfirmDelete(false)}
                className="px-3 py-2 text-sm rounded bg-gray-100 hover:bg-gray-200 text-gray-700">
                Anuluj
              </button>
            </div>
          ) : (
            <button onClick={() => setConfirmDelete(true)}
              className="px-3 py-2 text-sm rounded bg-red-50 hover:bg-red-100 text-red-700">
              Usuń trening
            </button>
          )}
          <button onClick={onClose} className="px-4 py-2 text-sm rounded bg-gray-100 hover:bg-gray-200 text-gray-900">
            Zamknij
          </button>
        </div>
      </div>
    </div>
  )
}

/** Grupuje powtórzenia po numerze serii; ciężar i ćwiczenie bierze z 1. powtórzenia. */
function groupBySet(reps: RepResult[]): SetGroup[] {
  const bySet = new Map<number, RepResult[]>()
  for (const rep of reps) {
    const arr = bySet.get(rep.set_number) ?? []
    arr.push(rep)
    bySet.set(rep.set_number, arr)
  }
  return Array.from(bySet.entries())
    .sort((a, b) => a[0] - b[0])
    .map(([setNumber, groupReps]) => {
      const sorted = [...groupReps].sort((a, b) => a.rep_number - b.rep_number)
      return {
        setNumber,
        loadKg: sorted[0]?.load_kg ?? 0,
        exerciseName: sorted[0]?.exercise?.name ?? 'Ćwiczenie',
        reps: sorted,
      }
    })
}
