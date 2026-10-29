package io.hizhengfu.cloud.museum.utils

import io.hizhengfu.cloud.common.extend.toLocalDateTime
import io.hizhengfu.cloud.museum.domain.Coordinate
import io.hizhengfu.cloud.museum.domain.RobotWrap
import io.hizhengfu.cloud.museum.domain.Task
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


/**
 * 像素坐标转物理坐标
 * */
fun px2physics(x: Double, y: Double): Coordinate {
    val px2m = 3.5 / 230.0
    val originX = 21.0
    val originY = 260.0
    val tx = (originY - y) * px2m
    val ty = (x - originX) * px2m
    return Coordinate(x = tx, y = ty)
}

/**
 * 计算两点之间距离
 * */
fun distance(c: Coordinate, t: Coordinate): Double {
    //当点位值不存在时，返回-1
    if (c.x == null || c.y == null || t.x == null || t.y == null) return -1.0
    return sqrt(abs(c.x - t.x).pow(2) + abs(c.y - t.y).pow(2));
}


/**
 * 判断任务是否空闲
 * */
fun idleTask(task_list: List<Task>?): Boolean {
    return if (task_list == null) true
    else {
        task_list.first().task_time?.toLocalDateTime()?.isAfter(LocalDateTime.now()) == true
    }
}

/**
 * 机器人空闲与距离选择调度算法
 * 找出空闲机器人，判断哪个机器人距离最近
 * 机器人有可能都空闲，一个空闲,执行顺序是先遍历机器人距离顺序，再找距离最近的空闲机器人
 * @param robots 机器人列表
 * @param coordinate 事件点
 * */
fun dispatch(robots: List<RobotWrap>, coordinate: Coordinate? = null): RobotWrap {
    var tempRobot = robots
    if (coordinate != null) {
        tempRobot = robots.sortedBy {
            //根据当前位置与目标位置排序
            distance(Coordinate(it.position?.x, it.position?.y), coordinate)
        }
    }

    val robot = tempRobot.find {
        //选择第一个未执行的机器人或者任务执行时间是待执行的
        idleTask(it.task_list)
    }

    //根据是否有合适机器人，否则随机调用
    return robot ?: robots.random()
}


fun test() {

//    val t = Duration("2022-11-08T12:39:39.783259Z".toLocalDateTime(), LocalDateTime.now())
//    val interval = (LocalDateTime.now().millis - "2022-11-08T12:39:39.783259Z".toLocalDateTime().millis) / 1000
//
//    println("s-${t.standardMinutes}")
//    println("s-${interval}")
//    println("m-${interval / 60}")
//    println("h-${interval / (60 * 60)}")
}