package pl.digitalbujo.app

import android.content.Context
import java.security.KeyStore

/** Upgrade cleanup only. Never reads or decrypts the removed provider credentials. */
object LegacyCredentials {
    fun remove(context: Context) {
        check(context.getSharedPreferences("api_keys", Context.MODE_PRIVATE).edit().clear().commit()) {
            "Could not remove obsolete credentials. Please restart the app."
        }
        check(context.deleteSharedPreferences("api_keys")) { "Could not remove obsolete credential storage." }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias("digital_journal_keys")) store.deleteEntry("digital_journal_keys")
    }
}
