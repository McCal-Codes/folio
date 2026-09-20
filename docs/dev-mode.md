# The developer's own switches

One build, three faces: what a stranger sees, what a supporter sees, and what the developer sees. It exists so a
gated feature can be checked without three phones and three codes, and so a bug reported against an older Folio can
be reproduced without installing that older Folio.

**It cannot be reached from a build anyone else has.** Two locks, both of which have to be open:

1. The package id ends in `.dev`. A release build's doesn't.
2. `BetaKeys.TEST` holds a development public key, and a code signed with its private half has been pasted in
   Settings › Supporter. The private half lives in `~/.folio/folio-dev-key.pem`, mode 600, never committed.

A release build refuses the development key outright: the check is on the package name, so `com.mccal.folio` will not
open these switches whatever code is pasted into it.

## Making the key, once

```bash
mkdir -p ~/.folio && cd ~/.folio
python3 ~/dev/duo-fold-launcher/scripts/beta-code.py newkey --key folio-dev-key.pem
```

It prints the public half. Paste that into `BetaKeys.TEST` (`app/src/main/java/com/mccal/folio/Supporter.kt`). A new
key stops every development code already minted, which matters to nobody but you.

## Minting codes for yourself

```bash
cd ~/.folio
# the unlock: the dev scope, plus every supporter scope so one code does everything
python3 ~/dev/duo-fold-launcher/scripts/beta-code.py mint --key folio-dev-key.pem --scopes dev,beta,look,power,keys --tier 2

# a supporter code to test redeeming, exactly as a supporter's behaves
python3 ~/dev/duo-fold-launcher/scripts/beta-code.py mint --key folio-dev-key.pem --scopes beta,look,power,keys --tier 2

# the same, but one month from the day it is redeemed
python3 ~/dev/duo-fold-launcher/scripts/beta-code.py mint --key folio-dev-key.pem --scopes beta,look,power,keys --tier 2 --months 1
```

The supporter-signed codes in `supporter-codes*.txt` are **not** accepted as an unlock: the dev scope is checked, and
a code handed to a supporter never carries it.

## What the switches do

In Settings › Supporter, on a development build:

- **Behave as** — *Free* is the app as a stranger has it, *Supporter* grants every scope a code can carry without a
  code, *Developer* is that plus anything built for testing. Beta features still need the Beta Updates switch, the
  way they do for a real supporter, so the gate is tested rather than bypassed.
- **Update channel** — *The Beta Updates switch* leaves that switch in charge; *Stable only* and *Betas too* aim
  Software Update regardless of it, so the update path can be walked as a stranger sees it.
- **Features** — each of this release's features can be turned off to see how Folio behaved before it arrived.

**Lock and forget the switches** removes all of it: the unlock, the face, the channel and every feature flag. The
build then behaves exactly like anyone else's.

## Testing a redeem end to end

Install the development build beside the one you use rather than over it — a debug build of an older line is a
downgrade of the newer one, and the newer one is probably your Home app:

```bash
# in a throwaway worktree, so the change never reaches a commit
applicationIdSuffix = ".test.dev"   # still ends in .dev, so the switches still appear
```

Then: paste a supporter code in Settings › Supporter, check the row says when it runs out, force-stop and reopen to
prove it survives, and *Remove code* to prove it goes. A months code re-pasted after its window resumes the window
rather than starting a new one — that is the behaviour worth checking by hand, since no test can see the phone's own
clock.
