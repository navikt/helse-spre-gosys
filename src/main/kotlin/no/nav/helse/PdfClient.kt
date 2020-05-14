package no.nav.helse

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import java.time.LocalDate

class PdfClient(
    private val httpClient: HttpClient
) {
    suspend fun hentPdf(vedtak: Payload): ByteArray =
        httpClient.post("http://spre-gosys-pdf/api/v1/genpdf/gosys-pdf/vedtak") {
            contentType(Json)
            body = vedtak
        }
}

data class Payload(
    val navn: String,
    val fødselsnummer: String,
    val fagsystemId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val behandlingsdato: LocalDate,
    val saksbehandlernavn: String,
    val arbeidsgiver: String,
    val sykepengegrunnlag: Int,
    val avvik: Int,
    val opptjeningsdager: Int?,
    val dagerIgjen: Int,
    val utbetaling: Int
)
