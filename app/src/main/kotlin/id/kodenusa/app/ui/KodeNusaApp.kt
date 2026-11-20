package id.kodenusa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import id.kodenusa.app.AppContainer
import java.io.File

/** Navigasi sengaja dibuat sebagai state biasa: tiga layar tidak butuh library navigasi. */
sealed interface Screen {
    data object Workspace : Screen
    data object Wizard : Screen
    data class Editor(val projectDir: File) : Screen
    data class Build(val projectDir: File) : Screen
    data class Situs(val projectDir: File) : Screen
}

@Composable
fun KodeNusaApp(container: AppContainer) {
    var screen by remember { mutableStateOf<Screen>(Screen.Workspace) }

    BackHandler(enabled = screen !is Screen.Workspace) {
        screen = when (val current = screen) {
            is Screen.Editor -> Screen.Workspace
            is Screen.Build -> Screen.Editor(current.projectDir)
            is Screen.Situs -> Screen.Editor(current.projectDir)
            else -> Screen.Workspace
        }
    }

    when (val current = screen) {
        is Screen.Workspace -> WorkspaceScreen(
            container = container,
            onOpenProject = { screen = Screen.Editor(it) },
            onNewProject = { screen = Screen.Wizard },
        )

        is Screen.Wizard -> WizardScreen(
            container = container,
            onCancel = { screen = Screen.Workspace },
            onCreated = { screen = Screen.Editor(it) },
        )

        is Screen.Editor -> EditorScreen(
            container = container,
            projectDir = current.projectDir,
            onBack = { screen = Screen.Workspace },
            onBuild = { screen = Screen.Build(current.projectDir) },
        )

        is Screen.Build -> BuildScreen(
            container = container,
            projectDir = current.projectDir,
            onBack = { screen = Screen.Editor(current.projectDir) },
            onSitus = { screen = Screen.Situs(current.projectDir) },
        )

        is Screen.Situs -> SitusScreen(
            projectDir = current.projectDir,
            onBack = { screen = Screen.Editor(current.projectDir) },
        )
    }
}
