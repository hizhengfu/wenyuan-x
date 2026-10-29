package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext

class CollectionSafetyRecipientPersistenceTest {
    private fun configure(id:String,group:String="museum",roles:Set<String> = emptySet(),permissions:Set<String> = setOf(CollectionConst.CARE),admin:Boolean=false) {
        UserDao[id].groupId=group
        RoleUsers.deleteWhere { userId eq id }
        RolePermissions.deleteWhere { roleId eq "role:BASE_$id" }
        actorRole(id,"BASE_$id",permissions)
        roles.forEach {actorRole(id,it)}
        if(admin)actorRole(id,"ADMIN")
    }
    private fun fixture(block:(CollectionSafetyRecipientService)->Unit) {
        val context=StaticApplicationContext();context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        try {
            val db=Database.connect("jdbc:sqlite:file:recipient-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
            transaction(db) {
                SchemaUtils.create(CollectionOrgs);actorSchema()
                CollectionOrgDao.new("museum"){name="本馆";code="M";systemGroupId="museum"};CollectionOrgDao.new("other"){name="外馆";code="O";systemGroupId="other"}
                GroupDao.new("museum"){name="本馆机构";type='1';parentId="0";parentIds="0,"};GroupDao.new("other"){name="外馆机构";type='1';parentId="0";parentIds="0,"}
                GroupDao.new("department"){name="提用部门";type='2';parentId="museum";parentIds="0,museum,"}
                listOf("keeper","coordinator","outside","departmentUser").forEach {id->UserDao.new(id){name=id;loginName=id;password="unused";enabled=true};configure(id)}
                val scope=CollectionScopeResolver("multi","");val directory=CollectionActorDirectory(scope)
                block(CollectionSafetyRecipientService(scope,CollectionResponsibleUserService(directory,scope),directory))
            }
        }finally{context.close()}
    }
    @Test fun `actual role membership enabled status and source permission are all required`()=fixture {service->
        val a=CollectionSafetyAssignee("keeper",setOf(CollectionConst.ROLE_STORAGE_HEAD));val u=UserDao["keeper"]
        configure("keeper",roles=setOf(CollectionConst.ROLE_STORAGE_HEAD))
        assertTrue(service.eligible("museum",EnumCollectionSafetyDomain.CARE,a))
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.EMERGENCY,a))
        configure("keeper",admin=true,permissions=setOf("ADMIN"))
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.CARE,a))
        configure("keeper",group="other",roles=setOf(CollectionConst.ROLE_STORAGE_HEAD),admin=true,permissions=setOf("ADMIN"))
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.CARE,a))
        configure("keeper",roles=setOf(CollectionConst.ROLE_STORAGE_HEAD));u.enabled=false
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.CARE,a))
    }
    @Test fun `disabled responsible person routes only to authorized nominated coordinator`()=fixture {service->
        val task=CollectionSafetyTask("task","museum",EnumCollectionSafetyDomain.CARE,"CHECK","保存状况检查","piece",basis="basis",assignees=listOf(CollectionSafetyAssignee("keeper",setOf(CollectionConst.ROLE_STORAGE_HEAD))))
        UserDao["keeper"].enabled=false;configure("outside",group="other",admin=true,permissions=setOf("ADMIN"))
        val r=service.resolve(task,setOf("coordinator","outside"))
        assertEquals(setOf("coordinator"),r.userIds);assertEquals(setOf("keeper"),r.unavailableUserIds);assertFalse(r.coordinationMissing)
        UserDao["coordinator"].enabled=false
        assertTrue(service.resolve(task,setOf("coordinator","outside")).coordinationMissing)
        assertTrue(service.resolve(task,emptySet()).userIds.isEmpty())
    }
    @Test fun `loan reminders require loan read permission and museum membership`()=fixture {service->
        val assignee=CollectionSafetyAssignee("keeper")
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.LOAN,assignee))
        configure("keeper",permissions=setOf(CollectionConst.SELECT))
        assertTrue(service.eligible("museum",EnumCollectionSafetyDomain.LOAN,assignee))
        configure("keeper",permissions=setOf(CollectionConst.STORAGE))
        assertTrue(service.eligible("museum",EnumCollectionSafetyDomain.LOAN,assignee))
        configure("keeper",group="other",permissions=setOf(CollectionConst.STORAGE))
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.LOAN,assignee))
    }
    @Test fun `use responsibility requires actual department and missing originals use coordinator only`()=fixture {service->
        val a=CollectionSafetyAssignee("departmentUser",departmentId="department")
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.JOINT,a))
        configure("departmentUser",group="department")
        assertTrue(service.eligible("museum",EnumCollectionSafetyDomain.JOINT,a))
        val error=CollectionSafetyTask("error","museum",EnumCollectionSafetyDomain.CARE,"ORIGINAL_ERROR","原件核查","piece",basis="error",status="ORIGINAL_ERROR")
        val r=service.resolve(error,setOf("coordinator"));assertEquals(setOf("coordinator"),r.userIds);assertEquals(setOf("coordinator"),r.coordinatorUserIds)
    }

    @Test fun `administrator role uses the actual system id and still requires current museum membership`()=fixture {service->
        configure("coordinator",permissions=emptySet())
        Roles.insert { it[id]="ADMIN";it[code]="ORDINARY_CUSTOM_ROLE";it[name]="同名主键普通角色" }
        RoleUsers.insert { it[userId]="coordinator";it[roleId]="ADMIN" }
        val assignee=CollectionSafetyAssignee("coordinator")
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.CARE,assignee))
        Roles.insert { it[id]=EnumRole.ADMIN.value;it[code]="ADMIN";it[name]="系统管理员" }
        RoleUsers.insert { it[userId]="coordinator";it[roleId]=EnumRole.ADMIN.value }
        assertTrue(service.eligible("museum",EnumCollectionSafetyDomain.CARE,assignee))
        UserDao["coordinator"].groupId="other"
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.CARE,assignee))
        UserDao["coordinator"].groupId="museum"
        Roles.update({Roles.id eq EnumRole.ADMIN.value}) {it[enabled]=false}
        assertFalse(service.eligible("museum",EnumCollectionSafetyDomain.CARE,assignee))
    }
}
