#!/usr/bin/env python3
"""Folio's supporter code admin: a page on this Mac for handing out codes.

    ./scripts/code-admin.py                 # opens the page in your browser
    ./scripts/code-admin.py --no-open       # prints the address instead

Drop in the Ko-fi CSV export (Ko-fi › Transactions › Export) and the page shows who has a code and who still needs
one, with the months their payments are worth. One click hands out a code: a ready one from the trackers if there is
one of the right length, or a freshly minted one. The code, its folio://redeem link and a draft message are there to
copy; nothing is sent from here.

Why a page on this Mac and nowhere else: whoever holds supporter-key.pem can mint codes every copy of Folio accepts,
and Folio checks them offline, so a leaked key can't be taken back. The server listens on 127.0.0.1 only, every
launch gets a fresh token that each request has to carry, and the key never leaves the machine. The Ko-fi worker
and Folio Dev only ever get codes minted here in advance.

Everything the page knows is in supporter-ledger.json next to the key (gitignored, 0600). It holds real codes and
email addresses, so it never gets committed, pasted or screenshotted. On first run the ledger picks up the codes
already in supporter-codes*.txt, so nothing handed out before is handed out twice.

Pricing (McCal, 22 Sep 2026): a one-off tip or donation is one month, whatever the amount, each payment counting.
The tiers are Coffee $3, Backer $7 and Builder $15 a month. Backer and Builder include the code, so each of their
monthly payments is one month; Coffee's perks are posts, previews and the vote, with no code.
"""
import argparse
import csv
import datetime
import hmac
import http.server
import importlib.util
import io
import json
import os
import re
import secrets
import sys
import threading
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("beta_code", os.path.join(HERE, "beta-code.py"))
bc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bc)

ALL_SCOPES = ["beta", "look", "power", "keys"]  # what supporters get; "dev" is McCal's alone and never offered here
CODE_PATTERN = re.compile(r"\b[0-9A-HJKMNP-TV-Z]{5}(?:-[0-9A-HJKMNP-TV-Z]{1,5}){10,}\b")
ONE_OFF = {"tip", "donation"}
CODE_TIER_FROM = 7  # Backer ($7) and Builder ($15) carry a code; Coffee ($3) doesn't
MAX_MONTHS = 15  # what a version 2 code can carry


def months_for(payment):
    """What one Ko-fi payment is worth, in months."""
    if payment["type"].lower() in ONE_OFF:
        return 1
    # A membership payment pays for one month of its tier; a shop item is priced as one month too.
    return 1 if payment["amount"] >= CODE_TIER_FROM else 0


class Ledger:
    """Every code this Mac knows about and every Ko-fi payment it has seen, in one JSON file."""

    def __init__(self, path):
        self.path = path
        self.lock = threading.Lock()
        if os.path.exists(path):
            with open(path) as f:
                data = json.load(f)
        else:
            data = {"codes": [], "payments": []}
        self.codes = data["codes"]
        self.payments = data["payments"]

    def save(self):
        temporary = self.path + ".saving"
        fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
        with os.fdopen(fd, "w") as f:
            json.dump({"codes": self.codes, "payments": self.payments}, f, indent=1)
        os.replace(temporary, self.path)
        os.chmod(self.path, 0o600)

    def serials(self):
        return {c["serial"] for c in self.codes}

    def add(self, code, **fields):
        entry = {**bc.describe(code), "code": code, "status": "ready", "name": "", "email": "", "payments": [],
                 "minted": today(), "given": None, "sent": None, "how": "", "note": ""}
        entry.update(fields)
        self.codes.append(entry)
        return entry

    def adopt_trackers(self, root):
        """First run: take in the codes already minted, so the page never hands one out twice."""
        people = {}  # serial → (name, email) from the table at the top of supporter-codes-people.txt
        path = os.path.join(root, "supporter-codes-people.txt")
        if os.path.exists(path):
            for line in open(path):
                row = re.match(r"\s+\d+\s+(.+?)\s{2,}(\S+@\S+)\s+\$\d+, [^0-9]*\d+ \w+\s+(\d+)\s", line)
                if row:
                    people[int(row.group(3))] = (row.group(1).strip(), row.group(2).lower())
        spare = {}  # the month codes minted for the same four, left unused (McCal chose no-expiry codes)
        if os.path.exists(path):
            text = open(path).read()
            if "UNUSED" in text:
                for name, serial in re.findall(r"^(.+?) — \$\d+ — \d+ months? — serial (\d+)$",
                                               text.split("UNUSED", 1)[1], re.M):
                    spare[int(serial)] = name
        found = 0
        for name in sorted(os.listdir(root)):
            if not (name.startswith("supporter-codes") and name.endswith(".txt")):
                continue
            for code in CODE_PATTERN.findall(open(os.path.join(root, name)).read()):
                info = bc.describe(code)
                if info["serial"] in self.serials():
                    continue
                if info["serial"] in people:
                    person, email = people[info["serial"]]
                    # They supported before there was anything to give; the code covers everything up to then.
                    self.add(code, status="given", name=person, email=email, given="2026-09-19",
                             covers_before="2026-09-19", note=f"early supporter, from {name}")
                elif info["serial"] in spare:
                    self.add(code, status="spare", note=f"unused month code for {spare[info['serial']]}")
                else:
                    self.add(code, status="ready", note=f"from {name}")
                found += 1
        return found

    def take_payments(self, rows):
        known = {p["id"] for p in self.payments}
        added = 0
        for row in rows:
            if row["id"] and row["id"] not in known:
                self.payments.append(row)
                known.add(row["id"])
                added += 1
        return added

    def covered(self, payment):
        for code in self.codes:
            if payment["id"] in code["payments"]:
                return code
            email = payment["email"].lower()
            if email and code["email"] == email and code.get("covers_before", "") >= payment["date"][:10]:
                return code
        return None

    def people(self):
        """Payments grouped by person (email, else Ko-fi name), with what's still owed a code."""
        groups = {}
        for p in sorted(self.payments, key=lambda p: p["date"]):
            key = p["email"].lower() or p["name"].lower()
            person = groups.setdefault(key, {"key": key, "name": p["name"], "email": p["email"].lower(),
                                             "payments": [], "codes": []})
            code = self.covered(p)
            person["payments"].append({**p, "months": months_for(p), "code": code["serial"] if code else None})
        for person in groups.values():
            serials = {x["code"] for x in person["payments"] if x["code"]}
            person["codes"] = [c for c in self.codes if c["serial"] in serials or
                               (person["email"] and c["email"] == person["email"])]
            # A payment worth no months (a Coffee membership) never makes someone owed a code.
            owed = [x for x in person["payments"] if not x["code"] and x["months"]]
            person["owed"] = [x["id"] for x in owed]
            person["months"] = min(MAX_MONTHS, sum(x["months"] for x in owed))
        newest_first = sorted(groups.values(), key=lambda g: g["payments"][-1]["date"], reverse=True)
        return sorted(newest_first, key=lambda g: not g["owed"])


def today():
    return datetime.date.today().isoformat()


def read_csv(text):
    rows = []
    for r in csv.DictReader(io.StringIO(text.lstrip("﻿"))):
        get = lambda *names: next((r[n] for n in names if n in r and r[n] is not None), "")
        try:
            when = datetime.datetime.strptime(get("DateTime (UTC)"), "%m/%d/%Y %H:%M").strftime("%Y-%m-%d %H:%M")
        except ValueError:
            when = get("DateTime (UTC)")
        try:
            amount = float(get("Received") or 0)
        except ValueError:
            amount = 0.0
        rows.append({"id": get("TransactionId"), "date": when, "name": get("From"), "email": get("BuyerEmail"),
                     "message": get("Message"), "item": get("Item"), "type": get("TransactionType"),
                     "amount": amount, "currency": get("Currency")})
    return rows


def first_name(name):
    return name.split()[0] if name and " " in name.strip() else name


def message(entry):
    """The draft from the hand-out tracker, filled in. McCal puts it in his own words before it goes anywhere."""
    if entry["months"]:
        lasts = f"It lasts {entry['months']} month{'s' if entry['months'] != 1 else ''} from the day you redeem it."
    elif entry["expires"]:
        lasts = f"It works until {entry['expires']}."
    else:
        lasts = "It doesn't run out."
    paragraphs = [
        "Subject: Your Folio supporter code",
        f"Hi {first_name(entry['name']) or 'there'},",
        "Thank you for supporting Folio.",
        "Your code:",
        entry["code"],
        "To use it: Folio › Settings › Supporter › Redeem a Code, then paste it. You need Folio 0.6.6 (or 0.6.5). "
        f"Or tap this on your phone: {redeem_link(entry)}",
        "It's checked on your phone against a key inside Folio, so it works offline and tells me nothing about who "
        f"redeemed it. {lasts}",
        "What it opens right now: the Market (themes, tweaks and sources, before it opens to everyone in 0.7.0), "
        "Keyd, the keyboard, from the Market, and Beta Features, which arrive a release or two early with more bugs "
        "than usual. You can turn Beta Features off in Settings › Supporter whenever you like.",
        "<one line of your own>",
        "McCal",
    ]
    # One line per paragraph, so it reflows wherever it's pasted.
    return "\n\n".join(paragraphs) + "\n"


def redeem_link(entry):
    return "folio://redeem?c=" + entry["code"]


class Admin:
    def __init__(self, root, key, ledger_path):
        self.root = root
        self.key = key
        self.ledger = Ledger(ledger_path)
        self.signing = None

    def unlock(self):
        """Asked once at launch, like beta-code.py, so the page never handles the passphrase."""
        if not os.path.exists(self.key):
            sys.exit(f"{self.key} isn't here; run from the checkout that holds the key, or pass --key")
        self.signing = bc.passin(self.key)
        # Prove the key opens now rather than on the first click.
        bc.run(["openssl", "ec", "-in", self.key, "-noout"] + self.signing)

    def state(self):
        return {
            "key": os.path.basename(self.key),
            "encrypted": bc.encrypted(self.key),
            "codes": sorted(self.ledger.codes, key=lambda c: (c["given"] or c["minted"]), reverse=True),
            "people": self.ledger.people(),
            "ready": ready_counts(self.ledger.codes),
            "pools": pool_files(self.root),
        }

    def give(self, key, months, scopes, fresh):
        person = next((p for p in self.ledger.people() if p["key"] == key), None)
        if not person:
            raise ValueError("That supporter isn't in the imported payments")
        if not 1 <= months <= MAX_MONTHS:
            raise ValueError(f"Months must be 1 to {MAX_MONTHS}")
        scopes = [s for s in ALL_SCOPES if s in scopes] or ALL_SCOPES
        entry = None
        if not fresh:
            entry = next((c for c in self.ledger.codes if c["status"] == "ready" and c["months"] == months
                          and sorted(c["scopes"]) == sorted(scopes)), None)
        if entry is None:
            version, tier_byte, day = bc.shape(1, None, months)
            code = bc.sign(self.key, self.signing, version, bc.scope_bits(scopes), tier_byte, day)
            entry = self.ledger.add(code, note="minted on the admin page")
        entry.update(status="given", name=person["name"], email=person["email"], payments=person["owed"],
                     given=today())
        self.ledger.save()
        return self.shown(entry)

    def mint_ready(self, months, count):
        if not 1 <= months <= MAX_MONTHS or not 1 <= count <= 50:
            raise ValueError("1 to 15 months, 1 to 50 codes")
        version, tier_byte, day = bc.shape(1, None, months)
        for _ in range(count):
            code = bc.sign(self.key, self.signing, version, bc.scope_bits(ALL_SCOPES), tier_byte, day)
            self.ledger.add(code, note="minted ready on the admin page")
        self.ledger.save()

    def pool(self, months, count):
        """Codes for the Ko-fi worker, written as SQL next to it. They're recorded as the pool's, never handed out here."""
        if not 1 <= months <= MAX_MONTHS or not 1 <= count <= 500:
            raise ValueError("1 to 15 months, 1 to 500 codes")
        name = f"m{months}"
        version, tier_byte, day = bc.shape(1, None, months)
        lines = []
        for _ in range(count):
            code = bc.sign(self.key, self.signing, version, bc.scope_bits(ALL_SCOPES), tier_byte, day)
            self.ledger.add(code, status="pool", note=f"pool {name}")
            lines.append(f"INSERT OR IGNORE INTO codes (code, pool) VALUES ('{code}', '{name}');")
        path = os.path.join(self.root, "tools", "kofi-worker", f"pool-{name}.sql")
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_APPEND, 0o600)
        with os.fdopen(fd, "a") as f:
            f.write("\n".join(lines) + "\n")
        self.ledger.save()
        return os.path.relpath(path, self.root)

    def mark(self, serial, status, how):
        entry = self.find(serial)
        if status == "sent":
            entry.update(status="sent", sent=today(), how=how or entry["how"])
        elif status == "given":
            entry.update(status="given", sent=None)
        elif status == "withdrawn":
            entry.update(status="withdrawn")
        elif status == "ready" and entry["status"] in ("given", "spare"):
            entry.update(status="ready", name="", email="", payments=[], given=None, sent=None)
        else:
            raise ValueError("Unknown change")
        self.ledger.save()
        return self.shown(entry)

    def find(self, serial):
        entry = next((c for c in self.ledger.codes if c["serial"] == serial), None)
        if not entry:
            raise ValueError("No code with that serial")
        return entry

    def shown(self, entry):
        return {**entry, "link": redeem_link(entry), "message": message(entry)}


def ready_counts(codes):
    counts = {}
    for c in codes:
        if c["status"] == "ready":
            counts[c["months"]] = counts.get(c["months"], 0) + 1
    return counts


def pool_files(root):
    folder = os.path.join(root, "tools", "kofi-worker")
    out = {}
    if os.path.isdir(folder):
        for name in os.listdir(folder):
            match = re.fullmatch(r"pool-(m\d+)\.sql", name)
            if match:
                out[match.group(1)] = sum(1 for line in open(os.path.join(folder, name)) if line.strip())
    return out


def handler(admin, token, port):
    allowed_hosts = {f"127.0.0.1:{port}", f"localhost:{port}"}

    class Handler(http.server.BaseHTTPRequestHandler):
        server_version = "FolioCodeAdmin"

        def log_message(self, fmt, *args):
            pass  # requests carry codes; keep them off the terminal

        def refuse(self, status, text):
            body = text.encode()
            self.send_response(status)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def allowed(self, presented):
            # A page elsewhere could aim a request at this port, or rebind a name to 127.0.0.1: the Host check stops
            # the second, the token (never in a cookie, so never sent by someone else's page) stops the first.
            if self.headers.get("Host") not in allowed_hosts:
                self.refuse(403, "Wrong host")
                return False
            if not presented or not hmac.compare_digest(presented, token):
                self.refuse(403, "This page needs the address the admin printed when it started")
                return False
            return True

        def send_json(self, value, status=200):
            body = json.dumps(value).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            path, _, query = self.path.partition("?")
            if path == "/":
                presented = dict(p.split("=", 1) for p in query.split("&") if "=" in p).get("t", "")
                if not self.allowed(presented):
                    return
                body = PAGE.encode()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                self.send_header("Referrer-Policy", "no-referrer")
                self.send_header("Content-Security-Policy",
                                 "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
                                 "connect-src 'self'; img-src data:; base-uri 'none'; form-action 'none'")
                self.end_headers()
                self.wfile.write(body)
            elif path == "/api/state":
                if self.allowed(self.headers.get("X-Admin-Token", "")):
                    with admin.ledger.lock:
                        self.send_json(admin.state())
            else:
                self.refuse(404, "Not here")

        def do_POST(self):
            if not self.allowed(self.headers.get("X-Admin-Token", "")):
                return
            size = int(self.headers.get("Content-Length") or 0)
            if size > 5_000_000:
                return self.refuse(413, "Too big")
            raw = self.rfile.read(size).decode("utf-8", "replace")
            try:
                with admin.ledger.lock:
                    self.send_json(self.act(self.path, raw))
            except ValueError as problem:
                self.send_json({"error": str(problem)}, 400)
            except SystemExit as problem:  # beta-code.py reports openssl failures this way
                self.send_json({"error": str(problem)}, 500)

        def act(self, path, raw):
            if path == "/api/import":
                rows = read_csv(raw)
                if not rows or not any(r["id"] for r in rows):
                    raise ValueError("That doesn't look like a Ko-fi transactions export")
                added = admin.ledger.take_payments(rows)
                admin.ledger.save()
                return {"added": added, "seen": len(rows), **admin.state()}
            body = json.loads(raw or "{}")
            if path == "/api/give":
                shown = admin.give(body["person"], int(body["months"]), body.get("scopes", ALL_SCOPES),
                                   bool(body.get("fresh")))
                return {"shown": shown, **admin.state()}
            if path == "/api/show":
                return {"shown": admin.shown(admin.find(int(body["serial"])))}
            if path == "/api/mark":
                shown = admin.mark(int(body["serial"]), body["status"], body.get("how", ""))
                return {"shown": shown, **admin.state()}
            if path == "/api/ready":
                admin.mint_ready(int(body["months"]), int(body["count"]))
                return admin.state()
            if path == "/api/pool":
                written = admin.pool(int(body["months"]), int(body["count"]))
                return {"written": written, **admin.state()}
            raise ValueError("Unknown action")

    return Handler


PAGE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Supporter Codes</title>
<style>
:root {
  --bg: #f2f2f7; --card: #fff; --text: #000; --secondary: #3c3c4399; --tertiary: #3c3c434d; --sep: #3c3c434a;
  --tint: #007aff; --green: #34c759; --orange: #ff9500; --red: #ff3b30; --fill: #7878801f; --teal: #30b0c7;
}
@media (prefers-color-scheme: dark) {
  :root {
    --bg: #000; --card: #1c1c1e; --text: #fff; --secondary: #ebebf599; --tertiary: #ebebf54d; --sep: #54545899;
    --tint: #0a84ff; --green: #30d158; --orange: #ff9f0a; --red: #ff453a; --fill: #7878805c; --teal: #40c8e0;
  }
}
* { box-sizing: border-box; }
body { margin: 0; background: var(--bg); color: var(--text);
  font: 17px/1.3 -apple-system, BlinkMacSystemFont, "SF Pro Text", system-ui, sans-serif; -webkit-font-smoothing: antialiased; }
main { max-width: 720px; margin: 0 auto; padding: 0 16px 64px; }
h1 { font: 700 34px/1.2 -apple-system, "SF Pro Display", system-ui; letter-spacing: .01em; margin: 44px 4px 4px; }
.sub { color: var(--secondary); margin: 0 4px 20px; font-size: 15px; }
.header { font-size: 13px; text-transform: uppercase; color: var(--secondary); margin: 28px 16px 7px; letter-spacing: .02em; }
.footer { font-size: 13px; color: var(--secondary); margin: 7px 16px 0; }
.group { background: var(--card); border-radius: 10px; overflow: hidden; }
.row { display: flex; align-items: center; gap: 12px; min-height: 44px; padding: 11px 16px; position: relative; }
.row + .row::before { content: ""; position: absolute; top: 0; left: 16px; right: 0; border-top: .5px solid var(--sep); }
.row .main { flex: 1; min-width: 0; }
.row .title { overflow-wrap: anywhere; }
.row .detail { color: var(--secondary); font-size: 15px; margin-top: 2px; overflow-wrap: anywhere; }
.row .value { color: var(--secondary); white-space: nowrap; }
.pill { display: inline-flex; align-items: center; line-height: 1; font-size: 12px; font-weight: 600; padding: 3px 8px; border-radius: 99px; white-space: nowrap; }
.pill.need { background: color-mix(in srgb, var(--orange) 18%, transparent); color: var(--orange); }
.pill.done { background: color-mix(in srgb, var(--green) 18%, transparent); color: var(--green); }
.pill.muted { background: var(--fill); color: var(--secondary); }
.pill.bad { background: color-mix(in srgb, var(--red) 16%, transparent); color: var(--red); }
button, .button { font: inherit; border: 0; background: none; color: var(--tint); cursor: pointer; padding: 0; min-height: 44px; }
button.filled { background: var(--tint); color: #fff; border-radius: 10px; padding: 0 16px; font-weight: 600; }
button.tinted { background: color-mix(in srgb, var(--tint) 15%, transparent); color: var(--tint); border-radius: 99px;
  padding: 0 14px; min-height: 34px; font-size: 15px; font-weight: 600; }
button:disabled { opacity: .4; cursor: default; }
.drop { display: block; cursor: pointer; border: 1.5px dashed var(--tertiary); border-radius: 10px; padding: 22px 16px; text-align: center; color: var(--secondary); background: var(--card); }
.drop.over { border-color: var(--tint); color: var(--tint); }
.drop input { display: none; }
.stepper { display: inline-flex; background: var(--fill); border-radius: 8px; align-items: center; }
.stepper button { min-height: 32px; width: 40px; color: var(--text); font-size: 20px; }
.stepper span { min-width: 64px; text-align: center; font-variant-numeric: tabular-nums; font-size: 15px; }
.banner { background: color-mix(in srgb, var(--orange) 14%, var(--card)); border-radius: 10px; padding: 12px 16px; font-size: 15px; margin-top: 16px; }
.segmented { display: flex; background: var(--fill); border-radius: 9px; padding: 2px; margin: 0 0 8px; }
.segmented button { flex: 1; min-height: 30px; font-size: 13px; font-weight: 500; color: var(--text); border-radius: 7px; }
.segmented button.on { background: var(--card); box-shadow: 0 3px 8px #0000001f, 0 3px 1px #0000000a; font-weight: 600; }
code, .mono { font: 13px/1.45 ui-monospace, "SF Mono", Menlo, monospace; overflow-wrap: anywhere; }
dialog { border: 0; padding: 0; border-radius: 14px; width: min(560px, calc(100vw - 32px)); max-height: calc(100vh - 48px);
  background: var(--bg); color: var(--text); box-shadow: 0 20px 60px #0000004d; }
dialog::backdrop { background: #0006; }
.sheet-head { display: flex; justify-content: space-between; align-items: center; padding: 6px 16px; background: var(--card); border-bottom: .5px solid var(--sep); position: sticky; top: 0; }
.sheet-head b { font-weight: 600; }
.sheet-body { padding: 0 16px 20px; overflow: auto; }
pre.message { white-space: pre-wrap; margin: 0; padding: 12px 16px; font: 14px/1.45 -apple-system, system-ui; overflow-wrap: anywhere; }
.toast { position: fixed; left: 50%; bottom: 24px; transform: translateX(-50%); background: var(--card); color: var(--text);
  padding: 10px 18px; border-radius: 99px; box-shadow: 0 6px 24px #0003; font-size: 15px; opacity: 0; transition: opacity .2s; pointer-events: none; }
.toast.on { opacity: 1; }
.empty { color: var(--secondary); text-align: center; padding: 18px 16px; font-size: 15px; }
.actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
@media (max-width: 520px) { .row { flex-wrap: wrap; } .actions { width: 100%; justify-content: flex-start; } h1 { font-size: 30px; } }
</style>
</head>
<body>
<main>
  <h1>Supporter Codes</h1>
  <p class="sub" id="sub">Codes are signed on this Mac. Nothing here is sent anywhere.</p>
  <div id="warn"></div>

  <div class="header">Ko-fi</div>
  <label class="drop" id="drop">
    <input type="file" id="file" accept=".csv,text/csv">
    Drop the Ko-fi transactions CSV here, or <span style="color:var(--tint)">choose it</span>
  </label>
  <div class="footer">Ko-fi › Transactions › Export. Payments are remembered, so each month's file only adds what's new.</div>

  <div class="header" id="needHead">Needs a code</div>
  <div class="group" id="need"></div>
  <div class="footer">A tip or donation is one month each. Backer and Builder payments are a month each; Coffee has no code.</div>

  <div class="header">Has a code, or needs none</div>
  <div class="group" id="have"></div>

  <div class="header">Codes</div>
  <div class="segmented" id="filter">
    <button data-f="ready" class="on">Ready</button><button data-f="given">Given</button><button data-f="sent">Sent</button><button data-f="other">Other</button>
  </div>
  <div class="group" id="codes"></div>

  <div class="header">Make codes</div>
  <div class="group">
    <div class="row">
      <div class="main"><div class="title">Months</div></div>
      <div class="stepper"><button data-step="-1" aria-label="Fewer months">−</button><span id="mMonths">1 month</span><button data-step="1" aria-label="More months">+</button></div>
    </div>
    <div class="row">
      <div class="main"><div class="title">How many</div></div>
      <div class="stepper"><button data-count="-1" aria-label="Fewer">−</button><span id="mCount">5</span><button data-count="1" aria-label="More">+</button></div>
    </div>
    <div class="row">
      <div class="main"><div class="title">Ready to hand out</div><div class="detail">Kept here for the next supporters</div></div>
      <button class="tinted" id="makeReady">Make</button>
    </div>
    <div class="row">
      <div class="main"><div class="title">For the Ko-fi worker</div><div class="detail" id="poolDetail">Written to tools/kofi-worker as SQL</div></div>
      <button class="tinted" id="makePool">Make</button>
    </div>
  </div>
  <div class="footer">Every code opens the Market, Keyd and Beta Features, for its months counted from the day it's redeemed.</div>
</main>

<dialog id="sheet">
  <div class="sheet-head"><button id="close">Done</button><b id="sheetTitle">Code</b><span style="width:44px"></span></div>
  <div class="sheet-body" id="sheetBody"></div>
</dialog>
<div class="toast" id="toast"></div>

<script>
const token = new URLSearchParams(location.search).get("t") || sessionStorage.getItem("t") || "";
try { sessionStorage.setItem("t", token); } catch (e) {}
history.replaceState(null, "", "/");
let state = null, filter = "ready", months = 1, count = 5;
const $ = id => document.getElementById(id);
const esc = s => String(s ?? "").replace(/[&<>"']/g, c => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const plural = (n, w) => `${n} ${w}${n === 1 ? "" : "s"}`;
const money = p => (p.currency === "USD" ? "$" : "") + (p.amount % 1 ? p.amount.toFixed(2) : p.amount) + (p.currency === "USD" ? "" : " " + p.currency);
const day = d => d ? new Date(d.slice(0, 10) + "T12:00").toLocaleDateString(undefined, {day: "numeric", month: "short"}) : "";

async function call(path, body, raw) {
  const r = await fetch(path, {method: body === undefined ? "GET" : "POST", headers: {"X-Admin-Token": token,
    "Content-Type": raw ? "text/csv" : "application/json"}, body: body === undefined ? undefined : raw ? body : JSON.stringify(body)});
  const text = await r.text();
  let data; try { data = JSON.parse(text); } catch (e) { throw new Error(text); }
  if (!r.ok) throw new Error(data.error || text);
  if (data.people) { state = data; render(); }
  return data;
}
function toast(t) { const el = $("toast"); el.textContent = t; el.classList.add("on"); clearTimeout(el.t); el.t = setTimeout(() => el.classList.remove("on"), 1800); }
async function copy(text, what) { await navigator.clipboard.writeText(text); toast(what + " copied"); }

function render() {
  $("warn").innerHTML = state.encrypted ? "" : `<div class="banner"><b>${esc(state.key)} isn't encrypted.</b> Anyone who gets a copy of it can make codes. Run <code>./scripts/beta-code.py protect</code> once and keep the passphrase in your password manager.</div>`;
  const need = state.people.filter(p => p.owed.length), have = state.people.filter(p => !p.owed.length);
  $("needHead").textContent = need.length ? `Needs a code · ${need.length}` : "Needs a code";
  $("need").innerHTML = need.length ? need.map(p => {
    const pays = p.payments.filter(x => !x.code);
    const ready = state.ready[p.months] || 0;
    return `<div class="row"><div class="main"><div class="title">${esc(p.name)}</div>
      <div class="detail">${pays.map(x => `${money(x)} ${esc(x.type.toLowerCase())}, ${day(x.date)}`).join(" · ")}${pays.some(x => x.message) ? "<br>“" + esc(pays.map(x => x.message).filter(Boolean).join(" ")) + "”" : ""}</div></div>
      <div class="actions"><span class="pill need">${plural(p.months, "month")}</span>
      <button class="tinted" data-give="${esc(p.key)}" data-months="${p.months}">${ready ? "Give a ready code" : "Mint a code"}</button></div></div>`;
  }).join("") : `<div class="empty">${state.people.length ? "Everyone who paid has a code." : "Import a Ko-fi CSV to see who needs a code."}</div>`;
  $("have").innerHTML = have.length ? have.map(p => {
    const c = p.codes[0];
    return `<div class="row"><div class="main"><div class="title">${esc(p.name)}</div>
      <div class="detail">${p.payments.map(x => `${money(x)}, ${day(x.date)}`).join(" · ")}</div></div>
      ${c ? `<span class="pill ${c.status === "sent" ? "done" : "muted"}">${c.status === "sent" ? "Sent " + day(c.sent) : "Not sent yet"}</span>`
          : `<span class="pill muted">No code with this tier</span>`}
      ${c ? `<button data-show="${c.serial}">Show</button>` : ""}</div>`;
  }).join("") : `<div class="empty">Nobody yet.</div>`;
  const pick = c => filter === "other" ? !["ready", "given", "sent"].includes(c.status) : c.status === filter;
  const list = state.codes.filter(pick);
  document.querySelectorAll("#filter button").forEach(b => {
    const n = state.codes.filter(c => b.dataset.f === "other" ? !["ready", "given", "sent"].includes(c.status) : c.status === b.dataset.f).length;
    b.classList.toggle("on", b.dataset.f === filter); b.textContent = {ready: "Ready", given: "Given", sent: "Sent", other: "Other"}[b.dataset.f] + (n ? ` ${n}` : "");
  });
  $("codes").innerHTML = list.length ? list.slice(0, 200).map(c => `<div class="row"><div class="main">
      <div class="title">${c.name ? esc(c.name) : `<span class="mono">…${esc(c.code.slice(-7))}</span>`}</div>
      <div class="detail">${c.months ? plural(c.months, "month") : c.expires ? "until " + c.expires : "no end"} · serial ${c.serial}${c.note ? " · " + esc(c.note) : ""}</div></div>
      ${c.status === "withdrawn" ? `<span class="pill bad">Withdrawn</span>` : c.status === "pool" ? `<span class="pill muted">Ko-fi pool</span>` : c.status === "spare" ? `<span class="pill muted">Spare</span>` : ""}
      <button data-show="${c.serial}">Show</button></div>`).join("") : `<div class="empty">None.</div>`;
  $("mMonths").textContent = plural(months, "month"); $("mCount").textContent = count;
  const pools = Object.entries(state.pools).map(([k, v]) => `${k}: ${v}`).join(", ");
  $("poolDetail").textContent = pools ? `Written so far: ${pools}` : "Written to tools/kofi-worker as SQL";
}

function show(c) {
  $("sheetTitle").textContent = c.name || "Code";
  const status = {ready: "Ready to hand out", given: "Given, not sent yet", sent: `Sent ${day(c.sent)}${c.how ? " by " + c.how : ""}`, withdrawn: "Withdrawn", pool: "In a Ko-fi pool", spare: "Spare, not meant to be used"}[c.status] || c.status;
  $("sheetBody").innerHTML = `
    <div class="header">${esc(status)}</div>
    <div class="group">
      <div class="row"><div class="main"><div class="mono">${esc(c.code)}</div></div><button data-copy="code">Copy</button></div>
      <div class="row"><div class="main"><div class="detail">Redeem link</div><div class="mono">${esc(c.link)}</div></div><button data-copy="link">Copy</button></div>
    </div>
    <div class="footer">${c.months ? plural(c.months, "month") + " from the day it's redeemed" : c.expires ? "Works until " + c.expires : "Never runs out"} · ${esc(c.scopes.join(", "))} · serial ${c.serial}</div>
    ${c.name ? `<div class="header">Message (a draft: make it yours)</div>
    <div class="group"><pre class="message">${esc(c.message)}</pre><div class="row"><div class="main"></div><button data-copy="message">Copy message</button></div></div>` : ""}
    <div class="header">Change</div>
    <div class="group">
      ${c.status === "given" ? `<div class="row"><div class="main">Mark as sent</div><div class="actions">${["Ko-fi", "email", "DM"].map(h => `<button class="tinted" data-mark="sent" data-how="${h}">${h}</button>`).join("")}</div></div>` : ""}
      ${c.status === "sent" ? `<div class="row"><div class="main">Not sent after all</div><button data-mark="given">Undo</button></div>` : ""}
      ${["given", "spare"].includes(c.status) ? `<div class="row"><div class="main">Put back as ready</div><button data-mark="ready">Put back</button></div>` : ""}
      ${c.status !== "withdrawn" ? `<div class="row"><div class="main">Withdraw<div class="detail">For a code that got posted publicly</div></div><button data-mark="withdrawn" style="color:var(--red)">Withdraw</button></div>`
        : `<div class="row"><div class="main">Add this to <code>BetaKeys.WITHDRAWN</code> in Supporter.kt for the next release:<div class="mono" style="margin-top:6px">${c.serial}L</div></div><button data-copy="serial">Copy</button></div>`}
    </div>`;
  $("sheetBody").onclick = async e => {
    const b = e.target.closest("button"); if (!b) return;
    if (b.dataset.copy) return copy(b.dataset.copy === "serial" ? c.serial + "L" : c[b.dataset.copy], {code: "Code", link: "Link", message: "Message", serial: "Serial"}[b.dataset.copy]);
    if (b.dataset.mark) {
      if (b.dataset.mark === "withdrawn" && !confirm("Withdraw this code? Folio refuses it once its serial is in a release.")) return;
      try { const r = await call("/api/mark", {serial: c.serial, status: b.dataset.mark, how: b.dataset.how}); show(r.shown); } catch (err) { toast(err.message); }
    }
  };
  if (!$("sheet").open) $("sheet").showModal();
}

document.addEventListener("click", async e => {
  const b = e.target.closest("button"); if (!b || b.closest("#sheet")) return;
  try {
    if (b.dataset.give) { b.disabled = true; const r = await call("/api/give", {person: b.dataset.give, months: +b.dataset.months}); show(r.shown); }
    else if (b.dataset.show) { const r = await call("/api/show", {serial: +b.dataset.show}); show(r.shown); }
    else if (b.dataset.f) { filter = b.dataset.f; render(); }
    else if (b.dataset.step) { months = Math.min(15, Math.max(1, months + +b.dataset.step)); render(); }
    else if (b.dataset.count) { count = Math.min(50, Math.max(1, count + +b.dataset.count)); render(); }
    else if (b.id === "makeReady") { b.disabled = true; await call("/api/ready", {months, count}); toast(`${plural(count, "code")} ready`); b.disabled = false; }
    else if (b.id === "makePool") { b.disabled = true; const r = await call("/api/pool", {months, count}); toast("Written to " + r.written); b.disabled = false; }
  } catch (err) { b.disabled = false; toast(err.message); }
});
$("close").onclick = () => $("sheet").close();
async function importFile(f) {
  try { const r = await call("/api/import", await f.text(), true); toast(r.added ? `${plural(r.added, "new payment")}` : "No new payments in that file"); }
  catch (err) { toast(err.message); }
}
$("file").onchange = e => e.target.files[0] && importFile(e.target.files[0]);
const drop = $("drop");
drop.ondragover = e => { e.preventDefault(); drop.classList.add("over"); };
drop.ondragleave = () => drop.classList.remove("over");
drop.ondrop = e => { e.preventDefault(); drop.classList.remove("over"); e.dataTransfer.files[0] && importFile(e.dataTransfer.files[0]); };
call("/api/state").then(s => { state = s; render(); }).catch(err => { document.querySelector("main").innerHTML = `<h1>Supporter Codes</h1><p class="sub">${esc(err.message)}</p>`; });
</script>
</body>
</html>
"""


def main():
    root = os.path.dirname(HERE)
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=root, help="the checkout holding the key and trackers (default: this one)")
    parser.add_argument("--key", help="default: supporter-key.pem in --root")
    parser.add_argument("--ledger", help="default: supporter-ledger.json in --root")
    parser.add_argument("--port", type=int, default=8770)
    parser.add_argument("--no-open", action="store_true", help="print the address instead of opening it")
    args = parser.parse_args()
    key = args.key or os.path.join(args.root, "supporter-key.pem")
    admin = Admin(args.root, key, args.ledger or os.path.join(args.root, "supporter-ledger.json"))
    admin.unlock()
    adopted = admin.ledger.adopt_trackers(args.root)
    if adopted:
        admin.ledger.save()
        print(f"Took in {adopted} codes from the supporter-codes trackers.")
    token = secrets.token_urlsafe(24)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", args.port), handler(admin, token, args.port))
    url = f"http://127.0.0.1:{args.port}/?t={token}"
    print(f"Supporter code admin on this Mac only: {url}\nCtrl-C to stop. The address changes every launch.")
    if not args.no_open:
        webbrowser.open(url)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
