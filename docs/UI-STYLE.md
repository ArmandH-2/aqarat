# Aqarat UI & UX Design System (Modern Upgrade)

This design system defines the visual identity, tokens, component library, and interaction patterns for the Aqarat Real Estate Management Platform.

---

## 1. Design Philosophy

- **Modern Desktop Experience**: Clean, airy, high-contrast layouts tailored for 1280×800+ resolution with fluid responsive behavior.
- **Visual Depth & Elevation**: Refined multi-layered surfaces, subtle drop shadows, clean borders, and smooth hover micro-interactions.
- **Brand Identity**: British Racing Green (`#004225` / `#0B5331`) balanced with crisp warm whites, soft neutrals, and vibrant status indicators.
- **Exceptional Craft**: Polished typography, consistent rhythm, clear visual hierarchy, rich data cards, and responsive state feedback.

---

## 2. Palette & Tokens

### Core Colors & Surfaces

| Token | Value | Description |
|---|---|---|
| `-c-bg` | `#F4F5F4` | Application canvas background |
| `-c-surface` | `#FFFFFF` | Primary card, panel, and input background |
| `-c-surface-subtle` | `#F8F9F8` | Table headers, secondary container surfaces |
| `-c-surface-hover` | `#EDF0EE` | Hover states for list rows, table rows, and tiles |
| `-c-surface-active` | `#E3E8E4` | Active/selected background states |
| `-c-border-subtle` | `#E2E5E2` | Hairline dividers and card borders |
| `-c-border-medium` | `#CDD2CD` | Input borders, secondary button outlines |
| `-c-border-strong` | `#9FA7A0` | Focused boundaries and high-emphasis outlines |

### Brand & Accents

| Token | Value | Description |
|---|---|---|
| `-c-primary` | `#004225` | Deep British Racing Green (Primary brand) |
| `-c-primary-light` | `#0B5D36` | Primary hover & interactive elements |
| `-c-primary-dark` | `#002E19` | Primary pressed state |
| `-c-primary-tint` | `#E6EFEA` | Soft green background for pills, highlights, active nav |
| `-c-primary-subtle` | `#F0F6F2` | Subtle section backgrounds |
| `-c-on-primary` | `#FFFFFF` | Text on primary brand surfaces |

### Typography Colors

| Token | Value | Description |
|---|---|---|
| `-c-text-primary` | `#141A16` | Main headings, key data, body text |
| `-c-text-secondary` | `#4F5952` | Subheadings, section titles, field labels |
| `-c-text-muted` | `#78837B` | Captions, hints, placeholders, secondary timestamps |
| `-c-text-disabled` | `#A6B0A8` | Disabled text |

### Status Colors & Tints

| Status | Foreground | Background Tint | Border | Usage |
|---|---|---|---|---|
| **Success / Good** | `#1B6E32` | `#E8F5EC` | `#BCE6C8` | Available, Paid, Confirmed, Completed, OK |
| **Warning / Alert** | `#9A5B00` | `#FEF5E7` | `#FBD89F` | Pending Review, Needs Info, Partially Paid, Above Market |
| **Danger / Bad** | `#C5221F` | `#FDECEB` | `#F8BCBA` | Rejected, Overdue, Implausible, Terminated |
| **Info / Progress** | `#1A65B8` | `#EBF3FC` | `#BCD8F8` | Reserved, Under Contract, Active, Draft |
| **Neutral / Muted** | `#525D56` | `#EFF2F0` | `#D5DDD7` | Closed, Withdrawn, Inactive, Expired |

---

## 3. Elevation & Shadows

JavaFX supports smooth drop shadows via `-fx-effect`. Use layered shadows to establish clear visual depth:

- **Level 0 (Flat)**: Hairline border `-c-border-subtle`, no shadow. (Dividers, inline containers)
- **Level 1 (Card Default)**: `-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.04), 8, 0, 0, 2);`
- **Level 2 (Card Hover & Dropdowns)**: `-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.08), 16, 0, 0, 4);`
- **Level 3 (Modals, Popovers & Dialogs)**: `-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.16), 24, 0, 0, 8);`

---

## 4. Typography Scale

Font Family: `"Inter", "Segoe UI", -apple-system, sans-serif;`

| Style Class | Size | Weight | Line Height / Role |
|---|---|---|---|
| `.hero-title` | 26px | 700 (Bold) | Dashboard greetings, main landing headers |
| `.page-title` | 20px | 600 (Semi-Bold) | Top-level view headings |
| `.section-title` | 16px | 600 (Semi-Bold) | Card headers, form section headers |
| `.metric-value` | 24px | 700 (Bold) | Key KPI metrics and summary numbers |
| `.body` | 13px | 400 (Regular) | Default content and tables |
| `.body-medium` | 13px | 500 (Medium) | Emphasized body text |
| `.label-caption` | 12px | 500 (Medium) | Form input labels, table column headers |
| `.hint-caption` | 11px | 400 (Regular) | Metadata, input help text, timestamps |
| `.badge-text` | 11px | 600 (Semi-Bold) | Status badges, category pills |

---

## 5. Components & Interaction Guidelines

### A. Navigation Sidebar
- Width: `240px`, painted in dark luxury green (`-c-primary-dark` to `-c-primary`) or crisp light mode.
- Navigation items: `40px` tall, `8px` corner radius, smooth hover lift, leading icon + label, trailing count badge for queues.
- Active state: `-c-primary-tint` background with bold `-c-primary` text and an accent indicator bar.

### B. Action Buttons
- **Primary**: `-c-primary` solid fill, white text, 6px radius, subtle hover glow/darken.
- **Secondary / Outline**: White fill, `-c-border-medium` outline, dark text. On hover: `-c-primary` border and text.
- **Ghost**: Transparent background, `-c-text-secondary`. On hover: `-c-surface-hover`.
- **Danger**: Soft red tint or solid red for destructive confirmations.
- **Icon Buttons**: Circular or square 32×32 / 36×36 buttons with centered vector icons.

### C. Rich Property & Listing Cards
- Rounded corners (`8px` to `10px`), white surface, `1px` subtle border, elevation Level 1.
- Photo thumbnail container with aspect ratio handling, gradient scrim for text overlay, and status badge pill anchored at top-right.
- Structured pricing block (large bold price, price/m² subscript, deal type tag).
- Key specs chips (Bedrooms, Bathrooms, Area in m², Parking).

### D. Metric & KPI Stat Cards
- Layout: Icon badge (top right), secondary label (top left), large bold metric value, trend indicator (e.g. `+8.4% vs last month` in green/red).

### E. Data Tables
- Header: `-c-surface-subtle` background with `-c-text-secondary` 600 labels, `38px` height.
- Rows: `44px` height, subtle bottom divider, hover highlight (`-c-surface-hover`), selected row highlighted in `-c-primary-tint`.
- Right-aligned numerical columns (Price, Area, Date).
- Action columns with compact icon buttons (View, Edit, More actions).

### F. Inputs & Form Fields
- Height: `36px`, rounded `6px`, `-c-border-medium` border.
- Focus State: Highlighted `-c-primary` outline with subtle focus ring.
- Inline Validation: Red border (`-c-bad`) and clear error caption underneath the input.
- Search Input: Integrated search icon on the left and quick-clear `(×)` button on the right.

### G. Status Pills & Badges
- Height: `24px`, padding `3px 10px`, radius `12px` (pill shape).
- Includes colored bullet indicator or subtle icon alongside status text.

### H. Async Loading & State Feedback
- **Loading Spinners & Progress**: For background queries or data fetches, show smooth progress bars or centered spinner overlays.
- **Empty States**: Centered illustration/icon, friendly title, descriptive helper text, and a primary "Create / Reset" action button.

---

## 6. Global CSS (`src/main/resources/css/app.css`) Reference Skeleton

```css
/* ==========================================================================
   AQARAT DESIGN SYSTEM - MODERN PALETTE & TOKENS
   ========================================================================== */

.root {
    /* Surfaces */
    -c-bg:             #F4F5F4;
    -c-surface:        #FFFFFF;
    -c-surface-subtle: #F8F9F8;
    -c-surface-hover:  #EDF0EE;
    -c-surface-active: #E3E8E4;

    /* Borders */
    -c-border-subtle:  #E2E5E2;
    -c-border-medium:  #CDD2CD;
    -c-border-strong:  #9FA7A0;

    /* Typography */
    -c-text-primary:   #141A16;
    -c-text-secondary: #4F5952;
    -c-text-muted:     #78837B;
    -c-text-disabled:  #A6B0A8;

    /* Brand Accent */
    -c-primary:        #004225;
    -c-primary-light:  #0B5D36;
    -c-primary-dark:   #002E19;
    -c-primary-tint:   #E6EFEA;
    -c-primary-subtle: #F0F6F2;
    -c-on-primary:     #FFFFFF;

    /* Status Tokens */
    -c-good:           #1B6E32;  -c-good-bg:    #E8F5EC;  -c-good-border:    #BCE6C8;
    -c-warn:           #9A5B00;  -c-warn-bg:    #FEF5E7;  -c-warn-border:    #FBD89F;
    -c-bad:            #C5221F;  -c-bad-bg:     #FDECEB;  -c-bad-border:     #F8BCBA;
    -c-info:           #1A65B8;  -c-info-bg:    #EBF3FC;  -c-info-border:    #BCD8F8;
    -c-neutral:        #525D56;  -c-neutral-bg: #EFF2F0;  -c-neutral-border: #D5DDD7;

    -fx-font-family: "Inter", "Segoe UI", -apple-system, sans-serif;
    -fx-font-size: 13px;
    -fx-background-color: -c-bg;
    -fx-text-fill: -c-text-primary;
}

.label {
    -fx-text-fill: -c-text-primary;
}

/* Typography Hierarchy */
.hero-title    { -fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: -c-text-primary; }
.page-title    { -fx-font-size: 20px; -fx-font-weight: 600; -fx-text-fill: -c-text-primary; }
.section-title { -fx-font-size: 16px; -fx-font-weight: 600; -fx-text-fill: -c-text-primary; }
.metric-value  { -fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: -c-text-primary; }
.label-soft    { -fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: -c-text-secondary; }
.hint          { -fx-font-size: 11px; -fx-font-weight: 400; -fx-text-fill: -c-text-muted; }

/* Sidebar Navigation */
.sidebar {
    -fx-background-color: -c-primary;
    -fx-pref-width: 240px;
}
.sidebar .section-title { -fx-text-fill: -c-on-primary; }
.sidebar .label-soft, .sidebar .hint { -fx-text-fill: -c-primary-tint; }

.nav-item {
    -fx-background-radius: 8px;
    -fx-padding: 10 14 10 14;
    -fx-text-fill: -c-primary-tint;
    -fx-cursor: hand;
    -fx-font-size: 13px;
    -fx-font-weight: 500;
}
.nav-item:hover {
    -fx-background-color: derive(-c-primary, 12%);
    -fx-text-fill: #FFFFFF;
}
.nav-item.active {
    -fx-background-color: -c-primary-tint;
    -fx-text-fill: -c-primary;
    -fx-font-weight: 600;
}

/* Cards & Containers with Elevation */
.card {
    -fx-background-color: -c-surface;
    -fx-background-radius: 10px;
    -fx-border-color: -c-border-subtle;
    -fx-border-radius: 10px;
    -fx-padding: 18px;
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.04), 8, 0, 0, 2);
}
.card-hoverable:hover {
    -fx-border-color: -c-border-medium;
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.08), 14, 0, 0, 4);
    -fx-cursor: hand;
}

/* Stat KPI Tile */
.stat-tile {
    -fx-background-color: -c-surface;
    -fx-background-radius: 10px;
    -fx-border-color: -c-border-subtle;
    -fx-border-radius: 10px;
    -fx-padding: 16px;
    -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.03), 6, 0, 0, 2);
}

/* Buttons */
.button {
    -fx-background-radius: 6px;
    -fx-padding: 8 16 8 16;
    -fx-pref-height: 34px;
    -fx-cursor: hand;
    -fx-font-size: 13px;
    -fx-font-weight: 500;
}
.button-primary {
    -fx-background-color: -c-primary;
    -fx-text-fill: -c-on-primary;
}
.button-primary:hover {
    -fx-background-color: -c-primary-light;
    -fx-effect: dropshadow(gaussian, rgba(0, 66, 37, 0.25), 8, 0, 0, 2);
}
.button-primary:pressed {
    -fx-background-color: -c-primary-dark;
}
.button-secondary {
    -fx-background-color: -c-surface;
    -fx-text-fill: -c-text-primary;
    -fx-border-color: -c-border-medium;
    -fx-border-radius: 6px;
}
.button-secondary:hover {
    -fx-border-color: -c-primary;
    -fx-text-fill: -c-primary;
    -fx-background-color: -c-surface-subtle;
}
.button-danger {
    -fx-background-color: -c-bad-bg;
    -fx-text-fill: -c-bad;
    -fx-border-color: -c-bad-border;
    -fx-border-radius: 6px;
}
.button-danger:hover {
    -fx-background-color: -c-bad;
    -fx-text-fill: #FFFFFF;
}

/* Inputs & Form Controls */
.text-field, .text-area, .combo-box, .date-picker {
    -fx-background-color: -c-surface;
    -fx-background-radius: 6px;
    -fx-border-color: -c-border-medium;
    -fx-border-radius: 6px;
    -fx-pref-height: 34px;
    -fx-padding: 0 10 0 10;
    -fx-font-size: 13px;
}
.text-field:hover, .text-area:hover, .combo-box:hover, .date-picker:hover {
    -fx-border-color: -c-border-strong;
}
.text-field:focused, .text-area:focused, .combo-box:focused, .date-picker:focused {
    -fx-border-color: -c-primary;
    -fx-effect: dropshadow(gaussian, rgba(0, 66, 37, 0.15), 4, 0, 0, 1);
}
.field-invalid {
    -fx-border-color: -c-bad;
}
.field-error {
    -fx-font-size: 12px;
    -fx-text-fill: -c-bad;
}

/* Tables */
.table-view {
    -fx-background-color: -c-surface;
    -fx-border-color: -c-border-subtle;
    -fx-border-radius: 8px;
    -fx-background-radius: 8px;
}
.table-view .column-header {
    -fx-background-color: -c-surface-subtle;
    -fx-pref-height: 38px;
    -fx-border-color: transparent transparent -c-border-subtle transparent;
}
.table-view .column-header .label {
    -fx-font-size: 12px;
    -fx-text-fill: -c-text-secondary;
    -fx-font-weight: 600;
    -fx-alignment: center-left;
}
.table-row-cell {
    -fx-pref-height: 42px;
    -fx-border-color: transparent transparent -c-border-subtle transparent;
}
.table-row-cell:hover {
    -fx-background-color: -c-surface-hover;
}
.table-row-cell:selected {
    -fx-background-color: -c-primary-tint;
    -fx-text-fill: -c-text-primary;
}

/* Status Badges & Pills */
.pill {
    -fx-background-radius: 12px;
    -fx-padding: 4 10 4 10;
    -fx-font-size: 11px;
    -fx-font-weight: 600;
}
.pill-good    { -fx-background-color: -c-good-bg;    -fx-text-fill: -c-good;    -fx-border-color: -c-good-border;    -fx-border-radius: 12px; }
.pill-warn    { -fx-background-color: -c-warn-bg;    -fx-text-fill: -c-warn;    -fx-border-color: -c-warn-border;    -fx-border-radius: 12px; }
.pill-bad     { -fx-background-color: -c-bad-bg;     -fx-text-fill: -c-bad;     -fx-border-color: -c-bad-border;     -fx-border-radius: 12px; }
.pill-info    { -fx-background-color: -c-info-bg;    -fx-text-fill: -c-info;    -fx-border-color: -c-info-border;    -fx-border-radius: 12px; }
.pill-neutral { -fx-background-color: -c-neutral-bg; -fx-text-fill: -c-neutral; -fx-border-color: -c-neutral-border; -fx-border-radius: 12px; }

/* Empty States */
.empty-state {
    -fx-text-fill: -c-text-muted;
    -fx-font-size: 13px;
    -fx-alignment: center;
}
```
