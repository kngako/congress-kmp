package press.mantra.compose.database.migrations

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
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
import kotlin.test.assertTrue
import press.mantra.compose.database.MantraDatabase
import press.mantra.compose.database.builder.getRoomDatabase

/**
 * The half of a migration a bare-connection test cannot reach: Room's own wiring.
 *
 * [DkgSessionPerProfileMigrationJvmTest] drives `MIGRATION_21_22`'s SQL directly,
 * which covers the copy and the shape it leaves behind. What it cannot cover is
 * whether that shape is the one Room expects. On opening a file at an older version
 * Room runs the migrations and then compares every table against its entity, failing
 * with `Migration didn't properly handle: …` when they differ. A migration whose DDL
 * has drifted from its `@Entity` fails there and nowhere else.
 *
 * Nothing in this repository exercised that path before. The three existing migration
 * tests all stop at the SQL, and `MantraDatabaseJvmTest` only ever opens a fresh
 * database at the current version, where no migration runs at all. So a mismatch
 * between `DkgSessionPerProfileMigration`'s `CREATE TABLE` strings and the two
 * entities would have been caught by nothing until an upgrade on a real device.
 *
 * ### What it does and does not catch
 *
 * Measured rather than assumed, by drifting the migration and watching:
 *
 * - **a column dropped from the rebuilt table fails all three tests**, which is the
 *   drift that actually happens — somebody adds a field to the entity and does not
 *   carry it into the `CREATE TABLE` here;
 * - **an index renamed does not fail anything.** Room's comparison is lenient about
 *   index names, so the rebuilt index name is on nobody's authority but the
 *   migration's. `DkgSessionPerProfileMigrationJvmTest` pins it by string against the
 *   schema export instead, which is the only thing that holds it.
 *
 * Worth knowing before trusting this to cover the next migration: it covers the
 * columns, the types and the key, and not the index names.
 *
 * ### How the old file is built
 *
 * From `schemas/21.json`, the schema export committed beside its predecessors, rather
 * than from DDL copied into this file. Fifty-five tables is too many to restate, and
 * restating them is the thing that would rot: the export *is* what a v21 device has,
 * and reading it means this test cannot drift from it. `workingDir` is the module
 * directory, which is what makes the relative path work.
 *
 * `room_master_table` and `user_version` are set by hand because they are what Room
 * reads to decide there is anything to do.
 */
class DkgSessionMigrationThroughRoomJvmTest {

    private val databaseFile: File =
        File.createTempFile("dkg-per-profile-migration", ".db").also { it.delete() }

    private var database: MantraDatabase? = null

    @AfterTest
    fun cleanUp() {
        database?.close()
        // SQLite leaves these beside the file when it is closed less than cleanly.
        listOf("", "-wal", "-shm").forEach { File(databaseFile.path + it).delete() }
    }

    private val alice = "0a".repeat(32)
    private val bob = "0b".repeat(32)
    private val roomId = "11".repeat(32)
    private val secretShare = "cc".repeat(32)
    private val round1Random = "aa".repeat(32)
    private val hostKeyPayload = "01".repeat(33)

    /** The exported schema of a version, as a v21 device actually holds it. */
    private fun schema(version: Int): SchemaExport {
        val candidates = listOf(
            File("schemas/press.mantra.compose.database.MantraDatabase/$version.json"),
            File("composeApp/schemas/press.mantra.compose.database.MantraDatabase/$version.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error(
                "No schema export for version $version. Looked in " +
                    candidates.joinToString { it.absolutePath }
            )
        return SchemaExport(file.readText())
    }

    /**
     * The three things wanted out of an export: the identity hash, and every table's
     * and index's `CREATE` statement with Room's placeholder resolved.
     *
     * Walked as a tree rather than matched with a regex, because the placeholder is
     * resolved per entity and pairing `createSql` to `tableName` by position in the
     * text is the kind of thing that works until a schema export changes shape.
     */
    private class SchemaExport(json: String) {
        private val database = Json.parseToJsonElement(json).jsonObject
            .getValue("database").jsonObject

        val identityHash: String =
            database.getValue("identityHash").jsonPrimitive.content

        val createStatements: List<String> = buildList {
            database.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                fun resolve(element: JsonElement) =
                    element.jsonPrimitive.content.replace("${'$'}{TABLE_NAME}", table)

                add(resolve(entity.jsonObject.getValue("createSql")))
                entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                    add(resolve(index.jsonObject.getValue("createSql")))
                }
            }
            database["views"]?.jsonArray?.forEach { view ->
                add(view.jsonObject.getValue("createSql").jsonPrimitive.content)
            }
        }
    }

    /** Stands a v21 file up on disk, holding one finished ceremony and one message. */
    private fun createV21Database() {
        val export = schema(21)
        val connection = BundledSQLiteDriver().open(databaseFile.absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            export.createStatements.forEach { connection.execSQL(it) }

            // What Room reads to know whose schema this is and how old it is.
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, '${export.identityHash}')"
            )
            connection.execSQL("PRAGMA user_version = 21")

            seedV21Rows(connection)
        } finally {
            connection.close()
        }
    }

    /** A ceremony worth not losing, with the rows its foreign keys need. */
    private fun seedV21Rows(connection: SQLiteConnection) {
        connection.execSQL(
            "INSERT INTO `NostrEvent` (`id`, `pubKey`, `kind`, `tags`, `content`, `sig`, " +
                "`createdAt`, `updatedAt`, `savedAt`) VALUES " +
                "('${"f".repeat(64)}', '$alice', 0, '[]', '{}', '${"0".repeat(128)}', 1000, 1000, 1000)"
        )
        connection.execSQL(
            "INSERT INTO `Profile` (`publicKey`, `nostrEventId`, `createdAt`, `updatedAt`, `savedAt`) " +
                "VALUES ('$alice', '${"f".repeat(64)}', 1000, 1000, 1000)"
        )
        connection.execSQL(
            "INSERT INTO `ChatRoom` (`id`, `userPublicKey`, `createdAt`, `updatedAt`, `savedAt`) " +
                "VALUES ('$roomId', '$alice', 1000, 1000, 1000)"
        )
        connection.execSQL(
            "INSERT INTO `DkgSession` (`id`, `chatRoomId`, `coordinatorPublicKey`, " +
                "`userPublicKey`, `threshold`, `participantCount`, `stage`, `hostPublicKey`, " +
                "`round1Random`, `round2AuxRandom`, `thresholdPublicKey`, `secretShare`, " +
                "`createdAt`, `updatedAt`, `savedAt`) VALUES " +
                "('ceremony-1', '$roomId', '$alice', '$alice', 2, 3, 'COMPLETE', " +
                "'$hostKeyPayload', '$round1Random', '${"bb".repeat(32)}', " +
                "'02${"ab".repeat(32)}', '$secretShare', 1000, 1000, 1000)"
        )
        connection.execSQL(
            "INSERT INTO `DkgParticipantMessage` " +
                "(`sessionId`, `participantPublicKey`, `kind`, `payload`, `createdAt`) " +
                "VALUES ('ceremony-1', '$bob', 30450, '$hostKeyPayload', 1000)"
        )
    }

    /** Opens the file the way the app does, which is what runs the migration. */
    private fun openThroughRoom(): MantraDatabase =
        getRoomDatabase(Room.databaseBuilder<MantraDatabase>(name = databaseFile.absolutePath))
            .also { database = it }

    private fun <T> readOnce(sql: String, read: (androidx.sqlite.SQLiteStatement) -> T): T? {
        val connection = BundledSQLiteDriver().open(databaseFile.absolutePath)
        return try {
            connection.prepare(sql).use { if (it.step()) read(it) else null }
        } finally {
            connection.close()
        }
    }

    /**
     * The assertion that costs nothing to write and would have caught a drifted
     * `CREATE TABLE`: Room opens the file, migrates it, and validates what the
     * migration left against the entities. Reaching a readable database at all is the
     * result.
     */
    @Test
    fun `Room migrates a v21 file and validates what the migration left`() = runBlocking {
        createV21Database()

        val db = openThroughRoom()

        // Room is lazy: the file is not touched until something is asked of it, so the
        // migration and its schema validation both happen inside this call.
        val session = assertNotNull(
            db.dkgSessionDao().getSessionById("ceremony-1", alice),
            "the ceremony has to survive the upgrade and be readable under the new key",
        )

        assertEquals(alice, session.userPublicKey)
        assertEquals(secretShare, session.secretShare, "the share came through verbatim")
        assertEquals(round1Random, session.round1Random)

        // And the message, now owned by the profile whose session it hung off.
        val message = assertNotNull(
            db.dkgSessionDao().getMessage("ceremony-1", alice, 30450, bob),
            "the cascade must not have taken it during the rebuild",
        )
        assertEquals(alice, message.userPublicKey)
        assertEquals(hostKeyPayload, message.payload)
    }

    /** Room records the upgrade, rather than quietly recreating the file. */
    @Test
    fun `the file is left at version 22 with the new identity`() = runBlocking {
        createV21Database()

        openThroughRoom().dkgSessionDao().getSessionById("ceremony-1", alice)
        database?.close()
        database = null

        assertEquals(22, readOnce("PRAGMA user_version") { it.getInt(0) })
        assertEquals(
            schema(22).identityHash,
            readOnce("SELECT identity_hash FROM room_master_table WHERE id = 42") { it.getText(0) },
            "Room stamps the schema it validated, so this is its own verdict on the migration",
        )
    }

    /**
     * The upgraded file takes a second profile's row for the same ceremony, which a
     * v21 file could not hold at all — the point of the migration, asserted through
     * the DAO rather than through the rebuilt DDL.
     */
    @Test
    fun `the upgraded file holds a second profiles row for one ceremony`() = runBlocking {
        createV21Database()

        val db = openThroughRoom()
        val alices = assertNotNull(db.dkgSessionDao().getSessionById("ceremony-1", alice))

        db.dkgSessionDao().upsert(
            alices.copy(
                userPublicKey = bob,
                hostPublicKey = "02".repeat(33),
                round1Random = "dd".repeat(32),
                secretShare = "ee".repeat(32),
            )
        )

        assertEquals(
            secretShare,
            db.dkgSessionDao().getSessionById("ceremony-1", alice)?.secretShare,
            "the first profile's share is untouched by the second's row appearing",
        )
        assertEquals(
            "ee".repeat(32),
            db.dkgSessionDao().getSessionById("ceremony-1", bob)?.secretShare,
        )
        assertTrue(
            db.dkgSessionDao().getKeyHoldingSessions(alice).size == 1 &&
                db.dkgSessionDao().getKeyHoldingSessions(bob).size == 1,
            "one ceremony, two rows, and each profile finds exactly its own",
        )
    }
}
