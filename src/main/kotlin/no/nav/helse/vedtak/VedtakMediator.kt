package no.nav.helse.vedtak

import kotlinx.coroutines.runBlocking
import no.nav.helse.*
import no.nav.helse.log

class VedtakMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient
){
    internal fun opprettVedtak(pdfPayload: VedtakPdfPayload, metadata: JoarkMetadata) {
        runBlocking {
            val pdf = pdfClient.hentVedtakPdf(pdfPayload)
            val journalpostPayload = JournalpostPayload(
                tittel = "Vedtak om sykepenger",
                bruker = JournalpostPayload.Bruker(id = metadata.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = "Sykepenger behandlet i ny løsning, ${metadata.norskFom} - ${metadata.norskTom}",
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                )
            )
            joarkClient.opprettJournalpost(metadata.hendelseId, journalpostPayload).let { success ->
                if (success) log.info("Vedtak journalført for aktør: ${metadata.aktørId}")
                else log.warn("Feil oppstod under journalføring av vedtak")
            }
        }
    }
}
