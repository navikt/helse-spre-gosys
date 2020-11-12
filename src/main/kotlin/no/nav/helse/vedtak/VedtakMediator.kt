package no.nav.helse.vedtak

import kotlinx.coroutines.runBlocking
import no.nav.helse.JoarkClient
import no.nav.helse.JournalpostPayload
import no.nav.helse.PdfClient
import no.nav.helse.log

class VedtakMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient
){
    internal fun opprettVedtak(vedtakMessage: VedtakMessage) {
        runBlocking {
            val pdf = pdfClient.hentVedtakPdf(vedtakMessage.toVedtakPdfPayload())
            val journalpostPayload = JournalpostPayload(
                tittel = "Vedtak om sykepenger",
                bruker = JournalpostPayload.Bruker(id = vedtakMessage.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = "Sykepenger behandlet i ny løsning, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}",
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                )
            )
            joarkClient.opprettJournalpost(vedtakMessage.hendelseId, journalpostPayload).let { success ->
                if (success) log.info("Vedtak journalført for aktør: ${vedtakMessage.aktørId}")
                else log.warn("Feil oppstod under journalføring av vedtak")
            }
        }
    }
}
