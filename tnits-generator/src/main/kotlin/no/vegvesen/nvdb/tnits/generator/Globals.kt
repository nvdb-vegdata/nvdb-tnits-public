package no.vegvesen.nvdb.tnits.generator

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.openlr.binary.BinaryMarshaller
import org.openlr.binary.BinaryMarshallerFactory

fun ObjectMapper.initialize(): ObjectMapper = apply {
    registerModule(JavaTimeModule())
    findAndRegisterModules()
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
}

val marshaller: BinaryMarshaller = BinaryMarshallerFactory().create()
val objectMapper = jacksonObjectMapper().initialize()
