package ac.aux.compose.nostr

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

object Relays {
    val nos = RelayUrlNormalizer.normalize("wss://nos.lol")
    val mom = RelayUrlNormalizer.normalize("wss://nostr.mom")
    val primal = RelayUrlNormalizer.normalize("wss://relay.primal.net")
    val damus = RelayUrlNormalizer.normalize("wss://relay.damus.io")
    val wine = RelayUrlNormalizer.normalize("wss://nostr.wine")

    val where = RelayUrlNormalizer.normalize("wss://relay.noswhere.com")
    val elites = RelayUrlNormalizer.normalize("wss://nostrelites.org")

    val bitcoiner = RelayUrlNormalizer.normalize("wss://nostr.bitcoiner.social")
    val oxtr = RelayUrlNormalizer.normalize("wss://nostr.oxtr.dev")

    val nostoday = RelayUrlNormalizer.normalize("wss://search.nos.today")
    val antiprimal = RelayUrlNormalizer.normalize("wss://antiprimal.net")
    val ditto = RelayUrlNormalizer.normalize("wss://relay.ditto.pub")

    val auth = RelayUrlNormalizer.normalize("wss://auth.nostr1.com")
    val oxchat = RelayUrlNormalizer.normalize("wss://relay.0xchat.com")

    val news = RelayUrlNormalizer.normalize("wss://news.utxo.one")

    val purplepages = RelayUrlNormalizer.normalize("wss://purplepag.es")
    val coracle = RelayUrlNormalizer.normalize("wss://indexer.coracle.social")
    val userkinds = RelayUrlNormalizer.normalize("wss://user.kindpag.es")
    val yabu = RelayUrlNormalizer.normalize("wss://directory.yabu.me")
    val nostr1 = RelayUrlNormalizer.normalize("wss://profiles.nostr1.com")

    val bootstrapInboxRelaySet = setOf(damus, primal, mom, nos, bitcoiner, oxtr, yabu)
    val eventFinderRelaySet = setOf(wine, damus, primal, mom, nos, bitcoiner, oxtr)
}