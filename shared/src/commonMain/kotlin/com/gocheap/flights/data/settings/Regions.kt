package com.gocheap.flights.data.settings

import com.gocheap.flights.data.model.settings.RegionDto
import flighttp.shared.generated.resources.Res
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Somewhere a search can be priced and localised against. */
data class Country(
    val code: String,
    val name: String,
    /** The flag emoji the catalogue carries. Blank on the rows that have none — about a sixth. */
    val flag: String,
    /**
     * What this country quotes prices in, as the catalogue pairs them.
     *
     * Carried rather than derived: the pairing is the supplier's, and picking a country is the
     * natural moment to offer its currency.
     */
    val currencyCode: String,
    /** The languages the supplier localises this country's pages into. Often empty. */
    val supportedLanguages: List<String> = emptyList(),
    /**
     * The supplier's own site for this country, e.g. `https://www.kayak.co.uk/` for GB.
     *
     * Null on most rows — about three quarters of the catalogue carries none — and that is a
     * meaningful null rather than missing data: it marks a country the supplier does not run a
     * site for. What reads it is the ad path, where an impression URL arrives relative to this
     * and cannot be requested without it, so a country with no domain is a country with no ads.
     * See [com.gocheap.flights.data.ads.AdsRepository].
     */
    val domain: String? = null,
)

/**
 * [name] is the currency's name in English where one is known, and otherwise the code repeated.
 *
 * The catalogue carries no currency names — the reference app's own picker shows the code and the
 * symbol and nothing else. Rather than invent a name for each of the 100-odd currencies with no
 * confirmed one, those fall back to the code and the picker then draws a single line for them. A
 * redundant row is a far smaller problem than one naming the wrong currency.
 */
data class Currency(val code: String, val name: String, val symbol: String)

/**
 * [endonym] is the language's name in itself, which is how a language list is normally read.
 *
 * [apiCode] is the regional locale the supplier wants — "en-US" rather than "en" — which the
 * reference app maps every language through before sending it.
 */
data class AppLanguage(
    val code: String,
    val name: String,
    val endonym: String,
    val apiCode: String,
)

/**
 * The regional lists the settings pages are picked from.
 *
 * Read from `files/settings/Countries.json`, which is the reference app's own catalogue shipped
 * unchanged — the same arrangement as the popular-location files, and for the same reason:
 * refreshing it is a file copy rather than a transcription of 235 rows into Kotlin.
 *
 * Shipped rather than fetched because the three codes here go out on every supplier link, so a
 * list that was briefly unavailable would mean a search that could not be built. The reference
 * app reaches the same conclusion from the other direction — its region endpoint call is
 * commented out and it reads the bundled file.
 *
 * **Read synchronously, loaded asynchronously.** [load] has to be called once at launch; until it
 * returns, the three lists are empty and the lookups answer null. That is safe because nothing
 * functional reads them: the codes that go on the wire are the plain strings in
 * [AppSettingsState], and these objects exist only to put a name and a flag on screen beside one.
 * [AppSettingsState] knows to fall back to the bare code in the meantime.
 */
object Regions {

    private const val ResourcePath = "files/settings/Countries.json"

    private val json = Json {
        // The catalogue may carry keys this app has no use for after the next refresh.
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var catalogue: List<Country> = emptyList()
    private var currencyList: List<Currency> = emptyList()

    /** Alphabetical, as the catalogue is: at this length the list is searched rather than scanned. */
    val countries: List<Country> get() = catalogue

    val currencies: List<Currency> get() = currencyList

    /**
     * Sent as `language`, which is what the suppliers render their results pages in.
     *
     * The one list that is not in the catalogue — it carries only the language *codes* each
     * country supports, with no names to show for them. These are the reference app's own
     * `appLanguages`, which is likewise a table in its source rather than a file.
     *
     * Two codes differ from its on purpose: it stores Java's legacy "in" and "iw" for Indonesian
     * and Hebrew, and this stores the modern "id" and "he". They are the same two languages, the
     * supplier is sent "id-ID" and "he-IL" either way — see [AppLanguage.apiCode] — and the modern
     * codes are what this app's stored settings already hold.
     */
    val languages: List<AppLanguage> = listOf(
        AppLanguage("en", "English", "English", "en-US"),
        AppLanguage("ar", "Arabic", "العربية", "ar-AE"),
        AppLanguage("ca", "Catalan", "Català", "ca-ES"),
        AppLanguage("cs", "Czech", "Čeština", "cs-CZ"),
        AppLanguage("da", "Danish", "Dansk", "da-DK"),
        AppLanguage("de", "German", "Deutsch", "de-DE"),
        AppLanguage("el", "Greek", "Ελληνικά", "el-GR"),
        AppLanguage("es", "Spanish", "Español", "es-ES"),
        AppLanguage("fi", "Finnish", "Suomi", "fi-FI"),
        AppLanguage("fr", "French", "Français", "fr-FR"),
        AppLanguage("he", "Hebrew", "עברית", "he-IL"),
        AppLanguage("hi", "Hindi", "हिन्दी", "hi-IN"),
        AppLanguage("id", "Indonesian", "Bahasa Indonesia", "id-ID"),
        AppLanguage("it", "Italian", "Italiano", "it-IT"),
        AppLanguage("ja", "Japanese", "日本語", "ja-JP"),
        AppLanguage("ko", "Korean", "한국어", "ko-KR"),
        AppLanguage("ms", "Malay", "Bahasa Malaysia", "ms-MY"),
        AppLanguage("nl", "Dutch", "Nederlands", "nl-NL"),
        AppLanguage("no", "Norwegian", "Norsk", "nb-NO"),
        AppLanguage("pl", "Polish", "Polski", "pl-PL"),
        AppLanguage("pt", "Portuguese", "Português", "pt-PT"),
        AppLanguage("ro", "Romanian", "Română", "ro-RO"),
        AppLanguage("ru", "Russian", "Русский", "ru-RU"),
        AppLanguage("sv", "Swedish", "Svenska", "sv-SE"),
        AppLanguage("th", "Thai", "ไทย", "th-TH"),
        AppLanguage("tr", "Turkish", "Türkçe", "tr-TR"),
        AppLanguage("uk", "Ukrainian", "Українська", "uk-UA"),
        AppLanguage("vi", "Vietnamese", "Tiếng Việt", "vi-VN"),
        AppLanguage("zh", "Chinese", "中文", "zh-CN"),
    )

    /**
     * Reads the catalogue in. Called once at launch, beside the other startup work.
     *
     * An unreadable or malformed file costs the settings pages their names and flags and nothing
     * else — the stored codes still go out on every link — so this leaves the lists empty rather
     * than failing the launch.
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load() {
        if (catalogue.isNotEmpty()) return
        val rows = try {
            json.decodeFromString<List<RegionDto>>(Res.readBytes(ResourcePath).decodeToString())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            return
        }
        install(rows)
    }

    /**
     * Populates the lists from a catalogue already in hand.
     *
     * Exists so the parsing and the resource read can be exercised apart — a test has no resource
     * loader, and the mapping is the part worth asserting on.
     */
    internal fun install(contents: String) {
        install(json.decodeFromString<List<RegionDto>>(contents))
    }

    private fun install(rows: List<RegionDto>) {
        catalogue = rows.toCountries()
        currencyList = rows.toCurrencies()
    }

    /**
     * Split out from the resource read so the mapping can be tested without a resource loader,
     * exactly as the popular-location files are.
     */
    internal fun parse(contents: String): Pair<List<Country>, List<Currency>> {
        val rows = json.decodeFromString<List<RegionDto>>(contents)
        return rows.toCountries() to rows.toCurrencies()
    }

    private fun List<RegionDto>.toCountries(): List<Country> = mapNotNull { row ->
        // A row that cannot name a country is no use to a picker and no use on the wire.
        val code = row.code?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val name = row.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        Country(
            code = code,
            name = name,
            flag = row.flag.orEmpty(),
            currencyCode = row.currency?.code.orEmpty(),
            // `null` and `[]` mean the same thing in this file.
            supportedLanguages = row.supportedLanguages.orEmpty(),
            // Blank and absent likewise: both mean "no site for this country".
            domain = row.domain?.takeIf(String::isNotBlank),
        )
    }.sortedBy { it.name }

    /**
     * The distinct currencies the catalogue's countries quote in.
     *
     * Derived rather than listed separately: the file has no currency array, and every currency
     * worth offering is one some country prices in. First row wins, since the catalogue repeats
     * the same pair for every country sharing a currency.
     */
    private fun List<RegionDto>.toCurrencies(): List<Currency> = this
        // Only rows that are usable countries: a currency is offered because some country prices
        // in it, so one whose only row was dropped has nothing behind it.
        .filter { !it.code.isNullOrBlank() && !it.name.isNullOrBlank() }
        .mapNotNull { it.currency }
        .mapNotNull { currency ->
            val code = currency.code?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            Currency(
                code = code,
                name = ConfirmedNames[code] ?: code,
                // Several rows give the code again in place of a symbol. Where this app already
                // carried a real one — AED, SAR, SEK — it is preferred; otherwise the code stands,
                // and the picker draws a single line rather than repeating it.
                symbol = currency.symbol
                    ?.takeIf { it.isNotBlank() && it != code }
                    ?: BetterSymbols[code]
                    ?: code,
            )
        }
        .distinctBy(Currency::code)
        .sortedBy(Currency::code)

    /** Null for a code outside the catalogue, which the settings store then falls back from. */
    fun country(code: String): Country? = catalogue.firstOrNull { it.code.equals(code, true) }

    fun currency(code: String): Currency? = currencyList.firstOrNull { it.code.equals(code, true) }

    fun language(code: String): AppLanguage? = languages.firstOrNull { it.code.equals(code, true) }

    /**
     * The currency names this app has confirmed. Everything else shows its code — see [Currency].
     *
     * Kept here rather than in the file because the file is the reference app's and is meant to be
     * replaceable by a copy; a name added to it would be lost on the next refresh.
     */
    private val ConfirmedNames: Map<String, String> = mapOf(
        "USD" to "US dollar", "EUR" to "Euro", "GBP" to "Pound sterling",
        "INR" to "Indian rupee", "AED" to "UAE dirham", "CAD" to "Canadian dollar",
        "AUD" to "Australian dollar", "NZD" to "New Zealand dollar",
        "SGD" to "Singapore dollar", "CHF" to "Swiss franc", "SEK" to "Swedish krona",
        "NOK" to "Norwegian krone", "DKK" to "Danish krone", "PLN" to "Polish złoty",
        "CZK" to "Czech koruna", "TRY" to "Turkish lira", "ILS" to "Israeli shekel",
        "SAR" to "Saudi riyal", "QAR" to "Qatari riyal", "EGP" to "Egyptian pound",
        "ZAR" to "South African rand", "BRL" to "Brazilian real", "MXN" to "Mexican peso",
        "JPY" to "Japanese yen", "KRW" to "South Korean won", "CNY" to "Chinese yuan",
        "HKD" to "Hong Kong dollar", "THB" to "Thai baht", "MYR" to "Malaysian ringgit",
        "IDR" to "Indonesian rupiah", "PHP" to "Philippine peso", "VND" to "Vietnamese dong",
    )

    /** Where the catalogue repeats the code instead of giving a symbol, and a real one is known. */
    private val BetterSymbols: Map<String, String> = mapOf(
        "AED" to "د.إ",
        "SAR" to "﷼",
        "SEK" to "kr",
    )
}
