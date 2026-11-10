# compose-kotlin-agent-skills — AlAzharTex vendored kit

Source: vendored from the master spec (Sections 0, 8, 9) + Android official Compose guidance. The online `android skills add` path was unavailable in this sandbox (hosts unreachable); this file is the pinned offline equivalent and is binding for this repo.

## Architecture
- Unidirectional data flow. Screen = stateless @Composable(state, onEvent); ViewModel holds MutableStateFlow<ScreenState>; UI collects with collectAsStateWithLifecycle().
- State updates via _state.update { it.copy(...) } — NEVER `_state.value = ...`.
- Effects via Channel + LaunchedEffect(Unit) collection of one-shot events (navigation, toasts with REAL interpolated values).
- No LiveData. No GlobalScope. No mutableStateListOf in ViewModels. No var mutableState read across composition boundaries without rememberSaveable where restoration matters.
- LazyColumn/LazyVerticalGrid ALWAYS carry key = { it.id } and contentType = { "rowType" }.
- Strings: stringResource(R.string.x) only. contentDescription on every icon (or decorative = true with semantics {} ). 48dp minimum touch targets. No colour-only meaning (pair with icon/text).

## Banned antipatterns (G8; reject on sight)
1. _state.value = x → use _state.update { }
2. collectAsState() → collectAsStateWithLifecycle()
3. LazyColumn item without key
4. GlobalScope.launch / CoroutineScope() created ad-hoc in VM (use viewModelScope)
5. mutableStateListOf/mutableStateOf in ViewModel (use StateFlow/UiState)
6. Hardcoded strings in @Composable bodies
7. !! without a preceding guard; prefer requireNotNull with message or safe calls
8. Recomposition-triggering reads of expensive objects in bodies (remember them)
9. LaunchedEffect(Unit) for one-shot work without a key on the triggering input
10. Icons/clickables without contentDescription/role/stateDescription
11. remember { } capturing context-dependent values across configuration changes
12. Passing ViewModel into stateless composables instead of state+callbacks

## Compose testing
- Use testTag on every interactive node; semantics tree assertions over screenshot-only checks.
- Compose UI tests run in connectedAndroidTest (CI emulator matrix) and as the §9.11 D10 fallback leg #1.
- Paparazzi (JVM) is CI fallback leg #2; trace.json parity is leg #3.
