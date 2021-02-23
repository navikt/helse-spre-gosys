package no.nav.helse.annullering

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.log
import no.nav.helse.rapids_rivers.*
import no.nav.helse.sikkerLogg

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val annulleringMediator: AnnulleringMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetaling_annullert")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "aktørId",
                    "organisasjonsnummer",
                    "fagsystemId",
                    "saksbehandlerEpost"
                )
                it.require("annullertAvSaksbehandler", JsonNode::asLocalDateTime)
                it.requireArray("utbetalingslinjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("grad", "beløp")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("Oppdaget annullering-event {}", StructuredArguments.keyValue("id", packet["@id"].asText()))
        sikkerLogg.info(packet.toJson())

        val annulleringMessage = AnnulleringMessage(packet)
        annulleringMediator.opprettAnnullering(annulleringMessage)
    }
}
