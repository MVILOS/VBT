from pydantic import BaseModel, EmailStr, ConfigDict
from typing import Optional, List
from datetime import datetime


# User Schemas
class UserCreate(BaseModel):
    email: Optional[str] = None
    username: str
    password: str
    role: str = "athlete"


class UserLogin(BaseModel):
    email: Optional[str] = None
    username: Optional[str] = None
    password: str


class UserResponse(BaseModel):
    id: int
    email: Optional[str] = None
    username: str
    role: str
    is_active: bool
    created_at: datetime
    coach_id: Optional[int] = None

    class Config:
        from_attributes = True


class UserUpdate(BaseModel):
    email: Optional[str] = None
    username: Optional[str] = None
    password: Optional[str] = None
    is_active: Optional[bool] = None


class TokenResponse(BaseModel):
    access_token: str
    token_type: str
    user: UserResponse


# Exercise Schemas
class ExerciseCreate(BaseModel):
    name: str
    category: Optional[str] = None
    mvt: Optional[float] = None
    description: Optional[str] = None


class ExerciseResponse(BaseModel):
    id: int
    name: str
    category: Optional[str]
    mvt: Optional[float]
    description: Optional[str]
    created_by: Optional[int]

    class Config:
        from_attributes = True


# Plan Set Schemas
class PlanSetCreate(BaseModel):
    set_number: int
    reps: int
    load_kg: float
    load_percent_1rm: Optional[float] = None
    target_velocity_min: Optional[float] = None
    target_velocity_max: Optional[float] = None
    rest_seconds: int = 180


class PlanSetResponse(BaseModel):
    id: int
    plan_exercise_id: int
    set_number: int
    reps: int
    load_kg: float
    load_percent_1rm: Optional[float]
    target_velocity_min: Optional[float]
    target_velocity_max: Optional[float]
    rest_seconds: int

    class Config:
        from_attributes = True


# Plan Exercise Schemas
class PlanExerciseCreate(BaseModel):
    exercise_id: int
    order_index: int = 0
    notes: Optional[str] = None
    sets: List[PlanSetCreate]


class PlanExerciseResponse(BaseModel):
    id: int
    plan_id: int
    exercise_id: int
    order_index: int
    notes: Optional[str]
    exercise: ExerciseResponse
    sets: List[PlanSetResponse]

    class Config:
        from_attributes = True


# Training Plan Schemas
class TrainingPlanCreate(BaseModel):
    name: str
    description: Optional[str] = None
    assigned_to: Optional[int] = None
    is_template: bool = False
    exercises: List[PlanExerciseCreate] = []


class TrainingPlanUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    assigned_to: Optional[int] = None
    is_template: Optional[bool] = None
    exercises: Optional[List[PlanExerciseCreate]] = None


class TrainingPlanResponse(BaseModel):
    id: int
    name: str
    description: Optional[str]
    owner_id: int
    assigned_to: Optional[int]
    created_at: datetime
    updated_at: datetime
    is_template: bool
    exercises: List[PlanExerciseResponse]

    class Config:
        from_attributes = True


# Calendar Entry Schemas
class CalendarEntryCreate(BaseModel):
    athlete_id: int
    plan_id: Optional[int] = None
    date: str
    time_slot: Optional[str] = None
    title: str
    notes: Optional[str] = None
    overrides_json: Optional[str] = None


class CalendarEntryUpdate(BaseModel):
    plan_id: Optional[int] = None
    date: Optional[str] = None
    time_slot: Optional[str] = None
    title: Optional[str] = None
    notes: Optional[str] = None
    status: Optional[str] = None
    overrides_json: Optional[str] = None


class CalendarEntryResponse(BaseModel):
    id: int
    athlete_id: int
    plan_id: Optional[int]
    date: str
    time_slot: Optional[str]
    title: str
    notes: Optional[str]
    status: str
    created_by: int
    overrides_json: Optional[str] = None

    class Config:
        from_attributes = True


# Workout Session & Rep Result Schemas
class RepResultCreate(BaseModel):
    exercise_id: int
    set_number: int
    rep_number: int
    mean_velocity: float
    peak_velocity: float
    load_kg: float
    power_watts: Optional[float] = None
    estimated_1rm: Optional[float] = None


class RepResultResponse(BaseModel):
    id: int
    session_id: int
    exercise_id: int
    set_number: int
    rep_number: int
    mean_velocity: float
    peak_velocity: float
    load_kg: float
    power_watts: Optional[float]
    estimated_1rm: Optional[float]
    timestamp: Optional[datetime] = None

    class Config:
        from_attributes = True


class RepResultUpdate(BaseModel):
    load_kg: Optional[float] = None
    set_number: Optional[int] = None
    rep_number: Optional[int] = None
    estimated_1rm: Optional[float] = None


class WorkoutSessionCreate(BaseModel):
    athlete_id: Optional[int] = None
    plan_id: Optional[int] = None
    calendar_entry_id: Optional[int] = None
    started_at: Optional[datetime] = None
    finished_at: Optional[datetime] = None
    duration_seconds: Optional[int] = None
    notes: Optional[str] = None
    reps: List[RepResultCreate] = []


class StartLiveSessionRequest(BaseModel):
    athlete_id: Optional[int] = None
    plan_id: Optional[int] = None
    notes: Optional[str] = None
    started_at: Optional[datetime] = None


class AppendRepsRequest(BaseModel):
    reps: List[RepResultCreate]
    finished_at: Optional[datetime] = None


class WorkoutSessionResponse(BaseModel):
    id: int
    athlete_id: int
    plan_id: Optional[int]
    calendar_entry_id: Optional[int]
    started_at: datetime
    finished_at: Optional[datetime]
    duration_seconds: Optional[int]
    notes: Optional[str]
    reps: List[RepResultResponse]

    class Config:
        from_attributes = True


# Analytics Schemas
class AnalyticsVelocityPoint(BaseModel):
    date: str
    mean_velocity: float
    peak_velocity: float
    load_kg: float
    exercise_name: str
    rep_number: int


class VelocityTrendResponse(BaseModel):
    data_points: List[AnalyticsVelocityPoint]
    average_velocity: float
    trend: str  # "improving", "declining", "stable"


class OneRMProgressResponse(BaseModel):
    data_points: List[dict]
    latest_1rm: float
    progress_percentage: float


class VolumeAnalyticsResponse(BaseModel):
    total_volume_kg: float
    average_volume_per_session: float
    session_count: int
    period_days: int


class CompareAthletesResponse(BaseModel):
    athletes: List[dict]
    top_performer: Optional[dict]


# Velocity Trace Schemas
class VelocityPoint(BaseModel):
    timestamp_ms: int
    velocity_ms: float


class VelocityTraceCreate(BaseModel):
    points: List[VelocityPoint]


class VelocityTraceResponse(BaseModel):
    id: int
    rep_result_id: int
    session_id: int
    points: List[VelocityPoint]
    recorded_at: datetime

    model_config = ConfigDict(from_attributes=True)
