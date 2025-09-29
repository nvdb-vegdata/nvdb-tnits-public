package no.vegvesen.nvdb.tnits

import io.kotest.core.annotation.RequiresTag
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXParseException
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

const val Manual = "Manual"

@RequiresTag(Manual)
class XsdValidationTest : ShouldSpec() {
    init {
        should("validate expected snapshot against its XSD") {
            val xml = readFile("expected-snapshot.xml")

            shouldBeValidXsd(xml)
        }

        should("validate expected update against its XSD") {
            val xml = readFile("expected-update.xml")

            shouldBeValidXsd(xml)
        }
    }
}

fun shouldBeValidXsd(xml: String) {
    val (warnings, errors) = validateXmlWithSchemaHints(xml.byteInputStream())
    warnings.shouldBeEmpty()
    errors.shouldBeEmpty()
}

data class XsdValidationResult(val warnings: List<SAXParseException>, val errors: List<SAXParseException>)

fun validateXmlWithSchemaHints(xmlStream: InputStream): XsdValidationResult {
    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    // Empty schema: relies on xsi:schemaLocation or xsi:noNamespaceSchemaLocation
    val schema = schemaFactory.newSchema()
    val warnings = mutableListOf<SAXParseException>()
    val errors = mutableListOf<SAXParseException>()
    val validator = schema.newValidator().apply {
        errorHandler = object : ErrorHandler {
            override fun warning(e: SAXParseException) {
                println("Warning: ${e.message} at ${e.lineNumber}:${e.columnNumber}")
                warnings.add(e)
            }

            override fun error(e: SAXParseException) {
                println("Error: ${e.message} at ${e.lineNumber}:${e.columnNumber}")
                errors.add(e)
            }

            override fun fatalError(e: SAXParseException): Unit = throw e
        }
    }

    xmlStream.use {
        validator.validate(StreamSource(it))
    }
    return XsdValidationResult(warnings, errors)
}
