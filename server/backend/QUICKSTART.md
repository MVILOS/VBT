# VBT Platform Backend - Quick Start Guide

## Prerequisites
- Python 3.11+
- PostgreSQL 12+ (or configure another database)
- pip/venv

## Installation

1. Create and activate virtual environment:
```bash
cd /home/diabolik_zotac/Documents/vbt/server/backend
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

2. Install dependencies:
```bash
pip install -r requirements.txt
```

3. Set up environment variables:
```bash
export SECRET_KEY="your-super-secret-key-change-this"
export DATABASE_URL="postgresql://user:password@localhost:5432/vbt_db"
export ACCESS_TOKEN_EXPIRE_MINUTES=30
```

4. Create PostgreSQL database:
```bash
createdb vbt_db
```

## Running the Server

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Server will start at: http://localhost:8000

## Quick API Test

### 1. Register an athlete
```bash
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "athlete@example.com",
    "username": "athlete1",
    "password": "password123",
    "role": "athlete"
  }'
```

Save the `access_token` from the response.

### 2. Use the token to call protected endpoints
```bash
curl -X GET http://localhost:8000/api/auth/me \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 3. View auto-generated API documentation
Open browser to: http://localhost:8000/docs

## Database Structure

The backend will automatically create all tables on first run thanks to:
```python
Base.metadata.create_all(bind=engine)
```

Tables created:
- users
- exercise_definitions
- training_plans
- plan_exercises
- plan_sets
- calendar_entries
- workout_sessions
- rep_results

## Docker Deployment

Build and run with Docker:
```bash
docker build -t vbt-backend .
docker run -p 8000:8000 \
  -e SECRET_KEY="your-secret-key" \
  -e DATABASE_URL="postgresql://vbt:password@db:5432/vbt_db" \
  vbt-backend
```

## Architecture Overview

```
FastAPI Application (app/main.py)
├── Authentication (app/api/auth.py)
│   └── Dependency: get_current_user_dependency (app/core/security.py)
├── Database Layer (app/db/database.py)
│   └── SQLAlchemy ORM Models (app/models/__init__.py)
├── API Routes
│   ├── Users (app/api/users.py) - Coach endpoints
│   ├── Exercises (app/api/exercises.py)
│   ├── Plans (app/api/plans.py)
│   ├── Calendar (app/api/calendar.py)
│   ├── Sessions (app/api/sessions.py)
│   └── Analytics (app/api/analytics.py)
└── Data Schemas (app/schemas/__init__.py) - Pydantic validation
```

## Key Features

### Role-Based Access
- **Coach**: Can create athletes, training plans, calendar entries, view all athlete data
- **Athlete**: Can view own data, create sessions, track workouts

### Nested Object Creation
Create a complete training plan in one request:
```json
{
  "name": "Week 1 Strength",
  "exercises": [
    {
      "exercise_id": 1,
      "sets": [
        {
          "set_number": 1,
          "reps": 5,
          "load_kg": 100.0,
          "rest_seconds": 300
        }
      ]
    }
  ]
}
```

### Real-Time Analytics
- Velocity trends (improving/declining detection)
- 1RM progress tracking
- Volume analysis
- Multi-athlete comparison

## Troubleshooting

### Database Connection Error
Check DATABASE_URL format:
```
postgresql://username:password@host:port/dbname
```

### Import Errors
Ensure you're in the correct directory and virtual environment is activated:
```bash
cd /home/diabolik_zotac/Documents/vbt/server/backend
source venv/bin/activate
```

### Port Already in Use
Run on a different port:
```bash
uvicorn app.main:app --port 8001
```

## Testing the API

See `/home/diabolik_zotac/Documents/vbt/server/backend/API_EXAMPLES.md` for complete examples of all endpoints.

## Production Deployment

1. Set a strong `SECRET_KEY` (use `openssl rand -hex 32`)
2. Use environment variables for all configuration
3. Set `--reload` to False
4. Use a production ASGI server (gunicorn, uvicorn with workers)
5. Enable HTTPS/TLS
6. Use a production database
7. Set up proper logging and monitoring

## API Documentation

Once running, visit:
- **Swagger UI**: http://localhost:8000/docs
- **ReDoc**: http://localhost:8000/redoc

Both are auto-generated from the code!

## File Structure

```
backend/
├── app/
│   ├── main.py              # FastAPI app
│   ├── models/__init__.py    # SQLAlchemy models
│   ├── schemas/__init__.py   # Pydantic schemas
│   ├── core/security.py      # JWT & auth
│   ├── db/database.py        # DB connection
│   └── api/
│       ├── auth.py           # Authentication
│       ├── users.py          # User management
│       ├── exercises.py      # Exercises
│       ├── plans.py          # Training plans
│       ├── calendar.py       # Calendar
│       ├── sessions.py       # Sessions
│       └── analytics.py      # Analytics
├── requirements.txt          # Python dependencies
├── Dockerfile               # Container config
└── API_EXAMPLES.md          # API usage examples
```

## Next Steps

1. Test all endpoints using the examples in `API_EXAMPLES.md`
2. Integrate with your frontend (Android app, web app, etc.)
3. Set up database backups
4. Configure monitoring and logging
5. Deploy to production infrastructure

Happy training!
