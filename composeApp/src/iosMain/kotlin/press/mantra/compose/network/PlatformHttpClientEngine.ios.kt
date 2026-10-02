package press.mantra.compose.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

internal actual fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*> = Darwin
