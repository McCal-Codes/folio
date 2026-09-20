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
  emailed    INTEGER NOT NULL DEFAULT 0
);

-- Payments that earned a code when the pool was empty: mint more, then send these by hand.
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
