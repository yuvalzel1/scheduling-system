# Scheduling System: The Plan (in simple words)

Version 3, the slim one. We build exactly what the assignment asks for, plus its bonus item. Nothing else.

Tool choices were checked against the current official docs through Context7.

## 1. What we are building

A small web app where a user can say: **"run this task, at these times, with these inputs"**.

- The user sees a table of all schedules.
- The user can add, edit and delete a schedule.
- The server runs the tasks at the right time, even after a restart.

## 2. The three parts

| Part | What it is | Tools |
|---|---|---|
| Frontend | The screen the user sees | React + TypeScript, Vite, MUI, TanStack Query, React Hook Form |
| Backend | The brain that saves schedules and runs tasks | Java 21, Spring Boot 4.1, Quartz, Maven |
| Database | Where everything is saved | PostgreSQL |

All three start with one command: `docker compose up`.

## 3. Folder layout

```
/frontend   React app + its Dockerfile
/backend    Spring Boot app + its Dockerfile
/docker     docker-compose.yml (starts database, backend, frontend)
README.md   how to run, design decisions, assumptions, AI tools used
```

Backend code is grouped by topic: `task/`, `schedule/`, `common/`.

## 4. The main ideas

### Tasks (fixed list, read-only)

A task is a piece of Java code. The user cannot create or edit tasks, only pick one.

Each task tells us (this is the bonus item):
- its name and a short description
- which inputs it needs: name, type (text or number), required or optional

We build 3 tasks, the ones the assignment gives as examples:

| Task | What it does | Inputs |
|---|---|---|
| Log Task | writes a message to the server log | `message` (text, required) |
| Dummy Email Task | pretends to send an email; it only writes it to the log | `to` (text, required), `subject` (text, required), `body` (text, optional) |
| DB Query Task | reads the newest schedules from the database and logs their count | `limit` (number, optional) |

Together they cover every case: text and number, required and optional.

Adding a new task later = adding one new Java class.

### Schedules (the thing the user manages)

A schedule holds exactly what the assignment lists:
- which task to run
- when to run it (one of the 4 kinds below)
- the input values for the task

### The 4 kinds of timing

| Kind | Example | What the user fills in | How Quartz runs it |
|---|---|---|---|
| One time | "On 1 Dec at 09:00" | date + time | simple trigger, fires once |
| Every X | "Every 15 minutes" | a number + minutes/hours | simple trigger, repeats forever |
| Weekly | "Mon and Wed at 08:30" | pick days + time | cron trigger; we build the cron text `0 30 8 ? * MON,WED` |
| Cron | `0 0/5 * * * ?` | a cron text (advanced users) | cron trigger with the user's text |

Two details:
- **Quartz cron is not Unix cron.** It has 6 or 7 parts and starts with *seconds*. The form says this in a short help text with an example.
- **Time zone.** The browser sends its own time zone along with the schedule, without asking the user. So "08:30" means 08:30 where the user is.

## 5. How the backend works

1. The frontend sends a schedule to the backend.
2. The backend checks it:
   - the task exists
   - all required inputs are there and have the right type
   - the timing makes sense (cron text is valid, one-time date is in the future, at least one weekday is picked)
3. The backend saves it in our `schedules` table.
4. The backend tells **Quartz** to set an alarm (a "trigger") for it.
5. When the alarm rings, Quartz calls one generic job. That job reads the schedule, finds the right task, and runs it with the saved inputs.

Steps 3 and 4 happen in one database transaction, so our table and Quartz never disagree.

### Rules for the tricky cases

| Case | What we do |
|---|---|
| An input the task does not know | reject with an error |
| A required text input that is only spaces | counts as missing |
| A number input sent as text, or text sent as a number | reject with an error |
| "Every X" with 0, a negative number, or not a whole number | reject with an error |
| "Every X": when is the first run? | one full gap after saving ("every 15 minutes" first runs 15 minutes from now) |
| Weekly with the same day twice | treat it as once |
| A 5-part Unix cron | reject, with a message that explains the Quartz format |
| A valid cron that can never fire (for example a year in the past) | reject with an error, not a server crash |
| An unknown time zone | reject with an error |
| Fields that belong to another timing kind | ignore and do not save them |
| Edit or delete a one-time schedule that already ran | must work; Quartz has already removed its own alarm, so we handle "alarm not found" quietly |
| Edit a schedule | the old alarm is replaced, so there is always exactly one alarm per schedule |
| An edit that fails the checks | the old schedule and its alarm stay untouched |
| A task throws an error while running | the error is logged; the scheduler keeps working and the next run still happens |
| The alarm rings but the schedule row is gone | the job logs a warning and stops quietly |
| Unknown id on read, edit or delete | "not found" error (404) |

### API

| Call | What it does |
|---|---|
| `GET /api/tasks` | list of tasks and the inputs each one needs |
| `GET /api/schedules` | all schedules |
| `GET /api/schedules/{id}` | one schedule |
| `POST /api/schedules` | create |
| `PUT /api/schedules/{id}` | edit |
| `DELETE /api/schedules/{id}` | delete |

### Database tables

- `schedules`: one row per schedule (task, timing fields, time zone, inputs saved as JSON)
- `QRTZ_*`: Quartz's own tables

## 6. Backend rules we follow (from the official docs)

**Quartz**

| Rule | In simple words |
|---|---|
| Keep Quartz data in the database (`job-store-type: jdbc`) | Alarms survive a restart. |
| Do **not** let Spring Boot create the Quartz tables on startup | The docs warn that `initialize-schema: always` drops the tables and deletes all alarms on every restart. We set it to `never` and create the tables with a Flyway migration file. |
| Put only simple text in the job's data (`useProperties: true`) | We store just the schedule's id. The real inputs stay in our own table. |
| One generic job class, one Quartz job per schedule | The job extends Spring's `QuartzJobBean`, so Spring can hand it our services. |
| `@DisallowConcurrentExecution` on the job | The same schedule never runs twice at once. |
| Say clearly what to do with missed runs | See below. |

Missed runs (server was down when a run was due):

| Kind | What we do |
|---|---|
| One time | run it once as soon as the server is back |
| Every X, Weekly, Cron | skip the missed ones, continue at the next normal time |

**Spring Boot**

| Rule | In simple words |
|---|---|
| Flyway migration files create all tables | The database builds itself on first start. Hibernate only checks the tables, it never changes them. |
| Standard error format (Problem Details) | Every error has the same shape, with a "field → message" list so the form can show each error next to the right input. |
| Separate classes for API data and database data | The API stays stable when tables change. |
| Settings come from environment variables | Same build runs locally and in Docker. No passwords in the code. |

## 7. How the frontend works

**Main page: the table**

One row per schedule. Columns: task, timing in plain words ("Every 15 minutes", "Mon, Wed at 08:30"), inputs, and edit / delete buttons.

Also: an "Add schedule" button, an empty state, loading and error states, a confirm box before delete, and a short message after each action ("Schedule saved").

**Add / Edit form (a dialog)**

1. Pick a task from a dropdown
2. Pick the timing kind (One time / Every X / Weekly / Cron); the matching fields appear
3. Task inputs: **built automatically** from the task's input list. Required inputs are marked, and the form cannot be saved without them.

## 8. Frontend rules we follow (from the official docs)

| Rule | In simple words |
|---|---|
| TanStack Query holds all server data | No hand-written loading flags. |
| After create / edit / delete, refresh the list (`invalidateQueries`) | The table updates itself. |
| React Hook Form with its built-in rules | One way to check every field, including the task inputs that change per task. Fields that are hidden are removed from the form, so they are not checked or sent. |
| Server errors go back into the form (`setError`) | A field error from the backend shows under that field. |
| The browser's own date-time and time inputs, inside MUI text fields | No extra date library. |
| Same `/api` path in dev and in Docker | Vite forwards `/api` in dev, nginx does it in Docker. No CORS setup. |
| Labels on every input, keyboard works everywhere | Basic accessibility. |

## 9. The bonus item

Each task declares its inputs: name, type, required or optional. This list is used in two places:

- **Frontend**: builds the input fields from it, marks required ones, and blocks saving when one is missing or has the wrong type.
- **Backend**: checks the same rules again on create and edit, and answers with a clear error per field.

## 10. Tests

Tools: JUnit 5, Mockito, AssertJ and Testcontainers (real PostgreSQL) for the backend; Vitest, React Testing Library and a fake backend (MSW) for the frontend. Coverage reports: JaCoCo and Vitest.

Every row in "Rules for the tricky cases" (section 5) has at least one test below.

### Backend unit tests (fast, no database)

**Checking task inputs**
- all required inputs given → OK
- required input missing → error on that field
- required text is empty or only spaces → error
- optional input left out → OK
- number given where text is expected, and text where a number is expected → error
- an input the task does not know → error
- no inputs at all for a task with no required inputs → OK
- unknown task → error

**Checking the timing**
- one time: future date OK; past date → error; no date → error
- every X: 1 is OK; 0, negative, not a whole number → error; unknown unit → error
- weekly: one day OK; all seven days OK; no days → error; same day twice → treated as once; bad time (25:00) → error
- cron: valid Quartz text OK; garbage → error; 5-part Unix cron → error with the helpful message; cron that can never fire → error
- time zone: valid OK; unknown → error

**Turning a schedule into a Quartz alarm**
- one time → fires once at the exact moment, in the right time zone
- every X → right gap for minutes and for hours, repeats forever, first run is one gap from now
- weekly → right cron text for the chosen days and time (checks the day names, including Sunday, and midnight 00:00)
- cron → the user's text is used as is
- time zone is set on the alarm
- weekly at 08:30 stays 08:30 local time across a daylight-saving change
- each kind carries the right missed-run rule
- the job data holds only the schedule id
- the job class is marked "no two runs of the same schedule at once"

**Each task**
- Log Task writes the message to the log
- Dummy Email Task logs recipient and subject; works with and without a body
- DB Query Task works with a limit, without a limit, and on an empty table

**The generic job**
- finds the schedule and calls the right task with the saved inputs (a number arrives as a number)
- schedule row is gone → logs a warning, does not throw
- task throws → error is logged, the job does not break the scheduler

### Backend integration tests (real PostgreSQL, real Quartz)

**API, the normal flows**
- list tasks → the 3 tasks with their input lists
- list schedules → empty list at first, then the created ones
- create one schedule of each of the 4 kinds → saved, and exactly one alarm exists in Quartz for each
- read one schedule
- edit: change only the inputs; change the timing inside the same kind; change to another kind; change to another task (inputs are checked against the new task)
- after every edit → still exactly one alarm, with the new timing
- delete → row gone and alarm gone

**API, the error flows**
- every bad input from the unit lists above, sent through the real API, returns 400 with an error on the right field
- broken JSON and an unknown timing kind → 400
- read, edit, delete with an unknown id → 404
- a failed edit leaves the old schedule and alarm unchanged
- all errors come back in the same standard shape

**Running for real**
- a one-time schedule 2 seconds ahead fires once, and the task gets the saved inputs
- an "every X" schedule fires more than once (the test uses a short gap set directly in Quartz terms, so it does not wait a full minute)
- a task that throws does not stop the next run
- after delete, the task is not called again
- edit and delete of a one-time schedule that already ran both succeed

**Staying in sync and surviving restarts**
- "all or nothing": when Quartz refuses the alarm, no row is left in our table; when our save fails, no alarm is left in Quartz
- after create, the alarm is really stored in the Quartz database tables (this is what makes it survive a restart)
- the app starts on an empty database and Flyway builds every table

### Frontend tests

**Table**
- loading state, empty state, rows for several schedules
- loading fails → error message is shown
- timing in plain words is right for each of the 4 kinds (small separate tests for the text function)

**Create**
- for each of the 4 kinds: fill the form, save → the right request is sent, including the browser's time zone
- a number input is sent as a number, not as text
- an optional input left empty is not sent at all
- required input missing → save is blocked, nothing is sent, the field shows an error
- weekly with no day picked → blocked
- every X with 0, a negative number or letters → blocked
- one-time date in the past → blocked
- cron left empty → blocked
- switching task → the old task's inputs disappear and are not sent
- switching timing kind → the old kind's fields disappear and are not sent
- the save button is disabled while saving (no double save)

**Edit**
- the form opens filled with the schedule's task, timing and inputs (one-time date shown in local time, weekly days ticked)
- save → an update request is sent for the right id, and the table shows the new values

**Errors from the server**
- a field error (for example a bad cron) shows under that field, and the dialog stays open with the user's data
- a general failure (server down) shows a message at the top of the dialog
- the task list fails to load → the form says so instead of showing an empty dropdown

**Delete**
- confirm → delete request sent, row disappears
- cancel → nothing sent, row stays
- delete fails → error message, row stays

### One manual check at the end (written in the README)

1. `docker compose up` on a clean machine state
2. create a "Log Task every 1 minute" schedule in the browser
3. see the message in the backend log
4. restart only the backend container
5. see the message appear again without touching the UI

### Not tested, on purpose

- two people editing the same schedule at the same moment (we assume one user)
- a real long outage (we test that each alarm carries the right missed-run rule, and trust Quartz to apply it)
- several backend instances, and heavy load

## 11. Docker

- `backend/Dockerfile`: two stages. Stage 1 builds the jar and splits it into layers (Spring Boot's recommended way). Stage 2 is a small Java 21 image that runs it as a non-root user.
- `frontend/Dockerfile`: two stages. Stage 1 builds the React app. Stage 2 is nginx serving the files and forwarding `/api` to the backend.
- `docker/docker-compose.yml`: starts `postgres`, `backend`, `frontend`. The backend waits until the database is healthy. Database data is kept in a named volume.

Result: open `http://localhost:3000` and the app works.

## 12. README

- how to run (one command) and how to run the tests
- how to see a task running (watch the backend log)
- design decisions
- assumptions
- AI tools used: Claude Code for planning, code, tests and review, with the Context7 docs server to check choices against current official docs

## 13. Assumptions

- One user, no login.
- One backend instance.
- The time zone is the browser's time zone.
- A one-time schedule stays in the table after it ran, until the user deletes it.
- The email task does not send real email.
- The same task can be used in many schedules.

## 14. Left out on purpose

Not asked for, so not built:

- run history
- pause / resume
- "run now" button
- preview of next run times
- schedule names
- time zone picker
- login and users

## 15. Checklist against the assignment

| Asked | Where |
|---|---|
| Table of all schedules, one row each | section 7 |
| Create, edit, delete | sections 5, 7 |
| Pick a task from a fixed list | sections 4, 7 |
| Set the timing | sections 4, 7 |
| Fill in task inputs | sections 4, 7 |
| One-time, every X, weekly, cron | section 4 |
| Spring Boot CRUD API | section 5 |
| Quartz runs the jobs | sections 5, 6 |
| Everything saved in the database | sections 5, 6 |
| Read-only tasks: log + others | section 4 |
| Inputs passed to the task when it runs | section 5 |
| Bonus: input list per task, enforced in UI and backend | section 9 |
| Unit tests, integration tests, coverage | section 10 |
| `/frontend`, `/backend`, `/docker` | section 3 |
| Dockerfile per part + docker-compose | section 11 |
| README: run, decisions, assumptions | section 12 |
| AI tools stated and explained | section 12 |

## 16. Build order

1. **Skeleton**: folders, git, Spring Boot project, React project, docker-compose with PostgreSQL
2. **Tasks**: task interface, the 3 tasks, `GET /api/tasks`
3. **Schedules CRUD**: Flyway tables, API, input checking, error format
4. **Quartz**: turn each schedule into a trigger, the generic job, missed-run rules
5. **Frontend table** + delete
6. **Frontend form** with the 4 timing kinds and automatic task inputs
7. **Tests**: written alongside each step, completed and measured here
8. **Docker** files for backend and frontend, full `docker compose up` check
9. **README** and a final clean-up pass
