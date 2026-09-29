# Evan Fitness — training ledger

A server-rendered fitness tracker for people who want their training, nutrition,
recovery and body data in one place, with records, trends and insights computed
from what they actually log.

- **Train** — one-submit session builder (exercise blocks of sets, cardio, effort,
  location), a searchable library of 132 exercises (7–17 per muscle group) or any
  exercise name typed free-form, "last time" hints, repeat-a-session, per-exercise
  history, automatic PRs.
- **Fuel** — day view against calorie/macro/fiber targets, one-click re-log of recent
  foods, bulk-edit ledger, targets suggested from your profile (Mifflin–St Jeor).
- **Recover** — 30-second daily check-in (sleep, energy, mood, stress, soreness, water,
  steps, resting HR) → readiness score; one-tap water from anywhere.
- **Body** — weigh-ins and tape measurements with a rolling-average trend and weekly rate.
- **Goals, custom metrics, limitations** — goals measure themselves against live data;
  track anything numeric (fitness tests, habits); injuries shown while logging workouts.
- **Today, Insights, Timeline** — daily checklist and scoreboard, range-scoped analytics,
  data-backed insights and comparisons, achievements, and a feed of everything logged.
- **Your data** — CSV export of every dataset; self-service account deletion.

## Run it locally (no setup)

Requires JDK 17+.

```bash
./gradlew bootRun          # dev profile: in-memory H2 + demo data
```

Open http://localhost:8080 and log in as **demo / demo-password** — a synthetic member
with ~16 weeks of made-up history — or create your own account. Dev data is in memory
and resets on restart.

## Run against your own MySQL

One-time setup (MySQL 8 + MySQL Workbench):

1. In Workbench, connected as root, open `config/mysql-setup.sql`, replace `CHANGE_ME`
   with a password of your choice and run it. It creates an empty `fitness` database
   and a `fitness_app` account that can only use that database.
2. Copy `config/application-local.example.yml` to `config/application-local.yml` and
   enter the same username and password (and host/port if not `localhost:3306`).
   That file is git-ignored, so credentials never get committed.
3. Start the app on MySQL:

   ```bash
   ./gradlew bootRun -Pprofile=local        # macOS / Linux
   .\gradlew.bat bootRun -Pprofile=local    # Windows PowerShell
   ```

The first start creates every table automatically (Flyway); later starts only apply new
migrations. Your data persists between runs. Plain `bootRun` (no `-Pprofile`) still uses
the in-memory demo database.

Troubleshooting: *"MySQL credentials are not set"* → step 2 wasn't done or still says
`CHANGE_ME`. *"Access denied for user"* → the password in the file doesn't match the one
in the setup script. *"Communications link failure"* → MySQL isn't running or isn't on
the host/port in the URL.

### Servers and deployments

Instead of the file, supply credentials as environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/fitness` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | — | Database credentials |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` when served over HTTPS |
| `APP_DEFAULT_TIME_ZONE` | `America/New_York` | "Today" for members without a time zone set |
| `APP_BRAND_NAME` | `Evan Fitness` | Name shown in the UI |

```bash
./gradlew bootJar
DB_URL=jdbc:mysql://db:3306/fitness DB_USERNAME=... DB_PASSWORD=... \
SESSION_COOKIE_SECURE=true java -jar build/libs/Application-Fitness-0.1.0-SNAPSHOT.jar
```

## Upgrading an existing (legacy) database

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate only validates it.

- **V1** records the schema the original app created through Hibernate `ddl-auto`.
  Existing databases have no Flyway history, so they are *baselined* at V1 and skip it.
- **V2** drops the retired HRA upload table (`reports`), renames duplicate usernames to
  `<name>_dup<id>` (those accounts could never log in), enforces unique usernames, one
  profile and one target row per member, and seeds `ROLE_USER`.
- **V3** adds the tracking model and converts legacy data: the flat workout log becomes
  sessions → exercises → one row per set; sign-up weight becomes the first weigh-in;
  age becomes birth year.
- **V4** expands the exercise library to 132. Where a member had already created a
  personal exercise with the same name as a new library one, its sets and goals move to
  the library exercise and the duplicate is removed (same movement, same name).

**Back up the database before the first start of this version.** V2 permanently drops
the `reports` table (uploaded HRA files) and V3 drops `workout_information` after copying
every row. Both paths are tested on H2 (`LegacyMigrationTests`) and were verified against
a MySQL 8 copy of a database created by the original application.

## Architecture

Spring Boot 3.5 · Java 17 · Spring MVC + Thymeleaf · Spring Security · Spring Data JPA ·
Flyway · MySQL 8 (H2 for dev/tests). No front-end build: one CSS file, two small
vanilla-JS files, a self-hosted variable font.

```
Controller/   HTTP + view models (one controller per area; legacy URLs redirect)
Service/      Business logic, always scoped by member id
  AnalyticsService   dashboard, insights, achievements, timeline (derived on read)
  RecordsService     PRs / e1RM from set history (never stored, never stale)
  GoalService        progress and pace from live data
Model/        JPA entities + enums
Repositorys/  Spring Data repositories (queries filter by member)
Form/         Form objects for multi-part forms (session builder, sign-up)
Security/     Form login, CSRF, CSP; principal carries the member id
Web/          Binding guard, errors, chart JSON, dev demo seeder
templates/    fragments/ (layout, icons, ui), one folder per area
static/       css/app.css (design system), js/app.js, js/charts.js (SVG charts)
```

**Data ownership.** Every record belongs to a member directly or through its parent.
Ids come from the URL and ownership from the session; a global binder guard stops
request parameters from setting `id`, owners or roles, and services copy only editable
fields. A record that isn't yours is a 404.

**Design system.** Black for structure (navigation rail, scoreboards), white content
cards on warm paper, tan rules and labels, dodger blue only for actions and progress.
Chart colors (`--s1..--s3`) were validated for color-vision deficiency and contrast;
every chart has hover/focus tooltips and a "View as table" equivalent.

## Tests

```bash
./gradlew test
```

41 tests cover migrations (legacy replay), security and member isolation, every
tracking area end to end, analytics rules, and rendering every page against months of
synthetic data. All test data is synthetic.

## Data privacy

This app stores self-reported wellness data (weights, sleep, mood, injuries). Treat a
production database accordingly: TLS, restricted access, backups, and a retention policy.
Never load real member data into dev or test environments; use the demo seeder.

## Known follow-ups

- **Trainer/client features** (coach role, sharing, notes) and **progress photos** need
  product and privacy decisions (sharing model, storage, retention) before building.
- Password change and login rate-limiting are not implemented yet.
- Units are imperial (lb, in, oz); a metric preference would need display conversion.
- Analytics load a member's full history per request — fine at personal scale; add
  date-bounded queries or caching if histories grow very large.
- Leftover legacy column/naming quirks kept to avoid churn: package names
  (`Evan.Application.Fitness`, `Repositorys`) and `user_login_details` table names.
