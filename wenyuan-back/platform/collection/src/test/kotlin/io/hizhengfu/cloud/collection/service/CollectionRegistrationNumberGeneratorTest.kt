package io.hizhengfu.cloud.collection.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CollectionRegistrationNumberGeneratorTest {

    @Test
    fun `next available registration number fills numeric gaps before appending`() {
        val next = CollectionRegistrationNumberGenerator.nextAvailable(setOf("000001", "000003"))

        assertEquals("000002", next)
    }

    @Test
    fun `next available registration number appends after continuous sequence`() {
        val next = CollectionRegistrationNumberGenerator.nextAvailable(setOf("000001", "000002"))

        assertEquals("000003", next)
    }

    @Test
    fun `next available registration number ignores non numeric legacy numbers`() {
        val next = CollectionRegistrationNumberGenerator.nextAvailable(setOf("000001", "旧藏-A"))

        assertEquals("000002", next)
    }
}
