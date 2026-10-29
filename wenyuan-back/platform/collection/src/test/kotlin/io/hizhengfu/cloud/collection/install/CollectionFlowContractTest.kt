package io.hizhengfu.cloud.collection.install

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CollectionFlowContractTest {

    @Test
    fun `core collection businesses use flow engine instead of local status only`() {
        val root = projectRoot()
        val contracts = listOf(
            FlowContract("征集入藏", "Acquisition", "Acquisitions", "collection_acquisition_2026", "FLOW_ACQUISITION"),
            FlowContract("提用出入库", "Requisition", "Requisitions", "collection_requisition_2026", "FLOW_REQUISITION"),
            FlowContract("鉴定定级", "Appraisal", "Appraisals", "collection_appraisal_2026", "FLOW_APPRAISAL"),
            // 修复保护流程契约随模块迁移至 module-collection-restoration，本文件不再断言
            FlowContract("借展外展", "Loan", "Loans", "collection_loan_2026", "FLOW_LOAN"),
            FlowContract("藏品展览", "Exhibition", "Exhibitions", "collection_exhibition", "FLOW_EXHIBITION"),
            FlowContract("归还管理", "CollectionReturn", "CollectionReturns", "collection_return_2026", "FLOW_RETURN"),
            FlowContract("退出管理", "Deregistration", "Deregistrations", "collection_deregister_2026", "FLOW_DEREGISTER"),
            FlowContract("复制拓印", "Reproduction", "Reproductions", "collection_reproduction", "FLOW_REPRODUCTION"),
            FlowContract("藏品盘点", "Inventory", "Inventories", "collection_inventory", "FLOW_INVENTORY"),
            FlowContract("跨馆调拨", "CollectionTransfer", "CollectionTransfers", "collection_transfer_2026", "FLOW_TRANSFER"),
            FlowContract("人员入库申请", "WarehouseEntryApplication", "WarehouseEntryApplications", "collection_warehouse_entry_2026", "FLOW_WAREHOUSE_ENTRY"),
        )

        val violations = contracts.flatMap { it.validate(root) }

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `collection flow definitions use legal reject types`() {
        val root = projectRoot()
        val allowedRejectTypes = setOf("BACK_TO_START", "BACK_TO_PREV", "BACK_TO_NODE", "TERMINATE")
        val rejectTypePattern = """"rejectType"\s*:\s*"([^"]+)"""".toRegex()
        val flowDir = root.resolve("src/main/resources/flow")

        val violations = Files.list(flowDir).use { files ->
            files
                .filter { it.fileName.toString().endsWith(".json") }
                .map { file ->
                    val source = Files.readString(file)
                    rejectTypePattern.findAll(source)
                        .map { match -> file.fileName.toString() to match.groupValues[1] }
                        .filter { (_, rejectType) -> rejectType !in allowedRejectTypes }
                        .map { (fileName, rejectType) -> "$fileName uses illegal rejectType $rejectType" }
                        .toList()
                }
                .toList()
                .flatten()
        }

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `flow controller methods can be overridden to keep business permissions`() {
        val flowController = Files.readString(
            repositoryRoot(projectRoot()).resolve("module/flow/src/main/kotlin/io/hizhengfu/cloud/flow/api/FlowController.kt"),
        )
        val requiredOpenMethods = listOf("submit", "cancel", "void", "flowInfo")

        val violations = requiredOpenMethods
            .filterNot { method -> flowController.contains("open fun $method(") }
            .map { method -> "FlowController.$method must be open so business controllers can keep @PreAuthorize" }

        assertEquals(emptyList<String>(), violations)
    }

    private data class FlowContract(
        val label: String,
        val classPrefix: String,
        val tableObject: String,
        val definitionKey: String,
        val definitionKeyConstant: String,
    ) {
        fun validate(root: Path): List<String> {
            val dao = read(root, "src/main/kotlin/io/hizhengfu/cloud/collection/dao/${classPrefix}Dao.kt")
            val service = read(root, "src/main/kotlin/io/hizhengfu/cloud/collection/service/${classPrefix}Service.kt")
            val controller = read(root, "src/main/kotlin/io/hizhengfu/cloud/collection/api/${classPrefix}Controller.kt")
            val flowResource = root.resolve("src/main/resources/flow/$definitionKey.json")
            return buildList {
                if (!dao.contains("object $tableObject : FlowUIDTable")) add("$label DAO must inherit FlowUIDTable")
                if (!service.contains(": FlowService<")) add("$label Service must inherit FlowService")
                if (!service.contains("override fun definitionKey()")) add("$label Service must provide definitionKey")
                if (!service.contains(definitionKeyConstant)) add("$label Service must bind definition key constant $definitionKeyConstant")
                if (classPrefix == "WarehouseEntryApplication") {
                    // 此控制器提供强制原馆/版本参数，不能再暴露不带参数的继承映射。
                    for ((method, operation) in listOf("submit" to "SUBMIT", "cancel" to "CANCEL", "void" to "VOID")) {
                        val signature = controller.substringAfter("fun $method(").substringBefore("): ApiData")
                        if (!signature.contains("@RequestParam expectedOrgId:String") || !signature.contains("@RequestParam expectedRevision:String"))
                            add("$label $method must require original museum and revision")
                        if (!controller.contains("service.operateConfirmed(id,FlowUserOperation.$operation,expectedOrgId,expectedRevision)"))
                            add("$label $method must delegate to its confirmed flow operation")
                        if (!service.contains("FlowUserOperation.$operation->$method(id)"))
                            add("$label confirmed $method must use inherited FlowService operation")
                    }
                    if (!controller.contains("service.getFlowInfo(id)")) add("$label must retain FlowService flow details")
                    if (controller.contains(": FlowController<")) add("$label must not inherit unconfirmed write routes")
                } else {
                    if (!controller.contains(": FlowController<")) add("$label Controller must inherit FlowController")
                    if (!controller.contains("override fun flowService()")) add("$label Controller must bind FlowController to its FlowService")
                    if (!controller.contains("override fun submit(")) add("$label Controller must override submit to keep write permissions")
                    if (!controller.contains("override fun cancel(")) add("$label Controller must override cancel to keep write permissions")
                    if (!controller.contains("override fun void(")) add("$label Controller must override void to keep write permissions")
                    if (!controller.contains("override fun flowInfo(")) add("$label Controller must override flowInfo to keep read permissions")
                    if (controller.contains(".submit(id)") && !controller.contains("super.submit(id)")) add("$label Controller submit must delegate to FlowController")
                    if (controller.contains(".cancel(id)") && !controller.contains("super.cancel(id)")) add("$label Controller cancel must delegate to FlowController")
                    if (controller.contains(".void(id)") && !controller.contains("super.void(id)")) add("$label Controller void must delegate to FlowController")
                    if (controller.contains(".getFlowInfo(id)") && !controller.contains("super.flowInfo(id)")) add("$label Controller flowInfo must delegate to FlowController")
                }
                if (!Files.isRegularFile(flowResource)) add("$label must provide flow definition resource $definitionKey.json")
            }
        }

        private fun read(root: Path, relative: String): String = Files.readString(root.resolve(relative))
    }

    private fun projectRoot(): Path {
        val userDir = Path.of(System.getProperty("user.dir"))
        return listOf(
            userDir,
            userDir.resolve("module/collection"),
        ).first { Files.isDirectory(it.resolve("src/main/kotlin/io/hizhengfu/cloud/collection")) }
    }

    private fun repositoryRoot(collectionRoot: Path): Path = collectionRoot.parent.parent
}
