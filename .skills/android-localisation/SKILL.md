# Localisation / string parity skill
- values/strings.xml (en) and values-ar/strings.xml (ar) carry IDENTICAL key sets; one key per line; same placeholder arity; apostrophes escaped (\') in EN.
- G6 parity gate: missing key in either file fails the build (tools/check_i18n_parity.py).
- RTL-first: layouts must not hardcode left/right (use start/end); direction-aware icons; wh gauges and counts mirror naturally.
- All number rendering: NumberFormat.getInstance(activeLocale); Western digits default; Arabic-Indic only behind Settings.arabicIndicDigits (default OFF, persisted in AppSettings).
- Dates yyyy-mm-dd only. "Today" = ZoneId.of("Africa/Cairo"). UTC epoch millis in DB.
