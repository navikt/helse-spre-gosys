package no.nav.helse.vedtak

import no.nav.helse.io.IO
import no.nav.helse.log
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import java.time.LocalDate
import java.util.*

data class VedtakPdfPayload(
    val fødselsnummer: String,
    val fagsystemId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val linjer: List<Linje>,
    val organisasjonsnummer: String,
    val behandlingsdato: LocalDate,
    val dagerIgjen: Int,
    val automatiskBehandling: Boolean,
    val godkjentAv: String,
    val totaltTilUtbetaling: Int,
    val ikkeUtbetalteDager: List<IkkeUtbetalteDager>,
    val dagsats: Int?,
    val maksdato: LocalDate?,
    val sykepengegrunnlag: Double
) {

    companion object {
        fun tilPdfPayload(packet: JsonMessage): VedtakPdfPayload {
            val utbetaling = packet["utbetalt"].find { it["fagområde"].asText() == "SPREF" }!!
            return VedtakPdfPayload(
                fagsystemId = utbetaling["fagsystemId"].asText(),
                totaltTilUtbetaling = utbetaling["totalbeløp"].asInt(),
                linjer = utbetaling["utbetalingslinjer"].map { utbetalingslinje ->
                    Linje(
                        fom = utbetalingslinje["fom"].asLocalDate(),
                        tom = utbetalingslinje["tom"].asLocalDate(),
                        grad = utbetalingslinje["grad"].asInt(),
                        beløp = utbetalingslinje["beløp"].asInt(),
                        mottaker = "arbeidsgiver"
                    )
                },
                dagsats = utbetaling["utbetalingslinjer"].firstOrNull()?.get("dagsats")?.asInt(),
                fødselsnummer = packet["fødselsnummer"].asText(),
                fom = packet["fom"].asLocalDate(),
                tom = packet["tom"].asLocalDate(),
                behandlingsdato = packet["@opprettet"].asLocalDateTime().toLocalDate(),
                organisasjonsnummer = packet["organisasjonsnummer"].asText(),
                dagerIgjen = packet["gjenståendeSykedager"].asInt(),
                automatiskBehandling = packet["automatiskBehandling"].asBoolean(),
                godkjentAv = packet["godkjentAv"].asText(),
                maksdato = packet["maksdato"].asOptionalLocalDate(),
                sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
                ikkeUtbetalteDager = packet["ikkeUtbetalteDager"].map { dag ->
                    IkkeUtbetaltDag(
                        dato = dag["dato"].asLocalDate(),
                        type = dag["type"].asText()
                    )
                }.begrunnDager()
            )
        }

        fun tilPdfPayload(vedtak: IO.Vedtak): VedtakPdfPayload {
            val utbetaling = vedtak.utbetalt.find { it.fagområde == IO.Fagområde.SPREF }!!

            return VedtakPdfPayload(
                fagsystemId = utbetaling.fagsystemId,
                totaltTilUtbetaling = utbetaling.totalbeløp,
                dagsats = utbetaling.utbetalingslinjer.firstOrNull()?.dagsats,
                linjer = utbetaling.utbetalingslinjer.map { utbetalingslinje ->
                    Linje(
                        fom = utbetalingslinje.fom,
                        tom = utbetalingslinje.tom,
                        grad = utbetalingslinje.grad.toInt(),
                        beløp = utbetalingslinje.beløp,
                        mottaker = "arbeidsgiver"
                    )
                },
                behandlingsdato = vedtak.`@opprettet`.toLocalDate(),
                fødselsnummer = vedtak.fødselsnummer,
                fom = vedtak.fom,
                tom = vedtak.tom,
                organisasjonsnummer = vedtak.organisasjonsnummer,
                dagerIgjen = vedtak.gjenståendeSykedager,
                automatiskBehandling = vedtak.automatiskBehandling,
                godkjentAv = vedtak.godkjentAv,
                maksdato = vedtak.maksdato,
                sykepengegrunnlag = vedtak.sykepengegrunnlag,
                ikkeUtbetalteDager = vedtak.ikkeUtbetalteDager.map { IkkeUtbetaltDag(it) }
                    .begrunnDager()
            )
        }

    }

    data class Linje(
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Int,
        val beløp: Int,
        val mottaker: String
    )

    data class IkkeUtbetalteDager(
        val fom: LocalDate,
        val tom: LocalDate,
        val grunn: String
    )

    data class IkkeUtbetaltDag(
        val dato: LocalDate,
        val type: String
    ) {
        constructor(rådata: IO.IkkeUtbetaltDag) :
            this(
                dato = rådata.dato,
                type = rådata.type
            )
    }

    internal data class DagAcc(
        val fom: LocalDate,
        var tom: LocalDate,
        val type: String
    )
}

internal fun Iterable<VedtakPdfPayload.IkkeUtbetaltDag>.begrunnDager() =
    settSammenIkkeUtbetalteDager().map {
            VedtakPdfPayload.IkkeUtbetalteDager(
                fom = it.fom, tom = it.tom, grunn = when (it.type) {
                    "SykepengedagerOppbrukt" -> "Dager etter maksdato"
                    "MinimumInntekt" -> "Inntektsgrunnlag under 1/2 G"
                    "EgenmeldingUtenforArbeidsgiverperiode" -> "Egenmelding etter arbeidsgiverperioden"
                    "MinimumSykdomsgrad" -> "Sykdomsgrad under 20%"
                    "Fridag" -> "Ferie/Permisjon"
                    "Arbeidsdag" -> "Arbeidsdag"
                    "EtterDødsdato" -> "Personen er død"
                    else -> {
                        log.error("Ukjent dagtype $it")
                        "Ukjent dagtype: \"${it.type}\""
                    }
                }
            )
        }

internal fun Iterable<VedtakPdfPayload.IkkeUtbetaltDag>.settSammenIkkeUtbetalteDager() =
    map { VedtakPdfPayload.DagAcc(it.dato, it.dato, it.type) }
        .fold(listOf<VedtakPdfPayload.DagAcc>(), { acc, value ->
            if (acc.isNotEmpty()
                && (acc.last().type == value.type || (acc.last().type == "Arbeidsdag") && value.type == "Fridag")
                && acc.last().tom.plusDays(1) == value.tom
            ) {
                acc.last().tom = value.tom
                return@fold acc
            }
            acc + value
        })
