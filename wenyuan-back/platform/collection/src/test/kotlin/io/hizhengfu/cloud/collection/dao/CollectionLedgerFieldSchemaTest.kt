package io.hizhengfu.cloud.collection.dao

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CollectionLedgerFieldSchemaTest {

    @Test
    fun `collection table contains complete reference ledger columns`() {
        val columns = Collections.columns.map { it.name }.toSet()

        assertTrue(
            columns.containsAll(
                setOf(
                    "original_name",
                    "era_type_1",
                    "era_type_2",
                    "era_value_2",
                    "specific_era",
                    "texture_category_1",
                    "texture_category_2",
                    "weight_range",
                    "weight_unit",
                    "acquire_date_range",
                    "acquire_year",
                    "author",
                    "edition",
                    "volume_no",
                ),
            ),
        )
    }
}
