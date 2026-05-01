from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
from pydantic import BaseModel

from app.db.database import get_db
from app.models import User, AthleteCoach
from app.schemas import UserCreate, UserResponse, UserUpdate
from app.core.security import get_password_hash, get_current_user

router = APIRouter(prefix="/users", tags=["users"])


def require_coach(current_user: User) -> User:
    if current_user.role not in ("coach", "admin"):
        raise HTTPException(status_code=403, detail="Only coaches can perform this action")
    return current_user


def coach_has_athlete(coach_id: int, athlete_id: int, db: Session) -> bool:
    return db.query(AthleteCoach).filter_by(
        coach_id=coach_id, athlete_id=athlete_id
    ).first() is not None


class AssignByUsernameRequest(BaseModel):
    username: str


class SetCoachRequest(BaseModel):
    coach_id: int


@router.get("/athletes", response_model=List[UserResponse])
def list_athletes(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    require_coach(current_user)
    if current_user.role == "admin":
        athletes = db.query(User).filter(User.role.in_(["athlete", "coach"])).all()
    else:
        athletes = (
            db.query(User)
            .join(AthleteCoach, AthleteCoach.athlete_id == User.id)
            .filter(AthleteCoach.coach_id == current_user.id)
            .all()
        )
    return [UserResponse.model_validate(a) for a in athletes]


@router.post("/athletes", response_model=UserResponse)
def create_athlete(
    user_create: UserCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    require_coach(current_user)
    if user_create.email and db.query(User).filter(User.email == user_create.email).first():
        raise HTTPException(status_code=400, detail="Email already registered")
    if db.query(User).filter(User.username == user_create.username).first():
        raise HTTPException(status_code=400, detail="Username already taken")
    athlete = User(
        email=user_create.email or None,
        username=user_create.username,
        hashed_password=get_password_hash(user_create.password),
        role="athlete",
        coach_id=current_user.id,
    )
    db.add(athlete)
    db.flush()
    db.add(AthleteCoach(athlete_id=athlete.id, coach_id=current_user.id))
    db.commit()
    db.refresh(athlete)
    return UserResponse.model_validate(athlete)


@router.get("/athletes/{athlete_id}", response_model=UserResponse)
def get_athlete(
    athlete_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    require_coach(current_user)
    if current_user.role == "admin":
        athlete = db.query(User).filter(User.id == athlete_id).first()
    else:
        if not coach_has_athlete(current_user.id, athlete_id, db):
            raise HTTPException(status_code=404, detail="Athlete not found")
        athlete = db.query(User).filter(User.id == athlete_id).first()
    if not athlete:
        raise HTTPException(status_code=404, detail="Athlete not found")
    return UserResponse.model_validate(athlete)


@router.put("/athletes/{athlete_id}", response_model=UserResponse)
def update_athlete(
    athlete_id: int,
    update: UserUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    require_coach(current_user)
    if not coach_has_athlete(current_user.id, athlete_id, db) and current_user.role != "admin":
        raise HTTPException(status_code=404, detail="Athlete not found")
    athlete = db.query(User).filter(User.id == athlete_id).first()
    if not athlete:
        raise HTTPException(status_code=404, detail="Athlete not found")
    if update.username:
        athlete.username = update.username
    if update.password and update.password.strip():
        athlete.hashed_password = get_password_hash(update.password)
    if update.is_active is not None:
        athlete.is_active = update.is_active
    db.commit()
    db.refresh(athlete)
    return UserResponse.model_validate(athlete)


@router.post("/assign/{user_id}", response_model=UserResponse)
def assign_existing_user(
    user_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Przypisz istniejącego użytkownika (np. innego trenera) jako zawodnika."""
    require_coach(current_user)
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    if user.id == current_user.id:
        raise HTTPException(status_code=400, detail="Cannot assign yourself")
    # Dodaj do athlete_coaches (wiele trenerów)
    if not coach_has_athlete(current_user.id, user.id, db):
        db.add(AthleteCoach(athlete_id=user.id, coach_id=current_user.id))
        db.commit()
    db.refresh(user)
    return UserResponse.model_validate(user)


@router.post("/assign-by-username", response_model=UserResponse)
def assign_user_by_username(
    body: AssignByUsernameRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Przypisz użytkownika po nazwie użytkownika jako zawodnika u bieżącego trenera."""
    require_coach(current_user)
    username = body.username.strip()
    if not username:
        raise HTTPException(status_code=400, detail="Username is required")
    user = db.query(User).filter(User.username == username).first()
    if not user:
        raise HTTPException(status_code=404, detail=f"User '{username}' not found")
    if user.id == current_user.id:
        raise HTTPException(status_code=400, detail="Cannot assign yourself")
    if coach_has_athlete(current_user.id, user.id, db):
        raise HTTPException(status_code=400, detail="User is already your athlete")
    db.add(AthleteCoach(athlete_id=user.id, coach_id=current_user.id))
    db.commit()
    db.refresh(user)
    return UserResponse.model_validate(user)


@router.delete("/athletes/{athlete_id}/unassign", status_code=204)
def unassign_athlete(
    athlete_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Usuń zawodnika z listy (nie usuwa konta)."""
    require_coach(current_user)
    rel = db.query(AthleteCoach).filter_by(
        coach_id=current_user.id, athlete_id=athlete_id
    ).first()
    if not rel:
        raise HTTPException(status_code=404, detail="Athlete not found")
    db.delete(rel)
    db.commit()


@router.delete("/athletes/{athlete_id}", status_code=204)
def delete_athlete(
    athlete_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Usuń konto zawodnika (tylko jeśli jest pod tym trenerem)."""
    require_coach(current_user)
    if not coach_has_athlete(current_user.id, athlete_id, db) and current_user.role != "admin":
        raise HTTPException(status_code=404, detail="Athlete not found")
    athlete = db.query(User).filter(User.id == athlete_id).first()
    if not athlete:
        raise HTTPException(status_code=404, detail="Athlete not found")
    db.delete(athlete)
    db.commit()
