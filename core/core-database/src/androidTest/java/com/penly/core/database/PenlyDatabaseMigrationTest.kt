package com.penly.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenlyDatabaseMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PenlyDatabase::class.java,
        )

    @Test
    fun createV1DatabaseMatchesRoomSchema() {
        helper.createDatabase(TEST_DB, 1).close()
    }

    private companion object {
        const val TEST_DB = "penly-db-migration-test.db"
    }
}
