package com.alazhartex.stock.data.repo

/** Carrier for audited transactional context (spec §4.7, RES-014). Repository is the only creator. */
data class WriteContext(
    val actorId: String,
    val idempotencyKey: String,
    val action: String,
    val wallMillis: Long,
)
