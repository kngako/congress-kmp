package press.mantra.android

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import fr.acinq.phoenix.PhoenixGlobal
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.utils.PlatformContext
import fr.acinq.phoenix.utils.preferences.GlobalPrefs

/** This datastore persists preferences across node ids. */
val Context.globalPrefs: DataStore<Preferences> by preferencesDataStore(name = "globalprefs")

class MantraApplication: Application()  {
    lateinit var globalPrefs: GlobalPrefs
    lateinit var phoenixGlobal: PhoenixGlobal

    override fun onCreate() {
        super.onCreate()

        phoenixGlobal = PhoenixGlobal(PlatformContext(applicationContext))
        globalPrefs = GlobalPrefs(applicationContext.globalPrefs)
        BusinessManager.initialize(applicationContext)

    }
}