package press.mantra.compose.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Moves a room's parentage off two `GroupKeyState` columns onto
 * `GroupKeyStateParent`, so a room can be the child of more than one group.
 *
 * Not an `AutoMigration`. Room can create the table and it can drop the columns,
 * but it cannot copy between them, and this migration is entirely about the copy.
 *
 * ### Every existing lineage is a set of one
 *
 * A group could only be founded with a parent, never adopted by a second, so
 * every row that carries a parentage carries exactly one. Each becomes one row
 * in the new table and nothing is lost -- and the rule the new primary key
 * states, one certificate per child per parent, is already true of all of them.
 *
 * Rows with no parentage -- every group made before subgroups, and every
 * top-level group made after -- contribute nothing, which is what "nobody's
 * child" looks like in the new shape: no rows rather than a null.
 *
 * The `WHERE` takes both halves. A row with one and not the other should not
 * exist, because `GroupKeyStateManager.stateFrom` has always dropped a state
 * carrying a half-claim rather than filing it -- but this is the one moment the
 * data is reshaped rather than re-derived, and a half row copied forward would
 * become a parent whose certificate is the string "null". Better to leave such a
 * row behind entirely: the state keeps saying what the room signs with, which is
 * the part that matters, and loses a lineage it could never have proved.
 *
 * ### `ChatRoom.parentChatRoomId` goes with them, unread
 *
 * It was a cache of the verified value, written by `MarmotGroupCreation` and
 * read -- in production -- by nothing at all; the live answer went through
 * `GroupKeyState`. Keeping it would mean a column holding *one* of several
 * parents, which is worse than unused: it is a cache that is wrong. So it is
 * dropped rather than backfilled, and nothing is copied out of it.
 *
 * ### Why `DROP COLUMN` rather than a table rebuild
 *
 * The usual way to remove columns in SQLite -- create a new table, copy, drop
 * the old one, rename -- is unsafe here and quietly so. `GroupKeyStateParent`
 * references `GroupKeyState(chatRoomId)` `ON DELETE CASCADE`, and `DROP TABLE`
 * fires cascades: with foreign keys enforced it would delete every parent row
 * this migration had just written. Dropping `ChatRoom` would be far worse, since
 * most of the schema cascades off it.
 *
 * `ALTER TABLE ... DROP COLUMN` avoids the question. It needs SQLite 3.35 and
 * the columns must be free of indices and constraints -- all three are, and
 * `getRoomDatabase` pins `BundledSQLiteDriver` on every platform, so the version
 * is ours rather than the host's. Both tables keep their keys, their foreign keys
 * and their indices.
 */
val MIGRATION_20_21 = object : Migration(20, 21) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `GroupKeyStateParent` (" +
                "`chatRoomId` TEXT NOT NULL, " +
                "`parentChatRoomId` TEXT NOT NULL, " +
                "`birthCertificateJson` TEXT NOT NULL, " +
                "PRIMARY KEY(`chatRoomId`, `parentChatRoomId`), " +
                "FOREIGN KEY(`chatRoomId`) REFERENCES `GroupKeyState`(`chatRoomId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_GroupKeyStateParent_parentChatRoomId` " +
                "ON `GroupKeyStateParent` (`parentChatRoomId`)"
        )

        connection.execSQL(
            "INSERT OR IGNORE INTO `GroupKeyStateParent` " +
                "(`chatRoomId`, `parentChatRoomId`, `birthCertificateJson`) " +
                "SELECT `chatRoomId`, `parentChatRoomId`, `birthCertificateJson` " +
                "FROM `GroupKeyState` " +
                "WHERE `parentChatRoomId` IS NOT NULL AND `birthCertificateJson` IS NOT NULL"
        )

        listOf("parentChatRoomId", "birthCertificateJson").forEach { column ->
            connection.execSQL("ALTER TABLE `GroupKeyState` DROP COLUMN `$column`")
        }

        connection.execSQL("ALTER TABLE `ChatRoom` DROP COLUMN `parentChatRoomId`")
    }
}
