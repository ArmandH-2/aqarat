# Aqarat

Real estate management system for a Lebanese agency — properties, clients, contracts,
payments, and an explainable price estimator.

JavaFX desktop application, SQL Server, Java 21.

![The review screen](screenshots/03-review-submission.png)

---

## What it does

An owner submits a property. The system values it and tells the owner where their asking price
sits against comparable listings. An agent reviews the submission, publishes or rejects it.
Clients browse, book a viewing, reserve with a deposit, and sign a contract. Payments run on a
generated schedule until the deal closes.

The idea holding it together is that **every number can be traced to its source.**

- The estimator does not return a price. It returns an estimate, a confidence range, the
  factors that moved it, and the comparable properties it reasoned from.
- Every write records who did it, when, and what changed.
- The system advises. A person decides. Both are recorded.

The owner sets their own asking price. The estimator's job is not to price the property — it is
to answer whether the owner's number is believable, and to flag the ones that are not. An agent
may publish a listing the estimator called implausible; a model trained on a few thousand rows
does not get a veto over a real person's property.

## Screens

| | |
|---|---|
| ![Sign in](screenshots/01-login.png) | ![Discover](screenshots/02-browse-listings.png) |
| Sign in, or browse without an account | Describe what you want in plain English, or filter |
| ![Property details](screenshots/07-property-details.png) | ![Submit a property](screenshots/09-submit-valuation.png) |
| Photographs, price and specification for one listing | The estimate appears while you are still deciding a price |
| ![Portfolio](screenshots/08-portfolio.png) | ![Review queue](screenshots/04-review-queue.png) |
| Everything you own, with what needs you on top | Claim submissions from the unassigned queue |
| ![Contracts](screenshots/05-contracts.png) | ![Reports](screenshots/06-reports.png) |
| Draft a contract, then activate it | Revenue and commission by month |

Sixteen panels behind a role-aware sidebar. A customer navigates three: Discover,
Portfolio and Submit. An agent sees the pipeline on top of Discover, and an admin sees
everything plus accounts, reference data, reports and the audit log. A guest gets Discover
alone - the whole catalogue and every filter, without an account.

## Features

**Properties** — owner submission, agent review queue, publication, and a controlled status
lifecycle from draft through to closed. `property.status` moves only through `PropertyService`;
no screen writes the column.

**Clients** — one account per person. Whether someone is an owner or a client is answered by
their relationships, not by a column, so the person selling one apartment and renting another
has a single account.

**Price estimation** — comparable properties blended with a multiple linear regression, both
written in plain Java. No external service, no API key, no network. Produces a range and a
plausibility flag rather than a single authoritative number, with the comparables it used
shown beside it.

**Viewings and reservations** — a client requests, an agent confirms. One agent cannot hold two
confirmed viewings at the same moment, and one property cannot hold two active reservations —
both enforced by filtered unique indexes as well as by the service layer.

**Contracts** — sale and long-term lease, with term validation against the owner's minimum and
maximum. Activation runs as a single transaction: property status, generated payment schedule,
commission, and audit entry, all or nothing.

**Payments** — an installment schedule per contract, overdue detection, client-declared payments
with proof, agent confirmation, and receipts. A schedule sums to exactly the contract total; the
rounding remainder goes on the last row rather than quietly disappearing.

**Audit trail** — every create, update and delete, with before and after values, filterable and
exportable to CSV.

**Search assistant** — describe what you want in plain English and it finds matching listings,
explains why each one fits, and refines as you go: "cheaper", "add parking", "does the second one
have an elevator?". The model's only job is turning the sentence into filter arguments; Java runs
the search and every figure on a card is read from the database. It suggests and explains, and
writes nothing.

It needs an OpenAI-compatible API key in `config/local.properties`. **The application runs fully
without one** — leave `ai.enabled=false` and the assistant simply does not appear. Note that when
it is enabled, what you type in the conversation is sent to whichever provider you configured.

## Two things decided deliberately

**There is no scheduler.** A reservation past its expiry and a payment past its grace period are
decided when something *reads* the row, not by a timer. A background thread in a desktop
application is a source of bugs nobody needs, and the read-time answer is still correct after the
application has been closed for a month.

**The valuation package touches no database.** `PriceEstimator` takes a list of comparable
properties and returns a result; `ValuationService` is what queries and what saves. That is why
its tests run with no connection at all.

## Tech

| | |
|---|---|
| Language | Java 21 |
| UI | JavaFX 21, FXML, one stylesheet |
| Database | SQL Server |
| Driver | mssql-jdbc |
| Pooling | HikariCP |
| Passwords | BCrypt |
| Build | Maven |

No Spring, no Hibernate, no Lombok, no ORM. Roughly ninety classes across `model`, `dao`,
`service`, `controller`, `valuation` and `util`.

## Running it

You need Java 21, Maven, and a local SQL Server.

**1. Create the database.**

```bash
sqlcmd -S "localhost\SQLEXPRESS" -E -C -I -i db/schema.sql
sqlcmd -S "localhost\SQLEXPRESS" -E -C -I -d Aqarat -i db/seed.sql
```

The `-I` matters. It turns on `QUOTED_IDENTIFIER`, which the filtered unique indexes need;
without it `schema.sql` fails partway through with a message about SET options.

The seed prints its row counts at the end. You should see 2,000 properties.

**2. Point the application at it.**

```bash
cp config/local.properties.example config/local.properties
```

Then fill in your server, database and credentials. That file is gitignored and never
committed. If SQL Server is on a named instance with dynamic ports, either give the instance a
static port or put the dynamic one in the URL — the example assumes `localhost:1433`.

**3. Run it.**

```bash
mvn javafx:run
```

Sign in as `admin@aqarat.local` / `Password123!`, as a customer with
`user1@example.com` / `Password123!`, or use "Browse listings as a guest".

**Tests:**

```bash
mvn test
```

Fifteen tests, covering the two places where correctness is not visible by clicking: the price
estimator, and payment schedule generation.

## An honest note on the data

**The dataset is synthetic.** `db/seed.sql` generates 2,000 properties, 255 users, 489 contracts
and their payment histories from scratch. The districts and their average prices per m² are
plausible for Lebanon; everything else — the addresses, the names, the prices — is generated.

That matters for the estimator. Measured against the seeded closed properties, with each subject
excluded from its own comparables and from its own training set, the median error is about **12%**
on sales and **12%** on rentals, with roughly four in five predictions inside 25%. That is well
inside the range the design expected, but it is a model fitted to synthetic data and it should be
read as a demonstration of the method, not as a valuation anyone should trade on. The tail is
still long — the worst sale in the sample is out by more than a factor of two.

The property photos are generated illustrations, not photographs, written into `uploads/` — which
is gitignored, because uploaded files are runtime data rather than source. A fresh clone shows a
caption tile in their place until they are regenerated.

The seed also stops valuing properties once they are published, so the time-on-market report has
nothing to compare against until properties are valued and closed through the application
itself. The report is correct and returns rows the moment that data exists; it is empty on a
fresh seed by construction, not by fault.

## Documents

| | |
|---|---|
| [`docs/DESIGN.md`](docs/DESIGN.md) | What the system is, and why |
| [`docs/DIAGRAMS.md`](docs/DIAGRAMS.md) | Use case, entity, class and lifecycle diagrams |
| [`docs/BUILD-ORDER.md`](docs/BUILD-ORDER.md) | The order it was built in, phase by phase |
| [`docs/UI-STYLE.md`](docs/UI-STYLE.md) | The palette, spacing and components |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Decisions taken and the alternatives rejected |
| [`docs/ai-agent/`](docs/ai-agent/README.md) | The search assistant: requirements, diagrams, tasks |
| [`CLAUDE.md`](CLAUDE.md) | Coding conventions |

---

Built as a Java training assignment. Published under Syntropy.
