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
# Format nazwy: "English (Polski)" - angielski termin wiodący (spójny z
# literaturą VBT i standardem środowiska), polski w nawiasie wg terminologii
# PZPC. Nazwa to jednocześnie klucz dopasowania między serwerem a aplikacją.
EXERCISES = [
    # --- OLYMPIC (dwubój) ---
    # Main lifts
    ("Snatch (Rwanie)",                          "olympic",   0.80, "Klasyczne rwanie - ćwiczenie dwubojowe. VBT: peak velocity >1.0 m/s przy submaksymalnych ciężarach."),
    ("Clean (Zarzut)",                           "olympic",   0.75, "Zarzut na klatkę - pierwsza część podrzutu. Kluczowy ruch dwubojowy."),
    ("Jerk (Wyrzut)",                            "olympic",   0.65, "Wyrzut sztangi nad głowę - druga część podrzutu. Mierz peak velocity sztangi."),
    ("Split Jerk (Wyrzut nożycowy)",             "olympic",   0.65, "Wyrzut z ustawieniem nóg w nożyce - wariant startowy."),
    ("Push Jerk (Wyrzut siłowy)",                "olympic",   0.60, "Wyrzut bez nożyc, z ugięciem i wyprostem kolan."),
    ("Clean & Jerk (Podrzut)",                   "olympic",   0.75, "Pełny podrzut - zarzut + wyrzut. Kompleksowe ćwiczenie olimpijskie."),
    # Snatch derivatives
    ("Power Snatch (Rwanie siłowe)",             "olympic",   1.00, "Rwanie z chwytem ponad równoległą. Wyższe prędkości niż klasyczne rwanie."),
    ("Hang Snatch (Rwanie z zawieszenia)",       "olympic",   0.80, "Rwanie z pozycji zawieszenia (powyżej/poniżej kolan)."),
    ("Block Snatch (Rwanie z bloków)",           "olympic",   0.80, "Rwanie z bloków - skrócony zakres ruchu."),
    ("Snatch Pull (Ciąg rwaniowy)",              "olympic",   0.50, "Ciąg rwaniowy bez wejścia pod sztangę. Ćwiczenie siłowe dla rwania."),
    ("Hang Snatch Pull (Ciąg rwaniowy z zawieszenia)", "olympic", 0.50, "Ciąg rwaniowy z pozycji zawieszenia."),
    # Clean derivatives
    ("Power Clean (Zarzut siłowy)",              "olympic",   0.85, "Zarzut z chwytem ponad równoległą. Wysoka prędkość sztangi."),
    ("Hang Clean (Zarzut z zawieszenia)",        "olympic",   0.75, "Zarzut z pozycji zawieszenia."),
    ("Block Clean (Zarzut z bloków)",            "olympic",   0.75, "Zarzut z bloków - skrócony zakres ruchu."),
    ("Clean Pull (Ciąg zarzutowy)",              "olympic",   0.45, "Ciąg zarzutowy bez wejścia pod sztangę."),
    ("Hang Clean Pull (Ciąg zarzutowy z zawieszenia)", "olympic", 0.45, "Ciąg zarzutowy z pozycji zawieszenia."),
    # --- STRENGTH (siłowe) ---
    ("Back Squat (Przysiad tylny)",              "strength",  0.30, "Przysiad ze sztangą z tyłu. MVT 0.30 m/s przy 1RM (Pareja-Blanco 2017)."),
    ("Front Squat (Przysiad przedni)",           "strength",  0.32, "Przysiad przedni - kluczowe ćwiczenie pomocnicze w dwuboju."),
    ("Deadlift (Martwy ciąg)",                   "strength",  0.15, "Martwy ciąg. Niska MVT (~0.15 m/s) - monitoring zatrzymania technicznego."),
    ("Romanian Deadlift (Martwy ciąg rumuński)", "strength",  0.15, "RDL - wzmocnienie łańcucha tylnego dla ciągów olimpijskich."),
    ("Bench Press (Wyciskanie leżąc)",           "strength",  0.17, "Wyciskanie leżąc. MVT 0.17 m/s (García-Ramos 2018)."),
    ("Overhead Press (Wyciskanie nad głowę)",    "strength",  0.22, "Wyciskanie nad głowę stojąc. Ważne dla fazy wyrzutu."),
    ("Push Press (Wyciskanie siłowe)",           "strength",  0.50, "Wyciskanie z impulsem nóg (dip-drive), bez wejścia pod sztangę."),
    # --- BALLISTIC (balistyczne) ---
    ("Jump Squat (Przysiad ze skokiem)",         "ballistic", 1.00, "Przysiad ze skokiem z obciążeniem. MVT >1.0 m/s. Monitoring mocy eksplozywnej."),
    # --- AUXILIARY (pomocnicze) ---
    ("Barbell Row (Wiosłowanie sztangą)",        "auxiliary", 0.25, "Wiosłowanie poziome - antagonista do wyciskań i ciągów."),
    ("Good Morning (Skłon dzień dobry)",         "auxiliary", 0.20, "Wzmocnienie grzbietu i łańcucha tylnego."),
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

        # --- Scal duplikat Power Clean (id 5 i 7 miały tę samą nazwę) ---
        # Przepnij referencje z duplikatów na kanoniczny (najniższy) rekord,
        # potem usuń osierocone duplikaty. Kluczujemy po obecnej nazwie w bazie.
        for dup_name in ["Zarzut siłowy (Power Clean)"]:
            try:
                conn.execute(text("""
                    WITH canon AS (
                        SELECT MIN(id) AS keep_id FROM exercise_definitions
                        WHERE LOWER(name) = LOWER(:name)
                    ),
                    dups AS (
                        SELECT id FROM exercise_definitions, canon
                        WHERE LOWER(name) = LOWER(:name) AND id <> canon.keep_id
                    )
                    UPDATE plan_exercises pe SET exercise_id = (SELECT keep_id FROM canon)
                    WHERE pe.exercise_id IN (SELECT id FROM dups)
                      AND NOT EXISTS (
                          SELECT 1 FROM plan_exercises pe2
                          WHERE pe2.plan_id = pe.plan_id
                            AND pe2.exercise_id = (SELECT keep_id FROM canon)
                      )
                """), {"name": dup_name})
                conn.execute(text("""
                    UPDATE rep_results SET exercise_id = (
                        SELECT MIN(id) FROM exercise_definitions WHERE LOWER(name) = LOWER(:name)
                    )
                    WHERE exercise_id IN (
                        SELECT id FROM exercise_definitions
                        WHERE LOWER(name) = LOWER(:name)
                          AND id <> (SELECT MIN(id) FROM exercise_definitions WHERE LOWER(name) = LOWER(:name))
                    )
                """), {"name": dup_name})
                conn.execute(text("""
                    DELETE FROM exercise_definitions
                    WHERE LOWER(name) = LOWER(:name)
                      AND id <> (SELECT MIN(id) FROM exercise_definitions WHERE LOWER(name) = LOWER(:name))
                      AND id NOT IN (SELECT exercise_id FROM rep_results)
                      AND id NOT IN (SELECT exercise_id FROM plan_exercises)
                """), {"name": dup_name})
                conn.commit()
            except Exception:
                conn.rollback()

        # --- Zmień nazwy istniejących na format: English (Polski) ---
        # Klucz = obecna nazwa w bazie, wartość = nowa nazwa kanoniczna.
        renames = {
            "Rwanie (Snatch)":                                  "Snatch (Rwanie)",
            "Zarzut (Clean)":                                   "Clean (Zarzut)",
            "Wyrwanie (Jerk)":                                  "Jerk (Wyrzut)",
            "Wyrwanie z rozkroku (Split Jerk)":                 "Split Jerk (Wyrzut nożycowy)",
            "Szturm (Push Jerk)":                               "Push Jerk (Wyrzut siłowy)",
            "Podrzut - Zarzut i Wyrwanie (Clean & Jerk)":       "Clean & Jerk (Podrzut)",
            "Rwanie siłowe (Power Snatch)":                     "Power Snatch (Rwanie siłowe)",
            "Rwanie z wieszania (Hang Snatch)":                 "Hang Snatch (Rwanie z zawieszenia)",
            "Rwanie z klocków (Block Snatch)":                  "Block Snatch (Rwanie z bloków)",
            "Szarpanie do rwania (Snatch Pull)":               "Snatch Pull (Ciąg rwaniowy)",
            "Szarpanie do rwania z wieszania (Hang Snatch Pull)": "Hang Snatch Pull (Ciąg rwaniowy z zawieszenia)",
            "Zarzut siłowy (Power Clean)":                      "Power Clean (Zarzut siłowy)",
            "Zarzut z wieszania (Hang Clean)":                  "Hang Clean (Zarzut z zawieszenia)",
            "Zarzut z klocków (Block Clean)":                   "Block Clean (Zarzut z bloków)",
            "Szarpanie do zarzutu (Clean Pull)":               "Clean Pull (Ciąg zarzutowy)",
            "Szarpanie do zarzutu z wieszania (Hang Clean Pull)": "Hang Clean Pull (Ciąg zarzutowy z zawieszenia)",
            "Przysiad (Back Squat)":                            "Back Squat (Przysiad tylny)",
            "Przysiad przedni (Front Squat)":                   "Front Squat (Przysiad przedni)",
            "Martwy ciąg (Deadlift)":                           "Deadlift (Martwy ciąg)",
            "Martwy ciąg rumuński (Romanian Deadlift)":         "Romanian Deadlift (Martwy ciąg rumuński)",
            "Wyciskanie leżąc (Bench Press)":                   "Bench Press (Wyciskanie leżąc)",
            "Wyciskanie żołnierskie (Overhead Press)":          "Overhead Press (Wyciskanie nad głowę)",
            "Szturm ze sztangą (Push Press)":                   "Push Press (Wyciskanie siłowe)",
            "Przysiad skoczny (Jump Squat)":                    "Jump Squat (Przysiad ze skokiem)",
            "Wiosłowanie sztangą (Barbell Row)":               "Barbell Row (Wiosłowanie sztangą)",
            "Dobre rano (Good Morning)":                        "Good Morning (Skłon dzień dobry)",
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

        # --- Zaktualizuj MVT i category dla istniejących (po zmianie nazw) ---
        for name, cat, mvt, _desc in EXERCISES:
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
