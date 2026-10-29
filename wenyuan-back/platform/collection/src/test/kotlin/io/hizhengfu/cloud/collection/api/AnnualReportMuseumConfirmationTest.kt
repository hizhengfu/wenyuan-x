package io.hizhengfu.cloud.collection.api

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.service.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** Real service and current museum members behind the formal HTTP signing endpoint. */
class AnnualReportMuseumConfirmationTest {
    private val body = """{"year":2025,"statisticianUserId":"stat","keeperUserId":"head","leaderUserId":"leader","signedAt":"2026-01-02T12:00:00","signedReportFileIds":"sign","figures":{"openingTotal":0,"newTotal":0,"transferInTotal":0,"transferOutTotal":0,"deregTotal":0,"closingTotal":0,"quantities":[],"changesDescription":"开发合成空馆年度"}}"""

    private fun controller(service: AnnualReportEvidenceService) = CollectionController(
        mock(CollectionService::class.java), mock(CollectionImportService::class.java), mock(CollectionFileService::class.java),
        mock(CollectionFlowImporter::class.java), mock(CollectionDashboardService::class.java), mock(AnnualReportArchiveService::class.java),
        service, mock(CollectionUnifiedSearchService::class.java))

    private fun fixture(mode: String, block: (MockMvc, AnnualReportEvidenceService) -> Unit) {
        val file = java.nio.file.Files.createTempFile("annual-confirmed-museum-", ".sqlite")
        val db = Database.connect("jdbc:sqlite:$file", driver="org.sqlite.JDBC")
        val context = StaticApplicationContext()
        context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); context.refresh()
        SpringContextHolder().setApplicationContext(context)
        try { transaction(db) {
            originalReaderSchema()
            GroupDao.new("target") { name="接收馆"; type='1'; parentId="0"; parentIds="0," }
            SchemaUtils.create(Configs, Collections, CollectionOrgs, CollectionLedgerStatEvents, CollectionRetainedLedgers,
                CollectionLedgerLogs, CollectionCategories, AnnualReportEvidenceRecords, AnnualReportArchives,
                CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files)
            CollectionOrgDao.new("museum") { name="本馆"; code="M" }
            CollectionOrgDao.new("target") { name="接收馆"; code="T" }
            CollectionDefaultOrgSettings.seed()
            Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content]="museum" }
            originalReader("stat", CollectionConst.STAT)
            originalReader("head", CollectionConst.STAT); actorRole("head", CollectionConst.ROLE_STORAGE_HEAD)
            originalReader("leader", CollectionConst.STAT); actorRole("leader", CollectionConst.ROLE_DIRECTOR)
            listOf("stat", "head", "leader").forEach { id ->
                listOf("museum", "target").forEach { org -> UserGroups.insertIgnore { it[userId]=id; it[groupId]=org } }
            }
            FileDao.new("sign") { name="开发合成.pdf"; path="/files/sign.pdf"; hash="sign"; size=100; ext="pdf" }
            val actor=SecurityUser(id="stat", name="合成统计员", username="stat", password="", groupId="museum",
                groupIds=setOf("museum", "target"), postIds=emptySet(), dataType=0, roles=emptySet(),
                authorities=listOf(SimpleGrantedAuthority(CollectionConst.STAT)), enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(actor, null, actor.authorities)
            val scope=CollectionScopeResolver(mode, CollectionDefaultOrgSettings())
            val binder=mock(BusinessAttachmentBinder::class.java).also {
                `when`(it.resolveSingle(anyString())).thenAnswer { c -> c.getArgument<String>(0) }
            }
            val service=AnnualReportEvidenceService(scope, CollectionLedgerStatisticsService(scope),
                CollectionResponsibleUserService(CollectionActorDirectory(scope), scope),
                CollectionPermanentArchiveService(binder, scope, readAccess=unprotectedArchiveReadAccessProvider()))
            val mvc=MockMvcBuilders.standaloneSetup(controller(service)).addPlaceholderValue("apiPath", "/api").build()
            block(mvc, service)
        } } finally { SecurityContextHolder.clearContext(); context.close(); java.nio.file.Files.deleteIfExists(file) }
    }
    private fun send(mvc: MockMvc, org: String?) = mvc.perform(post("/api/collection/stat/annual/reports")
        .contentType(MediaType.APPLICATION_JSON).content(body).also { if(org!=null)it.param("expectedOrgId",org) })
        .andReturn().response

    private fun originals() = CollectionPermanentArchiveDao.all().map { Triple(it.id.value, it.snapshot, it.digest) }.sortedBy { it.first }
    private fun reject(mvc: MockMvc, org: String) {
        val count=AnnualReportEvidenceDao.count(); val before=originals(); val refs=CollectionPermanentArchiveFileDao.count()
        val failure=runCatching { send(mvc, org) }
        val cause=generateSequence(failure.exceptionOrNull()) { it.cause }.filterIsInstance<BadRequestException>().firstOrNull()
        assertTrue(cause!=null || failure.getOrNull()?.status==400, "Old or blank confirmation must reject rather than sign under another museum")
        assertEquals(count, AnnualReportEvidenceDao.count()); assertEquals(before, originals()); assertEquals(refs, CollectionPermanentArchiveFileDao.count())
    }
    @Test fun `missing formal HTTP museum confirmation cannot create a report`() = fixture("multi") { mvc, _ ->
        assertEquals(400, send(mvc, null).status)
        assertEquals(0L, AnnualReportEvidenceDao.count()); assertEquals(0L, CollectionPermanentArchiveDao.count())
    }
    @Test fun `blank museum confirmation cannot fall back to the current museum`() = fixture("multi") { mvc, _ -> reject(mvc, " ") }
    @Test fun `backend default switch rejects the old window and new confirmation signs the new museum`() = fixture("single") { mvc, service ->
        assertEquals(200, send(mvc, "museum").status); val historical=originals()
        Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content]="target" }
        assertEquals("target", service.detail(2025)["orgId"])
        reject(mvc, "museum")
        assertEquals(200, send(mvc, "target").status)
        assertEquals(setOf("museum", "target"), AnnualReportEvidenceDao.all().map { it.orgId }.toSet())
        assertTrue(originals().containsAll(historical)); assertEquals(2L, AnnualReportEvidenceDao.count())
    }
    @Test fun `current main assignment overrides the unchanged login snapshot and old duplicate cannot bypass confirmation`() = fixture("multi") { mvc, service ->
        assertEquals(200, send(mvc, "museum").status); val historical=originals()
        UserDao["stat"].groupId="target"
        assertEquals("target", service.detail(2025)["orgId"])
        reject(mvc, "museum")
        assertEquals(200, send(mvc, "target").status)
        assertEquals(setOf("museum", "target"), AnnualReportEvidenceDao.all().map { it.orgId }.toSet())
        assertTrue(originals().containsAll(historical)); assertEquals(2L, AnnualReportEvidenceDao.count())
    }
}
