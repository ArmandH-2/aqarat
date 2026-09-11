# Conventions

The rules the code is written to. Comments in the source cite this file by section, for
example `(CONVENTIONS.md, Errors)`. Read `docs/DESIGN.md` first for what the system is.

---

## Package layout

```
co.syntropyhq.aqarat
├── App.java
├── model/         POJOs and enums.
├── dao/           One DAO per table. SQL lives here and nowhere else.
├── service/       Business rules. Owns transactions.
├── controller/    One per FXML panel. Wires the UI to services.
├── valuation/     The price estimator.
├── ai/            The search assistant.
└── util/          Db, SessionManager, Router, Format and UI helpers.
```

---

## Layer rules

1. **Controllers never contain SQL.** Not one statement.
2. **DAOs never contain business rules.** No status transitions, no validation, no calculation.
   A DAO reads rows and writes rows.
3. **Services own transactions.** A DAO write method that takes part in a multi-step
   transaction accepts a `Connection`. The service opens it and owns commit and rollback.
4. **Models have no database or business behaviour.** Fields, constructor, getters, setters.
5. **Controllers do not call DAOs directly.** Always through a service.
6. **The valuation package touches no database.** `PriceEstimator` takes a list and returns a
   result, which is what makes it unit-testable.

The canonical transaction shape:

```java
try (Connection c = Db.get()) {
    c.setAutoCommit(false);
    try {
        propertyDao.updateStatus(c, propertyId, PropertyStatus.UNDER_CONTRACT);
        contractDao.activate(c, contractId);
        scheduleDao.insertAll(c, schedule);
        auditDao.record(c, entry);
        c.commit();
    } catch (SQLException e) {
        c.rollback();
        throw e;
    }
}
```

A read-only aggregate needs no transaction.

---

## Forbidden patterns

- String concatenation into SQL. Always `PreparedStatement`.
- `catch (Exception e) {}`, or a catch that only logs and carries on as if nothing failed.
- `e.printStackTrace()`.
- A UI view holding a database connection.

---

## Comments

Say **why**, not what. Document non-obvious business rules, valuation factors and SQL that
would otherwise look wrong.

---

## Naming

- Tables and columns: `snake_case`, singular table names (`property`, not `properties`).
- Java classes: `PascalCase`. DAOs end in `Dao`, services in `Service`, controllers in `Controller`.
- Enum constants: `UPPER_SNAKE`, matching the SQL `CHECK` constraints exactly.
- FXML files: `PascalCase.fxml`, matching the controller name minus `Controller`.
- Booleans read as questions: `hasParking`, `isFurnished`.

---

## SQL

- Always `PreparedStatement`, always try-with-resources for `Connection`, `PreparedStatement`
  and `ResultSet`.
- Explicit column lists. No `SELECT *`.
- Row mapping lives in one `mapRow(ResultSet)` per DAO.
- Money and area columns are `DECIMAL`, read with `getBigDecimal`.

---

## Navigation

**Panels are named by an enum, not by a string.**

```java
public enum Panel {
    BROWSE_LISTINGS("BrowseListings.fxml"),
    PROPERTY_DETAILS("PropertyDetails.fxml"),
    REVIEW_QUEUE("ReviewQueue.fxml");
    // ...one constant per panel

    private final String fxml;
}
```

```java
Router.show(Panel.BROWSE_LISTINGS);
Router.show(Panel.PROPERTY_DETAILS, propertyId);
Router.back();
```

**A panel that needs a record implements one interface.**

```java
public interface NeedsId {
    void receiveId(int id);
}
```

`Router` loads the FXML and, if an id was supplied and the controller implements `NeedsId`,
calls `receiveId(id)` before the view is attached. Nothing else is passed between panels. A
panel that needs more than an id takes the id and asks a service.

**The ordering rule:** `initialize()` runs when the FXML loads, *before* `receiveId()`. An
id-based panel builds its layout in `initialize()` and loads its data in `receiveId()`.

`Router` keeps a small history stack for `back()`, owns the shell's content pane, and is the
only class that calls `FXMLLoader`.

Queries and other slow work run off the FX thread in a `javafx.concurrent.Task`, with a loading
state on screen. Styling comes from the tokens in `src/main/resources/css/app.css`, described in
`docs/UI-STYLE.md`.

---

## Dates and times

- Models use `LocalDate` and `LocalDateTime`. Never `java.util.Date`, `java.sql.Date`, or
  `Timestamp` outside a DAO.
- DAOs convert at the boundary: `rs.getObject("due_date", LocalDate.class)` out,
  `ps.setObject(n, value)` in.
- `DATE` maps to `LocalDate`, `DATETIME2` to `LocalDateTime`.
- The database stores UTC (`SYSUTCDATETIME()`). Display is local, converted in `util/Format`.

---

## Tests

Test where correctness is not visible by clicking. Two places above all:

1. **`valuation/`** — a known input produces the expected estimate, the range brackets it, and
   an absurd asking price comes back `IMPLAUSIBLE`. No database.
2. **`PaymentService` schedule generation** — the instalments sum exactly to the total, with the
   rounding remainder on the last row. 1000 over 3 is 333.33 + 333.33 + 333.34.

Do not write tests for getters.

---

## Errors

- Services throw. Controllers catch and show a message.
- Messages are written for the user, not the developer. No stack traces in dialogs.
- Validation failure is a distinct path, not an exception used for flow control.

---

## Money and units

- All money is USD, `BigDecimal`, scale 2. Never `double` for money.
- All areas are m², `BigDecimal`, scale 2.
- Formatting happens in `util/Format`, never inline in a controller.
- Configurable values such as grace periods are read from `system_setting`, never hardcoded.
