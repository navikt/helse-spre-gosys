package no.nav.helse

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.vedtak.VedtakRiver
import no.nav.helse.vedtak.VedtakMediator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal val objectMapper: ObjectMapper = jacksonObjectMapper()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .registerModule(JavaTimeModule())

internal val log: Logger = LoggerFactory.getLogger("spregosys")
internal val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val rapidsConnection = launchApplication(System.getenv())
    rapidsConnection.start()
}

fun launchApplication(
    environment: Map<String, String>
): RapidsConnection {
    val serviceUser = readServiceUserCredentials()
    val stsRestClient = StsRestClient(requireNotNull(environment["STS_URL"]), serviceUser)
    val httpClient = HttpClient {
        install(JsonFeature) { serializer = JacksonSerializer(objectMapper) }
    }
    val joarkClient = JoarkClient(requireNotNull(environment["JOARK_BASE_URL"]), stsRestClient, httpClient)
    val pdfClient = PdfClient(httpClient)

    val vedtakMediator = VedtakMediator(pdfClient, joarkClient)

    return RapidApplication.create(environment).apply {
        VedtakRiver(this, vedtakMediator)
    }
}
