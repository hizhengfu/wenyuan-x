package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.common.file.FileReadGuard
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.support.DefaultListableBeanFactory

/** 仅供未建立受保护业务原件的持久化夹具显式装配读取服务。 */
internal fun unprotectedArchiveReadAccessProvider(): ObjectProvider<FileReadAccess> {
    val beans = DefaultListableBeanFactory()
    beans.registerSingleton("testFileReadAccess", FileReadAccess(beans.getBeanProvider(FileReadGuard::class.java)))
    return beans.getBeanProvider(FileReadAccess::class.java)
}

/** 仅用于另有业务策略核验的合成永久原件，不能用于测试真实文件权限。 */
internal fun syntheticProtectedArchiveReadAccessProvider(): ObjectProvider<FileReadAccess> {
    val beans = DefaultListableBeanFactory()
    beans.registerSingleton("syntheticProtectedGuard", object : FileReadGuard {
        override fun isProtected(path: String, storageType: String?) = true
        override fun requireReadable(path: String, storageType: String?) = Unit
    })
    beans.registerSingleton("testFileReadAccess", FileReadAccess(beans.getBeanProvider(FileReadGuard::class.java)))
    return beans.getBeanProvider(FileReadAccess::class.java)
}
