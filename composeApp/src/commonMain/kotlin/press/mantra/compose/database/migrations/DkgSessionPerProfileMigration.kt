package press.mantra.compose.database.migrations

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Keys a ritual's row by the profile that ran it, not by the ceremony alone.
 *
 * `DkgSession.id` is minted by the coordinator and carried on the wire, so it is
 * the same string on every device and for every profile taking part. One row per
 * ceremony therefore meant one row for two participants whenever two profiles on
 * a device were both in a group: `ChillDkgRitualManager.acceptProposal` returned
 * the row the first of them wrote, and the second ran the ritual behind the
 * first's host key, randomness and share. The key becomes (`id`, `userPublicKey`),
 * and `DkgParticipantMessage` gains the receiving profile for the same reason --
 * see that entity for why its rows are a profile's rather than the ceremony's.
 *
 * Not an `AutoMigration`: Room cannot change a primary key, and the column this
 * adds is `NOT NULL` with a value that has to be read from another table.
 *
 * ### Copy before dropping anything
 *
 * `DROP TABLE` fires foreign keys. `DkgParticipantMessage` references
 * `DkgSession(id)` `ON DELETE CASCADE`, so dropping `DkgSession` while messages
 * still point at it deletes every one of them -- the same trap `MIGRATION_9_10`
 * documents and avoids. Whether it fires at all depends on whether Room left
 * foreign keys enforced around the migration, which is not a thing to depend on
 * in either direction.
 *
 * So the cascade is made harmless rather than avoided:
 *
 * 1. copy both tables into backups that carry **no foreign key at all**, so
 *    nothing can cascade into them and no insert can fail a parent check;
 * 2. drop `DkgParticipantMessage`, then `DkgSession`;
 * 3. recreate both in their new shape and copy back from the backups;
 * 4. drop the backups.
 *
 * Step 1 is what makes this safe, and it is worth being exact about which part
 * carries the weight: by the time anything is dropped, every row exists in a table
 * that no cascade can reach, so a cascade firing in step 2 destroys only rows that
 * are about to be dropped regardless. `DkgSessionPerProfileMigrationJvmTest` runs
 * with foreign keys **on** and still passes with the two drops in either order,
 * which is the evidence for that.
 *
 * The child is dropped first anyway. It costs nothing, it keeps the intent legible
 * to the next person to touch this, and it means the migration does not rely on the
 * backups for correctness as well as for recovery.
 *
 * ### What the new column is filled with
 *
 * Each message takes the `userPublicKey` of the session it referenced, which is
 * unambiguous precisely because of the bug being fixed: before this there was one
 * session row per ceremony, so there is exactly one answer per message.
 *
 * A message whose session is missing has no answer and is dropped. The old
 * foreign key should have made that impossible; if one exists it was written
 * while foreign keys were off, and it could not satisfy the new key either --
 * keeping it would mean inventing an owner for somebody else's secret.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // 1. Backups, deliberately without constraints of any kind.
        connection.execSQL(
            "CREATE TABLE `DkgSessionBackup` (" +
                "`id` TEXT NOT NULL, `chatRoomId` TEXT NOT NULL, " +
                "`coordinatorPublicKey` TEXT NOT NULL, `userPublicKey` TEXT NOT NULL, " +
                "`threshold` INTEGER NOT NULL, `participantCount` INTEGER NOT NULL, " +
                "`participantPublicKeys` TEXT, `subject` TEXT, `stage` TEXT NOT NULL, " +
                "`hostPublicKey` TEXT NOT NULL, `round1Random` TEXT NOT NULL, " +
                "`round2AuxRandom` TEXT NOT NULL, `coordinatorRound1` TEXT, " +
                "`certificate` TEXT, `thresholdPublicKey` TEXT, `secretShare` TEXT, " +
                "`publicShares` TEXT, `recoveryData` TEXT, `failureReason` TEXT, " +
                "`hostKeyApprovedAt` INTEGER, `round1ApprovedAt` INTEGER, " +
                "`round2ApprovedAt` INTEGER, `approvalRequestedThrough` TEXT, " +
                "`parentChatRoomId` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL)"
        )
        connection.execSQL("INSERT INTO `DkgSessionBackup` SELECT * FROM `DkgSession`")

        connection.execSQL(
            "CREATE TABLE `DkgParticipantMessageBackup` (" +
                "`sessionId` TEXT NOT NULL, `userPublicKey` TEXT, " +
                "`participantPublicKey` TEXT NOT NULL, `kind` INTEGER NOT NULL, " +
                "`payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "INSERT INTO `DkgParticipantMessageBackup` " +
                "(`sessionId`, `userPublicKey`, `participantPublicKey`, `kind`, " +
                "`payload`, `createdAt`) " +
                "SELECT `m`.`sessionId`, " +
                "(SELECT `s`.`userPublicKey` FROM `DkgSession` AS `s` " +
                "WHERE `s`.`id` = `m`.`sessionId`), " +
                "`m`.`participantPublicKey`, `m`.`kind`, `m`.`payload`, `m`.`createdAt` " +
                "FROM `DkgParticipantMessage` AS `m`"
        )

        // 2. The child first, so the parent's drop has nothing to cascade to -- though
        // the copies above are what actually make this safe.
        connection.execSQL("DROP TABLE `DkgParticipantMessage`")
        connection.execSQL("DROP TABLE `DkgSession`")

        // 3. The new shapes. Both DDL strings are the ones Room generates for the
        // entities, so the schema Room validates against on open is this one.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `DkgSession` (" +
                "`id` TEXT NOT NULL, `chatRoomId` TEXT NOT NULL, " +
                "`coordinatorPublicKey` TEXT NOT NULL, `userPublicKey` TEXT NOT NULL, " +
                "`threshold` INTEGER NOT NULL, `participantCount` INTEGER NOT NULL, " +
                "`participantPublicKeys` TEXT, `subject` TEXT, `stage` TEXT NOT NULL, " +
                "`hostPublicKey` TEXT NOT NULL, `round1Random` TEXT NOT NULL, " +
                "`round2AuxRandom` TEXT NOT NULL, `coordinatorRound1` TEXT, " +
                "`certificate` TEXT, `thresholdPublicKey` TEXT, `secretShare` TEXT, " +
                "`publicShares` TEXT, `recoveryData` TEXT, `failureReason` TEXT, " +
                "`hostKeyApprovedAt` INTEGER, `round1ApprovedAt` INTEGER, " +
                "`round2ApprovedAt` INTEGER, `approvalRequestedThrough` TEXT, " +
                "`parentChatRoomId` TEXT, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`, `userPublicKey`), " +
                "FOREIGN KEY(`chatRoomId`) REFERENCES `ChatRoom`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DkgSession_chatRoomId` " +
                "ON `DkgSession` (`chatRoomId`)"
        )
        connection.execSQL("INSERT INTO `DkgSession` SELECT * FROM `DkgSessionBackup`")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `DkgParticipantMessage` (" +
                "`sessionId` TEXT NOT NULL, `userPublicKey` TEXT NOT NULL, " +
                "`participantPublicKey` TEXT NOT NULL, `kind` INTEGER NOT NULL, " +
                "`payload` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`sessionId`, `userPublicKey`, `participantPublicKey`, `kind`), " +
                "FOREIGN KEY(`sessionId`, `userPublicKey`) REFERENCES `DkgSession`(`id`, `userPublicKey`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DkgParticipantMessage_sessionId_userPublicKey` " +
                "ON `DkgParticipantMessage` (`sessionId`, `userPublicKey`)"
        )
        connection.execSQL(
            "INSERT INTO `DkgParticipantMessage` " +
                "SELECT `sessionId`, `userPublicKey`, `participantPublicKey`, `kind`, " +
                "`payload`, `createdAt` FROM `DkgParticipantMessageBackup` " +
                "WHERE `userPublicKey` IS NOT NULL"
        )

        // 4.
        connection.execSQL("DROP TABLE `DkgParticipantMessageBackup`")
        connection.execSQL("DROP TABLE `DkgSessionBackup`")
    }
}
