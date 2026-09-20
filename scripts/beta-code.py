#!/usr/bin/env python3
"""Make Folio supporter codes.

The private key stays on this machine; Folio only ever carries the public half.

    ./scripts/beta-code.py newkey                      # writes supporter-key.pem, prints the public key
    ./scripts/beta-code.py mint --scopes beta,look     # one code, no expiry
    ./scripts/beta-code.py mint --scopes beta --expires 2027-01-01 --count 25
    ./scripts/beta-code.py mint --scopes beta,keys --months 1         # one month from the day it's redeemed
    ./scripts/beta-code.py newkey --key ~/.folio/folio-dev-key.pem    # a development key, for your own phone
    ./scripts/beta-code.py mint --key ~/.folio/folio-dev-key.pem --scopes dev,beta,look,power,keys
    ./scripts/beta-code.py pool --scopes beta --count 200 > pool.sql   # load into the Ko-fi worker

Paste the printed public key into BetaKeys.SUPPORTER (app/src/main/java/com/mccal/folio/Supporter.kt).
Codes are checked on the phone against that key, so nothing here needs a server.
"""
import argparse
import base64
import datetime
import os
import secrets
import subprocess
import sys

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"  # Crockford base32, matching BetaCodes.kt
SCOPES = ["beta", "look", "power", "keys", "dev"]  # bit order must match BetaCodes.SCOPE_BITS
EPOCH = datetime.date(2026, 1, 1)
VERSION = 1           # a fixed last day, decided here
VERSION_MONTHS = 2    # months counted from the day the code is redeemed (months and tier share one byte)


def run(args, stdin=None):
    done = subprocess.run(args, input=stdin, capture_output=True)
    if done.returncode != 0:
        sys.exit(done.stderr.decode().strip() or f"{args[0]} failed")
    return done.stdout


def newkey(path):
    if os.path.exists(path):
        sys.exit(f"{path} already exists — move it aside first, codes signed with it would stop working.")
    run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", path])
    os.chmod(path, 0o600)
    public = run(["openssl", "ec", "-in", path, "-pubout", "-outform", "DER"])
    print(f"Private key: {path}  (keep it, never commit it)")
    print("\nBetaKeys.SUPPORTER:\n")
    print(f'    const val SUPPORTER = "{base64.b64encode(public).decode()}"')


def raw_signature(der):
    """OpenSSL signs to ASN.1; the code carries the plain r‖s pair."""
    assert der[0] == 0x30
    body = der[2:] if der[1] < 0x80 else der[3:]

    def take(rest):
        assert rest[0] == 0x02
        size = rest[1]
        value = rest[2:2 + size].lstrip(b"\x00")
        return value.rjust(32, b"\x00"), rest[2 + size:]

    r, rest = take(body)
    s, _ = take(rest)
    return r + s


def base32(data):
    bits = 0
    buffer = 0
    out = []
    for byte in data:
        buffer = buffer << 8 | byte
        bits += 8
        while bits >= 5:
            bits -= 5
            out.append(ALPHABET[buffer >> bits & 31])
    if bits:
        out.append(ALPHABET[buffer << (5 - bits) & 31])
    text = "".join(out)
    return "-".join(text[i:i + 5] for i in range(0, len(text), 5))


def mint(key, scopes, tier, expires, count, sql_pool=None, months=0):
    bits = 0
    for scope in scopes:
        if scope not in SCOPES:
            sys.exit(f"unknown scope {scope}; pick from {', '.join(SCOPES)}")
        bits |= 1 << SCOPES.index(scope)
    if expires:
        day = (datetime.date.fromisoformat(expires) - EPOCH).days
        if not 1 <= day <= 0xFFFF:
            sys.exit("expiry must be after 2026-01-01 and within about 180 years of it")
    else:
        day = 0
    # Months and tier share the third byte, so a months code is the same length as any other: months in the high
    # nibble, tier in the low one. Both are 0-15 in that shape; without --months the byte is the tier alone.
    if months:
        if not 1 <= months <= 15:
            sys.exit("--months takes 1 to 15; for longer, use --expires")
        if not 0 <= tier <= 15:
            sys.exit("--tier must be 0-15 when --months is used, since they share a byte")
        version, tier_byte = VERSION_MONTHS, months << 4 | tier
    else:
        if not 0 <= tier <= 255:
            sys.exit("--tier must be 0-255")
        version, tier_byte = VERSION, tier
    for _ in range(count):
        serial = secrets.randbits(32)
        payload = bytes([version, bits, tier_byte, day >> 8 & 0xFF, day & 0xFF]) + serial.to_bytes(4, "big")
        der = run(["openssl", "dgst", "-sha256", "-sign", key], stdin=payload)
        code = base32(payload + raw_signature(der))
        if sql_pool:
            print(f"INSERT OR IGNORE INTO codes (code, pool) VALUES ('{code}', '{sql_pool}');")
        else:
            print(code)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    new = sub.add_parser("newkey", help="make the signing key pair")
    new.add_argument("--key", default="supporter-key.pem")
    make = sub.add_parser("mint", help="make codes")
    make.add_argument("--key", default="supporter-key.pem")
    make.add_argument("--scopes", default="beta", help=f"comma separated: {', '.join(SCOPES)}")
    make.add_argument("--tier", type=int, default=1, help="0-255, your own meaning (1 = coffee, 2 = more)")
    make.add_argument("--expires", help="YYYY-MM-DD; leave out for a code that never expires")
    make.add_argument("--months", type=int, default=0,
                      help="1-15 months counted from the day it's redeemed, instead of a fixed date")
    make.add_argument("--count", type=int, default=1)
    pool = sub.add_parser("pool", help="codes as SQL, to load into the Ko-fi worker")
    pool.add_argument("--key", default="supporter-key.pem")
    pool.add_argument("--scopes", default="beta", help=f"comma separated: {', '.join(SCOPES)}")
    pool.add_argument("--tier", type=int, default=1)
    pool.add_argument("--expires", help="YYYY-MM-DD; leave out for a code that never expires")
    pool.add_argument("--months", type=int, default=0,
                      help="1-15 months counted from the day it's redeemed, instead of a fixed date")
    pool.add_argument("--count", type=int, default=100)
    pool.add_argument("--pool", help="pool name in the worker (default: the scopes joined by +)")
    args = parser.parse_args()
    if args.command == "newkey":
        newkey(args.key)
        return
    scopes = [s.strip() for s in args.scopes.split(",") if s.strip()]
    pool_name = (args.pool or "+".join(scopes)) if args.command == "pool" else None
    mint(args.key, scopes, args.tier, args.expires, args.count, pool_name, args.months)


if __name__ == "__main__":
    main()
