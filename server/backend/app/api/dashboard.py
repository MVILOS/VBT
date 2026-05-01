from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from datetime import datetime, timedelta

from app.db.database import get_db
from app.models import User, TrainingPlan, WorkoutSession, AthleteCoach
from app.schemas import WorkoutSessionResponse
from app.core.security import get_current_user

router = APIRouter(prefix="/dashboard", tags=["dashboard"])


@router.get("/stats")
def get_stats(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    week_ago = datetime.utcnow() - timedelta(days=7)

    if current_user.role == "admin":
        athletes = db.query(User).filter(User.role == "athlete").count()
        plans = db.query(TrainingPlan).count()
        sessions_week = (
            db.query(WorkoutSession)
            .filter(WorkoutSession.started_at >= week_ago)
            .count()
        )
    elif current_user.role == "coach":
        athletes = db.query(AthleteCoach).filter(AthleteCoach.coach_id == current_user.id).count()
        plans = db.query(TrainingPlan).filter(TrainingPlan.owner_id == current_user.id).count()
        athlete_ids = [r.athlete_id for r in db.query(AthleteCoach.athlete_id).filter(AthleteCoach.coach_id == current_user.id)]
        all_ids = [current_user.id] + athlete_ids
        sessions_week = (
            db.query(WorkoutSession)
            .filter(WorkoutSession.athlete_id.in_(all_ids))
            .filter(WorkoutSession.started_at >= week_ago)
            .count()
        )
    else:
        athletes = 0
        plans = db.query(TrainingPlan).filter(TrainingPlan.assigned_to == current_user.id).count()
        sessions_week = (
            db.query(WorkoutSession)
            .filter(WorkoutSession.athlete_id == current_user.id)
            .filter(WorkoutSession.started_at >= week_ago)
            .count()
        )

    return {
        "total_athletes": athletes,
        "active_plans": plans,
        "sessions_this_week": sessions_week,
    }


@router.get("/recent-sessions")
def recent_sessions(
    limit: int = Query(10),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.role == "admin":
        sessions = (
            db.query(WorkoutSession)
            .order_by(WorkoutSession.started_at.desc())
            .limit(limit)
            .all()
        )
    elif current_user.role == "coach":
        athlete_ids = [r.athlete_id for r in db.query(AthleteCoach.athlete_id).filter(AthleteCoach.coach_id == current_user.id)]
        all_ids = [current_user.id] + athlete_ids
        sessions = (
            db.query(WorkoutSession)
            .filter(WorkoutSession.athlete_id.in_(all_ids))
            .order_by(WorkoutSession.started_at.desc())
            .limit(limit)
            .all()
        )
    else:
        sessions = (
            db.query(WorkoutSession)
            .filter(WorkoutSession.athlete_id == current_user.id)
            .order_by(WorkoutSession.started_at.desc())
            .limit(limit)
            .all()
        )

    result = []
    for s in sessions:
        athlete = db.query(User).filter(User.id == s.athlete_id).first()
        result.append({
            "id": s.id,
            "athlete_id": s.athlete_id,
            "athlete_name": athlete.username if athlete else "Unknown",
            "started_at": s.started_at.isoformat(),
            "duration_seconds": s.duration_seconds,
            "reps_count": len(s.reps),
        })
    return result
