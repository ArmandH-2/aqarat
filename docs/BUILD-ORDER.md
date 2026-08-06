# Build order

Two people, fourteen days. Phases are ordered by dependency, not by preference — each one
needs the one before it.

Two tracks run in parallel. They are drawn so that the two of you almost never edit the same
file. Where a phase says "both", stop and sync before continuing.

---

## Ground rules for working with Claude Code

**One phase per session.** Not "build the app". A session should end with something that
compiles and runs.

**Point it at the design every time.** Start each session with: *read `CLAUDE.md` and
`docs/DESIGN.md`, then build phase N as described in `docs/BUILD-ORDER.md`.* Without that it
will invent its own structure and the two tracks will drift apart.

**Ask for one vertical slice at a time.** "The `PropertyDao`, `PropertyService` and
`BrowseListingsController` for browsing published listings" is a session. "The properties
module" is not.

**Read the diff before committing.** If a file contains something you could not explain to
an examiner, delete it and ask again. This is the whole reason `CLAUDE.md` exists — and it
only works if you actually enforce it.

**Never let it touch the schema.** If it wants a column that does not exist, that is a design
conversation, not a migration.

---

## Phase 0 — Standing it up (day 1, both) — **DONE**

Nobody writes application code until this works.

- Create the SQL Server database, run `db/schema.sql`, then `db/seed.sql`.
- Confirm the row counts printed at the end of the seed look right.
- `pom.xml` is already written with every version pinned. Do not change the versions to fix a
  build problem — if it will not resolve, say so rather than upgrading something.
- `.gitignore` is already written.
- Copy `config/local.properties.example` to `config/local.properties` and fill it in.
- `util/Db` — Hikari pool from a properties file, `Db.get()` returning a `Connection`.
- A throwaway main that opens a connection, counts properties, prints the number, exits.
- `App.java` opening an empty JavaFX window.
- First commit. Both of you clone it and confirm it runs on both machines.

**Done when:** both machines print `2000` and show a window.

---

## Phase 1 — Foundations (days 2–3) — **DONE**

### Track A
- `model/` — all thirteen POJOs and all thirteen enums, exactly as listed in
  `docs/DESIGN.md` section 8.
- `util/Format` — USD and m² formatting, date formatting.
- `util/AlertUtil` — info, error and confirm dialogs.

### Track B
- `util/SessionManager` — current user, `isAdmin()`, `isAgent()`, `isCustomer()`.
- `util/PasswordUtil` — BCrypt hash and verify.
- `dao/UserDao`, `service/AuthService` — login, register, find by id.
- `Login.fxml`, `Register.fxml` and their controllers.

### Both, at the end
- `MainShell.fxml` — sidebar plus content area.
- `util/Router` and the `Panel` enum, exactly as specified in `CLAUDE.md`. Build this together,
  in one sitting. It is the shared surface both tracks depend on for the next five phases.
- Sidebar items shown or hidden by role.
- `resources/css/app.css` — built from the skeleton at the end of `docs/UI-STYLE.md`.
  Do this once, properly, before any panel exists. Restyling twenty panels afterwards is
  the single most avoidable way to lose two days.

**Done when:** you can log in as `admin@aqarat.local` / `Password123!` and land in a shell
with a role-appropriate sidebar and an empty content area.

This is the riskiest sync point in the project. Do not start phase 2 until both tracks are
merged and the shell works.

---

## Phase 2 — Properties (days 4–6) — **DONE**

### Track A — the public side
- `dao/PropertyDao` — search with filters, find by id, find by owner, find by agent.
- `dao/DistrictDao`, `dao/PropertyTypeDao`, `dao/PropertyPhotoDao`.
- `service/PropertyService` — search, and the status state machine.
- `BrowseListings` panel — search box, filters, result cards.
- `PropertyDetails` panel — gallery, specs, actions that vary by role.

### Track B — the owner side
- `SubmitProperty` panel — the full form, with validation.
- `MyProperties` panel — the owner's submissions, their status, the review note,
  respond to a needs-info request, withdraw.
- `service/AuditService` and `dao/AuditDao` — wire it into every write in `PropertyService`
  as you go, not afterwards.

**Done when:** a guest can browse and filter 2000 seeded properties, and a customer can submit
a new one that lands in `PENDING_REVIEW`.

---

## Phase 3 — Valuation and review (days 6–8) — **DONE**

This phase contains the two things the project is actually judged on. Spend the time.

### Track A — the engine
- `valuation/ValuationResult` — estimate, bounds, price per m², factor contributions,
  comparables used.
- `valuation/LinearRegression` — normal equations. Roughly sixty lines.
- `valuation/PriceEstimator` — comparables plus regression, blended, with a range.
  Takes a list, returns a result, touches no database.
- `service/ValuationService` — fetches comparables, calls the estimator, computes the flag
  against the thresholds in `system_setting`, saves the row.
- `dao/ValuationDao`.

### Track B — the screen
- `ReviewQueue` panel — unassigned submissions, claim, and the agent's own queue.
- `ReviewSubmission` panel — **the centrepiece.** Property and photos on one side; asking
  price, estimate, range, flag, factor breakdown and comparables on the other. Approve,
  request more information, reject with a reason.
- `AgentDashboard` panel.

**Done when:** opening a seeded `PENDING_REVIEW` property shows a live estimate with its
comparables, and the deliberately inflated ones come up flagged.

Verify the estimator separately before wiring it in: feed it a hundred `CLOSED` properties,
predict each one, and check the average error. If it is worse than about 25% something is
wrong with the features, not with the model.

---

## Phase 4 — Viewings and reservations (days 8–9) — **DONE**

### Track A
- `dao/ViewingDao`, `service/ViewingService` — request, confirm, cancel, record outcome.
- `Viewings` panel for agents.
- Handle the unique-index violation on a double-booked slot as a readable error, not a stack
  trace. This is the one place the database will refuse you, and it is worth demonstrating.

### Track B
- `dao/ReservationDao`, `service/ReservationService` — create with deposit, lapse on read,
  cancel, convert.
- `MyActivity` panel for clients.
- Property status transitions to and from `RESERVED`.

**Done when:** a client can request a viewing, an agent can confirm it, and a reservation
moves a property to `RESERVED` and lapses correctly once its expiry passes.

---

## Phase 5 — Contracts and payments (days 9–12) — **DONE**

The longest phase. Do not start it late.

### Track A — contracts
- `dao/ContractDao`, `service/ContractService`.
- Draft validation: property is available or reserved by this client, lease term inside the
  owner's minimum and maximum, dates coherent.
- `ContractService.activate()` — the single transaction. Property status, schedule generation,
  commission, audit entry. All or nothing.
- `Contracts` panel — list plus the draft-and-activate editor.

### Track B — payments
- `dao/PaymentScheduleDao`, `dao/PaymentDao`, `service/PaymentService`.
- Schedule generation from term and frequency.
- Overdue computed on read against the grace period.
- Declare with proof, confirm, reject.
- `Payments` panel for agents, `MyContracts` panel for clients.
- Receipt output.

**Done when:** you can take a seeded `AVAILABLE` property from reservation through an activated
contract to a confirmed payment, and the audit log shows every step.

---

## Phase 6 — Admin and reports (days 12–13) — **DONE**

### Track A
- `Users` panel, `Reference` panel (districts, types, settings).
- `Listings` panel. It is in `docs/DESIGN.md` section 9 but was in no track here,
  so it was built in this phase — all listings across every status, filtered,
  with an agent's take-down.

### Track B
- `service/ReportService`, `Reports` panel — revenue and commission by period, overdue
  payments, and time on market against how far above the estimate a property was listed.
- `AuditLog` panel with filters.

**Done when:** an admin can change the commission rate in the UI and the next contract drafted
uses it.

---

## Phase 7 — Finishing (days 13–14, both) — **DONE**

This is not optional padding. It is where the marks are.

- `README.md` — what it is, screenshots, how to run it, and an honest note that the dataset
  is synthetic.
- Screenshots of the six best screens, committed under `screenshots/`.
- The four diagrams exported into `docs/`: use case, ER, class, and the lifecycle flow.
- Delete every scratch file, test main, and commented-out block.
- Read every file once. Anything you cannot explain, rewrite or remove.
- Check the repository root is clean — this is the first thing anyone sees.

---

## If you fall behind

Cut in this order, and say in the README that you cut them deliberately:

1. `Reports`
2. `AuditLog` panel (keep the table and the writes — just drop the viewer)
3. `Reference` panel (settings stay in the database, edited through SSMS)
4. `MyActivity`
5. Viewings entirely (phase 4 track A)

Do not cut: the review queue, the valuation, contract activation, or the payment schedule.
Those four are the project.
