package id.kodenusa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import id.kodenusa.app.AppContainer
import id.kodenusa.ui.KodenusaTokens
import id.kodenusa.ui.KodenusaTheme
import java.io.File

/** Navigasi sengaja dibuat sebagai state biasa: layar tidak butuh library navigasi. */
sealed interface Screen {
    data object Workspace : Screen
    data object Wizard : Screen
    data object Tema : Screen
    data class Editor(val projectDir: File) : Screen
    data class Build(val projectDir: File) : Screen
    data class Situs(val projectDir: File) : Screen
    data class Git(val projectDir: File) : Screen
    data class Agen(val projectDir: File) : Screen
}

/**
 * [tokensAwal] dibaca sebelum `setContent` (anti-kilat); [tokens] menyusul setiap kali pengguna
 * memilih tema di layar Tema.
 */
@Composable
fun KodeNusaApp(container: AppContainer, tokensAwal: KodenusaTokens) {
    var screen by remember { mutableStateOf<Screen>(Screen.Workspace) }
    var tokens by remember { mutableStateOf(tokensAwal) }

    BackHandler(enabled = screen !is Screen.Workspace) {
        screen = when (val current = screen) {
            is Screen.Editor -> Screen.Workspace
            is Screen.Build -> Screen.Editor(current.projectDir)
            is Screen.Situs -> Screen.Editor(current.projectDir)
            is Screen.Git -> Screen.Editor(current.projectDir)
            is Screen.Agen -> Screen.Editor(current.projectDir)
            else -> Screen.Workspace
        }
    }

    KodenusaTheme(tokens) {
        // Ikon status bar ikut tema saat pengguna berganti tema tanpa membuat ulang aktivitas.
        val aktivitas = LocalContext.current as? android.app.Activity
        SideEffect {
            val jendela = aktivitas?.window ?: return@SideEffect
            val pengatur = WindowCompat.getInsetsController(jendela, jendela.decorView)
            pengatur.isAppearanceLightStatusBars = !tokens.gelap
            pengatur.isAppearanceLightNavigationBars = !tokens.gelap
        }
        // Transisi antar layar 200 ms fade — cukup halus untuk HP 2 GB, tanpa animasi berat.
        Crossfade(targetState = screen, animationSpec = tween(200), label = "layar") { layar ->
            when (val current = layar) {
            is Screen.Workspace -> WorkspaceScreen(
                container = container,
                onOpenProject = { screen = Screen.Editor(it) },
                onNewProject = { screen = Screen.Wizard },
                onBukaTema = { screen = Screen.Tema },
            )

            is Screen.Wizard -> WizardScreen(
                container = container,
                onCancel = { screen = Screen.Workspace },
                onCreated = { screen = Screen.Editor(it) },
            )

            is Screen.Tema -> TemaScreen(
                temaStore = container.temaStore,
                onDipilih = { tokens = container.temaStore.tokens() },
                onBack = { screen = Screen.Workspace },
            )

            is Screen.Editor -> EditorScreen(
                container = container,
                projectDir = current.projectDir,
                onBack = { screen = Screen.Workspace },
                onBuild = { screen = Screen.Build(current.projectDir) },
                onSitus = { screen = Screen.Situs(current.projectDir) },
                onGantiTema = { modeBaru ->
                    container.temaStore.mode = modeBaru
                    tokens = container.temaStore.tokens()
                },
            )

            is Screen.Build -> BuildScreen(
                container = container,
                projectDir = current.projectDir,
                onBack = { screen = Screen.Editor(current.projectDir) },
                onSitus = { screen = Screen.Situs(current.projectDir) },
                onGit = { screen = Screen.Git(current.projectDir) },
                onAgen = { screen = Screen.Agen(current.projectDir) },
            )

            is Screen.Situs -> SitusScreen(
                projectDir = current.projectDir,
                onBack = { screen = Screen.Editor(current.projectDir) },
            )

            is Screen.Git -> GitScreen(
                projectDir = current.projectDir,
                onBack = { screen = Screen.Editor(current.projectDir) },
            )

            is Screen.Agen -> AgenScreen(
                container = container,
                projectDir = current.projectDir,
                onBack = { screen = Screen.Editor(current.projectDir) },
            )
            }
        }
    }
}
