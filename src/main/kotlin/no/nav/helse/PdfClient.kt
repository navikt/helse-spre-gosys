package no.nav.helse

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import java.time.LocalDate

class PdfClient(
    private val httpClient: HttpClient
) {
    suspend fun hentPdf(vedtak: PdfPayload): ByteArray =
        httpClient.post("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/vedtak") {
            contentType(Json)
            body = vedtak
        }
}

data class PdfPayload(
    val fødselsnummer: String,
    val fagsystemId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val linjer: List<Linje>,
    val organisasjonsnummer: String,
    val behandlingsdato: LocalDate,
    val dagerIgjen: Int,
    val totaltTilUtbetaling: Int,
    val ikkeUtbetalteDager: List<IkkeUtbetalteDager>,
    val dagsats: Int?,
    val sykepengegrunnlag: Double
)

data class Linje(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val beløp: Int,
    val mottaker: String
)

data class IkkeUtbetalteDager(
    val fom: LocalDate,
    val tom: LocalDate,
    val grunn: String
)
