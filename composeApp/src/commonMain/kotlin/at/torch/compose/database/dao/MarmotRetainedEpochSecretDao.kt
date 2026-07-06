package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotCommitResult
import at.torch.compose.database.model.MarmotKeyPackage
import at.torch.compose.database.model.MarmotRetainedEpochSecret
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.bitcoin.PublicKey

@Dao
interface MarmotRetainedEpochSecretDao {
    @Upsert
    suspend fun upsert(marmotRetainedEpochSecret: MarmotRetainedEpochSecret)
}