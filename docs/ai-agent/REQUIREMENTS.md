# Requirements — conversational property search assistant

Read `README.md` in this folder first. These requirements are the acceptance criteria for the
phase; `TASKS.md` says how to satisfy them.

Each requirement is numbered so a task, a test or a review comment can cite it.

---

## Functional requirements

| # | Requirement |
|---|---|
| FR-1 | A signed-in user can open an Assistant panel from the sidebar and type a free-text description of the property they want. Guests cannot: the entry is absent from the guest sidebar, and the panel shows a sign-in prompt if reached directly. Every turn spends against the agency key, so it stays attributable to an account. |
| FR-2 | The assistant extracts structured filters from the sentence: district, governorate, property type, deal type, minimum and maximum price, bedrooms, bathrooms, minimum and maximum area, parking, elevator, balcony, furnished. |
| FR-3 | Filters the user did not state are left unset. The assistant never invents a constraint. A sentence mentioning only a budget produces a filter set containing only a budget. |
| FR-4 | When the request is too vague to search usefully — no location, no budget and no property type — the assistant asks exactly one clarifying question instead of guessing or searching. |
| FR-5 | The assistant searches only `AVAILABLE` properties, through `PropertyService.searchPublished`. It has no way to reach any other status. |
| FR-6 | Up to five suggestions are shown as cards carrying photo, title, district, property type, bedrooms, bathrooms, area, price, and a one-line reason the property fits the request. |
| FR-7 | Clicking a card opens that property in `PropertyDetails` through `Router.show(Panel.PROPERTY_DETAILS, id)`. `Router.back()` returns to the conversation. |
| FR-8 | A follow-up message refines the previous filter set rather than starting from nothing. "Cheaper", "make it two bedrooms", and "add parking" each work as a second turn. |
| FR-9 | The user can ask about a property already suggested — "does the second one have an elevator?" — and the answer comes from a fresh `get_property_details` lookup, not from what the model remembers. |
| FR-10 | A "Start over" control clears the conversation, the carried-forward filters, and the transcript. |
| FR-11 | A search returning nothing produces an explanation in words and a proposal naming the single filter most worth relaxing. An empty card list with no explanation is a defect. |
| FR-12 | Every figure shown on a card is read from the database row. The model supplies prose only. No price, area, bedroom count, district name or property id displayed anywhere originates from model output. |
| FR-13 | The assistant performs no writes. It cannot reserve, request a viewing, submit a property, change a status, or add an audit entry. Asked to do any of these, it declines and points at the panel where a person does it. |
| FR-14 | With the assistant disabled or misconfigured, every other panel behaves exactly as it did before this phase. |
| FR-15 | The assistant discusses Aqarat listings and Lebanese property only. Asked about anything else, it declines in one sentence and says what it can help with, without answering first. |
| FR-16 | A conversation is capped at 20 user turns. At the limit the input is disabled with an explanation, and "Start over" resets both the transcript and the count. |
| FR-17 | Replies render as plain text. The prompt forbids markdown and any emphasis markers that slip through are stripped before display. |
| FR-18 | The reason shown under each card is matched to its property by position in the reply, not by title, because titles repeat. |

---

## Non-functional requirements

| # | Requirement |
|---|---|
| NFR-1 | The network call runs on a background `javafx.concurrent.Task`. The FX thread never blocks. A typing indicator is visible for the whole round trip and the input is disabled while a turn is in flight. |
| NFR-2 | HTTP timeout of 30 seconds, configurable. At most three tool rounds per user turn, after which the model is asked for a text answer with no tools offered. |
| NFR-3 | Conversation history is capped at the last twenty messages, so context cannot grow without bound across a long session. |
| NFR-4 | No SQL in a controller. No business rule in a DAO. No `Connection` anywhere in the `ai` package. `AgentTools` reaches the database only through `PropertyService`. |
| NFR-5 | No new table, no new column, no edit to `db/schema.sql` or `db/seed.sql`. |
| NFR-6 | The API key is read from `config/local.properties`. It is never committed, never logged, never shown in the UI, and never included in an error message. |
| NFR-7 | The panel is styled from the tokens and component classes already in `docs/UI-STYLE.md` and `src/main/resources/css/app.css` — `card`, `card-hoverable`, `pill`, `spec-chip`, `empty-state`, `label-soft`, `section-title`, `hint`. No new colour value is invented. |
| NFR-8 | `FilterMapper` and `Ranker` are unit-tested without a network and without a database, in the same style as `PriceEstimatorTest`. |
| NFR-9 | Every failure path shows a sentence written for a user. No stack trace, no HTTP status code, no raw JSON in a bubble or a dialog. |
| NFR-10 | Adding this feature changes no existing test. `./mvnw test` passes before and after. |

---

## Out of scope

Listed here so that a reviewer can tell a missing feature from a deliberate omission. The reasoning
for each is in `README.md` under "Deferred".

- Questions about the user's own account: outstanding balance, upcoming viewings, contract status.
- Any action taken from the conversation: reserving, booking a viewing, submitting a property.
- Retrieval over policy, FAQ or contract-template documents, and the tables that would need.
- Conversations that survive closing the application.
- Token-by-token streaming of the reply.
- Arabic, and voice input.
