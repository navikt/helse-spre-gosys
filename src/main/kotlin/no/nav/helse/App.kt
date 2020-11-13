package no.nav.helse

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import no.nav.helse.annullering.AnnulleringMediator
import no.nav.helse.annullering.AnnulleringRiver
import no.nav.helse.io.IO
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.vedtak.VedtakMediator
import no.nav.helse.vedtak.VedtakMessage
import no.nav.helse.vedtak.VedtakRiver
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
    val annulleringMediator = AnnulleringMediator(pdfClient, joarkClient)

    return RapidApplication.Builder(RapidApplication.RapidApplicationConfig.fromEnv(environment)).withKtorModule {
        basicAuthentication(environment.getValue("ADMIN_SECRET"))
        routing {
            authenticate("admin") {
                adminGrensesnitt(vedtakMediator)
            }
        }
    }.build()
        .apply {
            VedtakRiver(this, vedtakMediator)
            AnnulleringRiver(this, annulleringMediator)
        }
}

internal fun Route.adminGrensesnitt(
    vedtakMediator: VedtakMediator
) {
    route("/admin") {
        post("/utbetaling") {
            log.info("Leser inn utbetalinger")
            val utbetaling = call.receive<ArrayNode>()
            utbetaling.forEachIndexed { index, json ->
                try {
                    val format = Json { ignoreUnknownKeys = true }
                    val vedtak: IO.Vedtak = format.decodeFromString(json.toString())
                    val vedtakMessage = VedtakMessage(vedtak)
                    log.info("Behandler utbetaling {}", vedtakMessage.hendelseId)
                    sikkerLogg.info(json.toString())
                    vedtakMediator.opprettVedtak(vedtakMessage)
                } catch (error: RuntimeException) {
                    log.error("Kritisk feil, se sikker logg for fullstendig feilmelding")
                    sikkerLogg.error("Kritisk feil for index $index", error)
                }
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}
