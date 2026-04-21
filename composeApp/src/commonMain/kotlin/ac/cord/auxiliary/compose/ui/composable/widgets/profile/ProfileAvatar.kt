package ac.cord.auxiliary.compose.ui.composable.widgets.profile

import ac.cord.auxiliary.compose.database.model.Profile
import ac.cord.auxiliary.compose.ui.composable.widgets.coil.forwardingPainter
import ac.cord.auxiliary.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Face5
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.abs

@Composable
fun ProfileAvatar(publicKey: String, profile: Profile?) {
    val tintColor = Color.hsl(
        hue = abs(
            publicKey.hashCode()
        ).mod(360).toFloat(),
        saturation = 0.55f,
        lightness = 0.45f
    )
    val imageModifier = Modifier.size(55.dp).clip(shape = CircleShape)

    if (profile?.picture == null) {
        Icon(
            modifier = imageModifier,
            imageVector = Icons.Default.AccountCircle,
            tint = tintColor,
            contentDescription = "Profile picture",
        )
    } else {
        // Load image...
        val placeHolderGraphic = forwardingPainter(
            painter = rememberVectorPainter(Icons.Default.Face5),
            colorFilter = ColorFilter.tint(tintColor)
        )

        val fallbackGraphic = forwardingPainter(
            painter = rememberVectorPainter(Icons.Default.Face5),
            colorFilter = ColorFilter.tint(tintColor)
        )
        val errorGraphic = forwardingPainter(
            painter = rememberVectorPainter(Icons.Default.FaceRetouchingOff),
            colorFilter = ColorFilter.tint(tintColor)
        )

        AsyncImage(
            model = profile.picture,
            modifier = imageModifier,
            contentDescription = "Profile picture for ${profile.displayName ?: profile.publicKey}",
            placeholder = placeHolderGraphic,
            fallback = fallbackGraphic,
            error = errorGraphic,
            contentScale = ContentScale.Crop
        )
    }
    // TODO: Show if we follow user...
    // TODO: Show cards...
}

@Preview
@Composable
fun ProfileAvatarPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            ProfileAvatar(
                publicKey = "hwgwefwase",
                profile = Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Frank Talk",
                    picture = "pictureUrl"
                )
            )
        }
    }
}