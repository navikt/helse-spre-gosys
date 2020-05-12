package no.nav.helse

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class JoarkClient(
    private val baseUrl: String,
    private val stsRestClient: StsRestClient,
    private val httpClient: HttpClient
) {
    suspend fun opprettJournalpost(vedtak: Vedtak): Boolean {
        return httpClient.post<HttpStatement>("$baseUrl/rest/journalpostapi/v1/journalpost") {
            header("Nav-Consumer-Token", vedtak.hendelseId.toString())
            header("Authorization", "Bearer ${stsRestClient.token()}")
            contentType(ContentType.Application.Json)
            body = vedtak.toJournalpostPayload()
        }
            .execute {
                it.status == HttpStatusCode.OK
            }

    }
}

data class JournalpostPayload(
    val tittel: String,
    val journalpostType: String = "NOTAT",
    val tema: String = "SYK",
    val behandlingstema: String = "ab0061",
    val journalfoerendeEnhet: String = "9999",
    val bruker: Bruker,
    val sak: Sak = Sak(),
    val dokumenter: List<Dokument>
)

data class Bruker(
    val id: String,
    val idType: String = "FNR"
)

data class Sak(
    val sakstype: String = "GENERELL_SAK"
)

data class Dokument(
    val tittel: String,
    val dokumentvarianter: List<DokumentVariant>

)

data class DokumentVariant(
    val filtype: String = "PDFA",
    val fysiskDokument: String,
    val variantformat: String = "ARKIV"
)
