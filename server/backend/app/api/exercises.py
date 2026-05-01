from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List

from app.db.database import get_db
from app.models import User, ExerciseDefinition
from app.schemas import ExerciseCreate, ExerciseResponse
from app.core.security import get_current_user

router = APIRouter(prefix="/exercises", tags=["exercises"])


@router.get("", response_model=List[ExerciseResponse])
def list_exercises(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    return [ExerciseResponse.model_validate(e) for e in db.query(ExerciseDefinition).all()]


@router.post("", response_model=ExerciseResponse)
def create_exercise(
    exercise: ExerciseCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    db_ex = ExerciseDefinition(
        name=exercise.name,
        category=exercise.category,
        mvt=exercise.mvt,
        description=exercise.description,
        created_by=current_user.id,
    )
    db.add(db_ex)
    db.commit()
    db.refresh(db_ex)
    return ExerciseResponse.model_validate(db_ex)


@router.put("/{exercise_id}", response_model=ExerciseResponse)
def update_exercise(
    exercise_id: int,
    exercise: ExerciseCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    db_ex = db.query(ExerciseDefinition).filter(ExerciseDefinition.id == exercise_id).first()
    if not db_ex:
        raise HTTPException(status_code=404, detail="Exercise not found")
    db_ex.name = exercise.name
    db_ex.category = exercise.category
    db_ex.mvt = exercise.mvt
    db_ex.description = exercise.description
    db.commit()
    db.refresh(db_ex)
    return ExerciseResponse.model_validate(db_ex)


@router.delete("/{exercise_id}", status_code=204)
def delete_exercise(
    exercise_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    db_ex = db.query(ExerciseDefinition).filter(ExerciseDefinition.id == exercise_id).first()
    if not db_ex:
        raise HTTPException(status_code=404, detail="Exercise not found")
    db.delete(db_ex)
    db.commit()
