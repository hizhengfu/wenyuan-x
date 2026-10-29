package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CollectionMigrationPolicyTest {
    private val now=LocalDateTime.of(2026,10,10,12,0)
    private val row=CollectionMigrationRowVo(CollectionImportRowVo(totalRegNo="OLD-1",level="GENERAL"),"source-1","museum","RELIC","IN_STORE","2009-01-01","原登记人","file","CONFIRMED","NO")
    private fun errors(value: CollectionMigrationRowVo)=CollectionMigrationPolicy.errors(CollectionMigrationRequestVo("old","核对原账",true,listOf(value)),"museum",now)
    @Test fun `imported verification declarations never require fabricated historical data`() {
        assertTrue(errors(row).isEmpty())
        assertTrue(errors(row.copy(originalRegisteredAt=null)).isEmpty())
        assertTrue(errors(row.copy(originalRegisteredBy=null)).isEmpty())
        assertTrue(errors(row.copy(registrationEvidence=null)).isEmpty())
        assertTrue(errors(row.copy(registrationEvidence=null,registrationEvidenceName="原账.pdf")).isEmpty())
        assertTrue(errors(row.copy(verification="UNVERIFIED",originalRegisteredAt=null,originalRegisteredBy=null,registrationEvidence=null)).isEmpty())
        assertTrue(errors(row.copy(originalRegisteredAt="2027-01-01")).any { it.field=="originalRegisteredAt" })
        assertTrue(errors(row.copy(originalRegisteredAt="unknown")).any { it.field=="originalRegisteredAt" })
    }
    @Test fun `unavailable original facts require evidence and individual reasons before confirmation`() {
        val content = CollectionMigrationVerificationContent(registrationEvidence="file", matchesOriginalLedger=true, reason="原账核验",
            originalDateMissingReason="原账未载日期，已查第一页至末页", originalRegistrarMissingReason="原账未署名", classificationMissingReason="原账未编分类号")
        CollectionMigrationVerificationPolicy.validate(content, true, now, true)
        for (invalid in listOf(content.copy(originalDateMissingReason=null), content.copy(originalRegistrarMissingReason=" "),
            content.copy(classificationMissingReason=null), content.copy(registrationEvidence=null), content.copy(matchesOriginalLedger=false),
            content.copy(originalRegisteredAt=now), content.copy(originalRegisteredBy="某人")))
            assertThrows(io.hizhengfu.cloud.common.exception.BadRequestException::class.java) { CollectionMigrationVerificationPolicy.validate(invalid, true, now, true) }
    }
    @Test fun `migration cannot invent numbers nature or museum ownership and repeated identities fail`() {
        for(value in listOf(row.copy(item=row.item.copy(totalRegNo=null)),row.copy(nature=null),row.copy(originalOrgCode="other"),row.copy(sourceRecordId=""),row.copy(currentStatus="DEREGISTERED"),row.copy(verification="APPROVED"))) assertFalse(errors(value).isEmpty())
        val request=CollectionMigrationRequestVo("old","核对",true,listOf(row,row))
        assertTrue(CollectionMigrationPolicy.errors(request,"museum",now).any { it.field=="sourceRecordId" })
        assertTrue(CollectionMigrationPolicy.errors(request.copy(belongsToCurrentMuseum=false),"museum",now).any { it.field=="belongsToCurrentMuseum" })
        assertFalse(CollectionMigrationPolicy.historyVerified(null));assertFalse(CollectionMigrationPolicy.historyVerified("NO"));assertTrue(CollectionMigrationPolicy.historyVerified("是"))
    }
}
