# Client surface rework

Phase 9. Branch `phase9-design-rework`.

This document records what was wrong with the client-facing panels, what was
decided, and what was built. It is the reference for finishing the agent and
admin panels to the same standard.

---

## 1. The diagnosis

Every client panel was the same file:

```
ScrollPane > VBox spacing=20 > [title + subtitle] > [card] > [card] > [card]
```

Nine panels, one template, applied regardless of what the panel was for. Nothing
about Browse Listings' shape said "shopping"; nothing about My Contracts' shape
said "money over time". That is what made the application read as a spreadsheet:
not that it was minimal, but that it was **undifferentiated**. Real minimalism is
expensive-looking because every screen earns its own layout.

Three compounding causes:

1. **A cold grey canvas** (`#F4F5F4`). Grey is the strongest spreadsheet cue a
   desktop application has.
2. **No photography.** The seeded gallery was flat green clipart — a vector house
   on a grey field. No layout compensates for that.
3. **Schema-shaped navigation.** My properties, My contracts and My activity
   split one person's own business along database table boundaries.

---

## 2. Requirements

Derived from the diagnosis, and satisfied by the work in section 4.

### Ground and identity

- R1. The canvas must not be neutral grey, and cards must not be pure white.
      Beirut limestone `#E7DFCD` on cream `#FAF5E9`; three alternatives are
      photographed in `docs/palette-options`.
- R2. British Racing Green `#004225` is retained as brand, with a brass accent
      for eyebrows and emphasis. No new hues invented.
- R3. Display type must have character. A serif for display sizes, a sans for
      interface text, both bundled so the application looks identical on a
      machine with neither installed. A missing font degrades, never fails.
- R4. Listings must be shown with real photographs.

### Shape

- R5. No two panels may share a silhouette by default. Each panel's layout is
      derived from what it is for.
- R6. A panel that wants a full-bleed band must be able to reach the window
      edge. The shell contributes no padding.
- R7. Every list of things a person owns must lead with what needs their
      attention, not with the list.

### Navigation

- R8. Customer navigation is three items: Discover, Portfolio, Submit.
- R9. Search and the assistant are one surface, not two doors onto the same
      catalogue.
- R10. Signing in lands on a panel, never an empty content area.
- R11. Every capability in `DESIGN.md` §5 survives the reorganisation.

### Honesty

These follow from the system's stated purpose — that every number can be traced
to its source — and are the requirements most easily broken by a redesign.

- R12. The assistant must show what it understood, and must say when a phrase
       could not become a filter rather than silently dropping it.
- R13. A valuation must be shown with its range, its methods and its
       comparables — never as a bare number.
- R14. A verdict on an asking price must not be shown when no asking price has
       been entered.
- R15. The assistant stays behind sign-in. Each conversation is billed to the
       agency's API key, so it stays attributable. Guests keep keyword search
       and the full filter set.

---

## 3. Decisions

| Decision | Rationale |
|---|---|
| Limestone over dark luxe or pure editorial | Dark is risky for long back-office sessions, and pure editorial needs photography the project cannot guarantee. Limestone is the stone Beirut is built from, so the ground is answerable rather than decorative, and cards separate on it without a heavier border. |
| Instrument Serif + Inter | A display serif with real character against the interface sans already in use. Inter alone was part of why the application looked like a template. |
| Discover merges Browse and Assistant | They were two entrances to one catalogue. Merging makes the assistant more visible, not less. |
| Portfolio hosts the three panels rather than replacing them | They hold working payment-schedule and declaration logic. Rewriting that to change a layout risks regressions for no design gain. |
| Filters collapse behind "All filters" | Eight always-open controls cost ~380px above the first result. Most searches are one sentence. |
| Card width is computed, not fixed | Fixed widths leave a ragged gutter and lose a column when a scrollbar takes the last few pixels. |

---

## 4. What was built

| Panel | Change |
|---|---|
| Sign in / Register | Split brand panel over a photograph, carrying the traceability claim and live catalogue counts. Guest path kept first-class. |
| Discover | Replaces Browse Listings and Assistant. One field takes a sentence; the assistant turns it into filters shown as removable chips. Responsive photo grid. |
| Property details | Photograph leads at full width. Specification strip on rules replaces a twelve-cell grid. One primary action. |
| Portfolio | Replaces My properties, My contracts, My activity. "Needs you" strip surfaces expiring reservations, overdue instalments and agent requests. |
| Submit | Two columns, with a live valuation panel: estimate, range with the asking price marked, methods, comparables. |
| Agent / admin panels | Design system applied. No bespoke layout work yet. |

Supporting changes: 32 seed photographs replaced with licensed Unsplash
photography matched to property type; four photographs per property instead of
one; `ValuationService.previewValue` for valuing an unsaved draft;
`Router.load(Panel)` so a host panel can embed another; `SceneCapture` for
reviewing a panel's appearance without a person at the machine.

---

## 5. What testing found

The flows below were driven through the interface and checked against the
database, not read. Each one is a claim this application makes on screen, so
each one had to be true.

| Flow | Result |
|---|---|
| Customer requests a viewing | Row written, status `REQUESTED`. Stored two hours behind the chosen time, which is the documented UTC storage correct for Beirut. |
| Agent confirms it | Status `CONFIRMED`, agent assigned. |
| Agent confirms a second at the same hour | **Refused** by `ux_viewing_agent_slot`, exactly as the Viewings panel claims on screen. |
| Agent approves a submission | `PENDING_REVIEW` → `AVAILABLE`, audit row `REVIEW on property`. |
| Agent confirms a declared payment | Status moved, audit row `PAYMENT_APPLIED on payment_schedule`. |
| Guest keyword search | Routed locally to filters, no model call, no account. |
| Registration | Account created with role `CUSTOMER`. |
| Customer declares a payment | Row written with the proof reference and status `DECLARED`. The earlier attempt failed because proof is mandatory and was empty — the validation was correct. |

Defects it exposed, all fixed: a contract's rent rendered as "$340/mo / mo"
(twice, in two files); the viewing form put its submit button above its own
inputs; a date picker and a time dropdown sharing a 330px rail clipped the time
to an ellipsis; the schedule's action column clipped "Overdue" and "Declare
payment" on the row most needing action; a reservations list with a fixed
height showed a customer with one reservation a two-thirds-empty card; every
payment in the ledger displayed "02:00", a UTC-midnight artifact; every row in
the confirmation queue was titled "Contract Installment Payment"; the audit
log's Apply button was clipped to "Ap…"; alerts carried the toolkit's default
icon; and a malformed FXML made a navigation click look like it did nothing.

## 6. Reporting an outcome

The application opened a modal window 161 times: 113 errors, 30 confirmations of
success, 12 questions and 6 hand-built forms. Every one of them dimmed the
screen and demanded a click. That is a problem of count rather than of
appearance — restyling 161 modal windows still leaves 161 doors — so they were
sorted by what the message actually is.

One defect had to be fixed first. `app.css` defined `.content` for the shell's
content pane, and JavaFX uses that same class name internally inside every
`DialogPane`, `TitledPane`, `ScrollPane` and `TextArea`. Our rule was painting
the inside of every dialog with the canvas colour, leaving a cream frame around
a limestone block. Ours is now `.shell-content`, which fixed every dialog at
once.

| What the message is | Where it now goes |
|---|---|
| A field is wrong | Inline, under the field (`FieldError`), or prevented outright by a submit button that stays disabled until the required fields are filled |
| A panel could not load | `Banner` in the space the list would have filled, carrying a Try again |
| Something worked | `Toast`, bottom-right, four seconds, no focus taken |
| Something was cancelled or withdrawn | `Toast` in brass, seven seconds |
| Something failed | `Toast` in red, and it stays until dismissed |
| A decision | `Dialogs.ask` — still modal, because the answer changes what happens next |
| A form | `Dialogs.form` / `Dialogs.note` — still modal, one shared builder |
| A confirmed payment | `Receipt` — a document, not a message |

Rules that came out of it, and are now enforced in one place rather than
twenty:

- A button names its outcome. "Terminate the contract", never "Yes". Someone
  reading only the buttons still knows what will happen.
- A destructive button is red and is never the default, so Enter cannot destroy
  anything.
- A form cannot be submitted into an error it already knows about.
- A failure never disappears on a timer.
- Every outcome message says what happened on the first line and what changed
  underneath on the second. "The contract is now active" became "Contract
  active — the property is under contract and off the market. The payment
  schedule is now running."

`AlertUtil` survives as the plain way to report an outcome from a controller;
it no longer opens a window.

### The receipt

It was built in three places as a string of newlines and shown in an
information alert — the same blue-iconed box that reported failures. It is now
one `Receipt` view: masthead, the figure in the display serif, ruled lines of
detail, and the balance the payment leaves on the contract. It prints. On
Windows, choosing "Microsoft Print to PDF" in the print dialog saves it as a
PDF, which is why no PDF library was added: the platform already has one.

### A money bug the receipt exposed

Opening a receipt for a settled instalment reported "$-622,600.00 remaining".
Three instalments in the database had `amount_paid` at exactly twice
`amount_due`.

`db/rebase-arrears.sql` settled every overdue instalment older than the
collections window, including the ones the seed had hung a DECLARED payment on
for the agent's confirmation queue. That left a payment awaiting confirmation
against an instalment already marked paid, and confirming it added the money a
second time.

Closed at three depths: the script now skips an instalment that has a payment
awaiting confirmation; `PaymentService` refuses to apply a payment to an
instalment already settled in full; and a receipt cannot show a negative
balance. `db/repair-double-counted.sql` restates the rows written before any of
that existed, and is idempotent. `RoleWorkflowTest` now settles an instalment
and asserts that a further payment against it is refused and leaves the figure
untouched.

## 7. Outstanding

- (Resolved) The agent dashboard's Quick Navigation card duplicated the sidebar
  and the clickable KPI tiles; removed.
- `MyContracts` and `MyActivity` render inside Portfolio; their remaining
  sections are tidy but were not redesigned as thoroughly as the schedule.
- (Resolved) The declare-payment dialog was raw JavaFX with no design system
  and no marks on its two mandatory fields; rebuilt on the shared dialog
  builder, and a declaration driven through it end to end.
- `Banner` is compile-verified but was not seen on screen: reaching it needs the
  database to fail after sign-in, and sign-in needs the database. Its one
  reachable route today is a panel load failing while the application is
  already running.
- `docs/palette-options` holds three grounds not chosen. Clay is the one worth
  revisiting if the interface should feel heavier.

## 8. Reviewing a panel yourself

The application cannot be screenshotted from outside — JavaFX composites on the
GPU, so Windows returns a blank frame. Ask the scene to draw itself instead:

```bash
./mvnw javafx:run -Daqarat.capture.dir=C:/temp/aqarat-shots
```

A PNG is written when the window opens, and again whenever F12 is pressed.
Without the property nothing is captured and no key is taken from the interface.
