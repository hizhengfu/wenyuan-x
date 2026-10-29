package io.hizhengfu.cloud.collection

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 藏品管理模块自动配置。
 *
 * 表初始化由 [io.hizhengfu.cloud.collection.install.CollectionInstall]（@Install）在应用启动时执行。
 * 设计基线：docs/superpowers/specs/2026-06-21-collection-management-design.md
 */
@AutoConfiguration
@EnableTransactionManagement
@EnableScheduling
@ComponentScan(basePackages = ["io.hizhengfu.cloud.collection"])
class CollectionAutoConfiguration
