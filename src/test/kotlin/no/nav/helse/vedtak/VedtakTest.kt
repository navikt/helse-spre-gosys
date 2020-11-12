package no.nav.helse.vedtak

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helse.io.IO
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class VedtakTest {

    @Test
    fun `serialisering og deserialisering av vedtakjson`() {
        val format = Json { prettyPrint = true }
        val serialisert: List<IO.Vedtak> = format.decodeFromString(vedtakMessage)
        val deserialisert: String = format.encodeToString(serialisert)

        JSONAssert.assertEquals(vedtakMessage, deserialisert, true)
    }

    @Language("json")
    private val vedtakMessage: String = """
    [{
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
        "ikkeUtbetalteDager": [
              {
                "dato": "2020-10-21",
                "type": "Fridag"
              },
              {
                "dato": "2020-10-22",
                "type": "Fridag"
              },
              {
                "dato": "2020-10-23",
                "type": "Fridag"
              }
        ],
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
    }]
    """
}
