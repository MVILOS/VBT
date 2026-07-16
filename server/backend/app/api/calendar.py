from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from typing import List, Optional

from app.db.database import get_db
from app.models import User, CalendarEntry, AthleteCoach
from app.schemas import CalendarEntryCreate, CalendarEntryUpdate, CalendarEntryResponse
from app.core.security import get_current_user

router = APIRouter(prefix="/calendar", tags=["calendar"])


@router.get("", response_model=List[CalendarEntryResponse])
def list_calendar_entries(
    athlete_id: Optional[int] = Query(None),
    date_start: Optional[str] = Query(None),
    date_end: Optional[str] = Query(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.role == "coach":
        if athlete_id:
            # Trener widzi kalendarz tylko własny lub swoich zawodników
            # (relacja many-to-many athlete_coaches - zawodnik może mieć
            # kilku trenerów i każdy z nich ma tu dostęp).
            if athlete_id != current_user.id and not db.query(AthleteCoach).filter_by(
                coach_id=current_user.id, athlete_id=athlete_id
            ).first():
                raise HTTPException(status_code=403, detail="Athlete not under your supervision")
            q = db.query(CalendarEntry).filter(CalendarEntry.athlete_id == athlete_id)
        else:
            # Return entries for all athletes under this coach + coach's own
            athlete_ids = [
                ac.athlete_id for ac in db.query(AthleteCoach).filter(
                    AthleteCoach.coach_id == current_user.id
                ).all()
            ]
            athlete_ids.append(current_user.id)
            q = db.query(CalendarEntry).filter(CalendarEntry.athlete_id.in_(athlete_ids))
    else:
        q = db.query(CalendarEntry).filter(CalendarEntry.athlete_id == current_user.id)
    if date_start:
        q = q.filter(CalendarEntry.date >= date_start)
    if date_end:
        q = q.filter(CalendarEntry.date <= date_end)
    return [CalendarEntryResponse.model_validate(e) for e in q.order_by(CalendarEntry.date).all()]


@router.post("", response_model=CalendarEntryResponse)
def create_entry(
    entry: CalendarEntryCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    athlete_id = entry.athlete_id or current_user.id
    db_entry = CalendarEntry(
        athlete_id=athlete_id,
        plan_id=entry.plan_id,
        date=entry.date,
        time_slot=entry.time_slot,
        title=entry.title,
        notes=entry.notes,
        status="scheduled",
        created_by=current_user.id,
    )
    db.add(db_entry)
    db.commit()
    db.refresh(db_entry)
    return CalendarEntryResponse.model_validate(db_entry)


@router.put("/{entry_id}", response_model=CalendarEntryResponse)
def update_entry(
    entry_id: int,
    update: CalendarEntryUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    entry = db.query(CalendarEntry).filter(CalendarEntry.id == entry_id).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")
    if entry.athlete_id != current_user.id and entry.created_by != current_user.id:
        raise HTTPException(status_code=403, detail="Access denied")

    if update.title is not None:
        entry.title = update.title
    if update.notes is not None:
        entry.notes = update.notes
    if update.date is not None:
        entry.date = update.date
    if update.time_slot is not None:
        entry.time_slot = update.time_slot
    if update.status is not None:
        entry.status = update.status
    if update.plan_id is not None:
        entry.plan_id = update.plan_id
    if update.overrides_json is not None:
        entry.overrides_json = update.overrides_json

    db.commit()
    db.refresh(entry)
    return CalendarEntryResponse.model_validate(entry)


@router.delete("/{entry_id}", status_code=204)
def delete_entry(
    entry_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    entry = db.query(CalendarEntry).filter(CalendarEntry.id == entry_id).first()
    if not entry:
        raise HTTPException(status_code=404, detail="Entry not found")
    if entry.athlete_id != current_user.id and entry.created_by != current_user.id:
        raise HTTPException(status_code=403, detail="Access denied")
    db.delete(entry)
    db.commit()
