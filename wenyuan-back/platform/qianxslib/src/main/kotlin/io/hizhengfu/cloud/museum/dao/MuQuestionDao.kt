package io.hizhengfu.cloud.museum.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import jakarta.validation.constraints.NotBlank
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime

/**
 * 问答
 * */    
object MuQuestions : UIDTable("mu_question") {
    val title = varchar("title", 255)//标题
    val keywords = varchar("keywords", 512).nullable()//关键字
    val content = text("content").nullable()//内容
    val createTime = datetime("create_time")//创建时间
    val createBy = varchar("create_by", 64).nullable()//创建人
    val updateTime = datetime("update_time").nullable()//更新时间
    val updateBy = varchar("update_by", 64).nullable()//更新人

    fun toModel(row: ResultRow): MuQuestion {
        return MuQuestion(
                id = row[id]?.value,
                title = row[title],
                keywords = row[keywords],
                content = row[content],
                createTime = row[createTime],
                createBy = row[createBy],
                updateTime = row[updateTime],
                updateBy = row[updateBy])
    }
}

class MuQuestionDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<MuQuestionDao>(MuQuestions)

    var title by MuQuestions.title
    var keywords by MuQuestions.keywords
    var content by MuQuestions.content
    var createTime by MuQuestions.createTime
    var createBy by MuQuestions.createBy
    var updateTime by MuQuestions.updateTime
    var updateBy by MuQuestions.updateBy

    fun toModel(): MuQuestion {
        return MuQuestion(
                id = id.value,
                title = title,
                keywords = keywords,
                content = content,
                createTime = createTime,
                createBy = createBy,
                updateTime = updateTime,
                updateBy = updateBy)
    }
}

data class MuQuestion(
        val id: String? = null,
        @field:NotBlank
        val title: String? = null, 
        val keywords: String? = null, 
        val content: String? = null, 
        val createTime: LocalDateTime? = null,
        val createBy: String? = null, 
        val updateTime: LocalDateTime? = null, 
        val updateBy: String? = null)
