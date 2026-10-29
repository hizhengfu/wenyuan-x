package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.sys.system.dao.GroupDao
import io.hizhengfu.cloud.sys.system.dao.UserDao

/** 来源联动夹具明确配置事故查阅职责；不改变签署岗位，也不在登录时自动补回撤销的权限。 */
internal fun incidentReaderFixture(vararg ids:String) {
    actorSchema()
    val org=CollectionOrgDao["museum"]
    val group=org.systemGroupId?.takeIf {it.isNotBlank()} ?: "museum".also {org.systemGroupId=it}
    if(GroupDao.findById(group)==null)GroupDao.new(group){name="本馆";type='1';parentId="0";parentIds="0,"}
    ids.forEach {id ->
        if(UserDao.findById(id)==null)UserDao.new(id){name=id;loginName=id;password="unused";enabled=true;groupId=group}
        actorRole(id,"INCIDENT_READER",setOf(CollectionConst.INCIDENT))
    }
}
