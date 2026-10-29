package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.sys.system.dao.Dicts
import io.hizhengfu.cloud.sys.system.dao.DictTypes
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/** 通过既有版本升级入口订正系统显示名称，保留编号、技术码、馆方自定义名称和历史情形。 */
internal object CollectionBusinessTerminologyUpgrade {
    fun apply() {
        listOf(
            Triple(CollectionConst.DICT_LIFE_STATUS, "DEREGISTERED", "已注销" to "已退出馆藏"),
            Triple(CollectionConst.DICT_ATTACHMENT_TYPE, "DEREG_APPROVAL", "注销批文" to "退出馆藏批准文件"),
            Triple(CollectionConst.DICT_LEDGER_ACTION, "DEREGISTER", "注销" to "退出馆藏"),
            Triple(CollectionConst.DICT_PRINT_TYPE, "DEREGISTRATION", "注销审批表" to "退出馆藏审批表"),
        ).forEach { (type, value, names) ->
            Dicts.update({ (Dicts.type eq type) and (Dicts.value eq value) and
                (Dicts.isSys eq true) and (Dicts.label eq names.first) }) { it[label] = names.second }
        }
        DictTypes.update({ (DictTypes.type eq CollectionConst.DICT_DEREG_REASON) and
            (DictTypes.isSys eq true) and (DictTypes.name eq "注销原因") }) { it[name] = "退出馆藏原因" }
        DictTypes.update({ (DictTypes.type eq CollectionConst.DICT_DEREG_REASON) and
            (DictTypes.isSys eq true) and (DictTypes.remarks eq "注销原因") }) { it[remarks] = "退出馆藏原因" }
    }
}
