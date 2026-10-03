package press.mantra.compose.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Replaces `ChatMessage.isUserMessage` with the sender's identity.
 *
 * A boolean was the answer for *one* viewer, stored on a row that belongs to the group.
 * It worked while a device held one profile. With two profiles in one group it stops
 * working: a transcript line is deduped by the group event that produced it -- see
 * `NostrDao.persistMarmotChatMessage` -- and `ChatMessage.reconcileMarmotLine` lets the
 * fresher row win, so whichever profile indexed the event last decided whose message it
 * looked like. A member's own messages would draw as somebody else's, on every line, and
 * flip back when the other profile caught up.
 *
 * The identity is a fact about the message. Whether it is yours is a question only a
 * reader can ask, so `ChatMessage.isFrom` asks it.
 *
 * ### The backfill is exact, for the reason the bug could not happen yet
 *
 * `senderIdentity` cannot be recovered from the row: for a Marmot line `senderPublicKey`
 * is the kind:445's signer, which is a fresh ephemeral key on every send. But it does not
 * need to be recovered, because before this every room belonged to exactly one profile --
 * that is the whole reason `isUserMessage` was a safe thing to store. So a line marked as
 * the user's was sent by that room's one member, and `ChatRoomMembership` says who that
 * is.
 *
 * Lines where it was false get null, which is what [ChatMessage.isFrom] answers false to
 * -- the same thing they rendered as before. A line in a room with no membership also gets
 * null: there is nobody it could have been from.
 *
 * ### Why no table rebuild
 *
 * `isUserMessage` is named by no index and no constraint, so `ALTER TABLE ... DROP COLUMN`
 * will take it -- unlike the two columns `MIGRATION_24_25` had to rebuild a table for.
 * `ChatMessage` has three cascading children of its own, and this leaves all three alone.
 */
val MIGRATION_25_26 = object : Migration(25, 26) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `ChatMessage` ADD COLUMN `senderIdentity` TEXT")

        // The room's one member, for the lines that member sent. A room with no
        // membership leaves them null, which reads as nobody's.
        connection.execSQL(
            "UPDATE `ChatMessage` SET `senderIdentity` = (" +
                "SELECT `userPublicKey` FROM `ChatRoomMembership` " +
                "WHERE `ChatRoomMembership`.`chatRoomId` = `ChatMessage`.`chatRoomId` " +
                "LIMIT 1" +
                ") WHERE `isUserMessage` != 0"
        )

        connection.execSQL("ALTER TABLE `ChatMessage` DROP COLUMN `isUserMessage`")
    }
}
