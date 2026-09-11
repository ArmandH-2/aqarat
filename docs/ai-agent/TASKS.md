# Tasks — conversational property search assistant

Read `README.md`, `REQUIREMENTS.md` and `DIAGRAMS.md` in this folder before starting, and read
`docs/DESIGN.md` before writing any code.

Tasks are ordered by dependency. Nothing in a task compiles against something a later task builds.
Each is a session's work or less. Do one, verify it, commit it, then start the next.

Three files in this list are shared surface between the two tracks —
`dao/PropertyDao.java`, `controller/MainShellController.java` and `src/main/resources/css/app.css`.
Announce before touching them.

---

## T1 — Configuration and the one new dependency

- [x] Add Gson to `pom.xml`. Pin `<gson.version>2.11.0</gson.version>` beside the other version
      properties, with a comment saying it parses the tool-call responses from the chat API.
      Change no existing version. If the build will not resolve, say so rather than upgrading
      something else.
- [x] Create `util/Config.java`. It loads `config/local.properties` once into a static
      `Properties`, and exposes `Config.get(String key)` and `Config.get(String key, String
      fallback)`. It throws the same clear `IllegalStateException` that `Db` throws today when the
      file is missing, with the same wording.
- [x] Move the private `loadProperties()` and `requireProperty()` logic out of `util/Db.java` and
      have `Db` call `Config`. The file must be read in exactly one place after this task.
- [x] Append to `config/local.properties.example`:

```properties
# AI assistant. Leave ai.enabled=false to run the application without it.
ai.enabled=false
ai.baseUrl=https://api.openai.com/v1
ai.apiKey=CHANGE_ME
ai.model=gpt-4o-mini
ai.timeoutMs=30000
```

**Acceptance:** the application starts, logs in, and every panel works with no `ai.*` key present
in `config/local.properties` at all. `./mvnw test` passes unchanged.

---

## T2 — Extend the search surface

The chat will produce filters the current search cannot express. Every column needed already
exists on `property` and `district`. **No schema change.**

- [x] `dao/PropertySearch.java` — add fields and accessors, in the existing style, keeping the
      comment at the top of the file accurate:
      `Integer bathrooms`, `Boolean hasParking`, `Boolean hasElevator`, `Boolean hasBalcony`,
      `Boolean isFurnished`, `String governorate`.
- [x] `dao/PropertyDao.java` — add the matching lines to `FILTER_CLAUSE` and the matching binds to
      `bindFilters`, **in the same order in both places**. The existing pattern is
      `AND (? IS NULL OR col = ?)` with the value bound twice.
- [x] Governorate has no column on `property`, so it filters through the district table:
      `AND (? IS NULL OR district_id IN (SELECT id FROM district WHERE governorate = ?))`.
- [x] The four amenity filters mean "must have", not "must equal". Send them only when the user
      asked for the amenity; a null means do not filter. Do not let a `false` reach the query as a
      filter for properties without parking — nobody searches for that.

**Acceptance:** `search` and `count` still agree on what matches, because they still share
`FILTER_CLAUSE`. `BrowseListings` with no filters still returns the full 2000. A search with
`hasParking = true` returns strictly fewer rows than the same search without it.

Shared surface. Announce first.

---

## T3 — The `ai` package and `AssistantService`

The largest task, and the only one with no UI. Build it so it can be tested before a panel exists.

- [x] `ai/ChatMessage.java` — role, content, and the optional `toolCallId` and `toolCalls` a
      tool-calling exchange needs. A plain holder, no behaviour, matching the `model` package style.
- [x] `ai/Conversation.java` — a list of `ChatMessage`, with `append`, `messages()`, `clear()`, and
      a cap that keeps the system message plus the last twenty exchanges.
- [x] `ai/ChatClient.java` — one public method,
      `complete(List<ChatMessage> messages, boolean offerTools)`. Builds the request body with
      Gson, posts it with `java.net.http.HttpClient` to `{ai.baseUrl}/chat/completions` with an
      `Authorization: Bearer` header, and parses the first choice. Reads `ai.baseUrl`, `ai.apiKey`,
      `ai.model` and `ai.timeoutMs` from `Config`. Also exposes `isEnabled()`, true only when
      `ai.enabled` is true and the key is present and is not `CHANGE_ME`.
      Throws a checked `AssistantUnavailableException` on timeout, on any non-2xx status, and on a
      body it cannot parse. **The key is never placed in an exception message or a log line.**
- [x] `ai/AgentTools.java` — the two tool schemas below as constants, and
      `execute(String name, String argumentsJson)` returning a compact JSON string.
      Dispatches into `PropertyService`. **`PropertyStatus.AVAILABLE` is hard-wired here and is not
      a tool parameter.**
- [x] `ai/FilterMapper.java` — converts tool arguments into a `PropertySearch`. Resolves district
      and property-type **names** to ids against lists handed to it, so it opens no connection of
      its own. Matching is case-insensitive and trimmed. An unrecognised name leaves that filter
      unset rather than guessing at the nearest one. Merges the new arguments onto a carried filter
      set rather than replacing it.
- [x] `ai/Ranker.java` — a pure function from a list of properties plus the filter set to the top
      N by fit. Score is distance from the stated budget, plus a penalty per unmet soft preference.
      No randomness, no clock, no I/O.
- [x] `ai/Suggestion.java` — a `Property` and the one-line reason the model gave for it.
- [x] `service/AssistantService.java` — the loop described in `README.md`. Owns the three-round
      cap, the retry-once on unparseable arguments, and the system prompt. Depends on
      `PropertyService`, never on a DAO.
- [x] `src/test/java/co/syntropyhq/aqarat/ai/FilterMapperTest.java` and `RankerTest.java`.

### Tool schema — `search_properties`

```json
{
  "type": "function",
  "function": {
    "name": "search_properties",
    "description": "Search published Aqarat listings. Every parameter is optional; omit any the user did not state. Returns up to five properties ranked by how well they fit.",
    "parameters": {
      "type": "object",
      "properties": {
        "district":      { "type": "string",  "description": "District name, e.g. Achrafieh. Omit if not stated." },
        "governorate":   { "type": "string",  "description": "Governorate name, when the user named a region rather than a district." },
        "propertyType":  { "type": "string",  "description": "Property type name, e.g. Apartment, Villa, Office." },
        "dealType":      { "type": "string",  "enum": ["SALE", "RENT"] },
        "minPrice":      { "type": "number",  "description": "USD. Full sale price when dealType is SALE, monthly rent when RENT." },
        "maxPrice":      { "type": "number",  "description": "USD. Same meaning as minPrice." },
        "bedrooms":      { "type": "integer" },
        "bathrooms":     { "type": "integer" },
        "minArea":       { "type": "number",  "description": "Square metres." },
        "maxArea":       { "type": "number",  "description": "Square metres." },
        "hasParking":    { "type": "boolean", "description": "Send true only when the user asked for parking." },
        "hasElevator":   { "type": "boolean" },
        "hasBalcony":    { "type": "boolean" },
        "isFurnished":   { "type": "boolean" }
      },
      "additionalProperties": false
    }
  }
}
```

### Tool schema — `get_property_details`

```json
{
  "type": "function",
  "function": {
    "name": "get_property_details",
    "description": "Fetch the full details of one published property by id, to answer a question about a property already suggested. Use this rather than recalling details from earlier in the conversation.",
    "parameters": {
      "type": "object",
      "properties": {
        "propertyId": { "type": "integer", "description": "The id from a previous search_properties result." }
      },
      "required": ["propertyId"],
      "additionalProperties": false
    }
  }
}
```

### Tool result shape

Return a compact object, and include only fields a guest is allowed to see. No `ownerId`, no
`agentId`, no `reviewNote`, no valuation.

```json
{
  "count": 2,
  "results": [
    { "id": 412, "title": "...", "district": "Achrafieh", "propertyType": "Apartment",
      "dealType": "RENT", "askingPrice": 1150.00, "areaSqm": 135.00, "bedrooms": 2,
      "bathrooms": 2, "hasParking": true, "hasElevator": true, "hasBalcony": false,
      "isFurnished": true }
  ]
}
```

When nothing matched, return `{"count": 0, "results": [], "filtersApplied": [...]}` so the model
can name a filter worth relaxing rather than guessing at one.

### System prompt

Use this text. It is the only place the behavioural rules are stated to the model, so do not
paraphrase it.

> You are the Aqarat property search assistant. Aqarat is a Lebanese real estate agency.
>
> Your job is to understand what kind of property someone is looking for and find matching
> listings using the tools you have been given.
>
> Rules you must follow:
>
> - Only ever describe properties that came back from a tool call in this conversation. Never
>   invent a listing, a price, an address, or an availability.
> - Call `search_properties` with only the filters the user actually stated. Do not add a
>   constraint they did not give you.
> - If the request is too vague to search — no location, no budget and no property type — ask one
>   short clarifying question instead of searching.
> - When a follow-up message changes the search, call the tool again with the full updated filter
>   set, not just the part that changed.
> - To answer a question about a property already suggested, call `get_property_details` rather
>   than relying on what you remember.
> - When a search returns nothing, say so and suggest which single filter to relax.
> - For each property you suggest, give one short sentence on why it fits what they asked for.
>   Do not restate the price, area or bedroom count in that sentence; those are already shown.
> - All prices are USD and all areas are square metres. For a SALE listing the price is the full
>   sale price; for a RENT listing it is the monthly rent.
> - You cannot reserve a property, book a viewing, or change anything. If asked, say so plainly
>   and tell the user to open the property and use the buttons there.
>
> Keep replies short. Two or three sentences, then the properties.

**Acceptance:** `./mvnw test` passes, including the two new test classes, and neither of them opens
a socket or a database connection. `FilterMapperTest` covers: a budget-only sentence leaves
`minPrice` null; an unknown district name leaves `districtId` null; a refinement merges onto carried
filters instead of replacing them. `RankerTest` covers: the property closest to the stated budget
ranks first, and a property missing a requested amenity ranks below one that has it.

---

## T4 — The panel

- [x] `util/Panel.java` — add `ASSISTANT("Assistant.fxml")`.
- [x] `src/main/resources/fxml/Assistant.fxml` — a header with the title and a "Start over" ghost
      button, a `ScrollPane` holding the transcript `VBox`, and a `TextField` with a send button
      pinned to the bottom. Enter sends.
- [x] `controller/AssistantController.java` — loads districts and property types once in
      `initialize()` and caches them for `FilterMapper`. Renders user and assistant bubbles. Runs
      each turn on a `javafx.concurrent.Task`, disabling the input and showing a typing indicator
      for the duration. Renders `Suggestion` cards and wires each click to
      `Router.show(Panel.PROPERTY_DETAILS, id)`. Shows the empty state when `ChatClient.isEnabled()`
      is false. Catches `AssistantUnavailableException` and renders the unavailable bubble; catches
      `SQLException` and calls `AlertUtil.showError` with the existing wording.
- [x] `controller/MainShellController.java` — one `NavEntry` under `DISCOVER` for `CUSTOMER`,
      `AGENT` and `ADMIN`, and one in the guest list. The entry is not added at all when
      `ChatClient.isEnabled()` is false.
- [x] `src/main/resources/css/app.css` — `.chat-bubble-user`, `.chat-bubble-assistant`,
      `.chat-typing`. Use `-c-primary-tint` for the user bubble and `-c-surface` with
      `-c-border-subtle` for the assistant's. **No new colour value.**

Reuse what exists rather than rebuilding it: `UIHelper.createEmptyState`, `UIHelper.createSpecChip`,
`UIHelper.createPill`, `AnimationUtil.staggerIn`, `AnimationUtil.addHoverLift`, `Format.salePrice`,
`Format.monthlyRent`, `Format.area`, `Format.enumLabel`.

**Acceptance:** "3 bedroom apartment in Beirut under 300k" returns cards. "cheaper" narrows them
without losing Beirut. The window never freezes during a request.

`MainShellController` and `app.css` are shared surface. Announce first.

---

## T5 — Reuse the property card rather than cloning it

`BrowseListingsController.buildRichPropertyCard` (around line 288) builds exactly the card the
assistant needs.

- [x] Move it to `util/UIHelper` as
      `createPropertyCard(Property property, District district, PropertyType type, String photoPath, String reason)`,
      with `reason` nullable — `BrowseListings` passes null, the assistant passes the model's
      sentence, which renders as a line under the title.
- [x] Have `BrowseListingsController` call it.
- [x] Have `AssistantController` call it.

Do this **after** T4 renders something, so the shape of the shared method is driven by two real
callers rather than a guess about what the second one will need.

**Acceptance:** `BrowseListings` looks identical to before — compare against `docs/screenshots/`. The
assistant's cards match it. The card-building code exists in exactly one place.

---

## T6 — Documents and finishing

- [x] `docs/DESIGN.md` section 10 — replace the deferred RAG sketch with a pointer to
      `docs/ai-agent/` and one paragraph saying the retrieval design was dropped in favour of
      structured tool-calling, and why: the requirement is structured search, not document
      retrieval, and the filter surface it needs already existed.
- [x] `docs/BUILD-ORDER.md` — add "Phase 8 — Conversational search assistant" with these tasks and
      its own "Done when".
- [x] `docs/DIAGRAMS.md` — add the level 0 data flow diagram as a fifth diagram, cross-referencing
      `docs/ai-agent/DIAGRAMS.md` for the level 1.
- [x] `README.md` at the repository root — a paragraph on the assistant: what it does, that it
      needs an OpenAI-compatible key, that the application runs fully without one, and that
      conversation text is sent to the configured provider.
- [ ] One screenshot of the panel mid-conversation, committed under `docs/screenshots/`.
      **Outstanding.** Needs the application running with a real API key.
- [x] Delete any scratch file or test main created along the way. Read every new file once.
      Anything you could not explain to an examiner, rewrite or remove.

**Acceptance:** someone who has never seen this repository can go from `docs/ai-agent/README.md` to
a working assistant without asking a question.

---

## Files

**New**

```
src/main/java/co/syntropyhq/aqarat/ai/ChatMessage.java
src/main/java/co/syntropyhq/aqarat/ai/ChatClient.java
src/main/java/co/syntropyhq/aqarat/ai/Conversation.java
src/main/java/co/syntropyhq/aqarat/ai/AgentTools.java
src/main/java/co/syntropyhq/aqarat/ai/FilterMapper.java
src/main/java/co/syntropyhq/aqarat/ai/Ranker.java
src/main/java/co/syntropyhq/aqarat/ai/Suggestion.java
src/main/java/co/syntropyhq/aqarat/service/AssistantService.java
src/main/java/co/syntropyhq/aqarat/controller/AssistantController.java
src/main/java/co/syntropyhq/aqarat/util/Config.java
src/main/resources/fxml/Assistant.fxml
src/test/java/co/syntropyhq/aqarat/ai/FilterMapperTest.java
src/test/java/co/syntropyhq/aqarat/ai/RankerTest.java
```

**Modified**

```
pom.xml                                    Gson 2.11.0
config/local.properties.example            ai.* keys
util/Db.java                               delegates loading to Config
util/Panel.java                            ASSISTANT
util/UIHelper.java                         createPropertyCard
dao/PropertySearch.java                    six new fields
dao/PropertyDao.java                       six filter lines and binds     shared surface
controller/MainShellController.java        one nav entry                  shared surface
controller/BrowseListingsController.java   calls the shared card
src/main/resources/css/app.css             three chat classes             shared surface
docs/DESIGN.md, docs/BUILD-ORDER.md, docs/DIAGRAMS.md, README.md
```

**Untouched, deliberately:** `db/schema.sql`, `db/seed.sql`, every model, every other service,
every other DAO, and the whole `valuation` package.

---

## Verification

### Unit — no network, no database

```bash
./mvnw test
```

All existing tests must still pass unchanged, plus `FilterMapperTest` and `RankerTest`.

### Manual with the assistant off

Set `ai.enabled=false` in `config/local.properties`.

```bash
./mvnw javafx:run
```

Log in as `admin@aqarat.local` / `Password123!`. The Assistant entry must be absent and every other
panel must behave exactly as before. This is the regression check that matters most: an
unconfigured assistant costs nothing.

### Manual with a real key, signed in as a customer

**Outstanding.** No key was configured when the phase was built, so none of the nine checks below
has been run. Everything above them has: the suite passes, all three panels inflate in the JavaFX
toolkit, and the disabled path is verified. The live conversation is not.

1. "I want a 3 bedroom apartment in Beirut under 300,000" — cards appear, each with a reason.
2. "cheaper" — same shape of query, lower prices, Beirut not lost.
3. "actually make it furnished with parking" — fewer results, both amenities honoured.
4. "does the second one have an elevator?" — answered from the property row, not invented.
5. "something in Antarctica" — no results, and one named filter proposed to relax.
6. Click any card — `PropertyDetails` for that exact property; back returns to the conversation.
7. "reserve it for me" — declines and points at the property page. **No row is written.**
8. "Start over" — transcript cleared, and the next message starts from no filters.
9. Break the key deliberately — the unavailable bubble appears, the input is re-enabled, and
   nothing freezes.

Then open the audit log as an admin. It must contain **nothing** from the assistant.
