package no.vegvesen.nvdb.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.vegvesen.nvdb.database.RoadnetTable
import no.vegvesen.nvdb.database.SpeedLimitTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock

class TnItsServiceTest :
    StringSpec({

        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(RoadnetTable, SpeedLimitTable)
        }

        val service = TnItsService()

        "generateFullSnapshot should create FULL snapshot" {
            val snapshot = service.generateFullSnapshot()

            snapshot.type shouldBe "FULL"
            snapshot.baseDate shouldBe null
            snapshot.timestamp shouldNotBe null
        }

        "generateIncrementalSnapshot should create INCREMENTAL snapshot" {
            val since = Clock.System.now()
            val snapshot = service.generateIncrementalSnapshot(since)

            snapshot.type shouldBe "INCREMENTAL"
            snapshot.baseDate shouldBe since.toString()
            snapshot.timestamp shouldNotBe null
        }
    })
