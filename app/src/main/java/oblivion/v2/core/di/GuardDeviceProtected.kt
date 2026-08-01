package oblivion.v2.core.di

import javax.inject.Qualifier

/**
 * Qualifies the [oblivion.v2.core.prefs.SecurePrefs] instance backed by
 * **device-protected storage**, readable during Direct Boot (before the
 * first unlock). Used by stores whose trigger must be able to fire
 * without a prior unlock: [oblivion.v2.core.guard.GuardConfigStore]
 * (duress PIN), Scheduled Wipe, Dead Man's Switch. Everything else stays
 * on the default instance (credential-encrypted, unqualified).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GuardDeviceProtected
