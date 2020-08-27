package no.nav.helse.annullering

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import java.time.LocalDate
import java.util.UUID

class AnnulleringMessage private constructor(
    val hendelseId: UUID,
    val fødselsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val aktørId: String,
    private val organisasjonsnummer: String,
    private val fagsystemId: String,
    private val saksbehandlerId: String,
    private val dato: LocalDate,
    private val linjer: List<Linje>
) {
    constructor(packet: JsonMessage) :
        this(
            hendelseId = UUID.fromString(packet["@id"].asText()),
            fødselsnummer = packet["fødselsnummer"].asText(),
            aktørId = packet["aktørId"].asText(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            fagsystemId = packet["fagsystemId"].asText(),
            fom = requireNotNull(packet.utbetalingslinjer().map { it.fom }.min()),
            tom = requireNotNull(packet.utbetalingslinjer().map { it.tom }.max()),
            saksbehandlerId = packet["saksbehandlerEpost"].asText(),
            dato = packet["annullertAvSaksbehandler"].asLocalDateTime().toLocalDate(),
            linjer = packet.utbetalingslinjer()
        )

    internal fun toPdfPayload() = AnnulleringPdfPayload(
        fødselsnummer = fødselsnummer,
        fagsystemId = fagsystemId,
        fom = fom,
        tom = tom,
        organisasjonsnummer = organisasjonsnummer,
        saksbehandlerId = saksbehandlerId,
        dato = dato,
        linjer = linjer.map {
            AnnulleringPdfPayload.Linje(
                fom = it.fom,
                tom = it.tom,
                grad = it.grad,
                beløp = it.beløp
            )
        }
    )

    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val beløp: Int
    )
}

private fun JsonMessage.utbetalingslinjer() = this["utbetalingslinjer"].map {
    AnnulleringMessage.Linje(
        fom = it["fom"].asLocalDate(),
        tom = it["tom"].asLocalDate(),
        grad = it["grad"].asInt(),
        beløp = it["beløp"].asInt()
    )
}
