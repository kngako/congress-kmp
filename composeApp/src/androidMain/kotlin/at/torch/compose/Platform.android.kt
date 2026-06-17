package at.torch.compose

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = _root_ide_package_.at.torch.compose.AndroidPlatform()