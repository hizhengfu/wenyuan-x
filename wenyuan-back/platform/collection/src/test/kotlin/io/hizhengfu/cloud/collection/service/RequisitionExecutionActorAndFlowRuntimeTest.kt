package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.flow.dao.FlowInstanceDao
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.sql.DataSource
import io.hizhengfu.cloud.collection.service.ArchivePostgresEvidenceSupport as P

/** 06-C01: two independently attributable service boundaries; no public business data or GUI writes. */
class RequisitionExecutionActorAndFlowRuntimeTest {
    @TempDir lateinit var dir: Path

    private data class TableProjection(val rows: Int, val sha256: String)
    private data class Prepared(
        val runtime: Any,
        val currentId: String,
        val originalId: String,
        val currentInstanceId: String,
        val files: Map<String, Path>,
    )

    private fun digest(bytes: ByteArray, algorithm: String = "SHA-256") =
        MessageDigest.getInstance(algorithm).digest(bytes).joinToString("") { "%02x".format(it) }

    /** Full committed rows of every table in the owned schema, not a selected business-field projection. */
    private fun projection(r: Any): Map<String, TableProjection> {
        val schema = P.field<String>(r, "schema")
        val source = P.field<DataSource>(r, "source")
        return source.connection.use { connection ->
            val tables = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema=? AND table_type='BASE TABLE' ORDER BY table_name",
            ).use { q ->
                q.setString(1, schema)
                q.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
            }
            tables.associateWith { table ->
                val rows = connection.createStatement().use { q ->
                    q.executeQuery("SELECT row_to_json(t)::text FROM \"$schema\".\"$table\" t").use { rs ->
                        buildList { while (rs.next()) add(rs.getString(1)) }.sorted()
                    }
                }
                TableProjection(rows.size, digest(rows.joinToString("\n").toByteArray()))
            }
        }
    }

    private fun originals(prepared: Prepared) = prepared.files.mapValues { (_, path) ->
        Files.readAllBytes(path).let { it.size to digest(it) }
    }

    private fun fixture(body: (RequisitionReplacementRuntimeTest, Prepared) -> Unit) {
        val owner = RequisitionReplacementRuntimeTest().apply { dir = this@RequisitionExecutionActorAndFlowRuntimeTest.dir }
        P.call<Unit>(owner, "fixture", "multi", true, { r: Any, originalId: String ->
            val context = P.field<AnnotationConfigApplicationContext>(r, "c")
            val service = context.getBean(RequisitionService::class.java)
            val files = listOf("current-plan", "current-review", "current-safety", "current-sign").associateWith { key ->
                dir.resolve("$key.txt").also { path ->
                    Files.writeString(path, "DEV-SYNTHETIC 06-C01 $key software boundary input; no physical handover or human signature.\n")
                }
            }
            P.db(r) {
                actorRole("new-keeper", CollectionConst.ROLE_KEEPER)
                actorRole("new-operator", CollectionConst.ROLE_KEEPER)
                files.forEach { (key, path) ->
                    val bytes = Files.readAllBytes(path)
                    FileDao.new(key) {
                        name = "$key.txt"; this.path = "dev-synthetic/$key.txt"
                        hash = digest(bytes, "MD5"); size = bytes.size.toLong(); ext = "txt"
                    }
                }
                for (id in listOf("current-one", "current-two")) CollectionDao.new(id) {
                    orgId = "museum"; totalRegNo = "DEV-SYNTHETIC-$id"; name = "DEV-SYNTHETIC $id"
                    lifeStatus = EnumCollectionStatus.IN_STORE; warehouseId = "warehouse"
                    catalogStatus = EnumCatalogStatus.APPROVED; source = EnumSource.OLD
                }
            }
            P.call<Unit>(r, "login", "operator")
            val now = LocalDateTime.now()
            val currentId = service.save(RequisitionVo(
                collectionIds = "current-one,current-two", purpose = EnumMoveReason.RESEARCH,
                basis = "DEV-SYNTHETIC 06-C01 independent executor/flow boundary", department = "研究部",
                operator = "operator", toWarehouseId = "warehouse",
                dossier = RequisitionDossierVo(
                    "dept", "department", "keeper", "director", "researcher", "DEV-SYNTHETIC 无损观察",
                    "DEV-SYNTHETIC 软件验证", "DEV-SYNTHETIC 测试工作台", "DEV-SYNTHETIC 限位支撑",
                    "current-plan", now.minusDays(1), now.plusDays(1), transportRequired = false,
                    transportDecisionReason = "DEV-SYNTHETIC 同室软件测试，不发生实际运输",
                ),
            ))
            service.submit(currentId)
            P.call<Unit>(owner, "approve", r, currentId, "")
            val statutory = context.getBean(RequisitionStatutoryService::class.java)
            val approvedAt = P.db(r) { statutory.requireCurrentApproval(RequisitionDao[currentId]).createTime }
            P.call<Unit>(r, "login", "keeper")
            // SQL approval retains microseconds, while the persisted handover contract retains milliseconds.
            // Use an actually elapsed current millisecond; never backdate or provide a future instant.
            var reviewedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
            while (reviewedAt.isBefore(approvedAt)) {
                Thread.sleep(1)
                reviewedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
            }
            val handedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
            statutory.recordHandover(currentId, RequisitionHandoverVo(
                handedAt = handedAt, reviewedAt = reviewedAt, reviewerUserId = "keeper",
                reviewAndAssignmentFileIds = "current-review", custodianUserIds = setOf("new-keeper"),
                receivingUserIds = setOf("researcher"), items = listOf("current-one", "current-two").map {
                    RequisitionConditionVo(it, "DEV-SYNTHETIC software test condition; no physical observation", false)
                },
                safetyMeasuresConfirmed = true, siteSafetyConfirmed = true,
                safetyCheckFileIds = "current-safety", signedHandoverFileIds = "current-sign",
            ))
            val instance = P.db(r) {
                val current = RequisitionDao[currentId]
                assertEquals(EnumFlowStatus.PASS, current.flowStatus)
                assertFalse(current.outExecuted); assertFalse(current.returned)
                assertNull(current.supersededById); assertNull(current.replacesId)
                assertEquals(setOf("new-keeper"), statutory.requireHandover(current).custodianUserIds)
                assertEquals("COMPLETED", FlowInstanceDao[current.flowInstanceId!!].status)
                current.flowInstanceId!!
            }
            body(owner, Prepared(r, currentId, originalId, instance, files))
        })
    }

    private fun positive(prepared: Prepared, verifyNonDesignatedRepeat: Boolean = false) {
        val r = prepared.runtime
        val context = P.field<AnnotationConfigApplicationContext>(r, "c")
        P.call<Unit>(r, "login", "new-keeper")
        assertEquals(prepared.currentId, context.getBean(RequisitionService::class.java)
            .executeOut(prepared.currentId, CollectionOutboundExecutionVo()))
        P.db(r) {
            assertTrue(RequisitionDao[prepared.currentId].outExecuted)
            assertEquals(2L, StorageMovementDao.find { StorageMovements.refBizId eq prepared.currentId }.count())
            assertEquals(1, CollectionPermanentArchiveDao.all().count {
                it.bizId == prepared.currentId && it.phase == "OUT_EXECUTED"
            })
            assertTrue(listOf("current-one", "current-two").all { CollectionDao[it].lifeStatus == EnumCollectionStatus.IN_USE })
        }
        val once = projection(r)
        if (verifyNonDesignatedRepeat) {
            val localOnce = originals(prepared)
            P.call<Unit>(r, "login", "new-operator")
            val denied = assertThrows(BadRequestException::class.java) {
                context.getBean(RequisitionService::class.java)
                    .executeOut(prepared.currentId, CollectionOutboundExecutionVo())
            }
            assertEquals("须由本单指定保管人员本人执行提用出库", denied.message)
            assertEquals(once, projection(r), "A qualified but non-designated repeat must preserve every committed table")
            assertEquals(localOnce, originals(prepared))
            P.call<Unit>(r, "login", "new-keeper")
        }
        assertEquals(prepared.currentId, context.getBean(RequisitionService::class.java)
            .executeOut(prepared.currentId, CollectionOutboundExecutionVo()))
        assertEquals(once, projection(r), "An exact authorized repeat adds no movement, archive, reference or other row change")
    }

    @Tag("postgres-concurrency") @Test
    fun `fully approved signed current requisition rejects an otherwise qualified non designated executor`() = fixture { _, p ->
        val r = p.runtime
        val context = P.field<AnnotationConfigApplicationContext>(r, "c")
        P.call<Unit>(r, "login", "new-operator")
        P.db(r) {
            val actor = context.getBean(CollectionActorDirectory::class.java).requireActive("new-operator")
            assertTrue(actor.authorities.any { it.authority == CollectionConst.STORAGE })
            assertFalse(actor.authorities.any { it.authority == "ADMIN" })
            assertTrue(CollectionConst.ROLE_KEEPER in actor.roleCodes)
            assertFalse("new-operator" in context.getBean(RequisitionStatutoryService::class.java)
                .requireHandover(RequisitionDao[p.currentId]).custodianUserIds)
        }
        val before = projection(r); val localBefore = originals(p)
        println("REQUISITION_EXECUTION_PREPARED " + JsonUtils.toJson(mapOf(
            "kind" to "non-designated", "currentId" to p.currentId, "currentFlow" to p.currentInstanceId,
            "actor" to "new-operator", "custodians" to listOf("new-keeper"), "approved" to true,
            "validCurrentHandover" to true, "tables" to before.size, "localInputs" to localBefore,
        )))
        val denied = assertThrows(BadRequestException::class.java) {
            context.getBean(RequisitionService::class.java).executeOut(p.currentId, CollectionOutboundExecutionVo())
        }
        assertEquals("须由本单指定保管人员本人执行提用出库", denied.message)
        assertEquals(before, projection(r), "Every committed fixture table and archive/file metadata remains unchanged")
        assertEquals(localBefore, originals(p), "Input original bytes are not changed or deleted")
        positive(p, verifyNonDesignatedRepeat = true)
        assertEquals(localBefore, originals(p))
        println("REQUISITION_EXECUTION_RESULT " + JsonUtils.toJson(mapOf(
            "kind" to "non-designated", "denial" to denied.message, "allTablesUnchanged" to true,
            "localInputsUnchanged" to true, "designatedPositive" to true,
            "nonDesignatedRepeatDenied" to true, "designatedExactRepeatUnchanged" to true,
            "schema" to P.field<String>(r, "schema"),
        )))
    }

    @Tag("postgres-concurrency") @Test
    fun `otherwise executable current requisition cannot borrow an old completed workflow approval`() = fixture { owner, p ->
        val r = p.runtime
        val context = P.field<AnnotationConfigApplicationContext>(r, "c")
        P.call<Unit>(owner, "approve", r, p.originalId, "")
        val oldInstance = P.db(r) {
            val old = RequisitionDao[p.originalId]
            assertEquals(EnumFlowStatus.PASS, old.flowStatus)
            assertEquals("COMPLETED", FlowInstanceDao[old.flowInstanceId!!].status)
            old.flowInstanceId!!
        }
        assertNotEquals(p.currentInstanceId, oldInstance)
        P.call<Unit>(r, "login", "new-keeper")
        val fullCurrent = projection(r); val localBefore = originals(p)
        P.db(r) { Requisitions.update({ Requisitions.id eq p.currentId }) { it[flowInstanceId] = oldInstance } }
        val withOldInstance = projection(r)
        val denied = assertThrows(BadRequestException::class.java) {
            context.getBean(RequisitionService::class.java).executeOut(p.currentId, CollectionOutboundExecutionVo())
        }
        assertEquals("缺少本轮提用批准档案", denied.message)
        assertEquals(withOldInstance, projection(r), "The old-flow rejection must preserve all business and original rows")
        assertEquals(localBefore, originals(p))
        P.db(r) { Requisitions.update({ Requisitions.id eq p.currentId }) { it[flowInstanceId] = p.currentInstanceId } }
        assertEquals(fullCurrent, projection(r), "Restoring only the current link restores the exact prior baseline")
        positive(p)
        assertEquals(localBefore, originals(p))
        println("REQUISITION_EXECUTION_RESULT " + JsonUtils.toJson(mapOf(
            "kind" to "old-flow", "currentId" to p.currentId, "oldFlow" to oldInstance,
            "currentFlow" to p.currentInstanceId, "denial" to denied.message, "allTablesUnchanged" to true,
            "currentFlowRestoredPositive" to true, "localInputsUnchanged" to true,
            "schema" to P.field<String>(r, "schema"),
        )))
    }
}
