package app.servicepassport.lookup

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Rejestr jest w testach atrapą; prawdziwego API SVV nie wywołano. Numery są zmyślone. */
class LookupServerTest {
    private val http = HttpClient.newHttpClient()
    private val logs = mutableListOf<String>()
    private val calls = mutableListOf<Pair<LookupBy, String>>()
    private var now = Instant.parse("2026-11-10T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private var server: LookupServer? = null
    private var result: UpstreamResult = UpstreamResult.Found("""{"kjoretoy":"demo"}""")

    private fun start(config: Config = Config(port = 0), configured: Boolean = true): LookupServer {
        val upstream = if (configured) Upstream { by, value -> calls += by to value; result } else null
        return LookupServer(config, upstream, clock) { synchronized(logs) { logs += it } }.start().also { server = it }
    }

    private fun get(path: String, token: String? = null, method: String = "GET"): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:${server!!.port}$path"))
        token?.let { builder.header("X-App-Token", it) }
        builder.method(method, HttpRequest.BodyPublishers.noBody())
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    @AfterTest
    fun stop() { server?.stop() }

    @Test
    fun `plate lookup normalizes the number and wraps the registry answer`() {
        start()
        val r = get("/v1/vehicles/no/plate/ab%2012345")
        assertEquals(200, r.statusCode())
        assertEquals("""{"source":"svv","mapped":false,"data":{"kjoretoy":"demo"}}""", r.body())
        assertEquals(listOf(LookupBy.PLATE to "AB12345"), calls)
        assertEquals("no-store", r.headers().firstValue("Cache-Control").get())
    }

    @Test
    fun `vin lookup validates before calling the registry`() {
        start()
        assertEquals(200, get("/v1/vehicles/no/vin/xxdema0000000t123").statusCode())
        assertEquals(listOf(LookupBy.VIN to "XXDEMA0000000T123"), calls)
        for (bad in listOf("/v1/vehicles/no/vin/TOOSHORT", "/v1/vehicles/no/vin/XXDEMA0000000O123", "/v1/vehicles/no/plate/AB123456789", "/v1/vehicles/no/plate/%00%00")) {
            val r = get(bad)
            assertEquals(400, r.statusCode(), bad)
            assertEquals("""{"error":"invalid_identifier"}""", r.body())
        }
        assertEquals(1, calls.size, "invalid identifiers never reach the registry")
    }

    @Test
    fun `logs never contain the plate, the VIN or the client address`() {
        start()
        get("/v1/vehicles/no/plate/AB12345")
        get("/v1/vehicles/no/vin/XXDEMA0000000T123")
        get("/v1/vehicles/no/plate/!!")
        val all = synchronized(logs) { logs.joinToString("\n") }
        assertTrue("route=/v1/vehicles/no/plate/{plate} status=200" in all)
        assertTrue("route=/v1/vehicles/no/vin/{vin} status=200" in all)
        for (secret in listOf("AB12345", "XXDEMA", "127.0.0.1", "localhost")) assertFalse(secret in all, secret)
    }

    @Test
    fun `server without a key answers 503 but stays healthy`() {
        start(configured = false)
        assertEquals("""{"status":"ok","lookupConfigured":false}""", get("/v1/health").body())
        val r = get("/v1/vehicles/no/plate/AB12345")
        assertEquals(503, r.statusCode())
        assertEquals("""{"error":"lookup_not_configured"}""", r.body())
    }

    @Test
    fun `registry outcomes map to distinct errors`() {
        start()
        result = UpstreamResult.NotFound
        assertEquals(404 to """{"error":"vehicle_not_found"}""", get("/v1/vehicles/no/plate/AB11111").let { it.statusCode() to it.body() })
        result = UpstreamResult.Rejected
        assertEquals(503, get("/v1/vehicles/no/plate/AB22222").statusCode())
        result = UpstreamResult.Failed
        assertEquals(502, get("/v1/vehicles/no/plate/AB33333").statusCode())
    }

    @Test
    fun `per client limit resets with the next hour`() {
        start(Config(port = 0, perClientPerHour = 2))
        assertEquals(200, get("/v1/vehicles/no/plate/AB11111").statusCode())
        assertEquals(400, get("/v1/vehicles/no/plate/!!").statusCode()) // błędne zapytania też zużywają limit
        assertEquals(429, get("/v1/vehicles/no/plate/AB22222").statusCode())
        assertEquals(200, get("/v1/health").statusCode(), "health is not rate limited")
        now = now.plusSeconds(3600)
        assertEquals(200, get("/v1/vehicles/no/plate/AB22222").statusCode())
    }

    @Test
    fun `daily budget protects the registry quota and resets next day`() {
        start(Config(port = 0, upstreamPerDay = 2, perClientPerHour = 100))
        assertEquals(200, get("/v1/vehicles/no/plate/AB11111").statusCode())
        assertEquals(200, get("/v1/vehicles/no/plate/AB22222").statusCode())
        val r = get("/v1/vehicles/no/plate/AB33333")
        assertEquals(503 to """{"error":"daily_limit_reached"}""", r.statusCode() to r.body())
        assertEquals(2, calls.size)
        now = now.plusSeconds(24 * 3600)
        assertEquals(200, get("/v1/vehicles/no/plate/AB33333").statusCode())
    }

    @Test
    fun `cache is off by default and saves registry calls when enabled`() {
        start()
        repeat(2) { get("/v1/vehicles/no/plate/AB12345") }
        assertEquals(2, calls.size)
        stop(); calls.clear()

        start(Config(port = 0, cacheSeconds = 60))
        repeat(2) { assertEquals(200, get("/v1/vehicles/no/plate/AB12345").statusCode()) }
        get("/v1/vehicles/no/plate/ab-12345") // inny zapis tego samego numeru
        assertEquals(1, calls.size)
        now = now.plusSeconds(61)
        get("/v1/vehicles/no/plate/AB12345")
        assertEquals(2, calls.size)
    }

    @Test
    fun `app token is enforced when configured`() {
        start(Config(port = 0, appToken = "s3cret"))
        assertEquals(401, get("/v1/vehicles/no/plate/AB12345").statusCode())
        assertEquals(401, get("/v1/vehicles/no/plate/AB12345", token = "wrong").statusCode())
        assertEquals(200, get("/v1/vehicles/no/plate/AB12345", token = "s3cret").statusCode())
        assertEquals(200, get("/v1/health").statusCode())
        assertEquals(1, calls.size)
    }

    @Test
    fun `unknown paths and methods are refused`() {
        start()
        assertEquals(404, get("/").statusCode())
        assertEquals(404, get("/v1/vehicles/se/plate/ABC123").statusCode())
        assertEquals(405, get("/v1/vehicles/no/plate/AB12345", method = "POST").statusCode())
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `config reads environment and ignores blanks and garbage`() {
        val c = Config.fromEnv(mapOf("PORT" to "9000", "SVV_API_KEY" to "  ", "APP_TOKEN" to "t", "CACHE_SECONDS" to "abc", "TRUST_PROXY" to "true", "UPSTREAM_PER_DAY" to "-5"))
        assertEquals(9000, c.port)
        assertEquals(null, c.svvApiKey)
        assertEquals("t", c.appToken)
        assertEquals(0, c.cacheSeconds)
        assertTrue(c.trustProxy)
        assertEquals(5_000, c.upstreamPerDay)
    }
}
