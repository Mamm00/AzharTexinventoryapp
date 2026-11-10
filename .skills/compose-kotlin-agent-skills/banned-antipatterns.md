# Banned antipatterns — quick reference (enforced by tools/scan_antipatterns.sh and CI)
_state.value = | collectAsState() (without WithLifecycle) | GlobalScope | mutableStateListOf (in ViewModel) | LazyColumn missing key = | TODO|FIXME|XXX | println( | printStackTrace | runBlocking (non-test) | AllowMainThreadQueries | fallbackToDestructiveMigration | collectAsState(initial =
empty catch |!!| barcode|serial|width (case-insensitive) in schema/UI/CSV | hardcoded string literals in @Composable
