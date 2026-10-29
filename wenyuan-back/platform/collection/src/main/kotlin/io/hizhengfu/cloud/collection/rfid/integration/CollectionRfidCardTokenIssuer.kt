package io.hizhengfu.cloud.collection.rfid.integration

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.rfid.domain.RfidCardTokenIssuer
import io.hizhengfu.cloud.collection.rfid.domain.RfidIssuedUserToken
import io.hizhengfu.cloud.collection.rfid.domain.RfidWorkstationAccessPolicy
import io.hizhengfu.cloud.collection.rfid.security.RfidCardTokens
import io.hizhengfu.cloud.collection.service.OrgScopedReadPolicy
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.security.service.SystemUserDetailsService
import io.hizhengfu.cloud.security.utils.JwtTokenUtil
import io.hizhengfu.cloud.sys.system.dao.Users
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/** collection 自行签发人员卡短期令牌，复用 sys 的用户与 JWT 能力。 */
@Component
class CollectionRfidCardTokenIssuer(
    private val jwtTokenUtil: JwtTokenUtil,
    private val userDetailsService: SystemUserDetailsService,
    private val scope: CollectionScopeResolver,
    @param:Value("\${collection.rfid.card-session-expiration-millis:1800000}")
    private val cardSessionExpirationMillis: Long,
) : RfidCardTokenIssuer {
    override fun issue(
        userId: String,
        workstationOrgId: String,
        requiredAuthorities: Set<String>,
    ): RfidIssuedUserToken {
        val row = Users.selectAll().where { Users.id eq userId }.limit(1).firstOrNull()
            ?: throw BadRequestException("用户不存在")
        val user = userDetailsService.loadUserByUsername(row[Users.loginName])
        val granted = user.permissions.toSet()
        RfidWorkstationAccessPolicy.requireAllowed(granted, requiredAuthorities)
        if (!OrgScopedReadPolicy.isVisible(scope.writableOrgIds(user), workstationOrgId)) {
            throw BadRequestException("无权操作该 RFID 工作站")
        }
        return RfidIssuedUserToken(
            token = RfidCardTokens.issue(jwtTokenUtil, user.username, cardSessionExpirationMillis),
            expirationMillis = cardSessionExpirationMillis,
            userName = user.name,
        )
    }
}
