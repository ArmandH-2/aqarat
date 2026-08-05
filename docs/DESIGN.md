# Aqarat — design document

Real estate management system for a Lebanese agency.
JavaFX desktop application, SQL Server database, Java 21.

Built as a Java training assignment. Published under Syntropy.

---

## 1. Purpose

Aqarat manages the full life of a property inside a real estate agency: an owner submits it,
an agent reviews and publishes it, a client views and reserves it, a contract is signed, and
payments are tracked until the deal closes.

Its distinguishing idea is that **every number and every answer can be traced to its source.**

- The valuation does not return a price. It returns an estimate, a confidence range, the
  factors that moved it, and the comparable properties it reasoned from.
- Every create, update and delete writes an audit entry recording who, what, when, and the
  before and after values.
- The AI advises. A human decides. Both are recorded.

That last line is a hard constraint, not a slogan. Nothing in this system takes a decision
away from a person.

## 2. Market assumptions

- Lebanon. Districts are grouped by governorate.
- All prices in **USD**.
- All areas in **m²**.
- Two deal types: **sale** and **long-term lease**. No short-term or nightly rental.
- An owner may constrain lease length with a minimum and/or maximum term in months.
- Interface language is English only.

## 3. Scope

**In scope**

Properties, Clients, Contracts, Payments — the four required modules.
Owner submission and staff review pipeline.
Property price estimation with plausibility flagging.
Viewings and reservations.
Reporting and audit trail.

**Deferred to phase two** (designed, not built in the first two weeks)

RAG customer service assistant.
AI-guided listing intake chat.

These are described in section 10. They add three tables that touch nothing existing, so
deferring them costs nothing.

**Explicitly out of scope**

Offers and counter-offers. Multi-agency tenancy. Short-term rental and calendars.
Mortgage or financing calculation. Maps and geolocation. Email or SMS notification.
Mobile or web clients.

## 4. Actors

| Actor | Description |
|---|---|
| **Guest** | Not logged in. Browses and searches published listings. |
| **Customer** | One account. Acts as an owner when they have properties, as a client when they have contracts. The same person can be both. |
| **Agent** | Reviews submissions, manages listings and clients, drafts contracts, records payments. |
| **Admin** | User accounts, reference data, system settings, audit log, reports. |

There are three stored roles: `ADMIN`, `AGENT`, `CUSTOMER`. Whether a customer is "an owner"
or "a client" is answered by their relationships, not by a column. This means the person who
sells one apartment and rents another has one account, which is how it works in reality.

## 5. Use cases

### Guest

Browse published listings. Search and filter by district, price, type, bedrooms, area and deal
type. View listing details. Register an account.

Guests cannot see owner identity, internal notes, valuations, or contact details.

### Customer, acting as owner

Submit a property. Attach photos. Set asking price, deal type and lease term limits. View the
system valuation and its comparables. Track submission status. Respond to a request for more
information. Withdraw a submission. Reprice a live listing. View own listings and their
activity. View contracts on own property.

### Customer, acting as client

Search and browse. Request a viewing. Cancel or reschedule a viewing. Reserve a property and
pay a deposit. View reservation and its expiry. View own contracts. View payment schedule and
outstanding balance. Declare a payment with proof. Download receipts.

Saved favourites were cut along with their table. Do not reintroduce them without adding the
table back to `schema.sql` and to section 8.

### Agent

View review queue. Review a submission against its valuation, flag and comparables. Request
more information. Reject with a reason. Approve and publish. Edit, unpublish or archive a
listing. Manage own clients. Confirm or decline viewing requests. Record viewing outcomes.
Create a reservation and record its deposit. Cancel a lapsed reservation. Draft a sale or lease
contract. Activate a contract. Record payments and confirm client-declared ones. Issue
receipts. Run a standalone valuation. Close or terminate a contract. View own pipeline and
commission.

### Admin

Everything an agent can do, plus: create, deactivate and reset accounts. Assign roles. Manage
districts, property types and system settings. View, filter and export the audit log. View
agency-wide revenue, commission and overdue reports.

## 6. Property lifecycle

```
                    owner submits
                          |
                  system values it   ->  estimate, range, flag
                          |
                  owner sees estimate
                          |
                    PENDING_REVIEW
                          |
                    agent reviews
                    /     |      \
            NEEDS_INFO  REJECTED  AVAILABLE   (published)
                 |                     |
            back to owner        guest browses
                                       |
                              client requests viewing
                                       |
                                  agent confirms
                                       |
                            client reserves, pays deposit
                                       |
                                   RESERVED
                                       |
                                contract drafted
                                       |
                            contract activated   ->   one transaction:
                                       |               status flips,
                              UNDER_CONTRACT           schedule generated,
                                       |               commission computed,
                             payments recorded         audit written
                                       |
                                    CLOSED
```

Loops that are part of the design and must be built: a review bouncing back as `NEEDS_INFO`,
a rejection with a reason, a viewing cancelled or marked no-show, a reservation lapsing when
`expires_at` passes, and a lease reaching `end_date` and returning the property to `AVAILABLE`.

**Taking a published listing down.** An owner may withdraw a submission that is still in
review on their own. Once a listing is live, they ask instead: the property moves to
`WITHDRAWAL_REQUESTED` and appears in the review queue, and an agent either accepts it —
`WITHDRAWN` — or declines and it returns to `AVAILABLE`. The listing stays visible and
reservable while the request is outstanding, because nothing has been decided yet. The owner
has the right to have it removed; the review exists so the agency sees it happen rather than
finding out afterwards.

`property.status` is a controlled state machine. Transitions happen only through
`PropertyService`. No screen writes the status field directly.

### Assignment, commission, and things that expire

**Assignment.** A submitted property arrives with `agent_id` null. Any agent may claim it from
the unassigned queue, which sets `agent_id` and moves it into their review queue. An admin can
reassign. There is no manager approval step — that role was merged into admin.

**Commission.** `commission_rate` is copied onto the contract from `system_setting` at draft
time, so changing the agency rate later does not rewrite history. For a sale it applies to the
sale price. For a lease it applies to the total lease value, meaning `monthly_rent` multiplied
by `term_months`. `commission_amount` is computed once, on activation, and stored.

**Overdue payments.** A schedule row is overdue when `due_date` plus the grace period from
`system_setting` has passed and `amount_paid < amount_due`.

**Reservation lapse.** A reservation whose `expires_at` has passed is lapsed.

Both of the above are **evaluated on read**, not by a background job. There is no scheduler in
this system and none should be added. `PaymentService` and `ReservationService` write the
status change the next time the row is touched, and every query that displays status computes
it correctly regardless. This is deliberate: a timer thread in a desktop application is a
source of bugs nobody needs, and the read-time computation is always correct even if the app
has been closed for a month.

## 7. Price estimation

The owner sets the asking price. The system does **not** price the property. Its job is to
answer whether the owner's number is believable.

### Method

Two estimates, blended.

**Comparables.** Find properties in the same district, of the same type, within plus or minus
30% area, that have closed. Take the median price per m², adjust for bedroom count, floor, age
and condition, multiply by area.

**Regression.** Multiple linear regression over the closed-property dataset, using area,
bedrooms, bathrooms, floor, building age, district average price per m², and the boolean
features. Solved with normal equations in `LinearRegression`.

The two are averaged. The confidence range is derived from the spread of the comparables:
wide spread, wide range.

### Output

`ValuationResult` carries the estimate, lower and upper bounds, price per m², the contribution
of each factor, and the list of comparables used with their similarity scores.

### Flag

Computed by comparing the asking price to the range.

| Condition | Flag |
|---|---|
| Asking price inside the range | `OK` |
| Outside the range but within the implausibility threshold | `ABOVE_MARKET` |
| Beyond the threshold in either direction | `IMPLAUSIBLE` |

The threshold lives in `system_setting`, not in code.

The flag is advisory. An agent may publish a listing flagged `IMPLAUSIBLE`, and their decision
is recorded. A model trained on a few thousand rows does not get a veto over a real person's
property. It does, however, catch the villa listed at one dollar.

## 8. Data model

Thirteen tables. Full DDL in `db/schema.sql`.

| Table | Purpose |
|---|---|
| `app_user` | Accounts. Three roles. |
| `district` | Reference. Carries `avg_price_per_sqm`, used by the estimator and the seeder. |
| `property_type` | Reference. |
| `property` | The listing. Owner, agent, specs, asking price, term limits, status. |
| `property_photo` | Many per property. |
| `valuation` | History of estimates for a property. Estimate, bounds, flag, breakdown, comparables. |
| `viewing` | A client's request for a visit, and its outcome. |
| `reservation` | Deposit and expiry date. One active per property. |
| `contract` | Sale or lease. Parties, terms, commission, status. |
| `payment_schedule` | One row per installment. Due date, amount, status. |
| `payment` | An actual transaction. Declared by a client, confirmed by an agent. |
| `audit_log` | Who changed what, before and after. |
| `system_setting` | Commission rate, thresholds, durations. Nothing hardcoded. |

One column carries two meanings, and it must be documented everywhere it is used:
**`property.asking_price` is the full sale price when `deal_type = 'SALE'`, and the monthly
rent when `deal_type = 'RENT'`.** The alternative was two nullable columns, which is worse —
every read would have to coalesce them. Any code touching this column checks `deal_type` first.

Two integrity rules are enforced by the database itself, using SQL Server filtered indexes:

- one `ACTIVE` reservation per property
- no two `CONFIRMED` viewings for the same agent at the same time

Service-layer checks give better error messages. The indexes are the backstop.

Statuses are text columns with `CHECK` constraints, mapped to Java enums whose constant names
match the constraint strings exactly. No lookup tables.

### Enumerations

These names appear in three places and must be identical in all three: the SQL `CHECK`
constraint, the Java enum constant, and the seed data.

| Enum | Values |
|---|---|
| `Role` | `ADMIN`, `AGENT`, `CUSTOMER` |
| `UserStatus` | `ACTIVE`, `INACTIVE` |
| `DealType` | `SALE`, `RENT` |
| `PropertyStatus` | `DRAFT`, `PENDING_REVIEW`, `NEEDS_INFO`, `REJECTED`, `AVAILABLE`, `RESERVED`, `UNDER_CONTRACT`, `CLOSED`, `WITHDRAWAL_REQUESTED`, `WITHDRAWN` |
| `ValuationFlag` | `OK`, `ABOVE_MARKET`, `IMPLAUSIBLE` |
| `ViewingStatus` | `REQUESTED`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW` |
| `ReservationStatus` | `ACTIVE`, `CONVERTED`, `LAPSED`, `CANCELLED` |
| `ContractType` | `SALE`, `LEASE` |
| `ContractStatus` | `DRAFT`, `ACTIVE`, `COMPLETED`, `TERMINATED`, `EXPIRED` |
| `PaymentFrequency` | `ONE_OFF`, `MONTHLY`, `QUARTERLY`, `ANNUAL`, `INSTALLMENT` |
| `PaymentMethod` | `CASH`, `BANK_TRANSFER`, `CHEQUE` |
| `PaymentStatus` | `DECLARED`, `CONFIRMED`, `REJECTED` |
| `ScheduleStatus` | `PENDING`, `PARTIALLY_PAID`, `PAID`, `OVERDUE` |

### Security and reliability

Not a separate section in the code — these are properties the code has to have.

- Passwords are BCrypt hashed. Nothing anywhere stores or logs a plaintext password.
- Every query is a `PreparedStatement`. No string concatenation into SQL, ever.
- Connections come from a HikariCP pool and are always closed with try-with-resources.
- The connection string lives in a config file that is gitignored, not in compiled code.
- Customers can only read their own contracts, payments, reservations and properties. This is
  enforced in the service layer by passing the current user id into the query, not by filtering
  in the controller after fetching everything.

## 9. Screens

One `MainShell.fxml`: sidebar plus content area. Every panel is a small FXML loaded into the
content area by `Router`. The sidebar renders different items per role. `Login` and `Register`
are the only separate windows.

| Panel | Contents |
|---|---|
| `Login` | Email, password, link to register |
| `Register` | Account creation |
| `MainShell` | Sidebar, content area, current user |
| `BrowseListings` | Search, filters, result cards |
| `PropertyDetails` | Gallery, specs, price. Actions vary by role |
| `MyProperties` | Owner's submissions, status, valuation, review note, withdraw |
| `SubmitProperty` | Submission form |
| `MyContracts` | Client's contracts, payment schedule, declare payment, receipts |
| `MyActivity` | Client's viewings and reservations |
| `AgentDashboard` | Queue, active listings, week's viewings, overdue payments |
| `ReviewQueue` | Submissions assigned to this agent |
| `ReviewSubmission` | The centrepiece. Property beside asking price, estimate, range, flag, factor breakdown, comparables. Approve, request info, reject |
| `Listings` | All listings, filter, edit, unpublish, archive |
| `Viewings` | Confirm or decline requests, record outcomes |
| `Contracts` | List plus draft-and-activate editor. Term validation fires here |
| `Payments` | Schedules, declared payments awaiting confirmation, record, receipt |
| `Users` | Accounts, roles, activate, reset |
| `Reference` | Districts, property types, system settings |
| `AuditLog` | Filterable trail, export |
| `Reports` | Revenue and commission by period, overdue, time on market against listing premium |

Twenty panels. If time runs out, the five that go first are `MyActivity`, `AuditLog`,
`Reports`, `Reference` and `Viewings`. The demo still works end to end without them.

Disproportionate polish goes to `ReviewSubmission`, `PropertyDetails` and `MyContracts`.

## 10. Deferred: AI assistant (phase two)

Three additional tables, none of which touch the existing schema:

- `kb_document` — an indexed document
- `kb_chunk` — a chunk of it, with its embedding
- `chat_message` — conversation history, with the sources each answer used

**Hybrid design.** Policy, FAQ, contract-template and tenancy questions are answered from the
indexed documents. Data questions such as "what do I still owe?" are answered by calling a small
set of named, parameterised SQL queries scoped to the logged-in user. Pure retrieval is weak at
structured questions, so the split is deliberate.

Pure Java. An HTTP call to an OpenAI-compatible endpoint for chat and embeddings. Vectors are
stored in a table and cosine similarity is computed in Java. No Redis, no vector database, no
Python. The provider and model are configuration, so a local model and a hosted one are the
same code.

**AI-guided intake.** An optional second tab on `SubmitProperty` where the owner describes the
property in conversation and the assistant fills the form in. The form always works on its own.
The chat is a wrapper over it, never a replacement. This matters: a dead API key must not stop
anyone listing a property, and the chat cannot upload photos.

The intake exists to deliver the valuation at the moment it changes behaviour, before the owner
has committed to a number. Grounding an owner's expectation early is the difference between a
listing that moves and one that sits.

## 11. Architecture

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

`model` and `util` are shared by every layer.

Roughly seventy-five classes: thirteen models, thirteen DAOs, nine services, twenty
controllers, three valuation classes, six utilities. Almost all the thinking is concentrated in
the services and the estimator. The rest is typing.

Conventions and forbidden patterns are in `/CLAUDE.md` and are binding.

## 12. Non-goals

This is a training project, not a product. It does not need to scale, does not need caching,
does not need a message queue, does not need Docker, does not need CI. Adding any of those
would be architecture for its own sake and is explicitly unwanted.
