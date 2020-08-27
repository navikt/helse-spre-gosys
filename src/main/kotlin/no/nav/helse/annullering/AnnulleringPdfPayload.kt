package no.nav.helse.annullering

import java.time.LocalDate

data class AnnulleringPdfPayload(
    val fødselsnummer: String,
    val fagsystemId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val organisasjonsnummer: String,
    val saksbehandlerId: String,
    val dato: LocalDate,
    val linjer: List<Linje>
) {
    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val beløp: Int
    )
}
