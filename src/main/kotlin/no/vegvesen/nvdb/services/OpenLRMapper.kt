package no.vegvesen.nvdb.services

/**
 * Service for converting NVDB road network references to OpenLR format
 * compliant with TN-ITS specification.
 *
 * TODO: Implementation temporarily disabled due to TN-ITS model class generation issues.
 * The TN-ITS model classes cannot be generated due to unresolved XLink schema conflicts
 * in the W3C XLink specification that require extensive JAXB customization to resolve.
 *
 * To enable this functionality:
 * 1. Resolve XLink "Title" property conflicts in http://www.w3.org/1999/xlink.xsd
 * 2. Generate TN-ITS model classes using: ./gradlew generateTnItsClasses
 * 3. Replace this stub with the full implementation
 */
class OpenLRMapper {
    /**
     * Maps NVDB road link data to an OpenLR XML location reference string.
     * Currently returns a placeholder implementation.
     *
     * @param veglenkesekvensId The NVDB road link sequence ID
     * @param veglenkenummer The NVDB road link number
     * @return OpenLR XML string representation (placeholder)
     */
    fun mapToOpenLR(
        veglenkesekvensId: Long,
        veglenkenummer: Int,
    ): String = "<!-- TN-ITS OpenLR mapping not available - see class documentation -->"
}

/*
import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import no.vegvesen.nvdb.tnits.model.*
import java.io.StringWriter
import java.math.BigInteger

/**
 * Service for converting NVDB road network references to OpenLR format
 * compliant with TN-ITS specification.
 */
class OpenLRMapper {
    private val jaxbContext = JAXBContext.newInstance(ObjectFactory::class.java)

    /**
 * Maps NVDB road link data to an OpenLR XML location reference string.
 * This is a simplified implementation for demonstration purposes.
 *
 * @param veglenkesekvensId The NVDB road link sequence ID
 * @param veglenkenummer The NVDB road link number
 * @return OpenLR XML string representation
 */
    fun mapToOpenLR(
        veglenkesekvensId: Long,
        veglenkenummer: Int,
    ): String {
        val objectFactory = ObjectFactory()

        // Create a simple line location reference for demonstration
        val lineLocationRef = objectFactory.createLineLocationReference()

        // Create location reference points (simplified - would need real coordinate data)
        val startPoint =
            objectFactory.createLocationReferencePoint().apply {
                coordinates =
                    objectFactory.createCoordinates().apply {
                        latitude = 59.9139 // Oslo coordinates as example
                        longitude = 10.7522
                    }
                lineAttributes =
                    objectFactory.createLineAttributes().apply {
                        frc = FRCType.FRC_3 // Functional Road Class
                        fow = FOWType.MULTIPLE_CARRIAGEWAY // Form of Way
                        bear = 180
                    }
                pathAttributes =
                    objectFactory.createPathAttributes().apply {
                        lfrcnp = FRCType.FRC_3 // Functional Road Class to next point
                        dnp = BigInteger.valueOf(100) // Distance to next point in meters
                    }
            }

        val endPoint =
            objectFactory.createLastLocationReferencePoint().apply {
                coordinates =
                    objectFactory.createCoordinates().apply {
                        latitude = 59.9140
                        longitude = 10.7523
                    }
                lineAttributes =
                    objectFactory.createLineAttributes().apply {
                        frc = FRCType.FRC_3
                        fow = FOWType.MULTIPLE_CARRIAGEWAY
                        bear = 180
                    }
            }

        lineLocationRef.locationReferencePoint.add(startPoint)
        lineLocationRef.lastLocationReferencePoint = endPoint

        // Create the main XML location reference
        val xmlLocationRef = objectFactory.createXMLLocationReference()
        xmlLocationRef.lineLocationReference = lineLocationRef

        // Create the root OpenLR element
        val openLR = objectFactory.createOpenLR()
        openLR.xmlLocationReference = xmlLocationRef

        // Convert to XML string
        return marshallToXml(openLR)
    }

    private fun marshallToXml(openLR: OpenLR): String {
        val marshaller = jaxbContext.createMarshaller()
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)

        val stringWriter = StringWriter()
        marshaller.marshal(openLR, stringWriter)
        return stringWriter.toString()
    }
}
*/
