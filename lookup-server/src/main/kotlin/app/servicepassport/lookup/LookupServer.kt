package app.servicepassport.lookup

import app.servicepassport.vehicleid.NorwegianPlate
import app.servicepassport.vehicleid.PlateResult
import app.servicepassport.vehicleid.Vin
import app.servicepassport.vehicleid.VinResult
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.Executors

/**
 * Serwer pośredniczący między aplikacją a rejestrem pojazdów. Trzyma klucz API po stronie serwera,
 * ogranicza nadużycia i NIE zapisuje w logach numerów rejestracyjnych ani VIN.
 *
 *     GET /v1/health
 *     GET /v1/vehicles/no/plate/{numer}
 *     GET /v1/vehicles/no/vin/{vin}
 */
class LookupServer(
    private val config: Config,
    private val upstream: Upstream?,
    private val clock: Clock = Clock.systemUTC(),
    private val log: (String) -> Unit = ::println,
) {
    private val clientLimiter = WindowLimiter(config.perClientPerHour, 3600)
    private val budget = DailyBudget(config.upstreamPerDay)
    private val cache = TtlCache(config.cacheSeconds, config.cacheMaxEntries)
    private var server: HttpServer? = null

    val port: Int get() = checkNotNull(server) { "server not started" }.address.port

    fun start(): LookupServer {
        val s = HttpServer.create(InetSocketAddress(config.port), 64)
        s.createContext("/") { exchange -> exchange.use { handle(it) } }
        s.executor = Executors.newFixedThreadPool(8)
        s.start()
        server = s
        return this
    }

    fun stop() { server?.stop(0) }

    private class Response(val status: Int, val body: String, val route: String)

    private fun handle(exchange: HttpExchange) {
        val started = System.nanoTime()
        val response = try {
            route(exchange)
        } catch (e: Exception) {
            log("error type=${e.javaClass.simpleName}") // bez komunikatu: mógłby zawierać dane zapytania
            Response(500, error("internal_error"), "error")
        }
        val bytes = response.body.toByteArray()
        exchange.responseHeaders.apply {
            set("Content-Type", "application/json; charset=utf-8")
            set("Cache-Control", "no-store")
            set("X-Content-Type-Options", "nosniff")
        }
        exchange.sendResponseHeaders(response.status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        // Log zawiera szablon trasy, nigdy ścieżkę z numerem ani adres klienta.
        log("request method=${exchange.requestMethod} route=${response.route} status=${response.status} ms=${(System.nanoTime() - started) / 1_000_000}")
    }

    private fun route(exchange: HttpExchange): Response {
        val path = exchange.requestURI.rawPath.trimEnd('/')
        if (exchange.requestMethod != "GET") return Response(405, error("method_not_allowed"), "other")
        if (path == "/v1/health") return Response(200, """{"status":"ok","lookupConfigured":${upstream != null}}""", "/v1/health")

        val (by, raw, route) = when {
            path.startsWith(PLATE_PREFIX) -> Triple(LookupBy.PLATE, path.removePrefix(PLATE_PREFIX), "/v1/vehicles/no/plate/{plate}")
            path.startsWith(VIN_PREFIX) -> Triple(LookupBy.VIN, path.removePrefix(VIN_PREFIX), "/v1/vehicles/no/vin/{vin}")
            else -> return Response(404, error("not_found"), "other")
        }
        if (config.appToken != null && !sameToken(exchange.requestHeaders.getFirst("X-App-Token"), config.appToken)) {
            return Response(401, error("unauthorized"), route)
        }
        if (!clientLimiter.allow(clientKey(exchange), clock.instant())) return Response(429, error("rate_limited"), route)

        val decoded = try { URLDecoder.decode(raw, Charsets.UTF_8) } catch (_: IllegalArgumentException) { return Response(400, error("invalid_identifier"), route) }
        val id = when (by) {
            LookupBy.PLATE -> (NorwegianPlate.parse(decoded) as? PlateResult.Accepted)?.plate
            LookupBy.VIN -> (Vin.parse(decoded) as? VinResult.Valid)?.vin
        } ?: return Response(400, error("invalid_identifier"), route)

        val up = upstream ?: return Response(503, error("lookup_not_configured"), route)
        val cacheKey = "${by.name}:$id"
        cache.get(cacheKey, clock.instant())?.let { return Response(200, found(it), route) }
        if (!budget.take(clock.instant())) return Response(503, error("daily_limit_reached"), route)

        return when (val result = up.lookup(by, id)) {
            is UpstreamResult.Found -> {
                cache.put(cacheKey, result.json, clock.instant())
                Response(200, found(result.json), route)
            }
            UpstreamResult.NotFound -> Response(404, error("vehicle_not_found"), route)
            UpstreamResult.Rejected -> Response(503, error("lookup_unavailable"), route)
            UpstreamResult.Failed -> Response(502, error("upstream_error"), route)
        }
    }

    /** Adres klienta służy tylko jako klucz limitu, w postaci skrótu — nie jest logowany ani zapisywany. */
    private fun clientKey(exchange: HttpExchange): String {
        val forwarded = if (config.trustProxy) exchange.requestHeaders.getFirst("X-Forwarded-For")?.substringAfterLast(',')?.trim() else null
        val address = forwarded?.takeIf { it.isNotEmpty() } ?: exchange.remoteAddress.address.hostAddress
        return MessageDigest.getInstance("SHA-256").digest(address.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun sameToken(given: String?, expected: String): Boolean =
        given != null && MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())

    /**
     * `mapped:false` — odpowiedź rejestru jest przekazywana bez przetworzenia. Mapowanie na pola aplikacji
     * powstanie po sprawdzeniu prawdziwej odpowiedzi SVV.
     */
    private fun found(upstreamJson: String) = """{"source":"svv","mapped":false,"data":$upstreamJson}"""

    private fun error(code: String) = """{"error":"$code"}"""

    private companion object {
        const val PLATE_PREFIX = "/v1/vehicles/no/plate/"
        const val VIN_PREFIX = "/v1/vehicles/no/vin/"
    }
}
