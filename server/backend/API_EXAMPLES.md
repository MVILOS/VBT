# VBT Platform API - Usage Examples

## Base URL
```
http://localhost:8000/api
```

## Authentication
All endpoints (except `/health`) require a Bearer token in the Authorization header:
```
Authorization: Bearer <your_jwt_token>
```

## 1. Authentication

### Register as Athlete
```bash
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "athlete@example.com",
    "username": "john_athlete",
    "password": "secure_password",
    "role": "athlete"
  }'
```

Response:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "bearer",
  "user": {
    "id": 1,
    "email": "athlete@example.com",
    "username": "john_athlete",
    "role": "athlete",
    "is_active": true,
    "created_at": "2026-04-13T10:00:00",
    "coach_id": null
  }
}
```

### Register as Coach
```bash
curl -X POST http://localhost:8000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "coach@example.com",
    "username": "mike_coach",
    "password": "coach_password",
    "role": "coach"
  }'
```

### Login
```bash
curl -X POST http://localhost:8000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_athlete",
    "password": "secure_password"
  }'
```

## 2. Manage Athletes (Coach Only)

### Create Athlete Account
```bash
curl -X POST http://localhost:8000/api/users/athletes \
  -H "Authorization: Bearer <coach_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "new_athlete@example.com",
    "username": "new_athlete",
    "password": "password123",
    "role": "athlete"
  }'
```

### List Your Athletes
```bash
curl -X GET http://localhost:8000/api/users/athletes \
  -H "Authorization: Bearer <coach_token>"
```

### Get Athlete Details
```bash
curl -X GET http://localhost:8000/api/users/athletes/2 \
  -H "Authorization: Bearer <coach_token>"
```

## 3. Exercises

### Create Exercise
```bash
curl -X POST http://localhost:8000/api/exercises \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Bench Press",
    "category": "compound",
    "mvt": 0.5,
    "description": "Upper body pressing movement"
  }'
```

### List All Exercises
```bash
curl -X GET http://localhost:8000/api/exercises \
  -H "Authorization: Bearer <token>"
```

## 4. Training Plans

### Create Training Plan with Exercises
```bash
curl -X POST http://localhost:8000/api/plans \
  -H "Authorization: Bearer <coach_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Strength Week 1",
    "description": "Focus on heavy compound movements",
    "is_template": false,
    "exercises": [
      {
        "exercise_id": 1,
        "order_index": 0,
        "notes": "Main lift",
        "sets": [
          {
            "set_number": 1,
            "reps": 5,
            "load_kg": 100.0,
            "target_velocity_min": 0.8,
            "target_velocity_max": 1.2,
            "rest_seconds": 300
          },
          {
            "set_number": 2,
            "reps": 5,
            "load_kg": 100.0,
            "rest_seconds": 300
          }
        ]
      }
    ]
  }'
```

### Get Full Plan with Exercises and Sets
```bash
curl -X GET http://localhost:8000/api/plans/1 \
  -H "Authorization: Bearer <token>"
```

### Assign Plan to Athlete (Coach)
```bash
curl -X POST http://localhost:8000/api/plans/1/assign/2 \
  -H "Authorization: Bearer <coach_token>"
```

## 5. Calendar

### Create Calendar Entry
```bash
curl -X POST http://localhost:8000/api/calendar \
  -H "Authorization: Bearer <coach_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "athlete_id": 2,
    "plan_id": 1,
    "date": "2026-04-15",
    "time_slot": "09:00",
    "title": "Strength Session",
    "notes": "Focus on compound movements"
  }'
```

### List Calendar Entries with Filters
```bash
curl -X GET "http://localhost:8000/api/calendar?athlete_id=2&date_start=2026-04-01&date_end=2026-04-30" \
  -H "Authorization: Bearer <coach_token>"
```

### Update Calendar Entry Status
```bash
curl -X PUT http://localhost:8000/api/calendar/1 \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "completed"
  }'
```

## 6. Workout Sessions and Rep Tracking

### Create Session with Rep Results
```bash
curl -X POST http://localhost:8000/api/sessions \
  -H "Authorization: Bearer <athlete_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "athlete_id": 1,
    "plan_id": 1,
    "calendar_entry_id": 1,
    "notes": "Great workout, felt strong",
    "reps": [
      {
        "exercise_id": 1,
        "set_number": 1,
        "rep_number": 1,
        "mean_velocity": 1.05,
        "peak_velocity": 1.25,
        "load_kg": 100.0,
        "power_watts": 1250,
        "estimated_1rm": 125.0
      },
      {
        "exercise_id": 1,
        "set_number": 1,
        "rep_number": 2,
        "mean_velocity": 0.98,
        "peak_velocity": 1.18,
        "load_kg": 100.0,
        "power_watts": 1200,
        "estimated_1rm": 122.0
      }
    ]
  }'
```

### List Your Sessions
```bash
curl -X GET http://localhost:8000/api/sessions \
  -H "Authorization: Bearer <athlete_token>"
```

### Coach Views Athlete Sessions
```bash
curl -X GET "http://localhost:8000/api/sessions?athlete_id=2" \
  -H "Authorization: Bearer <coach_token>"
```

### Get Detailed Session with All Reps
```bash
curl -X GET http://localhost:8000/api/sessions/1 \
  -H "Authorization: Bearer <token>"
```

## 7. Analytics

### Velocity Trend (Last 30 Days)
```bash
curl -X GET "http://localhost:8000/api/analytics/velocity-trend?athlete_id=1&exercise_id=1&days=30" \
  -H "Authorization: Bearer <token>"
```

Response:
```json
{
  "data_points": [
    {
      "date": "2026-04-10",
      "mean_velocity": 1.05,
      "peak_velocity": 1.25,
      "load_kg": 100.0,
      "exercise_name": "Bench Press",
      "rep_number": 1
    }
  ],
  "average_velocity": 1.02,
  "trend": "improving"
}
```

### 1RM Progress
```bash
curl -X GET "http://localhost:8000/api/analytics/1rm-progress?athlete_id=1&exercise_id=1" \
  -H "Authorization: Bearer <coach_token>"
```

Response:
```json
{
  "data_points": [
    {
      "date": "2026-04-01",
      "estimated_1rm": 120.0,
      "load_kg": 100.0
    }
  ],
  "latest_1rm": 125.0,
  "progress_percentage": 4.17
}
```

### Volume Analysis (30 Days)
```bash
curl -X GET "http://localhost:8000/api/analytics/volume?athlete_id=1&days=30" \
  -H "Authorization: Bearer <coach_token>"
```

Response:
```json
{
  "total_volume_kg": 15000.0,
  "average_volume_per_session": 2500.0,
  "session_count": 6,
  "period_days": 30
}
```

### Compare Multiple Athletes
```bash
curl -X GET "http://localhost:8000/api/analytics/compare-athletes?athlete_ids=1,2,3&exercise_id=1&days=30" \
  -H "Authorization: Bearer <coach_token>"
```

Response:
```json
{
  "athletes": [
    {
      "athlete_id": 1,
      "athlete_name": "john_athlete",
      "average_velocity": 1.02,
      "average_1rm": 125.0,
      "total_volume": 5000.0,
      "rep_count": 50
    }
  ],
  "top_performer": {
    "athlete_id": 1,
    "athlete_name": "john_athlete",
    "average_velocity": 1.02,
    "average_1rm": 125.0,
    "total_volume": 5000.0,
    "rep_count": 50
  }
}
```

## Error Responses

### Unauthorized (Missing or Invalid Token)
```json
{
  "detail": "Could not validate credentials"
}
```
Status: 401

### Forbidden (Insufficient Permissions)
```json
{
  "detail": "Only coaches can perform this action"
}
```
Status: 403

### Not Found
```json
{
  "detail": "Not found"
}
```
Status: 404

### Bad Request
```json
{
  "detail": "Email already registered"
}
```
Status: 400

## Health Check
```bash
curl -X GET http://localhost:8000/health
```

Response: `{"status":"ok"}`

## Auto-Generated API Docs
Visit `http://localhost:8000/docs` for interactive Swagger UI
Visit `http://localhost:8000/redoc` for ReDoc documentation
