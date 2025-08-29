package no.vegvesen.nvdb.tnits.storage

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.LocalDate
import no.vegvesen.nvdb.apiles.uberiket.Detaljniva
import no.vegvesen.nvdb.apiles.uberiket.TypeVeg
import no.vegvesen.nvdb.tnits.geometry.SRID
import no.vegvesen.nvdb.tnits.model.Veglenke
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import java.nio.file.Files

class VeglenkerRocksDbStoreTest : StringSpec() {
    private lateinit var tempDir: String
    private lateinit var configuration: RocksDbConfiguration
    private lateinit var store: VeglenkerRocksDbStore

    override suspend fun beforeSpec(spec: Spec) {
        tempDir = Files.createTempDirectory("rocksdb-veglenker-test").toString()
        configuration = RocksDbConfiguration(tempDir, enableCompression = true)
        store =
            VeglenkerRocksDbStore(
                configuration.getDatabase(),
                configuration.getDefaultColumnFamily(),
            )
    }

    override suspend fun beforeEach(testCase: TestCase) {
        configuration.clear()
        // Recreate the store with fresh references after clearing
        store =
            VeglenkerRocksDbStore(
                configuration.getDatabase(),
                configuration.getDefaultColumnFamily(),
            )
    }

    override suspend fun afterSpec(spec: Spec) {
        configuration.close()
        java.io.File(tempDir).deleteRecursively()
    }

    init {
        "should store and retrieve veglenker" {
            val geometryFactory = GeometryFactory()
            val testGeometry =
                geometryFactory
                    .createLineString(
                        arrayOf(
                            Coordinate(10.0, 60.0),
                            Coordinate(10.1, 60.1),
                        ),
                    ).also { it.srid = SRID.UTM33 }

            val testVeglenker =
                listOf(
                    Veglenke(
                        veglenkesekvensId = 1L,
                        veglenkenummer = 1,
                        startposisjon = 0.0,
                        sluttposisjon = 100.0,
                        startnode = 100,
                        sluttnode = 200,
                        startdato = LocalDate(2020, 1, 1),
                        geometri = testGeometry,
                        typeVeg = TypeVeg.KANALISERT_VEG,
                        detaljniva = Detaljniva.VEGTRASE,
                        feltoversikt = emptyList(),
                    ),
                )

            store.upsert(1L, testVeglenker)

            val retrievedVeglenker = store.get(1L)
            retrievedVeglenker shouldNotBe null
            retrievedVeglenker shouldBe testVeglenker
            store.size() shouldBe 1L

            val batchResult = store.batchGet(listOf(1L, 2L))
            batchResult.size shouldBe 1
            batchResult[1L] shouldBe testVeglenker

            store.delete(1L)
            store.get(1L) shouldBe null
            store.size() shouldBe 0L
        }

        "should perform batch operations" {
            val geometryFactory = GeometryFactory()
            val testGeometry =
                geometryFactory
                    .createLineString(
                        arrayOf(
                            Coordinate(10.0, 60.0),
                            Coordinate(10.1, 60.1),
                        ),
                    ).also { it.srid = SRID.UTM33 }

            val testVeglenker1 =
                listOf(
                    Veglenke(
                        veglenkesekvensId = 1L,
                        veglenkenummer = 1,
                        startposisjon = 0.0,
                        sluttposisjon = 100.0,
                        startnode = 100,
                        sluttnode = 200,
                        startdato = LocalDate(2020, 1, 1),
                        geometri = testGeometry,
                        typeVeg = TypeVeg.KANALISERT_VEG,
                        detaljniva = Detaljniva.VEGTRASE,
                        feltoversikt = emptyList(),
                    ),
                )

            val testVeglenker2 =
                listOf(
                    Veglenke(
                        veglenkesekvensId = 2L,
                        veglenkenummer = 2,
                        startposisjon = 100.0,
                        sluttposisjon = 200.0,
                        startnode = 200,
                        sluttnode = 300,
                        startdato = LocalDate(2020, 1, 1),
                        geometri = testGeometry,
                        typeVeg = TypeVeg.KANALISERT_VEG,
                        detaljniva = Detaljniva.VEGTRASE,
                        feltoversikt = emptyList(),
                    ),
                )

            val updates =
                mapOf(
                    1L to testVeglenker1,
                    2L to testVeglenker2,
                    3L to null,
                )

            store.batchUpdate(updates)

            store.get(1L) shouldBe testVeglenker1
            store.get(2L) shouldBe testVeglenker2
            store.get(3L) shouldBe null
        }
    }
}
