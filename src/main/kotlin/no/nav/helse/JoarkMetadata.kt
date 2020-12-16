package no.nav.helse

import no.nav.helse.io.IO
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

data class JoarkMetadata private constructor(
    val hendelseId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    private val fom: LocalDate,
    private val tom: LocalDate,
) {

    constructor(packet: JsonMessage) :
        this(
            hendelseId = UUID.fromString(packet["@id"].asText()),
            fødselsnummer = packet["fødselsnummer"].asText(),
            aktørId = packet["aktørId"].asText(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
        )

    constructor(vedtak: IO.Vedtak) :
        this(
            hendelseId = vedtak.`@id`,
            fødselsnummer = vedtak.fødselsnummer,
            aktørId = vedtak.aktørId,
            fom = vedtak.fom,
            tom = vedtak.tom,
        )

    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)
}
