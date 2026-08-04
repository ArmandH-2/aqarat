# Aqarat

Real estate management system for a Lebanese agency — properties, clients, contracts,
payments, and an explainable price estimator.

JavaFX desktop application, SQL Server, Java 21.

<!-- Replace with a screenshot of ReviewSubmission once it exists. -->

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
to answer whether the owner's number is believable, and to flag the ones that are not.

## Features

**Properties** — owner submission, agent review queue, publication, a controlled status
lifecycle from draft through to closed.

**Clients** — one account per person. Whether someone is an owner or a client is answered by
their relationships, not by a column, so the person selling one apartment and renting another
has a single account.

**Contracts** — sale and long-term lease. Term validation against the owner's minimum and
maximum. Activation runs as a single transaction: property status, generated payment schedule,
commission, and audit entry, all or nothing.

**Payments** — an installment schedule per contract, overdue detection, client-declared
payments with proof, agent confirmation, and receipts.

**Price estimation** — comparable properties blended with a multiple linear regression, both
written in plain Java. No external service, no API key, no network. Produces a range and a
plausibility flag rather than a single authoritative number.

**Audit trail** — every create, update and delete, with before and after values.

## Tech

| | |
|---|---|
| Language | Java 21 |
| UI | JavaFX, FXML, CSS |
| Database | SQL Server |
| Driver | mssql-jdbc |
| Pooling | HikariCP |
| Passwords | BCrypt |
| Build | Maven |

No Spring, no ORM, no code generation. Every SQL statement in the project was written by hand
as a prepared statement.

## Running it

**1. Database**

Open `db/schema.sql` in SQL Server Management Studio and execute it, then `db/seed.sql`.
The seed prints row counts when it finishes.

**2. Configuration**

Copy `config/local.properties.example` to `config/local.properties` and set your connection
details:

```properties
db.url=jdbc:sqlserver://localhost:1433;databaseName=Aqarat;encrypt=true;trustServerCertificate=true
db.user=sa
db.password=your-password
```

This file is gitignored. No credentials are committed.

**3. Run**

```
./mvnw javafx:run
```

**4. Sign in**

| Role | Email | Password |
|---|---|---|
| Admin | `admin@aqarat.local` | `Password123!` |
| Agent | `rami@aqarat.local` | `Password123!` |
| Customer | `user1@example.com` | `Password123!` |

## About the data

**The seeded dataset is synthetic.** Two thousand properties are generated from district-level
price-per-m² figures, adjusted for type, size, age, floor and features, with noise applied. The
district figures are indicative of the Lebanese market; the individual properties are not real.

This is a deliberate choice, not a shortcut. Training the estimator only on deals closed inside
the application would mean roughly twenty rows and useless estimates. Generated data makes the
model demonstrable and every screen legible.

A minority of the seeded listings are deliberately overpriced, so the review queue contains
genuine `ABOVE_MARKET` and `IMPLAUSIBLE` cases to look at.

## Architecture

```
FXML panels
    |
controller      one per panel, never any SQL
    |
service         business rules, owns transactions   ->   valuation
    |
dao             prepared statements only
    |
SQL Server      13 tables
```

Three rules are enforced throughout: controllers contain no SQL, DAOs contain no business
rules, and services own transactions. Every DAO method takes a `Connection` as its first
parameter so that a service can span several of them in one atomic operation.

Two integrity rules live in the database rather than only in code, as filtered unique indexes:
one active reservation per property, and no two confirmed viewings for the same agent at the
same moment.

## Documentation

| | |
|---|---|
| [`docs/DESIGN.md`](docs/DESIGN.md) | Full specification — actors, use cases, lifecycle, data model, screens |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Every significant decision, what was rejected, and why |
| [`docs/BUILD-ORDER.md`](docs/BUILD-ORDER.md) | Phased build plan |
| [`CLAUDE.md`](CLAUDE.md) | Coding conventions |

## Not built yet

A retrieval-based customer service assistant and an AI-guided submission chat are designed in
`docs/DESIGN.md` section 10 but are not implemented. They add three tables that touch nothing
existing, so they can be added without disturbing the schema.

They are listed here rather than quietly omitted, because a README that describes features that
do not exist is worse than one that is honest about scope.

## Status

University coursework. Not production software, and not intended to be.

## Credits

Built by a team of two as a Java training project.
Published under [Syntropy](https://syntropyhq.co).
