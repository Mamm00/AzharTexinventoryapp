# Security / credential handling skill
- Passwords: PBKDF2WithHmacSHA256, salt >= 16 bytes (per-account seed salt derived from SHA-256("alazhartex.seed:account:<id>")), iterations >= 100,000 (seed uses 210,000), constant-time compare, stored as iterations$salt_b64$hash_b64.
- Sessions: (session_id, account_id, issued_at_wall, issued_at_elapsed, boot_id, expires_at_wall, is_active) in Room; ELAPSED_REALTIME_WAKEUP window + persisted tuple; boot_id change => wall clock authoritative; wall backwards > 5 min => refuse SESSION_CLOCK_ANOMALY + BLOCKED audit.
- No secrets in repo: *.jks/*.keystore/local.properties git-ignored; CI signing via GitHub Secrets; export files via FileProvider scoped to <filesDir>/exports only, MIME application/vnd.sqlite3.
- Import ladder R1..R5 (§9.8) before any preview; explicit overwrite confirmation; pre-import safety copy under filesDir/backups/safety-<ts>.db, keep last 3.
- CSV export: prefix ' on cells starting with =, +, -, @, tab, CR, LF, space, U+FF1D, U+FF0B, U+FF0D, U+FF20; BOM U+FEFF exactly once (§9.13).
- Every blocked RBAC path: typed RbacDeniedException + exactly one AuditLog BLOCKED row with reason + toast carrying the exact reason.
