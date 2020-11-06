package no.nav.helse.api

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.adminGrensesnitt
import no.nav.helse.basicAuthentication
import no.nav.helse.objectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.vedtak.VedtakMediator
import no.nav.helse.vedtak.VedtakMessage
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import java.util.*
import kotlin.test.assertEquals

class AdminApiTest {

    private val mediator: VedtakMediator = mockk(relaxed = true)

    @Test
    fun `oppslag på utbetaling`() {
        withTestApplication({
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            basicAuthentication("hunter2")
            routing { adminGrensesnitt(mediator) }
        }) {
            with(handleRequest(HttpMethod.Post, "/admin/utbetaling") {
                val userpass = Base64.getEncoder().encodeToString("admin:hunter2".toByteArray())
                addHeader(HttpHeaders.Authorization, "Basic $userpass")
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody(vedtakMessage)
            }) {
                assertEquals(HttpStatusCode.OK, response.status())
                verify(exactly = 1) {
                    mediator.opprettVedtak(
                        VedtakMessage(
                            JsonMessage(vedtakMessage, MessageProblems(vedtakMessage))
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `unauthorized uten autentisering`() {
        withTestApplication({
            install(ContentNegotiation) { register(ContentType.Application.Json, JacksonConverter(objectMapper)) }
            basicAuthentication("hunter2")
            routing { adminGrensesnitt(mediator) }
        }) {
            with(handleRequest(HttpMethod.Post, "/admin/utbetaling") {
                val userpass = Base64.getEncoder().encodeToString("admin:🅱️".toByteArray())
                addHeader(HttpHeaders.Authorization, "Basic $userpass")
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody(vedtakMessage)
            }) {
                assertEquals(HttpStatusCode.Unauthorized, response.status())
                verify(exactly = 0) { mediator.opprettVedtak(any<VedtakMessage>()) }
            }
        }
    }

    @AfterEach
    fun setupAfterEach() {
        clearMocks(mediator)
    }

    @Language("json")
    private val vedtakMessage: String = """[
    {
        "aktørId": "1427484794278",
        "fødselsnummer": "11109726259",
        "organisasjonsnummer": "910825526",
        "hendelser": [
            "57697a8b-d25b-4589-86d3-1028d843b173",
            "3fab565e-e4f1-4d80-a4eb-f1f83f008d42",
            "5821120e-1d4e-48d6-b73c-4d7d5cc4979b"
        ],
        "utbetalt": [
            {
                "mottaker": "910825526",
                "fagområde": "SPREF",
                "fagsystemId": "MB3Z5KXZMFCLHBXHEGMNPZ2QNE",
                "totalbeløp": 6300,
                "utbetalingslinjer": [
                    {
                        "fom": "2020-09-23",
                        "tom": "2020-09-30",
                        "dagsats": 1050,
                        "beløp": 1050,
                        "grad": 100.0,
                        "sykedager": 6
                    }
                ]
            },
            {
                "mottaker": "11109726259",
                "fagområde": "SP",
                "fagsystemId": "LI25TZVH2FHWLFJN3JZGPN6GT4",
                "totalbeløp": 0,
                "utbetalingslinjer": []
            }
        ],
        "ikkeUtbetalteDager": [],
        "fom": "2020-09-07",
        "tom": "2020-09-30",
        "forbrukteSykedager": 6,
        "gjenståendeSykedager": 242,
        "godkjentAv": "S151890",
        "automatiskBehandling": false,
        "opprettet": "2020-11-06T10:42:24.783454",
        "sykepengegrunnlag": 273012.0,
        "månedsinntekt": 22751.0,
        "maksdato": "2021-09-03",
        "system_read_count": 0,
        "system_participating_services": [
            {
                "service": "spleis",
                "instance": "spleis-857699d9b9-wzrk5",
                "time": "2020-11-06T10:43:33.925252"
            }
        ],
        "@event_name": "utbetalt",
        "@id": "34df5416-ba6d-429b-8746-abe5125b9530",
        "@opprettet": "2020-11-06T10:43:33.925287",
        "@forårsaket_av": {
            "event_name": "behov",
            "id": "22efebd0-a2d1-45d7-b4b0-df47a1289f72",
            "opprettet": "2020-11-06T10:43:33.573795"
        }
    }
]
    """
}
