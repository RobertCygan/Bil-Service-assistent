package app.servicepassport.lookup

/** Konfiguracja wyłącznie ze zmiennych środowiskowych. Sekrety nigdy nie trafiają do logów. */
data class Config(
    val port: Int = 8080,
    /** Klucz API Statens vegvesen; null = odczyt niedostępny (503), serwer mimo to działa. */
    val svvApiKey: String? = null,
    /**
     * Adres i nagłówek zapytania do SVV. Wartości domyślne pochodzą z wiedzy ogólnej i NIE były
     * sprawdzone z dokumentacją ani prawdziwym kluczem — do potwierdzenia przed uruchomieniem.
     */
    val svvUrl: String = "https://akfell-datautlevering.atlas.vegvesen.no/enkeltoppslag/kjoretoydata",
    val svvAuthHeader: String = "SVV-Authorization",
    val svvAuthPrefix: String = "Apikey ",
    /** Opcjonalny wspólny token aplikacji. Słaba bariera (da się go wyciągnąć z aplikacji), nie zabezpieczenie. */
    val appToken: String? = null,
    /** Limit na jeden adres IP. */
    val perClientPerHour: Int = 30,
    /** Twardy dzienny limit zapytań do SVV (ich limit to 50 000 na klucz na dobę). */
    val upstreamPerDay: Int = 5_000,
    /** Czas przechowywania odpowiedzi w pamięci; 0 = bez cache (domyślnie, do czasu sprawdzenia warunków SVV). */
    val cacheSeconds: Long = 0,
    val cacheMaxEntries: Int = 10_000,
    /** Ustaw true tylko za własnym reverse proxy — wtedy adres klienta bierzemy z X-Forwarded-For. */
    val trustProxy: Boolean = false,
    val upstreamTimeoutSeconds: Long = 10,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            val d = Config()
            fun text(name: String) = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun int(name: String, default: Int) = text(name)?.toIntOrNull()?.takeIf { it >= 0 } ?: default
            return Config(
                port = int("PORT", d.port),
                svvApiKey = text("SVV_API_KEY"),
                svvUrl = text("SVV_URL") ?: d.svvUrl,
                svvAuthHeader = text("SVV_AUTH_HEADER") ?: d.svvAuthHeader,
                svvAuthPrefix = env["SVV_AUTH_PREFIX"] ?: d.svvAuthPrefix,
                appToken = text("APP_TOKEN"),
                perClientPerHour = int("RATE_PER_CLIENT_PER_HOUR", d.perClientPerHour),
                upstreamPerDay = int("UPSTREAM_PER_DAY", d.upstreamPerDay),
                cacheSeconds = int("CACHE_SECONDS", 0).toLong(),
                cacheMaxEntries = int("CACHE_MAX_ENTRIES", d.cacheMaxEntries),
                trustProxy = text("TRUST_PROXY") == "true",
                upstreamTimeoutSeconds = int("UPSTREAM_TIMEOUT_SECONDS", 10).toLong(),
            )
        }
    }
}
