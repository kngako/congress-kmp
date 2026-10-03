package press.mantra.compose.nostr.subgroup

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.crypto.frost.Frost
import fr.acinq.bitcoin.crypto.frost.IndividualNonce
import fr.acinq.bitcoin.crypto.frost.KeyMaterial
import fr.acinq.bitcoin.crypto.frost.SecretNonce
import fr.acinq.bitcoin.crypto.frost.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import press.mantra.compose.extensions.toHex
import press.mantra.compose.managers.SharedKeyDerivation
import press.mantra.compose.managers.SubgroupManager
import press.mantra.compose.managers.SubgroupOrigin
import press.mantra.compose.nostr.nip30303.ArtifactEvent
import press.mantra.compose.nostr.nip30303.DialectEvent
import press.mantra.compose.nostr.nip30303.ProjectEvent
import press.mantra.compose.nostr.nip30303.TranslationArtifactVersionEvent
import press.mantra.compose.nostr.subgroup.tags.SubgroupOriginTag

/**
 * What a birth certificate is allowed to convince a member of.
 *
 * A certificate is the whole of what a subgroup relationship is, so everything
 * downstream of it -- a `GroupKeyState` carrying a parent, a subgroup list, a
 * lineage shown to a user -- is worth exactly what `certifies` is worth. It has
 * a database nowhere in it on purpose: the deciding is pure, and pure is what
 * can be exercised exhaustively.
 *
 * Two halves again, and they are the same two `GroupKeyStateTest` names. A
 * certificate has to be **true** -- the id it certifies rederives from the key
 * and path it carries, so it cannot be pointed at a room that key did not make.
 * And it has to be the **parent's** -- signed by the room it names as parent,
 * which no single member of that room can do.
 *
 * The last test is the odd one and the most important to keep. It asserts a
 * certificate still verifies when its name and admin set have nothing to do with
 * the room's current ones, because those are the *founding* roster and the world
 * moves. A `certifies` that compared them would start rejecting valid
 * certificates the first time somebody joined a subgroup, and the rejection
 * would look exactly like a forgery.
 */
class SubgroupBirthCertificateEventTest {
    private val participants = 3
    private val threshold = 2

    /** The parent group: the quorum that does the certifying. */
    private val parentMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("1c0ffee0000000000000000000000000000000000000000000000000000000a1")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    /** The child: its own ceremony, its own key, its own room. */
    private val childMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("2bada550000000000000000000000000000000000000000000000000000000b2")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    /** A third group entirely, for the certificates signed by the wrong room. */
    private val strangerMaterial: KeyMaterial = Frost.trustedDealerKeygen(
        thresholdSecretKey = PrivateKey(
            ByteVector32("3decade0000000000000000000000000000000000000000000000000000000c3")
        ),
        nParticipants = participants,
        threshold = threshold
    )

    private val path = SharedKeyDerivation.MARMOT_ADMIN_GROUP_PATH

    private val childKey = childMaterial.thresholdPublicKey.value.toHex()
    private val strangerKey = strangerMaterial.thresholdPublicKey.value.toHex()

    private val parentChatRoomId =
        SharedKeyDerivation.marmotGroupId(parentMaterial.thresholdPublicKey.value.toHex(), path)
    private val subgroupChatRoomId = SharedKeyDerivation.marmotGroupId(childKey, path)

    private val admins = listOf("a".repeat(64), "b".repeat(64), "c".repeat(64))

    private val name = "Translation team"

    @Test
    fun `a certificate the parent signed for the child it names verifies`() {
        assertTrue(
            SubgroupBirthCertificateEvent.certifies(
                event = certificate(),
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a certificate whose content and address disagree is refused`() {
        // The content is the claim and the `d` tag is the address. One pointing
        // somewhere the other does not is not a thing to guess the meaning of --
        // and guessing the content would let a certificate be filed against a
        // child it does not claim.
        val misaddressed = certificate(
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = parentChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = childKey,
                adminPublicKeys = admins,
                name = name,
                path = path
            )
        )

        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = misaddressed,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a certificate whose id does not derive from the key it names is refused`() {
        // The attack: certify a room id the parent's admins were shown alongside
        // a key that does not make it, so that what they approved and what they
        // signed are two different groups.
        val mismatched = certificate(
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = strangerKey,
                adminPublicKeys = admins,
                name = name,
                path = path
            )
        )

        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = mismatched,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a certificate naming the right key at the wrong path is refused`() {
        // The path is half the derivation, so a certificate at m/9420/0/1 names
        // a room that is not the one being certified just as surely as a wrong
        // key would.
        val elsewhere = certificate(
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = childKey,
                adminPublicKeys = admins,
                name = name,
                path = listOf(9420L, 0L, 1L)
            )
        )

        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = elsewhere,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a certificate signed by another group is refused`() {
        // A real quorum and a real signature, by a group with no standing to say
        // anything about this child. Lineage is only ever evidence about the
        // parent that signed it.
        val stranger = certificate(signer = strangerMaterial, author = strangerMaterial)

        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = stranger,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a certificate authored by the parent and signed by somebody else is refused`() {
        // Says the right things and is authored by the right room, and the
        // signature is over that id by a key that is not the room's. This is the
        // shape a member forging a lineage would produce.
        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = certificate(signer = strangerMaterial),
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a certificate whose signature is not a signature is refused`() {
        // Everything off the wire is allowed to be nonsense, and nonsense means
        // no rather than an exception.
        listOf("f".repeat(128), "not a signature", "").forEach { rubbish ->
            assertFalse(
                SubgroupBirthCertificateEvent.certifies(
                    event = certificate(signature = rubbish),
                    subgroupChatRoomId = subgroupChatRoomId,
                    parentChatRoomId = parentChatRoomId
                ),
                "a certificate signed with \"$rubbish\" was accepted"
            )
        }
    }

    @Test
    fun `a certificate whose key is not a point on the curve is refused`() {
        // 66 characters of hex, so it passes the shape check and fails the walk.
        // `marmotGroupId` throws on it and the catch is what turns that into a no.
        val notAKey = certificate(
            tags = arrayOf(
                arrayOf("d", subgroupChatRoomId),
                arrayOf("parent_group", parentChatRoomId),
                arrayOf("subgroup_key", "0".repeat(66)),
                arrayOf("frost_path", SharedKeyDerivation.formatPath(path)),
                arrayOf("name", name)
            )
        )

        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = notAKey,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a certificate carrying no parent no key or no path is refused`() {
        listOf("parent_group", "subgroup_key", "frost_path").forEach { dropped ->
            val incomplete = certificate(
                tags = SubgroupBirthCertificateEvent.assembleTags(
                    subgroupChatRoomId = subgroupChatRoomId,
                    parentChatRoomId = parentChatRoomId,
                    thresholdPublicKey = childKey,
                    adminPublicKeys = admins,
                    name = name,
                    path = path
                ).filterNot { it[0] == dropped }.toTypedArray()
            )

            assertFalse(
                SubgroupBirthCertificateEvent.certifies(
                    event = incomplete,
                    subgroupChatRoomId = subgroupChatRoomId,
                    parentChatRoomId = parentChatRoomId
                ),
                "a certificate with no $dropped tag was accepted"
            )
        }
    }

    @Test
    fun `a certificate still verifies once the subgroup has been renamed and re-staffed`() {
        // The one that has to pass. The name and the `p` tags are the founding
        // roster: a certificate is signed once and members join and leave
        // afterwards. This test exists to fail loudly the day somebody adds the
        // roster check that looks obviously missing -- which would reject every
        // certificate for a subgroup that has grown, and would look like a
        // forgery rather than like a rule.
        val founding = certificate(
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = childKey,
                adminPublicKeys = listOf("d".repeat(64)),
                name = "What it was called in 2026",
                path = path
            )
        )

        assertTrue(
            SubgroupBirthCertificateEvent.certifies(
                event = founding,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `the tags a certificate is proposed on read back as they were written`() {
        val tags = SubgroupBirthCertificateEvent.assembleTags(
            subgroupChatRoomId = subgroupChatRoomId,
            parentChatRoomId = parentChatRoomId,
            thresholdPublicKey = childKey,
            adminPublicKeys = admins,
            name = name,
            path = path
        )

        assertEquals(subgroupChatRoomId, SubgroupBirthCertificateEvent.parseSubgroupChatRoomId(tags))
        assertEquals(parentChatRoomId, SubgroupBirthCertificateEvent.parseParentChatRoomId(tags))
        assertEquals(childKey, SubgroupBirthCertificateEvent.parseThresholdPublicKey(tags))
        assertEquals(path, SubgroupBirthCertificateEvent.parsePath(tags))
        assertEquals(admins, SubgroupBirthCertificateEvent.parseAdminPublicKeys(tags))
        assertEquals(name, SubgroupBirthCertificateEvent.parseName(tags))
    }

    @Test
    fun `a duplicated admin is written once`() {
        // The p-tags are a roster, and `n` is not read off them by anything --
        // but a list that says somebody twice reads as two people to a human,
        // which is the only audience they have.
        val tags = SubgroupBirthCertificateEvent.assembleTags(
            subgroupChatRoomId = subgroupChatRoomId,
            parentChatRoomId = parentChatRoomId,
            thresholdPublicKey = childKey,
            adminPublicKeys = admins + admins.first(),
            name = name,
            path = path
        )

        assertEquals(admins, SubgroupBirthCertificateEvent.parseAdminPublicKeys(tags))
    }

    @Test
    fun `a threshold key is only read out of a tag that carries one`() {
        // Shape only, and borrowed from GroupKeyStateEvent so that the two
        // readers of a threshold key cannot disagree about what one is.
        assertNull(SubgroupBirthCertificateEvent.parseThresholdPublicKey(arrayOf(arrayOf("subgroup_key", ""))))
        assertNull(SubgroupBirthCertificateEvent.parseThresholdPublicKey(arrayOf(arrayOf("subgroup_key", subgroupChatRoomId))))
        assertNull(SubgroupBirthCertificateEvent.parseThresholdPublicKey(arrayOf(arrayOf("subgroup_key", "z".repeat(66)))))
        assertNull(SubgroupBirthCertificateEvent.parseThresholdPublicKey(arrayOf(arrayOf("subgroup_key"))))
    }

    /**
     * A certificate as the app produces one: the caller's fields under the
     * parent room's key, with the id hashed over them and the group's own
     * signature on it.
     *
     * [author] decides whose room the event claims to be from and [signer] whose
     * quorum actually signs, so the two can be pulled apart -- which is the only
     * way to test that both are checked. [signature] replaces the real one
     * outright, for the cases where what arrives is not a signature at all.
     */
    // ---- What an arriving certificate is ------------------------------------
    //
    // `SubgroupManager.classify` lives in a manager and is tested here, because
    // every case it decides is a question about a certificate and this is where
    // the three real quorums are. It needs no database: `certifies` checks a
    // signature against a room id, and a room id is a public key.

    @Test
    fun `in the parent's room a certificate is about the room's own child`() {
        assertEquals(
            SubgroupManager.ArrivingCertificate.OurChild(subgroupChatRoomId),
            SubgroupManager.classify(parentChatRoomId, certificate())
        )
    }

    @Test
    fun `in the child's room the same certificate is an offer to adopt it`() {
        // The half of an adoption the child's members would otherwise never hear
        // about until a proposal asked for their signature. One event, two
        // meanings, and the room it arrived in is the whole of the difference.
        assertEquals(
            SubgroupManager.ArrivingCertificate.OfferToUs(parentChatRoomId),
            SubgroupManager.classify(subgroupChatRoomId, certificate())
        )
    }

    @Test
    fun `in a third party's room it is nothing at all`() {
        val strangerRoom = SharedKeyDerivation.marmotGroupId(strangerKey, path)

        // Neither this room's act nor about this room. Whoever signed it, it is
        // nobody's business here, and a line in this transcript would be noise
        // about two groups the reader may not be in.
        assertNull(SubgroupManager.classify(strangerRoom, certificate()))
    }

    @Test
    fun `an offer has to be signed by the group it names`() {
        // The `parent_group` tag says the parent certified this room; the author
        // says who actually signed. A stranger putting another group's name on
        // its own signature is the forgery this check exists for.
        val forged = certificate(author = strangerMaterial)

        assertNull(SubgroupManager.classify(subgroupChatRoomId, forged))
    }

    @Test
    fun `a group is not its own child however truthfully it says so`() {
        // The one self-referential certificate `certifies` waves through: author,
        // subject and named parent all one room, the id deriving from the key,
        // the signature verifying. Refused before either branch -- read as an
        // ordinary child it would put the group in its own list of subgroups.
        val itself = certificate(
            content = parentChatRoomId,
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = parentChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = parentMaterial.thresholdPublicKey.value.toHex(),
                adminPublicKeys = admins,
                name = name,
                path = path
            )
        )

        assertNull(SubgroupManager.classify(parentChatRoomId, itself))
    }

    @Test
    fun `an offer must be about this room and not merely arrive in it`() {
        val strangerRoom = SharedKeyDerivation.marmotGroupId(strangerKey, path)
        val aboutSomebodyElse = certificate(
            content = strangerRoom,
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = strangerRoom,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = strangerKey,
                adminPublicKeys = admins,
                name = name,
                path = path
            )
        )

        assertNull(SubgroupManager.classify(subgroupChatRoomId, aboutSomebodyElse))
    }

    // ---- `addresses`: the half that can be asked of a proposal ------------
    //
    // The ceremony screen has to tell one *in-flight* certificate signing from
    // another in the parent's room, and a parent certifies every subgroup it
    // ever makes from that one room. `certifies` cannot answer it: it ends on a
    // signature by the parent's quorum, which is exactly what the screen is
    // waiting for. `addresses` is the structural half, and the only thing worth
    // asserting about it is where it stops.

    @Test
    fun `an unsigned proposal addresses its child and certifies nothing`() {
        // What the ceremony screen is actually holding: the event as it was
        // assembled, before anybody signed it. Any string in `sig` serves --
        // the point is that no quorum has put one there.
        val proposal = certificate(signature = "0".repeat(128))

        assertTrue(
            SubgroupBirthCertificateEvent.addresses(
                event = proposal,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            ),
            "the screen could not tell which subgroup its own proposal is about"
        )

        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = proposal,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            ),
            "an unsigned proposal is being read as a parent's certificate"
        )
    }

    @Test
    fun `a forgery by another group addresses the pair it names and certifies nothing`() {
        // The reason `addresses` must never decide a lineage: anybody can author
        // an event naming any pair, and this says yes to it. The guard is that
        // nothing downstream of a lineage asks `addresses` -- only the screen
        // deciding which of its own room's signings to draw does.
        val forged = certificate(author = childMaterial)

        assertTrue(
            SubgroupBirthCertificateEvent.addresses(
                event = forged,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )

        assertFalse(
            SubgroupBirthCertificateEvent.certifies(
                event = forged,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a sibling's proposal is not addressed to this child`() {
        // Two subgroups of one parent, which is the case the screen exists to
        // tell apart. The sibling's proposal is a real, well-formed certificate
        // request; it is simply about somebody else.
        val siblingRoomId = SharedKeyDerivation
            .marmotGroupId(strangerMaterial.thresholdPublicKey.value.toHex(), path)

        val sibling = certificate(
            content = siblingRoomId,
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = siblingRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = strangerMaterial.thresholdPublicKey.value.toHex(),
                adminPublicKeys = admins,
                name = "Another team",
                path = path
            )
        )

        assertFalse(
            SubgroupBirthCertificateEvent.addresses(
                event = sibling,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            ),
            "one subgroup's rung is following a sibling's proposal"
        )

        // And it is addressed to the sibling, so the sibling's own screen finds
        // it -- the two are told apart rather than both being refused.
        assertTrue(
            SubgroupBirthCertificateEvent.addresses(
                event = sibling,
                subgroupChatRoomId = siblingRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `a proposal naming another parent is not addressed by this one`() {
        val fromElsewhere = certificate(
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = SharedKeyDerivation
                    .marmotGroupId(strangerMaterial.thresholdPublicKey.value.toHex(), path),
                thresholdPublicKey = childKey,
                adminPublicKeys = admins,
                name = name,
                path = path
            )
        )

        assertFalse(
            SubgroupBirthCertificateEvent.addresses(
                event = fromElsewhere,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    @Test
    fun `everything certifies addresses so the weaker one cannot be the stricter`() {
        // `certifies` calls `addresses` and adds to it, and this is what keeps
        // that true: a change that let one pass where the other refuses would
        // mean two different answers to "which child is this about".
        val valid = certificate()

        assertTrue(
            SubgroupBirthCertificateEvent.certifies(
                event = valid,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
        assertTrue(
            SubgroupBirthCertificateEvent.addresses(
                event = valid,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    // --- what the subgroup is for ---

    @Test
    fun `an origin round trips for each of the four kinds`() {
        listOf(
            DialectEvent.KIND,
            ArtifactEvent.KIND,
            TranslationArtifactVersionEvent.KIND,
            ProjectEvent.KIND
        ).forEach { kind ->
            val entityId = "a1".padEnd(64, '0')
            val tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = childKey,
                adminPublicKeys = admins,
                name = name,
                path = path,
                origin = SubgroupOrigin.Ref(kind = kind, id = entityId)
            )

            assertEquals(
                SubgroupOrigin.Ref(kind = kind, id = entityId),
                SubgroupBirthCertificateEvent.parseOrigin(tags),
                "kind $kind did not read back"
            )
        }
    }

    @Test
    fun `a certificate made for nothing in particular has no origin`() {
        val tags = SubgroupBirthCertificateEvent.assembleTags(
            subgroupChatRoomId = subgroupChatRoomId,
            parentChatRoomId = parentChatRoomId,
            thresholdPublicKey = childKey,
            adminPublicKeys = admins,
            name = name,
            path = path
        )

        assertNull(SubgroupBirthCertificateEvent.parseOrigin(tags))
        assertTrue(
            tags.none { it[0] == SubgroupOriginTag.TAG_NAME },
            "an origin tag was written for a subgroup that is for nothing"
        )
    }

    /**
     * Half a reference is not a weaker reference. A reader without the kind
     * cannot tell a project it does not hold from something that is not a
     * project, and an id of the wrong length cannot be resolved by anything --
     * nothing downstream would refuse it, because nothing resolves an origin
     * before showing it.
     */
    @Test
    fun `a malformed origin reads as none rather than as half of one`() {
        val entityId = "a1".padEnd(64, '0')

        listOf(
            arrayOf(SubgroupOriginTag.TAG_NAME, "30330"),
            arrayOf(SubgroupOriginTag.TAG_NAME, "not-a-kind", entityId),
            arrayOf(SubgroupOriginTag.TAG_NAME, "30330", entityId.dropLast(1)),
            arrayOf(SubgroupOriginTag.TAG_NAME, "30330", ""),
            arrayOf(SubgroupOriginTag.TAG_NAME)
        ).forEach { malformed ->
            assertNull(
                SubgroupBirthCertificateEvent.parseOrigin(arrayOf(malformed)),
                "${malformed.toList()} parsed as an origin"
            )
        }
    }

    /**
     * The origin joins the founding roster, and this is the second half of the
     * test above that exists to fail loudly. A certificate naming an entity this
     * device does not hold still certifies -- a subgroup's members need never
     * have been in the parent, so a certificate pointing at a row they will never
     * see is the normal case rather than a forgery.
     */
    @Test
    fun `a certificate for an entity this device cannot resolve still verifies`() {
        val signed = certificate(
            tags = SubgroupBirthCertificateEvent.assembleTags(
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId,
                thresholdPublicKey = childKey,
                adminPublicKeys = admins,
                name = name,
                path = path,
                origin = SubgroupOrigin.Ref(
                    kind = ProjectEvent.KIND,
                    id = "beef".padEnd(64, '0')
                )
            )
        )

        assertTrue(
            SubgroupBirthCertificateEvent.certifies(
                event = signed,
                subgroupChatRoomId = subgroupChatRoomId,
                parentChatRoomId = parentChatRoomId
            )
        )
    }

    /**
     * The origin sits before the run of `p` tags, and the position is load
     * bearing: a reader that predates it takes the tags it knows off the front,
     * and one inserted into the admin run would split the founding roster for
     * anybody reading positionally.
     */
    @Test
    fun `an origin disturbs neither the admins nor the name`() {
        val tags = SubgroupBirthCertificateEvent.assembleTags(
            subgroupChatRoomId = subgroupChatRoomId,
            parentChatRoomId = parentChatRoomId,
            thresholdPublicKey = childKey,
            adminPublicKeys = admins,
            name = name,
            path = path,
            origin = SubgroupOrigin.Ref(
                kind = DialectEvent.KIND,
                id = "a1".padEnd(64, '0')
            )
        )

        assertEquals(admins, SubgroupBirthCertificateEvent.parseAdminPublicKeys(tags))
        assertEquals(name, SubgroupBirthCertificateEvent.parseName(tags))

        val originAt = tags.indexOfFirst { it[0] == SubgroupOriginTag.TAG_NAME }
        val firstAdminAt = tags.indexOfFirst { it[0] == "p" }

        assertTrue(originAt in 0 until firstAdminAt, "the origin is inside the admin run")
    }

    private fun certificate(
        content: String = subgroupChatRoomId,
        tags: Array<Array<String>> = SubgroupBirthCertificateEvent.assembleTags(
            subgroupChatRoomId = subgroupChatRoomId,
            parentChatRoomId = parentChatRoomId,
            thresholdPublicKey = childKey,
            adminPublicKeys = admins,
            name = name,
            path = path
        ),
        createdAt: Long = 1_700_000_000,
        author: KeyMaterial = parentMaterial,
        signer: KeyMaterial = author,
        signature: String? = null
    ): Event {
        val groupPubKey = SharedKeyDerivation
            .derive(author.thresholdPublicKey.value.toHex(), path)
            .hex

        val id = EventHasher.hashId(
            pubKey = groupPubKey,
            createdAt = createdAt,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = tags,
            content = content
        )

        return Event(
            id = id,
            pubKey = groupPubKey,
            createdAt = createdAt,
            kind = SubgroupBirthCertificateEvent.KIND,
            tags = tags,
            content = content,
            sig = signature ?: groupSignature(signer, id)
        )
    }

    /**
     * A real FROST signature by [material]'s quorum over [eventId], through the
     * same shape `FrostSigningManager.advance` runs -- nonces, a signer set,
     * partial signatures, an aggregate.
     *
     * Assembled any other way it would not be evidence about the signatures this
     * app actually produces.
     */
    private fun groupSignature(material: KeyMaterial, eventId: String): String {
        val cache = SharedKeyDerivation
            .derive(material.thresholdPublicKey.value.toHex(), path)
            .cache
        val message = ByteVector(eventId.hexToByteArray())
        val signerIds = listOf(0, 1)

        val nonces = signerIds.map { signerId ->
            SecretNonce.generate(
                sessionRandom = ByteVector32("a".repeat(63) + "${signerId + 1}"),
                secretShare = material.secretShares[signerId],
                publicShare = material.publicShares[signerId],
                tweakedThresholdPublicKey = cache.tweakedPublicKey,
                message = message,
                extraInput = null
            )
        }

        val signingSession = Session.create(
            aggregatedNonce = IndividualNonce.aggregate(nonces.map { it.second }).right!!,
            signerIds = signerIds.map { it.toUInt() },
            signerPublicShares = signerIds.map { material.publicShares[it] },
            nParticipants = participants,
            threshold = threshold,
            tweakCache = cache,
            message = message
        )

        val partials = signerIds.mapIndexed { position, signerId ->
            signingSession.sign(
                nonces[position].first,
                material.secretShares[signerId],
                signerId.toUInt()
            ).right!!
        }

        return signingSession.aggregateSigs(partials).right!!.toByteArray().toHex()
    }
}
