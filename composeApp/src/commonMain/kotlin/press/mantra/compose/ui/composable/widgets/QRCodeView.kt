package press.mantra.compose.ui.composable.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import press.mantra.compose.ui.theme.BluePill
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.QrLogoPadding
import io.github.alexzhirkevich.qrose.options.QrLogoShape
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.painterResource
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.compose_multiplatform

class QRCodeBackgroundPainter(
    private val painter: Painter,
    private val backgroundColor: Color = BluePill,
    private val padding: Dp = 0.dp,
): Painter() {
    override val intrinsicSize: Size = painter.intrinsicSize

    override fun DrawScope.onDraw() {
        val paddingPx = padding.toPx() // Convert Dp to Px

        // Calculate the size excluding the padding
        val paddedSize = Size(
            width = size.width - paddingPx * 2,
            height = size.height - paddingPx * 2
        )

        // Draw the white background
        drawRect(
            color = backgroundColor,
            size = size
        )

        // Draw the original painter with the padding
        with(painter) {
            // Translate to apply padding
            translate(left = paddingPx, top = paddingPx) {
                draw(size = paddedSize)
            }
        }
    }

}

@Composable
fun QRCodeView(
    data: String,
) {
    val qrCodeColor = if (isSystemInDarkTheme()) {
        Color.White
    } else {
        Color.Black
    }

    val logoPainter = QRCodeBackgroundPainter(
        painter = painterResource(
            Res.drawable.compose_multiplatform
        ),
    )

    val painter = rememberQrCodePainter(
        data = data,
    ) {
        logo {
            painter = logoPainter
            padding = QrLogoPadding.Natural(.1f)
            shape = QrLogoShape.Default
            size = 0.3f
        }
        colors {
            dark = QrBrush.solid(
                color = qrCodeColor,
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Image(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            painter = painter,
            contentDescription = data,
        )
    }
}