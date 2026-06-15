// Fichier intentionnellement vidé — remplacé par DecoyNotifier +
// DecoyActivity (notification FullScreenIntent).
//
// Historique :
//   v1 — DecoyActivity lancée via startActivity() depuis le service
//        → bloquée par Background Activity Launch restriction (Android 10+).
//   v2 — DecoyOverlay via TYPE_APPLICATION_OVERLAY + SYSTEM_ALERT_WINDOW
//        → overlay ajouté au WindowManager mais rendu sous le window
//          keyguard (protection anti-phishing Android). Pas visible sur
//          le lockscreen.
//   v3 (actuelle) — Notification FullScreenIntent → le système Android
//        dismisse lui-même le keyguard et lance DecoyActivity plein
//        écran. Même mécanisme que les apps d'appel entrant (WhatsApp,
//        Signal, Phone, etc.). Permission USE_FULL_SCREEN_INTENT
//        (auto-grantée pré-Android 14, manuelle après).
package oblivion.v2.ui.decoy
