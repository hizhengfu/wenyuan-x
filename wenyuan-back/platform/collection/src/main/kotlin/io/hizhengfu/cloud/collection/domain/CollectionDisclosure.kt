package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonInclude

enum class EnumDisclosureSubject { RESOURCE, ACQUISITION, EXIT_CONSULTATION, EXIT_RESULT, TRANSFER_RESULT, RESEARCH_ACHIEVEMENT }
enum class EnumDisclosureDecision { PUBLIC, WITHHELD }
enum class EnumDisclosureRestriction { STATE_SECRET, BUSINESS_SECRET, WORK_SECRET, PERSONAL_PRIVACY }
enum class EnumDisclosureHandling { NONE_PRESENT, REDACTED, WITHHELD }
enum class EnumPublicFact { TOTAL_REG_NO, NAME, CATEGORY, ERA, LEVEL, MATERIAL, QUANTITY, UNIT, DIMENSIONS, MANAGEMENT, USE, DISPOSITION_PLAN, ACCOUNT_DISPOSITION, OBJECT_DISPOSITION }
enum class EnumDisclosureChannel { MUSEUM_SITE, OFFICIAL_WEBSITE, ANNUAL_REPORT, MEDIA, AUTHORITY_WEBSITE }
enum class EnumDisclosureEvent { PUBLISHED, WITHDRAWN }

data class DisclosureRestrictionVo(val handling: EnumDisclosureHandling, val explanation: String)
data class PublicExpertOpinionVo(val sourceOpinionId: String, val publicExpertLabel: String, val opinion: String, val agree: Boolean)
/** Deliberate public copy only. Never serialize CollectionVo or private review evidence to a feed. */
data class CollectionPublicContentVo(
    val title: String, val body: String, val feedbackInstructions: String,
    val facts: Map<EnumPublicFact, String> = emptyMap(), val imageUrls: List<String> = emptyList(),
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY) val expertOpinions: List<PublicExpertOpinionVo> = emptyList(),
)
data class CollectionDisclosureReviewVo(
    val sourceRevision: String, val decision: EnumDisclosureDecision,
    val reviewedAt: LocalDateTime, val reviewerUserId: String, val signedReviewFileIds: String,
    val restrictions: Map<EnumDisclosureRestriction, DisclosureRestrictionVo>,
    val content: CollectionPublicContentVo? = null, val publicContentFileIds: String? = null,
    val withholdingReason: String? = null,
    val replacementReason: String? = null, val replacementFileIds: String? = null,
)
data class CollectionDisclosureEventVo(
    val action: EnumDisclosureEvent, val channel: EnumDisclosureChannel,
    val location: String, val occurredAt: LocalDateTime, val recorderUserId: String,
    val evidenceFileIds: String, val publicContentFileIds: String? = null,
    val revokesPublicationId: String? = null, val reason: String? = null,
)
data class DisclosureFeedStopVo(val reason: String)
/** A false available value is a removal instruction; it contains no old public text or private reason. */
data class CollectionPublicFeedVo(
    val id: String, val orgId: String, val subject: EnumDisclosureSubject,
    val available: Boolean, val publicVersionId: String? = null, val version: Int? = null,
    val content: CollectionPublicContentVo? = null,
)
