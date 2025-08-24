package no.vegvesen.nvdb.tnits.xml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
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
class XmlStreamDsl(
    val writer: XMLStreamWriter,
    private val indent: String? = null,
) {
    @PublishedApi
    internal val namespaces = mutableMapOf<String, String>()

    @PublishedApi
    internal var depth = 0

    @PublishedApi
    internal var hasChildElements = false

    fun startDocument(
        encoding: String = "UTF-8",
        version: String = "1.0",
    ) = writer.writeStartDocument(encoding, version)

    fun endDocument() = writer.writeEndDocument()

    fun flushAndClose() {
        try {
            writer.flush()
        } finally {
            writer.close()
        }
    }

    inline fun element(
        qName: String,
        namespaceDeclarations: Map<String, String> = emptyMap(),
        block: XmlStreamDsl.() -> Any?,
    ) {
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

    fun attribute(
        qName: String,
        value: String,
    ) {
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
inline fun writeXmlStream(
    outputStream: OutputStream,
    encoding: String = "UTF-8",
    indent: String? = null,
    block: XmlStreamDsl.() -> Unit,
) {
    val writer = xmlOutputFactory.createXMLStreamWriter(outputStream, encoding)
    val xml = XmlStreamDsl(writer, indent)
    try {
        xml.startDocument(encoding)
        xml.block()
        xml.endDocument()
        indent?.let { xml.writer.writeCharacters("\n") }
    } finally {
        xml.flushAndClose()
    }
}

/** Overload writing directly to a Path. */
inline fun writeXmlStream(
    path: Path,
    encoding: String = "UTF-8",
    indent: String? = null,
    vararg options: StandardOpenOption = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
    block: XmlStreamDsl.() -> Unit,
) {
    BufferedOutputStream(Files.newOutputStream(path, *options)).use { os ->
        writeXmlStream(os, encoding, indent) { block() }
    }
}

/** Convenience wrapper: write a document with a single root and declared namespaces. */
inline fun writeXmlDocument(
    outputStream: OutputStream,
    rootQName: String,
    namespaces: Map<String, String> = emptyMap(),
    encoding: String = "UTF-8",
    indent: String? = null,
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

/** Path overload of xmlDocument. */
inline fun writeXmlDocument(
    path: Path,
    rootQName: String,
    namespaces: Map<String, String> = emptyMap(),
    encoding: String = "UTF-8",
    indent: String? = null,
    vararg options: StandardOpenOption = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
    writeChildren: XmlStreamDsl.() -> Unit,
) = BufferedOutputStream(Files.newOutputStream(path, *options)).use { os ->
    writeXmlDocument(os, rootQName, namespaces, encoding, indent, writeChildren)
}

/** Stream a Sequence<T> under a root element to OutputStream. */
inline fun <T> writeSequence(
    outputStream: OutputStream,
    rootQName: String,
    namespaces: Map<String, String> = emptyMap(),
    items: Sequence<T>,
    encoding: String = "UTF-8",
    indent: String? = null,
    writeItem: XmlStreamDsl.(T) -> Unit,
) = writeXmlDocument(outputStream, rootQName, namespaces, encoding, indent) {
    for (item in items) writeItem(item)
}

/** Stream a Sequence<T> under a root element to Path. */
inline fun <T> writeSequence(
    path: Path,
    rootQName: String,
    namespaces: Map<String, String> = emptyMap(),
    items: Sequence<T>,
    encoding: String = "UTF-8",
    indent: String? = null,
    vararg options: StandardOpenOption = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
    writeItem: XmlStreamDsl.(T) -> Unit,
) = BufferedOutputStream(Files.newOutputStream(path, *options)).use { os ->
    writeSequence(os, rootQName, namespaces, items, encoding, indent, writeItem)
}

/** Stream a Flow<T> under a root element to OutputStream. */
suspend inline fun <T> writeFlow(
    outputStream: OutputStream,
    rootQName: String,
    namespaces: Map<String, String> = emptyMap(),
    flow: Flow<T>,
    encoding: String = "UTF-8",
    indent: String? = null,
    crossinline writeItem: XmlStreamDsl.(T) -> Unit,
) {
    val writer = xmlOutputFactory.createXMLStreamWriter(outputStream, encoding)
    val xml = XmlStreamDsl(writer, indent)
    try {
        xml.startDocument(encoding)
        xml.element(rootQName, namespaces) {
            flow.collect { item -> writeItem(item) }
        }
        xml.endDocument()
        indent?.let { xml.writer.writeCharacters("\n") }
    } finally {
        xml.flushAndClose()
    }
}

/** Stream a Flow<T> under a root element to Path. */
suspend inline fun <T> writeFlow(
    path: Path,
    rootQName: String,
    namespaces: Map<String, String> = emptyMap(),
    flow: Flow<T>,
    encoding: String = "UTF-8",
    indent: String? = null,
    vararg options: StandardOpenOption = arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING),
    crossinline writeItem: XmlStreamDsl.(T) -> Unit,
) = withContext(Dispatchers.IO) {
    BufferedOutputStream(Files.newOutputStream(path, *options)).use { os ->
        writeFlow(os, rootQName, namespaces, flow, encoding, indent, writeItem)
    }
}

/* ---------- Minimal example usage ----------
val NS = mapOf(
    "tnits" to "http://spec.tn-its.eu/tnits",
    "gml"   to "http://www.opengis.net/gml/3.2.1",
    "xlink" to "http://www.w3.org/1999/xlink"
)

data class Point(val lon: Double, val lat: Double)

fun example(path: Path, items: Sequence<List<Point>>) {
    writeSequence(path, "tnits:RoadFeatureList", NS, items) { line ->
        "tnits:RoadFeature" {
            attribute("gml:id", "doc-1")
            "tnits:location" {
                "tnits:locationReference" {
                    "tnits:GeometryLocationReference" {
                        "gml:LineString" {
                            attribute("srsName", "urn:ogc:def:crs:EPSG::4326")
                            attribute("srsDimension", "2")
                            "gml:posList" { -line.joinToString(" ") { "${it.lon} ${it.lat}" } }
                        }
                    }
                }
            }
        }
    }
}
*/
