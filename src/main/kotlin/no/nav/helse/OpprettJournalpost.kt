package no.nav.helse

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

class OpprettJournalpost(
    rapidsConnection: RapidsConnection,
    private val joarkClient: JoarkClient,
    private val pdfClient: PdfClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.requireKey(
                    "fødselsnummer",
                    "aktørId",
                    "@id",
                    "organisasjonsnummer",
                    "gjenståendeSykedager",
                    "utbetalt"
                )
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

private fun JsonMessage.toPayload(): PdfPayload {
    val arbeidsgiverUtbetaling = this["utbetalt"].find { it["fagområde"].asText() == "SPREF" }!!
    val totaltTilUtbetaling = arbeidsgiverUtbetaling["totalbeløp"].asInt()
    val linjer = arbeidsgiverUtbetaling["utbetalingslinjer"].map {
        Linje(
            fom = it["fom"].asLocalDate(),
            tom = it["tom"].asLocalDate(),
            grad = it["grad"].asInt(),
            sykepengegrunnlag = it["beløp"].asInt() * 260
        )
    }
    return PdfPayload(
        fødselsnummer = this["fødselsnummer"].asText(),
        fagsystemId = arbeidsgiverUtbetaling["fagsystemId"].asText(),
        fom = this["fom"].asLocalDate(),
        tom = this["tom"].asLocalDate(),
        behandlingsdato = this["@opprettet"].asLocalDateTime().toLocalDate(),
        organisasjonsnummer = this["organisasjonsnummer"].asText(),
        dagerIgjen = this["gjenståendeSykedager"].asInt(),
        totaltTilUtbetaling = totaltTilUtbetaling,
        linjer = linjer
    )
}

private fun ByteArray.toPdfString() = Base64.getEncoder().encodeToString(this)

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
