import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from app.db.database import engine
from app.models import Base
from app.api import auth, users, exercises, plans, calendar, sessions, analytics, dashboard, admin

Base.metadata.create_all(bind=engine)

# ---------------------------------------------------------------------------
# Exercises: Polish (English) names, categories, MVT values
# Sources: Jovanović & Flanagan (2014), Mann (2016), Vitruve VBT guide
# ---------------------------------------------------------------------------
EXERCISES = [
    # --- RUCHY OLIMPIJSKIE (Olympic) ---
    # Main lifts
    ("Rwanie (Snatch)",                         "olympic",   0.80, "Klasyczne rwanie - ćwiczenie dwubojowe. VBT: peak velocity >1.0 m/s przy submaksymalnych ciężarach."),
    ("Zarzut (Clean)",                           "olympic",   0.75, "Zarzut - pierwsza część podrzutu. Kluczowy ruch dwubojowy."),
    ("Wyrwanie (Jerk)",                          "olympic",   0.65, "Wyrwanie - druga część podrzutu. Mierz peak velocity sztangi."),
    ("Wyrwanie z rozkroku (Split Jerk)",         "olympic",   0.65, "Wyrwanie z rozkrokiem nóg - wariant wyrwania."),
    ("Szturm (Push Jerk)",                       "olympic",   0.60, "Szturm - wyrwanie bez rozkroku, z ugięciem kolan."),
    ("Podrzut - Zarzut i Wyrwanie (Clean & Jerk)", "olympic", 0.75, "Pełny podrzut - kompleksowe ćwiczenie olimpijskie."),
    # Snatch derivatives
    ("Rwanie siłowe (Power Snatch)",             "olympic",   1.00, "Rwanie siłowe - chwyt ponad równoległą. Wyższe prędkości niż klasyczne rwanie."),
    ("Rwanie z wieszania (Hang Snatch)",         "olympic",   0.80, "Rwanie z pozycji wieszania (powyżej/poniżej kolan)."),
    ("Rwanie z klocków (Block Snatch)",          "olympic",   0.80, "Rwanie z klocków - skrócony zakres ruchu."),
    ("Szarpanie do rwania (Snatch Pull)",        "olympic",   0.50, "Szarpanie do rwania bez wejścia pod sztangę. Ćwiczenie siłowe dla rwania."),
    ("Szarpanie do rwania z wieszania (Hang Snatch Pull)", "olympic", 0.50, "Szarpanie do rwania z pozycji wieszania."),
    # Clean derivatives
    ("Zarzut siłowy (Power Clean)",              "olympic",   0.85, "Zarzut siłowy - chwyt ponad równoległą. Wysoka prędkość paska."),
    ("Zarzut z wieszania (Hang Clean)",          "olympic",   0.75, "Zarzut z pozycji wieszania."),
    ("Zarzut z klocków (Block Clean)",           "olympic",   0.75, "Zarzut z klocków - skrócony zakres ruchu."),
    ("Szarpanie do zarzutu (Clean Pull)",        "olympic",   0.45, "Szarpanie do zarzutu bez wejścia pod sztangę."),
    ("Szarpanie do zarzutu z wieszania (Hang Clean Pull)", "olympic", 0.45, "Szarpanie do zarzutu z pozycji wieszania."),
    # --- SIŁOWE (Strength) ---
    ("Przysiad (Back Squat)",                    "strength",  0.30, "Przysiad ze sztangą z tyłu. MVT 0.30 m/s przy 1RM (Pareja-Blanco 2017)."),
    ("Przysiad przedni (Front Squat)",           "strength",  0.32, "Przysiad przedni - kluczowe ćwiczenie pomocnicze w dwuboju."),
    ("Martwy ciąg (Deadlift)",                   "strength",  0.15, "Martwy ciąg. Niska MVT (~0.15 m/s) - monitoring zatrzymania technicznego."),
    ("Martwy ciąg rumuński (Romanian Deadlift)", "strength",  0.15, "RDL - wzmocnienie łańcucha tylnego dla ciągów olimpijskich."),
    ("Wyciskanie leżąc (Bench Press)",           "strength",  0.17, "Wyciskanie leżąc. MVT 0.17 m/s (García-Ramos 2018)."),
    ("Wyciskanie żołnierskie (Overhead Press)",  "strength",  0.22, "Press nad głową stojąco. Ważne dla fazy wyrwania."),
    ("Szturm ze sztangą (Push Press)",           "strength",  0.50, "Push press - ballistyczne wyciskanie z impulsem nóg."),
    # --- BALISTYCZNE (Ballistic) ---
    ("Przysiad skoczny (Jump Squat)",            "ballistic", 1.00, "Przysiad skoczny z obciążeniem. MVT >1.0 m/s. Monitoring mocy eksplozywnej."),
    # --- POMOCNICZE (Auxiliary) ---
    ("Wiosłowanie sztangą (Barbell Row)",        "auxiliary", 0.25, "Wiosłowanie poziome - antagonista do wyciskań i ciągów."),
    ("Dobre rano (Good Morning)",                "auxiliary", 0.20, "Good Morning - wzmocnienie grzbietu i łańcucha tylnego."),
]


def run_migrations():
    with engine.connect() as conn:
        # --- athlete_coaches: many-to-many trener↔zawodnik ---
        try:
            conn.execute(text("""
                CREATE TABLE IF NOT EXISTS athlete_coaches (
                    athlete_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
                    coach_id   INTEGER REFERENCES users(id) ON DELETE CASCADE,
                    created_at TIMESTAMP DEFAULT NOW(),
                    PRIMARY KEY (athlete_id, coach_id)
                )
            """))
            conn.commit()
        except Exception:
            conn.rollback()

        # Migruj istniejące relacje coach_id → athlete_coaches
        try:
            conn.execute(text("""
                INSERT INTO athlete_coaches (athlete_id, coach_id)
                SELECT id, coach_id FROM users
                WHERE coach_id IS NOT NULL
                ON CONFLICT DO NOTHING
            """))
            conn.commit()
        except Exception:
            conn.rollback()

        # --- Stara migracja kolumny ---
        try:
            conn.execute(text("ALTER TABLE calendar_entries ADD COLUMN IF NOT EXISTS overrides_json TEXT"))
            conn.commit()
        except Exception:
            conn.rollback()

        # --- Email nullable (rejestracja bez emaila) ---
        try:
            conn.execute(text("ALTER TABLE users ALTER COLUMN email DROP NOT NULL"))
            conn.commit()
        except Exception:
            conn.rollback()

        # --- Popraw literówki ---
        for old, new in [("Snach", "Snatch"), ("Power Snach", "Power Snatch")]:
            try:
                conn.execute(text("UPDATE exercise_definitions SET name = :new WHERE name = :old"), {"new": new, "old": old})
                conn.commit()
            except Exception:
                conn.rollback()

        # --- Usuń duplikaty (tylko niereferncjonowane) ---
        try:
            conn.execute(text("""
                DELETE FROM exercise_definitions
                WHERE id NOT IN (SELECT MIN(id) FROM exercise_definitions GROUP BY LOWER(name))
                  AND id NOT IN (SELECT exercise_id FROM rep_results)
                  AND id NOT IN (SELECT exercise_id FROM plan_exercises)
            """))
            conn.commit()
        except Exception:
            conn.rollback()

        # --- Zmień nazwy istniejących na format: Polska (English) ---
        renames = {
            "Squat":                    "Przysiad (Back Squat)",
            "Back Squat":               "Przysiad (Back Squat)",
            "Deadlift":                 "Martwy ciąg (Deadlift)",
            "Bench Press":              "Wyciskanie leżąc (Bench Press)",
            "Romanian Deadlift":        "Martwy ciąg rumuński (Romanian Deadlift)",
            "Power Clean":              "Zarzut siłowy (Power Clean)",
            "Snatch":                   "Rwanie (Snatch)",
            "Power Snatch":             "Rwanie siłowe (Power Snatch)",
        }
        for old_name, new_name in renames.items():
            try:
                conn.execute(text("""
                    UPDATE exercise_definitions
                    SET name = :new_name
                    WHERE LOWER(name) = LOWER(:old_name)
                      AND LOWER(name) != LOWER(:new_name)
                """), {"old_name": old_name, "new_name": new_name})
                conn.commit()
            except Exception:
                conn.rollback()

        # --- Zaktualizuj MVT i category dla istniejących ---
        mvt_updates = [
            ("Przysiad (Back Squat)",                   "strength",  0.30),
            ("Rwanie (Snatch)",                         "olympic",   0.80),
            ("Zarzut siłowy (Power Clean)",             "olympic",   0.85),
            ("Rwanie siłowe (Power Snatch)",            "olympic",   1.00),
            ("Wyciskanie leżąc (Bench Press)",          "strength",  0.17),
            ("Martwy ciąg (Deadlift)",                  "strength",  0.15),
            ("Martwy ciąg rumuński (Romanian Deadlift)","strength",  0.15),
        ]
        for name, cat, mvt in mvt_updates:
            try:
                conn.execute(text("""
                    UPDATE exercise_definitions
                    SET category = :cat, mvt = :mvt
                    WHERE LOWER(name) = LOWER(:name)
                """), {"name": name, "cat": cat, "mvt": mvt})
                conn.commit()
            except Exception:
                conn.rollback()

        # --- Dodaj nowe ćwiczenia (pomijaj jeśli już istnieją) ---
        for name, category, mvt, description in EXERCISES:
            try:
                conn.execute(text("""
                    INSERT INTO exercise_definitions (name, category, mvt, description)
                    SELECT :name, :cat, :mvt, :desc
                    WHERE NOT EXISTS (
                        SELECT 1 FROM exercise_definitions WHERE LOWER(name) = LOWER(:name)
                    )
                """), {"name": name, "cat": category, "mvt": mvt, "desc": description})
                conn.commit()
            except Exception:
                conn.rollback()


run_migrations()

app = FastAPI(title="VBT Platform API", version="1.0.0")

ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.getenv("ALLOWED_ORIGINS", "https://130.61.232.212,http://130.61.232.212").split(",")
    if origin.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api")
app.include_router(users.router, prefix="/api")
app.include_router(exercises.router, prefix="/api")
app.include_router(plans.router, prefix="/api")
app.include_router(calendar.router, prefix="/api")
app.include_router(sessions.router, prefix="/api")
app.include_router(analytics.router, prefix="/api")
app.include_router(dashboard.router, prefix="/api")
app.include_router(admin.router, prefix="/api")


@app.get("/health")
def health():
    return {"status": "ok"}
