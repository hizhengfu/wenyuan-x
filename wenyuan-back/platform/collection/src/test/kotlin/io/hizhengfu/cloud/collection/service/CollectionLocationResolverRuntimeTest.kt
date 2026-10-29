package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDateTime

class CollectionLocationResolverRuntimeTest {
    private val at=LocalDateTime.of(2026,9,15,8,0)
    private fun fixture(body:()->Unit) {
        val db=Database.connect("jdbc:sqlite:file:location-resolver-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(Warehouses,Collections,CollectionPlacements,StorageMovements)
            location("root");location("child","root","0,root,");location("other-root")
            item("root-item","root");item("child-item","child");item("other-item","other-root")
            body()
        }
    }
    private fun location(id:String,parent:String="0",path:String="0,",org:String="museum")=WarehouseDao.new(id) {
        orgId=org;name=id;parentId=parent;parentIds=path;sceneType=EnumLocationScene.STORAGE
    }
    private fun item(id:String,location:String?=null,org:String="museum")=CollectionDao.new(id) {
        orgId=org;name=id;totalRegNo=id;warehouseId=location;lifeStatus=EnumCollectionStatus.IN_STORE;texture="材质-$id"
    }
    private fun placement(id:String,collection:String,org:String="museum",approved:Boolean=true,from:LocalDateTime?=at.minusHours(1),to:LocalDateTime?=at.plusHours(1))=CollectionPlacementDao.new(id) {
        orgId=org;sourceBizType="EXHIBITION";sourceBizId="show";collectionId=collection;locationId="child";this.approved=approved;validFrom=from;validTo=to
    }
    private fun resolver(withEmergency:Boolean)=CollectionLocationResolver(if(withEmergency)listOf(IncidentRelocationLocationResolver())else emptyList())

    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `actual descendants remain present when derived paths are missing`(withEmergency:Boolean)=fixture {
        WarehouseDao["child"].parentIds="0,"
        val service=resolver(withEmergency)
        assertEquals(setOf("root-item","child-item"),service.collectionIdsAt("root",at).toSet())
        assertEquals(setOf("材质-root-item","材质-child-item"),service.texturesAt("root",at))
        assertEquals("0,",WarehouseDao["child"].parentIds,"查询不能自动改写历史路径")
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `forged derived ancestors do not add unrelated branches`(withEmergency:Boolean)=fixture {
        WarehouseDao["other-root"].parentIds="0,root,"
        assertEquals(setOf("root-item","child-item"),resolver(withEmergency).collectionIdsAt("root",at).toSet())
        assertEquals("0,",WarehouseDao["root"].parentIds)
        assertEquals("0,root,",WarehouseDao["other-root"].parentIds)
    }
    @ParameterizedTest @ValueSource(strings=["foreign","scene","cycle"])
    fun `invalid actual branches are rejected rather than used as protection scope`(kind:String)=fixture {
        when(kind) {
            "foreign"->WarehouseDao["child"].orgId="other"
            "scene"->WarehouseDao["child"].sceneType=EnumLocationScene.EXHIBITION
            "cycle"->WarehouseDao["root"].parentId="child"
        }
        assertThrows(BadRequestException::class.java) {resolver(true).collectionIdsAt("root",at)}
    }
    @ParameterizedTest @ValueSource(booleans=[false,true])
    fun `foreign placement rows cannot locate a local collection`(withEmergency:Boolean)=fixture {
        placement("foreign-placement","other-item",org="other")
        assertEquals(setOf("root-item","child-item"),resolver(withEmergency).collectionIdsAt("root",at).toSet())
    }
    @Test fun `stored and displayed foreign collections are excluded without requiring an override provider`()=fixture {
        item("foreign-stored","child",org="other")
        item("foreign-displayed",org="other");placement("wrong-reference","foreign-displayed")
        val service=resolver(false)
        assertEquals(setOf("root-item","child-item"),service.collectionIdsAt("root",at).toSet())
        assertEquals(setOf("材质-root-item","材质-child-item"),service.texturesAt("root",at))
    }
    @Test fun `override results cannot introduce missing or foreign collection identities`()=fixture {
        item("foreign",org="other")
        val provider=object:CollectionLocationOverrideProvider {
            override fun resolve(orgId:String,locationIds:Set<String>,at:LocalDateTime,existing:Set<String>):Set<String> {
                assertEquals("museum",orgId);assertEquals(setOf("root","child"),locationIds)
                return existing+setOf("foreign","missing")
            }
        }
        assertEquals(setOf("root-item","child-item"),CollectionLocationResolver(listOf(provider)).collectionIdsAt("root",at).toSet())
    }
    @ParameterizedTest @ValueSource(strings=["unapproved","not-started","future","ended","starts-now","ends-now"])
    fun `displayed membership retains approval and exact time boundary rules`(kind:String)=fixture {
        val visible=kind in setOf("starts-now","ends-now")
        placement("timed","other-item",approved=kind!="unapproved",from=when(kind) {"not-started"->null;"future"->at.plusSeconds(1);"starts-now"->at;else->at.minusHours(1)},to=when(kind) {"ended"->at.minusSeconds(1);"ends-now"->at;else->null})
        val expected=setOf("root-item","child-item")+if(visible)setOf("other-item")else emptySet()
        assertEquals(expected,resolver(true).collectionIdsAt("root",at).toSet())
    }
    @Test fun `local actual location overrides may remove and add items without duplicates`()=fixture {
        val provider=object:CollectionLocationOverrideProvider {
            override fun resolve(orgId:String,locationIds:Set<String>,at:LocalDateTime,existing:Set<String>)=(existing-"child-item")+"other-item"
        }
        assertEquals(setOf("root-item","other-item"),CollectionLocationResolver(listOf(provider)).collectionIdsAt("root",at).toSet())
    }
    @Test fun `unknown location is empty and unrelated damaged branches do not expand the requested tree`()=fixture {
        WarehouseDao["other-root"].parentId="other-root"
        val service=resolver(true)
        assertTrue(service.collectionIdsAt("missing",at).isEmpty());assertTrue(service.texturesAt("missing",at).isEmpty())
        assertEquals(setOf("root","child"),service.descendantLocationIds("root").toSet())
    }
    @Test fun `wide actual subtrees include grandchildren across query batches`()=fixture {
        repeat(205) {i->location("wide-$i","root","0,");location("deep-$i","wide-$i","0,");item("item-$i","deep-$i")}
        val service=resolver(false)
        assertEquals(412,service.descendantLocationIds("root").size)
        assertEquals((0..204).map {"item-$it"}.toSet()+setOf("root-item","child-item"),service.collectionIdsAt("root",at).toSet())
    }

    @ParameterizedTest @ValueSource(strings=["normal","display","emergency-in","emergency-out","later-normal","future-emergency"])
    fun `batch resolution matches individual roots with bounded scoped movement reads`(kind:String)=fixture {
        if(kind=="display")placement("display","other-item")
        if(kind.startsWith("emergency") || kind in setOf("later-normal","future-emergency")) {
            StorageMovementDao.new("emergency") {
                orgId="museum";collectionId="other-item";reason=EnumMoveReason.EMERGENCY
                type=if(kind in setOf("emergency-out","future-emergency"))EnumMoveType.OUT else EnumMoveType.IN
                fromWarehouseId="other-root";toWarehouseId="child"
                operatedAt=if(kind=="future-emergency")at.plusSeconds(1)else at.minusMinutes(1)
            }
            if(kind=="later-normal")StorageMovementDao.new("normal") {
                orgId="museum";collectionId="other-item";reason=EnumMoveReason.RELOCATION
                type=EnumMoveType.IN;toWarehouseId="other-root";operatedAt=at
            }
        }
        val service=resolver(true)
        val roots=listOf("root","child","other-root")
        val expected=roots.associateWith {service.collectionIdsAt(it,at).toSet()}
        val queries=mutableListOf<String>()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(object:org.jetbrains.exposed.v1.core.SqlLogger {
            override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext,transaction:Transaction) {queries+=context.sql(transaction)}
        })
        val actual=service.collectionsAt("museum",roots,at).mapValues {it.value.map(LocatedCollection::id).toSet()}
        assertEquals(expected,actual)
        assertEquals(3,queries.count {it.startsWith("SELECT DISTINCT") && it.contains("FROM col_movement")})
        assertTrue(queries.filter { it.contains("FROM col_movement") }.none { it.contains("col_movement.*") })
        assertTrue(queries.count {it.startsWith("SELECT") }<=11,queries.joinToString("\n"))
    }

    @Test fun `thousands of movement versions still return one current and one future row per item`()=fixture {
        val metrics=io.hizhengfu.cloud.common.utils.RuntimeWorkMetrics
        repeat(2000) { n ->
            StorageMovements.insert {
                it[id]="history-%04d".format(n);it[orgId]="museum";it[collectionId]="other-item"
                it[reason]=EnumMoveReason.EMERGENCY;it[type]=EnumMoveType.IN;it[toWarehouseId]="child"
                it[operatedAt]=at.minusSeconds((2001-n).toLong())
            }
        }
        StorageMovements.insert {
            it[id]="future";it[orgId]="museum";it[collectionId]="other-item";it[reason]=EnumMoveReason.EMERGENCY
            it[type]=EnumMoveType.OUT;it[fromWarehouseId]="child";it[operatedAt]=at.plusSeconds(1)
        }
        val sql=mutableListOf<String>()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().addLogger(object:org.jetbrains.exposed.v1.core.SqlLogger {
            override fun log(context:org.jetbrains.exposed.v1.core.statements.StatementContext,transaction:Transaction) {sql+=context.sql(transaction)}
        })
        val previousRows=metrics.snapshots().firstOrNull {it.name=="test.location.history"}?.returnedRows ?: 0
        val result=metrics.measure("test.location.history") { IncidentRelocationLocationResolver().latest("museum",setOf("other-item"),at) }
        assertEquals(mapOf("other-item" to "child"),result)
        assertEquals(2,sql.size)
        assertEquals(2L,metrics.snapshots().single {it.name=="test.location.history"}.returnedRows - previousRows)
        assertTrue(sql.all {it.contains("NOT EXISTS")})
        // SQLite 执行计划仅验证测试方言能找到联合索引，不代替现场 PostgreSQL/DM 计划。
        val plan=org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
            "EXPLAIN QUERY PLAN SELECT collection_id FROM col_movement WHERE org_id = 'museum' AND reason = ${EnumMoveReason.EMERGENCY.ordinal} AND from_warehouse_id = 'child'", explicitStatementType=org.jetbrains.exposed.v1.core.statements.StatementType.SELECT
        ) {rs -> buildList {while(rs.next())add(rs.getString(4))} }.orEmpty()
        assertTrue(plan.any {it.contains("USING COVERING INDEX") || it.contains("USING INDEX")},plan.toString())
    }

    @ParameterizedTest @ValueSource(strings=["unapproved","not-started","future","ended","starts-now","ends-now"])
    fun `batch display resolution preserves exact approval and time boundaries`(kind:String)=fixture {
        placement("timed","other-item",approved=kind!="unapproved",from=when(kind) {"not-started"->null;"future"->at.plusNanos(1_000_000);"starts-now"->at;else->at.minusHours(1)},to=when(kind) {"ended"->at.minusNanos(1_000_000);"ends-now"->at;else->null})
        val service=resolver(true)
        val roots=listOf("root","child")
        val expected=roots.associateWith {service.collectionIdsAt(it,at).toSet()}
        assertEquals(expected,service.collectionsAt("museum",roots,at).mapValues {it.value.map(LocatedCollection::id).toSet()})
    }

    @ParameterizedTest @ValueSource(strings=["foreign","scene","cycle"])
    fun `batch rejects corrupt reachable branches`(kind:String)=fixture {
        when(kind) {
            "foreign"->WarehouseDao["child"].orgId="other"
            "scene"->WarehouseDao["child"].sceneType=EnumLocationScene.EXHIBITION
            "cycle"->WarehouseDao["root"].parentId="child"
        }
        assertThrows(BadRequestException::class.java) {resolver(true).collectionsAt("museum",listOf("root","child"),at)}
    }

    @Test fun `batch keeps missing foreign and inactive roots out and sees local changes`()=fixture {
        location("foreign",org="other");location("inactive")
        WarehouseDao["inactive"].status=io.hizhengfu.cloud.common.domain.EnumContentStatus.DELETE
        WarehouseDao["other-root"].parentId="other-root"
        WarehouseDao["child"].parentIds="forged,"
        val service=resolver(false)
        assertEquals(setOf("root"),service.collectionsAt("museum",listOf("root","missing","foreign","inactive"),at).keys)
        CollectionDao["child-item"].texture="变化后的材质"
        assertEquals("变化后的材质",service.collectionsAt("museum",listOf("root"),at).getValue("root").single {it.id=="child-item"}.texture)
    }

    @Test fun `batch does not trust foreign placement or override identities`()=fixture {
        item("foreign",org="other");placement("foreign-placement","other-item",org="other")
        placement("wrong-item","foreign")
        val provider=object:CollectionLocationOverrideProvider {
            override fun resolve(orgId:String,locationIds:Set<String>,at:LocalDateTime,existing:Set<String>)=existing+setOf("foreign","missing")
        }
        assertEquals(setOf("root-item","child-item"),CollectionLocationResolver(listOf(provider))
            .collectionsAt("museum",listOf("root"),at).getValue("root").map(LocatedCollection::id).toSet())
    }

    @Test fun `batch handles wide overlapping roots without per location queries`()=fixture {
        repeat(205) {i->location("wide-$i","root","0,");location("deep-$i","wide-$i","0,");item("item-$i","deep-$i")}
        val result=resolver(false).collectionsAt("museum",listOf("root","child")+(0..204).map {"wide-$it"},at)
        assertEquals(207,result.getValue("root").size)
        (0..204).forEach {assertEquals(listOf("item-$it"),result.getValue("wide-$it").map(LocatedCollection::id))}
    }

}
