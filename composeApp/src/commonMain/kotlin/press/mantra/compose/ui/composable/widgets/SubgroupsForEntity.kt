package press.mantra.compose.ui.composable.widgets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import mantra.composeapp.generated.resources.Res
import mantra.composeapp.generated.resources.a_key_ceremony_for_a_room_is_under_way
import mantra.composeapp.generated.resources.a_subgroup
import mantra.composeapp.generated.resources.certified_not_yet_created
import mantra.composeapp.generated.resources.created_you_are_not_a_member
import mantra.composeapp.generated.resources.give_this_a_room_of_its_own
import mantra.composeapp.generated.resources.give_this_another_room
import mantra.composeapp.generated.resources.rooms_of_its_own
import mantra.composeapp.generated.resources.run_by_members
import org.jetbrains.compose.resources.stringResource
import press.mantra.compose.database.model.DkgSession
import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.managers.SubgroupOrigin
import press.mantra.compose.ui.composable.navigation.routes.ChatRoomDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.NostrEventDetailRoute
import press.mantra.compose.ui.composable.navigation.routes.Route
import press.mantra.compose.ui.composable.navigation.routes.SelectSubgroupAdminsRoute
import press.mantra.compose.ui.theme.spacing

/**
 * The rooms a group has made for one thing, and the way to ask for another.
 *
 * Written once for four screens, which is the point: a dialect, an artifact, a
 * translation and a project all answer "has this been given a room of its own"
 * identically, and four copies of the answer would drift into four. It is a
 * `LazyListScope` extension rather than a composable so each screen's own
 * `LazyColumn` keeps one scroll and one set of keys.
 *
 * ### What is shown and what is not refused
 *
 * A group may legitimately want two rooms for one artifact -- two teams, two
 * bodies of work -- so a second is never refused. What is prevented is the
 * *accidental* second: two admins open this screen at the same time, both see
 * nothing, and the group gets two ceremonies and two rooms for one project.
 * [ceremonies] is what stops that, by making the act in progress visible on the
 * screen that invites the act. The button changes its words rather than
 * disappearing, so the deliberate second room is still one tap away.
 *
 * A subgroup with no room on this device is still listed. That is the normal
 * position of a member who is not in the child, and of everybody between the
 * parent certifying it and somebody creating it, so it opens at the certificate
 * instead -- which is the whole of what is known and is checkable.
 */
fun LazyListScope.subgroupsForEntitySection(
    activeUserPublicKey: HexKey,
    relayHint: String?,
    candidate: SubgroupOrigin.Candidate,
    subgroups: List<SubgroupManager.Subgroup>,
    ceremonies: List<DkgSession>,
    canAddSubgroup: Boolean,
    onNavigateToRoute: (Route) -> Unit,
) {
    // Nothing made, nothing running and nothing this device could start: the
    // section would be a heading over an empty space. Hidden entirely rather
    // than shown empty, the way the room's own subgroup entry is -- a member who
    // holds no share of the key cannot act on any of it.
    if (subgroups.isEmpty() && ceremonies.isEmpty() && !canAddSubgroup) return

    item { HorizontalDivider() }

    item {
        Text(
            text = stringResource(Res.string.rooms_of_its_own),
            style = MaterialTheme.typography.labelMedium
        )
    }

    items(
        items = subgroups,
        key = { subgroup -> subgroup.chatRoomId }
    ) { subgroup ->
        Card(
            onClick = {
                onNavigateToRoute.invoke(
                    subgroup.room?.let {
                        ChatRoomDetailRoute(
                            activeUserPublicKey = activeUserPublicKey,
                            chatRoomId = it.chatRoom.id,
                            relayHint = relayHint
                        )
                    } ?: NostrEventDetailRoute(
                        activeUserPublicKey = activeUserPublicKey,
                        nostrEventId = subgroup.certificate.id
                    )
                )
            }
        ) {
            ListItem(
                leadingContent = {
                    Icon(Icons.Default.AccountTree, contentDescription = Decorative)
                },
                // The room where this device holds one, and only otherwise the
                // certificate's: the certificate's name is what the parent
                // approved at founding, frozen, and the child may since have been
                // renamed. Where the room is here it is the live answer.
                headlineContent = {
                    Text(
                        text = subgroup.room?.chatRoom?.subject
                            ?: subgroup.name
                            ?: stringResource(Res.string.a_subgroup)
                    )
                },
                supportingContent = {
                    Text(
                        text = when {
                            subgroup.isJoined -> stringResource(
                                Res.string.run_by_members,
                                subgroup.adminPublicKeys.size.toString()
                            )
                            subgroup.keyState == null ->
                                stringResource(Res.string.certified_not_yet_created)
                            else -> stringResource(Res.string.created_you_are_not_a_member)
                        }
                    )
                }
            )
        }
    }

    // One line however many are running. Two ceremonies for one thing is already
    // the thing this is warning about, and listing them separately would make
    // the warning look like a feature.
    if (ceremonies.isNotEmpty()) {
        item {
            Card {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.HourglassTop, contentDescription = Decorative)
                    },
                    headlineContent = {
                        Text(stringResource(Res.string.a_key_ceremony_for_a_room_is_under_way))
                    }
                )
            }
        }
    }

    if (canAddSubgroup) {
        item {
            TextButton(
                onClick = {
                    onNavigateToRoute.invoke(
                        SelectSubgroupAdminsRoute(
                            activeUserPublicKey = activeUserPublicKey,
                            parentChatRoomId = candidate.parentChatRoomId,
                            relayHint = relayHint,
                            name = candidate.name,
                            originKind = candidate.origin.kind,
                            originId = candidate.origin.id,
                        )
                    )
                }
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = Decorative)

                Spacer(modifier = Modifier.width(MaterialTheme.spacing.itemGap))

                // "Another" once there is one, so the button is not inviting
                // something that already exists. Changed rather than hidden,
                // because a second room is a thing a group may genuinely want.
                Text(
                    stringResource(
                        if (subgroups.isEmpty() && ceremonies.isEmpty()) {
                            Res.string.give_this_a_room_of_its_own
                        } else {
                            Res.string.give_this_another_room
                        }
                    )
                )
            }
        }
    }
}
