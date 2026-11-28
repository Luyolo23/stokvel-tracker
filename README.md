# Stokvel Contribution Tracker

A mobile-friendly web app that gives a stokvel (savings group) **one shared, trustworthy record** of monthly contributions.
It **records** money; it never moves it. No payments, no bank integration, no messaging APIs.

**Live site:** `<paste your URL here>`
**Note:** free hosting may sleep when idle, so the first load can take up to a minute.

## Test accounts (all made-up data)

All accounts use the same password: `Stokvel2026!`

| Role | Username | What to try |
|------|----------|-------------|
| Chair | `chair.demo` | Summary dashboard, countersign the treasurer's payment, audit log |
| Treasurer | `treasurer.demo` | Confirm or reject Pule's claim, overdue list + WhatsApp reminder, reverse a confirmed payment |
| Member | `member.demo` | Tap "I've paid" on the current month, then sign in as the treasurer to confirm it |
| Member | `sipho.demo` | Has overdue months (two earlier months, plus the current month once its due day has passed) |
| Member | `ayanda.demo` | Has a rejected payment with a reason |
| Member | `pule.demo` | Has a payment awaiting confirmation |

Suggested walkthrough: sign in as `member.demo`, claim this month's payment, sign out, sign in as `treasurer.demo`, confirm it, sign in as `chair.demo` and watch the summary and audit log update.

## How it works

1. Each month the system creates a DUE contribution for every member (daily job at 00:05, idempotent). On startup it also backfills any months missed while the service was asleep or down.
2. A member taps **I've paid** and enters a reference. Status becomes PENDING. They can withdraw the claim until the treasurer decides it.
3. The treasurer checks the bank/cash and **confirms** or **rejects** (a reason is required and shown to the member).
4. The treasurer cannot decide on their own payment: the chair **countersigns** it from the chair's "Treasurer to confirm" tab.
5. The treasurer's **Overdue** tab lists unpaid months past the due date with a pre-written WhatsApp reminder (`wa.me` link: opens WhatsApp, nothing is sent automatically).
6. The chair sees collected vs expected, total owed, who is behind, and an audit log scoped to their group.

## Tech stack

- **Backend:** Java 21, Spring Boot 3.5 (Web, Data JPA, Security, Validation), PostgreSQL
- **Frontend:** React 18 + TypeScript (Vite), plain CSS, mobile-first
- **Delivery:** one Docker image (React build is served by Spring Boot, so there is no CORS and only one thing to host)

## Run locally

Prerequisites: Docker.

```bash
docker compose up --build
# open http://localhost:8080
```

`docker compose` enables the demo seed for local use. To develop without Docker rebuilds:

```bash
docker compose up db                 # Postgres only
SEED_ENABLED=true mvn spring-boot:run # backend on :8080 (needs Java 21 + Maven)
cd frontend && npm install && npm run dev   # frontend on :5173, proxies /api to :8080
```

Seeding is opt-in so an empty production database never gets publicly known accounts. Leave
`SEED_ENABLED` unset for a real group, or set `SEED_PASSWORD` to a non-default value for a demo.

Run the unit tests: `mvn test`

To reset the demo data locally: `docker compose down -v` (removes the database volume).

## Deploy (free tier)

1. Create a free hosted Postgres (e.g. Neon or Supabase). Note: the JDBC URL looks like
   `jdbc:postgresql://HOST/DBNAME?sslmode=require`, with the user and password given separately.
2. Create a Docker web service on a free host (e.g. Render) from this GitHub repo.
3. Set these environment variables:

| Variable | Example |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://HOST/DBNAME?sslmode=require` |
| `DB_USER` | your database user |
| `DB_PASSWORD` | your database password |
| `COOKIE_SECURE` | `true` (already the Docker default) |
| `SEED_ENABLED` | `true` for the demo; leave unset/false for a real group |
| `SEED_PASSWORD` | optional, overrides the demo password |

With `SEED_ENABLED=true` the app seeds the demo group on first start. Check the host's current free-tier limits (sleeping, database expiry) so the link stays live until selection ends.

## Design decisions

- **Money is stored as whole cents** (`long`), never floats.
- **Nothing is deleted.** Mistakes are fixed with *Reverse*, which puts a confirmed month back to DUE and writes who/why to the audit log. A member can also withdraw a pending claim; the audit log keeps the history.
- **Segregation of duties:** a treasurer cannot confirm, reject or reverse their own payment. The chair countersigns the treasurer's own payments.
- **Role rules live in one place** (`SecurityConfig` for route access, `ContributionService.decidable`/`decidableByChair` for business rules).
- **Audit entries are scoped to the group** so a chair only sees their own group's history.
- **Entities are never returned by the API**, only DTOs (`Dtos.java`).
- **Passwords are BCrypt-hashed.** Sessions use an HttpOnly, SameSite=Strict cookie. CSRF protection is disabled on purpose because the state-changing API is same-origin and JSON-only, and the session cookie is SameSite=Strict. The login endpoint accepts a form post, so login CSRF is still possible in principle; revisit if the API is opened to other origins.
- **Why a web app, not a native app:** members already use WhatsApp on their phones and need nothing to install.

## Known limitations / what I'd build next

- Group and member setup is done by the seed script, not admin screens.
- Schema is auto-updated by Hibernate (`ddl-auto=update`); I'd move to Flyway migrations.
- No partial or multi-month payments, payout rotation, late fees, or CSV export.
- Login has no rate limiting or password reset.
- Real groups should confirm whether members may see each other's payment status (currently they cannot).
- The demo phone numbers are deliberately invalid placeholders, so the WhatsApp reminder buttons will not reach a real phone until real numbers are loaded.
