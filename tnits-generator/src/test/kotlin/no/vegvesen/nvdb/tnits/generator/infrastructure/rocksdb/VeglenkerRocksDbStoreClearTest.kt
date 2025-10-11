package no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.generator.core.extensions.SRID
import no.vegvesen.nvdb.tnits.generator.core.extensions.geometryFactories
import no.vegvesen.nvdb.tnits.generator.core.model.Veglenke
import no.vegvesen.nvdb.tnits.generator.infrastructure.rocksdb.VeglenkerRocksDbStore
import no.vegvesen.nvdb.tnits.generator.openlr.TempRocksDbConfig.Companion.withTempDb
import org.locationtech.jts.geom.Coordinate

class VeglenkerRocksDbStoreClearTest :
    ShouldSpec({

        should("verify data is gone after clear and store can access empty database") {
            withTempDb { config ->
                // Arrange - Create store and add data
                val store = VeglenkerRocksDbStore(config)

                val geometryFactory = geometryFactories[SRID.WGS84]!!
                val testGeometry =
                    geometryFactory.createLineString(
                        arrayOf(
                            Coordinate(10.0, 60.0),
                            Coordinate(10.1, 60.1),
                        ),
                    )

                val testVeglenker =
                    listOf(
                        Veglenke(
                            veglenkesekvensId = 42L,
                            veglenkenummer = 1,
                            startposisjon = 0.0,
                            sluttposisjon = 100.0,
                            startnode = 100,
                            sluttnode = 200,
                            startdato = LocalDate(2020, 1, 1),
                            geometri = testGeometry,
                            typeVeg = TypeVeg.KANALISERT_VEG,
                            detaljniva = Detaljniva.VEGTRASE,
                            lengde = testGeometry.length,
                            feltoversikt = emptyList(),
                            konnektering = false,
                        ),
                    )

                store.upsert(42L, testVeglenker)
                store.get(42L) shouldBe testVeglenker

                // Act - Clear the database configuration (closes DB, deletes files, reinitializes)
                config.clear()

                // Assert - Store should still be usable, but data should be gone
                store.get(42L) shouldBe null
                store.size() shouldBe 0L
                store.getAll() shouldBe emptyMap()

                // Verify store is functional
                store.upsert(99L, testVeglenker)
                store.get(99L) shouldBe testVeglenker
            }
        }
    })
