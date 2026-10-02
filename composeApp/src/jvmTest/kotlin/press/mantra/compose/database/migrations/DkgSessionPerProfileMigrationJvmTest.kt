package press.mantra.compose.database.migrations

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The v21 -> v22 rebuild, against a real database holding a real finished ceremony.
 *
 * The stake is the share. A `DkgSession` row holds `secretShare` and the two pieces
 * of randomness that reconstruct it, and this migration drops and recreates the
 * table that holds them -- with a child table pointing at it `ON DELETE CASCADE`.
 * Get the order wrong and the upgrade silently deletes a group's ability to sign:
 * the messages first, by cascade, and with them every ritual still in flight.
 *
 * So these assert the values through, not merely that rows survived. Foreign keys
 * are turned **on** for the whole test, which is the hostile setting -- it is the
 * one under which a mis-ordered drop would cascade -- so passing here means the
 * migration does not depend on Room having turned them off.
 *
 * Run against the migration's own SQL on a bare connection, as
 * [FrostSigningItemMigrationJvmTest] is and for the same reason: what is under test
 * is the copy and the shape left behind, not Room's version wiring.
 */
class DkgSessionPerProfileMigrationJvmTest {

    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:")

    @AfterTest
    fun close() = connection.close()

    private val alice = "0a".repeat(32)
    private val bob = "0b".repeat(32)
    private val secretShare = "cc".repeat(32)
    private val round1Random = "aa".repeat(32)
    private val round2AuxRandom = "bb".repeat(32)
    private val hostKeyPayload = "01".repeat(33)

    /**
     * The two ritual tables and the room they hang off, verbatim from `schemas/21.json`
     * -- neither of them changed between 20 and 21, which added the parentage table.
     */
    private fun createV21() {
        connection.execSQL("PRAGMA foreign_keys = ON")

        // Only the columns the foreign key needs; this is not the room table under test.
        connection.execSQL("CREATE TABLE IF NOT EXISTS `ChatRoom` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `DkgSession` (`id` TEXT NOT NULL, " +
                "`chatRoomId` TEXT NOT NULL, `coordinatorPublicKey` TEXT NOT NULL, " +
                "`userPublicKey` TEXT NOT NULL, `threshold` INTEGER NOT NULL, " +
                "`participantCount` INTEGER NOT NULL, `participantPublicKeys` TEXT, " +
                "`subject` TEXT, `stage` TEXT NOT NULL, `hostPublicKey` TEXT NOT NULL, " +
                "`round1Random` TEXT NOT NULL, `round2AuxRandom` TEXT NOT NULL, " +
                "`coordinatorRound1` TEXT, `certificate` TEXT, `thresholdPublicKey` TEXT, " +
                "`secretShare` TEXT, `publicShares` TEXT, `recoveryData` TEXT, " +
                "`failureReason` TEXT, `hostKeyApprovedAt` INTEGER, " +
                "`round1ApprovedAt` INTEGER, `round2ApprovedAt` INTEGER, " +
                "`approvalRequestedThrough` TEXT, `parentChatRoomId` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`chatRoomId`) REFERENCES `ChatRoom`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DkgSession_chatRoomId` " +
                "ON `DkgSession` (`chatRoomId`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `DkgParticipantMessage` (`sessionId` TEXT NOT NULL, " +
                "`participantPublicKey` TEXT NOT NULL, `kind` INTEGER NOT NULL, " +
                "`payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`sessionId`, `participantPublicKey`, `kind`), " +
                "FOREIGN KEY(`sessionId`) REFERENCES `DkgSession`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DkgParticipantMessage_sessionId` " +
                "ON `DkgParticipantMessage` (`sessionId`)"
        )

        connection.execSQL("INSERT INTO `ChatRoom` VALUES ('room')")
    }

    /** A ceremony that finished, so the row holds a share worth not losing. */
    private fun insertV21Session(id: String = "ceremony-1", owner: String = alice) =
        connection.execSQL(
            "INSERT INTO `DkgSession` VALUES (" +
                "'$id', 'room', '$owner', '$owner', 2, 3, '$alice,$bob', 'Ekklesia', " +
                "'COMPLETE', '$hostKeyPayload', '$round1Random', '$round2AuxRandom', " +
                "NULL, NULL, '02${"ab".repeat(32)}', '$secretShare', NULL, NULL, NULL, " +
                "1000, 1000, 1000, NULL, NULL, 1000, 1000, 1000)"
        )

    private fun insertV21Message(
        sessionId: String = "ceremony-1",
        participant: String = bob,
        kind: Int = 30450,
    ) = connection.execSQL(
        "INSERT INTO `DkgParticipantMessage` VALUES " +
            "('$sessionId', '$participant', $kind, '$hostKeyPayload', 1000)"
    )

    private fun columns(table: String): List<String> =
        connection.prepare("PRAGMA table_info(`$table`)").use { statement ->
            buildList { while (statement.step()) add(statement.getText(1)) }
        }

    /** The primary-key columns of [table], in key order. */
    private fun primaryKey(table: String): List<String> =
        connection.prepare("PRAGMA table_info(`$table`)").use { statement ->
            buildList {
                while (statement.step()) {
                    // columns: cid, name, type, notnull, dflt_value, pk
                    val position = statement.getInt(5)
                    if (position > 0) add(position to statement.getText(1))
                }
            }
        }.sortedBy { it.first }.map { it.second }

    private fun <T> query(sql: String, read: (androidx.sqlite.SQLiteStatement) -> T): List<T> =
        connection.prepare(sql).use { statement ->
            buildList { while (statement.step()) add(read(statement)) }
        }

    private fun count(table: String): Int =
        query("SELECT COUNT(*) FROM `$table`") { it.getInt(0) }.first()

    @Test
    fun `the key becomes the ceremony and the profile`() = runBlocking {
        createV21()
        insertV21Session()

        MIGRATION_21_22.migrate(connection)

        assertEquals(listOf("id", "userPublicKey"), primaryKey("DkgSession"))
        assertEquals(
            listOf("sessionId", "userPublicKey", "participantPublicKey", "kind"),
            primaryKey("DkgParticipantMessage"),
        )

        // The column order Room checks the table against, unchanged apart from the
        // one insertion.
        assertEquals(
            listOf(
                "id", "chatRoomId", "coordinatorPublicKey", "userPublicKey", "threshold",
                "participantCount", "participantPublicKeys", "subject", "stage",
                "hostPublicKey", "round1Random", "round2AuxRandom", "coordinatorRound1",
                "certificate", "thresholdPublicKey", "secretShare", "publicShares",
                "recoveryData", "failureReason", "hostKeyApprovedAt", "round1ApprovedAt",
                "round2ApprovedAt", "approvalRequestedThrough", "parentChatRoomId",
                "createdAt", "updatedAt", "savedAt",
            ),
            columns("DkgSession"),
        )
        assertEquals(
            listOf("sessionId", "userPublicKey", "participantPublicKey", "kind", "payload", "createdAt"),
            columns("DkgParticipantMessage"),
        )
    }

    /**
     * The one that matters. `DkgParticipantMessage` cascades off `DkgSession`, so a
     * migration that dropped the parent while the child still pointed at it would
     * delete the ritual's messages -- and with foreign keys on, as they are here,
     * it would do it silently.
     */
    @Test
    fun `a finished ceremony keeps its share and its messages`() = runBlocking {
        createV21()
        insertV21Session()
        insertV21Message(participant = bob)
        insertV21Message(participant = alice)

        MIGRATION_21_22.migrate(connection)

        assertEquals(1, count("DkgSession"))
        assertEquals(2, count("DkgParticipantMessage"), "the cascade must not have fired")

        val session = query(
            "SELECT `userPublicKey`, `secretShare`, `round1Random`, `round2AuxRandom`, `stage` " +
                "FROM `DkgSession`"
        ) { listOf(it.getText(0), it.getText(1), it.getText(2), it.getText(3), it.getText(4)) }.single()

        assertEquals(
            listOf(alice, secretShare, round1Random, round2AuxRandom, "COMPLETE"),
            session,
            "every secret the share is rebuilt from has to come through verbatim",
        )

        // Each message is now owned by the profile whose session it hung off.
        assertEquals(
            listOf(alice to alice, alice to bob),
            query(
                "SELECT `userPublicKey`, `participantPublicKey` FROM `DkgParticipantMessage` " +
                    "ORDER BY `participantPublicKey`"
            ) { it.getText(0) to it.getText(1) },
        )
    }

    /** Two ceremonies, so the backfill is shown picking per message rather than one owner. */
    @Test
    fun `each message takes the owner of its own ceremony`() = runBlocking {
        createV21()
        insertV21Session(id = "alices", owner = alice)
        insertV21Session(id = "bobs", owner = bob)
        insertV21Message(sessionId = "alices", participant = bob)
        insertV21Message(sessionId = "bobs", participant = alice)

        MIGRATION_21_22.migrate(connection)

        assertEquals(
            listOf("alices" to alice, "bobs" to bob),
            query(
                "SELECT `sessionId`, `userPublicKey` FROM `DkgParticipantMessage` ORDER BY `sessionId`"
            ) { it.getText(0) to it.getText(1) },
        )
    }

    /**
     * The cascade still works afterwards, which is the half of this a rebuild can
     * quietly lose: a child recreated without its foreign key would leave a deleted
     * room's rituals behind, and nothing in the app would ever notice.
     */
    @Test
    fun `deleting the room still takes the ritual and its messages`() = runBlocking {
        createV21()
        insertV21Session()
        insertV21Message()

        MIGRATION_21_22.migrate(connection)
        connection.execSQL("DELETE FROM `ChatRoom` WHERE `id` = 'room'")

        assertEquals(0, count("DkgSession"))
        assertEquals(0, count("DkgParticipantMessage"))
    }

    /**
     * A message whose session is gone has no owner to take, and could not satisfy
     * the new key either. The old foreign key should have made it impossible; if one
     * exists it was written with foreign keys off, and keeping it would mean
     * inventing an owner for somebody else's secret.
     */
    @Test
    fun `a message with no ceremony is dropped rather than given an owner`() = runBlocking {
        createV21()
        insertV21Session()
        insertV21Message(participant = bob)

        // Off, so the orphan can be created at all -- which is the only way it could
        // have got there in the first place.
        connection.execSQL("PRAGMA foreign_keys = OFF")
        insertV21Message(sessionId = "no-such-ceremony", participant = alice)
        connection.execSQL("PRAGMA foreign_keys = ON")

        MIGRATION_21_22.migrate(connection)

        assertEquals(1, count("DkgParticipantMessage"))
        assertTrue(
            query("SELECT `sessionId` FROM `DkgParticipantMessage`") { it.getText(0) }
                .none { it == "no-such-ceremony" },
        )
    }

    /** Nothing of the migration's own scaffolding is left behind. */
    @Test
    fun `the backup tables are gone`() = runBlocking {
        createV21()
        insertV21Session()

        MIGRATION_21_22.migrate(connection)

        assertEquals(
            emptyList(),
            query(
                "SELECT `name` FROM `sqlite_master` WHERE `type` = 'table' AND `name` LIKE '%Backup'"
            ) { it.getText(0) },
        )
    }
}
