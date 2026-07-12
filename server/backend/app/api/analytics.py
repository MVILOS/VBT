from collections import defaultdict

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime, timedelta

from app.db.database import get_db
from app.models import User, RepResult, WorkoutSession, ExerciseDefinition, AthleteCoach
from app.core.security import get_current_user

router = APIRouter(prefix="/analytics", tags=["analytics"])


def resolve_athlete_id(
    athlete_id: Optional[int],
    current_user: User,
    db=None,
) -> int:
    """
    Resolves which athlete's data to query.
    - None → always returns current_user.id (coach can also be an athlete)
    - Given id + admin → any user allowed
    - Given id + coach → allowed only if athlete is under this coach OR is the coach themselves
    - Given id + athlete → only own id allowed
    """
    if athlete_id is None:
        return current_user.id

    if current_user.role == "admin":
        return athlete_id

    if current_user.role == "coach":
        if athlete_id == current_user.id:
            return athlete_id
        if db is not None:
            if not db.query(AthleteCoach).filter_by(
                coach_id=current_user.id, athlete_id=athlete_id
            ).first():
                raise HTTPException(status_code=403, detail="Athlete not under your supervision")
        return athlete_id

    # athlete role
    if athlete_id != current_user.id:
        raise HTTPException(status_code=403, detail="Access denied")
    return athlete_id


def check_session_access(session: WorkoutSession, current_user: User, db: Session) -> None:
    """Dostęp: właściciel sesji, admin, lub trener faktycznie nadzorujący tego zawodnika."""
    if session.athlete_id == current_user.id or current_user.role == "admin":
        return
    if current_user.role == "coach" and db.query(AthleteCoach).filter_by(
        coach_id=current_user.id, athlete_id=session.athlete_id
    ).first():
        return
    raise HTTPException(status_code=403, detail="Access denied")


@router.get("/velocity-trend")
def velocity_trend(
    athlete_id: Optional[int] = Query(None),
    exercise_id: Optional[int] = Query(None),
    days: int = Query(30),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    target_id = resolve_athlete_id(athlete_id, current_user, db)
    since = datetime.utcnow() - timedelta(days=days)

    q = (
        db.query(RepResult, WorkoutSession, ExerciseDefinition)
        .join(WorkoutSession, RepResult.session_id == WorkoutSession.id)
        .join(ExerciseDefinition, RepResult.exercise_id == ExerciseDefinition.id)
        .filter(WorkoutSession.athlete_id == target_id)
        .filter(WorkoutSession.started_at >= since)
    )
    if exercise_id:
        q = q.filter(RepResult.exercise_id == exercise_id)

    results = q.order_by(WorkoutSession.started_at).all()
    return [
        {
            "date": str(ws.started_at.date()),
            "mean_velocity": rr.mean_velocity,
            "load_kg": rr.load_kg,
            "exercise_name": ex.name,
            "power_watts": rr.power_watts,
        }
        for rr, ws, ex in results
    ]


@router.get("/1rm-progress")
def one_rm_progress(
    athlete_id: Optional[int] = Query(None),
    exercise_id: int = Query(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    target_id = resolve_athlete_id(athlete_id, current_user, db)

    results = (
        db.query(RepResult, WorkoutSession)
        .join(WorkoutSession, RepResult.session_id == WorkoutSession.id)
        .filter(WorkoutSession.athlete_id == target_id)
        .filter(RepResult.exercise_id == exercise_id)
        .filter(RepResult.estimated_1rm.isnot(None))
        .order_by(WorkoutSession.started_at)
        .all()
    )
    return [
        {
            "date": str(ws.started_at.date()),
            "estimated_1rm": rr.estimated_1rm,
            "load_kg": rr.load_kg,
        }
        for rr, ws in results
    ]


@router.get("/volume")
def volume(
    athlete_id: Optional[int] = Query(None),
    days: int = Query(30),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    target_id = resolve_athlete_id(athlete_id, current_user, db)
    since = datetime.utcnow() - timedelta(days=days)

    results = (
        db.query(RepResult, WorkoutSession)
        .join(WorkoutSession, RepResult.session_id == WorkoutSession.id)
        .filter(WorkoutSession.athlete_id == target_id)
        .filter(WorkoutSession.started_at >= since)
        .all()
    )

    # Group by week
    weeks: dict = {}
    for rr, ws in results:
        week = ws.started_at.strftime("%Y-W%W")
        if week not in weeks:
            weeks[week] = {"week": week, "total_reps": 0, "total_volume_kg": 0.0}
        weeks[week]["total_reps"] += 1
        weeks[week]["total_volume_kg"] += rr.load_kg

    return sorted(weeks.values(), key=lambda x: x["week"])


@router.get("/compare-athletes")
def compare_athletes(
    athlete_ids: str = Query(..., description="Comma-separated athlete IDs e.g. 1,2,3"),
    exercise_id: int = Query(...),
    days: int = Query(30),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.role != "coach":
        raise HTTPException(status_code=403, detail="Only coaches can compare athletes")

    ids = [int(i) for i in athlete_ids.split(",") if i.strip().isdigit()]
    since = datetime.utcnow() - timedelta(days=days)

    result: dict = {}
    for aid in ids:
        rows = (
            db.query(RepResult, WorkoutSession)
            .join(WorkoutSession, RepResult.session_id == WorkoutSession.id)
            .filter(WorkoutSession.athlete_id == aid)
            .filter(RepResult.exercise_id == exercise_id)
            .filter(WorkoutSession.started_at >= since)
            .order_by(WorkoutSession.started_at)
            .all()
        )
        result[str(aid)] = [
            {"date": str(ws.started_at.date()), "mean_velocity": rr.mean_velocity}
            for rr, ws in rows
        ]
    return result


@router.get("/session-detail")
def session_detail(
    session_id: int = Query(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Get all reps for a session grouped by exercise and set."""
    session = db.query(WorkoutSession).filter(WorkoutSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    check_session_access(session, current_user, db)

    reps = (
        db.query(RepResult, ExerciseDefinition)
        .join(ExerciseDefinition, RepResult.exercise_id == ExerciseDefinition.id)
        .filter(RepResult.session_id == session_id)
        .order_by(RepResult.exercise_id, RepResult.set_number, RepResult.rep_number)
        .all()
    )

    result = []
    for rr, ex in reps:
        result.append({
            "rep_id": rr.id,
            "exercise_id": rr.exercise_id,
            "exercise_name": ex.name,
            "set_number": rr.set_number,
            "rep_number": rr.rep_number,
            "mean_velocity": rr.mean_velocity,
            "peak_velocity": rr.peak_velocity,
            "load_kg": rr.load_kg,
            "power_watts": rr.power_watts,
            "label": f"S{rr.set_number}R{rr.rep_number}",
        })
    return result


@router.get("/sessions-list")
def sessions_list(
    athlete_id: Optional[int] = Query(None),
    exercise_id: Optional[int] = Query(None),
    days: int = Query(90),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """List sessions containing a specific exercise for selection."""
    target_id = resolve_athlete_id(athlete_id, current_user, db)
    since = datetime.utcnow() - timedelta(days=days)

    q = db.query(WorkoutSession).filter(
        WorkoutSession.athlete_id == target_id,
        WorkoutSession.started_at >= since,
    )
    sessions = q.order_by(WorkoutSession.started_at.desc()).all()

    result = []
    for s in sessions:
        # Filter to sessions that have reps for requested exercise
        if exercise_id:
            has_ex = db.query(RepResult).filter(
                RepResult.session_id == s.id,
                RepResult.exercise_id == exercise_id
            ).first()
            if not has_ex:
                continue
        result.append({
            "id": s.id,
            "date": str(s.started_at.date()),
            "started_at": s.started_at.isoformat(),
            "notes": s.notes,
        })
    return result


@router.get("/export-csv")
def export_csv(
    athlete_id: Optional[int] = Query(None),
    date_from: Optional[str] = Query(None),
    date_to: Optional[str] = Query(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Export rep data as CSV."""
    import csv
    import io
    from fastapi.responses import StreamingResponse

    target_id = resolve_athlete_id(athlete_id, current_user, db)

    q = (
        db.query(RepResult, WorkoutSession, ExerciseDefinition)
        .join(WorkoutSession, RepResult.session_id == WorkoutSession.id)
        .join(ExerciseDefinition, RepResult.exercise_id == ExerciseDefinition.id)
        .filter(WorkoutSession.athlete_id == target_id)
    )
    if date_from:
        q = q.filter(WorkoutSession.started_at >= datetime.fromisoformat(date_from))
    if date_to:
        q = q.filter(WorkoutSession.started_at <= datetime.fromisoformat(date_to + "T23:59:59"))

    rows = q.order_by(WorkoutSession.started_at, RepResult.set_number, RepResult.rep_number).all()

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow(["date", "session_id", "exercise", "set", "rep", "mean_velocity_ms", "peak_velocity_ms", "load_kg", "power_watts", "estimated_1rm"])
    for rr, ws, ex in rows:
        writer.writerow([
            ws.started_at.date(), ws.id, ex.name,
            rr.set_number, rr.rep_number,
            round(rr.mean_velocity, 3), round(rr.peak_velocity, 3),
            rr.load_kg, rr.power_watts, rr.estimated_1rm
        ])

    output.seek(0)
    return StreamingResponse(
        iter([output.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename=vbt_export_{target_id}.csv"}
    )


# ---------------------------------------------------------------------------
# FATIGUE INDEX — zmęczenie wewnątrztreningowe (per-set velocity drop)
# ---------------------------------------------------------------------------
@router.get("/fatigue-index")
def fatigue_index(
    session_id: int = Query(..., description="ID sesji treningowej"),
    athlete_id: Optional[int] = Query(None),
    exercise_id: Optional[int] = Query(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Indeks zmęczenia wewnątrztreningowego: spadek prędkości od pierwszej do ostatniej serii.
    Fatigue Index (FI%) = (1 - V_last / V_first) × 100
    FI < 5%  → bardzo niskie zmęczenie
    FI 5-10% → umiarkowane
    FI 10-20%→ wysokie (typowe dla akumulacji)
    FI > 20% → przekroczony próg — rozważ zatrzymanie
    """
    session = db.query(WorkoutSession).filter(WorkoutSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Session not found")
    check_session_access(session, current_user, db)

    q = (
        db.query(RepResult, ExerciseDefinition)
        .join(ExerciseDefinition, RepResult.exercise_id == ExerciseDefinition.id)
        .filter(RepResult.session_id == session_id)
        .order_by(RepResult.exercise_id, RepResult.set_number, RepResult.rep_number)
    )
    if exercise_id:
        q = q.filter(RepResult.exercise_id == exercise_id)

    results = q.all()

    # Grupuj wg ćwiczenia
    ex_groups: dict = defaultdict(list)
    for rr, ex in results:
        ex_groups[(ex.id, ex.name)].append(rr)

    output = []
    for (ex_id, ex_name), reps in ex_groups.items():
        # Grupuj po serii
        set_groups: dict = defaultdict(list)
        for r in reps:
            set_groups[r.set_number].append(r)

        sets_data = []
        for set_num in sorted(set_groups.keys()):
            set_reps = set_groups[set_num]
            avg_v = sum(r.mean_velocity for r in set_reps) / len(set_reps)
            max_v = max(r.peak_velocity for r in set_reps)
            power_vals = [r.power_watts for r in set_reps if r.power_watts is not None]
            avg_power = round(sum(power_vals) / len(power_vals), 1) if power_vals else None
            sets_data.append({
                "set_number": set_num,
                "reps": len(set_reps),
                "mean_velocity": round(avg_v, 3),
                "peak_velocity": round(max_v, 3),
                "load_kg": set_reps[0].load_kg,
                "mean_power_watts": avg_power,
            })

        if len(sets_data) >= 2:
            v_first = sets_data[0]["mean_velocity"]
            v_last = sets_data[-1]["mean_velocity"]
            fi = round((1 - v_last / v_first) * 100, 1) if v_first > 0 else 0.0
            v_drop = round(v_first - v_last, 3)
            best_set = max(sets_data, key=lambda s: s["mean_velocity"])["set_number"]
        elif sets_data:
            fi, v_drop, best_set = 0.0, 0.0, sets_data[0]["set_number"]
        else:
            continue

        output.append({
            "exercise_id": ex_id,
            "exercise_name": ex_name,
            "sets": sets_data,
            "fatigue_index_pct": fi,
            "velocity_drop_ms": v_drop,
            "best_set": best_set,
            "readiness_zone": (
                "optimal" if fi < 5 else
                "moderate" if fi < 10 else
                "high" if fi < 20 else
                "overreached"
            ),
        })

    return output


# ---------------------------------------------------------------------------
# WEEKLY LOAD — prędkość i objętość per dzień treningowy
# ---------------------------------------------------------------------------
@router.get("/weekly-load")
def weekly_load(
    athlete_id: Optional[int] = Query(None),
    weeks: int = Query(6, ge=1, le=26),
    exercise_id: Optional[int] = Query(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Prędkość i objętość na każdy dzień treningowy, pogrupowane tygodniami.
    Pozwala obserwować zmęczenie nagromadzone w ciągu tygodnia.
    """
    target_id = resolve_athlete_id(athlete_id, current_user, db)
    since = datetime.utcnow() - timedelta(weeks=weeks)

    q = (
        db.query(RepResult, WorkoutSession, ExerciseDefinition)
        .join(WorkoutSession, RepResult.session_id == WorkoutSession.id)
        .join(ExerciseDefinition, RepResult.exercise_id == ExerciseDefinition.id)
        .filter(WorkoutSession.athlete_id == target_id)
        .filter(WorkoutSession.started_at >= since)
    )
    if exercise_id:
        q = q.filter(RepResult.exercise_id == exercise_id)

    results = q.order_by(WorkoutSession.started_at).all()

    # week → day → list[RepResult]
    data: dict = defaultdict(lambda: defaultdict(list))
    session_meta: dict = defaultdict(lambda: defaultdict(set))  # week→day→session_ids

    for rr, ws, ex in results:
        week_key = ws.started_at.strftime("%Y-W%V")
        day_key = str(ws.started_at.date())
        data[week_key][day_key].append(rr)
        session_meta[week_key][day_key].add(ws.id)

    output = []
    for week in sorted(data.keys()):
        days_list = []
        for day in sorted(data[week].keys()):
            reps = data[week][day]
            avg_v = sum(r.mean_velocity for r in reps) / len(reps)
            total_vol = sum(r.load_kg for r in reps)
            power_vals = [r.power_watts for r in reps if r.power_watts is not None]
            avg_power = round(sum(power_vals) / len(power_vals), 1) if power_vals else None
            days_list.append({
                "date": day,
                "sessions": len(session_meta[week][day]),
                "total_reps": len(reps),
                "mean_velocity": round(avg_v, 3),
                "total_volume_kg": round(total_vol, 1),
                "mean_power_watts": avg_power,
            })

        week_reps = sum(d["total_reps"] for d in days_list)
        week_mean_v = (
            round(sum(d["mean_velocity"] for d in days_list) / len(days_list), 3)
            if days_list else 0.0
        )
        week_fatigue = (
            round((1 - days_list[-1]["mean_velocity"] / days_list[0]["mean_velocity"]) * 100, 1)
            if len(days_list) >= 2 and days_list[0]["mean_velocity"] > 0 else 0.0
        )
        power_days = [d["mean_power_watts"] for d in days_list if d.get("mean_power_watts") is not None]
        week_mean_power = round(sum(power_days) / len(power_days), 1) if power_days else None

        output.append({
            "week": week,
            "training_days": len(days_list),
            "week_mean_velocity": week_mean_v,
            "week_mean_power_watts": week_mean_power,
            "week_total_reps": week_reps,
            "week_total_volume_kg": round(sum(d["total_volume_kg"] for d in days_list), 1),
            "weekly_fatigue_pct": week_fatigue,
            "days": days_list,
        })

    return output


# ---------------------------------------------------------------------------
# WEEK COMPARISON — porównanie tygodni cyklu dla danego ćwiczenia
# ---------------------------------------------------------------------------
@router.get("/week-comparison")
def week_comparison(
    exercise_id: int = Query(...),
    athlete_id: Optional[int] = Query(None),
    weeks: int = Query(8, ge=2, le=52),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """
    Porównanie tygodniowe wydajności dla konkretnego ćwiczenia.
    Pozwala zobaczyć jak zmęczenie i adaptacja nakładają się w cyklu treningowym.
    Wysoka prędkość + duże obciążenie = dobra adaptacja.
    Niska prędkość + rosnąca objętość = akumulacja zmęczenia.
    """
    target_id = resolve_athlete_id(athlete_id, current_user, db)
    since = datetime.utcnow() - timedelta(weeks=weeks)

    results = (
        db.query(RepResult, WorkoutSession)
        .join(WorkoutSession, RepResult.session_id == WorkoutSession.id)
        .filter(WorkoutSession.athlete_id == target_id)
        .filter(RepResult.exercise_id == exercise_id)
        .filter(WorkoutSession.started_at >= since)
        .order_by(WorkoutSession.started_at)
        .all()
    )

    weeks_data: dict = defaultdict(list)
    weeks_sessions: dict = defaultdict(set)
    for rr, ws in results:
        week = ws.started_at.strftime("%Y-W%V")
        weeks_data[week].append(rr)
        weeks_sessions[week].add(ws.id)

    output = []
    for week in sorted(weeks_data.keys()):
        reps = weeks_data[week]
        velocities = [r.mean_velocity for r in reps]
        peak_velocities = [r.peak_velocity for r in reps]
        loads = [r.load_kg for r in reps]
        orm_values = [r.estimated_1rm for r in reps if r.estimated_1rm]
        power_vals = [r.power_watts for r in reps if r.power_watts is not None]
        mean_power = round(sum(power_vals) / len(power_vals), 1) if power_vals else None

        output.append({
            "week": week,
            "sessions": len(weeks_sessions[week]),
            "total_reps": len(reps),
            "mean_velocity": round(sum(velocities) / len(velocities), 3),
            "max_velocity": round(max(velocities), 3),
            "mean_peak_velocity": round(sum(peak_velocities) / len(peak_velocities), 3),
            "mean_load_kg": round(sum(loads) / len(loads), 1),
            "max_load_kg": round(max(loads), 1),
            "total_volume_kg": round(sum(loads), 1),
            "best_estimated_1rm": round(max(orm_values), 1) if orm_values else None,
            "mean_power_watts": mean_power,
        })

    return output
