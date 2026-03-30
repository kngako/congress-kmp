package ac.aux.compose.ui.composable.widgets.feed

import ac.aux.compose.database.model.NostrEvent
import ac.aux.compose.ui.theme.AuxTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip01Core.core.Event

@Composable
fun NostrEvent.ListView() {
    when (kind) {
        // TODO: Support other kinds...
        else -> {
            Column(
                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile picture"
                    )
                    Text(
                        text = pubKey
                    )
                }
                Text(
                    text = content,
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        10.dp,
                        alignment = Alignment.Start
                    )
                ) {
                    item {
                        AssistChip(
                            label = {
                                Text(
                                    text = kind.toString() // TODO: Kind by name...
                                )
                            },
                            onClick = {

                            }
                        )

                    }
                    items(
                        items = tags,
                    ) { tag ->
                        SuggestionChip(
                            label = {
                                Text(
                                    text = tag.joinToString(":")
                                )
                            },
                            onClick = {

                            }
                        )
                    }
                }

                Text(
                    text = createdAt.toString()
                )
            }
        }
    }
}

@Preview
@Composable
private fun EventListViewPreview() {
    AuxTheme {
        Surface(
            modifier = Modifier.padding(20.dp)
        ) {
            NostrEvent(
                id = "hex",
                content = "Something something seomthing",
                pubKey = "hexpubkeypubkeypubkeypubkey",
                kind = 0,
                sig = "hexsig",
                tags = emptyArray()
            ).ListView()
        }
    }
}