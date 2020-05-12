package no.nav.helse

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

class Vedtak(
    private val fnr: String,
    val aktørId: String,
    private val fom: LocalDate,
    private val tom: LocalDate,
    val hendelseId: UUID
) {

    private fun pdfAsBase64(): String {
        return Base64.getEncoder().encodeToString("Test".toByteArray())
    }

    fun toJournalpostPayload() = JournalpostPayload(
        tittel = "Vedtak om sykepenger",
        bruker = Bruker(id = fnr),
        dokumenter = listOf(
            Dokument(
                tittel = "Vedtak på sykepenger ${formatter.format(fom)}-${formatter.format(tom)}",
                dokumentvarianter = listOf(DokumentVariant(fysiskDokument = pdfAsBase64()))
            )
        )
    )
}
