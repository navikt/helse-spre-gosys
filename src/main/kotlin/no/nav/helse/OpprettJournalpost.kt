package no.nav.helse

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
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
                    "ikkeUtbetalteDager",
                    "sykepengegrunnlag"
                )
                it.interestedIn("maksdato", JsonNode::asLocalDate)
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
        val utbetalingsperioder = "${formatter.format(packet["fom"].asLocalDate())} - ${formatter.format(packet["tom"].asLocalDate())}"

        runBlocking {
            val pdf = pdfClient.hentPdf(packet.toPdfPayload()).toPdfString()
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

private fun JsonMessage.toPdfPayload(): PdfPayload {
    val arbeidsgiverUtbetaling = this["utbetalt"].find { it["fagområde"].asText() == "SPREF" }!!
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

    val ikkeUtbetalteDager = this["ikkeUtbetalteDager"]
        .settSammenIkkeUtbetalteDager()
        .map {
            IkkeUtbetalteDager(
                fom = it.fom, tom = it.tom, grunn = when (it.type) {
                    "SykepengedagerOppbrukt" -> "Dager etter maksdato"
                    "MinimumInntekt" -> "Inntektsgrunnlag under 1/2 G"
                    "EgenmeldingUtenforArbeidsgiverperiode" -> "Egenmelding etter arbeidsgiverperioden"
                    "MinimumSykdomsgrad" -> "Sykdomsgrad under 20%"
                    "Fridag" -> "Ferie/Permisjon"
                    else -> {
                        log.error("Ukjent dagtype $it")
                        "Ukjent dagtype: \"${it.type}\""
                    }
                }
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
        totaltTilUtbetaling = arbeidsgiverUtbetaling["totalbeløp"].asInt(),
        ikkeUtbetalteDager = ikkeUtbetalteDager,
        dagsats = dagsats,
        maksdato = this["maksdato"].asOptionalLocalDate(),
        linjer = arbeidsgiverutbetalingslinjer,
        sykepengegrunnlag = this["sykepengegrunnlag"].asDouble()
    )
}

internal data class DagAcc(
    val fom: LocalDate,
    var tom: LocalDate,
    val type: String
)

internal fun Iterable<JsonNode>.settSammenIkkeUtbetalteDager(): List<DagAcc> = this
    .map { DagAcc(it["dato"].asLocalDate(), it["dato"].asLocalDate(), it["type"].asText()) }
    .fold(listOf<DagAcc>()) { acc, value ->
        if (acc.isNotEmpty() && acc.last().type == value.type && acc.last().tom.plusDays(1) == value.tom) {
            acc.last().tom = value.tom
            return@fold acc
        }
        acc + value
    }

private fun ByteArray.toPdfString() = Base64.getEncoder().encodeToString(this)

private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
