package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumRepType
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import java.util.stream.Stream
import javax.sql.DataSource
import io.hizhengfu.cloud.collection.service.ArchivePostgresEvidenceSupport as P

/** 12-C72: ten listed registrations, current ordinary writer, actual archive INSERT, separate PG transaction. */
class ReproductionArchiveWriterPostgresTest {
    @TempDir lateinit var dir:Path
    companion object {
        @JvmStatic fun stages():Stream<Arguments> = listOf(EnumRepType.COPY,EnumRepType.RUBBING).flatMap {type->
            listOf("plan","administrative","start","safety","output","complete","stop","clearStop","use","return").flatMap {stage->
                listOf("role","account","group","museum").flatMap {change->listOf(false,true).map {rollback->Arguments.of(type,stage,change,rollback)}}
            }
        }.stream()
    }
    @Tag("postgres-concurrency") @ParameterizedTest(name="{0} {1} writer {2} rollback={3}") @MethodSource("stages")
    fun `ten archive registrations reread ordinary writer after independent commit or rollback`(type:EnumRepType,stage:String,change:String,rollback:Boolean) {
        val fixture=ReproductionWriterRuntimeTest().apply {dir=this@ReproductionArchiveWriterPostgresTest.dir}
        P.call<Unit>(fixture,"fixture",type,true,{r:Any->
            assertTrue(P.field<Boolean>(r,"postgres"),"Requires isolated development PostgreSQL")
            val scope=P.field<io.hizhengfu.cloud.collection.common.CollectionScopeResolver>(r,"scope")
            P.db(r) {assertFalse(EnumRole.ADMIN.value in CollectionActorDirectory(scope).requireActive("operator").roles)}
            P.call<Unit>(fixture,"prepare",r,stage)
            P.call<Unit>(fixture,"trigger",r,stage,"PERFORM pg_advisory_xact_lock(7100482)")
            val business=arrayOf(Collections,Reproductions,ReproductionPlans,ReproductionSafetyRecords,ReproductionOutputs,
                ReproductionOutputUses,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,
                CollectionOperationLocks,io.hizhengfu.cloud.sys.file.dao.Files,io.hizhengfu.cloud.flow.dao.FlowInstances,io.hizhengfu.cloud.flow.dao.FlowInstanceLogs)
            val before=P.db(r) {settledSafetySelectionState(*business)}
            val qualificationBefore=P.db(r) {settledSafetySelectionState(Users,RoleUsers,RolePermissions,CollectionOrgs)}
            val result=P.archiveChange(P.field<DataSource>(r,"source"),P.field<TransactionTemplate>(r,"tx"),7100482,
                P.call<String>(fixture,"mutation",r,change),rollback) {P.call<String>(fixture,"operation",r,stage)}
            if(rollback) {
                assertTrue(result.getOrThrow().isNotBlank())
                assertNotEquals(before,P.db(r) {settledSafetySelectionState(*business)})
                assertEquals(qualificationBefore,P.db(r) {settledSafetySelectionState(Users,RoleUsers,RolePermissions,CollectionOrgs)})
            } else {
                assertTrue(result.exceptionOrNull() is BadRequestException,"Committed writer change must reject: $result")
                assertEquals(before,P.db(r) {settledSafetySelectionState(*business)},"Business rows, originals, refs and old approvals must fully roll back")
                assertNotEquals(qualificationBefore,P.db(r) {settledSafetySelectionState(Users,RoleUsers,RolePermissions,CollectionOrgs)},"External committed qualification change must remain")
            }
        })
    }
}
