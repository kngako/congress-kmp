package press.mantra.compose.database.migrations

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase

/**
 * The membership split as a real device performs it, which is the only setting that
 * answers the question this migration turns on.
 *
 * Starts from a v22 file and lets Room take it all the way to the current version, so it
 * runs 22 -> 23 (the ritual keying) and 23 -> 24 (this split) in one upgrade, which is what
 * a device coming off the projects release will actually do.
 *
 * `ChatRoom` is the parent of twenty-two `ON DELETE CASCADE` children -- the messages, the
 * participants, the ceremonies, the signing sessions, the key states and the whole
 * translation domain -- and splitting the membership off means rebuilding that table.
 * There is no way to rebuild it that survives foreign keys being enforced:
 *
 * - `DROP TABLE` performs an implicit `DELETE FROM`, so the cascades fire and every child
 *   row goes;
 * - `ALTER TABLE ... RENAME TO` rewrites the children's foreign-key clauses to follow the
 *   rename, pointing every one of them at a table about to be dropped. `PRAGMA
 *   legacy_alter_table` does **not** prevent that -- measured, not assumed: it reads back
 *   as set and the clause is rewritten anyway;
 * - `ALTER TABLE ... DROP COLUMN` cannot remove `userPublicKey` or
 *   `initialGiftWrapPayloadId`, because a foreign key names them;
 * - and a migration cannot turn foreign keys off itself, because `PRAGMA foreign_keys` is
 *   a no-op inside a transaction and Room runs migrations in one.
 *
 * Backing up all twenty-two children and their own dependents is the only
 * alternative, and it is worse than what it protects against.
 *
 * So this migration depends on Room disabling foreign keys around the migration it runs,
 * which is what makes the ordinary rebuild-a-parent-table migration possible at all. That
 * dependency is exactly the kind `MIGRATION_9_10` warns against taking on trust -- so it
 * is not taken on trust. It is asserted here, against a real file, through
 * `getRoomDatabase`, with children seeded in four of the twenty-two tables.
 *
 * If this test ever fails, the migration is unsafe and no amount of reordering its
 * statements will fix it; the children have to be copied out and back.
 */
class ChatRoomSplitThroughRoomJvmTest {

    private val databaseFile: File =
        File.createTempFile("chat-room-split", ".db").also { it.delete() }

    private var database: MantraDatabase? = null

    @AfterTest
    fun cleanUp() {
        database?.close()
        listOf("", "-wal", "-shm").forEach { File(databaseFile.path + it).delete() }
    }

    private val roomId = "11".repeat(32)
    private val holder = "0a".repeat(32)
    private val mlsState = "a1".repeat(32)
    private val wrapId = "ff".repeat(32)

    private fun schemaFile(version: Int): File =
        listOf(
            File("schemas/press.mantra.compose.database.MantraDatabase/$version.json"),
            File("composeApp/schemas/press.mantra.compose.database.MantraDatabase/$version.json"),
        ).firstOrNull { it.isFile } ?: error("no schema export for version $version")

    /** Every `CREATE` in an export, with Room's placeholder resolved, plus its identity. */
    private fun export(version: Int): Pair<String, List<String>> {
        val db = Json.parseToJsonElement(schemaFile(version).readText())
            .jsonObject.getValue("database").jsonObject
        val statements = buildList {
            db.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                fun resolve(e: JsonElement) =
                    e.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
                add(resolve(entity.jsonObject.getValue("createSql")))
                entity.jsonObject["indices"]?.jsonArray?.forEach {
                    add(resolve(it.jsonObject.getValue("createSql")))
                }
            }
        }
        return db.getValue("identityHash").jsonPrimitive.content to statements
    }

    /**
     * A v22 file holding one room, its membership-to-be, and work in four of the tables
     * that cascade off it.
     *
     * From the committed export rather than from DDL restated here: fifty-eight tables is too
     * many to copy, and the export is what a v22 device actually holds.
     */
    private fun createV22Database() {
        val (identity, statements) = export(22)
        val connection = BundledSQLiteDriver().open(databaseFile.absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            statements.forEach { connection.execSQL(it) }
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, '$identity')"
            )
            connection.execSQL("PRAGMA user_version = 22")

            connection.execSQL(
                "INSERT INTO `NostrEvent` (`id`, `pubKey`, `kind`, `tags`, `content`, `sig`, " +
                    "`createdAt`, `updatedAt`, `savedAt`) VALUES " +
                    "('${"ee".repeat(32)}', '$holder', 0, '[]', '{}', '${"0".repeat(128)}', 1000, 1000, 1000)"
            )
            connection.execSQL(
                "INSERT INTO `Profile` (`publicKey`, `nostrEventId`, `createdAt`, `updatedAt`, `savedAt`) " +
                    "VALUES ('$holder', '${"ee".repeat(32)}', 1000, 1000, 1000)"
            )
            connection.execSQL(
                "INSERT INTO `ChatRoom` (`id`, `userPublicKey`, `subject`, `description`, " +
                    "`mlsGroupState`, `joinedGroupAt`, `createdAt`, `updatedAt`, `savedAt`) VALUES " +
                    "('$roomId', '$holder', 'the collective', 'a description', " +
                    "'$mlsState', 2000, 1000, 1000, 1000)"
            )

            // Four of the twenty-two, one per shape: the roster, a transcript line, a
            // ceremony and a piece of the group's signed work. They cascade identically,
            // so they are lost or kept together.
            connection.execSQL(
                "INSERT INTO `Participant` (`participantPublicKey`, `chatRoomId`, `createdAt`, " +
                    "`updatedAt`, `savedAt`) VALUES ('$holder', '$roomId', 1000, 1000, 1000)"
            )
            connection.execSQL(
                "INSERT INTO `ChatMessage` (`chatRoomId`, `senderPublicKey`, `content`, " +
                    "`isUserMessage`, `messageType`, `createdAt`, `updatedAt`, `savedAt`) VALUES " +
                    "('$roomId', '$holder', 'hello', 1, 14, 1000, 1000, 1000)"
            )
            connection.execSQL(
                "INSERT INTO `DkgSession` (`id`, `chatRoomId`, `coordinatorPublicKey`, " +
                    "`userPublicKey`, `threshold`, `participantCount`, `stage`, `hostPublicKey`, " +
                    "`round1Random`, `round2AuxRandom`, `secretShare`, `createdAt`, `updatedAt`, " +
                    "`savedAt`) VALUES ('ceremony-1', '$roomId', '$holder', '$holder', 2, 3, " +
                    "'COMPLETE', '${"01".repeat(33)}', '${"aa".repeat(32)}', '${"bb".repeat(32)}', " +
                    "'${"cc".repeat(32)}', 1000, 1000, 1000)"
            )
            connection.execSQL(
                "INSERT INTO `GroupSignedEvent` (`id`, `chatRoomId`, `publicKey`, `kind`, " +
                    "`tags`, `content`, `signature`, `createdAt`, `updatedAt`, `savedAt`) VALUES " +
                    "('${"dd".repeat(32)}', '$roomId', '${"02".repeat(32)}', 30303, '[]', '{}', " +
                    "'${"11".repeat(64)}', 1000, 1000, 1000)"
            )
        } finally {
            connection.close()
        }
    }

    private fun openThroughRoom(): MantraDatabase =
        getRoomDatabase(Room.databaseBuilder<MantraDatabase>(name = databaseFile.absolutePath))
            .also { database = it }

    private fun count(table: String): Int {
        val connection = BundledSQLiteDriver().open(databaseFile.absolutePath)
        return try {
            connection.prepare("SELECT COUNT(*) FROM `$table`").use { it.step(); it.getInt(0) }
        } finally {
            connection.close()
        }
    }

    /**
     * The whole of it. Room opens the file, runs 22 -> 23, and validates what the migration
     * left against the entities -- and the group's history is still there.
     */
    @Test
    fun `Room splits the membership off without taking the room's children`() = runBlocking {
        createV22Database()

        val db = openThroughRoom()

        // Reaching a readable room at all means Room migrated and validated the schema.
        val room = assertNotNull(
            db.chatRoomDao().findChatRoomById(roomId),
            "the group has to survive the upgrade and be readable",
        )
        assertEquals("the collective", room.chatRoom.subject)

        val membership = assertNotNull(
            room.membershipOf(holder),
            "the room's holder has to come out of it as a membership",
        )
        assertEquals(mlsState, membership.mlsGroupState, "the member's MLS state came through")
        assertEquals(2000, membership.joinedGroupAt?.epochSeconds, "and when they joined")

        db.close()
        database = null

        listOf("Participant", "ChatMessage", "DkgSession", "GroupSignedEvent").forEach { table ->
            assertEquals(
                1,
                count(table),
                "$table was taken by a cascade during the rebuild -- the migration is unsafe",
            )
        }
        assertEquals(1, count("ChatRoom"))
        assertEquals(1, count("ChatRoomMembership"))
    }

    /** And the cascade still works afterwards, which a rebuild can quietly lose instead. */
    @Test
    fun `deleting the group afterwards still takes its children`() = runBlocking {
        createV22Database()

        val db = openThroughRoom()
        val room = assertNotNull(db.chatRoomDao().findChatRoomById(roomId))
        db.chatRoomDao().delete(room.chatRoom)
        db.close()
        database = null

        listOf("Participant", "ChatMessage", "DkgSession", "GroupSignedEvent", "ChatRoomMembership")
            .forEach { assertEquals(0, count(it), "$it outlived the group it hangs off") }
    }
}
