package no.nav.syfo.retry

import org.apache.kafka.common.serialization.Serializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

class OppgaveKafkaSerializer : Serializer<OppgaveRetryKafkaMessage> {

    private val jsonMapper: JsonMapper =
        jacksonMapperBuilder()
            .enable(
                tools.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT
            )
            .build()

    override fun configure(configs: MutableMap<String, *>, isKey: Boolean) {
        jsonMapper
    }

    override fun serialize(topic: String?, data: OppgaveRetryKafkaMessage): ByteArray =
        jsonMapper.writeValueAsBytes(data)

    // no need to close anything
    override fun close() {}
}
