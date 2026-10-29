package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path
import java.time.LocalDateTime

class CollectionUsageSummaryRuntimeTest {
    @TempDir lateinit var dir: Path
    private val at=LocalDateTime.of(2026,10,11,12,0)
    private fun fixture(body:(CollectionUsageSummaryService)->Unit) {
        val db=Database.connect("jdbc:sqlite:${dir.resolve("usage.db")}","org.sqlite.JDBC")
        val user=SecurityUser(id="reader",name="读者",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf("ADMIN"),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try { transaction(db) {
            originalReaderSchema();originalReader("reader",CollectionConst.STAT)
            SchemaUtils.create(CollectionOrgs,Collections,CollectionPlacements)
            for(org in listOf("museum","other"))CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
            body(CollectionUsageSummaryService(CollectionScopeResolver("multi","museum")))
        }} finally {SecurityContextHolder.clearContext()}
    }
    private fun collection(id:String,status:EnumCollectionStatus=EnumCollectionStatus.IN_USE,org:String="museum",registered:Boolean=true,amount:Int=1,measure:String="件")=CollectionDao.new(id) {
        orgId=org;totalRegNo=id;name=id;lifeStatus=status;registeredAt=if(registered)at.minusDays(1)else null;quantity=amount;unit=measure;warehouseId="store"
    }
    private fun placement(id:String,collection:String,start:LocalDateTime?=at.minusHours(1),end:LocalDateTime?=null,approvedValue:Boolean=true,org:String="museum")=CollectionPlacementDao.new(id) {
        orgId=org;sourceBizType=CollectionConst.BIZ_EXHIBITION;sourceBizId=id;collectionId=collection;locationId="case";validFrom=start;validTo=end;approved=approvedValue
    }
    @Test fun `actual exhibition excludes planned future ended unapproved retired and other internal uses`()=fixture {service->
        listOf("actual","planned","future","ended","unapproved","internal").forEach{collection(it)}
        placement("p-actual","actual");placement("p-duplicate","actual")
        placement("p-planned","planned",start=null);placement("p-future","future",start=at.plusHours(1))
        placement("p-ended","ended",end=at);placement("p-unapproved","unapproved",approvedValue=false)
        collection("loan",EnumCollectionStatus.ON_LOAN,amount=2,measure="套")
        collection("store",EnumCollectionStatus.IN_STORE,amount=3);placement("p-store","store")
        collection("pending",EnumCollectionStatus.PENDING,registered=false);placement("p-pending","pending")
        collection("retired",EnumCollectionStatus.DEREGISTERED);placement("p-retired","retired")
        collection("foreign",org="other");placement("p-foreign","foreign",org="other")
        val summary=service.summary(at)
        assertEquals(8,summary.total);assertEquals(1,summary.exhibitionCount);assertEquals(1,summary.loanCount)
        assertEquals(mapOf("件" to 9L,"套" to 2L),summary.quantityByUnit)
        assertEquals(mapOf("件" to 1L),summary.exhibitionQuantityByUnit)
        assertEquals(mapOf("套" to 2L),summary.loanQuantityByUnit)
        assertEquals("store",summary.items.single{it.id=="store"}.warehouseId)
        assertTrue(summary.items.single{it.id=="store"}.inStore)
    }
    @Test fun `fresh membership and revoked rights control usage despite stale administrator login`()=fixture {service->
        collection("local");placement("p-other","local",org="other")
        collection("other",org="other");placement("p-local","other")
        assertEquals(listOf("local"),service.summary(at).items.map{it.id});assertEquals(0,service.summary(at).exhibitionCount)
        UserDao["reader"].groupId="other"
        assertEquals(listOf("other"),service.summary(at).items.map{it.id});assertEquals(0,service.summary(at).exhibitionCount)
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.summary(at)}
        actorRole("reader","STAT",setOf(CollectionConst.STAT));UserDao["reader"].enabled=false
        assertThrows(BadRequestException::class.java){service.summary(at)}
    }
    @Test fun `quantity summaries retain separate units and disclose unverifiable measurements`() {
        val summary=CollectionUsageSummaryBuilder.build(listOf(
            CollectionUsageItemVo("one","museum",3," 件 ",true,false),
            CollectionUsageItemVo("two","museum",2,"套",false,true),
            CollectionUsageItemVo("three","museum",null,"件",false,false),
            CollectionUsageItemVo("four","museum",1," ",false,false)))
        assertEquals(mapOf("件" to 3L,"套" to 2L),summary.quantityByUnit)
        assertEquals(2,summary.quantityUnverifiedTotal)
    }
}
