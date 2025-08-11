package no.vegvesen.nvdb.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TnItsServiceTest : StringSpec({
    
    val service = TnItsService()
    
    "generateFullSnapshot should create FULL snapshot" {
        val snapshot = service.generateFullSnapshot()
        
        snapshot.type shouldBe "FULL"
        snapshot.baseDate shouldBe null
        snapshot.timestamp shouldNotBe null
    }
    
    "generateIncrementalSnapshot should create INCREMENTAL snapshot" {
        val since = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val snapshot = service.generateIncrementalSnapshot(since)
        
        snapshot.type shouldBe "INCREMENTAL"
        snapshot.baseDate shouldBe since.toString()
        snapshot.timestamp shouldNotBe null
    }
})