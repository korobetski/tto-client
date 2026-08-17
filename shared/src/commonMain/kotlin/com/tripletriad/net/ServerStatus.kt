package com.tripletriad.net

import com.tripletriad.protocol.AppVersion
import com.tripletriad.protocol.CURRENT_VERSION
import com.tripletriad.protocol.ClientPlatform
import com.tripletriad.protocol.ServerInfo
import com.tripletriad.protocol.VERSION_HEADER
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException

internal expect val clientPlatform: ClientPlatform

sealed interface ServerStatus {

    data object Unknown : ServerStatus

    data object Checking : ServerStatus

    data class Online(val info: ServerInfo, val latencyMillis: Long) : ServerStatus

    data class Degraded(val info: ServerInfo, val latencyMillis: Long) : ServerStatus

    data class Outdated(val info: ServerInfo) : ServerStatus

    data class Unreachable(val cause: String) : ServerStatus

    data class Unusable(val detail: String) : ServerStatus
}

val ServerStatus.isUsable: Boolean get() = this is ServerStatus.Online

val ServerStatus.serverInfo: ServerInfo?
    get() = when (this) {
        is ServerStatus.Online -> info
        is ServerStatus.Degraded -> info
        is ServerStatus.Outdated -> info
        else -> null
    }

val ServerStatus.latency: Long?
    get() = when (this) {
        is ServerStatus.Online -> latencyMillis
        is ServerStatus.Degraded -> latencyMillis
        else -> null
    }

fun ServerInfo.downloadForThisPlatform(): String? = release?.downloads?.get(clientPlatform)

class ServerProbe(
    private val client: HttpClient,
    private val clientVersion: AppVersion = CURRENT_VERSION,
    private val elapsed: () -> Long,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun probe(baseUrl: String): ServerStatus {
        val startedAt = elapsed()
        return try {
            val response = client.get("${baseUrl.trimEnd('/')}/server") {
                // Sent even though this route does not refuse anyone, so a server's logs can see
                // which builds are asking — and so this request looks like every other one.
                header(VERSION_HEADER, clientVersion.toString())
                header(HttpHeaders.Accept, "application/json")
            }
            response.readStatus(elapsed() - startedAt)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            ServerStatus.Unreachable(failure.message ?: failure.toString())
        }
    }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private suspend fun HttpResponse.readStatus(latency: Long): ServerStatus {
        if (status.value != HTTP_OK) {
            return ServerStatus.Unusable("the server answered ${status.value}")
        }

        val info = try {
            body<ServerInfo>()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            // A 200 that is not a `ServerInfo`: a captive portal, a proxy, or an address that is
            // not this game at all. Falling back to the header keeps a *newer* server usable — it
            // is the one field this build is certain it can read.
            return headers[VERSION_HEADER]?.let(AppVersion::parse)?.let { theirs ->
                if (theirs.acceptsPeer(clientVersion)) {
                    ServerStatus.Unusable("unreadable answer: ${failure.message}")
                } else {
                    ServerStatus.Outdated(minimalInfo(theirs))
                }
            } ?: ServerStatus.Unusable("unreadable answer: ${failure.message}")
        }

        return when {
            !info.accepts(clientVersion) -> ServerStatus.Outdated(info)
            !info.ready -> ServerStatus.Degraded(info, latency)
            else -> ServerStatus.Online(info, latency)
        }
    }

    private fun minimalInfo(theirs: AppVersion) = ServerInfo(
        name = "",
        version = theirs,
        minimumClient = theirs,
    )

    private companion object {
        const val HTTP_OK = 200
    }
}
