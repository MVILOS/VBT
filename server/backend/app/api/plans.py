from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List

from app.db.database import get_db
from app.models import User, TrainingPlan, PlanExercise, PlanSet, AthleteCoach
from app.schemas import TrainingPlanCreate, TrainingPlanUpdate, TrainingPlanResponse
from app.core.security import get_current_user

router = APIRouter(prefix="/plans", tags=["plans"])


@router.get("", response_model=List[TrainingPlanResponse])
def list_plans(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plans = (
        db.query(TrainingPlan)
        .filter(
            (TrainingPlan.owner_id == current_user.id) |
            (TrainingPlan.assigned_to == current_user.id)
        )
        .all()
    )
    return [TrainingPlanResponse.model_validate(p) for p in plans]


@router.post("", response_model=TrainingPlanResponse)
def create_plan(
    plan_data: TrainingPlanCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = TrainingPlan(
        name=plan_data.name,
        description=plan_data.description,
        owner_id=current_user.id,
        assigned_to=plan_data.assigned_to,
        is_template=plan_data.is_template,
    )
    db.add(plan)
    db.flush()

    for ex_data in (plan_data.exercises or []):
        plan_ex = PlanExercise(
            plan_id=plan.id,
            exercise_id=ex_data.exercise_id,
            order_index=ex_data.order_index,
            notes=ex_data.notes,
        )
        db.add(plan_ex)
        db.flush()
        for set_data in (ex_data.sets or []):
            db.add(PlanSet(
                plan_exercise_id=plan_ex.id,
                set_number=set_data.set_number,
                reps=set_data.reps,
                load_kg=set_data.load_kg,
                load_percent_1rm=set_data.load_percent_1rm,
                target_velocity_min=set_data.target_velocity_min,
                target_velocity_max=set_data.target_velocity_max,
                rest_seconds=set_data.rest_seconds,
            ))

    db.commit()
    db.refresh(plan)
    return TrainingPlanResponse.model_validate(plan)


@router.get("/{plan_id}", response_model=TrainingPlanResponse)
def get_plan(
    plan_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = db.query(TrainingPlan).filter(TrainingPlan.id == plan_id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    if plan.owner_id != current_user.id and plan.assigned_to != current_user.id:
        raise HTTPException(status_code=403, detail="Access denied")
    return TrainingPlanResponse.model_validate(plan)


@router.put("/{plan_id}", response_model=TrainingPlanResponse)
def update_plan(
    plan_id: int,
    plan_data: TrainingPlanUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = db.query(TrainingPlan).filter(TrainingPlan.id == plan_id, TrainingPlan.owner_id == current_user.id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")

    if plan_data.name is not None:
        plan.name = plan_data.name
    if plan_data.description is not None:
        plan.description = plan_data.description
    if plan_data.assigned_to is not None:
        plan.assigned_to = plan_data.assigned_to
    if plan_data.is_template is not None:
        plan.is_template = plan_data.is_template

    if plan_data.exercises is not None:
        # Replace all exercises
        for ex in plan.exercises:
            db.delete(ex)
        db.flush()
        for ex_data in plan_data.exercises:
            plan_ex = PlanExercise(
                plan_id=plan.id,
                exercise_id=ex_data.exercise_id,
                order_index=ex_data.order_index,
                notes=ex_data.notes,
            )
            db.add(plan_ex)
            db.flush()
            for set_data in (ex_data.sets or []):
                db.add(PlanSet(
                    plan_exercise_id=plan_ex.id,
                    set_number=set_data.set_number,
                    reps=set_data.reps,
                    load_kg=set_data.load_kg,
                    load_percent_1rm=set_data.load_percent_1rm,
                    target_velocity_min=set_data.target_velocity_min,
                    target_velocity_max=set_data.target_velocity_max,
                    rest_seconds=set_data.rest_seconds,
                ))

    db.commit()
    db.refresh(plan)
    return TrainingPlanResponse.model_validate(plan)


@router.delete("/{plan_id}", status_code=204)
def delete_plan(
    plan_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    plan = db.query(TrainingPlan).filter(TrainingPlan.id == plan_id, TrainingPlan.owner_id == current_user.id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    db.delete(plan)
    db.commit()


@router.post("/{plan_id}/assign/{athlete_id}", response_model=TrainingPlanResponse)
def assign_plan(
    plan_id: int,
    athlete_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.role != "coach":
        raise HTTPException(status_code=403, detail="Only coaches can assign plans")
    plan = db.query(TrainingPlan).filter(TrainingPlan.id == plan_id, TrainingPlan.owner_id == current_user.id).first()
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    has_athlete = db.query(AthleteCoach).filter_by(
        coach_id=current_user.id, athlete_id=athlete_id
    ).first()
    if not has_athlete:
        raise HTTPException(status_code=404, detail="Athlete not found")
    plan.assigned_to = athlete_id
    db.commit()
    db.refresh(plan)
    return TrainingPlanResponse.model_validate(plan)
