package press.mantra.compose.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Splits one profile's membership of a group off the group itself.
 *
 * `ChatRoom` held both: the group's id, name and description, and the asking profile's
 * MLS state, join time, chronicle request and archive flag. One row for two things,
 * keyed by the group id, so a second profile joining the same group wrote over the
 * first's ratchet state -- which breaks both members, in the shape of "the other device
 * never got it". See `ChatRoomMembership` for why the room was split rather than its key
 * widened to (`id`, `userPublicKey`).
 *
 * Every existing room becomes exactly one membership, which is right rather than
 * approximate: before several profiles, a room *was* one profile's, and its
 * `userPublicKey` says whose.
 *
 * ### Nineteen cascades, and the one thing that makes this possible
 *
 * `ChatRoom` is the parent of twenty-two tables, every one of them `ON DELETE CASCADE` --
 * the messages, the participants, the ceremonies, the signing sessions, the key states and
 * the whole translation domain. Two of the columns to remove are named by foreign keys
 * (`userPublicKey` onto `Profile`, `initialGiftWrapPayloadId` onto `GiftWrapPayload`), and
 * SQLite will not `DROP COLUMN` a column a constraint names, so the table must be rebuilt.
 *
 * With foreign keys enforced there is no way to rebuild it. Measured, not assumed:
 *
 * - `DROP TABLE` performs an implicit `DELETE FROM`, so all twenty-two cascades fire;
 * - `ALTER TABLE ... RENAME TO` rewrites the children's foreign-key clauses to follow the
 *   rename, pointing them at a table about to be dropped -- and `PRAGMA
 *   legacy_alter_table` does **not** stop it. It reads back as set and the clause is
 *   rewritten anyway;
 * - a migration cannot turn foreign keys off itself: `PRAGMA foreign_keys` is a no-op
 *   inside a transaction, and Room runs migrations in one.
 *
 * So this depends on Room disabling foreign keys around the migration, which is what makes
 * the ordinary rebuild-a-parent-table migration possible at all. `MIGRATION_9_10` warns
 * against taking that on trust, and it is not taken on trust:
 * `ChatRoomSplitThroughRoomJvmTest` asserts it against a real v22 file, through
 * `getRoomDatabase`, with rows seeded in four of the twenty-two. If that test fails this
 * migration is unsafe, and no reordering fixes it -- the children would have to be copied
 * out and back.
 *
 * ### The order still matters
 *
 * The replacement is built beside the original and renamed into its place *after* the
 * original is gone, rather than the original being renamed out of the way first. Nothing
 * ever references `ChatRoomNew`, so nothing is rewritten to follow that rename, and the
 * children go on naming `ChatRoom` throughout -- which resolves to the old table
 * before the swap and the new one after it. Renaming the original first would repoint every
 * one of them at `ChatRoomOld` and leave them dangling.
 *
 * `legacy_alter_table` is set for the rename after all, for a different reason than the
 * one above: the modern path re-parses the whole schema on rename and errors on the
 * children's momentarily unresolvable reference to `ChatRoom`.
 *
 * ### What the new column is filled with
 *
 * Every existing room becomes exactly one membership, which is right rather than
 * approximate: before several profiles, a room *was* one profile's, and its
 * `userPublicKey` says whose.
 */
val MIGRATION_23_24 = object : Migration(23, 24) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // 1. The membership, and one per room that exists.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ChatRoomMembership` (" +
                "`chatRoomId` TEXT NOT NULL, `userPublicKey` TEXT NOT NULL, " +
                "`mlsGroupState` TEXT, `initialGiftWrapPayloadId` TEXT, " +
                "`leftGroupAt` INTEGER, `joinedGroupAt` INTEGER, " +
                "`chronicleRequestedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, " +
                "`viewedAt` INTEGER, `deletedAt` INTEGER, " +
                "PRIMARY KEY(`chatRoomId`, `userPublicKey`), " +
                "FOREIGN KEY(`chatRoomId`) REFERENCES `ChatRoom`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`userPublicKey`) REFERENCES `Profile`(`publicKey`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`initialGiftWrapPayloadId`) REFERENCES `GiftWrapPayload`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ChatRoomMembership_userPublicKey` " +
                "ON `ChatRoomMembership` (`userPublicKey`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ChatRoomMembership_initialGiftWrapPayloadId` " +
                "ON `ChatRoomMembership` (`initialGiftWrapPayloadId`)"
        )
        connection.execSQL(
            "INSERT INTO `ChatRoomMembership` " +
                "(`chatRoomId`, `userPublicKey`, `mlsGroupState`, `initialGiftWrapPayloadId`, " +
                "`leftGroupAt`, `joinedGroupAt`, `chronicleRequestedAt`, " +
                "`createdAt`, `updatedAt`, `savedAt`, `viewedAt`, `deletedAt`) " +
                "SELECT `id`, `userPublicKey`, `mlsGroupState`, `initialGiftWrapPayloadId`, " +
                "`leftGroupAt`, `joinedGroupAt`, `chronicleRequestedAt`, " +
                "`createdAt`, `updatedAt`, `savedAt`, `viewedAt`, `deletedAt` " +
                "FROM `ChatRoom`"
        )

        // 2. The group's own row, built beside the original under a name nothing
        // references -- so the swap below rewrites none of those children.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `ChatRoomNew` (" +
                "`id` TEXT NOT NULL, `subject` TEXT, `description` TEXT, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "INSERT INTO `ChatRoomNew` (`id`, `subject`, `description`, `createdAt`, `updatedAt`, `savedAt`) " +
                "SELECT `id`, `subject`, `description`, `createdAt`, `updatedAt`, `savedAt` " +
                "FROM `ChatRoom`"
        )

        // 3. And into its place. The children named `ChatRoom` before this and still do.
        connection.execSQL("DROP TABLE `ChatRoom`")
        connection.execSQL("PRAGMA legacy_alter_table = ON")
        connection.execSQL("ALTER TABLE `ChatRoomNew` RENAME TO `ChatRoom`")
        connection.execSQL("PRAGMA legacy_alter_table = OFF")
    }
}
