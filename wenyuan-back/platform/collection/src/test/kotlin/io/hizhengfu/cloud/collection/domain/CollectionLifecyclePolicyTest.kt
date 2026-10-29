package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionLifecyclePolicyTest {

    @Test
    fun `in store collection can enter active business states`() {
        assertTrue(CollectionLifecyclePolicy.canTransition(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.IN_USE))
        assertTrue(CollectionLifecyclePolicy.canTransition(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.RESTORING))
        assertTrue(CollectionLifecyclePolicy.canTransition(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.ON_LOAN))
        assertTrue(CollectionLifecyclePolicy.canTransition(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.PRE_OUT))
        assertTrue(CollectionLifecyclePolicy.canTransition(EnumCollectionStatus.PRE_OUT, EnumCollectionStatus.IN_USE))
        assertTrue(CollectionLifecyclePolicy.canTransition(EnumCollectionStatus.PRE_OUT, EnumCollectionStatus.ON_LOAN))
        assertTrue(CollectionLifecyclePolicy.canTransition(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.DEREGISTERED))
    }

    @Test
    fun `pending collection cannot jump directly to loan`() {
        val ex = assertThrows(BadRequestException::class.java) {
            CollectionLifecyclePolicy.requireTransition(EnumCollectionStatus.PENDING, EnumCollectionStatus.ON_LOAN)
        }

        assertTrue(ex.message!!.contains("生命周期状态不允许"))
    }

    @Test
    fun `deregistered collection is terminal`() {
        val ex = assertThrows(BadRequestException::class.java) {
            CollectionLifecyclePolicy.requireTransition(EnumCollectionStatus.DEREGISTERED, EnumCollectionStatus.IN_STORE)
        }

        assertTrue(ex.message!!.contains("已退出馆藏"))
    }
}
