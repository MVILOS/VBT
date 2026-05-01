from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from datetime import timedelta

from app.db.database import get_db
from app.models import User
from app.schemas import UserCreate, UserLogin, TokenResponse, UserResponse
from app.core.security import (
    get_password_hash,
    verify_password,
    create_access_token,
    ACCESS_TOKEN_EXPIRE_MINUTES,
    get_current_user,
)

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=TokenResponse)
def register(user_create: UserCreate, db: Session = Depends(get_db)):
    # Only athletes can self-register; coaches are created by admin
    if db.query(User).filter(User.username == user_create.username).first():
        raise HTTPException(status_code=400, detail="Nazwa użytkownika jest już zajęta")

    db_user = User(
        username=user_create.username,
        hashed_password=get_password_hash(user_create.password),
        role="athlete",
    )
    db.add(db_user)
    db.commit()
    db.refresh(db_user)

    token = create_access_token(
        data={"sub": db_user.username},
        expires_delta=timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES),
    )
    return {"access_token": token, "token_type": "bearer", "user": UserResponse.model_validate(db_user)}


@router.post("/login", response_model=TokenResponse)
def login(user_login: UserLogin, db: Session = Depends(get_db)):
    login_id = user_login.email or user_login.username
    user = (
        db.query(User).filter(User.email == login_id).first()
        or db.query(User).filter(User.username == login_id).first()
    )
    if not user or not verify_password(user_login.password, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account is inactive")

    token = create_access_token(
        data={"sub": user.username},
        expires_delta=timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES),
    )
    return {"access_token": token, "token_type": "bearer", "user": UserResponse.model_validate(user)}


@router.get("/me", response_model=UserResponse)
def me(current_user: User = Depends(get_current_user)):
    return UserResponse.model_validate(current_user)
