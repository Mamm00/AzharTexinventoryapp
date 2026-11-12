package com.alazhartex.seed

import com.alazhartex.stock.domain.palette.PaletteResolver
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

/**
 * AlAzharTex seed generator (O1, spec §9.1). Deterministic by construction:
 * every value derives from SHA-256 / SHA-1 / SHA-256-HMAC (PBKDF2) of pinned strings.
 * No Locale, TimeZone, currentTimeMillis, Instant.now or randomness anywhere in seed fields.
 * Canonical JSON: sorted keys, UTF-8, LF, compact separators, trailing newline.
 */
object SeedGenerator {
    const val NAMESPACE = "alazhartex.seed"
    const val SEED_VERSION = "1.0.0"
    const val GENERATOR_ID = "alazhartex-generator/$SEED_VERSION"
    const val PBKDF2_ITERATIONS = 210_000
    const val MOVEMENT_BASE_INSTANT = "2026-01-05T09:17:00Z"
    const val MOVEMENT_STEP_SECONDS = 41 * 60L
    private val UUID_DNS = parseUuid("6ba7b810-9dad-11d1-80b4-00c04fd430c8")

    data class Warehouse(val id: String, val nameAr: String, val nameEn: String, val capacity: Long, val keeper: String)
    data class Account(val id: String, val username: String, val password: String, val role: String, val displayName: String)

    val warehouses = listOf(
        Warehouse("wh-alwa", "مخزن الواحة", "Al-Waha Warehouse", 16000, "keeper1"),
        Warehouse("wh-leon", "مخزن ليون", "Leon Warehouse", 14000, "keeper1"),
        Warehouse("wh-azouz", "مخزن عزوز", "Azouz Warehouse", 12000, "keeper1"),
        Warehouse("wh-zibda", "مخزن زبدة", "Zibda Warehouse", 13500, "keeper1"),
        Warehouse("wh-showroom", "المعرض الرئيسي", "Main Showroom", 22000, "keeper1"),
        Warehouse("wh-625", "عنبر 625", "Shed 625", 3000, "keeper2"),
        Warehouse("wh-magdy", "مخزن مجدي", "Magdy Warehouse", 4500, "keeper2"),
        Warehouse("wh-wahman", "مخزن وهمن", "Wahman Warehouse", 5200, "keeper2"),
        Warehouse("wh-lycra", "مخزن لايكرا", "Lycra Warehouse", 7000, "keeper2"),
        Warehouse("wh-kashry", "مخزن كشري", "Kashry Warehouse", 4100, "keeper2"),
    )

    val accounts = listOf(
        Account("admin", "admin", "admin123", "ADMIN", "الأزهر — المدير"),
        Account("keeper1", "keeper1", "mostafa123", "KEEPER", "مصطفى — أمين العنبر"),
        Account("keeper2", "keeper2", "keeper2123", "KEEPER", "أمناء العنبر ٢"),
    )

    val activeBudgetByShed = mapOf(
        "wh-alwa" to 11540L, "wh-leon" to 10208L, "wh-azouz" to 9672L, "wh-zibda" to 10341L, "wh-showroom" to 9572L,
        "wh-625" to 5255L, "wh-magdy" to 3018L, "wh-wahman" to 3144L, "wh-lycra" to 2851L, "wh-kashry" to 2966L,
    )
    val non625InactiveCount = mapOf(
        "wh-alwa" to 3, "wh-azouz" to 3, "wh-kashry" to 3, "wh-leon" to 3, "wh-magdy" to 3,
        "wh-wahman" to 2, "wh-lycra" to 2, "wh-showroom" to 2, "wh-zibda" to 1,
    )
    val wh625InactiveColours = setOf(45, 90, 135, 180) // C045, C090, C135, C180 (1-based)

    val families = listOf(
        "cotton", "linen", "wool", "silk", "polyester", "viscose", "denim", "velvet", "lycra", "oxford", "poplin",
    )
    val fabricFamilySizes = mapOf(
        "cotton" to 7, "linen" to 6, "wool" to 6, "silk" to 5, "polyester" to 6, "viscose" to 5,
        "denim" to 6, "velvet" to 5, "lycra" to 6, "oxford" to 4, "poplin" to 5,
    )
    val colourFamilySizes = mapOf(
        "cotton" to 30, "linen" to 22, "wool" to 18, "silk" to 22, "polyester" to 24, "viscose" to 14,
        "denim" to 20, "velvet" to 12, "lycra" to 14, "oxford" to 8, "poplin" to 24,
    )
    val keeperCodeByFamily = mapOf(
        "cotton" to "KH-101", "linen" to "KH-102", "wool" to "KH-103", "silk" to "KH-104",
        "denim" to "KH-201", "velvet" to "KH-202", "polyester" to "KH-203",
    )

    val fabricsByFamily: Map<String, List<Pair<String, String>>> = mapOf(
        "cotton" to listOf(
            "Giza 90s Egyptian Cotton" to "قطن جيزة 90",
            "Combed Cotton Twill" to "قطن ممشط قطكي",
            "Brushed Flannel Cotton" to "قطن فلانيل مبطّن",
            "Organic Percale" to "بيركال عضوي",
            "Egyptian Lawn" to "لتن قطن جيزة",
            "Cotton Sateen" to "ساتان قطن",
            "Heavy Canvas Cotton" to "كانفاس قطن ثقيل",
        ),
        "linen" to listOf(
            "Irish Linen" to "كتان إيرلندي",
            "Stonewashed Linen" to "كتان مغسول",
            "Linen Herringbone" to "كتان متعرج",
            "Belgian Flax" to "كتان بلجيكي",
            "Linen Toweling" to "كتان مناشف",
            "Ramie Blend Linen" to "كتان ممزوج بالرامي",
        ),
        "wool" to listOf(
            "Merino Fine" to "صوف ميرينو ناعم",
            "Gabardine Wool" to "صوف جابردين",
            "Harris Tweed" to "تويد هاريس",
            "Camel Hair Wool" to "صوف وبر الجمل",
            "Shetland Wool" to "صوف شيتلاند",
            "Cashmere Blend" to "صوف كشمير ممزوج",
        ),
        "silk" to listOf(
            "Mulberry Silk Charmeuse" to "حرير توت شارموز",
            "Dupioni Silk" to "حرير دوبيوني",
            "Silk Organza" to "حرير أورجانزا",
            "Tussar Silk" to "حرير توسار",
            "Habotai Silk" to "حرير هابوتي",
        ),
        "polyester" to listOf(
            "Microfiber Polyester" to "بوليستر ميكروفايبر",
            "Polyester Chiffon" to "بوليستر شيفون",
            "Poly Embossed Crepe" to "بوليستر كريب مجسم",
            "Polyester Taffeta" to "بوليستر تافته",
            "Recycled PET Geo" to "بوليستر معاد التدوير",
            "DTY Polyester" to "بوليستر DTY",
        ),
        "viscose" to listOf(
            "Viscose Twill" to "فسكوز قطكي",
            "Rayon Crepe" to "كريب رايون",
            "Viscose Satin" to "ساتان فسكوز",
            "Modal Jersey Viscose" to "فسكوز مودل جرسيه",
            "Bamboo Viscose" to "فسكوز خيزران",
        ),
        "denim" to listOf(
            "14oz Indigo Denim" to "دينيم إنديجو 14 أونصة",
            "Stretch Comfort Denim" to "دينيم ستريتش مريح",
            "Raw Selvedge Denim" to "دينيم سيلفيدج خام",
            "Stonewash Denim" to "دينيم مغسول بالحجر",
            "Black Denim 12oz" to "دينيم أسود 12 أونصة",
            "Ecru Selvedge" to "دينيم إكرو سيلفيدج",
        ),
        "velvet" to listOf(
            "Cotton Velvet" to "مخمل قطن",
            "Silk Velvet" to "مخمل حرير",
            "Stretch Velvet" to "مخمل ستريتش",
            "Devore Velvet" to "مخمل ديفوريه",
            "Embossed Velvet" to "مخمل مجسم",
        ),
        "lycra" to listOf(
            "Sport Lycra 230gsm" to "لايكرا رياضي 230",
            "Swim Lycra UV" to "لايكرا سباحة UV",
            "Compression Lycra" to "لايكرا ضغط",
            "Shiny Lycra" to "لايكرا لامعة",
            "Matte Lycra" to "لايكرا مطفية",
            "Mesh-Lycra Jacquard" to "لايكرا جاكار مخرم",
        ),
        "oxford" to listOf(
            "Classic Oxford 120gsm" to "أكسفورد كلاسيك 120",
            "Pinpoint Oxford" to "أكسفورد بينبوينت",
            "Oxford Chambray" to "أكسفورد شامبراي",
            "Royal Oxford" to "أكسفورد ملكي",
        ),
        "poplin" to listOf(
            "Broadcloth Poplin" to "بوبلين برودكلوث",
            "Stretch Poplin" to "بوبلين ستريتش",
            "Wrinkle-Free Poplin" to "بوبلين مقاوم للتجاعيد",
            "Poplin Stripe" to "بوبلين مخطط",
            "Poplin Solid 160" to "بوبلين صلب 160",
        ),
    )

    val hues = listOf(
        "Crimson" to "قرمزي", "Azure" to "لازوردي", "Emerald" to "زمردي", "Amber" to "كهرماني",
        "Ivory" to "عاجي", "Charcoal" to "فحمي", "Teal" to "فيروزي", "Burgundy" to "زركوني",
        "Olive" to "زيتوني", "Indigo" to "نيلي", "Sand" to "رملي", "Rose" to "وردي",
        "Gold" to "ذهبي", "Slate" to "أردوازي", "Terracotta" to "طيني", "Mint" to "نعناعي",
    )
    val shades = listOf(
        "Light" to "فاتح", "Pale" to "باهت", "Bright" to "ساطع", "Deep" to "داكن",
        "Dusky" to "غائم", "Soft" to "ناعم", "Rich" to "غني", "Muted" to "خافت",
        "Warm" to "دافئ", "Cool" to "بارد", "Antique" to "عتيق", "Vivid" to "حي", "Classic" to "كلاسيك",
    )

    // ---------- crypto helpers ----------
    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun sha256Hex(s: String): String = sha256(s.toByteArray(Charsets.UTF_8)).joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
    private fun sha1(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(bytes)
    private fun parseUuid(s: String): UUID = UUID.fromString(s)

    fun uuid5(name: String): String {
        val ns = UUID_DNS
        val fullNs = ByteArray(16)
        val msb = ns.mostSignificantBits
        val lsb = ns.leastSignificantBits
        for (i in 0 until 8) {
            fullNs[i] = (msb ushr (56 - 8 * i)).toByte()
            fullNs[8 + i] = (lsb ushr (56 - 8 * i)).toByte()
        }
        val digest = sha1(fullNs + name.toByteArray(Charsets.UTF_8))
        digest[6] = ((digest[6].toInt() and 0x0F) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = digest.joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }

    fun pbkdf2Hash(password: String, accountId: String): String {
        val salt = sha256("$NAMESPACE:account:$accountId".toByteArray(Charsets.UTF_8)).copyOfRange(0, 16)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val hash = factory.generateSecret(spec).encoded
        return "$PBKDF2_ITERATIONS\$${Base64.getEncoder().encodeToString(salt)}\$${Base64.getEncoder().encodeToString(hash)}"
    }

    private fun parseIsoZ(s: String): Instant = Instant.parse(s)

    // ---------- JSON (compact canonical) ----------
    sealed interface Json
    class JObj(keys: Map<String, Json>) : Json { val map = keys.toSortedMap() }
    class JArr(val items: List<Json>) : Json
    class JStr(val value: String) : Json
    class JNum(val value: Long) : Json
    class JBool(val value: Boolean) : Json
    object JNull : Json

    private fun escapeString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", c.code)) else sb.append(c)
            }
        }
        return sb.append("\"").toString()
    }

    fun writeCanonical(j: Json): String {
        val sb = StringBuilder()
        fun emit(node: Json) {
            when (node) {
                is JObj -> {
                    sb.append('{')
                    var first = true
                    for ((k, v) in node.map) {
                        if (!first) sb.append(',')
                        first = false
                        sb.append(escapeString(k)).append(':')
                        emit(v)
                    }
                    sb.append('}')
                }
                is JArr -> {
                    sb.append('[')
                    node.items.forEachIndexed { i, item ->
                        if (i > 0) sb.append(',')
                        emit(item)
                    }
                    sb.append(']')
                }
                is JStr -> sb.append(escapeString(node.value))
                is JNum -> sb.append(node.value)
                is JBool -> sb.append(if (node.value) "true" else "false")
                JNull -> sb.append("null")
            }
        }
        emit(j)
        return sb.toString()
    }
}

// ================= construction =================

private fun jstr(s: String) = SeedGenerator.JStr(s)
private fun jlong(v: Long) = SeedGenerator.JNum(v)
private fun jobj(vararg pairs: Pair<String, SeedGenerator.Json>): SeedGenerator.JObj =
    SeedGenerator.JObj(linkedMapOf(*pairs))
private fun jarr(items: List<SeedGenerator.Json>) = SeedGenerator.JArr(items)
private fun jbool(b: Boolean) = SeedGenerator.JBool(b)
private fun jnull() = SeedGenerator.JNull
private fun maybeStr(s: String?): SeedGenerator.Json = if (s == null) SeedGenerator.JNull else jstr(s)

data class GenColour(val code: String, val id: String, val family: String, val grade: String, val nameEn: String, val nameAr: String, val keeperCode: String?)
data class GenFabric(val index: Int, val id: String, val family: String, val nameEn: String, val nameAr: String)
data class GenMapping(val id: String, val naturalKey: String, val shedId: String, val fabricId: String, val colourId: String, val fabricCode: String, val colourCode: String, val active: Boolean)
data class GenMovement(val id: String, val code: String, val type: String, val mappingId: String, val shedId: String, val qtyDelta: Long, val transferPairId: String?, val constructOrder: Int)

class SeedBuild(val outDir: File) {
    private val sha = SeedGenerator::sha256Hex
    private val uuid5 = SeedGenerator::uuid5

    fun run(): Int {
        val colours = buildColours()
        val fabrics = buildFabrics()
        val mappings = buildMappings(colours, fabrics)
        val (rollActive, rollInactive) = buildRolls(mappings)
        val inventory = buildInventory(mappings, rollActive)
        val movements = buildMovements(mappings, rollActive)
        applyDeltasAndValidate(inventory, movements)
        val (jsonText, reportText) = emit(colours, fabrics, mappings, rollActive, rollInactive, inventory, movements)

        val paletteText = buildPaletteReport(colours)
        val jsonFile = File(outDir, "seed-alazhartex.json")
        val reportFile = File(outDir, "seed-report.txt")
        val paletteFile = File(outDir, "palette-report.txt")
        jsonFile.writeText(jsonText, Charsets.UTF_8)
        reportFile.writeText(reportText, Charsets.UTF_8)
        paletteFile.writeText(paletteText, Charsets.UTF_8)
        println("wrote ${jsonFile.absolutePath}")
        println("wrote ${reportFile.absolutePath}")
        println("wrote ${paletteFile.absolutePath}")
        return 0
    }

    /** v1.1 (delta §10.1): 208 swatch rows. Oracle for D20/D21; the app never reads this file at runtime. */
    private fun buildPaletteReport(colours: List<GenColour>): String = buildString {
        append("alazhartex-palette-report v1\n")
        append("generator=${SeedGenerator.GENERATOR_ID}\n")
        append("colours=${colours.size}\n")
        for (c in colours) {
            val sw = PaletteResolver.resolve(c.family, c.grade, c.code)
            val ratio = Math.round(sw.contrastRatio * 10000.0) / 10000.0
            append(c.id).append(',').append(sw.hex()).append(',').append(sw.onHex()).append(',')
                .append(String.format(java.util.Locale.ROOT, "%.4f", ratio)).append('\n')
        }
    }

    // ---- colours ----
    private fun buildColours(): List<GenColour> {
        val codes = (1..208).map { String.format(java.util.Locale.ROOT, "C%03d", it) }
        val gradePool = buildList {
            repeat(65) { add("A") }; repeat(70) { add("B") }; repeat(54) { add("C") }; repeat(19) { add("D") }
        }
        val familyPool = buildList { SeedGenerator.families.forEach { f -> repeat(SeedGenerator.colourFamilySizes.getValue(f)) { add(f) } } }
        require(familyPool.size == 208) { "family pool ${familyPool.size} != 208" }

        val gradeOf = HashMap<String, String>()
        codes.sortedBy { sha("${SeedGenerator.NAMESPACE}:grade:$it") }.forEachIndexed { i, code -> gradeOf[code] = gradePool[i] }
        val familyOf = HashMap<String, String>()
        codes.sortedBy { sha("${SeedGenerator.NAMESPACE}:family:$it") }.forEachIndexed { i, code -> familyOf[code] = familyPool[i] }

        val seen = HashSet<String>()
        val keeperOf = HashMap<String, String>()
        val firstOfFamily = HashMap<String, String>()
        for (code in codes) {
            val fam = familyOf.getValue(code)
            if (SeedGenerator.keeperCodeByFamily.containsKey(fam) && !firstOfFamily.containsKey(fam)) {
                firstOfFamily[fam] = code
            }
        }
        for ((fam, code) in firstOfFamily) {
            if (!seen.contains(fam)) {
                keeperOf[code] = SeedGenerator.keeperCodeByFamily.getValue(fam)
                seen.add(fam)
            }
        }
        check(keeperOf.size == 7) { "keeper code count ${keeperOf.size} != 7" }

        return codes.mapIndexed { i, code ->
            val hue = SeedGenerator.hues[i % 16]
            val shade = SeedGenerator.shades[i / 16]
            GenColour(
                code = code,
                id = uuid5("colour:$code"),
                family = familyOf.getValue(code),
                grade = gradeOf.getValue(code),
                nameEn = "${shade.first} ${hue.first}",
                nameAr = "${hue.second} ${shade.second}",
                keeperCode = keeperOf[code],
            )
        }
    }

    // ---- fabrics ----
    private fun buildFabrics(): List<GenFabric> {
        val out = ArrayList<GenFabric>(61)
        var index = 0
        for (family in SeedGenerator.families) {
            for ((en, ar) in SeedGenerator.fabricsByFamily.getValue(family)) {
                index += 1
                val code = String.format(java.util.Locale.ROOT, "FAB-%03d", index)
                out += GenFabric(index, uuid5("fabric:$code"), family, en, ar)
            }
        }
        check(out.size == 61) { "fabrics ${out.size} != 61" }
        return out
    }

    // ---- mappings ----
    private fun selectionKey(shed: String, naturalKey: String) = sha("${SeedGenerator.NAMESPACE}:select:$shed:$naturalKey")

    private fun buildMappings(colours: List<GenColour>, fabrics: List<GenFabric>): List<GenMapping> {
        val out = ArrayList<GenMapping>(1296)
        val colourByCode = colours.associateBy { it.code }
        val fabricByIndex = fabrics.associateBy { it.index }
        val wh625Inactive = SeedGenerator.wh625InactiveColours

        // wh-625: FAB-001 x C001..C180
        val f625 = fabricByIndex.getValue(1)
        for (i in 1..180) {
            val colour = colourByCode.getValue(String.format(java.util.Locale.ROOT, "C%03d", i))
            val nk = "wh-625|${f625.id}|${colour.id}"
            out += GenMapping(uuid5("mapping:$nk"), nk, "wh-625", f625.id, colour.id, "FAB-001", colour.code, i !in wh625Inactive)
        }

        // other sheds: 124 each over FAB-002..FAB-061 x 208 colours
        val otherSheds = SeedGenerator.warehouses.map { it.id }.filter { it != "wh-625" }
        for (shed in otherSheds) {
            val candidates = ArrayList<Pair<String, Pair<GenFabric, GenColour>>>(60 * 208)
            for (fi in 2..61) {
                val fabric = fabricByIndex.getValue(fi)
                for (colour in colours) {
                    val nk = "$shed|${fabric.id}|${colour.id}"
                    candidates += selectionKey(shed, nk) to (fabric to colour)
                }
            }
            candidates.sortBy { it.first }
            val chosen = candidates.take(124)
            val inactiveN = SeedGenerator.non625InactiveCount.getValue(shed)
            val inactiveKeys = chosen.take(inactiveN).map { it.first }.toHashSet()
            for ((key, pair) in chosen) {
                val (fabric, colour) = pair
                val nk = "$shed|${fabric.id}|${colour.id}"
                out += GenMapping(uuid5("mapping:$nk"), nk, shed, fabric.id, colour.id, String.format(java.util.Locale.ROOT, "FAB-%03d", fabric.index), colour.code, key !in inactiveKeys)
            }
        }
        check(out.size == 1296) { "mappings ${out.size} != 1296" }
        check(out.count { !it.active } == 26) { "inactive mappings ${out.count { !it.active }} != 26" }
        check(out.count { it.shedId == "wh-625" } == 180) { "wh-625 mapped ${out.count { it.shedId == "wh-625" }} != 180" }
        check(out.count { it.shedId == "wh-625" && it.active } == 176) { "wh-625 active mappings != 176" }
        return out
    }

    // ---- rolls ----
    private fun buildRolls(mappings: List<GenMapping>): Pair<Map<String, Long>, Map<String, Long>> {
        val activeById = HashMap<String, Long>(mappings.size)
        for (shed in SeedGenerator.warehouses.map { it.id }) {
            val actives = mappings.filter { it.shedId == shed && it.active }.sortedBy { it.id }
            val budget = SeedGenerator.activeBudgetByShed.getValue(shed)
            val n = actives.size.toLong()
            val base = budget / n
            val rem = (budget % n).toInt()
            actives.forEachIndexed { i, m -> activeById[m.id] = base + (if (i < rem) 1L else 0L) }
        }
        val inactiveById = HashMap<String, Long>(26)
        val inactives = mappings.filter { !it.active }.sortedBy { it.id }
        check(inactives.size == 26) { "inactive mapping rows ${inactives.size} != 26" }
        val baseI = 1220L / 26
        val remI = (1220L % 26).toInt()
        inactives.forEachIndexed { i, m -> inactiveById[m.id] = baseI + (if (i < remI) 1L else 0L) }
        return activeById to inactiveById
    }

    // ---- inventory ----
    data class GenInventory(val id: String, val mappingId: String, val closing: Long, var opening: Long)

    private fun buildInventory(mappings: List<GenMapping>, rollActive: Map<String, Long>): List<GenInventory> =
        mappings.map { m -> GenInventory(uuid5("inventory:${m.id}"), m.id, rollActive[m.id] ?: 0L, -1L) }

    // ---- movements ----
    data class GenMovementAssigned(val movement: GenMovement, val atIso: String)

    private fun buildMovements(mappings: List<GenMapping>, rollActive: Map<String, Long>): Pair<List<GenMovement>, List<GenMovementAssigned>> {
        val active = mappings.filter { it.active }
        val used = HashSet<String>(248)
        val out = ArrayList<GenMovement>(124)

        fun pick(code: String, predicate: (GenMapping) -> Boolean): GenMapping {
            val keyed = active.map { sha("${SeedGenerator.NAMESPACE}:movesel:$code:${it.id}") to it }.sortedBy { it.first }
            for ((_, m) in keyed) {
                if (!used.contains(m.id) && predicate(m)) return m
            }
            error("no eligible mapping for movement $code")
        }

        // IN — V0001..V0040, qty 1..12
        for (i in 1..40) {
            val code = String.format(java.util.Locale.ROOT, "V%04d", i)
            val qty = 1 + java.lang.Long.parseLong(sha("${SeedGenerator.NAMESPACE}:qty-in:$code").take(8), 16) % 12
            val m = pick(code) { (rollActive[it.id] ?: 0L) >= qty }
            used += m.id
            out += GenMovement(uuid5("movement:$code"), code, "IN", m.id, m.shedId, qty, null, out.size + 1)
        }
        // OUT — V0041..V0080, qty 1..10
        for (i in 41..80) {
            val code = String.format(java.util.Locale.ROOT, "V%04d", i)
            val qty = 1 + java.lang.Long.parseLong(sha("${SeedGenerator.NAMESPACE}:qty-out:$code").take(8), 16) % 10
            val m = pick(code) { true }
            used += m.id
            out += GenMovement(uuid5("movement:$code"), code, "OUT", m.id, m.shedId, -qty, null, out.size + 1)
        }
        // TRANSFER pairs — 12 pairs: V0081/82 .. V0103/104, qty 1..8
        val groups = HashMap<String, MutableList<GenMapping>>()
        for (m in active) {
            val key = "${m.fabricId}|${m.colourId}"
            groups.getOrPut(key) { ArrayList() }.add(m)
        }
        val sortedGroups = groups.entries.filter { it.value.size >= 2 }
            .sortedBy { sha("${SeedGenerator.NAMESPACE}:transfersel:${it.key}") }
        var placedPairs = 0
        for (pair in 1..12) {
            val tcode = String.format(java.util.Locale.ROOT, "T%04d", pair)
            val vout = String.format(java.util.Locale.ROOT, "V%04d", 80 + 2 * pair - 1)
            val vin = String.format(java.util.Locale.ROOT, "V%04d", 80 + 2 * pair)
            val qty = 1 + java.lang.Long.parseLong(sha("${SeedGenerator.NAMESPACE}:qty-xfer:$tcode").take(8), 16) % 8
            val pairId = uuid5("transfer:$tcode")
            var placed = false
            for ((groupKey, members) in sortedGroups) {
                val sortedMembers = members.sortedBy { sha("${SeedGenerator.NAMESPACE}:transfer:$tcode:${it.id}") }
                var done = false
                for (j in 0 until sortedMembers.size - 1) {
                    val src = sortedMembers[j]
                    val dst = sortedMembers[j + 1]
                    if (src.shedId == dst.shedId) continue
                    if (used.contains(src.id) || used.contains(dst.id)) continue
                    if ((rollActive[src.id] ?: 0L) < qty) continue
                    used += src.id
                    used += dst.id
                    out += GenMovement(uuid5("movement:$vout"), vout, "TRANSFER_OUT", src.id, src.shedId, -qty, pairId, out.size + 1)
                    out += GenMovement(uuid5("movement:$vin"), vin, "TRANSFER_IN", dst.id, dst.shedId, qty, pairId, out.size + 1)
                    placedPairs += 1
                    done = true
                    placed = true
                    break
                }
                if (done) break
            }
            check(placed) { "could not place transfer pair $tcode" }
        }
        check(placedPairs == 12) { "transfer pairs $placedPairs != 12" }
        // ADJUSTMENT — V0105..V0124, alternating sign starting positive, qty 1..6
        for (i in 0 until 20) {
            val code = String.format(java.util.Locale.ROOT, "V%04d", 105 + i)
            val sign = if (i % 2 == 0) 1L else -1L
            val qty = 1 + java.lang.Long.parseLong(sha("${SeedGenerator.NAMESPACE}:qty-adj:$code").take(8), 16) % 6
            val delta = sign * qty
            val m = pick(code) { if (delta > 0) (rollActive[it.id] ?: 0L) >= delta else true }
            used += m.id
            out += GenMovement(uuid5("movement:$code"), code, "ADJUSTMENT", m.id, m.shedId, delta, null, out.size + 1)
        }
        check(out.size == 124) { "movements ${out.size} != 124" }

        // deterministic UTC instants: sort by (shed_id, mapping_id, construct order)
        val base = java.time.Instant.parse(SeedGenerator.MOVEMENT_BASE_INSTANT)
        val ordered = out.sortedWith(compareBy({ it.shedId }, { it.mappingId }, { it.constructOrder }))
        val assigned = ordered.mapIndexed { idx, mv ->
            val instant = base.plusSeconds(idx * SeedGenerator.MOVEMENT_STEP_SECONDS)
            GenMovementAssigned(mv, DateTimeFormatter.ISO_INSTANT.format(instant))
        }
        return out to assigned
    }

    // ---- validation of the zero-drift invariant at seed time ----
    private fun applyDeltasAndValidate(inventory: List<GenInventory>, movements: Pair<List<GenMovement>, List<GenMovementAssigned>>) {
        val deltaByMapping = HashMap<String, Long>()
        for (mv in movements.first) {
            deltaByMapping[mv.mappingId] = (deltaByMapping[mv.mappingId] ?: 0L) + mv.qtyDelta
        }
        var checked = 0
        for (row in inventory) {
            val sum = deltaByMapping[row.mappingId] ?: 0L
            val opening = row.closing - sum
            require(opening >= 0L) { "row ${row.mappingId}: opening $opening < 0" }
            row.opening = opening
            checked += 1
        }
        check(checked == inventory.size) { "validated $checked != ${inventory.size}" }
        val transferDelta = movements.first.filter { it.type == "TRANSFER_OUT" || it.type == "TRANSFER_IN" }.sumOf { it.qtyDelta }
        check(transferDelta == 0L) { "transfer deltas net $transferDelta != 0" }
    }

    // ---- emission ----
    private fun emit(
        colours: List<GenColour>,
        fabrics: List<GenFabric>,
        mappings: List<GenMapping>,
        rollActive: Map<String, Long>,
        rollInactive: Map<String, Long>,
        inventory: List<GenInventory>,
        movements: Pair<List<GenMovement>, List<GenMovementAssigned>>,
    ): Pair<String, String> {
        val (movementsRaw, movementsAssigned) = movements
        val atIsoById = movementsAssigned.associate { it.movement.id to it.atIso }
        val shedOf = mappings.associate { it.id to it.shedId }

        val accountsJson = jarr(SeedGenerator.accounts.map { a ->
            jobj(
                "display_name" to jstr(a.displayName),
                "id" to jstr(a.id),
                "password_pbkdf2_sha256" to jstr(SeedGenerator.pbkdf2Hash(a.password, a.id)),
                "role" to jstr(a.role),
                "username" to jstr(a.username),
            )
        })
        val warehousesJson = jarr(SeedGenerator.warehouses.map { w ->
            jobj(
                "capacity_rolls" to jlong(w.capacity),
                "id" to jstr(w.id),
                "keeper_account_id" to jstr(w.keeper),
                "name_ar" to jstr(w.nameAr),
                "name_en" to jstr(w.nameEn),
            )
        })
        val assignmentsJson = jarr(
            SeedGenerator.warehouses.map { w -> w.keeper to w.id }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .map { (acc, wh) -> jobj("account_id" to jstr(acc), "warehouse_id" to jstr(wh)) },
        )
        val coloursJson = jarr(colours.map { c ->
            jobj(
                "code" to jstr(c.code),
                "family" to jstr(c.family),
                "grade" to jstr(c.grade),
                "id" to jstr(c.id),
                "is_active" to jbool(true),
                "keeper_code" to maybeStr(c.keeperCode),
                "name_ar" to jstr(c.nameAr),
                "name_en" to jstr(c.nameEn),
            )
        })
        val fabricsJson = jarr(fabrics.map { f ->
            jobj(
                "family" to jstr(f.family),
                "id" to jstr(f.id),
                "name_ar" to jstr(f.nameAr),
                "name_en" to jstr(f.nameEn),
            )
        })
        val mappingsJson = jarr(mappings.map { m ->
            jobj(
                "colour_id" to jstr(m.colourId),
                "fabric_id" to jstr(m.fabricId),
                "id" to jstr(m.id),
                "is_active" to jbool(m.active),
                "warehouse_id" to jstr(m.shedId),
            )
        })
        val rollsJson = jarr(
            rollActive.entries.sortedBy { it.key }.map { (mappingId, count) ->
                jobj("count" to jlong(count), "id" to jstr(uuid5("roll:$mappingId:ACTIVE")), "mapping_id" to jstr(mappingId), "state" to jstr("ACTIVE"))
            } + rollInactive.entries.sortedBy { it.key }.map { (mappingId, count) ->
                jobj("count" to jlong(count), "id" to jstr(uuid5("roll:$mappingId:INACTIVE")), "mapping_id" to jstr(mappingId), "state" to jstr("INACTIVE"))
            },
        )
        val inventoryJson = jarr(inventory.sortedBy { it.id }.map { row ->
            jobj("closing" to jlong(row.closing), "id" to jstr(row.id), "mapping_id" to jstr(row.mappingId), "opening_cache" to jlong(row.opening))
        })
        val movementsJson = jarr(movementsRaw.sortedBy { it.code }.map { mv ->
            jobj(
                "actor_account_id" to jstr("admin"),
                "at" to jstr(atIsoById.getValue(mv.id)),
                "id" to jstr(mv.id),
                "idempotency_key" to jstr(uuid5("idem:${mv.code}")),
                "mapping_id" to jstr(mv.mappingId),
                "note" to jstr(""),
                "qty_delta" to jlong(mv.qtyDelta),
                "transfer_pair_id" to maybeStr(mv.transferPairId),
                "type" to jstr(mv.type),
            )
        })
        val settingsRows = listOf(
            "arabic_indic_digits" to "false",
            "language" to "ar",
            "letterhead_address" to "",
            "letterhead_email" to "",
            "letterhead_legal_name" to "",
            "letterhead_logo_uri" to "",
            "letterhead_phone" to "",
            "letterhead_tax_id" to "",
        )
        val settingsJson = jarr(settingsRows.map { (k, v) -> jobj("key" to jstr(k), "value" to jstr(v)) })

        val root = jobj(
            "accounts" to accountsJson,
            "audit_log" to jarr(emptyList()),
            "colours" to coloursJson,
            "fabrics" to fabricsJson,
            "inventory" to inventoryJson,
            "mappings" to mappingsJson,
            "meta" to jobj(
                "generator" to jstr(SeedGenerator.GENERATOR_ID),
                "namespace" to jstr(SeedGenerator.NAMESPACE),
                "seed_version" to jstr(SeedGenerator.SEED_VERSION),
            ),
            "movements" to movementsJson,
            "rolls" to rollsJson,
            "sessions" to jarr(emptyList()),
            "settings" to settingsJson,
            "shed_assignments" to assignmentsJson,
            "warehouses" to warehousesJson,
        )
        val jsonText = SeedGenerator.writeCanonical(root) + "\n"
        val jsonSha = java.security.MessageDigest.getInstance("SHA-256").digest(jsonText.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }

        // ---- report ----
        val activeRolls = rollActive.values.sum()
        val inactiveRolls = rollInactive.values.sum()
        val rawRolls = activeRolls + inactiveRolls
        val keeper1Sheds = setOf("wh-alwa", "wh-leon", "wh-azouz", "wh-zibda", "wh-showroom")
        val keeper2Sheds = setOf("wh-625", "wh-magdy", "wh-wahman", "wh-lycra", "wh-kashry")
        val keeper1Active = mappings.filter { it.shedId in keeper1Sheds }.sumOf { rollActive[it.id] ?: 0L }
        val keeper2Active = mappings.filter { it.shedId in keeper2Sheds }.sumOf { rollActive[it.id] ?: 0L }
        val wh625Active = mappings.filter { it.shedId == "wh-625" }.sumOf { rollActive[it.id] ?: 0L }
        val wh625Capacity = SeedGenerator.warehouses.first { it.id == "wh-625" }.capacity
        val util = java.math.BigDecimal(wh625Active)
            .divide(java.math.BigDecimal(wh625Capacity), 10, java.math.RoundingMode.HALF_UP)
            .multiply(java.math.BigDecimal(100))
            .setScale(1, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        check(util == "175.2") { "wh-625 utilisation $util != 175.2" }
        val movementsIn = movementsRaw.count { it.type == "IN" }
        val movementsOut = movementsRaw.count { it.type == "OUT" }
        val movementsXOut = movementsRaw.count { it.type == "TRANSFER_OUT" }
        val movementsXIn = movementsRaw.count { it.type == "TRANSFER_IN" }
        val movementsAdj = movementsRaw.count { it.type == "ADJUSTMENT" }
        val transferPairs = movementsRaw.mapNotNull { it.transferPairId }.distinct().size
        val totalRecords = SeedGenerator.warehouses.size + fabrics.size + colours.size + mappings.size +
            inventory.size + movementsRaw.size + SeedGenerator.warehouses.size
        check(totalRecords == 3005) { "total records $totalRecords != 3005" }
        check(activeRolls == 68567L) { "active rolls $activeRolls != 68567" }
        check(keeper1Active == 51333L) { "keeper1 $keeper1Active != 51333" }
        check(keeper2Active == 17234L) { "keeper2 $keeper2Active != 17234" }
        check(rawRolls == 69787L) { "raw rolls $rawRolls != 69787" }
        check(inactiveRolls == 1220L) { "inactive rolls $inactiveRolls != 1220" }
        check(movementsIn == 40 && movementsOut == 40 && movementsAdj == 20) { "movement type counts wrong" }
        check(movementsXOut == 12 && movementsXIn == 12 && transferPairs == 12) { "transfer structure wrong" }

        val report = buildString {
            append("alazhartex-seed-report v1\n")
            append("generator=${SeedGenerator.GENERATOR_ID}\n")
            append("seed_version=${SeedGenerator.SEED_VERSION}\n")
            append("total_records=$totalRecords\n")
            append("sheds=${SeedGenerator.warehouses.size}\n")
            append("fabrics=${fabrics.size}\n")
            append("colours=${colours.size}\n")
            append("colour_grade_a=${colours.count { it.grade == "A" }}\n")
            append("colour_grade_b=${colours.count { it.grade == "B" }}\n")
            append("colour_grade_c=${colours.count { it.grade == "C" }}\n")
            append("colour_grade_d=${colours.count { it.grade == "D" }}\n")
            append("colour_families=${colours.map { it.family }.distinct().size}\n")
            append("fabric_colour_mappings=${mappings.size}\n")
            append("inventory_rows=${inventory.size}\n")
            append("movements=${movementsRaw.size}\n")
            append("movements_in=$movementsIn\n")
            append("movements_out=$movementsOut\n")
            append("movements_transfer=${movementsXOut + movementsXIn}\n")
            append("movements_transfer_pairs=$transferPairs\n")
            append("movements_adjustment=$movementsAdj\n")
            append("active_rolls=$activeRolls\n")
            append("active_rolls_keeper1=$keeper1Active\n")
            append("active_rolls_keeper2=$keeper2Active\n")
            append("raw_rolls=$rawRolls\n")
            append("inactive_rolls=$inactiveRolls\n")
            append("inactive_mapping_rows=${mappings.count { !it.active }}\n")
            append("keeper_colour_codes=${colours.count { it.keeperCode != null }}\n")
            append("wh625_active_rolls=$wh625Active\n")
            append("wh625_capacity_rolls=$wh625Capacity\n")
            append("wh625_utilisation_pct=$util\n")
            append("wh625_mapped_colours=${mappings.count { it.shedId == "wh-625" }}\n")
            append("wh625_active_mappings=${mappings.count { it.shedId == "wh-625" && it.active }}\n")
            append("opening_equals_recompute=true\n")
            append("seed_json_sha256=$jsonSha\n")
        }
        return jsonText to report
    }
}

fun main(args: Array<String>) {
    val outDir = java.io.File(args.getOrElse(0) { "." })
    if (!outDir.isDirectory && !outDir.mkdirs()) {
        System.err.println("cannot create output directory: $outDir")
        kotlin.system.exitProcess(2)
    }
    try {
        kotlin.system.exitProcess(SeedBuild(outDir).run())
    } catch (e: Exception) {
        System.err.println("seed generation failed: ${e.message}")
        kotlin.system.exitProcess(3)
    }
}
