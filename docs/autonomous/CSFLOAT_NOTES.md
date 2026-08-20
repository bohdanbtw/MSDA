# CSFLOAT_NOTES — Integration constraints for MSDA

Source skim: https://github.com/bohdanbtw/botCsFloat (desktop bot). MSDA must adapt, not copy polling habits.

## Hard rules (Boss / PROTOCOL)

- **Opt-in per Steam account** — default OFF. No global silent enable.
- **No aggressive Steam Guard confirmation polling** — confirm only when a real CSFloat sale/trade needs it (event-driven), never on a timer spam loop.
- Do **not** break existing MSDA Steam login, session revive, or manual confirmation UX (`MainActivity` load/accept flows stay as-is).
- Respect battery/network: prefer WorkManager with long flex intervals only after Phase 1 scaffold is stable; Phase 1 is **manual / foreground** only.

## botCsFloat → MSDA mapping (high level)

| botCsFloat idea | MSDA Phase 1 stance |
|-----------------|---------------------|
| API key auth to CSFloat | Store encrypted / EncryptedSharedPreferences per account; never log key |
| Poll listings / sales | Scaffold client + models only; no background poll yet |
| Auto-confirm Steam trades | Reuse existing Guard confirm APIs **on demand** when a sale requires it; no new timer |
| Continuous loop | Forbidden on Android for Steam confirmations |

## Suggested phased slices

1. **Scaffold (NOW / T010)** — package + API client stub + settings shape + version bump; zero Steam behavior change.
2. **Settings UI** — per-account enable + API key field (later task).
3. **Foreground fetch** — pull pending sales when user opens CSFloat screen.
4. **On-demand Guard** — if a sale needs confirmation, call existing confirm path once.
5. **Optional WorkManager** — only with documented battery budget (T004); never sub-minute Steam polls.

## Out of scope for scaffold

- Changing confirmation refresh intervals
- Push/FCM for CSFloat
- Touching `NativeAuthBridge` session revive unless a bugfix is separately assigned
