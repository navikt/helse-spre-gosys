package no.nav.helse.vedtak

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Serializable
data class Vedtak (
    val aktørId: String,
    val fødselsnummer: String,
    val organisasjonsnummer: String,
    val gjenståendeSykedager: Int,
    val hendelser: List<@Serializable(with = UUIDSerializer::class) UUID>,
    val utbetalt: List<Utbetaling>,
    val ikkeUtbetalteDager: List<IkkeUtbetaltDag>,
    @Serializable(with = LocalDateSerializer::class)
    val fom: LocalDate,
    @Serializable(with = LocalDateSerializer::class)
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val godkjentAv: String,
    val automatiskBehandling: Boolean,
    @Serializable(with = LocalDateTimeSerializer::class)
    val opprettet: LocalDateTime,
    val sykepengegrunnlag: Double,
    val månedsinntekt: Double,
    @Serializable(with = LocalDateSerializer::class)
    val maksdato: LocalDate,
    val system_read_count: Int,
    val system_participating_services: List<ParticipatingService>,
    val `@event_name` : String,
    @Serializable(with = UUIDSerializer::class)
    val `@id`: UUID,
    @Serializable(with = LocalDateTimeSerializer::class)
    val `@opprettet`: LocalDateTime,
    val `@forårsaket_av`: ForårsaketAv
)

@Serializable
data class ParticipatingService (
    val service: String,
    val instance: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val time: LocalDateTime
)

@Serializable
data class ForårsaketAv (
    val event_name: String,
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = LocalDateTimeSerializer::class)
    val opprettet: LocalDateTime
)

@Serializable
data class Utbetaling(
    val mottaker: String,
    val fagområde: Fagområde,
    val fagsystemId: String,
    val totalbeløp: Int,
    val utbetalingslinjer: List<Utbetalingslinje>
) {
    @Serializable
    enum class Fagområde {
        SPREF, SP
    }

    @Serializable
    data class Utbetalingslinje(
        val dagsats: Int,
        @Serializable(with = LocalDateSerializer::class)
        val fom: LocalDate,
        @Serializable(with = LocalDateSerializer::class)
        val tom: LocalDate,
        val grad: Double,
        val beløp: Int,
        val sykedager: Int
    )
}

@Serializable
data class IkkeUtbetaltDag(
    @Serializable(with = LocalDateSerializer::class)
    val dato: LocalDate,
    val type: String
)

@Serializer(forClass = UUID::class)
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

@Serializer(forClass = LocalDate::class)
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
}

@Serializer(forClass = LocalDateTime::class)
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }
}
