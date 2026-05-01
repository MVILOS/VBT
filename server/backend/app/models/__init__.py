from sqlalchemy import Column, Integer, String, Float, Boolean, DateTime, ForeignKey, Text, Table
from sqlalchemy.orm import relationship, DeclarativeBase
from datetime import datetime


class Base(DeclarativeBase):
    pass


class AthleteCoach(Base):
    """Many-to-many: jeden zawodnik może mieć wielu trenerów."""
    __tablename__ = "athlete_coaches"
    athlete_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    coach_id   = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    created_at = Column(DateTime, default=datetime.utcnow)


class User(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True)
    email = Column(String, unique=True, index=True, nullable=True)
    username = Column(String, unique=True, index=True, nullable=False)
    hashed_password = Column(String, nullable=False)
    role = Column(String, default="athlete")  # "coach" or "athlete"
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    coach_id = Column(Integer, ForeignKey("users.id"), nullable=True)

    owned_plans = relationship(
        "TrainingPlan",
        foreign_keys="TrainingPlan.owner_id",
        back_populates="owner",
        lazy="dynamic",
    )
    sessions = relationship(
        "WorkoutSession",
        foreign_keys="WorkoutSession.athlete_id",
        back_populates="athlete",
        lazy="dynamic",
    )


class ExerciseDefinition(Base):
    __tablename__ = "exercise_definitions"
    id = Column(Integer, primary_key=True)
    name = Column(String, nullable=False)
    category = Column(String)
    mvt = Column(Float)
    description = Column(Text)
    created_by = Column(Integer, ForeignKey("users.id"), nullable=True)


class TrainingPlan(Base):
    __tablename__ = "training_plans"
    id = Column(Integer, primary_key=True)
    name = Column(String, nullable=False)
    description = Column(Text)
    owner_id = Column(Integer, ForeignKey("users.id"))
    assigned_to = Column(Integer, ForeignKey("users.id"), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    is_template = Column(Boolean, default=False)

    owner = relationship("User", foreign_keys=[owner_id], back_populates="owned_plans")
    assignee = relationship("User", foreign_keys=[assigned_to])
    exercises = relationship("PlanExercise", back_populates="plan", cascade="all, delete-orphan")


class PlanExercise(Base):
    __tablename__ = "plan_exercises"
    id = Column(Integer, primary_key=True)
    plan_id = Column(Integer, ForeignKey("training_plans.id"))
    exercise_id = Column(Integer, ForeignKey("exercise_definitions.id"))
    order_index = Column(Integer, default=0)
    notes = Column(Text)

    plan = relationship("TrainingPlan", back_populates="exercises")
    exercise = relationship("ExerciseDefinition")
    sets = relationship("PlanSet", back_populates="plan_exercise", cascade="all, delete-orphan")


class PlanSet(Base):
    __tablename__ = "plan_sets"
    id = Column(Integer, primary_key=True)
    plan_exercise_id = Column(Integer, ForeignKey("plan_exercises.id"))
    set_number = Column(Integer)
    reps = Column(Integer)
    load_kg = Column(Float)
    load_percent_1rm = Column(Float, nullable=True)
    target_velocity_min = Column(Float, nullable=True)
    target_velocity_max = Column(Float, nullable=True)
    rest_seconds = Column(Integer, default=180)

    plan_exercise = relationship("PlanExercise", back_populates="sets")


class CalendarEntry(Base):
    __tablename__ = "calendar_entries"
    id = Column(Integer, primary_key=True)
    athlete_id = Column(Integer, ForeignKey("users.id"))
    plan_id = Column(Integer, ForeignKey("training_plans.id"), nullable=True)
    date = Column(String, nullable=False)  # "2026-04-15"
    time_slot = Column(String, nullable=True)  # "09:00"
    title = Column(String)
    notes = Column(Text)
    status = Column(String, default="scheduled")  # scheduled, completed, skipped
    created_by = Column(Integer, ForeignKey("users.id"), nullable=True)
    overrides_json = Column(Text, nullable=True)  # JSON: [{exercise_id, set_number, reps, load_kg}]

    athlete = relationship("User", foreign_keys=[athlete_id])
    plan = relationship("TrainingPlan", foreign_keys=[plan_id])


class WorkoutSession(Base):
    __tablename__ = "workout_sessions"
    id = Column(Integer, primary_key=True)
    athlete_id = Column(Integer, ForeignKey("users.id"))
    plan_id = Column(Integer, ForeignKey("training_plans.id"), nullable=True)
    calendar_entry_id = Column(Integer, ForeignKey("calendar_entries.id"), nullable=True)
    started_at = Column(DateTime, default=datetime.utcnow)
    finished_at = Column(DateTime, nullable=True)
    duration_seconds = Column(Integer, nullable=True)
    notes = Column(Text)

    athlete = relationship("User", foreign_keys=[athlete_id], back_populates="sessions")
    reps = relationship("RepResult", back_populates="session", cascade="all, delete-orphan")


class RepResult(Base):
    __tablename__ = "rep_results"
    id = Column(Integer, primary_key=True)
    session_id = Column(Integer, ForeignKey("workout_sessions.id"))
    exercise_id = Column(Integer, ForeignKey("exercise_definitions.id"))
    set_number = Column(Integer)
    rep_number = Column(Integer)
    mean_velocity = Column(Float)
    peak_velocity = Column(Float)
    load_kg = Column(Float)
    power_watts = Column(Float, nullable=True)
    estimated_1rm = Column(Float, nullable=True)
    timestamp = Column(DateTime, default=datetime.utcnow)

    session = relationship("WorkoutSession", back_populates="reps")
    exercise = relationship("ExerciseDefinition")


class RepVelocityTrace(Base):
    __tablename__ = "rep_velocity_traces"
    id = Column(Integer, primary_key=True)
    rep_result_id = Column(Integer, ForeignKey("rep_results.id"), nullable=False)
    session_id = Column(Integer, ForeignKey("workout_sessions.id"), nullable=False)
    # JSON lista punktów: [{"timestamp_ms": 100, "velocity_ms": 1.05}, ...]
    points_json = Column(Text, nullable=False, default="[]")
    recorded_at = Column(DateTime, default=datetime.utcnow)

    rep_result = relationship("RepResult", backref="velocity_trace")
