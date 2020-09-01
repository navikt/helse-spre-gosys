package no.nav.helse.annullering

import kotlinx.coroutines.runBlocking
import no.nav.helse.JoarkClient
import no.nav.helse.JournalpostPayload
import no.nav.helse.PdfClient
import no.nav.helse.log

class AnnulleringMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient
) {
    fun opprettAnnullering(annulleringMessage: AnnulleringMessage) {
        runBlocking {
            val pdf = pdfClient.hentAnnulleringPdf(annulleringMessage.toPdfPayload())
            val journalpostPayload = JournalpostPayload(
                tittel = "Annullering av vedtak om sykepenger",
                bruker = JournalpostPayload.Bruker(id = annulleringMessage.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = "Utbetaling annullert i ny løsning ${annulleringMessage.norskFom} - ${annulleringMessage.norskTom}",
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                )
            )
            joarkClient.opprettJournalpost(annulleringMessage.hendelseId, journalpostPayload).let { success ->
                if (success) log.info("Annullering journalført for aktør: ${annulleringMessage.aktørId}")
                else log.warn("Feil oppstod under journalføring av annullering")
            }
        }
    }
}
