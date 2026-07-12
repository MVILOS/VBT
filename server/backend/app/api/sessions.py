from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime
import json

from app.db.database import get_db
from app.models import User, WorkoutSession, RepResult, RepVelocityTrace, AthleteCoach
from app.schemas import (
    WorkoutSessionCreate,
    WorkoutSessionResponse,
    VelocityTraceCreate,
    VelocityTraceResponse,
    VelocityPoint,
    StartLiveSessionRequest,
    AppendRepsRequest,
)
from app.core.security import get_current_user

router = APIRouter(prefix="/sessions", tags=["sessions"])


def check_session_access(session: WorkoutSession, current_user: User, db: Session) -> None:
    """Dostęp: właściciel sesji, admin, lub trener faktycznie nadzorujący tego zawodnika."""
    if session.athlete_id == current_user.id or current_user.role == "admin":
        return
    if current_user.role == "coach" and db.query(AthleteCoach).filter_by(
        coach_id=current_user.id, athlete_id=session.athlete_id
    ).first():
        return
    raise HTTPException(status_code=403, detail="Access denied")


@router.get("", response_model=List[WorkoutSessionResponse])
def list_sessions(
    athlete_id: Optional[int] = Query(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.role == "admin":
        if athlete_id:
            sessions = (
                db.query(WorkoutSession)
                .filter(WorkoutSession.athlete_id == athlete_id)
                .order_by(WorkoutSession.started_at.desc())
                .all()
            )
        else:
            sessions = (
                db.query(WorkoutSession)
                .order_by(WorkoutSession.started_at.desc())
                .all()
            )
    elif current_user.role == "coach":
        if athlete_id:
            # Specific athlete filter: coach can see own sessions or supervised athletes
            if athlete_id != current_user.id:
                if not db.query(AthleteCoach).filter_by(
                    coach_id=current_user.id, athlete_id=athlete_id
                ).first():
                    raise HTTPException(status_code=403, detail="Athlete not under your supervision")
            target_ids = [athlete_id]
        else:
            # Default: all supervised athletes + coach's own sessions
            supervised = db.query(AthleteCoach).filter_by(coach_id=current_user.id).all()
            target_ids = [ac.athlete_id for ac in supervised] + [current_user.id]

        sessions = (
            db.query(WorkoutSession)
            .filter(WorkoutSession.athlete_id.in_(target_ids))
            .order_by(WorkoutSession.started_at.desc())
            .all()
        )
    else:
        sessions = (
            db.query(WorkoutSession)
            .filter(WorkoutSession.athlete_id == current_user.id)
            .order_by(WorkoutSession.started_at.desc())
            .all()
        )
    return [WorkoutSessionResponse.model_validate(s) for s in sessions]


@router.post("", response_model=WorkoutSessionResponse)
def create_session(
    session_data: WorkoutSessionCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    # Determine athlete: coach can record for themselves or their athletes
    if current_user.role == "coach" and session_data.athlete_id and session_data.athlete_id != 0:
        target_athlete_id = session_data.athlete_id
        if target_athlete_id != current_user.id:
            if not db.query(AthleteCoach).filter_by(
                coach_id=current_user.id, athlete_id=target_athlete_id
            ).first():
                raise HTTPException(status_code=403, detail="Athlete not under your supervision")
    else:
        target_athlete_id = current_user.id

    session = WorkoutSession(
        athlete_id=target_athlete_id,
        plan_id=session_data.plan_id,
        calendar_entry_id=session_data.calendar_entry_id,
        started_at=session_data.started_at or datetime.utcnow(),
        finished_at=session_data.finished_at,
        duration_seconds=session_data.duration_seconds,
        notes=session_data.notes,
    )
    db.add(session)
    db.flush()

    for rep in (session_data.reps or []):
        db.add(RepResult(
            session_id=session.id,
            exercise_id=rep.exercise_id,
            set_number=rep.set_number,
            rep_number=rep.rep_number,
            mean_velocity=rep.mean_velocity,
            peak_velocity=rep.peak_velocity,
            load_kg=rep.load_kg,
            power_watts=rep.power_watts,
            estimated_1rm=rep.estimated_1rm,
        ))

    db.commit()
    db.refresh(session)
    return WorkoutSessionResponse.model_validate(session)


@router.post("/live", response_model=WorkoutSessionResponse, status_code=201)
def start_live_session(
    data: StartLiveSessionRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Create an empty session at workout start for live tracking."""
    if current_user.role == "coach" and data.athlete_id and data.athlete_id != 0:
        target_athlete_id = data.athlete_id
        if target_athlete_id != current_user.id:
            if not db.query(AthleteCoach).filter_by(
                coach_id=current_user.id, athlete_id=target_athlete_id
            ).first():
                raise HTTPException(status_code=403, detail="Athlete not under your supervision")
    else:
        target_athlete_id = current_user.id

    session = WorkoutSession(
        athlete_id=target_athlete_id,
        plan_id=data.plan_id,
        started_at=data.started_at or datetime.utcnow(),
        notes=data.notes,
    )
    db.add(session)
    db.commit()
    db.refresh(session)
    return WorkoutSessionResponse.model_validate(session)


@router.post("/{session_id}/reps", response_model=WorkoutSessionResponse)
def append_reps(
    session_id: int,
    data: AppendRepsRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Append reps to an existing live session."""
    session = db.query(WorkoutSession).filter(WorkoutSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    check_session_access(session, current_user, db)

    for rep in data.reps:
        db.add(RepResult(
            session_id=session.id,
            exercise_id=rep.exercise_id,
            set_number=rep.set_number,
            rep_number=rep.rep_number,
            mean_velocity=rep.mean_velocity,
            peak_velocity=rep.peak_velocity,
            load_kg=rep.load_kg,
            power_watts=rep.power_watts,
            estimated_1rm=rep.estimated_1rm,
        ))

    if data.finished_at:
        session.finished_at = data.finished_at
        started = session.started_at
        finished = data.finished_at
        session.duration_seconds = int((finished - started).total_seconds())

    db.commit()
    db.refresh(session)
    return WorkoutSessionResponse.model_validate(session)


@router.delete("/{session_id}", status_code=204)
def delete_session(
    session_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Discard/delete a session (e.g., when user taps Odrzuć)."""
    session = db.query(WorkoutSession).filter(WorkoutSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    check_session_access(session, current_user, db)
    db.delete(session)
    db.commit()


@router.get("/{session_id}", response_model=WorkoutSessionResponse)
def get_session(
    session_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.query(WorkoutSession).filter(WorkoutSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    check_session_access(session, current_user, db)
    return WorkoutSessionResponse.model_validate(session)


@router.post("/{session_id}/reps/{rep_id}/velocity-trace", response_model=VelocityTraceResponse, status_code=201)
def save_velocity_trace(
    session_id: int,
    rep_id: int,
    trace: VelocityTraceCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    # Sprawdź czy sesja istnieje i należy do usera (lub user jest coach)
    session = db.query(WorkoutSession).filter(WorkoutSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    check_session_access(session, current_user, db)

    # Sprawdź czy rep istnieje
    rep = db.query(RepResult).filter(RepResult.id == rep_id, RepResult.session_id == session_id).first()
    if not rep:
        raise HTTPException(status_code=404, detail="Rep not found")

    # Zapisz lub zaktualizuj trace (upsert)
    existing = db.query(RepVelocityTrace).filter(RepVelocityTrace.rep_result_id == rep_id).first()
    points_json = json.dumps([{"timestamp_ms": p.timestamp_ms, "velocity_ms": p.velocity_ms} for p in trace.points])

    if existing:
        existing.points_json = points_json
        db_trace = existing
    else:
        db_trace = RepVelocityTrace(
            rep_result_id=rep_id,
            session_id=session_id,
            points_json=points_json
        )
        db.add(db_trace)

    db.commit()
    db.refresh(db_trace)

    # Zwróć response z deserializowanymi points
    points = [VelocityPoint(**p) for p in json.loads(db_trace.points_json)]
    return VelocityTraceResponse(
        id=db_trace.id,
        rep_result_id=db_trace.rep_result_id,
        session_id=db_trace.session_id,
        points=points,
        recorded_at=db_trace.recorded_at
    )


@router.get("/{session_id}/reps/{rep_id}/velocity-trace", response_model=VelocityTraceResponse)
def get_velocity_trace(
    session_id: int,
    rep_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.query(WorkoutSession).filter(WorkoutSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    check_session_access(session, current_user, db)

    trace = db.query(RepVelocityTrace).filter(
        RepVelocityTrace.rep_result_id == rep_id,
        RepVelocityTrace.session_id == session_id
    ).first()
    if not trace:
        raise HTTPException(status_code=404, detail="Velocity trace not found")

    points = [VelocityPoint(**p) for p in json.loads(trace.points_json)]
    return VelocityTraceResponse(
        id=trace.id,
        rep_result_id=trace.rep_result_id,
        session_id=trace.session_id,
        points=points,
        recorded_at=trace.recorded_at
    )
