package no.nav.helse

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import no.nav.helse.vedtak.VedtakPdfPayload
import java.util.Base64

class PdfClient(private val httpClient: HttpClient) {
    suspend fun hentVedtakPdf(vedtak: VedtakPdfPayload) =
        httpClient.post<ByteArray>("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/vedtak") {
            contentType(Json)
            body = vedtak
        }.toPdfString()

    private fun ByteArray.toPdfString() = Base64.getEncoder().encodeToString(this)
}
