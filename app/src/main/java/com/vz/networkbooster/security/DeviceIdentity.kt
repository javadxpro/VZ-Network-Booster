package com.vz.networkbooster.security

import android.content.Context
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair

/**
 * Per-device WireGuard identity. The private key is generated on the device,
 * stored only in Keystore-encrypted form, and only its public half is ever
 * displayed or transmitted (to register the peer on your relay).
 */
class DeviceIdentity(context: Context) {

    private val box = SecretBox(context)
    private val memoryKey: String by lazy { ensureKey() }

    fun privateKeyBase64(): String = memoryKey

    fun publicKeyBase64(): String = KeyPair(Key.fromBase64(privateKeyBase64())).publicKey.toBase64()

    /** Rotating the key invalidates every peer registration — the UI confirms first. */
    fun resetIdentity(): String {
        box.remove(PREF_PRIVATE_KEY)
        val fresh = KeyPair().privateKey.toBase64()
        box.put(PREF_PRIVATE_KEY, fresh)
        return fresh
    }

    private fun ensureKey(): String {
        box.get(PREF_PRIVATE_KEY)?.let { return it }
        val generated = KeyPair().privateKey.toBase64()
        box.put(PREF_PRIVATE_KEY, generated)
        return generated
    }

    private companion object {
        const val PREF_PRIVATE_KEY = "device_wg_private_key"
    }
}
