package no.vegvesen.nvdb.tnits.xml

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText

class XmlStreamDslTest :
    StringSpec({

        lateinit var tempFile: Path

        beforeTest {
            tempFile = Files.createTempFile("xml-test", ".xml")
        }

        afterTest {
            tempFile.deleteIfExists()
        }

        "writeXmlStream should generate basic XML with OutputStream" {
            val output = ByteArrayOutputStream()

            writeXmlStream(output) {
                "root" {
                    "child" {
                        +"text content"
                    }
                    "other" {
                        attribute("attr", "value")
                    }
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe """<?xml version="1.0" encoding="UTF-8"?><root><child>text content</child><other attr="value"></other></root>"""
        }

        "writeXmlStream should work with Path" {
            writeXmlStream(tempFile) {
                "root" {
                    "test" {
                        +"path content"
                    }
                }
            }

            val content = tempFile.readText()
            content shouldBe """<?xml version="1.0" encoding="UTF-8"?><root><test>path content</test></root>"""
        }

        "writeXmlDocument should generate document with namespaces using OutputStream" {
            val output = ByteArrayOutputStream()
            val namespaces =
                mapOf(
                    "gml" to "http://www.opengis.net/gml/3.2.1",
                    "test" to "http://example.com/test",
                    "xmlns:xsi" to "http://www.w3.org/2001/XMLSchema-instance",
                    "xsi:schemaLocation" to "http://example.com/test test.xsd",
                )

            writeXmlDocument(output, "gml:Root", namespaces) {
                "test:element" {
                    attribute("gml:id", "test-1")
                    +"content"
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe
                """<?xml version="1.0" encoding="UTF-8"?>
<gml:Root xmlns:gml="http://www.opengis.net/gml/3.2.1" xmlns:test="http://example.com/test" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://example.com/test test.xsd">
	<test:element gml:id="test-1">content</test:element>
</gml:Root>
"""
        }

        "writeXmlDocument Path should delegate to OutputStream version" {
            val namespaces = mapOf("ns" to "http://example.com")

            writeXmlDocument(tempFile, "ns:root", namespaces) {
                "ns:child" {
                    +"test"
                }
            }

            val content = tempFile.readText()
            content shouldBe
                """<?xml version="1.0" encoding="UTF-8"?>
<ns:root xmlns:ns="http://example.com">
	<ns:child>test</ns:child>
</ns:root>
"""
        }

        "namespace handling should work correctly" {
            val output = ByteArrayOutputStream()
            val namespaces =
                mapOf(
                    "gml" to "http://www.opengis.net/gml/3.2.1",
                    "xlink" to "http://www.w3.org/1999/xlink",
                )

            writeXmlDocument(output, "gml:FeatureCollection", namespaces) {
                "gml:featureMember" {
                    attribute("xlink:href", "#feature1")
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe
                """<?xml version="1.0" encoding="UTF-8"?>
<gml:FeatureCollection xmlns:gml="http://www.opengis.net/gml/3.2.1" xmlns:xlink="http://www.w3.org/1999/xlink">
	<gml:featureMember xlink:href="#feature1"></gml:featureMember>
</gml:FeatureCollection>
"""
        }

        "complex nested structure should generate correct XML" {
            val output = ByteArrayOutputStream()

            writeXmlDocument(output, "root") {
                "metadata" {
                    attribute("version", "1.0")
                    "author" { +"Test Author" }
                    "date" { +"2024-01-01" }
                }
                "data" {
                    "items" {
                        repeat(3) { i ->
                            "item" {
                                attribute("id", (i + 1).toString())
                                "name" { +"Item ${i + 1}" }
                                "properties" {
                                    "property" {
                                        attribute("key", "type")
                                        +"test"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val xml = output.toString("UTF-8")
            val expected =
                """<?xml version="1.0" encoding="UTF-8"?>
<root>
	<metadata version="1.0">
		<author>Test Author</author>
		<date>2024-01-01</date>
	</metadata>
	<data>
		<items>
			<item id="1">
				<name>Item 1</name>
				<properties>
					<property key="type">test</property>
				</properties>
			</item>
			<item id="2">
				<name>Item 2</name>
				<properties>
					<property key="type">test</property>
				</properties>
			</item>
			<item id="3">
				<name>Item 3</name>
				<properties>
					<property key="type">test</property>
				</properties>
			</item>
		</items>
	</data>
</root>
"""
            xml shouldBe expected
        }

        "writeXmlStream should support indentation with custom indent string" {
            val output = ByteArrayOutputStream()

            writeXmlStream(output, indent = "  ") {
                "root" {
                    "child" {
                        +"text content"
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

        "writeXmlStream should support indentation with tab character" {
            val output = ByteArrayOutputStream()

            writeXmlStream(output, indent = "\t") {
                "root" {
                    "nested" {
                        "deep" {
                            +"content"
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

        "writeXmlDocument should support indentation" {
            val output = ByteArrayOutputStream()
            val namespaces = mapOf("ns" to "http://example.com")

            writeXmlDocument(output, "ns:root", namespaces, indent = "    ") {
                "ns:metadata" {
                    attribute("version", "1.0")
                }
                "ns:data" {
                    "ns:item" {
                        +"value"
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

        "should write returned text" {
            val output = ByteArrayOutputStream()

            writeXmlStream(output) {
                "root" {
                    "child" {
                        "text content"
                    }
                }
            }

            val xml = output.toString("UTF-8")
            xml shouldBe """<?xml version="1.0" encoding="UTF-8"?><root><child>text content</child></root>"""
        }

        "can writeXmlStream from non-suspend function" {
            fun blockingWriteXml(output: ByteArrayOutputStream) {
                writeXmlStream(output) {
                    "root" {
                        "child" {
                            +"text content"
                        }
                    }
                }
            }

            val output = ByteArrayOutputStream()
            blockingWriteXml(output)
            val xml = output.toString("UTF-8")
            xml shouldBe """<?xml version="1.0" encoding="UTF-8"?><root><child>text content</child></root>"""
        }
    })
