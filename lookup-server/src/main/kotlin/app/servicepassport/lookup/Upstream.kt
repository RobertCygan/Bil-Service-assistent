package app.servicepassport.lookup

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

enum class LookupBy(val queryName: String) { PLATE("kjennemerke"), VIN("understellsnummer") }

sealed interface UpstreamResult {
    /** [json] to nieprzetworzona odpowiedź rejestru. */
    data class Found(val json: String) : UpstreamResult
    data object NotFound : UpstreamResult
    /** Rejestr odrzucił klucz lub przekroczono jego limit — błąd konfiguracji, nie użytkownika. */
    data object Rejected : UpstreamResult
    data object Failed : UpstreamResult
}

fun interface Upstream {
    fun lookup(by: LookupBy, value: String): UpstreamResult
}

class SvvUpstream(private val config: Config, private val apiKey: String) : Upstream {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(config.upstreamTimeoutSeconds)).build()

    override fun lookup(by: LookupBy, value: String): UpstreamResult {
        val request = HttpRequest.newBuilder(URI("${config.svvUrl}?${by.queryName}=${URLEncoder.encode(value, Charsets.UTF_8)}"))
            .timeout(Duration.ofSeconds(config.upstreamTimeoutSeconds))
            .header(config.svvAuthHeader, config.svvAuthPrefix + apiKey)
            .header("Accept", "application/json")
            .GET().build()
        return try {
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> UpstreamResult.Found(response.body())
                204, 404 -> UpstreamResult.NotFound
                401, 403, 429 -> UpstreamResult.Rejected
                else -> UpstreamResult.Failed
            }
        } catch (_: java.io.IOException) {
            UpstreamResult.Failed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            UpstreamResult.Failed
        }
    }
}
