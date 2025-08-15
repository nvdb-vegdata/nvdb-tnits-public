package no.vegvesen.nvdb.tnits

import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml

fun buildOneSpeedLimitFeature(f: SpeedLimitFeature): String =
    xml("tnits:RoadFeature") {
        namespace("tnits", "http://spec.tn-its.eu/tnits")
        namespace("gml", "http://www.opengis.net/gml/3.2")
        namespace("xlink", "http://www.w3.org/1999/xlink")

        attribute("gml:id", f.docLocalId)

        "tnits:id" {
            "tnits:RoadFeatureId" {
                "tnits:providerId" { -"NO.SVV.NVDB" }
                "tnits:id" { -f.stableId }
            }
        }

        "tnits:featureType" {
            attribute("xlink:href", "http://spec.tn-its.eu/codelists/RoadFeatureTypeCode#speedLimit")
        }

        "tnits:operation" { -f.operation }

        "tnits:properties" {
            "tnits:RoadFeatureProperty" {
                "tnits:type" {
                    attribute("xlink:href", "http://spec.tn-its.eu/codelists/RoadFeatureTypeCode#maximumSpeedLimit")
                }
                "tnits:value" {
                    attribute("uom", "km/h")
                    -"${f.kmh}"
                }
            }
        }

        "tnits:location" {
            "locationReference" {
                "OpenLRLocationReference" {
                    "binaryLocationReference" {
                        "BinaryLocationReference" {
                            "base64String" { -f.openLR.base64 }
                            "openLRBinaryVersion" {
                                attribute("xlink:href", "http://spec.tn-its.eu/codelists/OpenLRBinaryVersionCode#v2_4")
                            }
                        }
                    }
                }
            }

            "locationReference" {
                "GeometryLocationReference" {
                    "gml:LineString" {
                        attribute("srsName", "urn:ogc:def:crs:EPSG::4326")
                        attribute("srsDimension", "2")
                        "gml:posList" {
                            -f.geometry.joinToString(" ") { "${it.lon} ${it.lat}" }
                        }
                    }
                }
            }

            f.linearHref?.let {
                "locationReference" {
                    "LocationByExternalReference" {
                        "predefinedLocationReference" {
                            attribute("xlink:href", it)
                        }
                    }
                }
            }
        }
    }.toString(PrintOptions(pretty = false))
