package no.nav.helse.annullering

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.log
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
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
                    "saksbehandlerId"
                )
                it.requireArray("linjer")
                {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("grad", "beløp")
                }
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("dato", JsonNode::asLocalDate)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        log.info("Oppdaget annullering-event {}", StructuredArguments.keyValue("id", packet["@id"].asText()))
        sikkerLogg.info(packet.toJson())

        val annulleringMessage = AnnulleringMessage(packet)
        annulleringMediator.opprettAnnullering(annulleringMessage)
    }
}
