package net.mullvad.mullvadvpn.lib.billing

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import net.mullvad.mullvadvpn.lib.ipc.Event
import net.mullvad.mullvadvpn.lib.ipc.Request
import net.mullvad.mullvadvpn.lib.ipc.ServiceConnection
import net.mullvad.mullvadvpn.model.PlayPurchase
import net.mullvad.mullvadvpn.model.PlayPurchaseInitError
import net.mullvad.mullvadvpn.model.PlayPurchaseInitResult
import net.mullvad.mullvadvpn.model.PlayPurchaseVerifyError
import net.mullvad.mullvadvpn.model.PlayPurchaseVerifyResult

class PlayPurchaseRepository(val serviceConnection: ServiceConnection) {

    suspend fun purchaseInitialisation(): PlayPurchaseInitResult {
        val result = serviceConnection.trySendRequest(Request.InitPlayPurchase, logErrors = true)

        return if (result) {
            serviceConnection.events
                .filterIsInstance(Event.PlayPurchaseInitResultEvent::class)
                .first()
                .result
        } else {
            PlayPurchaseInitResult.Error(PlayPurchaseInitError.OtherError)
        }
    }

    suspend fun purchaseVerification(purchase: PlayPurchase): PlayPurchaseVerifyResult {
        val result =
            serviceConnection.trySendRequest(Request.VerifyPlayPurchase(purchase), logErrors = true)
        return if (result) {
            serviceConnection.events
                .filterIsInstance(Event.PlayPurchaseVerifyResultEvent::class)
                .first()
                .result
        } else {
            PlayPurchaseVerifyResult.Error(PlayPurchaseVerifyError.OtherError)
        }
    }
}
