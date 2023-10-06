package net.mullvad.mullvadvpn.service.endpoint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.trySendBlocking
import net.mullvad.mullvadvpn.lib.ipc.Event
import net.mullvad.mullvadvpn.lib.ipc.Request
import net.mullvad.mullvadvpn.model.PlayPurchase

// WIP - adapted copy of VoucherRedeemer
class PlayPurchaseHandler(private val endpoint: ServiceEndpoint) {
    private val daemon
        get() = endpoint.intermittentDaemon

    private val playPurchaseChannel = spawnActor()

    init {
        endpoint.dispatcher.registerHandler(Request.InitPlayPurchase::class) {
            playPurchaseChannel.trySendBlocking(Command.InitPlayPurchase)
        }
        endpoint.dispatcher.registerHandler(Request.VerifyPlayPurchase::class) { request ->
            playPurchaseChannel.trySendBlocking(Command.VerifyPlayPurchase(request.playPurchase))
        }
    }

    fun onDestroy() {
        playPurchaseChannel.close()
    }

    private fun spawnActor() =
        GlobalScope.actor<Command>(Dispatchers.Default, Channel.UNLIMITED) {
            try {
                for (command in channel) {
                    when (command) {
                        is Command.InitPlayPurchase -> initializePurchase()
                        is Command.VerifyPlayPurchase -> verifyPlayPurchase(command.playPurchase)
                    }
                }
            } catch (exception: ClosedReceiveChannelException) {
                // Channel was closed, stop the actor
            }
        }

    private suspend fun initializePurchase() {
        val result = daemon.await().initPlayPurchase()
        endpoint.sendEvent(Event.PlayPurchaseInitResultEvent(result))
    }

    private suspend fun verifyPlayPurchase(playPurchase: PlayPurchase) {
        val result = daemon.await().verifyPlayPurchase(playPurchase)
        endpoint.sendEvent(Event.PlayPurchaseVerifyResultEvent(playPurchase, result))
    }

    companion object {
        private sealed class Command {
            data object InitPlayPurchase : Command()

            data class VerifyPlayPurchase(val playPurchase: PlayPurchase) : Command()
        }
    }
}
