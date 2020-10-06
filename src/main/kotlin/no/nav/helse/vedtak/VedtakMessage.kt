package no.nav.helse.vedtak

import no.nav.helse.log
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class VedtakMessage private constructor(
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
        enum class Fagområde {
            SPREF
        }

        data class Utbetalingslinje(
            val dagsats: Int,
            val fom: LocalDate,
            val tom: LocalDate,
            val grad: Int,
            val beløp: Int,
            val mottaker: String
        )
    }

    data class IkkeUtbetaltDag(
        val dato: LocalDate,
        val type: String
    )
}

internal fun Iterable<VedtakMessage.IkkeUtbetaltDag>.settSammenIkkeUtbetalteDager(): List<VedtakMessage.DagAcc> = this
    .map { VedtakMessage.DagAcc(it.dato, it.dato, it.type) }
    .fold(listOf()) { acc, value ->
        if (acc.isNotEmpty()
            && (acc.last().type == value.type || (acc.last().type == "Arbeidsdag") && value.type == "Fridag")
            && acc.last().tom.plusDays(1) == value.tom
        ) {
            acc.last().tom = value.tom
            return@fold acc
        }
        acc + value
    }

