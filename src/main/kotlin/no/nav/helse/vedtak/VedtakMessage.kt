package no.nav.helse.vedtak

import no.nav.helse.io.IO
import no.nav.helse.log
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt

data class VedtakMessage private constructor(
    val hendelseId: UUID,
    val fødselsnummer: String,
    val aktørId: String,
    private val opprettet: LocalDateTime,
    private val fom: LocalDate,
    private val tom: LocalDate,
    private val organisasjonsnummer: String,
    private val gjenståendeSykedager: Int,
    private val automatiskBehandling: Boolean,
    private val godkjentAv: String,
    private val maksdato: LocalDate?,
    private val sykepengegrunnlag: Double,
    private val utbetaling: Utbetaling,
    private val ikkeUtbetalteDager: List<IkkeUtbetaltDag>
) {
    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    constructor(packet: JsonMessage) :
        this(
            hendelseId = UUID.fromString(packet["@id"].asText()),
            opprettet = packet["@opprettet"].asLocalDateTime(),
            fødselsnummer = packet["fødselsnummer"].asText(),
            aktørId = packet["aktørId"].asText(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText(),
            gjenståendeSykedager = packet["gjenståendeSykedager"].asInt(),
            automatiskBehandling = packet["automatiskBehandling"].asBoolean(),
            godkjentAv = packet["godkjentAv"].asText(),
            maksdato = packet["maksdato"].asOptionalLocalDate(),
            sykepengegrunnlag = packet["sykepengegrunnlag"].asDouble(),
            utbetaling = packet["utbetalt"].find { it["fagområde"].asText() == "SPREF" }!!.let { utbetaling ->
                Utbetaling(
                    fagområde = Utbetaling.Fagområde.SPREF,
                    fagsystemId = utbetaling["fagsystemId"].asText(),
                    totalbeløp = utbetaling["totalbeløp"].asInt(),
                    utbetalingslinjer = utbetaling["utbetalingslinjer"].map { utbetalingslinje ->
                        Utbetaling.Utbetalingslinje(
                            dagsats = utbetalingslinje["dagsats"].asInt(),
                            fom = utbetalingslinje["fom"].asLocalDate(),
                            tom = utbetalingslinje["tom"].asLocalDate(),
                            grad = utbetalingslinje["grad"].asInt(),
                            beløp = utbetalingslinje["beløp"].asInt(),
                            mottaker = "arbeidsgiver"
                        )
                    }
                )
            },
            ikkeUtbetalteDager = packet["ikkeUtbetalteDager"].map { dag ->
                IkkeUtbetaltDag(
                    dato = dag["dato"].asLocalDate(),
                    type = dag["type"].asText()
                )
            }
        )

    constructor(vedtak: IO.Vedtak) :
        this(
            hendelseId = vedtak.`@id`,
            opprettet = vedtak.`@opprettet`,
            fødselsnummer = vedtak.fødselsnummer,
            aktørId = vedtak.aktørId,
            fom = vedtak.fom,
            tom = vedtak.tom,
            organisasjonsnummer = vedtak.organisasjonsnummer,
            gjenståendeSykedager = vedtak.gjenståendeSykedager,
            automatiskBehandling = vedtak.automatiskBehandling,
            godkjentAv = vedtak.godkjentAv,
            maksdato = vedtak.maksdato,
            sykepengegrunnlag = vedtak.sykepengegrunnlag,
            utbetaling = Utbetaling(vedtak.utbetalt.find { it.fagområde == IO.Fagområde.SPREF }!!),
            ikkeUtbetalteDager = vedtak.ikkeUtbetalteDager.map { IkkeUtbetaltDag(it) }
        )

    internal fun toVedtakPdfPayload() = VedtakPdfPayload(
        fagsystemId = utbetaling.fagsystemId,
        totaltTilUtbetaling = utbetaling.totalbeløp,
        linjer = utbetaling.utbetalingslinjer.map {
            VedtakPdfPayload.Linje(
                fom = it.fom,
                tom = it.tom,
                grad = it.grad,
                beløp = it.beløp,
                mottaker = it.mottaker
            )
        },
        dagsats = utbetaling.utbetalingslinjer.takeIf { it.isNotEmpty() }?.first()?.dagsats,
        fødselsnummer = fødselsnummer,
        fom = fom,
        tom = tom,
        behandlingsdato = opprettet.toLocalDate(),
        organisasjonsnummer = organisasjonsnummer,
        dagerIgjen = gjenståendeSykedager,
        automatiskBehandling = automatiskBehandling,
        godkjentAv = godkjentAv,
        maksdato = maksdato,
        sykepengegrunnlag = sykepengegrunnlag,
        ikkeUtbetalteDager = ikkeUtbetalteDager
            .settSammenIkkeUtbetalteDager()
            .map {
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
    )

    internal data class DagAcc(
        val fom: LocalDate,
        var tom: LocalDate,
        val type: String
    )

    data class Utbetaling(
        val fagområde: Fagområde,
        val fagsystemId: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<Utbetalingslinje>
    ) {
        constructor(rådata: IO.Utbetaling) :
            this (
                fagområde= Fagområde.valueOf(rådata.fagområde),
                fagsystemId=rådata.fagsystemId,
                totalbeløp=rådata.totalbeløp,
                utbetalingslinjer=rådata.utbetalingslinjer.map { Utbetalingslinje(it) }
            )

        enum class Fagområde {
            SPREF;

            companion object {
                fun valueOf(fagområde: IO.Fagområde): Fagområde {
                    if(fagområde == IO.Fagområde.SPREF) return SPREF
                    throw RuntimeException("Fagområde $fagområde finnes ikke.")
                }
            }
        }

        data class Utbetalingslinje(
            val dagsats: Int,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Int,
            val beløp: Int,
            val mottaker: String
        ) {
            constructor(rådata: IO.Utbetalingslinje) :
                this(
                    dagsats=rådata.dagsats,
                    fom=rådata.fom,
                    tom=rådata.tom,
                    grad=rådata.grad.roundToInt(),
                    beløp=rådata.beløp,
                    mottaker="arbeidsgiver"
                )
        }
    }

    data class IkkeUtbetaltDag(
        val dato: LocalDate,
        val type: String
    ) {
        constructor(rådata: IO.IkkeUtbetaltDag) :
            this(
                dato=rådata.dato,
                type=rådata.type
            )
    }
}

internal fun Iterable<VedtakMessage.IkkeUtbetaltDag>.settSammenIkkeUtbetalteDager(): List<VedtakMessage.DagAcc> =
    map { VedtakMessage.DagAcc(it.dato, it.dato, it.type) }
        .fold(listOf()) { akkumulator, avvistDag ->
            val sisteInnslag = akkumulator.lastOrNull()
            if (sisteInnslag != null
                && (sisteInnslag.type == avvistDag.type || (sisteInnslag.type == "Arbeidsdag") && avvistDag.type == "Fridag")
                && sisteInnslag.tom.plusDays(1) == avvistDag.tom
            ) {
                sisteInnslag.tom = avvistDag.tom
                return@fold akkumulator
            }
            akkumulator + avvistDag
        }
