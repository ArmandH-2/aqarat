# Visual design

One stylesheet: `src/main/resources/css/app.css`. Every panel uses these classes. No inline
styles in FXML, no `setStyle()` in Java, no per-panel CSS files.

Default JavaFX looks like a 2008 Windows utility. Twenty panels styled ad hoc will look like
twenty different applications. This page exists so they look like one.

---

## Palette

Defined once as looked-up colours in `.root`, then referenced everywhere else. Change a value
here and the whole application follows.

| Token | Value | Used for |
|---|---|---|
| `-c-bg` | `#F7F7F5` | Page background |
| `-c-surface` | `#FFFFFF` | Cards, tables, panels, inputs |
| `-c-surface-alt` | `#F1F1ED` | Sidebar, table headers, hover rows |
| `-c-border` | `#E3E3DF` | All hairlines |
| `-c-border-strong` | `#CFCFC9` | Input borders, focus outlines |
| `-c-text` | `#1A1A18` | Primary text |
| `-c-text-soft` | `#6B6B66` | Labels, secondary text |
| `-c-text-mute` | `#9A9A94` | Placeholders, disabled, hints |
| `-c-accent` | `#1F6F5C` | Primary actions, active nav, links |
| `-c-accent-dark` | `#185746` | Hover and pressed |
| `-c-accent-soft` | `#E6F1ED` | Active nav background, accent pills |
| `-c-good` | `#2E7D32` / bg `#E8F3E8` | Available, paid, confirmed, OK |
| `-c-warn` | `#B26A00` / bg `#FDF1DF` | Pending review, needs info, above market |
| `-c-bad` | `#B3261E` / bg `#FBEAE9` | Rejected, overdue, implausible |
| `-c-info` | `#2A5D8F` / bg `#E8F0F7` | Reserved, under contract, declared |
| `-c-neutral` | `#5A5A55` / bg `#EFEFEB` | Closed, withdrawn, cancelled |

A muted teal rather than the usual corporate blue — it reads as considered rather than default,
and it stays legible against every status colour above.

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

`Segoe UI` first, since you are both on Windows, then a generic fallback.

| Role | Size | Weight |
|---|---|---|
| Page title | 20px | 600 |
| Section heading | 15px | 600 |
| Body and table cells | 13px | 400 |
| Field labels, captions | 12px | 500 |
| Status pills, hints | 11px | 500 |

Two weights only, 400 and 600. Sentence case everywhere — no ALL CAPS headers, no Title Case.

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

```css
.root {
    -c-bg:            #F7F7F5;
    -c-surface:       #FFFFFF;
    -c-surface-alt:   #F1F1ED;
    -c-border:        #E3E3DF;
    -c-border-strong: #CFCFC9;
    -c-text:          #1A1A18;
    -c-text-soft:     #6B6B66;
    -c-text-mute:     #9A9A94;
    -c-accent:        #1F6F5C;
    -c-accent-dark:   #185746;
    -c-accent-soft:   #E6F1ED;

    -fx-font-family: "Segoe UI", "Inter", sans-serif;
    -fx-font-size: 13px;
    -fx-background-color: -c-bg;
    -fx-text-fill: -c-text;
}

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

.content { -fx-padding: 24px; -fx-background-color: -c-bg; }

.card { -fx-background-color: -c-surface; -fx-background-radius: 6px;
        -fx-border-color: -c-border; -fx-border-radius: 6px; -fx-padding: 16px; }

.button           { -fx-background-radius: 6px; -fx-padding: 6 14 6 14;
                    -fx-pref-height: 32px; -fx-cursor: hand; -fx-font-size: 13px; }
.button-primary   { -fx-background-color: -c-accent; -fx-text-fill: white; }
.button-primary:hover { -fx-background-color: -c-accent-dark; }
.button-secondary { -fx-background-color: -c-surface; -fx-text-fill: -c-text;
                    -fx-border-color: -c-border-strong; -fx-border-radius: 6px; }
.button-danger    { -fx-background-color: -c-surface; -fx-text-fill: #B3261E;
                    -fx-border-color: #B3261E; -fx-border-radius: 6px; }

.text-field, .text-area, .combo-box, .date-picker {
    -fx-background-color: -c-surface; -fx-background-radius: 4px;
    -fx-border-color: -c-border-strong; -fx-border-radius: 4px;
    -fx-pref-height: 32px; -fx-padding: 0 8 0 8;
}
.text-field:focused, .combo-box:focused, .date-picker:focused {
    -fx-border-color: -c-accent;
}
.field-invalid { -fx-border-color: #B3261E; }
.field-error   { -fx-font-size: 12px; -fx-text-fill: #B3261E; }

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
.pill-good   { -fx-background-color: #E8F3E8; -fx-text-fill: #2E7D32; }
.pill-warn   { -fx-background-color: #FDF1DF; -fx-text-fill: #B26A00; }
.pill-bad    { -fx-background-color: #FBEAE9; -fx-text-fill: #B3261E; }
.pill-info   { -fx-background-color: #E8F0F7; -fx-text-fill: #2A5D8F; }
.pill-neutral{ -fx-background-color: #EFEFEB; -fx-text-fill: #5A5A55; }

.empty-state { -fx-text-fill: -c-text-soft; -fx-font-size: 13px; -fx-alignment: center; }
```
