package press.mantra.compose.database.migrations

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The v24 -> v25 backfill: who sent a line, in place of whether it was yours.
 *
 * The identity cannot be recovered from the row -- for a Marmot line `senderPublicKey` is
 * the kind:445's signer, a fresh ephemeral key on every send. It does not need to be: the
 * boolean was only ever safe to store because a room belonged to exactly one profile, so a
 * line marked as the user's was sent by that room's one member, and `ChatRoomMembership`
 * says who. That is what makes this backfill exact rather than a guess, and it is the same
 * fact whose ending is the reason for the change.
 *
 * Run with foreign keys off, as Room leaves them around a migration.
 */
class ChatMessageSenderIdentityMigrationJvmTest {

    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:")

    @AfterTest
    fun close() = connection.close()

    private val roomId = "11".repeat(32)
    private val otherRoomId = "22".repeat(32)
    private val orphanRoomId = "33".repeat(32)
    private val holder = "0a".repeat(32)
    private val otherHolder = "0b".repeat(32)

    /** Enough of v24 to hold a transcript: the room, its membership, and the lines. */
    private fun createV24() {
        connection.execSQL("PRAGMA foreign_keys = OFF")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ChatRoomMembership` (`chatRoomId` TEXT NOT NULL, " +
                "`userPublicKey` TEXT NOT NULL, PRIMARY KEY(`chatRoomId`, `userPublicKey`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ChatMessage` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`senderPublicKey` TEXT NOT NULL, `isUserMessage` INTEGER NOT NULL, " +
                "`content` TEXT NOT NULL, `chatRoomId` TEXT NOT NULL)"
        )
        connection.execSQL("INSERT INTO `ChatRoomMembership` VALUES ('$roomId', '$holder')")
        connection.execSQL("INSERT INTO `ChatRoomMembership` VALUES ('$otherRoomId', '$otherHolder')")
    }

    private fun line(room: String, sender: String, mine: Boolean, content: String) =
        connection.execSQL(
            "INSERT INTO `ChatMessage` (`senderPublicKey`, `isUserMessage`, `content`, `chatRoomId`) " +
                "VALUES ('$sender', ${if (mine) 1 else 0}, '$content', '$room')"
        )

    /** Every line's `(content, senderIdentity)`, ordered by id. */
    private fun lines(): List<Pair<String, String?>> =
        connection.prepare("SELECT `content`, `senderIdentity` FROM `ChatMessage` ORDER BY `id`")
            .use { s ->
                buildList {
                    while (s.step()) add(s.getText(0) to if (s.isNull(1)) null else s.getText(1))
                }
            }

    private fun columns(): List<String> =
        connection.prepare("PRAGMA table_info(`ChatMessage`)").use { s ->
            buildList { while (s.step()) add(s.getText(1)) }
        }

    @Test
    fun `a line the user sent takes that room's member as its identity`() = runBlocking {
        createV24()
        // The ephemeral signer is deliberately not the member: this is exactly the case
        // where the identity cannot be read off the row.
        line(roomId, sender = "ee".repeat(32), mine = true, content = "mine")
        line(otherRoomId, sender = "ee".repeat(32), mine = true, content = "from the other room")

        MIGRATION_25_26.migrate(connection)

        assertEquals(
            listOf("mine" to holder, "from the other room" to otherHolder),
            lines(),
            "each line takes the member of its own room, not of any room",
        )
    }

    @Test
    fun `a line somebody else sent takes no identity`() = runBlocking {
        createV24()
        line(roomId, sender = otherHolder, mine = false, content = "theirs")

        MIGRATION_25_26.migrate(connection)

        assertEquals(
            listOf("theirs" to null),
            lines(),
            "null is what isFrom answers false to, which is what it rendered as before",
        )
    }

    /**
     * A line in a room with no membership gets null rather than failing the upgrade.
     *
     * There is nobody it could have been from. A room can be in that state after the
     * owning profile was forgotten, which cascades the membership away and leaves the
     * group's own rows.
     */
    @Test
    fun `a line in a room nobody is a member of takes no identity`() = runBlocking {
        createV24()
        line(orphanRoomId, sender = holder, mine = true, content = "orphaned")

        MIGRATION_25_26.migrate(connection)

        assertEquals(listOf("orphaned" to null), lines())
    }

    @Test
    fun `the boolean is gone and nothing else is`() = runBlocking {
        createV24()
        line(roomId, sender = holder, mine = true, content = "mine")

        MIGRATION_25_26.migrate(connection)

        assertEquals(
            listOf("id", "senderPublicKey", "content", "chatRoomId", "senderIdentity"),
            columns(),
            "isUserMessage dropped, senderIdentity added, the rest untouched",
        )
        assertFalse("isUserMessage" in columns())
    }
}
