package io.hizhengfu.cloud

import io.hizhengfu.cloud.common.utils.EnvUtils
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching


/**
 * 应用入口
 * */
@SpringBootApplication()
@ConfigurationPropertiesScan
@EnableCaching
class AsaWmsApplication : CommandLineRunner {

    override fun run(vararg args: String) {
        println("已经启动：「${EnvUtils.name()}」,当前版本：「${EnvUtils.version()}」")
    }
}

fun main(args: Array<String>) {
    runApplication<AsaWmsApplication>(*args)
}

