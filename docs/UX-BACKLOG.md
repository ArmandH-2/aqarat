# UX backlog — after the popup rework

Raised 30 Aug 2026, on `phase9-design-rework`. **All eight are done**, across
five commits. The notes below are kept as the reasoning behind each change,
including the two that were decisions rather than instructions (5 and 8) and the
one whose diagnosis turned out to be wrong on the first reading (6).

---

## 1. Toasts move to the top right

Currently bottom-right of the focused window (`Toast.MARGIN`, anchor
`CONTENT_BOTTOM_RIGHT`). Move to top-right, anchor `CONTENT_TOP_RIGHT`, and
reverse the entry animation so cards drop in rather than rise.

One thing to keep: the stack order. The newest card should stay nearest the
top edge, so the oldest is the one pushed out.

## 2. Save the receipt as a PDF rather than printing it

**Decided: OpenPDF.** The button writes a PDF to a location the person chooses,
and printing is left to whatever they open it with.

`PrinterJob` cannot produce a file on its own. On Windows it prints, and it is
"Microsoft Print to PDF" — a printer driver — that writes the PDF. With no
printer installed there is no path to a PDF through the toolkit at all, which
is why the button currently reports that there is nothing to send it to.

## 3. Re-audit every line of copy

The bar is: does this sentence answer a question the reader is actually asking
at this moment? A sentence can be true, well written, and still in the wrong
place.

The sign-in line is the clearest example. "Every number here can be traced to
where it came from" states the system's one real claim — the valuation shows
its range, its methods and its comparables instead of a bare number, which is
requirements R13 and R14. But it is said to someone who has not yet seen a
valuation, so it answers nothing they are wondering about. It belongs where a
valuation appears; the sign-in screen needs a line about what Aqarat is for.

Everything to re-read: sign-in and register, every panel eyebrow and subtitle,
the empty states, the dialog bodies written yesterday, and the assistant's
placeholder.

## 4. The assistant's placeholder should keep typing

It types one example once and stops. It should cycle several, indefinitely, so
the field reads as an assistant rather than as a search box with a long hint.

Examples should show the range of what the parser and the model can each take:
a full sentence, a bare district, a spec, a budget.

## 5. Loosen the assistant's refusal to search

Done, and not as written: the gate was removed rather than loosened. See the
reasoning under "Decisions taken" below.

## 6. Scrolling behaves differently depending on where the pointer is

Walked every panel in all three roles, scrolling each one and photographing it
before and after. The earlier reading of the FXML was wrong: the problem is not
that panels are built with a fixed header. It is that **a `ListView` inside the
page swallows the wheel**, so which thing moves depends on where the pointer
happens to be sitting.

Over a list, the list scrolls and the page does not — the title, the subtitle
and the tabs stay put and the reader loses two hundred vertical pixels for the
whole session. Move the pointer an inch to the left, onto the page background,
and the same gesture scrolls the page instead. One gesture, two behaviours,
in the same panel.

### What each panel does today

| Panel | Over the list | Over the page |
|---|---|---|
| Discover | grid scrolls, band pinned | band is outside the scroll pane, so it never moves |
| Review queue | list only | page |
| Listings | list only | page |
| Viewings | list only | page |
| Contracts | list only | page |
| Payments | list only | page |
| Users | list only | page |
| Audit log | list only | page |
| Reference | list only | page |
| Portfolio (Properties / Contracts / Viewings) | list only | page |
| Review submission | discussion pane only | page |
| Agent dashboard | — | fits the window, nothing to scroll |
| Submit a property | — | page (correct) |
| Property details | — | page (correct) |

So: twelve panels affected, two already correct, one that does not scroll at all.

### The fix

One behaviour, installed once rather than twelve layouts rewritten.

**The page gets the wheel first.** An inner list only starts scrolling once the
page has reached its bottom, and hands the wheel back as soon as the page can
move again on the way up. That is what a browser does with nested scrollers, it
is what the reader already expects, and it produces exactly the behaviour asked
for: the header leaves on the way down and comes back on the way up.

It keeps `ListView` virtualisation, which matters — Listings holds 1,085 rows
and Discover's catalogue is the same size. Sizing lists to their content to
force the page to scroll would render every row and is not an option.

`Discover` needs one extra change on top: its search band sits *outside* the
scroll pane, so no amount of chaining can move it. The band moves inside.

A "back to top" button is not needed once the header returns on an upward
scroll, and is the fallback only if that proves unreliable.

## 7. The agent dashboard is thin

Five counts and nothing to act on. Worth adding what an agent opens the
application to find out: what is overdue and by how much, which submissions
have been waiting longest, today's viewings. Each row should be a way in, not
a number.

## 8. Remove the number count-up animation

Done. See the reasoning under "Decisions taken" below.

---

## Decisions taken

### 2. What "save as PDF" meant — OpenPDF

| Option | Cost | Result |
|---|---|---|
| Add a PDF library (OpenPDF, ~1.5 MB) | One dependency, and the receipt is drawn a second time in the library's own API | A real PDF, no printer needed, prints from any viewer |
| Snapshot the receipt and save a PNG | None. `SceneCapture` already snapshots a scene and writes a PNG | An image, not a document. Fine to send to someone, awkward to file |
| Keep printing | None | Needs a printer driver, which this machine does not have |

Recommendation: OpenPDF if it must be a PDF. It is a single dependency, it is
the artefact a client would expect, and "the receipt saves as a PDF" is a
better sentence in a demo than "the receipt prints".

### 5. How lenient the assistant should be — no gate at all

The rule lives in `AssistantService.SYSTEM_PROMPT`:

> If the request is too vague to search — no location, no budget and no
> property type — ask one short clarifying question instead of searching.

It is an AND of three, so on paper it only fires on a request that gave
nothing at all. If it feels stricter than that in use, the model is applying it
more eagerly than it is written.

**Nothing breaks if it always searches.** A `PropertySearch` with no filters
set is exactly what Discover runs on load — it returns the whole catalogue,
paginated, 1,085 properties. There is no query that requires a district or a
price.

So the answer is not "make it more lenient", it is **remove the gate**:

- always search with whatever was given, however little that is;
- say in one line what was taken from the phrase, which is what keeps the
  traceability claim honest (R12);
- name the single filter most worth adding, as a suggestion rather than a
  question.

A dead end costs a visitor a turn and tells them nothing. A wide result set
with "that is everything in Achrafieh — a budget would narrow it fastest" costs
them nothing and teaches them how to ask.

### 8. Removing the count-up animation — agreed

Agreed, and the reason is worth writing down because it generalises.

`AnimationUtil.animateCount` runs on nine labels: five on the agent dashboard,
four on My properties. It counts from 0 to the real figure over 300–350 ms.

Why it should go:

- **It animates the wrong thing.** Motion is for something that changed. These
  numbers did not change; the panel simply opened. Animating them says "look at
  this" about a figure nobody asked to be impressed by.
- **It delays the one thing the panel is for.** For a third of a second the
  dashboard shows five zeros. An agent opening it to see what is overdue reads
  a wrong number first.
- **It is a template gesture.** It is what a dashboard demo does, and it reads
  as decoration applied to a screen rather than as a screen designed around
  what it holds.

What changes: `animateCount` is deleted, and each label gets its value set
directly. The KPI cards keep the panel's existing entrance fade, so the screen
still arrives rather than snapping — the figures inside it are simply correct
from the first frame.

Nothing else uses it, so this removes a method rather than adding a flag.
