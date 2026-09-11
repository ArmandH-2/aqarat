# Phase 8 — Conversational property search assistant

An AI assistant that turns a plain-English description of a wanted property into a structured
search over Aqarat's existing listings, and suggests what fits.

This folder is the complete specification. Read it in this order:

1. `README.md` — this file. Why it exists, how it is built, what it must never do.
2. `REQUIREMENTS.md` — the numbered functional and non-functional requirements.
3. `DIAGRAMS.md` — use case diagram and two levels of data flow diagram.
4. `TASKS.md` — the ordered implementation task list. This is what you build from.

Before writing any code, read `docs/DESIGN.md`. Its conventions
are binding and this feature does not get an exemption from any of them.

---

## Context

Aqarat is complete through phase 7. Twenty panels ship, the valuation engine works, thirteen
tables are seeded with 2000 properties.

Section 10 of `docs/DESIGN.md` deferred an AI assistant and sketched a retrieval-augmented design
with three new tables: `kb_document`, `kb_chunk`, `chat_message`. **That sketch is superseded.**

The requirement is narrower and better defined than the sketch assumed: a user describes what they
want, and the assistant finds matching properties. That is not a retrieval problem. It is a
structured-search problem wearing a conversation as a coat.

This is also how the industry solves it. Zillow's natural-language search, and the patent covering
it, describe the same shape: the model classifies the sentence into filter types and filter values
as JSON, and an ordinary structured query engine runs the result. The model never touches the query
engine.

Aqarat already has that structured filter surface, and has had it since phase 2:

- `dao/PropertySearch` is a plain holder of optional filters. Every field may be null, meaning
  "do not filter on this".
- `dao/PropertyDao.FILTER_CLAUSE` guards each filter with `? IS NULL OR col = ?`, so any
  combination of filters is a single `PreparedStatement` with nothing concatenated into the SQL.
- `service/PropertyService.searchPublished` is the method `BrowseListingsController` already calls.

The assistant fills the same `PropertySearch` object that `BrowseListingsController.buildFilters()`
fills, and calls the same service method. **That is the entire integration.** No second search
path, no new SQL, no schema change.

---

## Decisions

These are settled. Do not revisit them during implementation.

| Question | Decision |
|---|---|
| LLM backend | OpenAI-compatible Chat Completions with tool calling. Base URL, key and model live in `config/local.properties`, which is gitignored. |
| Scope | Two tools: `search_properties` and `get_property_details`. Multi-turn refinement. Read-only. Published listings only. |
| Conversation persistence | In memory, for the life of the panel. **No schema change.** `kb_document`, `kb_chunk` and `chat_message` are not built. |
| Placement | A new `Panel.ASSISTANT` and `Assistant.fxml`, in the sidebar under `DISCOVER`. The Router contract is unchanged. |
| Missing filters | Extend `PropertySearch` and `PropertyDao.FILTER_CLAUSE` with bathrooms, the four amenities, and governorate. Existing columns only. |
| JSON | Gson 2.11.0, one new dependency. HTTP is `java.net.http.HttpClient` from the JDK, so no HTTP client dependency. |

### The hard constraint

`docs/DESIGN.md` states it in section 1 and it is not a slogan:

> The AI advises. A human decides. Both are recorded.

The assistant suggests and explains. It reserves nothing, books nothing, submits nothing, and
changes no status. It writes no row to any table, including `audit_log`. If a user asks it to
reserve a property, it declines and points them at the property page where a person does it.

---

## Architecture

```
Assistant.fxml
    |
controller/AssistantController      renders bubbles and result cards, runs each turn on a Task
    |
service/AssistantService            the agent loop: send, tool call, execute, send, reply
    |            |
    |            +--> PropertyService.searchPublished(...)   existing, unchanged
    |            +--> PropertyService.findById(...)          existing, unchanged
    |
ai/ChatClient                       one HTTP POST to /chat/completions, Gson in and out
ai/AgentTools                       the two tool schemas, and dispatch into PropertyService
ai/Conversation                     in-memory message list, capped
ai/FilterMapper                     tool arguments to PropertySearch, names to ids
ai/Ranker                           scores matches by fit, returns the top few
ai/Suggestion                       one result: a Property plus why it fits
```

Arrows point downwards, as everywhere else in this codebase. The `ai` package holds no SQL and
opens no `Connection`; it hands a `PropertySearch` to `PropertyService` exactly as a controller
does. `AssistantService` owns the loop, the controller owns the screen.

This mirrors how `valuation/` sits beside `ValuationService`: a package of pure logic that can be
unit-tested with no database, and a service that connects it to one.

### The agent loop

1. The controller appends the user's text to `Conversation`, disables the input, and shows a
   typing indicator.
2. `AssistantService.respond(...)` runs **on a background thread**, inside a
   `javafx.concurrent.Task`. The FX thread never waits on a socket.
3. `ChatClient` posts the conversation plus the two tool schemas to `/chat/completions`.
4. The response is either an assistant message, which is rendered and ends the turn, or a
   `tool_calls` array.
5. For each tool call, `FilterMapper` converts the JSON arguments into a `PropertySearch`,
   `AgentTools` calls `PropertyService`, and the result is serialised back as a compact JSON tool
   message. **Java executes the query. The model supplies arguments and nothing else.**
6. Back to step 3. Hard cap of three tool rounds per user turn, after which the model is asked for
   a text answer with no tools available.
7. `Task.setOnSucceeded` renders the reply and any suggestion cards on the FX thread.

### Ranking

`PropertyDao.search` orders by `created_at DESC`, which is right for browsing and wrong for
suggesting. So the search tool fetches up to fifty matches, `Ranker` scores them in Java, and the
top five are returned to the model and shown.

The score is distance from the stated budget, plus a penalty for each soft preference not met — a
bedroom count off by one, an area below what was asked. It is a deterministic function of a list
and a filter set, which is what lets `RankerTest` run with no network and no database.

Deliberately not built: learned ranking, embeddings, similarity vectors. Add them when a scored
list is demonstrably putting the wrong property first.

### Why each suggestion fits

The one-line reason under each card is written by the model, because the model is the only
component that knows which part of the sentence mattered.

Every number on the card — price, area, bedrooms, district — is read from the database row. None
of it comes from the model. A model that hallucinates a price onto a real listing is the single
failure mode that would discredit this feature, and the way to prevent it is to never let model
output reach a numeric field.

---

## Failure behaviour

| Failure | What the user sees |
|---|---|
| `ai.enabled=false`, or the key is blank | The sidebar entry is absent. If the panel is reached anyway, an empty state pointing at Browse listings. |
| Timeout, 5xx, or 429 | "The assistant is unavailable right now. You can still search with filters on Browse listings." The input is re-enabled. |
| Unparseable tool arguments | One silent retry with the error fed back to the model, then the message above. |
| Zero results | The assistant says so and proposes the single filter most worth relaxing. Never an empty card list with no explanation. |
| `SQLException` | The existing wording from `BrowseListingsController`: could not load listings, check that SQL Server is running. |

No stack trace, no error code and no JSON ever reaches a bubble or a dialog. The conventions require
error messages written for the user, and this feature is where it is most tempting to break that.

---

## Security

- The API key is read from `config/local.properties`, which is already gitignored. It is never in
  `pom.xml`, never in a committed file, never written to a log, and never shown in the UI.
  `config/local.properties.example` carries `CHANGE_ME`.
- The model returns **arguments**, never SQL. Every value still lands in the existing
  `PreparedStatement` through `PropertySearch`.
- The status filter is hard-wired to `PropertyStatus.AVAILABLE` inside `AgentTools`. It is not a
  tool parameter. The assistant cannot be talked into showing a `PENDING_REVIEW` submission, a
  rejected property, or anything else that is not published.
- Owner identity, internal notes, review notes and valuations are never placed in the model's
  context. Guests must not see them (`docs/DESIGN.md` section 5), and the reliable way to guarantee
  that is to never send them.
- Conversation text is transmitted to the configured provider. `README.md` at the repository root
  says so plainly, because a user is entitled to know.

---

## Known limitations

Recorded here because they are real, and a limitation named is worth more than one discovered.

### The API key ships with the client

This is a desktop application. `config/local.properties` sits on the machine that runs it, so
anyone who can run Aqarat can read the key out of the file and spend it on anything they like.
Gitignoring the file keeps the key out of the repository; it does not keep it out of the hands of
whoever the application is installed for.

There is no fix for this inside a desktop client. A key that the client must send is a key the
client must hold. The real answer is a thin server that holds the key, authenticates the user, and
proxies the call — which is a deployment this project does not have and should not grow one for.

What is done instead: the assistant is behind a sign-in, so spend is attributable to an account
rather than anonymous, and a conversation is capped so a single session cannot run away. Both are
mitigations. Neither is a fix, and the honest position is that this design is appropriate for a
training project on a trusted machine and would not ship to real users unchanged.

### Scope is held by the prompt, not by the code

The system prompt tells the model to discuss Aqarat listings and Lebanese property and to decline
everything else. A determined user can talk it out of that, as they can with any prompt rule.

What that costs is bounded, and the bound is structural rather than textual:

- It has two tools, both read-only.
- `AVAILABLE` is hard-wired in Java, so no argument it produces widens what it can see.
- It holds no `Connection` and reaches the database only through `PropertyService`.
- Owner identity, review notes and valuations are never in its context, so they cannot leak from it.
- Every figure rendered on a card is read from the database row, so a hallucinated price cannot
  reach the screen.

Talk it into discussing the weather and you have wasted a few tokens and produced an off-topic
paragraph. You have not read another user's data, changed a status, or written a row. That
containment is the part worth defending; the prompt is only the polite first line.

### Cost is capped, not measured

A conversation stops after twenty turns. Nothing counts tokens, records what a session cost, or
enforces a budget across sessions. For a project with one operator and a personal key that is
proportionate. An agency would want per-user accounting before enabling this for real clients.

---

## Deferred

Each of these is a later rung. None is in this phase.

- **Account-data questions** — "what do I still owe", "when is my viewing". Every such tool is an
  ownership-enforcement surface that must pass the current user id into the query rather than
  filtering afterwards. Worth doing, but it is a phase of its own.
- **Actions from chat** — requesting a viewing, starting a reservation. Blocked by the hard
  constraint above, and would need confirmation, audit entries, and the existing transaction
  boundaries.
- **Retrieval over policy documents** — there are no policy documents in this repository to index.
  Add it when there are.
- **Persisted conversations** — needs a `chat_message` table, and a schema change is a design
  conversation, not a migration.
- **Streaming replies** — renders faster, changes nothing about whether the answer is correct.
- **Arabic, and voice** — the interface is English only, per `docs/DESIGN.md` section 2.
