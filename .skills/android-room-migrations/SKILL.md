# Room schema migration skill
- Every entity change => bumps AppDatabase version AND adds an explicit Migration object; schema JSON exported via room.schemaLocation to data/schemas/ and diffed in CI (G5). fallbackToDestructiveMigration is FORBIDDEN.
- FKs: ON DELETE RESTRICT everywhere. Unique(warehouse_id, fabric_id, colour_id) on mappings. Unique(movements.idempotency_key). Unique(movements.transfer_pair_id, movements.type). Index on rolls(mapping_id, state). CHECK(qty_delta <> 0). CHECK(grade IN ('A','B','C','D')).
- Opening is DERIVED (closing - SUM(deltas)); opening_cache written ONLY inside the same transaction as closing update; @Ignore in domain model.
- Migration test: MigrationTestHelper with exported schema JSONs; never hand-write expected state from memory — replay from schema history.
- Exports of user data go through VACUUM INTO only (§9.9); never raw file copies of the live DB.
