package com.alexpo.grammermate.shared.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Routes audio input through a connected Bluetooth headset/microphone using SCO
 * (Synchronous Connection-Oriented) audio or the modern CommunicationDevice API.
 *
 * Used to make Google SpeechRecognizer and AudioRecord (offline ASR) capture audio
 * from a Bluetooth microphone instead of the built-in device microphone.
 *
 * Two API paths:
 * - API < 31 (legacy): [AudioManager.startBluetoothSco] + [AudioManager.isBluetoothScoOn]
 * - API >= 31 (modern): [AudioManager.setCommunicationDevice] with [AudioDeviceInfo.TYPE_BLUETOOTH_SCO]
 */
class BluetoothAudioRouter(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "BluetoothAudioRouter"
        private const val SCO_CONNECT_TIMEOUT_MS = 3000L
        private const val SCO_RETRY_DELAY_MS = 500L
        private const val MAX_SCO_RETRIES = 2
    }

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val mutex = Mutex()
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    // Legacy SCO broadcast receiver (API < 31)
    private var scoReceiver: BroadcastReceiver? = null
    private var scoConnectedLatch: (() -> Unit)? = null

    // Modern API listener (API >= 31)
    private var communicationDeviceListener: AudioManager.OnCommunicationDeviceChangedListener? = null
    private var communicationDeviceLatch: (() -> Unit)? = null

    // BluetoothHeadset profile proxy for startVoiceRecognition
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var headsetProfileReady: (() -> Unit)? = null

    /**
     * Check if Bluetooth audio routing is available (adapter exists + SCO device connected).
     * Does NOT require BLUETOOTH_CONNECT runtime permission on API 31+ —
     * it only checks AudioManager device lists, not BluetoothAdapter.
     */
    fun isBluetoothAvailable(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Modern: check if a Bluetooth SCO device is in available communication devices
                audioManager.availableCommunicationDevices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            } else {
                // Legacy: check if isBluetoothScoAvailableOffCall
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoAvailableOffCall
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check Bluetooth availability", e)
            false
        }
    }

    /**
     * Connect to BluetoothHeadset profile proxy. Must be called before startVoiceRecognition.
     * Requires BLUETOOTH_CONNECT permission on API 31+.
     */
    fun connectHeadsetProfile() {
        try {
            val btAdapter = BluetoothAdapter.getDefaultAdapter() ?: run {
                Log.w(TAG, "No BluetoothAdapter available")
                return
            }
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HEADSET) {
                        bluetoothHeadset = proxy as BluetoothHeadset
                        Log.i(TAG, "BluetoothHeadset profile proxy connected")
                        headsetProfileReady?.invoke()
                    }
                }
                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HEADSET) {
                        bluetoothHeadset = null
                        Log.w(TAG, "BluetoothHeadset profile proxy disconnected")
                    }
                }
            }
            btAdapter.getProfileProxy(context, listener, BluetoothProfile.HEADSET)
        } catch (e: SecurityException) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted for headset profile", e)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to connect BluetoothHeadset profile", e)
        }
    }

    /**
     * Activate voice recognition mode on the connected Bluetooth headset.
     * This tells the headset to use its microphone for speech input.
     * Must be called BEFORE launching SpeechRecognizer.
     */
    fun startHeadsetVoiceRecognition(): Boolean {
        val headset = bluetoothHeadset
        if (headset == null) {
            Log.w(TAG, "BluetoothHeadset proxy not available, cannot startVoiceRecognition")
            return false
        }
        return try {
            // Try each connected device until one succeeds
            val devices = headset.connectedDevices
            var started = false
            for (device in devices) {
                if (headset.startVoiceRecognition(device)) {
                    Log.i(TAG, "startVoiceRecognition succeeded for device: ${device.name}")
                    started = true
                    break
                } else {
                    Log.w(TAG, "startVoiceRecognition failed for device: ${device.name}")
                }
            }
            if (!started && devices.isEmpty()) {
                Log.w(TAG, "No connected Bluetooth headset devices found")
            }
            started
        } catch (e: SecurityException) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error starting voice recognition on headset", e)
            false
        }
    }

    /**
     * Deactivate voice recognition mode on the Bluetooth headset.
     */
    fun stopHeadsetVoiceRecognition() {
        val headset = bluetoothHeadset
        if (headset == null) return
        try {
            for (device in headset.connectedDevices) {
                headset.stopVoiceRecognition(device)
                Log.d(TAG, "stopVoiceRecognition called for device: ${device.name}")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "BLUETOOTH_CONNECT permission not granted", e)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping voice recognition on headset", e)
        }
    }

    /**
     * Start Bluetooth audio routing. Waits up to [SCO_CONNECT_TIMEOUT_MS] for the connection
     * to be established, with retries on failure.
     *
     * @return true if Bluetooth audio was successfully routed, false on failure (falls back to built-in mic)
     */
    suspend fun startBluetoothAudio(): Boolean = mutex.withLock {
        if (_isConnected.value) return true

        // Ensure headset profile proxy is connected
        if (bluetoothHeadset == null) {
            connectHeadsetProfile()
            // Wait briefly for proxy connection
            withTimeoutOrNull(1500L) {
                while (bluetoothHeadset == null) {
                    delay(100)
                }
            }
        }

        try {
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startModernApi()
            } else {
                startLegacyApi()
            }

            _isConnected.value = success
            if (success) {
                Log.i(TAG, "Bluetooth audio routing active")
            } else {
                Log.w(TAG, "Bluetooth audio routing failed, using default microphone")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error starting Bluetooth audio", e)
            _isConnected.value = false
            false
        }
    }

    /**
     * Stop Bluetooth audio routing and restore default audio mode.
     * Safe to call multiple times.
     */
    fun stopBluetoothAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stopModernApi()
            } else {
                stopLegacyApi()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Bluetooth audio", e)
        } finally {
            _isConnected.value = false
        }
    }

    /**
     * Full cleanup — unregister receivers and listeners.
     */
    fun release() {
        // Close BluetoothHeadset profile proxy
        try {
            bluetoothHeadset?.let { hs ->
                BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(BluetoothProfile.HEADSET, hs)
            }
        } catch (_: Exception) {}
        bluetoothHeadset = null

        stopBluetoothAudio()
        try {
            scoReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        scoReceiver = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                communicationDeviceListener?.let {
                    audioManager.removeOnCommunicationDeviceChangedListener(it)
                }
            } catch (_: Exception) {}
            communicationDeviceListener = null
        }
    }

    // ── Modern API (API 31+) ─────────────────────────────────────────────

    private suspend fun startModernApi(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false

        // Set communication mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        // Legacy SCO — system-wide flag that forces Google SpeechRecognizer to use BT mic
        @Suppress("DEPRECATION")
        audioManager.startBluetoothSco()
        @Suppress("DEPRECATION")
        audioManager.isBluetoothScoOn = true
        Log.d(TAG, "Legacy SCO activated alongside CommunicationDevice for system-wide routing")

        // Activate voice recognition on the BT headset — this tells the headset mic to be used
        val vrStarted = startHeadsetVoiceRecognition()
        if (vrStarted) {
            Log.i(TAG, "Headset voice recognition mode activated")
        } else {
            Log.w(TAG, "Headset voice recognition mode NOT activated (may use phone mic)")
        }

        // Find Bluetooth SCO or BLE Headset device
        val btDevice = audioManager.availableCommunicationDevices.find {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }

        if (btDevice == null) {
            Log.w(TAG, "No Bluetooth SCO/BLE device found in available communication devices")
            audioManager.mode = AudioManager.MODE_NORMAL
            return false
        }

        // Set up listener for confirmation
        var connected = false
        communicationDeviceListener = AudioManager.OnCommunicationDeviceChangedListener { device ->
            if (device?.id == btDevice.id) {
                connected = true
                communicationDeviceLatch?.invoke()
            }
        }
        audioManager.addOnCommunicationDeviceChangedListener(
            { runnable -> scope.launch { runnable.run() } },
            communicationDeviceListener!!
        )

        // Retry loop
        repeat(MAX_SCO_RETRIES) { attempt ->
            val result = audioManager.setCommunicationDevice(btDevice)
            if (!result) {
                Log.w(TAG, "setCommunicationDevice failed (attempt ${attempt + 1})")
                if (attempt < MAX_SCO_RETRIES - 1) delay(SCO_RETRY_DELAY_MS)
                return@repeat
            }

            // Wait for confirmation with timeout
            val waitResult = withTimeoutOrNull(SCO_CONNECT_TIMEOUT_MS / MAX_SCO_RETRIES) {
                while (!connected) {
                    delay(100)
                }
                true
            }

            if (waitResult == true) return true

            Log.w(TAG, "CommunicationDevice connection timeout (attempt ${attempt + 1})")
            if (attempt < MAX_SCO_RETRIES - 1) delay(SCO_RETRY_DELAY_MS)
        }

        // Fallback: check if it's already set (some devices don't fire the callback)
        val currentDevice = audioManager.communicationDevice
        if (currentDevice?.id == btDevice.id) {
            Log.i(TAG, "CommunicationDevice confirmed via polling")
            return true
        }

        audioManager.mode = AudioManager.MODE_NORMAL
        return false
    }

    private fun stopModernApi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopHeadsetVoiceRecognition()
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn = false
            @Suppress("DEPRECATION")
            audioManager.stopBluetoothSco()
            audioManager.clearCommunicationDevice()
            audioManager.mode = AudioManager.MODE_NORMAL
            communicationDeviceListener?.let {
                audioManager.removeOnCommunicationDeviceChangedListener(it)
            }
            communicationDeviceListener = null
        }
    }

    // ── Legacy API (API < 31) ────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private suspend fun startLegacyApi(): Boolean {
        // Register broadcast receiver for SCO state changes
        var scoConnected = false
        scoReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val state = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED
                )
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    scoConnected = true
                    scoConnectedLatch?.invoke()
                }
            }
        }
        context.registerReceiver(
            scoReceiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        )

        // Set communication mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        // Retry loop — startBluetoothSco() often fails on first call
        repeat(MAX_SCO_RETRIES) { attempt ->
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            startHeadsetVoiceRecognition()

            // Wait for SCO connection with timeout
            val waitResult = withTimeoutOrNull(SCO_CONNECT_TIMEOUT_MS / MAX_SCO_RETRIES) {
                while (!scoConnected) {
                    delay(100)
                }
                true
            }

            if (waitResult == true) return true

            Log.w(TAG, "SCO connection timeout (attempt ${attempt + 1})")
            audioManager.stopBluetoothSco()
            if (attempt < MAX_SCO_RETRIES - 1) delay(SCO_RETRY_DELAY_MS)
        }

        // Cleanup on failure
        try {
            context.unregisterReceiver(scoReceiver!!)
        } catch (_: Exception) {}
        scoReceiver = null
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        return false
    }

    @Suppress("DEPRECATION")
    private fun stopLegacyApi() {
        stopHeadsetVoiceRecognition()
        try {
            scoReceiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        scoReceiver = null

        audioManager.isBluetoothScoOn = false
        audioManager.stopBluetoothSco()
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
