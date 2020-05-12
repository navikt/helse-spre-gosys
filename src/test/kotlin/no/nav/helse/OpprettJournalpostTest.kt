package no.nav.helse

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.HttpRequestData
import io.ktor.http.fullPath
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

@KtorExperimentalAPI
class OpprettJournalpostTest {

    private val testRapid = TestRapid()
    private val stsMock: StsRestClient = mockk {
        coEvery { token() }.returns("6B70C162-8AAB-4B56-944D-7F092423FE4B")
    }
    private val mockClient = httpclient()
    private val joark = spyk(JoarkClient("https://url.no", stsMock, mockClient))
    private var capturedRequest: HttpRequestData? = null
    private var capturedPayload: JournalpostPayload? = null

    init {
        OpprettJournalpost(testRapid, joark)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `journalfører et vedtak`() = runBlocking {
        testRapid.sendTestMessage(vedtakV3())
        val request = requireNotNull(capturedRequest)
        val payload = requireNotNull(capturedPayload)

        assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", request.headers["Authorization"])
        assertEquals("e8eb9ffa-57b7-4fe0-b44c-471b2b306bb6", request.headers["Nav-Consumer-Token"])
        assertEquals("application/json", request.body.contentType.toString())
        assertEquals(expectedJournalpost(), payload)
    }

    private fun httpclient(): HttpClient {
        return HttpClient(MockEngine) {
            install(JsonFeature) {
                serializer = JacksonSerializer()
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true" -> {
                            capturedRequest = request
                            capturedPayload =  objectMapper.readValue(request.body.toByteArray(), JournalpostPayload::class.java)
                            respond("Hello, world")
                        }
                        else -> error("Unhandled ${request.url.fullPath}")
                    }
                }
            }
        }
    }

    private fun expectedJournalpost(): JournalpostPayload {
        return JournalpostPayload(
            tittel = "Vedtak om sykepenger",
            journalpostType = "NOTAT",
            tema = "SYK",
            behandlingstema = "ab0061",
            journalfoerendeEnhet = "9999",
            bruker = Bruker(
                id = "fnr",
                idType = "FNR"
            ),
            sak = Sak(
                sakstype = "GENERELL_SAK"
            ),
            dokumenter = listOf(
                Dokument(
                    tittel = "Sykepenger utbetalt i ny løsning 11.05.2020-30.05.2020",
                    dokumentvarianter = listOf(
                        DokumentVariant(
                            filtype = "PDFA",
                            fysiskDokument = Base64.getEncoder().encodeToString("Test".toByteArray()),
                            variantformat = "ARKIV"
                        )
                    )
                )
            )
        )
    }

    @Language("JSON")
    private fun vedtakV3() = """{
    "aktørId": "aktørId",
    "fødselsnummer": "fnr",
    "organisasjonsnummer": "orgnummer",
    "hendelser": [
        "7c1a1edb-60b9-4a1f-b976-ef39d4d5021c",
        "798f60a1-6f6f-4d07-a036-1f89bd36baca",
        "ee8bc585-e898-4f4c-8662-f2a9b394896e"
    ],
    "utbetalt": [
        {
            "mottaker": "orgnummer",
            "fagområde": "SPREF",
            "fagsystemId": "77ATRH3QENHB5K4XUY4LQ7HRTY",
            "førsteSykepengedag": "",
            "totalbeløp": 8586,
            "utbetalingslinjer": [
                {
                    "fom": "2020-05-11",
                    "tom": "2020-05-30",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 100.0,
                    "sykedager": 15
                }
            ]
        },
        {
            "mottaker": "fnr",
            "fagområde": "SP",
            "fagsystemId": "353OZWEIBBAYZPKU6WYKTC54SE",
            "totalbeløp": 0,
            "utbetalingslinjer": []
        }
    ],
    "fom": "2020-05-11",
    "tom": "2020-05-30",
    "forbrukteSykedager": 15,
    "gjenståendeSykedager": 233,
    "opprettet": "2020-05-04T11:26:30.23846",
    "system_read_count": 0,
    "@event_name": "utbetalt",
    "@id": "e8eb9ffa-57b7-4fe0-b44c-471b2b306bb6",
    "@opprettet": "2020-05-04T11:27:13.521398",
    "@forårsaket_av": {
        "event_name": "behov",
        "id": "cf28fbba-562e-4841-b366-be1456fdccee",
        "opprettet": "2020-05-04T11:26:47.088455"
    }
}
"""
}
