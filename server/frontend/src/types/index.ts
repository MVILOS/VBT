export interface User {
  id: number
  email?: string | null
  username: string
  role: 'coach' | 'athlete' | 'admin'
  is_active: boolean
  created_at: string
  coach_id?: number
}

export interface ExerciseDefinition {
  id: number
  name: string
  category?: string
  mvt?: number
  description?: string
}

export interface PlanSet {
  id?: number
  set_number: number
  reps: number
  load_kg: number
  load_percent_1rm?: number
  target_velocity_min?: number
  target_velocity_max?: number
  rest_seconds: number
}

export interface PlanExercise {
  id?: number
  exercise_id: number
  exercise?: ExerciseDefinition
  order_index: number
  notes?: string
  sets: PlanSet[]
}

export interface TrainingPlan {
  id: number
  name: string
  description?: string
  owner_id: number
  assigned_to?: number
  is_template: boolean
  created_at: string
  exercises: PlanExercise[]
}

export interface SetOverride {
  exercise_id: number
  exercise_name?: string
  set_number: number
  reps: number
  load_kg: number
}

export interface CalendarEntry {
  id: number
  athlete_id: number
  athlete?: User
  plan_id?: number
  plan?: TrainingPlan
  date: string
  time_slot?: string
  title: string
  notes?: string
  status: 'scheduled' | 'completed' | 'skipped'
  overrides_json?: string
}

export interface RepResult {
  id: number
  exercise_id: number
  exercise?: ExerciseDefinition
  set_number: number
  rep_number: number
  mean_velocity: number
  peak_velocity: number
  load_kg: number
  power_watts?: number
  estimated_1rm?: number
  timestamp: string
}

export interface WorkoutSession {
  id: number
  athlete_id: number
  athlete?: User
  plan_id?: number
  started_at: string
  finished_at?: string
  duration_seconds?: number
  notes?: string
  reps: RepResult[]
}

export interface AnalyticsPoint {
  date: string
  mean_velocity: number
  load_kg: number
  exercise_name: string
}
