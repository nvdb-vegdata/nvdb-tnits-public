package no.vegvesen.nvdb.tnits.xml

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

class XmlTest :
    StringSpec({

        lateinit var tempFile: Path

        beforeTest {
            tempFile = Files.createTempFile("xml-test", ".xml")
        }

        afterTest {
            tempFile.deleteIfExists()
        }

        "xmlStream should generate basic XML with OutputStream" {
            val output = ByteArrayOutputStream()

            xmlStream(output) {
                "root" {
                    "child" {
                        -"text content"
                    }
                    "other" {
                        attribute("attr", "value")
                    }
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe """<?xml version="1.0" encoding="UTF-8"?><root><child>text content</child><other attr="value"></other></root>"""
        }

        "xmlStream should work with Path" {
            xmlStream(tempFile) {
                "root" {
                    "test" {
                        -"path content"
                    }
                }
            }

            val content = tempFile.readText()
            content shouldBe """<?xml version="1.0" encoding="UTF-8"?><root><test>path content</test></root>"""
        }

        "xmlDocument should generate document with namespaces using OutputStream" {
            val output = ByteArrayOutputStream()
            val namespaces =
                mapOf(
                    "gml" to "http://www.opengis.net/gml/3.2.1",
                    "test" to "http://example.com/test",
                )

            xmlDocument(output, "gml:Root", namespaces) {
                "test:element" {
                    attribute("gml:id", "test-1")
                    -"content"
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe
                """<?xml version="1.0" encoding="UTF-8"?><gml:Root xmlns:gml="http://www.opengis.net/gml/3.2.1" xmlns:test="http://example.com/test"><test:element gml:id="test-1">content</test:element></gml:Root>"""
        }

        "xmlDocument Path should delegate to OutputStream version" {
            val namespaces = mapOf("ns" to "http://example.com")

            xmlDocument(tempFile, "ns:root", namespaces) {
                "ns:child" {
                    -"test"
                }
            }

            val content = tempFile.readText()
            content shouldBe
                """<?xml version="1.0" encoding="UTF-8"?><ns:root xmlns:ns="http://example.com"><ns:child>test</ns:child></ns:root>"""
        }

        "writeSequence should process items with OutputStream" {
            val output = ByteArrayOutputStream()
            val items = sequenceOf("first", "second", "third")

            writeSequence(output, "list", items = items) { item: String ->
                "item" {
                    attribute("value", item)
                    -item
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe
                """<?xml version="1.0" encoding="UTF-8"?><list><item value="first">first</item><item value="second">second</item><item value="third">third</item></list>"""
        }

        "writeSequence should work with Path" {
            val items = sequenceOf(1, 2, 3)

            writeSequence(tempFile, "numbers", items = items) { num: Int ->
                "number" {
                    -num.toString()
                }
            }

            val content = tempFile.readText()
            content shouldBe
                """<?xml version="1.0" encoding="UTF-8"?><numbers><number>1</number><number>2</number><number>3</number></numbers>"""
        }

        "writeFlow should process flow items with OutputStream" {
            val output = ByteArrayOutputStream()
            val flow = flowOf("a", "b", "c")

            writeFlow(output, "flow-root", flow = flow) { item: String ->
                "flow-item" {
                    -item.uppercase()
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe
                """<?xml version="1.0" encoding="UTF-8"?><flow-root><flow-item>A</flow-item><flow-item>B</flow-item><flow-item>C</flow-item></flow-root>"""
        }

        "writeFlow should work with Path" {
            val flow = flowOf("x", "y", "z")

            writeFlow(tempFile, "async-root", flow = flow) { item: String ->
                "async-item" {
                    attribute("letter", item)
                }
            }

            val content = tempFile.readText()
            content shouldBe
                """<?xml version="1.0" encoding="UTF-8"?><async-root><async-item letter="x"></async-item><async-item letter="y"></async-item><async-item letter="z"></async-item></async-root>"""
        }

        "namespace handling should work correctly" {
            val output = ByteArrayOutputStream()
            val namespaces =
                mapOf(
                    "gml" to "http://www.opengis.net/gml/3.2.1",
                    "xlink" to "http://www.w3.org/1999/xlink",
                )

            xmlDocument(output, "gml:FeatureCollection", namespaces) {
                "gml:featureMember" {
                    attribute("xlink:href", "#feature1")
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe
                """<?xml version="1.0" encoding="UTF-8"?><gml:FeatureCollection xmlns:gml="http://www.opengis.net/gml/3.2.1" xmlns:xlink="http://www.w3.org/1999/xlink"><gml:featureMember xlink:href="#feature1"></gml:featureMember></gml:FeatureCollection>"""
        }

        "complex nested structure should generate correct XML" {
            val output = ByteArrayOutputStream()

            xmlDocument(output, "root") {
                "metadata" {
                    attribute("version", "1.0")
                    "author" { -"Test Author" }
                    "date" { -"2024-01-01" }
                }
                "data" {
                    "items" {
                        repeat(3) { i ->
                            "item" {
                                attribute("id", (i + 1).toString())
                                "name" { -"Item ${i + 1}" }
                                "properties" {
                                    "property" {
                                        attribute("key", "type")
                                        -"test"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val xml = output.toString("UTF-8")
            val expected =
                """<?xml version="1.0" encoding="UTF-8"?><root><metadata version="1.0"><author>Test Author</author><date>2024-01-01</date></metadata><data><items><item id="1"><name>Item 1</name><properties><property key="type">test</property></properties></item><item id="2"><name>Item 2</name><properties><property key="type">test</property></properties></item><item id="3"><name>Item 3</name><properties><property key="type">test</property></properties></item></items></data></root>"""
            xml shouldBe expected
        }

        "xmlStream should support indentation with custom indent string" {
            val output = ByteArrayOutputStream()

            xmlStream(output, indent = "  ") {
                "root" {
                    "child" {
                        -"text content"
                    }
                    "other" {
                        attribute("attr", "value")
                    }
                }
            }

            val xml = output.toString("UTF-8")
            val expected = """<?xml version="1.0" encoding="UTF-8"?>
<root>
  <child>text content</child>
  <other attr="value"></other>
</root>
"""
            xml shouldBe expected
        }

        "xmlStream should support indentation with tab character" {
            val output = ByteArrayOutputStream()

            xmlStream(output, indent = "\t") {
                "root" {
                    "nested" {
                        "deep" {
                            -"content"
                        }
                    }
                }
            }

            val xml = output.toString("UTF-8")
            val expected = """<?xml version="1.0" encoding="UTF-8"?>
<root>
	<nested>
		<deep>content</deep>
	</nested>
</root>
"""
            xml shouldBe expected
        }

        "xmlDocument should support indentation" {
            val output = ByteArrayOutputStream()
            val namespaces = mapOf("ns" to "http://example.com")

            xmlDocument(output, "ns:root", namespaces, indent = "    ") {
                "ns:metadata" {
                    attribute("version", "1.0")
                }
                "ns:data" {
                    "ns:item" {
                        -"value"
                    }
                }
            }

            val xml = output.toString("UTF-8")
            val expected = """<?xml version="1.0" encoding="UTF-8"?>
<ns:root xmlns:ns="http://example.com">
    <ns:metadata version="1.0"></ns:metadata>
    <ns:data>
        <ns:item>value</ns:item>
    </ns:data>
</ns:root>
"""
            xml shouldBe expected
        }

        "writeSequence should support indentation" {
            val output = ByteArrayOutputStream()
            val items = sequenceOf("A", "B", "C")

            writeSequence(output, "list", items = items, indent = "  ") { item: String ->
                "item" {
                    attribute("value", item)
                    -item.lowercase()
                }
            }

            val xml = output.toString("UTF-8")
            val expected = """<?xml version="1.0" encoding="UTF-8"?>
<list>
  <item value="A">a</item>
  <item value="B">b</item>
  <item value="C">c</item>
</list>
"""
            xml shouldBe expected
        }

        "writeFlow should support indentation" {
            val output = ByteArrayOutputStream()
            val flow = flowOf(1, 2, 3)

            writeFlow(output, "numbers", flow = flow, indent = "  ") { num: Int ->
                "number" {
                    attribute("id", num.toString())
                    "value" { -num.toString() }
                    "squared" { -(num * num).toString() }
                }
            }

            val xml = output.toString("UTF-8")
            val expected = """<?xml version="1.0" encoding="UTF-8"?>
<numbers>
  <number id="1">
    <value>1</value>
    <squared>1</squared>
  </number>
  <number id="2">
    <value>2</value>
    <squared>4</squared>
  </number>
  <number id="3">
    <value>3</value>
    <squared>9</squared>
  </number>
</numbers>
"""
            xml shouldBe expected
        }

        "Path-based functions should support indentation" {
            writeSequence(tempFile, "test", items = sequenceOf("x", "y"), indent = "  ") { item: String ->
                "element" { -item }
            }

            val content = tempFile.readText()
            val expected = """<?xml version="1.0" encoding="UTF-8"?>
<test>
  <element>x</element>
  <element>y</element>
</test>
"""
            content shouldBe expected
        }
    })
