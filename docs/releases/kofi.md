# Ko-fi

How supporting Folio works, and what it does and doesn't buy. Local notes; nothing here is published until McCal says
so, and the page copy is his to write.

Page: <https://ko-fi.com/mccal> (already linked from Settings › Support Folio, and from Settings › Market).

## The rule that comes first

Folio's free core has to be good on its own. A supporter feature is an extra on top, and **nothing already shipped
ever becomes paid** — decided 2026-09-17. Two things follow from it:

- MIT code that's already published can't be locked. A genuinely closed feature has to be new code, in its own module,
  under its own licence, decided up front.
- Google Play requires Play Billing for paid unlocks in Play builds, so Ko-fi codes are for the GitHub and sideloaded
  builds only.

Right now the only thing behind a code is **early access to the Market**, and the Market hands out themes and tweaks
that are all in Settings anyway. So a code buys time, not features: you see it first.

**And the other side of that rule: not supporting must never cost you anything but time.** Closing the Beta Updates
door (2026-09-19) took away the one in-app route a non-paying tester had, so two things have to stay true and are
written into CONTRIBUTING.md:

- A build anyone makes themselves always has the Market, with no code - `assembleFast` installs as Folio Dev. Folio
  is MIT, so this is the point rather than a hole, and there is a test keeping that door open.
- **Codes are free for anyone testing or contributing**, with nothing to prove first. Someone filing fold bugs on a
  Pixel Fold is worth more than $3, and a store they can't open is a poor way to say thank you.

## How a code works

One short signed ticket, checked on the phone against a public key built into Folio. No account, no server call,
nothing stored about who paid: Folio keeps the code and reads what it says.

```
FOLIO-style groups of five, Crockford base32 (no I, L, O or U, so a typed code can't be misread)
 └─ 9 bytes: version · scopes · tier · expiry day · serial      + a 64-byte ECDSA P-256 signature
```

**Scopes** are what the code opens: `beta` (features a release or two early, which is what the Market is today),
`look`, `power` and `keys` (Keyd). **Expiry** is a day, or none. **Serial** is what a withdrawal names.
Editing any of it breaks the signature, and there are tests for that.

Codes are shareable on purpose. A supporter passing one to a friend is fine — it's a thank-you, not a licence — so
they're minted per scope, not per person.

```bash
./scripts/beta-code.py newkey --passphrase                    # once; prints what to paste into BetaKeys.SUPPORTER
./scripts/beta-code.py mint --scopes beta                     # a code that never runs out
./scripts/beta-code.py mint --scopes beta,keys --expires 2027-03-01 --count 25
./scripts/beta-code.py pool --scopes beta --count 200 > pool.sql   # a batch for the Ko-fi worker
```

`supporter-key.pem` never leaves the machine it's made on and is never committed. A new key stops every code already
handed out from working, so keep an offline copy.

`BetaCodeToolTest` mints a code with that script and reads it with the app's own `BetaCodes`, so the tool and the
phone can't drift apart without a test saying so.

### Where things stand

**One system, since 2026-09-19.** Two grew in parallel — `BetaCodes`/`Supporter` on `main` and an `EarlyAccess` in
the 0.7.0 branch — and they merged in together. `BetaCodes` won on every axis (short typeable codes, scopes,
withdrawal, a Settings page, a redeem link, a worker with tests), so `EarlyAccess`, `tools/folio-code.py` and
`tools/kofi-webhook/` are gone. What the retired side was better at came across: the signing key can be encrypted
at rest, and the verification now runs through the Market's `SourceKey`, so Folio has one ECDSA implementation
rather than two.

**What that costs McCal, concretely.** The key made on 2026-09-18 with the retired tool
(`~/.folio/folio-supporter.pem`, fingerprint `6423 2915 002A CAD7 FD07 CC99 AC27 B99F`) is **not** the key Folio
carries. The live one is `BetaKeys.SUPPORTER`, and its private half is `supporter-key.pem` in the main checkout
(gitignored, mode 600). Checked on 2026-09-19: the public half on disk is byte for byte the one in the app.

Done on 2026-09-19:

- **Re-minted.** `~/.folio/market-code.txt` holds a new code, scope `beta`, no expiry, serial `4195613006` — that
  serial is what `BetaKeys.WITHDRAWN` would name if it ever had to be pulled. A throwaway test read the file and
  verified it against `BetaKeys.SUPPORTER` before it went anywhere, so the code in the shop file is known to work.
- **`~/Downloads/folio-early-access.txt` rebuilt** around it, with the instructions pointing at Settings › Supporter
  and a line about the beta switch. Same words otherwise.
- `~/.folio/folio-supporter.pem` and any code from it are dead. Delete them when convenient; nothing reads them.

Still to do, and it is McCal's to do because it needs a passphrase typed:

- **Encrypt the live key.** `cd ~/dev/duo-fold-launcher && ~/dev/folio-0.7.0/scripts/beta-code.py protect`. It asks
  twice, proves the encrypted copy opens before replacing the old file, and from then on minting asks for the
  passphrase or reads `FOLIO_KEY_PASSPHRASE`. Put the passphrase in a password manager first: without it the key is
  gone, and losing the key is worse than leaking it.
- **Back it up offline**, after encrypting rather than before.

Still done and still good: `~/Downloads/folio-kofi-shop.jpg`, the 1080×1080 shop image from the Mockup Lab.

Left to do, on Ko-fi itself:

1. Ko-fi → **Shop** → add an item, with the image and the text below.
2. Attach the re-minted `folio-early-access.txt` as the digital file.
3. Buy it yourself for the minimum, check the file arrives, and paste the code into Settings › Supporter on the
   phone. It should say "Code added — thank you", and the Market should appear.

## Who can make a code

Only whoever has `supporter-key.pem`. A code is an ECDSA P-256 signature over its own bytes: the public half in the
app can check one, and can't be used to make one.

So nobody can forge a code. Three things that are worth being clear-eyed about, because they aren't forgery:

- **A code can be passed around.** That's deliberate. If it ever matters, mint dated ones (`--expires`) or a code
  per person through `tools/kofi-worker/`.
- **Folio is MIT, and the check runs on the phone.** Anyone can build from source with the check removed. No
  client-side check survives that, and pretending otherwise would mean shipping something closed. The answer is that
  the free core is worth having on its own, so there's little to gain.
- **Beta Updates used to open the Market too.** It doesn't since 2026-09-19 (McCal): the store is what a supporter
  gets for supporting, and a switch anyone can flick is not that. It goes the other way round now - redeeming a code
  switches Beta Updates on, so a supporter gets the builds as well as the store, and can turn that off on its own.

Keeping the key safe, in order of how much it buys:

1. **Back it up offline.** Losing it is worse than leaking it: every code already handed out dies with it.
2. **Encrypt it on disk:** `./scripts/beta-code.py protect`. It asks for a passphrase twice, writes the encrypted
   copy beside the old file, checks it reads back as the same key, and only then replaces it - a wrong passphrase or
   a full disk leaves the key exactly as it was. Minting afterwards asks for the passphrase, or reads
   `FOLIO_KEY_PASSPHRASE`. A new key can start that way with `newkey --passphrase`.

   The passphrase belongs in a password manager: **without it the key is gone**, and losing the key is worse than
   leaking it. Replace the offline backup afterwards, since the old backup is still unencrypted.
3. **Never let it near a server.** `tools/kofi-worker/` hands out pre-minted codes for exactly this reason.
4. **CI checks the repository for private keys** on every push (`tools/check-secrets.sh`). `.gitignore` covers the
   usual names, but ignoring a file doesn't stop `git add -f` or a key pasted into a document.

If it ever does leak: mint a new key, put its public half in `BetaKeys.SUPPORTER`, ship it, and say so in the
release notes. Every code made with the old key stops working at that release, including the honest ones - so
supporters need new codes, which is the real cost of a leak. A code that went around publicly rather than a key can
be withdrawn on its own, by putting its serial in `BetaKeys.WITHDRAWN`; that takes effect when people update.

## Getting a code to a supporter

Ko-fi has three ways in, in order of how little work they are:

| Way | What the supporter does | What McCal does |
|---|---|---|
| **Supporters-only post** | Follows the page, then reads the post | Writes one post with the code in it, marked supporters-only |
| **Shop item** (digital) | Buys a "Folio early access" item | Uploads a small text file with the code; Ko-fi sends it automatically |
| **Membership tier** | Joins a monthly tier | Same post, restricted to the tier |

The shop item is the only one that works while asleep, and it's the one to start with. A code with no expiry means the
file never needs changing; a dated code means re-uploading it when it runs out.

### A code per person, automatically

`tools/kofi-worker/` is a Cloudflare Worker that does this when the shop item isn't enough: Ko-fi posts to it on every
payment, it checks the payment is really Ko-fi's, takes one code off a batch minted offline, and emails it.

The point of the design is that **the signing key never goes online**. Codes are minted on the Mac and uploaded; the
worker only hands them out, so breaking into it leaks a handful of codes rather than the ability to make them. Its
README has the setup, and the whole thing is optional — start with the shop item.

## The shop item

A draft, not copy — **the words are McCal's.** Ko-fi asks for a title, a price, a description and an image.

**One item** (McCal, 23 Sep 2026), now that a one-off means one month:

| Title | Price | Delivery |
|---|---|---|
| Folio early access · 1 month | $3 | the worker sends a code of its own to each buyer |

The worker is live, so the item needs **no digital file attached**: add its `direct_link_code` to `POOLS` under
`shop` and every buyer gets their own code, which can be withdrawn on its own if it ends up posted somewhere. A file
attached in Ko-fi would hand the same code to everyone instead. Emailing needs `RESEND_KEY` and a verified sending
domain; without one the code is still claimed and recorded, and the admin page shows who is waiting.

*(Superseded: three items at $3, $6 and $12 for one, two and four months, each with a code file attached.)*

$3 stays the entry price he settled on 18 Sep ($2 first, raised once the fees were on the table): a card fee is
roughly a fixed 30c plus a few percent, and Ko-fi's own cut applies unless the account has Gold, so about $2.20 of a
$3 sale arrives against roughly $1.30 of a $2 one, because the fixed part is what bites at small prices. It also
matches Ko-fi's default coffee and the Coffee tier. Check the live numbers on the Ko-fi page rather than trusting
these.

- **Image:** `~/Downloads/folio-kofi-shop.jpg` (the same one on all three)
- **Digital file:** one per item, each holding a months code. Because the month starts when the code is redeemed,
  these files never go stale and never need re-uploading.

Description (McCal, 2026-09-18). Every line is either one of his own sentences from the README, a fact about what the
app does, or - the last line - his answer about where the money goes. Nothing here was written for him. One factual
edit since: the code goes into Settings › Supporter, because Early access moved there when the two supporter-code
systems became one.

> Folio Launcher: a clean, iPhone-style Home Screen for Android, with the jailbreak tweaks I always wanted, and none
> of the lockdown.
>
> This gets you the **Folio Market** before it opens to everyone. It's how Folio hands out themes, tweaks and layouts: packages
> you can get, remove and undo, from sources you choose. Every page says what a package changes and what it can't
> reach before you get it.
>
> The tweaks are the ones I missed from jailbreaking. I've been in that world since iOS 7 or 8. Cabinet after Velox,
> Harborline after Harbor, Roll Call after Axon, Palette after Velvet, Colored Albums after ColorFlow. All re-created
> from scratch for Android; none of their code is in here, and everyone is credited in the app.
>
> You'll get a code to paste into Settings › Supporter, and the store appears.
>
> Everything in the Market is already in Folio's Settings. This is a head start, not a paywall. Folio is free and
> open source and stays that way, and nothing that has already shipped will ever move behind a code. There's no
> account: the code is checked on your phone, and nothing about you is stored or sent.
>
> Fair warning: it's still very early. A bit rusty in places, and it settles down as more people use it. Developed and
> tested on a Galaxy Z Fold8, and it needs Folio 0.6.6 or later from GitHub.
>
> The $3 goes towards test devices and more time to build.

## Memberships, and one-off support

What Ko-fi's tier form asks for is on the page itself (tier name, 30 characters; minimum price per month; benefit
lines; description; a 2:1 tier image; a welcome message that is sent on joining, which is where a code can go; Discord
roles; address; a join limit). Check the live fees and limits on Ko-fi rather than trusting a number written here.

**What Ko-fi can do:** recurring monthly tiers, posts restricted to a tier, a welcome message per tier, shop items
with a digital file attached, and a webhook on every payment carrying the type (`Subscription`, `Shop Order`,
`Donation`), the tier name and the amount.

**What it can't do:** turn a one-off tip into membership time. There is no "$20 buys four months of this tier" in
Ko-fi. So don't try to buy Ko-fi membership with one-off money — hand out **Folio** time instead, which is ours to
give: a supporter code with an end date. The months are Folio's, not Ko-fi's.

The rule from the top of this file still decides everything below: a tier buys a head start and extras on the side.
Nothing already shipped moves behind one.

### The rule to print above the tiers

Folio is open and free, so the membership has to be patronage rather than a paywall, and it has to say so where
someone deciding can read it (McCal, 19 Sep 2026):

> Folio remains Folio whether you support development or not. Supporters help fund continued development and get a
> few extras along the way.

That sentence answers the question an open-source user asks quietly: *are features about to disappear behind a
paywall?* It belongs above the tier list on the page, and in the announcement post.

### What can actually be promised every month

A benefit that can't be delivered monthly turns into a debt. Measured against what Folio has today:

| Benefit | Real now? |
|---|---|
| Supporters-only posts, before the public ones | **Yes** — the 0.6.5 posts are written |
| Design previews of what's being built | **Yes** — the Mockup Lab produces them already |
| A vote on what comes next | **Yes** — the supporters post carries one |
| A code for the Market before it ships | **Yes** — `beta` scope, checked offline |
| Keyd, the keyboard extras | **Yes** — `keys` scope, merged for 0.6.5 |
| Beta builds before they're public | **Yes**, once 0.6.5 is out |
| Preview and experimental builds | **Yes**, same channel as the betas |
| A weekly "This week in Folio" post | **Only if it's written** — the information already exists as work happens |
| A supporter Discord role | **Yes, since 22 Sep 2026.** The Folio Discord exists (discord.gg/pxQT9Xj2Ed), and Ko-fi hands out the role
  once the server is connected (Ko-fi › Settings › Integrations › Discord). Every monthly tier gets it |
| A name in supporter acknowledgements | **No** — there's no supporters list in the app or the repository yet |

The last two are the only places the usual membership advice doesn't fit Folio yet. Build them or leave them out;
don't list them.

### The tiers

**Decided (McCal, 19 Sep 2026): Coffee $3, Backer $7, Builder $15.** Names say what you *are* rather than what you
unlock, so the perks can move without the name lying, and $15 reads as funding the project rather than buying a
launcher. **The words are McCal's**; what's written below is a draft he edits. Images:
`lab/walls/kofi-tiers/{coffee,backer,builder,one-time}.jpg`, 1600 × 800, from `lab/kofi-tiers.html`.

**Re-cut 23 Sep 2026**, once every tier earned the code:

| | Coffee $3 | Backer $7 | Builder $15 |
|---|---|---|---|
| A supporter code each month: the Market, Keyd and the betas | ✓ | ✓ | ✓ |
| Supporters-only posts, before the public ones | ✓ | ✓ | ✓ |
| A supporter role in the Folio Discord | ✓ | ✓ | ✓ |
| Design previews of what's being built | ✓ | ✓ | ✓ |
| A vote on what comes after each update | ✓ | ✓ | ✓ |
| Preview builds as soon as there's something to try | | ✓ | ✓ |
| Folio updating itself from the Market, on the beta channel (0.6.7) | | ✓ | ✓ |
| Development posts as the work happens | | | ✓ |
| Tweak and theme requests read first | | | ✓ |
| A lasting Supporter badge, and a name in Supporters (0.6.7) | | | ✓ |

**Keyd is not a tier difference** (decided 23 Sep, after checking the code). Redeeming any code adds the supporter
source, which is where Keyd lives, so every supporter can install it. The `keys` scope only shows the KEYD card in
Settings › Supporter. Making Keyd a Backer perk would mean gating the supporter source on that scope in the app, and
it would take something away from codes already out; Backer's difference is the preview builds and the Folio Beta
source instead.

**Backer is the card that leads** — more benefits, the "everything in Coffee, plus" framing, and a lighter card with
a teal ring around it. No "best value", no scarcity; the weight is in the design, not the shouting.

Each tier needs a description and a welcome message on Ko-fi. The welcome message is where a code goes for the two
paid tiers (see *Getting the code out automatically*), and every card carries the "Folio remains Folio" line.

One line that is **not** on any tier, because it doesn't exist yet: a name in supporter acknowledgements in the app
(the Supporters list is a mockup until 0.6.7). The Discord role is real: the server exists, and every monthly tier
gets the role through Ko-fi's Discord integration. "Development posts as the work happens" is a writing commitment, not a built feature — it
becomes real with the first one, and *This week in Folio* is the shape suggested for it.

### The copy for each Ko-fi field

Drafted 22 Sep 2026 and re-cut on the 23rd for the rule as it stands: a month of any tier earns two months of the
code, a one-off of $3 or more earns one, and $15 or more also earns the lasting Supporter badge. The only promises here
about something unbuilt are the ones marked 0.6.7 (the badge and the Supporters list, mockup
`docs/mockups/lasting-supporter.html`). "Within a day" is honest until `RESEND_KEY` is set; when it is, those lines
become "your code is in this message".

**Page intro, above the tiers**

> Folio is a clean, iPhone-style Home Screen for Android, with the jailbreak tweaks I always wanted, and none of the
> lockdown.
>
> Folio remains Folio whether you support development or not. The core is free and stays free: Home, the island,
> panels, gestures and themes are never behind a code. Supporting buys me time to keep building, and the extras are
> the thank-you.

**Coffee · $3 a month — description**

> A coffee a month, and the work keeps going. It comes with a supporter code good for two months: the Folio Market
> before it opens to everyone, Keyd, and the betas. You also get supporters-only posts, design previews of screens
> I'm still deciding on, a vote on what I build next after each update, and the supporter role in the Folio
> Discord. Folio remains Folio whether you support development or not.

**Coffee — welcome message**

> Thank you for backing Folio.
>
> Your supporter code is coming in a separate message within a day. To use it: Folio › Settings › Supporter ›
> Redeem a Code. It opens the Market, adds Keyd, and turns on beta builds for two months, and a new code comes with
> each month's payment, so there is always time in hand.
>
> You'll also get supporters-only posts here before they go public, design previews of what I'm working on, and a
> vote on what comes next after each update. The next vote is on the order I build 0.6.7 in.
>
> The Folio Discord is at https://discord.gg/pxQT9Xj2Ed. Link your Discord account to Ko-fi (Ko-fi › Settings ›
> Connections) and you'll get the supporter role there.
>
> McCal

**Backer · $7 a month — description**

> The same code as Coffee, and more of my time. Every month it opens the Folio Market before it's open to everyone,
> unlocks Keyd (the keyboard, split around the crease, no permissions at all), and brings preview builds when
> there's something to try. What $7 adds is that it keeps this going: more test devices, more hours on the fold.
> The supporter role in the Folio Discord comes with it.

**Backer — welcome message**

> Thank you for backing Folio.
>
> Your supporter code is coming in a separate message within a day. To use it: Folio › Settings › Supporter › Redeem
> a Code. It opens the Market, adds Keyd, and turns on beta builds.
>
> The code is checked on your phone against a key inside Folio, so it works offline and tells me nothing about who
> redeemed it. It lasts two months from the day you redeem it, and a new one comes with each month's payment.
>
> Betas can have more bugs than releases. Settings › Software Update › Beta Updates turns them off whenever you like.
>
> The Folio Discord is at https://discord.gg/pxQT9Xj2Ed. Link your Discord account to Ko-fi (Ko-fi › Settings ›
> Connections) and you'll get the supporter role there.
>
> McCal

**Builder · $15 a month — description**

> Everything in Backer, for the people who want to shape where Folio goes. Beta builds as soon as they exist,
> development posts as the work happens, and your tweak and theme requests read first. It also earns the supporter
> role in the Folio Discord, the lasting Folio Supporter badge in the app, and your name in Supporters if you'd like
> it there. (The badge and the list arrive in 0.6.7.)

**Builder — welcome message**

> Thank you for building Folio with me.
>
> Your supporter code is coming in a separate message within a day. In Folio: Settings › Supporter › Redeem a Code.
> It opens the Market, adds Keyd, and brings the betas as soon as they exist. A new code comes with each month's
> payment, and each one lasts two months and carries the lasting Folio Supporter badge.
>
> Your support also earns the lasting Folio Supporter badge in the app, which stays for good, not just while a code
> is live. It arrives in 0.6.7. Want your name in Settings › Supporters? Reply here and tell me how to write it.
> Nothing goes in without your yes.
>
> Got a tweak or a theme you want in Folio? Reply and tell me. Yours are the ones I read first.
>
> The Folio Discord is at https://discord.gg/pxQT9Xj2Ed. Link your Discord account to Ko-fi (Ko-fi › Settings ›
> Connections) and you'll get the supporter role there.
>
> McCal

**One-time support — the support box**

> Not after a monthly thing? A one-off works too. $3 or more earns a one-month supporter code: the Market, Keyd and
> the betas, and each payment counts, so two tips are two months. $15 or more also earns the lasting Folio
> Supporter badge, and your name in Supporters if you'd like. (The badge and the list arrive in 0.6.7.) The Discord
> role goes with the monthly tiers.

**One-time support — thank-you message**

> Thank you.
>
> I'll send you a supporter code within a day. In Folio: Settings › Supporter › Redeem a Code. It opens the Market,
> adds Keyd, the keyboard, and turns on beta builds, for a month from the day you redeem it. Every payment earns
> one, so if you come back, so does the month.
>
> If you gave $15 or more, it also earns the lasting Folio Supporter badge in the app, which stays after the month
> ends. That arrives in 0.6.7. Reply if you'd like your name in Settings › Supporters.
>
> Folio remains Folio whether you support development or not. The core is free and stays free; this buys me time to
> keep building.
>
> McCal

**Where each one goes on Ko-fi** (McCal, 23 Sep 2026). A tier's description and welcome message are both on the tier
itself: Ko-fi › Settings › Memberships, then Add Tier or Edit, and the Welcome Message box sits under the name,
price and description. Save publishes it. The welcome message is shown on screen right after payment and emailed as
well, and it is text only, so the Discord invite goes in as a plain link. The one-off thank-you is elsewhere:
Ko-fi › Settings › Payment, the box called Auto "Thank You" message.

**One thing to check before pasting any of it.** Every tier's code is the same code, by design (`"*"` in `POOLS`),
so what a tier buys beyond it is what McCal writes above: time, attention, and for $15 the lasting badge. Only
Builder is named in `POOLS`, so that its payments draw from the `thanks` pool; the rest fall to `"*"`.

### Positioning: two audiences, one promise each

**Decided (McCal, 19 Sep 2026): split by venue.**

- **GitHub, Ko-fi and Reddit** keep his own line from 18 Sep: "a clean, iPhone-style Home Screen for Android — with
  the jailbreak tweaks I always wanted, and none of the lockdown." That audience is self-selected and already looking
  for exactly this.
- **The Play listing, when it happens**, leads with the outcome instead — *make Android feel intentional*, a
  polished, customizable launcher for a calmer home screen — and treats the iOS-inspired work as *how*, not *what*.
  Play's store-listing guidance is stricter about metadata or graphics implying a relationship with another
  company's product, and the wider framing doesn't narrow the audience to people who want an iPhone clone.

Both must keep saying the same thing about what Folio *does*; only the emphasis changes. When one is edited, check
the other.

### One-off support: a month per donation

**Decided (McCal, 23 Sep 2026):** **every payment earns the code.** A tip or donation of any size, each payment
counting (two tips, two months), a month of any tier, and the shop item all open the same thing. The tiers stay
**Coffee $3, Backer $7, Builder $15** and differ in what McCal gives beyond the code, not in what the code opens.

**How long each one lasts (McCal, 23 Sep, later the same day):** a one-off or the shop item is **one month**; a
month of any tier is **two**. Not a better deal for members so much as a safer one: a code starts counting when it is
redeemed, so a member who pays on the 1st and redeems on the 3rd would be locked out for two days before the next
payment landed. Two months overlap, and a late or retried payment never reads as a lapse. The reasoning lives beside
`POOLS` in `tools/kofi-worker/wrangler.toml`, which is the file that actually decides it.

*(Superseded, from earlier the same day: one month for every payment, member or not.)*

Why it landed there: the Market needs a code carrying the `beta` scope (`MarketFeature.kt`), which also turns the beta
channel on, so the app can't separate "the store" from "the builds". The only scope with a feature behind it is
`keys`, for Keyd's card, and Keyd is in the Market anyway. A weaker Coffee code would have meant per-tier pools for a
difference Folio can't really express, and a $3 shop item would have undercut it regardless. One rule, one sentence:
$3 is a month, wherever you pay it.

**On top of it, the lasting badge (McCal, 22 Sep, restored 23 Sep once it was built):** **$15 or more** earns the
lasting Supporter badge as well. A month of Builder draws from `m2thanks` (two months, like every tier, plus the
badge) and a one-off of $15 or more from `thanks` (one month, as one-offs are, plus the badge). It buys recognition,
not more time, and these codes run out like any other: redeeming one only has Folio write down the month, so there
is no lasting code to withdraw later.

*Superseded (22 Sep):* Backer and Builder only, with Coffee carrying no code.

*Earlier (19 Sep 2026):* a one-off payment earned a dated code, $3 → one month, $6 → two, $12 → four, matching the
Coffee tier. The shop, worker and pool notes below were written for that rule; the pools become one-month pools.
Three ways to do it, cheapest first.

1. **Shop items, one per length.** "Folio early access · 1 month", "· 2 months", "· 4 months", each with a months
   code attached. Nothing to run, works while asleep, and the files never go stale.
2. **Tips, through the worker.** `tools/kofi-worker/` picks a pool by tier name, by shop item, by a single tip
   threshold (`tipFrom` / `tipPool`), and — since 19 Sep 2026 — by amount: `tipBands` is a list of `{from, pool}`, so
   $3, $6 and $12 land in the one-month, two-month and four-month pools. A payment earns the largest band it clears.
3. **Ko-fi's own annual option**, if it suits — a membership paid yearly is still a membership, and the worker sees it
   as `Subscription`.

**How the months work, now that the phone starts the clock.** A code is signed offline, so a fixed end date is
decided when the code is *minted*, not when it is *redeemed* — everyone in a `--expires 2027-01-01` pool gets the same
last day, and whoever pays on the 28th gets a short month. So codes grew a second shape, built 19 Sep 2026:

```bash
python3 scripts/beta-code.py mint --scopes beta,keys --months 1        # one month from the day it's redeemed
python3 scripts/beta-code.py pool --scopes beta,keys --months 4 --count 50 --pool months4 > pool.sql
```

`--months` mints a version 2 code: months and tier share one byte (months in the high nibble), so the code is the same
length and version 1 codes read exactly as before. Folio writes down the day a code was first redeemed on that phone,
one date per serial, and counts from there. Pasting the same code again resumes the window it started rather than
handing out another month, and removing the code doesn't reset it. A code carrying both months and a fixed date ends
on whichever comes first. Settings › Supporter shows the day it runs out.

Two things this does not change: a pre-0.6.5 build has no key at all, and a build older than this change reads a
version 2 code as "not a Folio code" — so months codes are for 0.6.5 and later. And `--months` takes 1 to 15; for
longer, use `--expires`.

### Getting the code out automatically

Three ways, cheapest first. What makes the cheap ones work is months-from-redemption (19 Sep): a `--months 1` code in
a file starts its month when the buyer redeems it, so the file never goes stale and never needs re-uploading.

| Path | What it takes | What it covers |
|---|---|---|
| **Shop item with the code file attached** | Nothing to run. Ko-fi emails the file on purchase | One-off support |
| **Tier welcome message** | Nothing to run. Ko-fi sends it when someone joins | New members, once — not monthly |
| **`tools/kofi-worker`** | A Cloudflare deploy, `KOFI_TOKEN` and `RESEND_KEY` | Every tier payment, shop order and tip, one unique code each |

The gap in the cheap path: a welcome message fires once, so a member's code has to outlast the join. Either mint the
member codes with a longer window (`--months 3`) and post a fresh one to the supporters feed when it runs out — the
posts are a benefit anyway — or deploy the worker, which mints a code per payment, two months for a member and
one for a one-off, and needs no post at all.

**Settled (McCal, 19 Sep 2026).** The shop item was $3 for a code that *never* expired, while Backer at $7/month
leads on that same early access — anyone who noticed would have bought the $3 item instead, and been right. One price
now means one month everywhere:

| One-off | Code |
|---|---|
| any amount | `--months 1` |

*(Until 22 Sep this was $3, $6 and $12 for one, two and four months.)*

and the membership promise is "supporter access stays on while your membership does". `tipBands` in the worker reads
`[{from:3,pool:'months1'},{from:6,pool:'months2'},{from:12,pool:'months4'}]`. Mint the pools with:

```bash
cd ~/.folio
for m in 1 2 4; do
  python3 ~/dev/folio-0.7.0/scripts/beta-code.py pool --key supporter-key.pem --scopes beta,keys \
    --months $m --count 50 --pool months$m >> pool.sql
done
```

`pool.sql` holds real codes, so it never gets committed.

### The admin page

`./scripts/code-admin.py`, run from the checkout that holds `supporter-key.pem`, opens a page on this Mac only
(127.0.0.1, a fresh token each launch). Drop in the Ko-fi CSV export and it lists who needs a code and for how many
months; one click hands out a ready code of that length or mints one, with the code, the `folio://redeem` link and a
draft message to copy. It also marks codes sent or withdrawn (and prints the serial for `BetaKeys.WITHDRAWN`), and
makes ready codes or a `tools/kofi-worker/pool-m<N>.sql` pool. Everything it knows is in `supporter-ledger.json`
(gitignored, 0600, real codes and emails); the first run takes in the codes already in `supporter-codes*.txt`.

Ko-fi has no API for reading transactions, so the CSV covers what already happened and the worker's webhook is the
only way codes go out on their own. The key never leaves the Mac: the worker and Folio Dev only ever hand out codes
minted here in advance.

### Two posts, in this order

The release first, the ask second — a day or two apart.

**Day 1 — Folio 0.6.5 is out.** Lead with the software: what changed, a short screen recording, the download button,
and the trust layer that a launcher needs because it asks for permissions that look alarming out of context — the APK
checksum, the VirusTotal scan and the permissions documentation. Nothing about money.

**Day 2 or 3 — Support Folio.** Now the tiers, with the "Folio remains Folio" line above them. The order matters: it
makes the message *here is something useful I shipped*, before *here is where you can help*.

### The announcement post

McCal's own draft (19 Sep 2026), with one fact corrected: there is no supporters
acknowledgements list in the app yet, so it isn't promised here.

> **Folio now has a way to support development.**
>
> I've been building Folio because I wanted an Android launcher that feels deliberate, polished and customizable
> without trying to fight the platform it runs on.
>
> As the project has grown, so has the amount of work behind it: testing devices, maintaining releases, building the
> Market and package system, fixing edge cases, documentation, infrastructure and everything else that comes with
> maintaining an actual piece of software.
>
> So I've opened three optional supporter tiers: **<tier names>**.
>
> Supporting Folio is not required to use the project. The goal is not to put the launcher behind a paywall.
>
> Supporters instead help fund continued development and get a few extras depending on the tier: development
> updates, previews of what I'm building, the Market before it ships, preview builds, and a say in what comes next.
>
> If Folio has made your phone a little better and you want to help me keep building it, you can now do that.
>
> And if you cannot, or simply do not want to pay, keep using Folio, reporting bugs, sharing feedback and showing
> people what you've built with it. That helps too.
>
> Thank you for giving this weird little launcher project a chance.

### A supporter benefit that pays for itself

"This week in Folio" — foldables: fixed X; Market: added Y; gestures: trying Z; one thing that broke; next: R. The
information already exists as the work happens, which is what makes it sustainable, and it's the kind of thing that
builds credibility faster than polished copy. It is also the honest version of "development updates" in the tier
list: promise it only once one has been written.

### Already done, and worth knowing

`.github/FUNDING.yml` already points GitHub's Sponsor button at `ko_fi: mccal`, so the repository side of the funnel
is in place. The order the rest of the funnel should read in: **Download Folio** first, then what it does, then the
source, the documentation, the community, and support last.

## The page itself

Things Ko-fi asks for, with what Folio needs each one to say. **The words are McCal's — these are placeholders, not
copy:**

- **Page title and tagline.** What Folio is, in one line, for someone who has never seen it.
- **About.** A short paragraph: what Folio does, that it's free and open source, and what a coffee actually pays for
  (test devices, time). `docs/launch-0.6.5-kofi.md` has the tone from the last update.
- **Goal.** Optional, and honest if used: a named thing the money is for, not a number with nothing behind it.
- **Shop item.** "Folio early access" — what it unlocks now (the Market, before it ships), and plainly that everything
  in it is already in Settings.
- **Gallery.** The feature wall from the Mockup Lab, and real Fold8 screenshots.

## What it says inside the app

- **Settings › Support Folio** — a row that opens the page.
- **Settings › Supporter** — redeem a code, see what it unlocks and when it runs out, remove it, and turn the beta
  features it carries on or off. The Market's own settings page points here rather than offering a second box.
- The Market is hidden entirely without a code or a dev build (`MarketFeature`), and it says so where
  someone would look for it.
