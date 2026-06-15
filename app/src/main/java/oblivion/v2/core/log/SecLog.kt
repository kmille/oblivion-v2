package oblivion.v2.core.log

import android.util.Log
import oblivion.v2.BuildConfig

/**
 * Logger sécurisé pour Oblivion.
 *
 * Tous les logs de niveau DEBUG/INFO/VERBOSE ne sont émis qu'en build debug.
 * En release, ces appels sont des no-op → rien n'est écrit dans logcat.
 *
 * Les logs d'ERREUR sont conservés en release (utile pour diagnostiquer
 * les crashes via Play Console vitals ou les rapports utilisateur), mais
 * attention à ne jamais passer de données sensibles (keyword, phrase,
 * numéro) dans les messages d'erreur.
 *
 * Usage : remplacer `Log.d(TAG, "...")` par `SecLog.d(TAG, "...")`.
 */
object SecLog {

    inline fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    inline fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg)
    }

    inline fun v(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.v(tag, msg)
    }

    inline fun w(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg)
    }

    inline fun w(tag: String, msg: String, t: Throwable) {
        if (BuildConfig.DEBUG) Log.w(tag, msg, t)
    }

    // Les erreurs sont toujours loguées (mais ne passez PAS de données sensibles).
    inline fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    inline fun e(tag: String, msg: String, t: Throwable) {
        Log.e(tag, msg, t)
    }
}
