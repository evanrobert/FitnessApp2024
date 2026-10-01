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
- **Wins** — lift goals ("Bench 185") are marked reached the moment a workout hits them, and the
  workout page shows how it moved each goal. Strength levels (Beginner → Elite) for bench, squat,
  deadlift, overhead press, row, front squat and incline bench compare your best estimated max
  with your body weight using widely published adult strength standards (a guide, not an
  official ranking). Entries get matching icons: a shake, chicken, coffee, a flexed arm...
- **Goals, custom metrics, limitations** — goals measure themselves against live data;
  track anything numeric (fitness tests, habits); injuries shown while logging workouts.
- **Today, Insights, Timeline** — daily checklist and scoreboard, range-scoped analytics,
  data-backed insights and comparisons, achievements, and a feed of everything logged.
- **Your data** — CSV export of every dataset; self-service account deletion.
- **Account** — sign in with username or email, password reset by email, password
  change, and an opt-in Monday summary email.

**Simple by default.** Today opens with four big buttons (I worked out, I ate something,
How I feel, Weigh myself). Each form asks only the basics: exercise, weight and how many
times; food name (calories optional); hours slept, energy and mood. Everything else
sits behind "More details". Labels use everyday words, with no abbreviations.

Navigation is five sections (Today, Workouts, Food, Sleep & mood, Progress) plus
Settings; Workouts, Progress and Settings have tabs for their pages.

**Support the developer.** A "Support the developer" link to
https://buymeacoffee.com/EvanLindsay appears in the menu and under Settings. Change it with
`APP_SUPPORT_URL` (or `app.support.url` in `config/application-local.yml`). Only `https://` links are accepted,
and payment happens entirely on that site; the app never sees card details. Set it to an
empty value to hide the button.

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

## Email

The app sends four kinds of email: address confirmation (after sign-up or an email
change), password reset links, "your password was changed" notices, and the opt-in
Monday summary.

- **No SMTP configured (default for dev and local):** nothing is sent. Each email,
  links included, is printed in the console where the app runs, so you can test the
  flows by copying the link from there.
- **Real email:** set `spring.mail.host`, `port`, `username` and `password` (in
  `config/application-local.yml`, or as `SPRING_MAIL_HOST` etc. on a server) plus
  `APP_BASE_URL` (the address people use to reach the app; links are built from it,
  never from the request) and `MAIL_FROM`. Use a transactional provider (SendGrid,
  Postmark, Mailgun, SES) with SPF/DKIM set up for your domain so mail isn't flagged as spam.

The weekly summary is **off by default** and can only be turned on for a confirmed
address. It goes out Monday at 07:00 in each member's time zone and carries activity
counts only (workouts, sets, cardio minutes, records, days logged). Weight, sleep,
mood, injuries and goal names stay in the app. Every summary has a one-click
unsubscribe link (and `List-Unsubscribe` headers).

## Security

- **Passwords:** at least 10 characters; common passwords and ones containing the
  username or email are rejected. Stored with BCrypt.
- **Password reset:** single-use link valid 30 minutes, sent only to a *confirmed*
  address (a mistyped, unconfirmed address could belong to someone else). Only a
  SHA-256 hash of each link token is stored. The forgot-password form responds the
  same whether or not an account exists. At most 3 reset emails per account per hour.
- **Password change:** requires the current password; resets and changes sign out
  every other session and email a notice.
- **Brute force:** 5 wrong passwords in a row lock the account for 15 minutes; each IP
  is limited to 10 sign-in attempts per 5 minutes, 10 sign-ups per hour and 5 reset
  requests per 15 minutes (HTTP 429). Limits are in memory, per app instance. Behind a
  reverse proxy, set `SERVER_FORWARD_HEADERS_STRATEGY=native` so the real client IP is used.
- **Sessions:** 30-minute idle timeout (`SESSION_TIMEOUT`), new session id on sign-in,
  HttpOnly + SameSite cookies, `Secure` when `SESSION_COOKIE_SECURE=true`.
- **Headers:** CSP, frame blocking, nosniff, same-origin referrer, Permissions-Policy.
  CSRF protection on every form.

### Servers and deployments

Instead of the file, supply credentials as environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/fitness` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | — | Database credentials |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` when served over HTTPS |
| `APP_DEFAULT_TIME_ZONE` | `America/New_York` | "Today" for members without a time zone set |
| `APP_BRAND_NAME` | `Evan Fitness` | Name shown in the UI |
| `APP_SUPPORT_URL` | `https://buymeacoffee.com/EvanLindsay` | "Support the developer" page (https only; empty hides it) |
| `APP_BASE_URL` | `http://localhost:8080` | Public URL used in email links |
| `SPRING_MAIL_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | — | SMTP server (unset = no email sent) |
| `MAIL_FROM` | `Evan Fitness <no-reply@localhost>` | Sender address |
| `SESSION_TIMEOUT` | `30m` | Idle sign-out |

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
- **V5** adds email (unique, optional for existing accounts), sign-in lockout fields,
  email preferences and the `account_token` table for reset/confirmation links.
  Existing members are reminded to add and confirm an email so they can reset their password.

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

71 tests cover migrations (legacy replay), security and member isolation, password
reset and lockout, email confirmation and the weekly summary, every
tracking area end to end, analytics rules, and rendering every page against months of
synthetic data. All test data is synthetic.

## Data privacy

This app stores self-reported wellness data (weights, sleep, mood, injuries). Treat a
production database accordingly: TLS, restricted access, backups, and a retention policy.
Never load real member data into dev or test environments; use the demo seeder.

## Known follow-ups

- **Trainer/client features** (coach role, sharing, notes) and **progress photos** need
  product and privacy decisions (sharing model, storage, retention) before building.
- Rate limits and the weekly-email schedule run in each app instance. That's fine for
  one instance; with several, move rate limiting to a shared store or the proxy (the weekly
  email is already safe: each week is claimed once in the database).
- Units are imperial (lb, in, oz); a metric preference would need display conversion.
- Analytics load a member's full history per request — fine at personal scale; add
  date-bounded queries or caching if histories grow very large.
- Leftover legacy column/naming quirks kept to avoid churn: package names
  (`Evan.Application.Fitness`, `Repositorys`) and `user_login_details` table names.
