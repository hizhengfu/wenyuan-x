package io.hizhengfu.cloud.museum.utils

import io.hizhengfu.cloud.museum.domain.Coordinate
import org.junit.jupiter.api.Test

internal class PositionUtilsKtTest {

    @Test
    fun distance() {

        val p = Coordinate(1.0, 2.0)
        val p1 = Coordinate(3.0, 4.0)
        val p3 = Coordinate(2.0, 2.0)
        val p4 = Coordinate(10.0, 20.0)

        println(distance(p3, p))
        println(distance(p3, p1))
        println(distance(p3, p4))
    }

    @Test
    fun test1() {
        test()

    }
}