# Diagrams

Five views of the same system: who uses it, what it stores, how the code is arranged, how a
property moves through its life, and what crosses the boundary when the search assistant runs.

They are written as Mermaid rather than exported from a drawing tool, so a change to the
design is a change to this file — a diagram that has to be re-exported by hand is a diagram
that goes stale.

---

## 1. Use cases

```mermaid
graph LR
    guest([Guest])
    customer([Customer])
    agent([Agent])
    admin([Admin])

    guest --> browse[Browse and filter listings]
    guest --> register[Register an account]

    customer --> browse
    customer --> submit[Submit a property]
    customer --> myProperties[Track own submissions]
    customer --> requestRemoval[Ask for a listing to be removed]
    customer --> requestViewing[Request a viewing]
    customer --> reserve[Reserve and pay a deposit]
    customer --> myContracts[View contracts and schedule]
    customer --> declare[Declare a payment]

    agent --> review[Review submissions]
    agent --> valuation[Run a valuation]
    agent --> listings[Manage listings]
    agent --> confirmViewing[Confirm viewings and record outcomes]
    agent --> draft[Draft and activate contracts]
    agent --> confirmPayment[Confirm declared payments]

    admin --> users[Manage accounts and roles]
    admin --> reference[Manage districts, types and settings]
    admin --> reports[Revenue, commission and overdue reports]
    admin --> audit[View and export the audit log]
```

An admin can do everything an agent can; the arrows above show only what is theirs alone.
Whether a customer is an owner or a client is answered by their relationships, not by a
column, which is why one actor points at both sets.

---

## 2. Entity relationships

```mermaid
erDiagram
    app_user ||--o{ property : owns
    app_user ||--o{ property : "is agent for"
    district ||--o{ property : "is in"
    property_type ||--o{ property : classifies
    property ||--o{ property_photo : has
    property ||--o{ valuation : "is estimated by"
    property ||--o{ viewing : "is visited in"
    property ||--o{ reservation : "is held by"
    property ||--o{ contract : "is sold or let by"
    app_user ||--o{ viewing : requests
    app_user ||--o{ reservation : places
    app_user ||--o{ contract : "is party to"
    contract ||--o{ payment_schedule : "is paid by"
    payment_schedule ||--o{ payment : settles
    reservation ||--o{ payment : "deposit for"
    app_user ||--o{ audit_log : acts

    property {
        int id PK
        int owner_id FK
        int agent_id FK "null until claimed"
        decimal area_sqm
        string deal_type "SALE or RENT"
        decimal asking_price "sale price, or monthly rent"
        string status "controlled state machine"
    }
    valuation {
        int id PK
        decimal estimated_value
        decimal lower_bound
        decimal upper_bound
        string flag "OK, ABOVE_MARKET, IMPLAUSIBLE"
        string comparables "what it reasoned from"
    }
    contract {
        int id PK
        string contract_type "SALE or LEASE"
        decimal total_amount
        decimal commission_rate "copied at draft time"
        decimal commission_amount "computed once, on activation"
    }
    payment {
        int id PK
        int schedule_id FK "one of these two"
        int reservation_id FK "and never both"
        string status "DECLARED, CONFIRMED, REJECTED"
    }
```

`property.asking_price` carries two meanings: the full sale price when `deal_type` is
`SALE`, the monthly rent when it is `RENT`. Every read checks the deal type first.

Two rules the database enforces itself, with filtered unique indexes: one active reservation
per property, and no two confirmed viewings for one agent at one moment.

---

## 3. Classes and layers

```mermaid
graph TD
    subgraph controller
        panels[20 panels, one controller each]
    end
    subgraph service
        propertyService[PropertyService<br/>the status state machine]
        valuationService[ValuationService]
        contractService[ContractService<br/>activation, one transaction]
        paymentService[PaymentService<br/>schedule generation]
        others[Auth, Audit, Reference,<br/>Viewing, Reservation, Report]
    end
    subgraph valuation
        estimator[PriceEstimator]
        regression[LinearRegression]
        result[ValuationResult]
    end
    subgraph dao
        daos[One per table.<br/>SQL lives here and nowhere else.]
    end
    subgraph model
        pojos[13 POJOs, 13 enums.<br/>No behaviour.]
    end
    subgraph util
        utils[Db, Router, Format,<br/>SessionManager, FieldError, AlertUtil]
    end

    panels --> propertyService
    panels --> contractService
    panels --> paymentService
    panels --> valuationService
    panels --> others
    contractService --> propertyService
    contractService --> paymentService
    valuationService --> estimator
    estimator --> regression
    estimator --> result
    propertyService --> daos
    valuationService --> daos
    contractService --> daos
    paymentService --> daos
    others --> daos
    daos --> database[(SQL Server)]
```

The arrows only ever point downwards. A controller never reaches a DAO, a DAO never holds a
business rule, and the `valuation` package never touches a connection — `PriceEstimator`
takes a list of comparables and returns a result, which is what lets its tests run with no
database at all.

---

## 4. Property lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING_REVIEW : owner submits
    PENDING_REVIEW --> NEEDS_INFO : agent asks for more
    NEEDS_INFO --> PENDING_REVIEW : owner responds
    PENDING_REVIEW --> REJECTED : rejected with a reason
    PENDING_REVIEW --> AVAILABLE : approved and published
    PENDING_REVIEW --> WITHDRAWN : owner withdraws
    NEEDS_INFO --> WITHDRAWN : owner withdraws

    AVAILABLE --> RESERVED : client reserves, pays deposit
    RESERVED --> AVAILABLE : reservation lapses or is cancelled
    RESERVED --> UNDER_CONTRACT : contract activated
    AVAILABLE --> UNDER_CONTRACT : contract drafted directly
    UNDER_CONTRACT --> CLOSED : sale completes
    UNDER_CONTRACT --> AVAILABLE : lease ends

    AVAILABLE --> WITHDRAWAL_REQUESTED : owner asks for removal
    WITHDRAWAL_REQUESTED --> WITHDRAWN : agent accepts
    WITHDRAWAL_REQUESTED --> AVAILABLE : agent declines
    AVAILABLE --> WITHDRAWN : agent takes it down

    REJECTED --> [*]
    WITHDRAWN --> [*]
    CLOSED --> [*]
```

`property.status` moves only through `PropertyService`. No screen writes the column.

Two of these transitions are decided when a row is **read**, not by a timer: a reservation
past its expiry, and a schedule row past its grace period. There is no scheduler in this
system, deliberately — a background thread in a desktop application is a source of bugs, and
a read-time answer is still correct after the application has been closed for a month.

---

## 5. Search assistant, data flow

The context-level view. The processes inside the assistant, and the use cases it serves, are in
[`docs/ai-agent/DIAGRAMS.md`](ai-agent/DIAGRAMS.md).

```mermaid
graph LR
    user([User<br/>guest, customer or agent])
    llm([OpenAI-compatible<br/>chat completions API])

    user -->|property description,<br/>refinement, question| assistant[Aqarat<br/>search assistant]
    assistant -->|suggestions, reasons,<br/>clarifying questions| user

    assistant -->|conversation + tool schemas| llm
    llm -->|reply, or tool call<br/>with filter arguments| assistant

    assistant -->|PropertySearch filters| db[(Aqarat<br/>SQL Server)]
    db -->|AVAILABLE property rows,<br/>districts, types, photos| assistant
```

The flow to the model carries the conversation and the two tool schemas, and nothing else. Owner
identity, internal notes, review notes and valuations never cross that boundary, because a guest
is not entitled to see them and the reliable way to guarantee that is to never send them.

The flow back carries either prose or a set of filter arguments. It never carries SQL, and it
never carries a price or an address that the application will then display — every figure on a
result card is read from the database row.
