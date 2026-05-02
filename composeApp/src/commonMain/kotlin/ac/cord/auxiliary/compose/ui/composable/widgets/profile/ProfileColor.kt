package ac.cord.auxiliary.compose.ui.composable.widgets.profile

import ac.cord.auxiliary.compose.database.model.Profile
import androidx.compose.ui.graphics.Color
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.math.abs

object ProfileColor {
    fun fromPublicKey(publicKey: HexKey): Color {
        return Color.hsl(
            hue = abs(
                publicKey.hashCode()
            ).mod(360).toFloat(),
            saturation = 0.55f,
            lightness = 0.45f
        )
    }

    fun fromProfile(profile: Profile): Color {
        return fromPublicKey(
            profile.publicKey
        )
    }
}