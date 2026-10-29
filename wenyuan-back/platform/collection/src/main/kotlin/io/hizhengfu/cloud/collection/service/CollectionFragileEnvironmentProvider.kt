package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.domain.*
import java.time.LocalDateTime

interface CollectionFragileEnvironmentProvider {
    fun sources(orgId: String): List<FragileEnvironmentDeviceOption>
    fun verify(orgId: String, source: FragileEnvironmentSourceVo, criterion: FragileEnvironmentCriterionVo, startedAt: LocalDateTime, evaluatedAt: LocalDateTime): FragileEnvironmentDeviceEvidence
    fun inspectPeriod(orgId: String, source: FragileEnvironmentSourceVo, criterion: FragileEnvironmentCriterionVo,
                      from: LocalDateTime, to: LocalDateTime): FragilePeriodSourceEvidence
}
