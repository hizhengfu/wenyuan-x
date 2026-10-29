package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.Duration

class FragileEnvironmentCoverageTest {
    private val start=LocalDateTime.of(2026,9,8,8,0)
    private val criterion=FragileEnvironmentCriterionVo(EnumFragileUseKind.DISPLAY,"TEMP","温度","°C",18.0,22.0,10,"本件评估")
    private fun sample(at:Long,received:Long=at,value:Double=20.0)=FragilePeriodSample(start.plusSeconds(at),start.plusSeconds(received),value,"°C",true)
    private fun evaluate(vararg samples:FragilePeriodSample)=FragileEnvironmentCoverage.evaluate(criterion,start,start.plusSeconds(30),FragilePeriodSourceEvidence(samples.toList()))
    @Test fun `point evidence expires precisely and only availability intervals are covered`() {
        val r=evaluate(sample(0),sample(15),sample(25))
        assertTrue(r.needsHandling)
        assertEquals(listOf("AVAILABLE","GAP","AVAILABLE"),r.spans.map {it.state})
        assertEquals(5L,r.spans.filter {it.state=="GAP"}.sumOf {Duration.between(it.from,it.to).seconds})
        assertEquals(30L,r.spans.sumOf {Duration.between(it.from,it.to).seconds})
    }
    @Test fun `late receipt does not backfill a real gap and latest normal cannot hide earlier exceedance`() {
        val r=evaluate(sample(0),sample(8,12,30.0),sample(20),sample(29))
        assertTrue(r.needsHandling);assertTrue(r.issues.any {it.contains("超出")})
        assertEquals(listOf("AVAILABLE","GAP","EXCEEDED","AVAILABLE"),r.spans.map {it.state})
        assertEquals(start.plusSeconds(12),r.spans[2].from)
        assertEquals("AVAILABLE",r.spans.last().state)
    }
    @Test fun `late older readings cannot replace newer observations or extend their age`() {
        val r=evaluate(sample(0),sample(5,12),sample(10),sample(20))
        assertFalse(r.needsHandling)
        assertEquals(listOf("AVAILABLE"),r.spans.map {it.state})
    }
    @Test fun `conflicting simultaneous values clocks and evidence quality cannot certify coverage`() {
        val conflict=evaluate(sample(0),sample(0,value=30.0),sample(10),sample(20))
        assertTrue(conflict.needsHandling);assertEquals("UNVERIFIED",conflict.spans.first().state)
        assertTrue(evaluate(sample(10,0),sample(20)).needsHandling)
        assertTrue(evaluate(sample(0).copy(usable=false),sample(10),sample(20)).needsHandling)
    }
    @Test fun `published evidence validity ending before specimen age creates a gap`() {
        val r=evaluate(sample(0).copy(validUntil=start.plusSeconds(3)),sample(10),sample(20))
        assertEquals(start.plusSeconds(3),r.spans.single {it.state=="GAP"}.from)
        assertTrue(r.needsHandling)
    }
}
