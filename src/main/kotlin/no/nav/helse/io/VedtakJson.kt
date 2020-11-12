@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class, UUIDSerializer::class)

package no.nav.helse.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class IO {
    @Serializable
    data class Vedtak (
        val aktørId: String,
        val fødselsnummer: String,
        val organisasjonsnummer: String,
        val gjenståendeSykedager: Int,
        val hendelser: List<UUID>,
        val utbetalt: List<Utbetaling>,
        val ikkeUtbetalteDager: List<IkkeUtbetaltDag>,
        val fom: LocalDate,
        val tom: LocalDate,
        val forbrukteSykedager: Int,
        val godkjentAv: String,
        val automatiskBehandling: Boolean,
        val opprettet: LocalDateTime,
        val sykepengegrunnlag: Double,
        val månedsinntekt: Double,
        val maksdato: LocalDate,
        val system_read_count: Int,
        val system_participating_services: List<ParticipatingService>,
        val `@event_name` : String,
        val `@id`: UUID,
        val `@opprettet`: LocalDateTime,
        val `@forårsaket_av`: ForårsaketAv
    )

    @Serializable
    data class ParticipatingService (
        val service: String,
        val instance: String,
        val time: LocalDateTime
    )

    @Serializable
    data class ForårsaketAv (
        val event_name: String,
        val id: UUID,
        val opprettet: LocalDateTime
    )

    @Serializable
    data class Utbetaling(
        val mottaker: String,
        val fagområde: Fagområde,
        val fagsystemId: String,
        val totalbeløp: Int,
        val utbetalingslinjer: List<Utbetalingslinje>
    )

    @Serializable
    enum class Fagområde {
        SPREF, SP
    }

    @Serializable
    data class Utbetalingslinje(
        val dagsats: Int,
        val fom: LocalDate,
        val tom: LocalDate,
        val grad: Double,
        val beløp: Int,
        val sykedager: Int
    )

    @Serializable
    data class IkkeUtbetaltDag(
        val dato: LocalDate,
        val type: String
    )
}
