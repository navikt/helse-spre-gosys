package no.nav.helse

import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class BeOmInntektsmeldingerTest {

    private val testRapid = TestRapid()
    private val mockproducer: KafkaProducer<String, TrengerInntektsmeldingDTO> = mockk(relaxed = true)
    private val fnr = "12345678910"
    private val orgnr = "987654321"
    private val opprettet = LocalDateTime.now()

    init {
        BeOmInntektsmeldinger(testRapid, mockproducer)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `event genererer request for inntektsmelding`() {
        testRapid.sendTestMessage(eventSomJson())
        testRapid.sendTestMessage(eventSomJson())

        verify(exactly = 2) { mockproducer.send(
            ProducerRecord("aapen-helse-spre-arbeidsgiver", fnr, TrengerInntektsmeldingDTO(
                organisasjonsnummer = orgnr,
                fødselsnummer = fnr,
                fom = LocalDate.now(),
                tom = LocalDate.now(),
                opprettet = opprettet
            )))
        }
        assertEquals(2, testRapid.inspektør.size)
    }

    @Test
    fun `event uten event_name blir ignorert`() {
        testRapid.sendTestMessage(JsonMessage.newMessage(mapOf(
            "vedtaksperiodeId" to UUID.randomUUID(),
            "fødselsnummer" to fnr,
            "organisasjonsnummer" to orgnr,
            "@opprettet" to opprettet,
            "fom" to LocalDate.now(),
            "tom" to LocalDate.now()
        )).toJson())
        verify(exactly = 0) { mockproducer.send(any()) }
    }

    private fun eventSomJson(): String {
        return JsonMessage.newMessage(
            mapOf(
                "@event_name" to "trenger_inntektsmelding",
                "vedtaksperiodeId" to UUID.randomUUID(),
                "fødselsnummer" to fnr,
                "organisasjonsnummer" to orgnr,
                "@opprettet" to opprettet,
                "fom" to LocalDate.now(),
                "tom" to LocalDate.now()
            )
        ).toJson()
    }
}
