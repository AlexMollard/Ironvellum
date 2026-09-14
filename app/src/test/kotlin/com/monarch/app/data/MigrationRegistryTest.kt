package com.monarch.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM invariants over the migration registry. Every failure mode that has
 * actually bitten this repo is covered: an unregistered migration (the
 * MIGRATION_17_18 launch crash), a duplicated registration, and a gap that
 * leaves Room with no path from the shipped baseline to the current version.
 * The expected version comes from the database companion, so bumping `version`
 * without extending the chain fails here immediately.
 */
class MigrationRegistryTest {

    private val migrations = MonarchDatabase.MIGRATIONS
    private val version = MonarchDatabase.VERSION

    /** Shipped baseline: no install below version 11 exists in the wild. */
    private val shippedBaseline = 11

    @Test
    fun chainIsUnbrokenFromShippedBaselineToCurrentVersion() {
        val byStart = migrations.groupBy { it.startVersion }
        var v = shippedBaseline
        while (v < version) {
            val steps = byStart[v].orEmpty()
            assertEquals(
                "Expected exactly one migration $v -> ${v + 1}, found ${steps.size}",
                1,
                steps.size,
            )
            assertEquals(
                "Migration starting at $v must end at ${v + 1}, was ${steps.single().endVersion}",
                v + 1,
                steps.single().endVersion,
            )
            v++
        }
    }

    @Test
    fun highestEndVersionMatchesDatabaseVersion() {
        assertEquals(
            "MIGRATIONS chain must reach the @Database version",
            version,
            migrations.maxOf { it.endVersion },
        )
    }

    @Test
    fun noMigrationRegisteredTwice() {
        val steps = migrations.map { it.startVersion to it.endVersion }
        assertEquals(
            "Duplicate (start, end) registration in MIGRATIONS: " +
                steps.groupBy { it }.filterValues { it.size > 1 }.keys,
            migrations.size,
            steps.distinct().size,
        )
    }

    @Test
    fun noMigrationTargetsBelowShippedBaseline() {
        assertTrue(
            "A migration targets a pre-baseline schema: ${migrations.filter { it.startVersion < shippedBaseline }}",
            migrations.all { it.startVersion >= shippedBaseline },
        )
    }
}
