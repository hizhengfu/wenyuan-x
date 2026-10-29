package io.hizhengfu.cloud.collection.domain

import java.time.LocalDateTime
import java.time.LocalDate

enum class EnumArchiveJurisdiction { LOCAL_COMPETENT, NATIONAL_COMPETENT }
enum class EnumArchiveFilingTarget { COMPETENT, NATIONAL }
enum class EnumCollectionArchiveFormat { PAPER, ELECTRONIC, BOTH }
enum class EnumArchiveSection { BASIC, ACQUISITION, IDENTIFICATION, GRADING, STORAGE, CONSERVATION, RESEARCH, EXHIBITION, USE, OTHER_HISTORY }
enum class EnumArchiveSectionStatus { INCLUDED, NOT_OCCURRED, NOT_APPLICABLE, MISSING }

data class ArchiveJurisdictionVo(
    val kind: EnumArchiveJurisdiction, val competentAuthorityName: String,
    val nationalAuthorityName: String, val localArchiveAuthorityName: String, val effectiveOn: LocalDate, val verifiedAt: LocalDateTime,
    val responsibleUserId: String, val basisFileIds: String,
    val replacementReason: String? = null, val replacementFileIds: String? = null,
)
data class ArchiveJurisdictionSnapshotVo(val recordId: String, val version: Int, val content: ArchiveJurisdictionVo)
data class ArchiveSectionVo(val status: EnumArchiveSectionStatus, val reference: String, val fileIds: String? = null)
data class CollectionArchivePacketVo(
    val catalogVersionId: String, val archiveNo: String,
    val format: EnumCollectionArchiveFormat, val preparedAt: LocalDateTime, val archivistUserId: String,
    val physicalLocation: String? = null, val electronicLocation: String? = null,
    val inventoryFileIds: String, val photographFileIds: String, val signedVerificationFileIds: String,
    val sections: Map<EnumArchiveSection, ArchiveSectionVo> = emptyMap(),
    val replacementReason: String? = null, val replacementFileIds: String? = null,
)
data class ArchivePacketSourceVo(
    val totalRegNo: String, val name: String, val catalogVersion: CollectionCatalogVersionVo,
    val source: String, val capturedAt: LocalDateTime,
)
data class ArchivePacketSubmissionVo(
    val target: EnumArchiveFilingTarget, val authorityName: String, val submittedAt: LocalDateTime,
    val submissionNo: String, val submissionFileIds: String,
)
data class ArchivePacketReceiptVo(
    val authorityName: String, val receivedAt: LocalDateTime, val filingNo: String, val receiptFileIds: String,
)
