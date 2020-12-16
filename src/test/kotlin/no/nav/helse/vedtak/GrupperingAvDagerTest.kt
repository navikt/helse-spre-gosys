package no.nav.helse.vedtak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class GrupperingAvDagerTest {
    @Test
    fun `grupperer ikke utbetalte dager basert på type`() {
        val json =
            listOf(
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 20), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 21), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 22), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 23), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 24), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 25), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 26), "MinimumSykdomsgrad"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 27), "MinimumSykdomsgrad"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 28), "MinimumSykdomsgrad"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 29), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 30), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 7, 31), "Fridag"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 8, 4), "SykepengedagerOppbrukt"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 8, 5), "SykepengedagerOppbrukt"),
                VedtakPdfPayload.IkkeUtbetaltDag(LocalDate.of(2020, 8, 6), "SykepengedagerOppbrukt")
            ).settSammenIkkeUtbetalteDager()

        assertEquals(
            listOf(
                VedtakPdfPayload.DagAcc(LocalDate.of(2020, 7, 20), LocalDate.of(2020, 7, 25), "Fridag"),
                VedtakPdfPayload.DagAcc(LocalDate.of(2020, 7, 26), LocalDate.of(2020, 7, 28), "MinimumSykdomsgrad"),
                VedtakPdfPayload.DagAcc(LocalDate.of(2020, 7, 29), LocalDate.of(2020, 7, 31), "Fridag"),
                VedtakPdfPayload.DagAcc(LocalDate.of(2020, 8, 4), LocalDate.of(2020, 8, 6), "SykepengedagerOppbrukt")
            ), json
        )
    }
}
