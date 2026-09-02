package no.nav.syfo.retry

import org.apache.kafka.common.serialization.Deserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

class OppgaveKafkaDeserializer : Deserializer<OppgaveRetryKafkaMessage> {
    private val jsonMapper: JsonMapper =
        jacksonMapperBuilder()
            .enable(
                tools.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
            )
            .build()

    override fun deserialize(topic: String?, data: ByteArray?): OppgaveRetryKafkaMessage {
        return jsonMapper.readValue(data, OppgaveRetryKafkaMessage::class.java)
    }

    // nothing to close
    override fun close() {}

    // nothing to configure
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {}
}
