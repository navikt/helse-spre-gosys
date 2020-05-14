package no.nav.helse

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.*
import java.time.format.DateTimeFormatter
import java.util.*

class OpprettJournalpost(
    rapidsConnection: RapidsConnection,
    private val joarkClient: JoarkClient,
    private val pdfClient: PdfClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey("fødselsnummer", "aktørId", "@id", "organisasjonsnummer", "gjenståendeSykedager")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        log.info("Oppdaget utbetaling")
        val fnr = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val fom = packet["fom"].asLocalDate()
        val tom = packet["tom"].asLocalDate()
        val hendelseId = UUID.fromString(packet["@id"].asText())

        runBlocking {
            val pdf = pdfClient.hentPdf(packet.toPayload()).toPdfString()
            val journalpostPayload = JournalpostPayload(
                tittel = "Vedtak om sykepenger",
                bruker = Bruker(id = fnr),
                dokumenter = listOf(
                    Dokument(
                        tittel = "Sykepenger utbetalt i ny løsning ${formatter.format(fom)}-${formatter.format(tom)}",
                        dokumentvarianter = listOf(DokumentVariant(fysiskDokument = pdf))
                    )
                )
            )
            joarkClient.opprettJournalpost(hendelseId, journalpostPayload).let { status ->
                if (status) log.info("Vedtak journalført på aktør: $aktørId")
                else log.warn("Feil oppstod under journalføring av vedtak")
            }
        }
    }
}

private fun JsonMessage.toPayload() = Payload(
    navn = "",
    fødselsnummer = this["fødselsnummer"].asText(),
    fagsystemId = "",
    fom = this["fom"].asLocalDate(),
    tom = this["tom"].asLocalDate(),
    grad = /* hente fra linjer */ 42,
    behandlingsdato = this["@opprettet"].asLocalDateTime().toLocalDate(),
    saksbehandlernavn = "", // Hente dette fra noe sted?
    arbeidsgiver = this["organisasjonsnummer"].asText(),
    sykepengegrunnlag =  /* hente fra linjer (beløp-feltet?) */ 42,
    avvik = /* hente fra linjer */ 42,
    opptjeningsdager = /* mangler i event */ null,
    dagerIgjen = this["gjenståendeSykedager"].asInt(),
    utbetaling = /* hente fra linjer */ 42
)

private fun ByteArray.toPdfString() = Base64.getEncoder().encodeToString(this)

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
