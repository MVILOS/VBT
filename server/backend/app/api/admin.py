from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel, EmailStr

from app.db.database import get_db
from app.models import User
from app.schemas import UserResponse
from app.core.security import get_password_hash, get_current_user

router = APIRouter(prefix="/admin", tags=["admin"])


def require_admin(current_user: User = Depends(get_current_user)) -> User:
    if current_user.role != "admin":
        raise HTTPException(status_code=403, detail="Admin access required")
    return current_user


class AdminUserCreate(BaseModel):
    email: Optional[str] = None
    username: str
    password: str
    role: str = "athlete"
    coach_id: Optional[int] = None
    is_active: bool = True


class AdminUserUpdate(BaseModel):
    email: Optional[str] = None
    username: Optional[str] = None
    password: Optional[str] = None
    role: Optional[str] = None
    coach_id: Optional[int] = None
    is_active: Optional[bool] = None


@router.get("/users", response_model=List[UserResponse])
def list_all_users(
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    users = db.query(User).order_by(User.role, User.username).all()
    return [UserResponse.model_validate(u) for u in users]


@router.post("/users", response_model=UserResponse, status_code=201)
def create_user(
    body: AdminUserCreate,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    if body.role not in ("athlete", "coach", "admin"):
        raise HTTPException(status_code=400, detail="Invalid role")
    if body.email and db.query(User).filter(User.email == body.email).first():
        raise HTTPException(status_code=400, detail="Email already registered")
    if db.query(User).filter(User.username == body.username).first():
        raise HTTPException(status_code=400, detail="Username already taken")
    user = User(
        email=body.email or None,
        username=body.username,
        hashed_password=get_password_hash(body.password),
        role=body.role,
        coach_id=body.coach_id,
        is_active=body.is_active,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return UserResponse.model_validate(user)


@router.put("/users/{user_id}", response_model=UserResponse)
def update_user(
    user_id: int,
    body: AdminUserUpdate,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    if body.email is not None:
        if body.email and db.query(User).filter(User.email == body.email, User.id != user_id).first():
            raise HTTPException(status_code=400, detail="Email already taken")
        user.email = body.email or None
    if body.username is not None:
        if db.query(User).filter(User.username == body.username, User.id != user_id).first():
            raise HTTPException(status_code=400, detail="Username already taken")
        user.username = body.username
    if body.password is not None and body.password.strip():
        user.hashed_password = get_password_hash(body.password)
    if body.role is not None:
        if body.role not in ("athlete", "coach", "admin"):
            raise HTTPException(status_code=400, detail="Invalid role")
        user.role = body.role
    if body.coach_id is not None:
        user.coach_id = body.coach_id
    if body.is_active is not None:
        user.is_active = body.is_active
    db.commit()
    db.refresh(user)
    return UserResponse.model_validate(user)


@router.delete("/users/{user_id}", status_code=204)
def delete_user(
    user_id: int,
    admin: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    if user.id == admin.id:
        raise HTTPException(status_code=400, detail="Cannot delete your own account")
    db.delete(user)
    db.commit()


@router.post("/bootstrap", response_model=UserResponse, status_code=201)
def bootstrap_admin(
    body: AdminUserCreate,
    db: Session = Depends(get_db),
):
    """Create the first admin account — only works when no admins exist."""
    existing_admin = db.query(User).filter(User.role == "admin").first()
    if existing_admin:
        raise HTTPException(status_code=403, detail="Admin already exists. Use /admin/users to create more.")
    user = User(
        email=body.email,
        username=body.username,
        hashed_password=get_password_hash(body.password),
        role="admin",
        is_active=True,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return UserResponse.model_validate(user)
