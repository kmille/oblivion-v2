# ══════════════════════════════════════════════════════════════════
# Oblivion V2 — ProGuard / R8 rules
# ══════════════════════════════════════════════════════════════════

# ── Device Admin ────────────────────────────────────────────────
# Le system bind le receiver par class name — il ne doit pas être renommé.
-keep class oblivion.v2.core.admin.DeviceAdminReceiver { *; }

# ── Services & Receivers (manifest-bound) ───────────────────────
-keep class oblivion.v2.core.guard.GuardAccessibilityService { *; }
-keep class oblivion.v2.core.usb.UsbKillService { *; }
-keep class oblivion.v2.core.usb.UsbKillBootReceiver { *; }
-keep class oblivion.v2.core.voice.VoiceKillService { *; }
-keep class oblivion.v2.core.voice.VoiceKillBootReceiver { *; }
-keep class oblivion.v2.core.sms.SmsKillReceiver { *; }

# ── Vosk + JNA ──────────────────────────────────────────────────
# Vosk utilise JNA pour accéder aux libs natives — tout doit être préservé.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn com.sun.jna.**

# ── Hilt ────────────────────────────────────────────────────────
# Hilt génère du code qui dépend de la réflection.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Kotlin Serialization / Metadata ─────────────────────────────
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# ── EncryptedSharedPreferences (Tink) ───────────────────────────
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── Compose ─────────────────────────────────────────────────────
# Supprime les logs Compose en release.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ── Misc ────────────────────────────────────────────────────────
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
