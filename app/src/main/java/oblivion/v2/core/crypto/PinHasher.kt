package oblivion.v2.core.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Hashage des PINs / mots de passe de détresse.
 *
 * Threat model : l'attaquant a le téléphone déverrouillé et veut savoir
 * QUEL PIN déclenche le wipe pour l'éviter.  Pas de brute-force "offline"
 * sur un serveur parce qu'on n'est pas un serveur.  Un simple SHA-256 avec
 * un sel par-entrée suffit largement et évite les tables arc-en-ciel.
 *
 * On ne stocke JAMAIS le PIN en clair.  On compare un hash fraîchement
 * calculé au hash stocké.
 */
object PinHasher {

    private const val ALGO = "SHA-256"
    private const val SALT_BYTES = 16

    /** Génère un sel aléatoire encodé Base64 (à stocker à côté du hash). */
    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Hashe [pin] avec [salt] (Base64).  Renvoie un hash encodé Base64
     * NO_WRAP, sans saut de ligne.
     */
    fun hash(pin: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val md = MessageDigest.getInstance(ALGO)
        md.update(saltBytes)
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /**
     * Comparaison en temps constant pour éviter un timing attack local
     * (paranoïa mais trivial à faire).
     */
    fun verify(pin: String, expectedHash: String, salt: String): Boolean {
        val actual = hash(pin, salt)
        if (actual.length != expectedHash.length) return false
        var diff = 0
        for (i in actual.indices) {
            diff = diff or (actual[i].code xor expectedHash[i].code)
        }
        return diff == 0
    }
}
