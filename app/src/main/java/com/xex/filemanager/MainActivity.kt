@file:OptIn(ExperimentalMaterial3Api::class)

package com.xex.filemanager

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xex.filemanager.fs.ShizukuManager
import com.xex.filemanager.ui.AnalyzerScreen
import com.xex.filemanager.ui.AppsScreen
import com.xex.filemanager.ui.LocalXfm
import com.xex.filemanager.ui.BrowserScreen
import com.xex.filemanager.ui.DialogHost
import com.xex.filemanager.ui.EditorScreen
import com.xex.filemanager.ui.HomeScreen
import com.xex.filemanager.ui.ImageViewerScreen
import com.xex.filemanager.ui.ProgressHost
import com.xex.filemanager.ui.SettingsScreen
import com.xex.filemanager.ui.TerminalScreen
import com.xex.filemanager.ui.TrashScreen
import com.xex.filemanager.ui.XfmTheme
import com.xex.filemanager.util.Utils
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    XfmTheme(vm.settings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val ctx = LocalContext.current
            var hasAccess by remember { mutableStateOf(Utils.hasAllFilesAccess(ctx)) }
            val owner = LocalLifecycleOwner.current

            DisposableEffect(owner) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_RESUME) {
                        hasAccess = Utils.hasAllFilesAccess(ctx)
                        ShizukuManager.refresh()
                        vm.reload()
                    }
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }

            if (!hasAccess) {
                PermissionScreen { hasAccess = Utils.hasAllFilesAccess(ctx) }
            } else {
                MainContent(vm)
            }
        }
    }
}

@Composable
private fun PermissionScreen(onResult: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onResult()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Нужен доступ к файлам", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Чтобы управлять файлами на устройстве, разрешите приложению доступ ко всем файлам.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Utils.openAllFilesSettings(ctx)
            } else {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }) { Text("Дать доступ") }
    }
}

@Composable
private fun MainContent(vm: AppViewModel) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = vm.canHandleBack && !drawerState.isOpen) { vm.onBack() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = vm.top is Screen.Home || vm.top is Screen.Browser || vm.top is Screen.Category,
        drawerContent = { DrawerContent(vm) { scope.launch { drawerState.close() } } }
    ) {
        Box(Modifier.fillMaxSize()) {
            when (val t = vm.top) {
                is Screen.Home -> HomeScreen(vm) { scope.launch { drawerState.open() } }
                is Screen.Browser -> BrowserScreen(vm) { scope.launch { drawerState.open() } }
                is Screen.Category -> BrowserScreen(vm) { scope.launch { drawerState.open() } }
                is Screen.Apps -> AppsScreen(vm)
                is Screen.Trash -> TrashScreen(vm)
                is Screen.Analyzer -> AnalyzerScreen(vm)
                is Screen.Prefs -> SettingsScreen(vm)
                is Screen.Terminal -> TerminalScreen(vm)
                is Screen.Images -> ImageViewerScreen(vm, t)
                is Screen.Editor -> EditorScreen(vm, t.path)
            }
            DialogHost(vm)
            ProgressHost(vm)
        }
    }
}

@Composable
private fun DrawerContent(vm: AppViewModel, close: () -> Unit) {
    val root = Utils.primaryRoot()
    val itemPad = NavigationDrawerItemDefaults.ItemPadding

    @Composable
    fun Item(label: String, icon: Int, onClick: () -> Unit) {
        NavigationDrawerItem(
            label = { Text(label) },
            selected = false,
            onClick = { close(); onClick() },
            icon = { Icon(painterResource(icon), null) },
            modifier = Modifier.padding(itemPad)
        )
    }

    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalXfm.current.toolbar)
                    .statusBarsPadding()
                    .padding(20.dp)
            ) {
                Column {
                    Icon(
                        painterResource(R.drawable.ic_folder),
                        null,
                        Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "XFM Файлы",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        "Файловый менеджер + Shizuku",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBDBDBD)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Item("Главная", R.drawable.ic_folder_open) { vm.goHome() }
            vm.volumes.forEach { v ->
                Item(v.label, if (v.removable) R.drawable.ic_sd else R.drawable.ic_phone) {
                    vm.openFromRoot(Screen.Browser(v.path))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
            Item("Загрузки", R.drawable.ic_download) { vm.openFromRoot(Screen.Browser("$root/Download")) }
            Item("Камера (DCIM)", R.drawable.ic_image) { vm.openFromRoot(Screen.Browser("$root/DCIM")) }
            Item("Изображения", R.drawable.ic_image) { vm.openFromRoot(Screen.Browser("$root/Pictures")) }
            Item("Документы", R.drawable.ic_description) { vm.openFromRoot(Screen.Browser("$root/Documents")) }
            Item("Музыка", R.drawable.ic_audio) { vm.openFromRoot(Screen.Browser("$root/Music")) }
            Item("Видео", R.drawable.ic_movie) { vm.openFromRoot(Screen.Browser("$root/Movies")) }
            val bms = vm.settings.bookmarks
            if (bms.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
                bms.forEach { p ->
                    Item(com.xex.filemanager.fs.PathUtil.name(p).ifEmpty { p }, R.drawable.ic_bookmark) {
                        vm.openFromRoot(Screen.Browser(p))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp))
            Item("Программы", R.drawable.ic_android) { vm.openFromRoot(Screen.Apps) }
            Item("Архивы", R.drawable.ic_archive) { vm.openFromRoot(Screen.Category(com.xex.filemanager.model.Cat.ARCHIVES)) }
            Item("APK-файлы", R.drawable.ic_android) { vm.openFromRoot(Screen.Category(com.xex.filemanager.model.Cat.APK)) }
            Item("Корзина", R.drawable.ic_delete_sweep) { vm.openFromRoot(Screen.Trash) }
            Item("Анализ памяти", R.drawable.ic_pie) { vm.openFromRoot(Screen.Analyzer) }
            Item("Терминал (Shizuku)", R.drawable.ic_terminal) { vm.openFromRoot(Screen.Terminal) }
            Item("Настройки и Shizuku", R.drawable.ic_palette) { vm.openFromRoot(Screen.Prefs) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
