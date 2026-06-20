package at.torch.compose.ui.composable.widgets.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun LiveStreamCardContent(
    event: at.torch.compose.database.model.NostrEvent?,
    streamUrl: String?,
    image: String?,
    title: String?,
    summary: String?,
    status: String?,
    profile: at.torch.compose.database.model.Profile?,
    author: String,
    onProfileClick: ((String) -> Unit)?
) {
    if (event == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.width(14.dp).height(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Loading stream...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column {
            if (streamUrl != null) {
//               TODO:  InlineVideoPlayer(
//                    url = streamUrl,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
//                )
            } else if (image != null) {
                // TODO: Support media...
//                val meta = remember(event.id) { parseImetaTags(event.tags)[image] ?: MediaMeta(url = image) }
//                val ratio = remember(meta.dimension) { parseAspectRatio(meta.dimension) }
//                val blurPainter = remember(meta.blurhash, meta.dimension) {
//                    val dims = meta.dimension?.split('x')
//                    val w = dims?.getOrNull(0)?.toIntOrNull()?.coerceAtMost(100) ?: 32
//                    val h = dims?.getOrNull(1)?.toIntOrNull()?.coerceAtMost(100) ?: 32
//                    BlurHashDecoder.decode(meta.blurhash, w, h)?.asImageBitmap()?.let { BitmapPainter(it) }
//                }
//                LoadingAsyncImage(
//                    model = image,
//                    contentDescription = title,
//                    contentScale = ContentScale.Crop,
//                    blurPainter = blurPainter,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .let { if (ratio != null) it.aspectRatio(ratio) else it.heightIn(max = 180.dp) }
//                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
//                )
            }
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status == "live") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE53935),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (status == "ended") {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "ENDED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = title ?: "Live Stream",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!summary.isNullOrBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    _root_ide_package_.at.torch.compose.ui.composable.widgets.profile.ProfileAvatar(
                        profile = profile,
                        publicKey = event.pubKey,
                        size = 20.dp
                    )
                    Spacer(Modifier.width(6.dp))
                    val displayName = profile?.humanReadableNameOrPubkey()
                        ?: "${author.take(8)}...${author.takeLast(4)}"
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (onProfileClick != null) {
                            Modifier.clickable { onProfileClick(author) }
                        } else Modifier
                    )
                }
            }
        }
    }
}