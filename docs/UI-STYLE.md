# Visual design

One stylesheet: `src/main/resources/css/app.css`. Every panel uses these classes. No inline
styles in FXML, no `setStyle()` in Java, no per-panel CSS files.

Default JavaFX looks like a 2008 Windows utility. Twenty panels styled ad hoc will look like
twenty different applications. This page exists so they look like one.

The brand this implements is in `Artifacts/` (gitignored — it holds the source logo). Where
that material and this page disagree, the reason is written down below rather than settled
per panel.

---

## Palette

Defined once as looked-up colours in `.root`, then referenced everywhere else. Change a value
here and the whole application follows.

| Token | Value | Used for |
|---|---|---|
| `-c-bg` | `#F8F8F6` | Page background |
| `-c-surface` | `#FFFFFF` | Cards, tables, panels, inputs |
| `-c-surface-alt` | `#F1F1EE` | Sidebar, table headers, hover rows |
| `-c-border` | `#D7D8D5` | All hairlines |
| `-c-border-strong` | `#BFC0BC` | Input borders, focus outlines |
| `-c-text` | `#1F2320` | Primary text |
| `-c-text-soft` | `#5B5B57` | Labels, secondary text |
| `-c-text-mute` | `#70706B` | Placeholders, disabled, hints |
| `-c-accent` | `#004225` | Primary actions, active nav, links |
| `-c-accent-dark` | `#00301B` | Hover and pressed |
| `-c-accent-soft` | `#E6EEE9` | Active nav background, accent pills |
| `-c-stone` | `#8B8680` | Architectural detail and heavier dividers. **Never text.** |
| `-c-good` | `#26682B` / bg `#E8F3E8` | Available, paid, confirmed, OK |
| `-c-warn` | `#8F5800` / bg `#FDF1DF` | Pending review, needs info, above market |
| `-c-bad` | `#B3261E` / bg `#FBEAE9` | Rejected, overdue, implausible |
| `-c-info` | `#255C99` / bg `#E8F0F7` | Reserved, under contract, declared |
| `-c-neutral` | `#5B5B57` / bg `#EFEFEB` | Closed, withdrawn, cancelled |

British Racing Green, from the brand. It carries the identity, and at 11.6:1 on white it is
legible anywhere — including reversed out as white text on a green button.

### Contrast — measured, not estimated

Status pill text is 11px, so every pill needs the full 4.5:1 for normal text. Measured against
the background each colour is actually used on:

| Pair | Ratio |
|---|---|
| `-c-accent` on `-c-surface` | 11.63:1 |
| white on `-c-accent` | 11.63:1 |
| `-c-text` on `-c-bg` | 14.97:1 |
| `-c-text-soft` on `-c-bg` | 6.41:1 |
| `-c-text-mute` on `-c-bg` | 4.68:1 |
| `-c-good` on its tint | 5.95:1 |
| `-c-warn` on its tint | 5.28:1 |
| `-c-bad` on its tint | 5.62:1 |
| `-c-info` on its tint | 5.94:1 |
| `-c-neutral` on its tint | 5.92:1 |

Two values were changed because they failed. `-c-text-mute` was `#9A9A94`, which measured
**2.66:1** — it never passed. The brand's own warning amber `#C7831A` measures **2.81:1** on
the warning tint, so the darker `#8F5800` is used instead; it is the same hue, just deep
enough to read.

**Warm Stone Gray is not a text colour.** `#8B8680` measures 3.39:1 on the page background,
which is large-text-only. It belongs to the logo's keystone and to dividers. Reach for
`-c-text-soft` when you want quieter text.

## Status colours

Never invent a colour at the call site. Every status maps to exactly one pill style.

| Status | Style |
|---|---|
| `AVAILABLE`, `PAID`, `CONFIRMED`, `COMPLETED`, `OK` | good |
| `PENDING_REVIEW`, `NEEDS_INFO`, `PARTIALLY_PAID`, `ABOVE_MARKET`, `DECLARED`, `REQUESTED` | warn |
| `REJECTED`, `OVERDUE`, `IMPLAUSIBLE`, `TERMINATED`, `NO_SHOW` | bad |
| `RESERVED`, `UNDER_CONTRACT`, `ACTIVE`, `DRAFT` | info |
| `CLOSED`, `WITHDRAWN`, `CANCELLED`, `LAPSED`, `EXPIRED`, `INACTIVE` | neutral |

## Typography

`Inter` first, as the brand specifies, then `Segoe UI`. Inter is not installed on Windows by
default, so on your machines this resolves to Segoe UI — which is why Segoe stays second
rather than being dropped. Listing Inter first costs nothing and is correct anywhere it does
exist.

| Role | Size | Weight |
|---|---|---|
| Page title | 20px | 600 |
| Section heading | 15px | 600 |
| Body and table cells | 13px | 400 |
| Field labels, captions | 12px | 500 |
| Status pills, hints | 11px | 500 |

Two weights only, 400 and 600. Sentence case everywhere — no ALL CAPS headers, no Title Case.

**This scale deliberately differs from the brand's.** The brand sheet specifies 16px body and
a 40/32/24/20 heading scale. That is a scale for a web page viewed at arm's length. This is a
desktop application in a 1280×800 window showing tables of properties, and 16px body would
push roughly a third of the rows off screen. The brand's *rules* — sentence case, clarity
before personality, generous spacing, consistent alignment — apply here unchanged; only the
numbers differ. Use the brand scale for the website, decks and printed material.

## Spacing

Use 4, 8, 12, 16, 24, 32. Nothing else. Content area padding is 24. Card padding is 16. Gap
between form rows is 12. Gap between sections is 24.

Corner radius: 6px for cards and buttons, 4px for inputs and pills.

**No drop shadows, no gradients, no glow.** JavaFX renders all three badly and they are the
fastest way to make a desktop app look amateur. Flat surfaces separated by hairlines.

## Layout

Window opens at 1280×800, minimum 1100×700.

Sidebar is 220px fixed. Items are 36px tall with 8px radius and 12px horizontal padding.
The active item gets `-c-accent-soft` background and `-c-accent` text.

Content area scrolls; the sidebar does not.

## Components

**Buttons** — 32px tall, 14px horizontal padding, 6px radius, 13px text.

- Primary: `-c-accent` background, white text. One per screen, at most.
- Secondary: white background, `-c-border-strong` border, `-c-text` text.
- Danger: white background, `-c-bad` border and text. Solid red only inside a confirmation dialog.
- Disabled: 45% opacity, no hover.

**Inputs** — 32px tall, white, 1px `-c-border-strong`, 4px radius. On focus the border becomes
`-c-accent`. Invalid fields get a `-c-bad` border and a 12px message directly beneath. Never use
a dialog for field validation.

**Tables** — header row 36px on `-c-surface-alt`, 12px `-c-text-soft` labels. Rows 40px with a
hairline between them, hover `-c-surface-alt`, selected `-c-accent-soft`. Money and area columns
right-aligned; everything else left. No vertical gridlines.

**Cards** — white, 1px `-c-border`, 6px radius, 16px padding. Used for property results,
dashboard tiles, and the valuation panel.

**Status pills** — 22px tall, 4px radius, 8px horizontal padding, 11px text. Background and
text from the status table above.

**Empty states** — every list needs one. Centred, `-c-text-soft`, a single sentence saying what
would appear here and how to make it appear. "No submissions waiting for review." Not "No data."

**Loading** — nothing in this application should take long enough to need a spinner. If
something does, the query is wrong.

## Icons

None. There is no icon library in the dependency list and none is being added — an icon font is
one more thing that can fail to load, and a text-only interface with good spacing looks
deliberate rather than unfinished.

If you decide otherwise later, `org.kordamp.ikonli:ikonli-javafx` is the one to add, and it
should be a conversation before it is a commit.

## The logo

Derived assets live in `src/main/resources/images/` and are committed. The source lockup is in
`Artifacts/`, which is gitignored because it is a megabyte of PNG nobody needs at runtime.

| File | Use |
|---|---|
| `logo-32/64/128/256.png` | Window and task bar icon, sidebar mark |
| `wordmark.png` | Name alone, where the mark is already present |
| `lockup.png` | Mark above the name — sign-in window, about, splash |

All have transparent backgrounds, so they sit on any surface. There is no SVG: JavaFX cannot
render one without a third-party library, and none is being added.

Clearspace is half the mark's width on every side. Never recolour it, never stretch it, and do
not place the green mark on a dark background — it is drawn for light surfaces.

## Formatting

All of this lives in `util/Format` and nowhere else.

| Value | Format | Example |
|---|---|---|
| Sale price | `$` + thousands separator, no decimals | `$187,000` |
| Monthly rent | `$` + separator + `/mo` | `$1,250/mo` |
| Payment amount | `$` + separator + 2 decimals | `$4,166.67` |
| Area | integer + ` m²` | `145 m²` |
| Price per m² | `$` + separator + ` /m²` | `$2,340 /m²` |
| Date | `d MMM yyyy` | `4 Aug 2026` |
| Date and time | `d MMM yyyy, HH:mm` | `4 Aug 2026, 14:30` |
| Percentage | one decimal + `%` | `23.4%` |

Never print a raw enum to the user. `PENDING_REVIEW` is displayed as `Pending review`, and the
conversion happens in one place.

---

## app.css skeleton

Start from this. Add to it; do not restructure it.

Every colour below is a token. A literal hex outside the `.root` block is a bug — it is how a
palette change stops propagating and panels start disagreeing with each other.

```css
.root {
    -c-bg:            #F8F8F6;
    -c-surface:       #FFFFFF;
    -c-surface-alt:   #F1F1EE;
    -c-border:        #D7D8D5;
    -c-border-strong: #BFC0BC;
    -c-text:          #1F2320;
    -c-text-soft:     #5B5B57;
    -c-text-mute:     #70706B;
    -c-accent:        #004225;
    -c-accent-dark:   #00301B;
    -c-accent-soft:   #E6EEE9;
    -c-stone:         #8B8680;

    -c-good:          #26682B;  -c-good-bg:    #E8F3E8;
    -c-warn:          #8F5800;  -c-warn-bg:    #FDF1DF;
    -c-bad:           #B3261E;  -c-bad-bg:     #FBEAE9;
    -c-info:          #255C99;  -c-info-bg:    #E8F0F7;
    -c-neutral:       #5B5B57;  -c-neutral-bg: #EFEFEB;

    -fx-font-family: "Inter", "Segoe UI", "Arial", sans-serif;
    -fx-font-size: 13px;
    -fx-background-color: -c-bg;
    -fx-text-fill: -c-text;
}

/* Modena gives every Label its own #333333 fill, which beats the -fx-text-fill
   inherited from .root. Without this the palette's text colour never applies.
   It comes first so the rules below still win. */
.label { -fx-text-fill: -c-text; }

.page-title    { -fx-font-size: 20px; -fx-font-weight: 600; }
.section-title { -fx-font-size: 15px; -fx-font-weight: 600; }
.label-soft    { -fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: -c-text-soft; }
.hint          { -fx-font-size: 11px; -fx-text-fill: -c-text-mute; }

.sidebar        { -fx-background-color: -c-surface-alt;
                  -fx-border-color: transparent -c-border transparent transparent;
                  -fx-border-width: 0 1 0 0; -fx-pref-width: 220px; }
.nav-item       { -fx-background-radius: 8px; -fx-padding: 8 12 8 12;
                  -fx-text-fill: -c-text-soft; -fx-cursor: hand; }
.nav-item:hover { -fx-background-color: derive(-c-surface-alt, -4%); }
.nav-item.active{ -fx-background-color: -c-accent-soft; -fx-text-fill: -c-accent;
                  -fx-font-weight: 600; }

/* Modena's hyperlink is its own blue and ignores the palette. */
.hyperlink          { -fx-text-fill: -c-accent; -fx-border-color: transparent;
                      -fx-padding: 0; -fx-cursor: hand; }
.hyperlink:visited  { -fx-text-fill: -c-accent; }
.hyperlink:hover    { -fx-text-fill: -c-accent-dark; -fx-underline: true; }

.content { -fx-padding: 24px; -fx-background-color: -c-bg; }

.card { -fx-background-color: -c-surface; -fx-background-radius: 6px;
        -fx-border-color: -c-border; -fx-border-radius: 6px; -fx-padding: 16px; }

.button           { -fx-background-radius: 6px; -fx-padding: 6 14 6 14;
                    -fx-pref-height: 32px; -fx-cursor: hand; -fx-font-size: 13px; }
.button-primary   { -fx-background-color: -c-accent; -fx-text-fill: white; }
.button-primary:hover { -fx-background-color: -c-accent-dark; }
.button-secondary { -fx-background-color: -c-surface; -fx-text-fill: -c-text;
                    -fx-border-color: -c-border-strong; -fx-border-radius: 6px; }
.button-danger    { -fx-background-color: -c-surface; -fx-text-fill: -c-bad;
                    -fx-border-color: -c-bad; -fx-border-radius: 6px; }

.text-field, .text-area, .combo-box, .date-picker {
    -fx-background-color: -c-surface; -fx-background-radius: 4px;
    -fx-border-color: -c-border-strong; -fx-border-radius: 4px;
    -fx-pref-height: 32px; -fx-padding: 0 8 0 8;
}
.text-field:focused, .combo-box:focused, .date-picker:focused {
    -fx-border-color: -c-accent;
}
.field-invalid { -fx-border-color: -c-bad; }
.field-error   { -fx-font-size: 12px; -fx-text-fill: -c-bad; }

.table-view { -fx-background-color: -c-surface; -fx-border-color: -c-border;
              -fx-border-radius: 6px; -fx-background-radius: 6px; }
.table-view .column-header { -fx-background-color: -c-surface-alt; -fx-pref-height: 36px; }
.table-view .column-header .label { -fx-font-size: 12px; -fx-text-fill: -c-text-soft;
                                    -fx-font-weight: 500; -fx-alignment: center-left; }
.table-row-cell { -fx-pref-height: 40px; -fx-border-color: transparent transparent -c-border transparent; }
.table-row-cell:hover    { -fx-background-color: -c-surface-alt; }
.table-row-cell:selected { -fx-background-color: -c-accent-soft; -fx-text-fill: -c-text; }
.numeric { -fx-alignment: center-right; }

.pill        { -fx-background-radius: 4px; -fx-padding: 3 8 3 8; -fx-font-size: 11px;
               -fx-font-weight: 500; }
.pill-good   { -fx-background-color: -c-good-bg;    -fx-text-fill: -c-good; }
.pill-warn   { -fx-background-color: -c-warn-bg;    -fx-text-fill: -c-warn; }
.pill-bad    { -fx-background-color: -c-bad-bg;     -fx-text-fill: -c-bad; }
.pill-info   { -fx-background-color: -c-info-bg;    -fx-text-fill: -c-info; }
.pill-neutral{ -fx-background-color: -c-neutral-bg; -fx-text-fill: -c-neutral; }

.empty-state { -fx-text-fill: -c-text-soft; -fx-font-size: 13px; -fx-alignment: center; }
```
