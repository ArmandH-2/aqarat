# Diagrams — conversational property search assistant

Three views: who uses the assistant, what crosses its boundary, and what happens inside it.

Written as Mermaid rather than exported from a drawing tool, matching `docs/DIAGRAMS.md`. A
diagram that has to be re-exported by hand is a diagram that goes stale.

---

## 1. Use cases

```mermaid
graph LR
    guest([Guest])
    customer([Customer])
    agent([Agent])

    guest --> describe[Describe a property in plain English]
    guest --> suggestions[Receive ranked suggestions with reasons]
    guest --> openDetails[Open a suggestion's full details]

    customer --> describe
    customer --> suggestions
    customer --> openDetails
    customer --> refine[Refine the search conversationally]
    customer --> askAbout[Ask about a suggested property]
    customer --> startOver[Start a new conversation]

    agent --> describe
    agent --> suggestions

    describe -.->|includes| extract[Extract structured filters]
    refine -.->|includes| extract
    extract -.->|includes| search[Search published listings]
    suggestions -.->|includes| rank[Rank by fit to stated needs]
    askAbout -.->|includes| lookup[Look up one property]
    describe -.->|extends| clarify[Ask one clarifying question]
    search -.->|extends| noResults[Explain no matches, propose a filter to relax]
```

Every use case here is a read. No actor reaches one that writes, which is the whole point: the
assistant advises and a person decides.

An agent gets the panel as well, because the same conversation is a fast way to check what is on
the books while a client is on the phone. Nothing about the feature is agent-specific, so there is
no separate agent use case.

The dashed `includes` edges are the steps that always happen; the dashed `extends` edges are the
two that happen only in particular conditions — a request too vague to search, and a search that
matched nothing.

---

## 2. Data flow, level 0 — context

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

Two external entities and one data store.

The flow worth reading carefully is the one to the language model. It carries the conversation and
the two tool schemas, and nothing else. Owner identity, internal notes, review notes and valuations
never cross that boundary, because guests are not entitled to see them and the reliable way to
guarantee that is to never send them.

The flow back carries either prose or a set of filter arguments. It never carries SQL, and it never
carries a price or an address that the application will display.

---

## 3. Data flow, level 1 — inside the assistant

```mermaid
graph TD
    user([User])
    llm([Chat completions API])
    db[(SQL Server)]

    user -->|typed message| p1[1.0<br/>Capture turn]
    p1 -->|message appended| d1[/D1 Conversation<br/>in memory, last 20/]
    d1 -->|history| p2[2.0<br/>Request completion]
    p2 -->|messages + tool schemas| llm
    llm -->|tool_calls| p3[3.0<br/>Map arguments<br/>to PropertySearch]
    llm -->|assistant message| p6[6.0<br/>Render reply]

    d2[/D2 Districts and types<br/>cached at panel load/] -->|name to id| p3
    d3[/D3 Carried filters/] -->|previous filters| p3
    p3 -->|merged filters| d3
    p3 -->|PropertySearch| p4[4.0<br/>Search published<br/>listings]
    p4 -->|status = AVAILABLE + filters| db
    db -->|matching rows| p4
    p4 -->|up to 50 rows| p5[5.0<br/>Score and take top 5]
    p5 -->|compact tool result| p2
    p5 -->|suggestions| d4[/D4 Shown suggestions/]

    p6 -->|bubbles + cards| user
    d4 -->|cards| p6
    d4 -->|"the second one"| p3
```

Processes 2.0 and 3.0 form the loop, capped at three passes per user turn.

Two of the four stores exist for a specific behaviour and are worth naming:

- **D3, carried filters,** is what makes "cheaper" work. The new filter set is merged onto the
  previous one rather than replacing it, so a refinement never silently loses the location the user
  gave two turns ago.
- **D4, shown suggestions,** is what makes "the second one" resolvable. Without it the model would
  have to be trusted to remember which property was second, and it would eventually be wrong about
  a real listing.

Process 4.0 binds the status itself. `AVAILABLE` is not a value that arrives from process 3.0, so
no filter argument the model produces can widen what the search returns.
