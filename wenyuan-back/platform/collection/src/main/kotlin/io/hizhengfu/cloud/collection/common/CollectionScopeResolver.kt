package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.sys.system.dao.Groups
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.CollectionOrgTreeScope
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.EnumRole
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SecurityUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 单馆 / 多馆作用域解析。
 *
 * - `collection.mode = single`（默认）：当前业务读取限后端设置的默认收藏单位。
 * - `collection.mode = multi`：多馆，按登录用户机构（groupId/groupIds）过滤。
 * - 多馆模式下系统管理员角色可读写全部收藏单位；跨馆权限只放开读范围，不放开写范围。
 *
 * 从已认证身份推导隔离（藏品全为后台登录用户），比信任前端 header 更安全。
 */
@Component
class CollectionScopeResolver(
    private val mode: String,
    private val defaultOrgId: String,
) {
    private var backendDefaults: CollectionDefaultOrgSettings? = null

    /** Spring 运行时从后端设置取默认馆；显式字符串构造仅供独立调用者固定作用域。 */
    @Autowired
    constructor(
        @Value("\${collection.mode:single}") mode: String,
        settings: CollectionDefaultOrgSettings,
    ) : this(mode, "") {
        backendDefaults = settings
    }
    fun isMulti(): Boolean = mode.equals("multi", ignoreCase = true)

    /** 当前任职与跨馆读取资格；历史原件的归属链另行核实，不在此替代其馆别规则。 */
    fun currentReadableOrgIds(user: SecurityUser, crossOrgPermission: String): Set<String>? {
        if (EnumRole.ADMIN.value in user.roles || crossOrgPermission in user.permissions) return null
        val groups = (user.groupIds + setOfNotNull(user.groupId)).filter { currentGroup(it)?.enabled == true }.toSet()
        return ownedOrgIds(groups)
    }

    /** 当前业务读取同时遵守部署馆别；管理员与跨馆资格不能扩大单馆的后端默认馆范围。 */
    fun currentBusinessReadableOrgIds(user: SecurityUser, crossOrgPermission: String): Set<String>? {
        if (isMulti()) return currentReadableOrgIds(user, crossOrgPermission)
        val configured = configuredOrgId()
        val current = currentReadableOrgIds(user, crossOrgPermission)
        // 默认馆只收窄部署范围，不替代普通查阅人的当前任职关系。
        return if (current == null || configured in current) setOf(configured) else DENIED_ORG_IDS
    }

    /** 当前业务提交的馆别；单馆仍核对真实关系，跨馆读取权限不扩大提交范围。 */
    fun currentWritableOrgIds(user: SecurityUser): Set<String>? {
        if (EnumRole.ADMIN.value in user.roles) return null
        val groups = (user.groupIds + setOfNotNull(user.groupId)).filter { currentGroup(it)?.enabled == true }.toSet()
        return ownedOrgIds(groups)
    }

    /** 当前用户可读收藏单位；返回 null = 不过滤。 */
    fun readableOrgIds(crossOrgPermission: String = CollectionConst.CROSS_ORG): Set<String>? {
        if (!isMulti()) return null
        val user = SecurityUtils.getUserDetail()
        if (SecurityUtils.isAdminRole() || user.permissions.contains(crossOrgPermission)) return null
        return ownedOrgIds(user)
    }

    /** 当前用户可写收藏单位；跨馆查看权限不扩大此范围。 */
    fun writableOrgIds(): Set<String>? {
        if (!isMulti()) return null
        return writableOrgIds(SecurityUtils.getUserDetail())
    }

    /** 为人员卡等签发前校验复用同一馆级写范围，不要求先把目标用户写入 SecurityContext。 */
    fun writableOrgIds(user: SecurityUser): Set<String>? {
        if (!isMulti()) return null
        if (EnumRole.ADMIN.value in user.roles) return null
        return ownedOrgIds(user)
    }

    /** 实际所属馆；不使用管理员豁免，也不把总馆的数据查看范围当作任职。 */
    fun isMuseumMember(user: SecurityUser, orgId: String): Boolean {
        return membershipChecker()(user.groupIds + setOfNotNull(user.groupId), orgId)
    }

    /** 任职与单馆的数据查询范围无关；批量派单只读取一次收藏单位映射。 */
    fun membershipChecker(): (Set<String>, String) -> Boolean {
        val museums = CollectionOrgDao.all().toList()
        val active = museums.filter { it.status == EnumContentStatus.NORMAL && it.orgStatus == EnumOrgStatus.ENABLED }.map { it.id.value }.toSet()
        return { groups, orgId ->
            val current = groups.filter { currentGroup(it)?.enabled == true }.toSet()
            orgId in active && orgId in mappedOrgIds(current, museums)
        }
    }

    private fun ownedOrgIds(user: SecurityUser): Set<String> {
        val ids = buildSet {
            user.groupId?.let { add(it) }
            addAll(user.groupIds)
        }
        return ownedOrgIds(ids)
    }

    private fun ownedOrgIds(ids: Set<String>): Set<String> {
        // 多馆但未绑定机构：看不到任何数据
        if (ids.isEmpty()) return DENIED_ORG_IDS
        val museums=CollectionOrgDao.all().toList()
        val nodes=museums.map { CollectionOrgTreeScope.Node(it.id.value,it.parentId) }
        return CollectionOrgTreeScope.expand(mappedOrgIds(ids,museums),nodes).ifEmpty { DENIED_ORG_IDS }
    }

    private fun mappedOrgIds(groupIds: Set<String>,museums: List<CollectionOrgDao>): Set<String> {
        val mapped=museums.filter { !it.systemGroupId.isNullOrBlank() }.groupBy { it.systemGroupId!! }
        val museumIds=museums.map { it.id.value }.toSet()
        val result=groupIds.filterTo(linkedSetOf()) { it in museumIds }
        if(mapped.isNotEmpty()) groupIds.filter { it !in museumIds }.forEach { groupId ->
            var current:String?=groupId
            val visited=mutableSetOf<String>()
            while(current!=null && visited.add(current)) {
                val group=currentGroup(current)?.takeIf { it.enabled } ?: break
                val match=mapped[current]
                // 只取最近映射；分馆部门不扩大到上级总馆和兄弟分馆。
                if(!match.isNullOrEmpty()){result.addAll(match.map { it.id.value });break}
                current=group.parentId.takeIf { it.isNotBlank() && it!="0" }
            }
        }
        return result
    }

    /** 写入归属的收藏单位 id。 */
    fun currentOrgId(): String {
        return if (isMulti()) currentOrgId(SecurityUtils.getUserDetail()) else configuredOrgId()
    }

    /** 已核实当前人员的只读选择器复用主机构映射，不再回到旧登录资料。 */
    fun currentOrgId(user: SecurityUser): String {
        if (isMulti()) {
            val museums=CollectionOrgDao.all().toList()
            val primary=mappedOrgIds(setOfNotNull(user.groupId),museums)
            if(primary.size==1)return primary.single()
            if(primary.size>1)throw BadRequestException("当前主机构对应多个收藏单位，请核实机构映射")
            return mappedOrgIds(user.groupIds,museums).singleOrNull()
                ?: throw BadRequestException("当前用户未明确绑定唯一收藏单位，请核实主机构映射")
        }
        return configuredOrgId()
    }

    private fun configuredOrgId(): String {
        backendDefaults?.let { return it.resolve() }
        if (defaultOrgId.isNotBlank()) return defaultOrgId
        val candidates = CollectionOrgDao.all().limit(2).toList()
        if (candidates.isEmpty()) throw BadRequestException("尚未初始化收藏单位")
        if (candidates.size > 1) {
            throw BadRequestException("单馆模式存在多个收藏单位，请在后端系统设置中选择默认收藏单位后再办理")
        }
        return candidates.single().id.value
    }

    private data class CurrentGroup(val parentId: String, val enabled: Boolean)

    /** 资格复核读取当前机构状态和父级，不复用当前事务先前载入的 GroupDao。 */
    private fun currentGroup(id: String): CurrentGroup? = Groups.select(Groups.parentId, Groups.enabled)
        .where { Groups.id eq id }.singleOrNull()?.let { CurrentGroup(it[Groups.parentId], it[Groups.enabled]) }

    private companion object {
        val DENIED_ORG_IDS = setOf("__none__")
    }
}
