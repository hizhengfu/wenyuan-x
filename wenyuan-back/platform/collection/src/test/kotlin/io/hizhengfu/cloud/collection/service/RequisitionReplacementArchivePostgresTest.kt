package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.RequisitionReplacementRequest
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path
import javax.sql.DataSource
import io.hizhengfu.cloud.collection.service.ArchivePostgresEvidenceSupport as P

/** 06-C15 supplements the existing SQLite corruption and PG repeated-entry proofs at the second archive write. */
class RequisitionReplacementArchivePostgresTest {
    @TempDir lateinit var dir:Path
    @Tag("postgres-concurrency") @ParameterizedTest
    @CsvSource("role,false", "account,false", "group,false", "permission,false", "keeper,false",
        "role,true", "account,true", "group,true", "permission,true", "keeper,true")
    fun `both replacement archives roll back on independently committed current duty loss`(change:String,approved:Boolean) {
        for(rollback in listOf(false,true)) {
            val fixture=RequisitionReplacementRuntimeTest().apply {dir=this@RequisitionReplacementArchivePostgresTest.dir}
            P.call<Unit>(fixture,"fixture","multi",true,{r:Any,id:String->
                if(approved)P.call<Unit>(fixture,"approve",r,id,"")
                val request=P.call<RequisitionReplacementRequest>(fixture,"request",r,id)
                val context=P.field<AnnotationConfigApplicationContext>(r,"c")
                val service=context.getBean(RequisitionResponsibilityReplacementService::class.java)
                P.db(r) {assertFalse(EnumRole.ADMIN.value in context.getBean(CollectionActorDirectory::class.java).requireActive("new-director").roles)}
                val (mutation,restore)=when(change) {
                    "role"->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='new-director' AND role_id='role:${CollectionConst.ROLE_DIRECTOR}'" to "INSERT INTO ${RoleUsers.tableName}(user_id,role_id) VALUES('new-director','role:${CollectionConst.ROLE_DIRECTOR}')"
                    "account"->"UPDATE ${Users.tableName} SET enabled=false WHERE id='new-director'" to "UPDATE ${Users.tableName} SET enabled=true WHERE id='new-director'"
                    "group"->"UPDATE ${Users.tableName} SET group_id='g-other' WHERE id='new-director'" to "UPDATE ${Users.tableName} SET group_id='dept' WHERE id='new-director'"
                    "permission"->"DELETE FROM ${RolePermissions.tableName} WHERE role_id='role:BUSINESS' AND permission_id='${CollectionConst.STORAGE}'" to "INSERT INTO ${RolePermissions.tableName}(role_id,permission_id) VALUES('role:BUSINESS','${CollectionConst.STORAGE}')"
                    else->"DELETE FROM ${RoleUsers.tableName} WHERE user_id='new-keeper' AND role_id='role:${CollectionConst.ROLE_STORAGE_HEAD}'" to "INSERT INTO ${RoleUsers.tableName}(user_id,role_id) VALUES('new-keeper','role:${CollectionConst.ROLE_STORAGE_HEAD}')"
                }
                P.db(r) {
                    TransactionManager.current().exec("CREATE FUNCTION pause_replacement_archive() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.phase='${RequisitionReplacementEvidence.CREATED}' THEN PERFORM pg_advisory_xact_lock(7100483); END IF; RETURN NEW; END $$")
                    TransactionManager.current().exec("CREATE TRIGGER pause_replacement_archive AFTER INSERT ON ${CollectionPermanentArchives.tableName} FOR EACH ROW EXECUTE FUNCTION pause_replacement_archive()")
                }
                val before=P.call<Any>(fixture,"state",r)
                val qualificationBefore=P.db(r) {settledSafetySelectionState(Users,RoleUsers,RolePermissions)}
                val result=P.archiveChange(P.field<DataSource>(r,"source"),P.field<TransactionTemplate>(r,"tx"),7100483,mutation,rollback) {service.replace(id,request)}
                if(rollback) {
                    val child=result.getOrThrow();assertTrue(child.isNotBlank())
                    assertEquals(qualificationBefore,P.db(r) {settledSafetySelectionState(Users,RoleUsers,RolePermissions)})
                    P.db(r) {assertEquals(EnumFlowStatus.DRAFT,RequisitionDao[child].flowStatus);assertEquals(child,RequisitionDao[id].supersededById)}
                } else {
                    assertTrue(result.exceptionOrNull() is BadRequestException,"Current duty loss must reject after both archives: $result")
                    assertEquals(before,P.call<Any>(fixture,"state",r),"Both archives, tasks/logs, old approval, reservations and collection positions remain unchanged")
                    assertNotEquals(qualificationBefore,P.db(r) {settledSafetySelectionState(Users,RoleUsers,RolePermissions)})
                    P.db(r) {
                        TransactionManager.current().exec(restore)
                        TransactionManager.current().exec("DROP TRIGGER pause_replacement_archive ON ${CollectionPermanentArchives.tableName}")
                        TransactionManager.current().exec("DROP FUNCTION pause_replacement_archive()")
                    }
                    val child=service.replace(id,request)
                    assertEquals(child,service.replace(id,request),"Restored retry creates one child draft")
                    P.db(r) {assertEquals(EnumFlowStatus.DRAFT,RequisitionDao[child].flowStatus);assertEquals(child,RequisitionDao[id].supersededById);assertEquals(1,RequisitionDao.find {Requisitions.replacesId eq id}.count())}
                }
            })
        }
    }
}
