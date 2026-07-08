package com.alexpo.grammermate.v2.core.data.audio

import android.content.Context
import android.util.Log
import com.alexpo.grammermate.v2.di.AudioCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Маршрутизация захвата аудио на подключённую Bluetooth-гарнитуру через SCO
 * (Synchronous Connection-Oriented) или современный CommunicationDevice API.
 *
 * Используется чтобы `AudioRecord` (offline ASR) захватывал аудио с BT-микрофона,
 * а не со встроенного микрофона устройства. SRS-003 §2.8, FR-11.
 *
 * **Двухпутёвый API:**
 *  - API < 31 (legacy): `AudioManager.startBluetoothSco` + `BroadcastReceiver`
 *    на `ACTION_SCO_AUDIO_STATE_UPDATED` + `BluetoothHeadset.startVoiceRecognition`.
 *  - API ≥ 31 (modern): `AudioManager.setCommunicationDevice(btDevice)` +
 *    `addOnCommunicationDeviceChangedListener` (+ legacy SCO флаг для system-wide
 *    routing — Google SpeechRecognizer смотрит на него).
 *
 * Перенос из legacy `shared/audio/BluetoothAudioRouter.kt` (v1). SRS-003 §2.8.
 *
 * ## Статус (scaffold, E03 task #467)
 *
 * Скелет: публичная поверхность зафиксирована, нативная логика — TODO. Body-задача
 * (AC-14) реализует TODO внутри фиксированного контракта.
 *
 * @param context application context (для `AudioManager`, BroadcastReceiver-регистрации).
 * @param scope application-scoped coroutine scope — для `AudioManager`-коллбэков
 *  (`addOnCommunicationDeviceChangedListener` требует Executor/scope.launch).
 *  SRS §2.11 — `@ApplicationScoped` CoroutineScope, Dispatchers.Main для коллбэков.
 */
@Singleton
class BluetoothAudioRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    @AudioCoroutineScope private val scope: CoroutineScope,
) {
    private val _isConnected = MutableStateFlow(false)

    /** Реактивное состояние: активна ли BT-маршрутизация сейчас. */
    val isConnected: StateFlow<Boolean> = _isConnected

    /**
     * Доступна ли Bluetooth-маршрутизация (адаптер + SCO-устройство подключено).
     * НЕ требует BLUETOOTH_CONNECT permission на API 31+ — проверяет только
     * device-списки AudioManager'а.
     *
     * TODO(body, AC-14): перенести из legacy `BluetoothAudioRouter.isBluetoothAvailable()`
     *   — API ≥ 31: `audioManager.availableCommunicationDevices.any { ... }`;
     *   API < 31: `isBluetoothScoAvailableOffCall`.
     */
    fun isBluetoothAvailable(): Boolean {
        // TODO(body): port legacy BluetoothAudioRouter.isBluetoothAvailable.
        Log.d(TAG, "isBluetoothAvailable() — skeleton returns false")
        return false
    }

    /**
     * Подключить BluetoothHeadset profile proxy. Вызывать до startVoiceRecognition.
     * Требует BLUETOOTH_CONNECT permission на API 31+.
     *
     * TODO(body): legacy `connectHeadsetProfile()`.
     */
    fun connectHeadsetProfile() {
        // TODO(body): port legacy BluetoothAudioRouter.connectHeadsetProfile.
        Log.d(TAG, "connectHeadsetProfile() — skeleton no-op")
    }

    /**
     * Запустить Bluetooth-маршрутизацию. Ждёт подключения (timeout + retry), при
     * неудаче возвращается на встроенный микрофон.
     *
     * TODO(body, AC-14): перенести из legacy `BluetoothAudioRouter.startBluetoothAudio()`:
     *   - API ≥ 31: `startModernApi()` — setCommunicationDevice + retry-loop
     *     (MAX_SCO_RETRIES=2, SCO_CONNECT_TIMEOUT_MS=3000, SCO_RETRY_DELAY_MS=500).
     *   - API < 31: `startLegacyApi()` — BroadcastReceiver + startBluetoothSco.
     *   - startHeadsetVoiceRecognition() в обеих ветках.
     *
     * @return true если BT-аудио успешно промаршрутизировано, false при неудаче.
     */
    suspend fun startBluetoothAudio(): Boolean {
        // TODO(body): port legacy BluetoothAudioRouter.startBluetoothAudio.
        Log.d(TAG, "startBluetoothAudio() — skeleton returns false (fallback to built-in mic)")
        _isConnected.value = false
        return false
    }

    /**
     * Остановить Bluetooth-маршрутизацию, вернуть default audio mode.
     * Безопасно вызывать многократно.
     *
     * TODO(body): legacy `stopBluetoothAudio()` (modern + legacy ветки).
     */
    fun stopBluetoothAudio() {
        // TODO(body): port legacy BluetoothAudioRouter.stopBluetoothAudio.
        _isConnected.value = false
        Log.d(TAG, "stopBluetoothAudio() — skeleton no-op")
    }

    /**
     * Полный cleanup — unregister receivers/listeners, close profile proxy.
     *
     * TODO(body): legacy `release()`.
     */
    fun release() {
        // TODO(body): port legacy BluetoothAudioRouter.release.
        stopBluetoothAudio()
        Log.d(TAG, "release() — skeleton no-op")
    }

    companion object {
        private const val TAG = "BluetoothAudioRouter"
    }
}
