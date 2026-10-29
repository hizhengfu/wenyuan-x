package io.hizhengfu.cloud

import io.hizhengfu.cloud.common.utils.EnvUtils
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableTransactionManagement
@ConfigurationPropertiesScan
@EnableCaching
@EnableScheduling
class QXSLibApplication : CommandLineRunner {

    override fun run(vararg args: String) {
        println("已经启动：「${EnvUtils.name()}」,当前版本：「${EnvUtils.version()}」")
    }
}

fun main(args: Array<String>) {
    runApplication<QXSLibApplication>(*args)
}

