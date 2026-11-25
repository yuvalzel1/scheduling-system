# Scheduling System

A small full-stack app for scheduling predefined tasks: pick a task, say when it should run, give it its inputs, and the server runs it at the right time, also after a restart.

- **Frontend**: React + TypeScript (Vite, MUI, TanStack Query, React Hook Form)
- **Backend**: Java 21, Spring Boot 4.1, Quartz
- **Database**: PostgreSQL 17

```
/frontend   React app and its Dockerfile
/backend    Spring Boot app and its Dockerfile
/docker     docker-compose.yml for the whole system
DESIGN.md   how the system is built, with diagrams
PLAN.md     the plan this was built from
```

For the design in pictures (architecture, class and sequence diagrams, the database, and how a new task shows up in the UI by itself), see **[DESIGN.md](DESIGN.md)**.

## Run it

You need Docker (with Compose). From the repository root:

```bash
docker compose -f docker/docker-compose.yml up --build
```

Then open **http://localhost:3000**.

| What | Where |
|---|---|
| UI | http://localhost:3000 |
| API (direct) | http://localhost:8080/api |
| API (through the UI's nginx) | http://localhost:3000/api |

Stop with `Ctrl+C`. To also delete the saved data: `docker compose -f docker/docker-compose.yml down -v`.

If ports 3000 or 8080 are taken, copy `docker/.env.example` to `docker/.env` and change `FRONTEND_PORT` / `BACKEND_PORT`. The same file sets the database name, user and password; the values in `docker-compose.yml` are demo defaults.

### See a task run

1. In the UI choose **Add schedule** → **Log Task** → **Every X** → every `1` minute → message `hello`.
2. Within a minute the row's **Last run** column changes from "Not run yet" to "Succeeded" with the time. The page refreshes itself; no reload is needed.
3. Press the **Run history** (clock) button on the row to see every run so far.
4. Restart only the backend: `docker compose -f docker/docker-compose.yml restart backend`.
5. New runs keep appearing without touching the UI: the alarm lives in the database.

What the tasks themselves write (the log message, the pretend email) goes to the backend log:

```bash
docker compose -f docker/docker-compose.yml logs -f backend
```

## What you can do

**Tasks** are fixed in code and read-only. Each declares its inputs (name, type, required or optional):

| Task | Does | Inputs |
|---|---|---|
| Log Task | writes a message to the log | `message` (text, required) |
| Dummy Email Task | pretends to send an email; it is only logged | `to`, `subject` (text, required), `body` (text, optional) |
| DB Query Task | reads the newest schedules from the database and logs how many it found | `limit` (number, optional, 1–100, default 10) |

**Schedules** can be created, edited and deleted. Each has a task, a timing and the task's input values.

| Timing | Example | Notes |
|---|---|---|
| One time | 1 Dec, 09:00 | must be in the future |
| Every X | every 15 minutes / every 2 hours | whole number ≥ 1; the first run is one full gap after saving |
| Weekly | Mon and Wed at 08:30 | at least one day; time is in the browser's time zone |
| Cron | `0 0/5 * * * ?` | **Quartz** format: 6 or 7 parts, starting with seconds. Not the 5-part Unix format |

The form builds the input fields from the chosen task's declaration and refuses to save while a required input is missing. The backend checks the same rules again.

### Run history (not asked for, added on purpose)

**The assignment does not ask for this.** It asks for a table of schedules and a form to manage them. I added run history anyway because I thought it was very much needed: without it, a user creates a schedule and then sees nothing happen, and the only proof that a task ran is a line in the server log. A scheduling tool where you cannot see what ran, or whether it worked, did not feel finished.

What it adds:

- a **Last run** column in the table: "Succeeded" or "Failed" with the time, or "Not run yet";
- a **Run history** button per row that lists that schedule's past runs, newest first, with start time, duration, the task that ran, the result, and the error message if it failed;
- both update by themselves every 5 seconds.

It is kept deliberately small: only the newest 100 runs per schedule are stored, and there is no filtering or paging. Everything else outside the brief is still left out (see [Left out on purpose](#left-out-on-purpose)). The design is in [DESIGN.md, section 9](DESIGN.md#9-run-history-added-beyond-the-brief).

## Tests

| | Command | Result at the time of writing |
|---|---|---|
| Backend | `cd backend && ./mvnw verify` | 283 tests pass; 99.6% line and 99.1% branch coverage (`backend/target/site/jacoco/index.html`) |
| Frontend | `cd frontend && npm ci && npm run test:coverage` | 137 tests pass; 96.4% statement and 95.0% branch coverage (`frontend/coverage/index.html`) |

The backend tests need Docker running: integration tests start a real PostgreSQL with Testcontainers.

**Backend**

- *Unit tests* (no Spring, no database): task-input checking, timing checking, turning each timing kind into the right Quartz trigger (including time zone, daylight-saving change and the missed-run rule), each task, the generic job.
- *Integration tests* (`*IntegrationTests`, whole app + real PostgreSQL + real Quartz):
  - every API flow, and about 30 kinds of bad input on both create and edit, each checked to return a 400 naming the right field and to save nothing;
  - schedules that really fire, a failing task that does not stop the next run, a deleted schedule that does not run again, editing and deleting a one-time schedule after it ran;
  - "all or nothing": when Quartz fails, no row is left; when the save fails after Quartz accepted the alarm, no alarm is left;
  - alarms are really in the Quartz tables, and Flyway builds every table from an empty database;
  - the database itself refuses a row whose timing columns do not match its kind (rows written directly with SQL, behind the application's back);
  - a task that exists only in the test sources is listed, has its declared inputs enforced, and is run by Quartz, with no application code knowing about it;
  - run history: successful and failed runs are recorded with their times and error, the newest run shows on the schedule, only the newest 100 are kept, deleting a schedule deletes its runs, and a run started by Quartz for real ends up in the history.

**Frontend** (Vitest + React Testing Library, with MSW as a fake backend that fails any request a test did not expect): table states, creating each timing kind and the exact request sent, required inputs, switching task or timing kind, editing, server errors shown under the right field, delete with confirmation, the Last run column (including picking up a new run by itself), the run history dialog with its loading, empty and error states, and keeping and showing a schedule's time zone when it is edited from another zone.

**Not tested, on purpose**: two people editing the same schedule at once (one user is assumed), a real long outage (tests check that each alarm carries the right missed-run rule and trust Quartz to apply it), several backend instances, load.

## Develop locally

```bash
# backend on :8080 with a throwaway PostgreSQL started by Testcontainers (needs Docker)
cd backend && ./mvnw spring-boot:test-run

# frontend on :5173, forwarding /api to :8080
cd frontend && npm ci && npm run dev
```

To run the backend against your own PostgreSQL instead, use `./mvnw spring-boot:run` with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` set.

Frontend checks: `npm run lint`, `npm run format:check`, `npm run build`.

## API

All under `/api`, JSON.

| Call | Does | Success |
|---|---|---|
| `GET /tasks` | the tasks and the inputs each one declares | 200 |
| `GET /schedules` | all schedules, newest first | 200 |
| `GET /schedules/{id}` | one schedule | 200 |
| `GET /schedules/{id}/executions` | that schedule's run history, newest first (at most 100) | 200 |
| `POST /schedules` | create | 201 + `Location` |
| `PUT /schedules/{id}` | replace | 200 |
| `DELETE /schedules/{id}` | delete | 204 |

Request body for create and replace:

```json
{
  "taskKey": "log",
  "timing": { "type": "WEEKLY", "days": ["MONDAY", "WEDNESDAY"], "time": "08:30" },
  "timeZone": "Europe/Berlin",
  "parameters": { "message": "hello" }
}
```

`timing` is one of:

```json
{ "type": "ONE_TIME", "runAt": "2026-12-01T07:00:00Z" }
{ "type": "INTERVAL", "every": 15, "unit": "MINUTES" }
{ "type": "WEEKLY", "days": ["MONDAY", "WEDNESDAY"], "time": "08:30" }
{ "type": "CRON", "expression": "0 0/5 * * * ?" }
```

Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details. Validation errors (400) add an `errors` list so a client can show each message next to its field:

```json
{
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are not valid.",
  "instance": "/api/schedules",
  "errors": [{ "field": "parameters.message", "message": "Message is required" }]
}
```

Unknown ids give 404; a failure on the server gives 500 without internal details.

Every schedule in a response carries `lastExecution`, its newest run, or `null` if it has not run yet. A run looks like this:

```json
{
  "id": "0b0c6f0e-6d2a-4d0e-9c59-3f3f0a1d7b11",
  "taskKey": "log",
  "status": "FAILED",
  "startedAt": "2026-12-01T07:00:00.012Z",
  "finishedAt": "2026-12-01T07:00:00.954Z",
  "errorMessage": "IllegalStateException: the disk is full"
}
```

`status` is `SUCCEEDED` or `FAILED`; `errorMessage` is `null` for a successful run.

## Design decisions

The diagrams behind these decisions are in [DESIGN.md](DESIGN.md).

**One generic Quartz job; tasks are plain Spring beans.** A task implements a two-method interface: `definition()` (key, name, inputs) and `execute(parameters)`. Quartz only ever sees one job class, `ScheduledTaskJob`, with one job and one trigger per schedule. Adding a task means adding one class; nothing in the scheduling code, the API or the UI changes, because the form is generated from the task's declaration.

**Our own `schedules` table next to Quartz's tables.** The `schedules` row is what the user sees and edits. Quartz holds only the alarm. The Quartz job data carries just the schedule's id, as plain text (`useProperties=true`), which is what the Quartz best-practice guide recommends; task and inputs are read from our table when the alarm rings. This avoids serialized Java objects in the database and means an edit never leaves stale inputs inside Quartz.

**Both are changed in one transaction.** Quartz's JDBC job store uses the same data source and joins the Spring transaction, so saving the row and setting the alarm either both happen or neither does. This is not taken on trust: two integration tests break the save at each point and check that nothing is left behind.

**The database guards its own rows.** Besides `NOT NULL` and allowed-value checks, a `CHECK` constraint in the table definition says which timing columns must and must not be filled for each timing kind. The application already guarantees this; the constraint means a bug or a manual SQL edit cannot store a half-filled schedule.

**Setting and ringing alarms is kept behind an interface.** `ScheduleService` talks to a two-method interface, `ScheduleAlarms`; the jobs, triggers and scheduler calls all sit behind it in the `schedule.quartz` package. The isolation is not total: `ScheduleValidator` uses Quartz's `CronExpression` class directly to check a cron text, because Quartz is what defines that syntax. Package dependencies run one way only: `schedule.quartz` → `schedule` → `task` and `execution`, and nothing points back.

**Run history is one row per finished run.** The runner writes it after the task ends, success or failure, and writing it can never break the run. Only the newest 100 runs per schedule are kept, so a schedule that fires every minute cannot grow the table without limit. The `execution` package knows a schedule only by its id; the database ties them together with a foreign key that deletes a schedule's runs with it. The UI asks again every 5 seconds, because tasks run on the server without the page knowing; polling is the simplest thing that works here.

**Quartz tables are created by Flyway, not by Spring Boot.** Spring Boot's `initialize-schema: always` drops the Quartz tables on every start, which would silently delete every alarm. It is set to `never`, and migration `V2` creates the tables from the script shipped inside the Quartz jar. Hibernate only validates the schema.

**The four timing kinds map to two trigger types.** One-time and "every X" are simple triggers. Weekly is turned into a cron expression (`0 30 8 ? * MON,WED`) and shares the cron trigger with user-written cron. Day names are used instead of numbers because in Quartz `1` means Sunday.

**Missed runs are decided explicitly, not left to Quartz defaults.** If the server was down when a run was due: a one-time run still happens once when the server is back (skipping it silently would be a surprise); repeating schedules skip what they missed and continue at the next normal time (so a long outage does not cause a burst of runs).

**A schedule never overlaps itself.** `@DisallowConcurrentExecution` applies per schedule, so a slow run delays that schedule's next run instead of piling up, while other schedules are unaffected.

**A failing task does not disturb the scheduler.** The error is logged and the next run happens as planned.

**Validation happens in one place on the server and reports everything at once.** Each problem is tied to a field name such as `timing.expression` or `parameters.message`. Values Jackson cannot even read (text for a number, `25:00`, an unknown timing kind) are reported in the same shape. Two cron mistakes get their own explanation because they are common: a 5-part Unix cron, and filling both day-of-month and day-of-week.

**Time zones.** Weekly and cron times are meant in a time zone, stored with the schedule and set on the trigger, so 08:30 stays 08:30 local time across daylight-saving changes. A new schedule gets the browser's zone automatically. Editing keeps the zone the schedule was created in, so changing only a message from another time zone cannot silently move 08:30 to a different moment. The form shows the zone next to the weekly time and the cron field, and the table names it when it is not the viewer's own zone.

**The UI calls a relative `/api` path.** Vite forwards it in development and nginx forwards it in Docker, so there is no CORS configuration and no build-time API address.

### One behaviour worth knowing

Because the alarm is saved inside our transaction, Quartz's internal "something new was scheduled" signal fires just before the commit, so the scheduler may not see a brand-new alarm until its next look at the database. `org.quartz.scheduler.idleWaitTime` is therefore set to 5 seconds, the lowest value Quartz recommends (default 30). The practical effect: a schedule created to fire within the next few seconds can be up to about 5 seconds late. It is never lost, and anything due later than that fires on time.

## Assumptions

- One user, no login.
- One backend instance. (The database job store is what Quartz clustering builds on, but clustering is not configured.)
- A task runs at most once per alarm. If the server is killed while a task is in the middle of running, that run is not repeated after the restart; later runs of the schedule are unaffected, and the interrupted run leaves no entry in the run history. (Runs that were due while the server was *down* are handled by the missed-run rules above.)
- The time zone of a schedule is the browser's time zone at the moment it is created. It is shown, but there is no picker to change it.
- A one-time schedule stays in the table after it ran, until the user deletes it. Editing it needs a new future date.
- "Every 15 minutes" first runs 15 minutes after saving, not immediately.
- An input the task does not declare is rejected rather than silently dropped; a required text input that contains only spaces counts as missing.
- The DB Query Task quietly keeps `limit` between 1 and 100, since a task input declares only a name, a type and whether it is required.
- The email task does not send real email.

## Left out on purpose

The brief asks for specific things, and this builds those and the bonus (a declared input list per task, enforced in the UI and the backend) and, with one exception, stops there. The exception is [run history](#run-history-not-asked-for-added-on-purpose), which I added because the tool did not feel usable without it. Not built: pause/resume, "run now", a preview of upcoming run times, schedule names, a time zone picker, login.

## AI tools

This project was built with **Claude Code** (Anthropic's coding agent, model Claude Fable 5.2), directed step by step by me:

- **Planning.** I gave it the assignment and had it write `PLAN.md` in plain language. I then asked it to check the plan against current documentation, questioned whether it was simple enough, cut everything the assignment does not ask for, and asked whether the planned tests covered the edge cases. `PLAN.md` is the result of those rounds and is kept in the repository.
- **Documentation lookups.** Through the **Context7** MCP server it read the current Spring Boot, Quartz, TanStack Query, React Hook Form, MUI, Vite and Vitest docs before choosing an approach. Several decisions above come straight from that, for example not letting Spring Boot initialise the Quartz schema, and keeping only strings in the Quartz job data.
- **Writing code and tests.** It wrote the backend, the tests, the Docker files and this README in the main session, and built the frontend in a parallel sub-agent working from a written API contract. The backend skeleton came from Spring Initializr.
- **Independent review.** I had **Codex** (OpenAI's coding agent) review the finished code and documentation without changing any files. Its findings were then fixed with Claude Code: editing a schedule from another time zone silently changed its zone, a run duration could display as "1 min 60 s", one architecture diagram reused a node name, and this README overstated how isolated Quartz is.
- **Verifying.** It ran both test suites, built and started the Docker stack, exercised the API with `curl`, restarted the backend to confirm alarms survive, and drove the UI in a real browser through the **Playwright** MCP server to check create, edit, a server-side validation error and delete against the real backend.
