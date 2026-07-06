package at.torch.compose.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import at.torch.compose.database.model.MarmotCommitResult
import at.torch.compose.database.model.MarmotKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import fr.acinq.bitcoin.PublicKey

@Dao
interface MarmotCommitResultDao {
    @Upsert
    suspend fun upsert(marmotCommitResult: MarmotCommitResult)
}