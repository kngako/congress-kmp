package at.torch.compose.ui.composable.widgets.profile

import at.torch.compose.database.model.Profile
import at.torch.compose.ui.composable.widgets.coil.forwardingPainter
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun ProfileAvatar(
    size: Dp = 55.dp,
    shape: Shape = CircleShape,
    profile: at.torch.compose.database.model.Profile
) {
    ProfileAvatar(
        size = size,
        shape = shape,
        profile = profile,
        publicKey = profile.publicKey
    )
}

@Composable
fun ProfileAvatar(
    size: Dp = 55.dp,
    shape: Shape = CircleShape,
    publicKey: String,
    profile: at.torch.compose.database.model.Profile?
) {
    val modifier = Modifier.size(size).clip(shape = shape)

    val tintColor = ProfileColor.fromPublicKey(
        publicKey
    )

    if (profile?.picture == null) {
        Icon(
            modifier = modifier,
            imageVector = Icons.Default.AccountCircle,
            tint = tintColor,
            contentDescription = "Profile picture",
        )
    } else {
        // Load image...
        val placeHolderGraphic =
            _root_ide_package_.ac.cord.compose.ui.composable.widgets.coil.forwardingPainter(
                painter = rememberVectorPainter(Icons.Default.Face5),
                colorFilter = ColorFilter.tint(tintColor)
            )

        val fallbackGraphic =
            _root_ide_package_.ac.cord.compose.ui.composable.widgets.coil.forwardingPainter(
                painter = rememberVectorPainter(Icons.Default.Face5),
                colorFilter = ColorFilter.tint(tintColor)
            )
        val errorGraphic =
            _root_ide_package_.ac.cord.compose.ui.composable.widgets.coil.forwardingPainter(
                painter = rememberVectorPainter(Icons.Default.FaceRetouchingOff),
                colorFilter = ColorFilter.tint(tintColor)
            )

        AsyncImage(
            model = profile.picture,
            modifier = modifier,
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
    _root_ide_package_.ac.cord.compose.ui.theme.AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            ProfileAvatar(
                publicKey = "hwgwefwase",
                profile = _root_ide_package_.ac.cord.compose.database.model.Profile(
                    publicKey = "",
                    nostrEventId = "referenced",
                    displayName = "Frank Talk",
                    picture = "pictureUrl"
                )
            )
        }
    }
}