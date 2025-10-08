package no.vegvesen.nvdb.tnits.generator.xml

import java.io.OutputStream
import java.lang.AutoCloseable
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

@PublishedApi
internal val xmlOutputFactory: XMLOutputFactory by lazy { XMLOutputFactory.newFactory() }

/**
 * Minimal streaming XML DSL built on StAX, inspired by redundent.kotlin.xml.
 * - "prefix:Local" { ... }      -> element
 * - attribute("xlink:href", v)  -> namespaced attribute
 * - namespace("gml", uri)       -> declare prefix->URI
 * - -"text"                     -> text node
 */
class XmlStreamDsl(val writer: XMLStreamWriter, private val indent: String? = null) : AutoCloseable {
    @PublishedApi
    internal val namespaces = mutableMapOf<String, String>()

    @PublishedApi
    internal var depth = 0

    @PublishedApi
    internal var hasChildElements = false

    fun startDocument(encoding: String = "UTF-8", version: String = "1.0") = writer.writeStartDocument(encoding, version)

    fun endDocument() {
        writer.writeEndDocument()
        if (indent != null) {
            writer.writeCharacters("\n")
        }
    }

    override fun close() {
        writer.close()
    }

    inline fun element(qName: String, namespaceDeclarations: Map<String, String> = emptyMap(), block: XmlStreamDsl.() -> Any?) {
        writeIndent()
        hasChildElements = true // Mark that this element exists, so parent knows it has child elements

        // Add any namespace declarations to context before resolving QName
        namespaceDeclarations.forEach { (p, u) -> namespaces[p] = u }
        val (prefix, localName, namespace) = splitQName(qName)
        when {
            prefix != null && namespace != null -> writer.writeStartElement(prefix, localName, namespace)
            prefix == null -> writer.writeStartElement(localName)
            else -> writer.writeStartElement(prefix, localName, "")
        }
        // Write namespace declarations on this element
        namespaceDeclarations.forEach { (p, u) ->
            writer.writeNamespace(p, u)
        }
        depth++
        val previousHasChildElements = hasChildElements
        hasChildElements = false
        this.block().also {
            if (it != null && it !is Unit) {
                text(it.toString())
            }
        }
        val elementHasChildElements = hasChildElements
        hasChildElements = previousHasChildElements
        depth--
        if (elementHasChildElements) {
            writeIndent()
        }
        writer.writeEndElement()
    }

    @PublishedApi
    internal fun writeIndent() {
        indent?.let { indentStr ->
            writer.writeCharacters("\n" + indentStr.repeat(depth))
        }
    }

    inline operator fun String.invoke(block: XmlStreamDsl.() -> Any?) = element(this, block = block)

    fun attribute(qName: String, value: String) {
        val (prefix, localName, namespace) = splitQName(qName)
        if (prefix != null && namespace != null) {
            writer.writeAttribute(prefix, namespace, localName, value)
        } else {
            writer.writeAttribute(localName, value)
        }
    }

    fun text(value: String) {
        writer.writeCharacters(value)
    }

    operator fun String.unaryPlus() {
        text(this)
    }

    fun splitQName(qName: String): Triple<String?, String, String?> {
        val idx = qName.indexOf(':')
        return if (idx >= 0) {
            val prefix = qName.take(idx)
            val localName = qName.substring(idx + 1)
            val uri = namespaces[prefix]
            Triple(prefix, localName, uri)
        } else {
            Triple(null, qName, null)
        }
    }
}

/** Generic entry point over an OutputStream. */
inline fun writeXmlStream(outputStream: OutputStream, encoding: String = "UTF-8", indent: String? = null, block: XmlStreamDsl.() -> Unit) {
    val writer = xmlOutputFactory.createXMLStreamWriter(outputStream, encoding)
    XmlStreamDsl(writer, indent).use { xml ->
        xml.startDocument(encoding)
        xml.block()
        xml.endDocument()
    }
}

/** Convenience wrapper: write a document with a single root and declared namespaces. */
inline fun writeXmlDocument(
    outputStream: OutputStream,
    rootQName: String,
    namespaces: Map<String, String> = emptyMap(),
    encoding: String = "UTF-8",
    indent: String? = "\t",
    writeChildren: XmlStreamDsl.() -> Unit,
) = writeXmlStream(outputStream, encoding, indent) {
    // Separate different types of namespace map entries:
    // 1. xmlns: declarations -> namespace mappings
    // 2. prefixed entries (like xsi:schemaLocation) -> root attributes
    // 3. plain keys -> namespace mappings (traditional style)
    val nsDeclarations = mutableMapOf<String, String>()
    val prefixedAttributes = mutableListOf<Pair<String, String>>()

    namespaces.forEach { (key, value) ->
        when {
            key.startsWith("xmlns:") -> {
                // xmlns:prefix -> namespace mapping
                nsDeclarations[key.removePrefix("xmlns:")] = value
            }

            key.contains(":") -> {
                // prefixed attribute (like xsi:schemaLocation)
                prefixedAttributes.add(key to value)
            }

            else -> {
                // traditional namespace mapping
                nsDeclarations[key] = value
            }
        }
    }

    element(rootQName, nsDeclarations) {
        // Add any prefixed attributes to the root element
        prefixedAttributes.forEach { (key, value) ->
            attribute(key, value)
        }
        writeChildren()
    }
}
