package no.nav.helse.vedtak

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.log
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.sikkerLogg
import java.util.UUID

class VedtakRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakMediator: VedtakMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "aktørId",
                    "organisasjonsnummer",
                    "gjenståendeSykedager",
                    "utbetalt",
                    "ikkeUtbetalteDager",
                    "sykepengegrunnlag",
                    "automatiskBehandling",
                    "godkjentAv"
                )
                it.interestedIn("maksdato", JsonNode::asLocalDate)
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        log.info("Oppdaget utbetalingevent {}", keyValue("id", UUID.fromString(packet["@id"].asText())))
        sikkerLogg.info(packet.toJson())

        vedtakMediator.opprettVedtak(VedtakMessage(packet))
    }
}

