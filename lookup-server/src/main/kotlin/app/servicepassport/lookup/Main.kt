package app.servicepassport.lookup

fun main() {
    val config = Config.fromEnv()
    val upstream = config.svvApiKey?.let { SvvUpstream(config, it) }
    val server = LookupServer(config, upstream).start()
    println("started port=${server.port} lookupConfigured=${upstream != null} cacheSeconds=${config.cacheSeconds}")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
}
