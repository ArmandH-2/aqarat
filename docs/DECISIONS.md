# Decision record

Every significant choice made while designing Aqarat, what was rejected, and why.

Kept because the reasoning matters more than the conclusion — in a viva you will be asked
"why did you do it this way", and the honest answer is always the alternatives you considered.

---

### 1. JavaFX desktop, not a web application

**Chosen:** JavaFX with FXML and CSS, talking to SQL Server over JDBC.

**Rejected:** Spring Boot with Thymeleaf; a REST API with a JavaScript front end; a Java API
with a JavaFX client.

**Why:** The assignment's main language is Java and the accepted precedent at this university
is a JavaFX desktop app. With two weeks and two people, matching a proven format and executing
it better is a stronger bet than learning a new framework mid-sprint. "Enterprise level" comes
from the architecture and the workflow, not from running in a browser.

---

### 2. Name: Aqarat

**Chosen:** Aqarat, from عقارات — the plural, "properties".

**Rejected:** Cadastra (a cadastre is an official property register — the most on-brand option,
but less immediately understood); Domora; Tenura; Locare; Estatra (too derivative of the
reference project).

**Why:** Locally rooted and immediately meaningful to the intended market. The singular form
"Aqara" was avoided because it collides with a well-known smart-home brand, which matters for
a public repository.

---

### 3. Owners submit properties; staff review and publish

**Chosen:** An owner registers, submits a property, and it enters a review queue. An agent
approves, rejects, or requests more information.

**Rejected:** Staff-only entry, where owners never log in; a self-serve marketplace where
owners publish directly.

**Why:** This is how real agencies actually work, so it is more defensible than borrowing
mechanics from a different industry. It also gives the price estimation feature a place inside
a workflow rather than being a calculator with a button. Direct self-publishing was rejected
because it removes the agency — and the agency is the entity that the required Contracts and
Payments modules belong to.

---

### 4. Sale and long-term lease only

**Chosen:** Two deal types. Lease carries a minimum and/or maximum term in months, set by the
owner and validated when a contract is drafted.

**Rejected:** Adding short-term nightly rental.

**Why:** Short-term rental is effectively a fifth module — it needs a calendar, date-range
availability, and overlapping-booking prevention, with a different pricing model. Long-term
lease is what makes the Payments module interesting anyway, because recurring installments beat
one-off transactions.

---

### 5. The owner sets the price; the system only flags implausibility

**Chosen:** The owner's asking price is the listed price. The valuation produces an estimate
and a range, and classifies the asking price as `OK`, `ABOVE_MARKET` or `IMPLAUSIBLE`. The flag
is advisory — an agent can publish anyway, and that decision is recorded.

**Rejected:** The agent proposes a price the owner must accept; the system sets the price.

**Why:** An agency does not own the property and cannot price it. More importantly, a model
trained on a few thousand rows should not be trusted to name a number — but it can absolutely
be trusted to say "this is nowhere near anything comparable". Outlier detection with a
defensible threshold is honest; "the AI decided your house is worth $118,000" is not.

This reframing is what turns a regression into a decision-support signal, and it is the single
most important design decision in the project.

---

### 6. Reservation with a deposit, no offers

**Chosen:** Between "the client wants it" and "there is a contract" sits a reservation: a
deposit, a named client, and an explicit expiry date. It lapses on its own.

**Rejected:** Going straight from viewing to contract; adding a full offer and counter-offer
negotiation.

**Why:** Without a reservation there is nothing marking a property as spoken for, so two agents
can draft contracts on the same unit and the property status machine has nothing to do. Offers
were rejected as scope: an entire negotiation sub-system with its own states, screens and
notification flow, spent on haggling rather than on the four required modules.

---

### 7. Browsing never locks anything

**Chosen:** No hold is placed on a property while anyone looks at it. The only short-lived hold
in the system is on viewing appointment slots, and it is enforced by a filtered unique index on
confirmed viewings rather than a separate lock table.

**Rejected:** Copying the airline seat-map pattern of a short TTL soft-lock on the item being
viewed.

**Why:** The airline pattern exists for a specific shape of problem — perishable inventory, a
decision window of minutes, high contention, and a transaction completed in one sitting. Real
estate is the opposite: decisions take weeks, contention is low, and closing takes weeks. A TTL
lock on a property would be the wrong tool. The real-estate equivalent of a hold is a
reservation with a deposit, which is a business object rather than a technical one — visible,
auditable, and on a human timescale.

Appointment slots *are* the airline shape, so that is where the hold lives. Redis was rejected
as unnecessary at this scale; a `WHERE status = 'CONFIRMED'` filtered index does the same job in
one line of DDL.

---

### 8. One account, role derived from relationship

**Chosen:** Three stored roles — `ADMIN`, `AGENT`, `CUSTOMER`. Whether a customer is an owner
or a client is answered by whether they have properties or contracts.

**Rejected:** Separate Owner and Client account types chosen at signup.

**Why:** The same person sells one apartment and rents another. A fixed type at signup means
that person needs two accounts and appears twice in the database, which is the kind of thing
anyone who knows data modelling will notice immediately.

---

### 9. Simple role checks, not a permission table

**Chosen:** A `role` column on the user and a small `hasPermission` helper.

**Rejected:** A permission table with role-to-permission mapping and `OWN`/`ALL` scoping.

**Why:** This was designed first and then cut. Six tables of access-control machinery for a
three-role application is architecture for its own sake. The permission model was the right
answer to a question nobody was asking.

---

### 10. Property and Listing merged into one table

**Chosen:** A single `property` table holding both the physical facts and the current offer.

**Rejected:** Splitting `property` (permanent, physical) from `listing` (one offer to sell or
rent, with its own dates and status).

**Why:** The split is genuinely more correct — it survives relisting and preserves history for
the valuation model to train on. It was cut because nothing gets relisted inside a two-week
demo, and it adds a join to nearly every query in the system. This is the decision most worth
revisiting if the project continues.

---

### 11. Statuses as CHECK constraints, not lookup tables

**Chosen:** `status VARCHAR(20) CHECK (status IN (...))`, mapped to a Java enum whose constant
names match the constraint strings exactly.

**Rejected:** A `status_type` table with foreign keys, as the reference project used.

**Why:** Readable in the database, no join needed to resolve a status, and the Java enum is a
single source of truth. Lookup tables would add five or six small tables and a join to every
read query, in exchange for normalization nobody benefits from here.

---

### 12. Local SQL Server, not Azure

**Chosen:** Local SQL Server managed through SSMS, with `schema.sql` and `seed.sql` committed
to the repository.

**Rejected:** Azure SQL, as the reference project used.

**Why:** Azure means the demo depends on network access and live credentials on the day.
Local plus committed scripts means anyone can stand the system up in two minutes and nothing
can fail in front of the class.

---

### 13. Price estimation in pure Java; the assistant deferred

**Chosen:** The price estimator is plain Java — comparables plus a linear regression, no
external service, no API key. It ships in the first two weeks. The RAG assistant and the
AI-guided intake chat are designed but deferred to phase two.

**Rejected:** A Python sidecar for the ML; building all AI features up front.

**Why:** The estimator is the AI feature the assignment actually requires, and it is
mathematics rather than a language model, so it has no runtime dependency. A Python service
would add a second runtime the grader must install and would mean the interesting logic is not
Java. The assistant was deferred on the reasoning that the app should exist before its
conversational layer is designed — and it costs nothing, because its three tables touch no
existing table.

---

### 14. Synthetic seed data

**Chosen:** A generated dataset of a few thousand realistic Lebanese properties, produced by
`seed.sql` from district-level price-per-m² figures plus adjustments and noise.

**Rejected:** Sourcing a real open dataset; training only on contracts closed inside the app.

**Why:** A real dataset would not match the Lebanese market and cleaning it would consume days.
Training only on the app's own history means roughly twenty rows at demo time and useless
estimates. Synthetic data is honest as long as the README says so, and it makes every screen
look like a working agency rather than a test fixture.

---

### 15. One shell window, not twenty

**Chosen:** A single `MainShell.fxml` with a sidebar and a content area. Every panel is a small
FXML loaded into that area. Only Login and Register are separate windows.

**Why:** Twenty independent windows would each need their own navigation, menu and lifecycle.
One shell reduces a panel to about sixty lines of FXML and a controller, which is the
difference between this being buildable in two weeks and not.

---

### 16. Services own transactions; DAOs take a Connection

**Chosen:** Every DAO method takes `Connection` as its first parameter. The service opens it,
disables auto-commit when more than one write is involved, commits, and rolls back on failure.

**Why:** Activating a contract must update the property status, insert every schedule row, and
write an audit entry as one atomic operation. Passing the connection down is the simplest
mechanism that makes this correct, needs no framework, and can be explained in thirty seconds.
It is the strongest single piece of evidence that this is not a form generator.

---

### 17. Scope: staff and owner deep, client and guest light

**Chosen:** Agent and admin panels fully built. Owner side complete. Client gets contracts,
payments and activity. Guest gets browse and search.

**Rejected:** All actors built evenly; staff-only with everything else documented as designed.

**Why:** Screens, not tables, are the real budget — thirty small tables with straightforward
DAOs are a lot of typing and almost no thinking, but twenty panels each need layout, wiring,
validation, error states and styling. Spending them where the required modules live is the
correct trade. The schema stays complete regardless, which costs nothing and means the ER
diagram shows the real design.

---

### 18. Agents claim submissions; nobody assigns them

**Chosen:** A submitted property arrives unassigned. Any agent can claim it, which moves it
into their queue. An admin can reassign.

**Rejected:** A manager assigning submissions to agents.

**Why:** Follows directly from merging the manager role into admin. Claiming needs no approval
screen, no notification, and no bottleneck — one button and one `UPDATE`.

---

### 19. Nothing expires on a timer

**Chosen:** Overdue payments and lapsed reservations are computed at read time from
`due_date` / `expires_at` against the current date. Status columns are corrected the next time
the row is touched.

**Rejected:** A background thread or scheduled job that sweeps for expiries.

**Why:** A timer thread in a desktop application is a reliable source of bugs, and it would be
wrong anyway — if the app has been closed for a month, the sweep never ran, but the payments
were still overdue the whole time. Computing at read is always correct and needs no
infrastructure. This is the same reasoning that made a Redis-backed TTL lock unnecessary for
viewing slots.

---

### 20. English only

**Chosen:** English interface and English data. `VARCHAR`, not `NVARCHAR`.

**Why:** An Arabic interface would mean right-to-left layout work in JavaFX, bidirectional text
handling, and a translation layer, none of which is what this project is being marked on.
Switching later means changing the column types and adding a resource bundle, which is a
contained change.
