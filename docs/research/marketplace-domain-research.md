# Marketplace domain research

How real-world real estate marketplaces and property management systems actually handle
listing verification, viewings, contact, and money — and which parts of that a two-week
student project should copy, fake, or deliberately refuse to build.

External research only. Nothing here is a design decision for Aqarat; it is the evidence a
design decision can be argued from. `docs/DESIGN.md` remains the authority on what we build.

**Verification legend**

| Mark | Meaning |
|---|---|
| (no mark) | Claim taken from the cited page, fetched and read directly. |
| *snippet* | Claim comes from a search index summary of the cited page. The page itself returns HTTP 403 to automated fetching (Zendesk-hosted help centres and zillow.com both do this). Treat as strong but second-hand. |
| **unverified** | Could not confirm from a primary source. Recorded because it is widely repeated, not because it is proven. |

One structural note before anything else: `docs/DESIGN.md` places Aqarat in **Lebanon**, and
the brief for this research asked about **Jordan's** Department of Land and Survey. Both are
covered in section 1. If the two ever have to agree, DESIGN.md wins.

---

## 1. Listing and ownership verification

### 1.1 There are three gate models, not one

Every platform researched falls into one of three patterns. This is the single most useful
finding in the section, because it determines *where* verification lives in the data model.

| Model | Who may list | Where the check happens | Examples |
|---|---|---|---|
| **Agent-gated** | Only licensed/registered brokerages | Once, at onboarding, against a licence | Rightmove, dubizzle Property (UAE sale listings) |
| **Permit-per-listing** | Licensed brokers, *and* each individual advert needs its own regulator-issued number | Once at onboarding **and** once per listing, against a government API | Bayut, Property Finder, dubizzle (Dubai) |
| **Self-serve** | Anyone with an account | Weakly, after the fact, mostly by moderation and complaint | Zillow FSBO/FRBO, Airbnb |

Aqarat's "owner submits, agent reviews and publishes" flow is a fourth thing: a **private
moderation queue** inside one agency. That is closest to the self-serve model with a human
gate bolted on — which is a defensible design, and worth saying out loud in the report.

### 1.2 Agent-gated: Rightmove

Rightmove does not accept listings from the public at all. To join as an agent you "must be a
registered company" and "a member of a Redress Scheme"; applications go through an account
manager, not a signup form
([Rightmove customer FAQ](https://customerfaq.rightmove.co.uk/support/solutions/articles/7000048729-how-to-join-rightmove-as-a-uk-estate-agent)).

Verification is therefore **not per-listing**. Rightmove trusts the agency; the agency carries
the legal duty to have the seller's instruction. There is no title-deed upload anywhere in the
consumer flow. Rightmove also states plainly that it does not stand between the parties: "Rightmove
is not able to contact agents or landlords on your behalf or arrange viewings directly"
([Rightmove Help Centre](https://faq.rightmove.co.uk/support/solutions/articles/7000049007-i-m-interested-in-a-property-what-do-i-do-next-)).

Zoopla operates on the same agent-only commercial model. **unverified** — I did not fetch a
Zoopla membership page, so treat "Zoopla = Rightmove model" as an assumption, not a finding.

### 1.3 Permit-per-listing: the UAE (the strictest regime found)

Dubai is the clearest example anywhere of verification being a *first-class data field on the
listing row*.

- A **Trakheesi permit** is the mandatory advertising authorisation issued by the Dubai Land
  Department through the Trakheesi system and supervised by RERA. It is required before an
  advert is published on any channel — portal, print, billboard, SMS, social
  (*snippet*, [Bayut agent portal](https://www.bayut.com/agentportal/the-real-estate-agents-guide-to-applying-for-a-trakheesi-permit/);
  [MyBayut Trakheesi guide](https://www.bayut.com/mybayut/trakheesi/)).
- To obtain one the broker must already be registered with DLD with a valid broker number, and
  must supply an **advertising format** (a screenshot of the listing) plus a **marketing
  contract** — either DLD **Form A** or an **NOC from the legal owner**
  ([Bayut agent portal](https://www.bayut.com/agentportal/the-real-estate-agents-guide-to-applying-for-a-trakheesi-permit/)).
- The permit application itself carries the parcel identity: area, building name, **land
  number**, **unit number**, **municipality number**
  ([same source](https://www.bayut.com/agentportal/the-real-estate-agents-guide-to-applying-for-a-trakheesi-permit/)).
- The published listing then carries **permit number, transaction number and permit expiry
  date**, with the Trakheesi number shown in the listing footer next to the broker's BRN
  (*snippet*, [Bayut agent portal](https://www.bayut.com/agentportal/the-real-estate-agents-guide-to-applying-for-a-trakheesi-permit/)).
- The individual agent's **BRN (Broker Registration Number)** is the minimum credential to
  list, sell or lease on behalf of a client, is valid one year, and must be renewable/verifiable
  through DLD tools or the Dubai REST app
  (*snippet*, [Property Finder blog on Dubai licensing](https://www.propertyfinder.ae/blog/real-estate-license-dubai/)).
- Since 24 April 2023 DLD's **Madmoun** service puts a QR code on adverts. Scanning it returns
  the complete advertisement information — advertising company, property condition and
  specifications, whether the property has already been sold or rented, and RERA authorisation
  status
  ([Dubai Land Department](https://dubailand.gov.ae/en/news-media/dubai-land-department-provides-madmoun-service-to-verify-validity-of-real-estate-ads-via-qr-codes/)).
- Property Finder has moved most of this to an API check: in Dubai, document upload and manual
  verification are no longer required for residential listings because verification runs
  automatically against the DLD API using the Trakheesi permit; a Quality Control team still
  reviews images, title and description
  (*snippet*, [Property Finder Help Center](https://support.propertyfinder.ae/hc/en-us/articles/24558432104722-How-to-verify-your-listing-on-PF-Expert-2-0)).
  Where documents *are* required (rentals, and non-Dubai emirates), the set is **title deed**
  — or **Oqood / initial contract of sale plus handover certificate** for off-plan — plus an
  **NOC or leasing form signed by the owner**
  (*snippet*, [Property Finder Help Center](https://support.propertyfinder.ae/hc/en-us/articles/13334023017746-Documents-Required-for-Listing-Verification)).
- dubizzle enforces the same at the account level: only RERA-certified agents/brokers may post
  sale listings, certifications are monitored continuously, and listings can be pulled when a
  certification expires or is revoked
  (*snippet*, [dubizzle Help Center](https://support.dubizzle.com/hc/en-us/articles/15421331256082-How-does-dubizzle-verify-agents-or-brokers-on-property-listings)).
  A private landlord is specifically blocked from posting
  (*snippet*, [dubizzle Help Center](https://support.dubizzle.com/hc/en-us/articles/14782323912722-I-m-a-Landlord-why-am-I-not-able-to-post-an-Ad-for-my-property)).

Permit validity is reported inconsistently across secondary sources (60 days in one, three
months in another) — **unverified**. What *is* well supported is that permits expire, that
Trakheesi sends expiry alerts and offers bulk renewal, and that an expired permit means the
listing must be renewed or removed
(*snippet*, [Oliva Trakheesi guide](https://joinoliva.com/en/learn/blog/trakheesi-permit-dubai-listings-guide)).
The design lesson survives the uncertainty: **an advert has an expiry date and a listing is not
permanently publishable.**

Aqarmap (Egypt/Jordan) — **unverified**. I found no first-party help documentation describing
its listing verification requirements and will not guess.

### 1.4 Self-serve: Zillow and Airbnb

Zillow FSBO is close to the "photos plus an account" end of the spectrum, with a soft ownership
claim rather than a hard document check. Zillow's own pages say a homeowner "verifies ownership"
by entering the address and confirming they are the legal owner, that FSBO listings "must be
submitted from a valid consumer account", and that Zillow **may** require "a certificate of
title or other documentation"
(*snippet*, [zillow.com/for-sale-by-owner](https://www.zillow.com/for-sale-by-owner/),
[Claim Your Home](https://www.zillow.com/z/c/claim-your-home/)).
Note "may" — it is a discretionary escalation, not a gate. Secondary sources describe the usual
mechanism as a **code mailed to the property address** or a utility-bill upload
([HomeLight](https://www.homelight.com/blog/how-to-list-my-home-on-zillow/)) — plausible and
consistent, but **unverified** against Zillow itself.

Zillow's rental policy is stricter and more explicitly written as data rules: **one listing per
address, duplicates not permitted**; listings may only be advertised by the contracted exclusive
listing agent or an authorised representative; to advertise For Rent By Owner "you must be the
owner of that property"; and Zillow reserves the right to remove content and terminate accounts
at its sole discretion
(*snippet*, [Zillow Rentals Listing Quality Policy](https://www.zillow.com/rentals-network/listings-quality-policy/)).

Airbnb verifies the *person*, not the property. Identity verification is required of primary
hosts, new co-hosts and booking guests: legal name, address, contact details checked against
trusted third-party sources, escalating to a government ID photo and a selfie match when that
fails. The ID and selfie are never shared with the counterparty
(*snippet*, [Airbnb Help Center — Verifying your identity](https://www.airbnb.com/help/article/1237)).
That is a genuinely different design goal: Airbnb is preventing *impersonation and fraud*, not
proving *title*.

### 1.5 What a title deed actually looks like as data

**Jordan.** The Department of Lands and Survey uses a hierarchical parcel key. Every level
carries both a name and a code number: district → sub-district → village → block → sector, plus
sheet number and parcel number. Combined these form the **"DLS key, which is a unique key or
identifier for any parcel"**. The land register itself holds: names of landowners **and their
shares**, the unique parcel identification number, area, initial value, and **easements and
mortgages**. Registration and surveying are a single integrated system rather than two
([Cadastral Template — Jordan](https://cadastraltemplate.org/jordan.php)).

The colloquial Arabic terms the brief mentioned map onto this: the *koushan* / *sanad tasjeel*
(سند تسجيل) is the registration deed; *hawd* (basin) and *qit'a* (plot/parcel) are levels of
that key. I could not fetch an official DLS page showing the deed's printed field list —
**unverified** at the field-by-field level, but the register contents above are solid.

**Lebanon** (which is where DESIGN.md actually puts Aqarat). The Directorate General of Land
Registry and Cadastre, under the Ministry of Finance, keeps the record of land and property —
ownership, plot and building specifications, and outstanding disputes — across 17 registry
offices in the governorates and districts. All purchases, sales, leases and inheritances must be
registered with one of those offices. E-services since April 2016 include viewing the title
register, transaction tracking, fee simulation and title-register changes
([DLRC e-services](https://www.lrc.gov.lb/en/content/e-services);
[DLRC registry offices](https://www.lrc.gov.lb/en/content/registry-offices);
[background on the directorate](http://www.lebweb.com/site/lebanon-dlrc-gov-lb-139013)).
Records are addressed by governorate → registry office → zone/section → lot/parcel number
([DLRC e-services](https://www.lrc.gov.lb/en/content/e-services)).

**Dubai** shows the same shape from the advertising side: land number + unit number +
municipality number + building/area
([Bayut agent portal](https://www.bayut.com/agentportal/the-real-estate-agents-guide-to-applying-for-a-trakheesi-permit/)).

**The generalisable data model.** Across all three jurisdictions a deed reduces to roughly:

```
deed_number            the registration document's own id
registry_office        which office holds the original
parcel_key             hierarchical: region / district / village / basin(block) / plot
                       — one composite natural key, several integer parts
area                   from the survey, not from the advert
owner_name(s)          plural
owner_national_id
ownership_share        fractions. Co-ownership is the normal case, not the exception.
encumbrances           mortgages, easements, disputes
```

Two things a student schema usually gets wrong and this list fixes: **ownership is a set with
shares, not a single `owner_id`**, and **area from the registry is a different fact from area
in the advert**.

### 1.6 What this means for a two-week student project

**MUST-HAVE**
- A `PENDING → PUBLISHED / REJECTED` moderation state on a property, with the reviewing agent
  and timestamp recorded. That is Aqarat's whole verification story and it is a real-world one.
- A deed/registry reference stored as *fields*, not one free-text string: deed number, registry
  office, and the parcel key parts. It costs nothing and it is what makes the project look like
  it read the domain.
- A rejection reason the owner can see.

**NICE-TO-HAVE**
- Deed document upload (a file path plus a "verified by / verified on" pair). One screen.
- Ownership shares as a separate `property_owner` table.
- Listing expiry: a `published_until` date and a scheduled/lazy transition to `EXPIRED`.

**OUT-OF-SCOPE, AND THAT IS CORRECT**
- Any real registry integration. There is no public land-registry API to call in Lebanon or
  Jordan, and inventing a mock one teaches nothing.
- Agent licence verification against a regulator.
- OCR of an uploaded deed. Say "an agent reads it" and move on.

---

## 2. Viewing and visit booking

### 2.1 The soft-request end

**Rightmove does not book anything.** It is explicit: it cannot arrange viewings, and the user
must contact the agent, who "will respond to your enquiry and help you with next steps, such as
providing more details or arranging a viewing"
([Rightmove Help Centre](https://faq.rightmove.co.uk/support/solutions/articles/7000049007-i-m-interested-in-a-property-what-do-i-do-next-)).
On the agent side, Rightmove's Enquiry Manager lets the agent **confirm** a viewing *after* they
have separately agreed it with the applicant, sending confirmation to the contact details the
applicant supplied
(*snippet*, [Rightmove Hub — Enquiry Manager: how to book a viewing](https://hub.rightmove.co.uk/enquiry-manager-how-to-book-a-viewing/)).
So even the "booking" feature is a record of an agreement reached elsewhere.

**Zillow's "Request a Tour" is a lead-routing product, not a calendar.** The buyer picks a date
and preferred time and submits contact details; Zillow then rings Premier Agents one at a time
until one accepts; the buyer and agent are given each other's details by text and email, and
the *agent* then has to "organize the tour and confirm that the property is available"
(*snippet*, [Zillow Premier Agent — Real-Time Touring](https://www.zillow.com/premier-agent/real-time-touring/) and
[Zillow — Tour Connections](https://www.zillow.com/pro/home-tour-requests-what-they-are-and-how-to-win/)).
The agent who answers is often not the listing agent. Nothing is blocked, nothing is reserved.

### 2.2 The hard-booking end

**Redfin** sits in the middle and is the most interesting case. The buyer picks a slot; a
lightning bolt means a Redfin agent is free at that time — but "the tour still needs to be
confirmed with the listing agent once the home tour is submitted". Returning customers can be
auto-booked with only an email confirmation; first-timers get a call from a tour coordinator
(*snippet*, [Redfin — Scheduling a Tour](https://support.redfin.com/hc/en-us/articles/360001432232-Scheduling-a-Tour)).
Redfin's own launch material for **Book It Now** describes "a schedule of showing times, letting
you set up a tour with a couple of clicks", with a phone call retained "especially if you're new
to our service"
([Redfin News](https://www.redfin.com/news/redfin-book-it-now/)).

So: **agent availability is hard-booked; property access is still soft-confirmed.** Two
different calendars, and the second one is the one that can fail.

**ShowingTime** (the MLS showing-scheduling system, now ShowingTime+/Zillow) is where the real
state machine lives, and it is the best model available for a project like this. Appointment
types are a *per-listing setting*:

| Type | Behaviour |
|---|---|
| **Appointment Required** | "Permission must be obtained from ANY of the designated listing contacts before the appointment request can be confirmed." Recommended for occupied homes. |
| **Auto Confirm (Courtesy Call or Go and Show)** | "Appointment requests are documented and immediately confirmed." Recommended for vacant homes on lockbox. |
| **View Instructions Only** | No date/time selection at all; the buyer's agent just sees access notes. |

([ShowingTime for the MLS — appointment types](https://showingtimemls.uservoice.com/knowledgebase/articles/1903963-what-do-the-different-appointment-types-mean)).

Double-booking is a **deliberate configurable policy**, not a bug: "An exclusive showing will
only allow one buyer's agent and buyer in your home at a specified time, while overlapping them
can let more than one agent in at a time", and when overlapping is disallowed the system enforces
**buffer time** so appointments cannot be scheduled back-to-back
(*snippet*, [ShowingTime — Can agents schedule appointments at the same time](https://help.home.showingtime.com/knowledgebase/articles/1906834-can-agents-schedule-appointments-at-the-same-time),
[Appointment Restrictions](https://showingtimemls.uservoice.com/knowledgebase/articles/499239-appointment-restrictions)).
There is also a "listing agent accompanied showing" switch controlling whether the agent gets to
confirm or decline *before* the occupant is asked
(*snippet*, [ShowingTime — Listing Configuration & Settings](https://showingtimemls.uservoice.com/knowledgebase/articles/499193-listing-configuration-settings)).

**Zillow Rental Manager instant tour scheduling** is the one true hard-booking flow in the set
— the landlord publishes availability and the renter books into it
(*snippet*, [Zillow Rental Manager Help Center — Instant tour scheduling FAQ for landlords](https://help.zillowrentalmanager.com/hc/en-us/articles/47479822560275-Instant-tour-scheduling-FAQ-for-landlords)).
I could not fetch the article body, so the specifics of conflict handling and cancellation there
are **unverified**.

### 2.3 The state machine, reconstructed

Nothing found publishes a state diagram, but the states are implied consistently across
ShowingTime and Redfin:

```
REQUESTED ──confirm──► CONFIRMED ──happened──► COMPLETED ──► (feedback requested)
    │                      │
    ├─decline──► DECLINED  ├─either party──► CANCELLED
    └─timeout──► EXPIRED   └─nobody came──► NO_SHOW
```

Auto-confirm listings skip straight from `REQUESTED` to `CONFIRMED`. The confirmer is the
listing side (listing agent, or the occupant, or both). No-shows are handled socially, not
systematically — no platform found penalises them automatically; **unverified** whether any do.

Viewings on an already-sold property are handled by the *status*, not by the booking system: MLS
`Pending` means "under contract and **not** available for showings and additional offers",
whereas `Contingent`/`Active Under Contract` means under contract but **still** available for
showings and further offers
(*snippet*, [connectMLS listing status definitions](https://connectmls.smartmls.com/hc/en-us/articles/19267451493787-Listing-Status-Definitions)).
That is a genuinely elegant answer: the question "can this be viewed?" is a function of the
property's status, not a special case in the booking code.

### 2.4 What this means for a two-week student project

**MUST-HAVE**
- A viewing request with an explicit status enum: `REQUESTED, CONFIRMED, DECLINED, CANCELLED,
  COMPLETED, NO_SHOW`. Six constants, one column, and it is the single highest-value modelling
  decision in this section.
- The confirming agent and the confirmation timestamp.
- A guard that refuses new requests when the property is not in a viewable status — derived
  from the property status, not duplicated as a boolean.

**NICE-TO-HAVE**
- Agent availability slots and a real double-booking check. Note that "one viewing per slot" is
  a *policy choice* (ShowingTime makes it configurable), so if you implement it, say so in the
  report rather than presenting it as the only correct behaviour.
- Post-viewing feedback (see 5.5).

**OUT-OF-SCOPE, AND THAT IS CORRECT**
- Calendar sync (iCal/Google), SMS/email reminders, lockbox or self-tour access codes.
- Automatic no-show penalties. Nobody real does this.

---

## 3. Buyer–seller contact and messaging

### 3.1 Three postures, and they correlate with the business model

| Posture | Example | What the buyer sees |
|---|---|---|
| **Direct reveal** | Bayut, dubizzle, Property Finder, Rightmove | The agent's actual phone number, email, WhatsApp |
| **Lead routing** | Zillow Premier Agent | Contact details are *exchanged* — but with an agent Zillow chose, not necessarily the listing agent |
| **Closed channel** | Airbnb | In-app messaging only, with contact details actively filtered |

**Direct reveal.** Bayut offers Call, Email and WhatsApp buttons on the listing, plus Bayut Chat
(*snippet*, [Bayut KSA Help Center](https://help.bayut.sa/hc/en-us/articles/16336945574162-How-do-I-contact-a-property-agent-through-Bayut-sa);
[MyBayut — Bayut Chat](https://www.bayut.com/mybayut/bayut-chat-feature/)).
Rightmove reveals the agent's phone number behind a 'Call Agent' button and offers a
'Request Details' email through the portal
(*snippet*, [Rightmove Help Centre](https://faq.rightmove.co.uk/support/solutions/articles/7000049007-i-m-interested-in-a-property-what-do-i-do-next-)).

Crucially, in **every** direct-reveal case the number revealed belongs to an **agent**, never to
the owner. The agent-gated model means there is no owner contact detail on the platform to leak.
This is the sharpest finding in the section: the industry's answer to "do you expose the owner's
phone number?" is mostly "there is no owner phone number in the system".

**Closed channel.** Airbnb's off-platform policy prohibits communicating, sharing personal
contact information, paying or requesting payment outside Airbnb, and the platform actively
blocks messages containing "words or numbers that might include contact information or references
to other sites, including external links"
([Airbnb — paying and communicating through Airbnb](https://www.airbnb.com/help/article/209/paying-and-communicating-through-airbnb)).
Airbnb frames the reason as protection: booking off-platform forfeits AirCover and "makes it
harder for us to protect your information and puts you at greater risk of fraud and other
security issues, such as phishing"
([same source](https://www.airbnb.com/help/article/209/paying-and-communicating-through-airbnb)).
Enforcement escalates from warning to listing suspension to permanent removal
(*snippet*, secondary coverage of the May 2025 policy —
[Rental Scale-Up](https://www.rentalscaleup.com/airbnb-new-off-platform-policy-may-2025/)).

### 3.2 Number masking as infrastructure

Masked calling is a general marketplace pattern with off-the-shelf implementations. Twilio Proxy
"simplifies the task of masking the communications between two parties", automatically allocating
a number and associating two real numbers with it so calls and messages forward both ways; Twilio
names Uber, Lyft, Airbnb, Postmates and Instacart as voice-proxy marketplaces
([Twilio Proxy docs](https://www.twilio.com/docs/proxy);
[Twilio — what is voice proxy](https://www.twilio.com/docs/glossary/what-is-voice-proxy)).
The relevant property for a data model is that a **proxy session is an entity with a lifetime**:
it exists for the duration of a transaction, has participants, and can be closed.

### 3.3 Why platforms mask or broker contact — including disintermediation

Four distinct motives, and it is worth keeping them separate because they pull in different
design directions:

1. **Disintermediation risk.** The platform's revenue depends on the transaction happening
   *through* it. If the buyer and seller exchange numbers on the first message and complete
   privately, the platform did the work and captured none of the value. This is why Airbnb bans
   off-platform payment *and* off-platform contact in the same policy
   ([Airbnb](https://www.airbnb.com/help/article/209/paying-and-communicating-through-airbnb)) —
   and why a portal that charges agents for *leads* (Zillow) routes and measures every contact
   rather than publishing a phone number.
2. **Fraud and scams.** Contact details are the attack surface. The FTC documents scammers
   hijacking real listings by copying photos and descriptions and "replacing the agent's contact
   information with their own"
   ([FTC — Rental Listing Scams](https://consumer.ftc.gov/articles/rental-listing-scams)).
   Nearly 65,000 rental scams and about $65 million in reported losses since 2020
   ([FTC data spotlight, Dec 2025](https://www.ftc.gov/news-events/data-visualizations/data-spotlight/2025/12/rental-scams-hit-home-65-million-reported-losses)).
3. **Spam and lead quality.** A visible phone number is scraped. A gated one is a qualified lead
   — Rightmove's Enquiry Manager only surfaces applicants who "complete the enquiry process
   through Rightmove and submit the required qualification information"
   (*snippet*, [Rightmove Hub](https://hub.rightmove.co.uk/enquiry-manager-how-to-book-a-viewing/)).
4. **Data protection.** Once you hold personal contact data you own a compliance obligation.
   Not brokering it means not storing it.

Note how motive 1 conflicts with the others: disintermediation is a *commercial* reason dressed
in a *safety* argument. A student report that names that tension will read as informed.

### 3.4 What this means for a two-week student project

**MUST-HAVE**
- An **inquiry** record — client, property, message, created-at, and a status — rather than a
  `mailto:` link. The message is the audit trail; the link is not.
- The rule that the client never sees the owner's contact details. In an agency system, the
  agent *is* the channel. This matches every agent-gated platform researched, so it is not a
  simplification — it is the industry norm.

**NICE-TO-HAVE**
- Threaded replies inside the app (inquiry → agent response), which is Rightmove's model with
  extra steps.
- A "contact revealed" audit event, which is what a lead-gen portal actually monetises.

**OUT-OF-SCOPE, AND THAT IS CORRECT**
- Real number masking. It needs a telephony provider, a number pool and a webhook endpoint.
  Cite Twilio Proxy in the report as the real-world answer and do not build it.
- Live chat, WhatsApp integration, push notifications, email delivery.

---

## 4. Payments and contracts — where the platform is *not*

### 4.1 The boundary, stated first because it is the most important finding

Three product categories, routinely confused, doing three different jobs:

| | **Marketplace / portal** | **Property management system** | **Brokerage CRM** |
|---|---|---|---|
| Examples | Zillow, Rightmove, Bayut, Property Finder | Buildium, AppFolio, Yardi, RentRedi | Bayut's BayutPro, Rightmove's Enquiry Manager, agency CRMs |
| Primary user | The public | The landlord / property manager | The agent |
| Core object | The **listing** | The **lease** and the **unit ledger** | The **lead** |
| Touches money? | Essentially never for sales; only rent rails for rentals | Yes — trust accounts, rent, deposits, owner distributions | No |
| Ends when | The enquiry is passed on | The tenancy ends | The deal is won or lost |

Zillow says its own role out loud. In its Rentals User Terms: *in providing the Rentals Platform,
Zillow does not act as a broker, property manager, payment processor, legal advisor, money
transmitter, payment manager, or credit reporting agency*
(*snippet*, [Zillow Rentals User Terms](https://www.zillow.com/renter-hub/terms/Rental-User-Terms)).
That single sentence is the boundary in one line, from the largest player in the industry.

Buildium sits on the other side of it: "The property manager holds these funds for the rightful
owners — either the property owner or the tenant… these funds don't belong to the property
management company"
(*snippet*, [Buildium — property management trust account](https://www.buildium.com/dictionary/property-management-trust-account/)).
Trust accounting, owner distributions, EFT settlement dates, state audit reports
(*snippet*, [Buildium accounting best practices](https://www.buildium.com/blog/property-management-accounting-best-practices/),
[Buildium ePay / EFT help](https://help.buildium.com/hc/s/article/ePay-BasicS)) —
none of which appears anywhere in a portal.

**Aqarat is a fourth thing again**: a single agency's internal system that spans listing +
contract + payment tracking. That is closest to a brokerage CRM with a PMS's payment ledger
bolted on, and it is a perfectly reasonable teaching scope — but the team should say in the
report that it is *not* a marketplace, because "we didn't build escrow" then reads as correct
scoping rather than an omission.

### 4.2 Sale: the platform never touches the money

**Earnest money** is a good-faith deposit "paid by a homebuyer to show their interest is
legitimate and they intend to close on a home" — distinct from the down payment, which goes
toward the purchase price. Typically 1–10% of price, with no law requiring it
([NAR consumer guide — escrow and earnest money](https://www.nar.realtor/the-facts/consumer-guide-escrow-and-earnest-money)).

**Escrow** is the mechanism: a neutral third party — an attorney, title/settlement agent, escrow
company, or in many US markets the listing brokerage's own trust account — controls the funds so
that neither buyer nor seller can reach them until the contract's conditions are met
([NAR](https://www.nar.realtor/the-facts/consumer-guide-escrow-and-earnest-money);
*snippet*, [ATG Title](https://atgtitle.com/who-holds-earnest-money-title-companies-or-agents-why/)).
The money returns to the buyer if a contingency (inspection, appraisal, financing) fails or the
seller cancels, and is forfeited if the buyer walks without a valid reason
([NAR](https://www.nar.realtor/the-facts/consumer-guide-escrow-and-earnest-money)).

**Purchase agreement vs deed transfer.** These are two documents at two moments. The purchase
agreement is a contract creating obligations; the deed transfer is the act that moves title, and
it is completed at the **land registry**, not on any platform. In Lebanon, "all purchases, sales,
leases and inheritance of land or real estate… must be registered with one of the directorate's
offices"
([DLRC background](http://www.lebweb.com/site/lebanon-dlrc-gov-lb-139013)); in Jordan the DLS
register records owners and their shares against the parcel key
([Cadastral Template](https://cadastraltemplate.org/jordan.php)).

**Does a listing portal ever hold sale money? No — and now we can say why**, not just assert it:
being a neutral fund-holder means being an escrow agent or a money transmitter, both licensed
activities, which is precisely what Zillow's terms disclaim
(*snippet*, [Zillow Rentals User Terms](https://www.zillow.com/renter-hub/terms/Rental-User-Terms)).
The regulatory perimeter is the reason, not squeamishness.

There is also a compliance layer around the closing that students never see. FinCEN's 2024
Residential Real Estate Rule requires reporting on non-financed ("all-cash") residential transfers
to legal entities or trusts, including the beneficial owners behind the purchase, filed by
professionals involved in the closing
([Federal Register, 29 Aug 2024](https://www.federalregister.gov/documents/2024/08/29/2024-19198/anti-money-laundering-regulations-for-residential-real-estate-transfers);
*snippet*, [Mayer Brown summary](https://www.mayerbrown.com/en/insights/publications/2024/09/fincen-finalizes-residential-real-estate-reporting-requirements)).
Its effective date has moved — an exemptive relief order pushed reporting persons' obligations to
1 March 2026
(*snippet*, [Holland & Knight](https://www.hklaw.com/en/insights/publications/2025/10/fincen-delays-residential-real-estate-transfer-reporting-rule)) —
so treat the date as volatile, but the *existence* of KYC at closing as settled.

### 4.3 Rental: platforms move money, they do not hold it

Zillow Rental Manager collects rent free for landlords; the tenant pays nothing by ACH but 2.95%
by credit card and $9.95 by debit. ACH takes about 5 business days to deposit, and 7–10 business
days for a tenant's first payment for security reasons
(*snippet*, [Zillow Rental Manager — online payments FAQ](https://www.zillow.com/rental-manager/online-payments-faq/) and
[Zillow Rental Manager Help Center](https://help.zillowrentalmanager.com/hc/en-us/articles/360000603188-How-much-does-it-cost-to-use-online-rent-payments)).
The multi-day lag is the tell: this is **ACH rails, not a wallet**. Zillow explicitly disclaims
being a payment processor or money transmitter
(*snippet*, [Rentals User Terms](https://www.zillow.com/renter-hub/terms/Rental-User-Terms)).

The pattern generalises. Dwolla markets bank-to-bank rails for property management on exactly
this reasoning: payments "can go directly from tenants to landlords without involving the
property management platform in the payment flow, helping to reduce regulatory and compliance
risk"
(*snippet*, [Dwolla — property management](https://www.dwolla.com/use-case/property-management)).
Rent platforms integrate a processor (Stripe, Dwolla, Plaid) rather than becoming one
(*snippet*, [Stripe — how to accept rent payments online](https://stripe.com/resources/more/how-to-accept-rent-payments-online);
[Baselane](https://www.baselane.com/resources/rent-payment-system-for-landlords)).

The exception is the property manager themselves, who genuinely *does* hold client money — in
segregated **trust accounts**, with rent held before distribution to owners and deposits held
separately until move-out, reconciled and documented for state audits
(*snippet*, [Buildium](https://www.buildium.com/dictionary/property-management-trust-account/),
[Buildium accounting best practices](https://www.buildium.com/blog/property-management-accounting-best-practices/)).

**Lease lifecycle.** Consistently described as draft → sign (e-signature) → active (with dates
tracked) → renewal or termination → close-out (deposit settlement, final statements)
(*snippet*, [DoorLoop — lease management](https://www.doorloop.com/blog/lease-management)).
The renewal stage is a decision point — tenant history, rent review, renewal notice — not an
automatic rollover
(*snippet*, [Second Nature — lease renewal checklist](https://www.secondnature.com/blog/lease-renewal-checklist/)).

**Security deposits are statutory, not contractual.** Caps are commonly one to two months' rent
(California: two months unfurnished, three furnished); return deadlines run 14–60 days with 30
days the most common; itemised deduction statements are widely required; normal wear and tear
cannot be deducted; and penalties for wrongful withholding are typically 2× or 3× damages
(*snippet*, [iPropertyManagement — security deposit laws by state](https://ipropertymanagement.com/laws/security-deposits),
[Rentec Direct state guide](https://www.rentecdirect.com/blog/security-deposit-state-guide/)).
Lebanon-specific deposit rules — **unverified**, not researched.

Late fees, partial payments and arrears are jurisdiction-specific and I found no authoritative
cross-jurisdiction source. **unverified.** What the software side needs is uncontroversial
though: a payment is `DUE / PAID / PARTIAL / LATE`, arrears is a derived balance, and a late fee
is another line item on the ledger rather than a mutation of the rent amount.

### 4.4 Off-plan and installment sales (Middle East)

This is the one place where a **payment schedule** is the central object, which makes it the most
relevant external model for Aqarat's `PaymentService`.

Structure: a booking/down payment of roughly 5–20%, the balance split across construction
milestones and handover — quoted as 80/20, 60/40, 50/50, or e.g. 20/60/20 (20% at booking, 60%
across construction stages, 20% on handover). Milestones are physical: foundation complete,
structure complete, interior finishing started
(*snippet*, [Binghatti — Dubai off-plan payment plans](https://www.binghatti.com/en/blog/dubai-off-plan-payment-plans),
[SBA Properties](https://sbaproperties.ae/off-plan/off-plan-payment-plans-dubai/)).
Post-handover plans extend installments past delivery.

**Who guarantees them: the escrow regime, not the developer's promise.** Dubai Law No. 8 of 2007
requires a dedicated escrow account per off-plan project, held at a RERA-approved trustee bank,
monitored by DLD, with a separate account per project so funds cannot be moved between schemes;
developers may draw only in stages tied to construction progress; and no developer may operate
unless entered in the Register of Real Estate Developers
([Law No. 8 of 2007, Dubai legislation portal](https://dlp.dubai.gov.ae/Legislation%20Reference/2007/Law%20No.%20(8)%20of%202007.html);
*snippet*, [BSA Law](https://bsalaw.com/insight/navigating-dubais-off-plan-real-estate-laws-compliance-essentials-for-developers/)).
The buyer's interim interest is registered separately via **Oqood** before the title deed exists
(*snippet*, [Property Finder documents-required article](https://support.propertyfinder.ae/hc/en-us/articles/13334023017746-Documents-Required-for-Listing-Verification)).

Two modelling consequences worth stealing: an installment can be **milestone-triggered** rather
than date-triggered, and the schedule is agreed **once, up front**, as a set of rows — which is
exactly the shape `PaymentService` already generates.

### 4.5 E-signature: what makes a signature binding rather than a click

**United States.** ESIGN (federal) and UETA (adopted by 49 states, DC and territories) establish
that "an electronic signature cannot be denied legal effect, validity, or enforceability solely
because it is in electronic form". When challenged, the party relying on it must show four
things: **intent** to sign, **attribution** to the signer by any reasonable means, **association**
of the signature with the specific record, and **retention** — the signer keeps a copy and the
record is stored so its integrity is preserved
([DocuSign — legality](https://www.docusign.com/how-it-works/legality)).
The statutory definition is deliberately broad: "any electronic sound, symbol, or process
attached to or logically associated with a record and executed or adopted by a person with the
intent to sign the record"
([same](https://www.docusign.com/how-it-works/legality)).

**European Union.** eIDAS tiers it. Article 25(1): a signature "shall not be denied legal effect
and admissibility as evidence… solely on the grounds that it is in an electronic form or that it
does not meet the requirements for qualified electronic signatures"; Article 25(2): a **qualified**
electronic signature "shall have the equivalent legal effect of a handwritten signature"
([eIDAS Article 25, legislation.gov.uk](https://www.legislation.gov.uk/eur/2014/910/article/25/data.html)).
Three levels: **SES** (a click, an email reply, a drawn mark), **AES** (uniquely identifies the
signer and links identity to the document cryptographically), **QES** (an AES made with a
qualified device and a qualified certificate from a qualified trust service provider). Only QES
gets automatic handwritten-equivalence; AES's evidential weight must be argued if disputed
(*snippet*, [e-signature.eu](https://www.e-signature.eu/en/3-types-of-eidas-signature-simple-advanced-and-qualified/),
[eEvidence](https://blog.eevidence.com/en/simple-vs-advanced-vs-qualified-electronic-signature-real-differences/)).

**The practical answer to "click vs binding":** a click *is* a signature in both regimes. What
separates a defensible signature from an indefensible one is the **evidence around it** —
identity attribution, an immutable copy of exactly what was signed, and a timestamped record. In
other words, the difference is an **audit trail**, which is a thing a student project can
actually build. Lebanese and Jordanian e-signature law — **unverified**, not researched.

### 4.6 What this means for a two-week student project

**MUST-HAVE**
- The contract as a state machine: `DRAFT → SIGNED → ACTIVE → COMPLETED / TERMINATED`, with
  sale and lease as **genuinely different lifecycles** (see 5.6), not one table with nullable
  columns for both.
- An installment schedule generated once, stored as rows, each with due date, amount, and a
  status. The rounding-drift test in `docs/CONVENTIONS.md` is the right test — it is the classic money bug
  in exactly this table.
- Payments recorded against schedule rows. Never mutate the schedule when a payment arrives;
  append.
- A one-paragraph statement in the report that Aqarat **records** money and does not **move** it,
  citing Zillow's own disclaimer as precedent. This turns the biggest omission into the most
  defensible design decision in the project.

**NICE-TO-HAVE**
- Partial payments and a derived arrears balance per contract.
- A late-fee line item (separate row, not an edit to the rent).
- Contract PDF generation, and a `signed_at` + `signed_by` pair as the "e-signature" — with the
  four ESIGN elements named in the report as what a real one would need.

**OUT-OF-SCOPE, AND THAT IS CORRECT**
- Escrow, trust accounts, card processing, ACH, refunds, chargebacks, money transmission.
- Real DocuSign integration.
- Any land-registry transfer step. The deed transfer happens at a government office. The right
  model is a `deed_transferred_on` date the agent enters after the fact.
- Commission accounting and owner distributions — that is the PMS column of the table in 4.1.

---

## 5. What student projects forget

### 5.1 Listing expiry

Every serious system has it. MLS: an **Expired** listing means the contract between seller and
brokerage has ended and the property has fallen out of the MLS; **Withdrawn** means off-market
but the agency agreement still stands — two different states, deliberately
(*snippet*, [connectMLS](https://connectmls.smartmls.com/hc/en-us/articles/19267451493787-Listing-Status-Definitions),
[MLS Technology](https://gtar.zendesk.com/hc/en-us/articles/17659502372379-MLS-Listing-Statuses-Defined)).
Dubai: the advertising permit itself expires, with automated alerts and bulk renewal
(*snippet*, [Oliva](https://joinoliva.com/en/learn/blog/trakheesi-permit-dubai-listings-guide)).
Cheapest possible implementation: a `published_until` column and a query filter. One column.

### 5.2 Duplicate and fraudulent listings

Zillow Rentals permits **one listing per address** and resolves multi-source syndication by
priority rules
(*snippet*, [Zillow Rentals Listing Quality Policy](https://www.zillow.com/rentals-network/listings-quality-policy/)).
The FTC's description of the dominant fraud is directly actionable as a detection rule: scammers
copy a genuine listing's photos, description and virtual tour, swap the contact details, and
repost. The FTC's own advice to consumers is effectively a duplicate query — search the address
plus the owner/company name, and treat two ads for one address under different names as a scam
signal
([FTC — Rental Listing Scams](https://consumer.ftc.gov/articles/rental-listing-scams)).
A unique constraint on (address/parcel, active status) is a two-line version of a real control.

### 5.3 Moderation queues and audit trails

Property Finder runs an automatic DLD API check *plus* a human Quality Control review of images,
title and description
(*snippet*, [Property Finder Help Center](https://support.propertyfinder.ae/hc/en-us/articles/24558432104722-How-to-verify-your-listing-on-PF-Expert-2-0));
dubizzle moderates every ad and refuses ones that fail guidelines
(*snippet*, [dubizzle Ad Posting Rules](https://support.dubizzle.com/hc/en-us/articles/4408114950673-dubizzle-Ad-Posting-Rules));
Zillow reserves removal and account termination rights
(*snippet*, [Zillow Rentals Listing Quality Policy](https://www.zillow.com/rentals-network/listings-quality-policy/)).
Aqarat's review queue is already this, and DESIGN.md's audit-entry requirement is already the
matching control. Worth noting in the report that the pairing — a moderation gate plus an
immutable record of who passed it — is what real platforms do.

### 5.4 KYC

Identity verification is mandatory for Airbnb hosts and guests
(*snippet*, [Airbnb Help Center](https://www.airbnb.com/help/article/1237));
beneficial-ownership reporting is mandatory at US residential closings under the FinCEN rule
([Federal Register](https://www.federalregister.gov/documents/2024/08/29/2024-19198/anti-money-laundering-regulations-for-residential-real-estate-transfers));
broker registration is mandatory to list in Dubai
(*snippet*, [dubizzle Help Center](https://support.dubizzle.com/hc/en-us/articles/15421331256082-How-does-dubizzle-verify-agents-or-brokers-on-property-listings)).
For a student project, a national ID field on the client record plus a "verified by agent" flag
is the honest 5% of this that costs nothing.

### 5.5 Viewing feedback

ShowingTime auto-sends a feedback form to the showing agent after each appointment, tracks who
declined to respond, lets the listing agent approve feedback before the seller sees it, and
aggregates it into seller reports with charts — by default *without* the buyer agent's identity
(*snippet*, [ShowingTime Feedback FAQ](https://showingtimemls.uservoice.com/knowledgebase/articles/1162072-feedback-faq),
[ShowingTime blog](https://showingtime.com/resources/blog/3-ways-to-easily-share-feedback-with-sellers)).
Two design points worth stealing even if the feature is skipped: feedback is **moderated before
the owner sees it**, and it is **anonymised**.

### 5.6 Offer / counter-offer, and why status matters

A counteroffer **voids the original offer** — the seller cannot go back and accept it — and
counters typically carry a 24–72 hour expiry after which they are treated as rejected
(*snippet*, [Amerisave](https://www.amerisave.com/learn/real-estate-counteroffers-in-2026-a-complete-buyer-and-seller-negotiation-guide),
[iBuyer](https://ibuyer.com/blog/how-counter-offers-work-in-real-estate/)).
NAR notes that the strongest offer is often not the highest price, because contingencies, timing
and earnest money size all count
([NAR — navigating multiple offers](https://www.nar.realtor/the-facts/consumer-guide-navigating-multiple-offers)).
If Aqarat models offers at all, the expiry and the void-on-counter rule are the two rules that
make it look real.

### 5.7 Property status lifecycle

The MLS vocabulary, which is worth matching because it is already a solved ontology:

| Status | Meaning |
|---|---|
| **Active** | For sale, showable, accruing days on market |
| **Contingent / Active Under Contract** | Offer accepted, conditions outstanding, **still showable and still taking offers** |
| **Pending** | Under contract, **not** showable, no further offers, days on market frozen |
| **Withdrawn** | Off market, agency agreement still in force |
| **Expired** | Agency agreement ended, listing out of the MLS |
| **Sold** | Closed |

(*snippet*, [connectMLS](https://connectmls.smartmls.com/hc/en-us/articles/19267451493787-Listing-Status-Definitions),
[MLS Technology](https://gtar.zendesk.com/hc/en-us/articles/17659502372379-MLS-Listing-Statuses-Defined)).

The important structural insight: **Contingent and Pending both mean "under contract" but differ
only in whether viewings are allowed**. That is the whole reason viewability should be derived
from status rather than stored as its own flag.

### 5.8 Sale and rental are different lifecycles

They share a property and nothing else:

| | **Sale** | **Rental / lease** |
|---|---|---|
| Terminates in | Deed transfer at the land registry | Move-out and deposit settlement |
| Money shape | One price, deposit + balance, one closing | Recurring rent + a held deposit, indefinitely |
| Third parties | Escrow agent, notary, registry | Nobody, or a property manager |
| Fails by | Contingency not met, deposit forfeited | Arrears, eviction, early termination |
| Ends the property's life on the platform | Yes | No — it renews |

Modelling them as one table with a `type` column and a lot of nullable fields is the mistake this
table exists to prevent.

### 5.9 Agent commission

Since 17 August 2024 US MLSs may no longer publish offers of compensation, and agents must have
a written agreement with a buyer before touring a home; compensation is negotiated off-MLS
([NAR settlement FAQs](https://www.nar.realtor/the-facts/nar-settlement-faqs);
*snippet*, [NAR — summary of 2024 MLS changes](https://www.nar.realtor/about-nar/policies/summary-of-2024-mls-changes)).
Relevant here only as a caution: **do not put a commission field on the listing.** The largest
market in the world just spent a settlement removing exactly that field.

### 5.10 What this means for a two-week student project

**MUST-HAVE**
- The status lifecycle as an enum matching the MLS vocabulary, with viewability derived from it.
- Separate sale and lease contract handling.
- Audit entries on state transitions (already required by DESIGN.md).

**NICE-TO-HAVE**
- Listing expiry (`published_until`).
- Duplicate prevention: a unique constraint on active listings per parcel/address.
- A national ID field on the client.

**OUT-OF-SCOPE, AND THAT IS CORRECT**
- Offer/counter-offer negotiation threads, viewing feedback surveys, commission accounting,
  fraud scoring, document OCR, credit/tenant screening.

---

## Gaps and honest limits

- **Zendesk-hosted help centres and zillow.com return HTTP 403 to automated fetching.** Every
  claim sourced from Zillow, Redfin support, Property Finder support, dubizzle support and
  Zillow Rental Manager help is marked *snippet* and comes from a search index's rendering of
  those pages, not from the pages themselves. The URLs are correct and human-readable; verify
  in a browser before quoting any of them in an academic submission.
- **Aqarmap** — no first-party documentation found on listing verification. Not covered.
- **Zoopla** — assumed to follow Rightmove's agent-only model; not verified.
- **Jordan DLS deed field list** — the register *contents* are verified via the Cadastral
  Template; the printed deed layout is not.
- **Lebanon** — registry structure and e-services verified; deposit law, late-fee law and
  e-signature law were not researched.
- **Trakheesi permit validity period** — secondary sources disagree (60 days vs 3 months).
- **No-show handling** — no platform found that automates a penalty. Absence of evidence.
