package oblivion.v2.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import oblivion.v2.core.deadman.DeadmanConfigStore
import oblivion.v2.core.guard.GuardConfigStore
import oblivion.v2.core.prefs.SecurePrefs
import oblivion.v2.core.scheduled.ScheduledWipeStore
import oblivion.v2.core.wipe.WipeGateway
import javax.inject.Singleton

/**
 * Module Hilt pour les singletons "core" (infra partagée).
 *
 * Scope : SingletonComponent → une seule instance pour toute la durée de
 * vie du process.  Parfait pour des stores et des facades.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideSecurePrefs(@ApplicationContext context: Context): SecurePrefs =
        SecurePrefs.create(context)

    /**
     * Device-protected instance, readable from Direct Boot (before the
     * first unlock). Reserved for stores whose trigger must be able to
     * fire without a prior unlock: Guard (duress PIN), Scheduled Wipe
     * (the "alarm clock"), Dead Man's Switch. See [GuardDeviceProtected]
     * for the full reasoning.
     */
    @Provides
    @Singleton
    @GuardDeviceProtected
    fun provideGuardSecurePrefs(@ApplicationContext context: Context): SecurePrefs =
        SecurePrefs.createDeviceProtected(context)

    @Provides
    @Singleton
    fun provideGuardConfigStore(@GuardDeviceProtected securePrefs: SecurePrefs): GuardConfigStore =
        GuardConfigStore(securePrefs)

    @Provides
    @Singleton
    fun provideWipeGateway(@ApplicationContext context: Context): WipeGateway =
        WipeGateway(context)

    @Provides
    @Singleton
    fun provideDeadmanConfigStore(@GuardDeviceProtected securePrefs: SecurePrefs): DeadmanConfigStore =
        DeadmanConfigStore(securePrefs)

    @Provides
    @Singleton
    fun provideScheduledWipeStore(@GuardDeviceProtected securePrefs: SecurePrefs): ScheduledWipeStore =
        ScheduledWipeStore(securePrefs)
}
