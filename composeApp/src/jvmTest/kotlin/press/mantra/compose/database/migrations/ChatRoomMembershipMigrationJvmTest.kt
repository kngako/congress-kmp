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
 * The v23 -> v24 column mapping, on a bare connection.
 *
 * What moves where, and that the group keeps only its own facts. Run with foreign keys
 * **off**, which is not a convenience -- it is the condition the migration actually runs
 * under, because Room disables them around a migration and `ChatRoom` is the parent of
 * twenty-two `ON DELETE CASCADE` children that no rebuild could survive otherwise. See
 * `MIGRATION_24_25` for why there is no alternative, and
 * [ChatRoomSplitThroughRoomJvmTest] for the test that proves the condition holds rather
 * than assuming it: that one runs the real Room path with children seeded, and is where
 * the cascade question is settled.
 *
 * So nothing here asserts anything about cascades. With them on, this migration cannot
 * work at all; asserting otherwise would be asserting against a state no device is in.
 */
class ChatRoomMembershipMigrationJvmTest {

    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:")

    @AfterTest
    fun close() = connection.close()

    private val roomId = "11".repeat(32)
    private val holder = "0a".repeat(32)
    private val mlsState = "a1".repeat(32)

    /**
     * `ChatRoom` as it stands before the split, its `Profile` parent, and four of its
     * twenty-two children.
     *
     * The room's shape is the same at v22 and v23 -- the ritual keying in between did not
     * touch it -- so this fixture is the pre-split room whichever of the two it came from.
     */
    private fun createV22() {
        // Off, as Room leaves them around a migration. See the note on the class.
        connection.execSQL("PRAGMA foreign_keys = OFF")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `NostrEvent` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `Profile` (`publicKey` TEXT NOT NULL, " +
                "PRIMARY KEY(`publicKey`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `GiftWrapPayload` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ChatRoom` (`id` TEXT NOT NULL, " +
                "`userPublicKey` TEXT NOT NULL, `subject` TEXT, `description` TEXT, " +
                "`mlsGroupState` TEXT, `initialGiftWrapPayloadId` TEXT, " +
                "`leftGroupAt` INTEGER, `joinedGroupAt` INTEGER, " +
                "`chronicleRequestedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, " +
                "`viewedAt` INTEGER, `deletedAt` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`userPublicKey`) REFERENCES `Profile`(`publicKey`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`initialGiftWrapPayloadId`) REFERENCES `GiftWrapPayload`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )

        // Four of the twenty-two, standing in for the lot: a message, a participant, a
        // ceremony and a piece of the group's signed work.
        CHILDREN.forEach { (table, extra) ->
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `$table` (`id` TEXT NOT NULL, " +
                    "`chatRoomId` TEXT NOT NULL, $extra PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`chatRoomId`) REFERENCES `ChatRoom`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
        }

        connection.execSQL("INSERT INTO `Profile` VALUES ('$holder')")
        connection.execSQL("INSERT INTO `GiftWrapPayload` VALUES ('${"ff".repeat(32)}')")
        connection.execSQL(
            "INSERT INTO `ChatRoom` VALUES ('$roomId', '$holder', 'the collective', " +
                "'a description', '$mlsState', '${"ff".repeat(32)}', NULL, 2000, 3000, " +
                "1000, 1000, 1000, NULL, NULL)"
        )
        CHILDREN.forEach { (table, _) ->
            connection.execSQL("INSERT INTO `$table` VALUES ('row-of-$table', '$roomId')")
        }
    }

    private fun count(table: String): Int =
        connection.prepare("SELECT COUNT(*) FROM `$table`").use { it.step(); it.getInt(0) }

    private fun <T> one(sql: String, read: (androidx.sqlite.SQLiteStatement) -> T): T? =
        connection.prepare(sql).use { if (it.step()) read(it) else null }

    private fun columns(table: String): List<String> =
        connection.prepare("PRAGMA table_info(`$table`)").use { s ->
            buildList { while (s.step()) add(s.getText(1)) }
        }

    /** The foreign keys [table] declares, as (parent table, parent column). */
    private fun foreignKeys(table: String): List<Pair<String, String>> =
        connection.prepare("PRAGMA foreign_key_list(`$table`)").use { s ->
            buildList { while (s.step()) add(s.getText(2) to s.getText(4)) }
        }

    @Test
    fun `the group keeps its own facts and loses the rest`() = runBlocking {
        createV22()

        MIGRATION_24_25.migrate(connection)

        assertEquals(
            listOf("id", "subject", "description", "createdAt", "updatedAt", "savedAt"),
            columns("ChatRoom"),
            "the room keeps what belongs to the group and nothing else",
        )
        assertEquals(
            listOf("the collective", "a description"),
            listOf(
                one("SELECT `subject` FROM `ChatRoom`") { it.getText(0) },
                one("SELECT `description` FROM `ChatRoom`") { it.getText(0) },
            ),
        )
        assertEquals(
            emptyList(),
            foreignKeys("ChatRoom"),
            "both of the room's foreign keys were facts about a membership and went with it",
        )
    }

    @Test
    fun `every room becomes exactly one membership, carrying the profile's half`() = runBlocking {
        createV22()

        MIGRATION_24_25.migrate(connection)

        assertEquals(1, count("ChatRoomMembership"))
        assertEquals(
            listOf(roomId, holder, mlsState, "ff".repeat(32), "2000", "3000"),
            one(
                "SELECT `chatRoomId`, `userPublicKey`, `mlsGroupState`, " +
                    "`initialGiftWrapPayloadId`, `joinedGroupAt`, `chronicleRequestedAt` " +
                    "FROM `ChatRoomMembership`"
            ) {
                listOf(it.getText(0), it.getText(1), it.getText(2), it.getText(3),
                    it.getText(4), it.getText(5))
            },
            "the member's MLS state, the wrap it came in and both timestamps come through",
        )
        assertEquals(
            setOf("ChatRoom" to "id", "Profile" to "publicKey", "GiftWrapPayload" to "id"),
            foreignKeys("ChatRoomMembership").toSet(),
            "and it takes the two foreign keys the room used to carry, plus its own parent",
        )
    }



    /** The new shape holds what the old one could not: two profiles in one group. */
    @Test
    fun `the upgraded schema takes a second profile's membership`() = runBlocking {
        createV22()

        MIGRATION_24_25.migrate(connection)

        val second = "0b".repeat(32)
        connection.execSQL("INSERT INTO `Profile` VALUES ('$second')")
        connection.execSQL(
            "INSERT INTO `ChatRoomMembership` " +
                "(`chatRoomId`, `userPublicKey`, `mlsGroupState`, `createdAt`, `updatedAt`, `savedAt`) " +
                "VALUES ('$roomId', '$second', '${"b2".repeat(32)}', 1000, 1000, 1000)"
        )

        assertEquals(2, count("ChatRoomMembership"), "one group, two memberships")
        assertEquals(
            1,
            count("ChatRoom"),
            "and still one room -- which is what the twenty-two children point at",
        )
        assertTrue(
            one("SELECT `mlsGroupState` FROM `ChatRoomMembership` WHERE `userPublicKey` = '$holder'") {
                it.getText(0)
            } == mlsState,
            "the first profile's MLS state is untouched by the second's arriving",
        )
    }

    /** Nothing of the migration's own scaffolding is left behind. */
    @Test
    fun `the renamed table is gone`() = runBlocking {
        createV22()

        MIGRATION_24_25.migrate(connection)

        assertEquals(
            emptyList(),
            connection.prepare(
                "SELECT `name` FROM `sqlite_master` WHERE `type` = 'table' AND `name` = 'ChatRoomOld'"
            ).use { s -> buildList<String> { while (s.step()) add(s.getText(0)) } },
        )
    }

    private companion object {
        /**
         * Four of `ChatRoom`'s twenty-two children, one per shape that matters: a message,
         * the roster, a ceremony and a piece of the group's signed work. They are all the
         * same single-column cascade onto the same parent, so they fail or survive
         * together.
         */
        val CHILDREN = listOf(
            "ChatMessage" to "",
            "Participant" to "",
            "DkgSession" to "",
            "GroupSignedEvent" to "",
        )
    }
}
