package oblivion.v2.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import oblivion.v2.feature.dashboard.DashboardScreen
import oblivion.v2.feature.deadman.DeadmanScreen
import oblivion.v2.feature.guard.GuardScreen
import oblivion.v2.feature.scheduled.ScheduledWipeScreen
import oblivion.v2.feature.wipetest.WipeTestScreen

/**
 * Destinations de navigation de l'app.
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val WIPE_TEST = "wipeTest"
    const val GUARD = "guard"
    const val DEADMAN = "deadman"
    const val SCHEDULED = "scheduled"
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenGuard = { navController.navigate(Routes.GUARD) },
                onOpenDeadman = { navController.navigate(Routes.DEADMAN) },
                onOpenScheduled = { navController.navigate(Routes.SCHEDULED) },
                onOpenWipeTest = { navController.navigate(Routes.WIPE_TEST) },
            )
        }
        composable(Routes.WIPE_TEST) {
            WipeTestScreen(
                onOpenGuard = { navController.navigate(Routes.GUARD) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.GUARD) {
            GuardScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DEADMAN) {
            DeadmanScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SCHEDULED) {
            ScheduledWipeScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
