package net.mullvad.mullvadvpn.lib.ipc

import kotlinx.coroutines.flow.Flow

interface ServiceConnection {

    val events: Flow<Event>

    fun trySendRequest(request: Request, logErrors: Boolean = false): Boolean
}
