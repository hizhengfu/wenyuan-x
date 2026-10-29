package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.*

/** Unavailable originals remain visible as warnings, with no downloadable file references. */
internal fun assertUnavailableOriginals(value:Any?) {
    val damaged=mutableListOf<Map<*,*>>()
    fun visit(v:Any?) {
        when(v) {
            is Map<*,*> -> { if(v["evidenceIssue"] is String && v.containsKey("files")) damaged+=v;v.values.forEach(::visit) }
            is Iterable<*> -> v.forEach(::visit)
        }
    }
    visit(value)
    assertTrue(damaged.isNotEmpty(), "Detail must expose the unavailable original")
    damaged.forEach {assertTrue((it["evidenceIssue"] as String).isNotBlank());assertEquals(emptyList<Any>(),it["files"])}
}
