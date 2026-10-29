package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.common.CollectionFlowImporter
import io.hizhengfu.cloud.collection.diagnostic.CollectionRequiredFlowDiagnosticCheck
import io.hizhengfu.cloud.sys.diagnostic.domain.EnumDeploymentCheckState
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.DefinitionStatus
import io.hizhengfu.cloud.flow.service.FlowDefinitionService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.nio.file.Path

class CollectionFlowUpgradePersistenceTest {
    @TempDir lateinit var dir:Path
    private fun database():Database=Database.connect("jdbc:sqlite:${dir.resolve("flows.db")}",driver="org.sqlite.JDBC").also {db->transaction(db){SchemaUtils.create(FlowDefinitions,FlowNodeDefs,FlowEdgeDefs,FlowInstances);seedFlowRoles()}}
    private fun custom(keyValue:String,state:DefinitionStatus)=FlowDefinitionDao.new {
        key=keyValue;name="馆方自定义原件";version=8;status=state;modelData="{\"museumOriginal\":true}";createBy="museum-user"
    }
    @Test fun `default import preserves custom drafts active and inactive definitions and is idempotent`() {
        val db=database();transaction(db) {
            val draft=custom("collection_return_2026",DefinitionStatus.DRAFT)
            val active=custom("collection_requisition_2026",DefinitionStatus.ACTIVE)
            val inactive=custom("collection_acquisition_2026",DefinitionStatus.DISABLED)
            val originals=listOf(draft,active,inactive).associate {it.id.value to Triple(it.modelData,it.status,it.version)}
            val importer=CollectionFlowImporter(FlowDefinitionService());val result=importer.importAll()
            assertTrue(result.any {it.contains("保留馆方原定义")})
            assertTrue(FlowDefinitionDao.all().any {it.key=="collection_deregister_2026" && it.status==DefinitionStatus.ACTIVE})
            val count=FlowDefinitionDao.all().count();importer.importAll();assertEquals(count,FlowDefinitionDao.all().count())
            val readiness=CollectionRequiredFlowDiagnosticCheck(FlowDefinitionService()).check()
            assertEquals(EnumDeploymentCheckState.FAIL,readiness.state)
            assertTrue(readiness.safeFacts.single {it.name=="missingActiveFlows"}.value.contains("collection_return_2026"))
            originals.forEach {(id,original)->val r=FlowDefinitionDao[id];assertEquals(original,Triple(r.modelData,r.status,r.version))}
        }
    }
    @Test fun `old activity and new draft do not satisfy the actual 2026 business keys`() {
        val db=database();transaction(db) {
            custom("collection_return",DefinitionStatus.ACTIVE)
            custom("collection_return_2026",DefinitionStatus.DRAFT)
            val check=CollectionRequiredFlowDiagnosticCheck(FlowDefinitionService())
            val before=FlowDefinitionDao.all().count();assertEquals(EnumDeploymentCheckState.FAIL,check.check().state);assertEquals(before,FlowDefinitionDao.all().count())
            CollectionFlowImporter(FlowDefinitionService()).importAll(true)
            assertEquals(EnumDeploymentCheckState.PASS,check.check().state)
        }
    }
    @Test fun `explicit force creates versions while retaining custom draft original model`() {
        val db=database();transaction(db) {
            val draft=custom("collection_return_2026",DefinitionStatus.DRAFT)
            CollectionFlowImporter(FlowDefinitionService()).importAll(true)
            assertEquals("{\"museumOriginal\":true}",draft.modelData);assertEquals(DefinitionStatus.DRAFT,draft.status)
            assertTrue(FlowDefinitionDao.all().any {it.key==draft.key && it.id!=draft.id && it.status==DefinitionStatus.ACTIVE})
        }
    }
    @Test fun `publish failure escapes importer and rolls back enclosing install transaction`() {
        val db=database();val service=spy(FlowDefinitionService())
        doThrow(IllegalStateException("cannot publish")).`when`(service).publish(anyString(),anyString())
        assertThrows(IllegalStateException::class.java){transaction(db){CollectionFlowImporter(service).importAll()}}
        transaction(db){assertEquals(0,FlowDefinitionDao.all().count())}
    }
}
