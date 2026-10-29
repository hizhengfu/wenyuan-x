package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DigitalArchiveSnapshotComparisonTest {
    @ParameterizedTest @CsvSource("CAPTURED,rawFileIds", "COMPLETED,storedResourceIds", "RESOURCE_x,fileIds", "RESOURCE_x,parentResourceIds")
    fun `only declared set fields tolerate serialization reordering`(phase:String,field:String) {
        fun json(values:String):String {
            val data="""{"$field":[$values],"packageFiles":{"scene.gltf":"a"}}"""
            return if(phase.startsWith("RESOURCE_"))"""{"content":$data}""" else data
        }
        assertTrue(DigitalArchiveSnapshotComparison.same(phase,json("\"a\",\"b\""),json("\"b\",\"a\"")))
        assertFalse(DigitalArchiveSnapshotComparison.same(phase,json("\"a\",\"a\""),json("\"a\"")))
        assertFalse(DigitalArchiveSnapshotComparison.same(phase,json("\"a\",\"b\""),json("\"a\",\"c\"")))
    }
    @Test fun `file list order unknown properties types and path mapping remain significant`() {
        assertFalse(DigitalArchiveSnapshotComparison.same("PLAN","""{"rawFileIds":["a","b"]}""","""{"rawFileIds":["b","a"]}"""))
        assertFalse(DigitalArchiveSnapshotComparison.same("RESOURCE_x","""{"files":["a","b"]}""","""{"files":["b","a"]}"""))
        assertFalse(DigitalArchiveSnapshotComparison.same("CAPTURED","""{"rawFileIds":[1]}""","""{"rawFileIds":["1"]}"""))
        assertFalse(DigitalArchiveSnapshotComparison.same("CAPTURED","""{"rawFileIds":["a"],"extra":1}""","""{"rawFileIds":["a"]}"""))
        assertFalse(DigitalArchiveSnapshotComparison.same("RESOURCE_x","""{"content":{"packageFiles":{"a":"one"}}}""","""{"content":{"packageFiles":{"a":"two"}}}"""))
    }
}
