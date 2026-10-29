package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import java.time.LocalDateTime

interface CollectionSafetyTaskSource {
    val domain:EnumCollectionSafetyDomain
    fun tasks(orgId:String,at:LocalDateTime):List<CollectionSafetyTask>
}
internal fun safetyTask(org:String,domain:EnumCollectionSafetyDomain,kind:String,title:String,source:String,record:String?=null,collection:String?=null,name:String?=null,due:LocalDateTime?=null,at:LocalDateTime,basis:Any,assignees:List<CollectionSafetyAssignee> = emptyList(),status:String?=null,error:String?=null)=CollectionSafetyTask(
    key=CollectionCareEvidence.digest("$domain|$kind|$source|${record.orEmpty()}|${collection.orEmpty()}"),orgId=org,domain=domain,kind=kind,title=title,sourceId=source,recordId=record,collectionId=collection,name=name,dueAt=due,status=status ?: if(due==null)"PENDING" else if(due<=at)"OVERDUE" else "SCHEDULED",basis=CollectionCareEvidence.digest(CollectionCareEvidence.json(basis)),assignees=assignees,evidenceError=error,
)
