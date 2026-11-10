# Compose testing skill
- testTag("screen.stock"), testTag("gauge.wh-625") etc. — stable, locale-independent tags; assert via onNodeWithTag + semantics properties (text, contentDescription, stateDescription).
- Drive every screen x account x {ar,en} in androidTest (D10 leg 1). Keep tests hermetic: seeded in-memory DB per test via the test rule, or the instrumentation copies a fixture DB built by the generator into the app filesDir and launches with ACTION_DEBUG_SEED.
- Screenshot parity (Paparazzi) for gauge colour buckets and the wh-625 RED state at 175.2%.
