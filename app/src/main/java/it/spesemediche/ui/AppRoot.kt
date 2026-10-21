package it.spesemediche.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import it.spesemediche.ui.screens.DetailScreen
import it.spesemediche.ui.screens.EditScreen
import it.spesemediche.ui.screens.ListScreen
import it.spesemediche.ui.screens.ReportScreen

@Composable
fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "list") {
            composable("list") {
                ListScreen(
                    vm = vm,
                    onOpen = { nav.navigate("detail/$it") },
                    onNewReady = { nav.navigate("edit") },
                    onReport = { nav.navigate("report") },
                )
            }
            composable("edit") {
                EditScreen(
                    vm = vm,
                    onSaved = { id ->
                        // Dopo il salvataggio di una nuova spesa si apre il dettaglio
                        val wasEditingDetail = nav.previousBackStackEntry?.destination?.route == "detail/{id}"
                        nav.popBackStack()
                        if (!wasEditingDetail) nav.navigate("detail/$id")
                    },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                "detail/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                DetailScreen(
                    vm = vm,
                    id = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { vm.editExisting(id) { nav.navigate("edit") } },
                )
            }
            composable("report") {
                ReportScreen(vm = vm, onBack = { nav.popBackStack() })
            }
        }

        busy?.let { message ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) {
                Card {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(message)
                    }
                }
            }
        }
    }
}
