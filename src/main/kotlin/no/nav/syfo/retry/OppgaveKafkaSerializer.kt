package no.nav.syfo.retry

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.apache.kafka.common.serialization.Serializer

class OppgaveKafkaSerializer : Serializer<OppgaveRetryKafkaMessage> {

    private val objectMapper: ObjectMapper = ObjectMapper()

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        objectMapper.apply {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        }
    }

    override fun serialize(topic: String?, data: OppgaveRetryKafkaMessage): ByteArray =
        objectMapper.writeValueAsBytes(data)

    // no need to close anything
    override fun close() {}
}
