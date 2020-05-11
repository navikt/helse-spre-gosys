package no.nav.helse

import org.apache.kafka.common.serialization.Serializer

class TrengerInntektsmeldingSerializer<T> : Serializer<T> {
    override fun serialize(topic: String?, data: T): ByteArray = objectMapper.writeValueAsBytes(data)
}
