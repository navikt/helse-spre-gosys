package no.nav.helse.vedtak

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
import no.nav.helse.JoarkClient
import no.nav.helse.JournalpostPayload
import no.nav.helse.PdfClient
import no.nav.helse.StsRestClient
import no.nav.helse.objectMapper
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Base64

@KtorExperimentalAPI
class VedtakRiverTest {

    private val testRapid = TestRapid()
    private val stsMock: StsRestClient = mockk {
        coEvery { token() }.returns("6B70C162-8AAB-4B56-944D-7F092423FE4B")
    }
    private val mockClient = httpclient()
    private val joarkClient = spyk(JoarkClient("https://url.no", stsMock, mockClient))
    private val pdfClient = PdfClient(mockClient)
    private val vedtakMediator = VedtakMediator(pdfClient, joarkClient)
    private val godkjentAv = "A123456"

    private var capturedJoarkRequest: HttpRequestData? = null
    private var capturedPdfRequest: HttpRequestData? = null

    init {
        VedtakRiver(testRapid, vedtakMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `journalfører et vedtak`() = runBlocking {
        testRapid.sendTestMessage(vedtakV3())
        val joarkRequest = requireNotNull(capturedJoarkRequest)
        val joarkPayload =
            requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

        assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
        assertEquals("e8eb9ffa-57b7-4fe0-b44c-471b2b306bb6", joarkRequest.headers["Nav-Consumer-Token"])
        assertEquals("application/json", joarkRequest.body.contentType.toString())
        assertEquals(expectedJournalpost(), joarkPayload)

        val pdfRequest = requireNotNull(capturedPdfRequest)
        val pdfPayload =
            requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), VedtakPdfPayload::class.java))

        val expectedPdfPayload = VedtakPdfPayload(
            fødselsnummer = "fnr",
            fagsystemId = "77ATRH3QENHB5K4XUY4LQ7HRTY",
            fom = LocalDate.of(2020, 5, 11),
            tom = LocalDate.of(2020, 5, 30),
            organisasjonsnummer = "orgnummer",
            behandlingsdato = LocalDate.of(2020, 5, 4),
            dagerIgjen = 233,
            automatiskBehandling = false,
            godkjentAv = godkjentAv,
            totaltTilUtbetaling = 8586,
            ikkeUtbetalteDager = listOf(),
            dagsats = 1431,
            sykepengegrunnlag = 12345.0,
            maksdato = null,
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2020, 5, 11),
                    tom = LocalDate.of(2020, 5, 30),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )

        assertEquals(expectedPdfPayload, pdfPayload)
    }

    @Test
    fun `journalfører et vedtak uten utbetaling`() = runBlocking {
        testRapid.sendTestMessage(vedtakUtenUtbetaling())
        val joarkRequest = requireNotNull(capturedJoarkRequest)
        val joarkPayload =
            requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

        assertEquals(expectedJournalpostUtenUtbetaling(), joarkPayload)

        val pdfRequest = requireNotNull(capturedPdfRequest)
        val pdfPayload =
            requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), VedtakPdfPayload::class.java))

        val expectedPdfPayload = VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "V7E6ZHOCKJDNZNQMCAU23MQ47A",
            fom = LocalDate.of(2020, 5, 16),
            tom = LocalDate.of(2020, 5, 17),
            organisasjonsnummer = "123456789",
            behandlingsdato = LocalDate.of(2020, 5, 20),
            dagerIgjen = 48,
            automatiskBehandling = false,
            godkjentAv = godkjentAv,
            totaltTilUtbetaling = 0,
            ikkeUtbetalteDager = listOf(),
            dagsats = null,
            sykepengegrunnlag = 1337.69,
            maksdato = null,
            linjer = listOf()
        )

        assertEquals(expectedPdfPayload, pdfPayload)
    }

    @Test
    fun `journalfører et vedtak med flere utbetalinger`() = runBlocking {
        testRapid.sendTestMessage(vedtakFlereUtbetalinger())
        val joarkRequest = requireNotNull(capturedJoarkRequest)
        val joarkPayload =
            requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

        assertEquals(expectedJournalpostMedFlereUtbetalinger(), joarkPayload)

        val pdfRequest = requireNotNull(capturedPdfRequest)
        val pdfPayload =
            requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), VedtakPdfPayload::class.java))

        val expectedPdfPayload = VedtakPdfPayload(
            fødselsnummer = "12345678910",
            fagsystemId = "V7E6ZHOCKJDNZNQMCAU23MQ47A",
            fom = LocalDate.of(2020, 5, 16),
            tom = LocalDate.of(2020, 5, 17),
            organisasjonsnummer = "123456789",
            behandlingsdato = LocalDate.of(2020, 5, 20),
            dagerIgjen = 48,
            automatiskBehandling = false,
            godkjentAv = godkjentAv,
            totaltTilUtbetaling = 15000,
            ikkeUtbetalteDager = listOf(),
            dagsats = 1431,
            sykepengegrunnlag = 420.69,
            maksdato = LocalDate.of(2020, 7, 17),
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2020, 5, 11),
                    tom = LocalDate.of(2020, 5, 20),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                ),
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2020, 5, 21),
                    tom = LocalDate.of(2020, 5, 30),
                    grad = 50,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )

        assertEquals(expectedPdfPayload, pdfPayload)
    }

    @Test
    fun `mapper ikke-utbetalte dager`() = runBlocking {
        testRapid.sendTestMessage(vedtakIkkeUtbetalteDager())
        val joarkRequest = requireNotNull(capturedJoarkRequest)
        val joarkPayload =
            requireNotNull(objectMapper.readValue(joarkRequest.body.toByteArray(), JournalpostPayload::class.java))

        assertEquals("Bearer 6B70C162-8AAB-4B56-944D-7F092423FE4B", joarkRequest.headers["Authorization"])
        assertEquals("e8eb9ffa-57b7-4fe0-b44c-471b2b306bb6", joarkRequest.headers["Nav-Consumer-Token"])
        assertEquals("application/json", joarkRequest.body.contentType.toString())
        assertEquals(expectedJournalpost(), joarkPayload)

        val pdfRequest = requireNotNull(capturedPdfRequest)
        val pdfPayload =
            requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), VedtakPdfPayload::class.java))

        val expectedPdfPayload = VedtakPdfPayload(
            fødselsnummer = "fnr",
            fagsystemId = "77ATRH3QENHB5K4XUY4LQ7HRTY",
            fom = LocalDate.of(2020, 5, 11),
            tom = LocalDate.of(2020, 5, 30),
            organisasjonsnummer = "orgnummer",
            behandlingsdato = LocalDate.of(2020, 5, 4),
            dagerIgjen = 233,
            automatiskBehandling = false,
            godkjentAv = godkjentAv,
            totaltTilUtbetaling = 8586,
            ikkeUtbetalteDager = listOf(
                VedtakPdfPayload.IkkeUtbetalteDager(
                    fom = LocalDate.of(2020, 5, 11),
                    tom = LocalDate.of(2020, 5, 15),
                    grunn = "Sykdomsgrad under 20%"
                )
            ),
            dagsats = 1431,
            sykepengegrunnlag = 12345.0,
            maksdato = null,
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2020, 5, 16),
                    tom = LocalDate.of(2020, 5, 30),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )

        assertEquals(expectedPdfPayload, pdfPayload)
    }

    @Test
    fun `tar med arbeidsdager og kollapser over inneklemte fridager`() = runBlocking {
        testRapid.sendTestMessage(vedtakMedGjenopptattArbeid())
        val pdfRequest = requireNotNull(capturedPdfRequest)
        val pdfPayload =
            requireNotNull(objectMapper.readValue(pdfRequest.body.toByteArray(), VedtakPdfPayload::class.java))
        val expectedPdfPayload = VedtakPdfPayload(
            fødselsnummer = "fnr",
            fagsystemId = "77ATRH3QENHB5K4XUY4LQ7HRTY",
            fom = LocalDate.of(2020, 5, 11),
            tom = LocalDate.of(2020, 5, 25),
            organisasjonsnummer = "orgnummer",
            behandlingsdato = LocalDate.of(2020, 5, 4),
            dagerIgjen = 233,
            automatiskBehandling = false,
            godkjentAv = godkjentAv,
            totaltTilUtbetaling = 8586,
            ikkeUtbetalteDager = listOf(
                VedtakPdfPayload.IkkeUtbetalteDager(
                    fom = LocalDate.of(2020, 5, 20),
                    tom = LocalDate.of(2020, 5, 25),
                    grunn = "Arbeidsdag"
                )
            ),
            dagsats = 1431,
            sykepengegrunnlag = 12345.0,
            maksdato = null,
            linjer = listOf(
                VedtakPdfPayload.Linje(
                    fom = LocalDate.of(2020, 5, 16),
                    tom = LocalDate.of(2020, 5, 19),
                    grad = 100,
                    beløp = 1431,
                    mottaker = "arbeidsgiver"
                )
            )
        )
        assertEquals(expectedPdfPayload, pdfPayload)
    }

    private fun httpclient(): HttpClient {
        return HttpClient(MockEngine) {
            install(JsonFeature) {
                serializer = JacksonSerializer(objectMapper)
            }
            engine {
                addHandler { request ->
                    when (request.url.fullPath) {
                        "/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true" -> {
                            capturedJoarkRequest = request
                            respond("Hello, world")
                        }
                        "/api/v1/genpdf/spre-gosys/vedtak" -> {
                            capturedPdfRequest = request
                            respond("Test".toByteArray())
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
            bruker = JournalpostPayload.Bruker(
                id = "fnr",
                idType = "FNR"
            ),
            sak = JournalpostPayload.Sak(
                sakstype = "GENERELL_SAK"
            ),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = "Sykepenger behandlet i ny løsning, 11.05.2020 - 30.05.2020",
                    dokumentvarianter = listOf(
                        JournalpostPayload.Dokument.DokumentVariant(
                            filtype = "PDFA",
                            fysiskDokument = Base64.getEncoder().encodeToString("Test".toByteArray()),
                            variantformat = "ARKIV"
                        )
                    )
                )
            )
        )
    }

    private fun expectedJournalpostUtenUtbetaling(): JournalpostPayload {
        return JournalpostPayload(
            tittel = "Vedtak om sykepenger",
            journalpostType = "NOTAT",
            tema = "SYK",
            behandlingstema = "ab0061",
            journalfoerendeEnhet = "9999",
            bruker = JournalpostPayload.Bruker(
                id = "12345678910",
                idType = "FNR"
            ),
            sak = JournalpostPayload.Sak(
                sakstype = "GENERELL_SAK"
            ),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = "Sykepenger behandlet i ny løsning, 16.05.2020 - 17.05.2020",
                    dokumentvarianter = listOf(
                        JournalpostPayload.Dokument.DokumentVariant(
                            filtype = "PDFA",
                            fysiskDokument = Base64.getEncoder().encodeToString("Test".toByteArray()),
                            variantformat = "ARKIV"
                        )
                    )
                )
            )
        )
    }

    private fun expectedJournalpostMedFlereUtbetalinger(): JournalpostPayload {
        return JournalpostPayload(
            tittel = "Vedtak om sykepenger",
            journalpostType = "NOTAT",
            tema = "SYK",
            behandlingstema = "ab0061",
            journalfoerendeEnhet = "9999",
            bruker = JournalpostPayload.Bruker(
                id = "12345678910",
                idType = "FNR"
            ),
            sak = JournalpostPayload.Sak(
                sakstype = "GENERELL_SAK"
            ),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = "Sykepenger behandlet i ny løsning, 16.05.2020 - 17.05.2020",
                    dokumentvarianter = listOf(
                        JournalpostPayload.Dokument.DokumentVariant(
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
    private fun vedtakV3() = """
        {
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
            "ikkeUtbetalteDager": [],
            "fom": "2020-05-11",
            "tom": "2020-05-30",
            "forbrukteSykedager": 15,
            "automatiskBehandling": false,
            "godkjentAv": "$godkjentAv",
            "gjenståendeSykedager": 233,
            "sykepengegrunnlag": 12345.0,
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

    @Language("JSON")
    private fun vedtakUtenUtbetaling() = """
        {
          "aktørId": "1000012345678",
          "fødselsnummer": "12345678910",
          "organisasjonsnummer": "123456789",
          "hendelser": [
            "56a20c00-51c5-4e0b-9136-e0ba0b329041",
            "8af1e0a9-5178-4f62-b2a0-7cbce6acfc07"
          ],
          "utbetalt": [
            {
              "mottaker": "123456789",
              "fagområde": "SPREF",
              "fagsystemId": "V7E6ZHOCKJDNZNQMCAU23MQ47A",
              "totalbeløp": 0,
              "utbetalingslinjer": []
            },
            {
              "mottaker": "12345678910",
              "fagområde": "SP",
              "fagsystemId": "HHJX3CRNDVCXZPHGJ7HNMGHT3M",
              "totalbeløp": 0,
              "utbetalingslinjer": []
            }
          ],
          "ikkeUtbetalteDager": [],
          "fom": "2020-05-16",
          "tom": "2020-05-17",
          "forbrukteSykedager": 200,
          "automatiskBehandling": false,
          "godkjentAv": "$godkjentAv",
          "gjenståendeSykedager": 48,
          "maksdato": null,
          "opprettet": "2020-05-19T23:22:53.123929",
          "sykepengegrunnlag": 1337.69,
          "system_read_count": 1,
          "system_participating_services": [
            {
              "service": "spleis",
              "instance": "spleis-5859fd6594-nlcrp",
              "time": "2020-05-20T06:36:02.865565"
            },
            {
              "service": "spre-gosys",
              "instance": "spre-gosys-6c67d7998f-5mxlr",
              "time": "2020-05-20T09:13:29.880501"
            }
          ],
          "@event_name": "utbetalt",
          "@id": "efad5041-23de-49ff-85bb-7f9e8d59c77a",
          "@opprettet": "2020-05-20T06:36:02.865575",
          "@forårsaket_av": {
            "event_name": "behov",
            "id": "0ae18ef0-84a8-417b-bcb5-d1953c1b59cb",
            "opprettet": "2020-05-19T23:22:53.134056"
          }
        }
    """

    @Language("JSON")
    private fun vedtakFlereUtbetalinger() = """
        {
          "aktørId": "1000012345678",
          "fødselsnummer": "12345678910",
          "organisasjonsnummer": "123456789",
          "hendelser": [
            "56a20c00-51c5-4e0b-9136-e0ba0b329041",
            "8af1e0a9-5178-4f62-b2a0-7cbce6acfc07"
          ],
          "utbetalt": [
            {
              "mottaker": "123456789",
              "fagområde": "SPREF",
              "fagsystemId": "V7E6ZHOCKJDNZNQMCAU23MQ47A",
              "totalbeløp": 15000,
              "utbetalingslinjer": [
                {
                    "fom": "2020-05-11",
                    "tom": "2020-05-20",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 100.0,
                    "sykedager": 15
                },
                {
                    "fom": "2020-05-21",
                    "tom": "2020-05-30",
                    "dagsats": 1431,
                    "beløp": 1431,
                    "grad": 50.0,
                    "sykedager": 15
                }
              ]
            },
            {
              "mottaker": "12345678910",
              "fagområde": "SP",
              "fagsystemId": "HHJX3CRNDVCXZPHGJ7HNMGHT3M",
              "totalbeløp": 0,
              "utbetalingslinjer": []
            }
          ],
          "ikkeUtbetalteDager": [],
          "fom": "2020-05-16",
          "tom": "2020-05-17",
          "forbrukteSykedager": 200,
          "gjenståendeSykedager": 48,
          "automatiskBehandling": false,
          "godkjentAv": "$godkjentAv",
          "maksdato": "2020-07-17",
          "sykepengegrunnlag": 420.69,
          "opprettet": "2020-05-19T23:22:53.123929",
          "system_read_count": 1,
          "system_participating_services": [
            {
              "service": "spleis",
              "instance": "spleis-5859fd6594-nlcrp",
              "time": "2020-05-20T06:36:02.865565"
            },
            {
              "service": "spre-gosys",
              "instance": "spre-gosys-6c67d7998f-5mxlr",
              "time": "2020-05-20T09:13:29.880501"
            }
          ],
          "@event_name": "utbetalt",
          "@id": "efad5041-23de-49ff-85bb-7f9e8d59c77a",
          "@opprettet": "2020-05-20T06:36:02.865575",
          "@forårsaket_av": {
            "event_name": "behov",
            "id": "0ae18ef0-84a8-417b-bcb5-d1953c1b59cb",
            "opprettet": "2020-05-19T23:22:53.134056"
          }
        }
    """

    @Language("JSON")
    private fun vedtakIkkeUtbetalteDager() = """
{
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
                    "fom": "2020-05-16",
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
    "ikkeUtbetalteDager": [
        {
            "dato": "2020-05-11",
            "type": "MinimumSykdomsgrad"
        },
        {
            "dato": "2020-05-12",
            "type": "MinimumSykdomsgrad"
        },
        {
            "dato": "2020-05-13",
            "type": "MinimumSykdomsgrad"
        },
        {
            "dato": "2020-05-14",
            "type": "MinimumSykdomsgrad"
        },
        {
            "dato": "2020-05-15",
            "type": "MinimumSykdomsgrad"
        }
    ],
    "fom": "2020-05-11",
    "tom": "2020-05-30",
    "forbrukteSykedager": 15,
    "automatiskBehandling": false,
    "godkjentAv": "$godkjentAv",
    "gjenståendeSykedager": 233,
    "sykepengegrunnlag": 12345.0,
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

    @Language("JSON")
    private fun vedtakMedGjenopptattArbeid() = """{
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
                    "fom": "2020-05-16",
                    "tom": "2020-05-19",
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
    "ikkeUtbetalteDager": [
        {
            "dato": "2020-05-20",
            "type": "Arbeidsdag"
        },
        {
            "dato": "2020-05-21",
            "type": "Arbeidsdag"
        },
        {
            "dato": "2020-05-22",
            "type": "Arbeidsdag"
        },
        {
            "dato": "2020-05-23",
            "type": "Fridag"
        },
        {
            "dato": "2020-05-24",
            "type": "Fridag"
        },
        {
            "dato": "2020-05-25",
            "type": "Arbeidsdag"
        }
    ],
    "fom": "2020-05-11",
    "tom": "2020-05-25",
    "forbrukteSykedager": 15,
    "automatiskBehandling": false,
    "godkjentAv": "$godkjentAv",
    "gjenståendeSykedager": 233,
    "sykepengegrunnlag": 12345.0,
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
