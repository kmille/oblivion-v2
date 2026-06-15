package oblivion.v2.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import oblivion.v2.R
import oblivion.v2.core.auth.BiometricAuthState
import oblivion.v2.core.auth.BiometricAuthenticator

/**
 * "Porte" d'authentification biométrique qui masque [content] tant que
 * l'utilisateur n'est pas authentifié.
 *
 * Comportement :
 *  - Si `authState.isAuthenticated == true` → affiche directement [content].
 *  - Sinon → affiche un écran de verrouillage avec bouton
 *    "Déverrouiller" qui lance le BiometricPrompt.
 *  - Au démarrage, le prompt se déclenche automatiquement une fois.
 *
 * @param authState Le state holder partagé (injecté via Hilt dans MainActivity
 *                  et passé ici explicitement).
 * @param autoPrompt Si `true` (défaut), lance le prompt dès que l'écran
 *                   est affiché sans attendre un clic utilisateur.
 */
@Composable
fun BiometricGate(
    authState: BiometricAuthState,
    autoPrompt: Boolean = true,
    content: @Composable () -> Unit,
) {
    val isAuthenticated by authState.isAuthenticated.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) {
        var ctx: android.content.Context? = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return@remember ctx as FragmentActivity
            ctx = ctx.baseContext
        }
        null
    }

    if (isAuthenticated) {
        content()
        return
    }

    // Déclenche automatiquement le prompt au premier affichage non-authentifié.
    LaunchedEffect(Unit) {
        if (autoPrompt && activity != null) {
            launchPrompt(activity, authState)
        }
    }

    LockUi(
        onUnlockClick = {
            if (activity != null) launchPrompt(activity, authState)
        },
    )
}

private fun launchPrompt(
    activity: FragmentActivity,
    authState: BiometricAuthState,
) {
    val capability = BiometricAuthenticator.canAuthenticate(activity)
    if (capability == BiometricAuthenticator.Capability.UNAVAILABLE) {
        // Pas de biométrie ni PIN système disponible : on laisse passer
        // pour ne pas bloquer l'utilisateur (il n'y a rien à authentifier
        // avec). Dans ce cas l'app reste utilisable mais sans protection
        // biométrique — c'est au système d'avoir un PIN de verrouillage.
        authState.markAuthenticated()
        return
    }
    BiometricAuthenticator.authenticate(
        activity = activity,
        title = activity.getString(R.string.biometric_title),
        subtitle = activity.getString(R.string.biometric_subtitle),
        onSuccess = { authState.markAuthenticated() },
        onFailure = { _, _ -> /* l'utilisateur pourra retaper via le bouton */ },
    )
}

@Composable
private fun LockUi(onUnlockClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = stringResource(R.string.biometric_locked_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.biometric_locked_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Button(onClick = onUnlockClick) {
                Text(stringResource(R.string.biometric_unlock_button))
            }
        }
    }
}
