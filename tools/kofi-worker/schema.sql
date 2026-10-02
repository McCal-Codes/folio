-- Codes minted on McCal's Mac and loaded here; the signing key never leaves that machine.
CREATE TABLE IF NOT EXISTS codes (
  code    TEXT PRIMARY KEY,
  pool    TEXT NOT NULL,          -- which rule hands this out: "beta", "all", whatever POOLS names
  used_at TEXT                    -- null until a payment claims it
);
CREATE INDEX IF NOT EXISTS codes_free ON codes (pool, used_at);

-- One row per Ko-fi payment, so a retried webhook never hands out a second code.
CREATE TABLE IF NOT EXISTS handled (
  message_id TEXT PRIMARY KEY,
  code       TEXT NOT NULL,
  pool       TEXT NOT NULL,
  at         TEXT NOT NULL,
  emailed    INTEGER NOT NULL DEFAULT 0,
  -- What the Mac's ledger matches on. No email address: it's used to send the code, then dropped.
  transaction_id TEXT NOT NULL DEFAULT '',   -- Ko-fi's kofi_transaction_id, the TransactionId in its CSV
  from_name  TEXT NOT NULL DEFAULT '',
  type       TEXT NOT NULL DEFAULT '',       -- Tip, Donation, Subscription, Shop Order, or "By hand"
  amount     TEXT NOT NULL DEFAULT '',
  currency   TEXT NOT NULL DEFAULT '',
  by_hand    INTEGER NOT NULL DEFAULT 0      -- 1 when handed out from Folio Dev or the Mac, not by a payment
);

-- Payments that needed a hand: the pool was empty, or the money arrived in a currency no rate could
-- price (`unpriced EUR`). Mint more, or price it and send the code by hand.
CREATE TABLE IF NOT EXISTS problems (
  message_id TEXT PRIMARY KEY,
  pool       TEXT NOT NULL,
  at         TEXT NOT NULL
);

-- The day a months-code was first used for a beta download, so its window runs from the same day here as on the phone.
CREATE TABLE IF NOT EXISTS beta_seen (
  serial     INTEGER PRIMARY KEY,
  first_seen TEXT NOT NULL          -- YYYY-MM-DD, UTC
);

-- The last time something proved the path works, for the health check: 'kofi-test' is Ko-fi's own Send test button.
CREATE TABLE IF NOT EXISTS checks (
  name  TEXT PRIMARY KEY,
  value TEXT NOT NULL,              -- JSON
  at    TEXT NOT NULL
);

-- Supporter roles handed out in Discord by Mr Folio's /redeem. One code, one person: the serial is the key, so a code
-- redeemed by someone else is refused rather than granted twice. The daily cron takes the role back after ends_on.
CREATE TABLE IF NOT EXISTS discord_roles (
  serial     INTEGER PRIMARY KEY,
  user_id    TEXT NOT NULL,
  guild_id   TEXT NOT NULL,
  role_id    TEXT NOT NULL,
  granted_at TEXT NOT NULL,        -- YYYY-MM-DD, UTC
  ends_on    TEXT,                 -- the code's last day, YYYY-MM-DD; null for a code with no end
  removed_at TEXT                  -- set when the cron takes the role back
);
CREATE INDEX IF NOT EXISTS discord_roles_due ON discord_roles (removed_at, ends_on);
CREATE INDEX IF NOT EXISTS discord_roles_person ON discord_roles (user_id, role_id);
