package no.nav.helse

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

class OpprettJournalpost(
    rapidsConnection: RapidsConnection,
    private val joarkClient: JoarkClient,
    private val pdfClient: PdfClient
) : River.PacketListener {
    private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")

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
                    "utbetalt",
                    "sykepengegrunnlag"
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        log.info("Oppdaget utbetalingevent {}", keyValue("id", hendelseId))
        sikkerLogg.info(packet.toJson())

        val fnr = packet["fødselsnummer"].asText()
        val aktørId = packet["aktørId"].asText()
        val utbetalingsperioder = packet["utbetalt"]
            .find { it["fagområde"].asText() == "SPREF" }
            ?.path("utbetalingslinjer")
            ?.map { "${formatter.format(it["fom"].asLocalDate())} - ${formatter.format(it["tom"].asLocalDate())}" }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "utbetalte perioder: ")
            ?: "ingen utbetalingsperioder"

        runBlocking {
            val pdf = pdfClient.hentPdf(packet.toPayload()).toPdfString()
            val journalpostPayload = JournalpostPayload(
                tittel = "Vedtak om sykepenger",
                bruker = Bruker(id = fnr),
                dokumenter = listOf(
                    Dokument(
                        tittel = "Sykepenger behandlet i ny løsning, $utbetalingsperioder",
                        dokumentvarianter = listOf(DokumentVariant(fysiskDokument = pdf))
                    )
                )
            )
            joarkClient.opprettJournalpost(hendelseId, journalpostPayload).let { status ->
                if (status) log.info("Vedtak journalført på aktør: $aktørId")
                else log.warn("Feil oppstod under journalføring av vedtak. Status fra Joark: ")
            }
        }
    }
}

private fun JsonMessage.toPayload(): PdfPayload {
    val arbeidsgiverUtbetaling = this["utbetalt"].find { it["fagområde"].asText() == "SPREF" }!!
    val totaltTilUtbetaling = arbeidsgiverUtbetaling["totalbeløp"].asInt()
    val dagsats = arbeidsgiverUtbetaling["utbetalingslinjer"][0]?.get("dagsats")?.asInt()
    val arbeidsgiverutbetalingslinjer = arbeidsgiverUtbetaling["utbetalingslinjer"].map {
        Linje(
            fom = it["fom"].asLocalDate(),
            tom = it["tom"].asLocalDate(),
            grad = it["grad"].asInt(),
            beløp = it["beløp"].asInt(),
            mottaker = "arbeidsgiver"
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
        dagsats = dagsats,
        linjer = arbeidsgiverutbetalingslinjer,
        sykepengegrunnlag = this["sykepengegrunnlag"].asDouble()
    )
}

private fun ByteArray.toPdfString() = Base64.getEncoder().encodeToString(this)

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
