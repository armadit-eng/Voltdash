package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AtMapsConfigSheet
import com.example.ui.components.CockpitBottomBar
import com.example.ui.components.MainTab
import com.example.ui.components.MirrorModalSheet
import com.example.ui.screens.AkunScreen
import com.example.ui.screens.DiagnosisScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ModeDashboardScreen
import com.example.ui.screens.NavigasiScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.AccentTheme
import com.example.ui.theme.GesitsDashTheme
import com.example.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { GesitsDashApp() }
  }
}

@Composable
fun GesitsDashApp(viewModel: DashboardViewModel = viewModel()) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val mirrorActive by viewModel.mirrorActive.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  var currentTabName by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
  val currentTab = MainTab.valueOf(currentTabName)

  var showModeDashboard by rememberSaveable { mutableStateOf(false) }
  var showMirrorModal by rememberSaveable { mutableStateOf(false) }
  var showAtMapsConfig by rememberSaveable { mutableStateOf(false) }
  var pendingSpkluOpen by remember { mutableStateOf(false) }
  var pendingConnectSlot by remember { mutableStateOf<String?>(null) }
  var pendingOpenGarage by remember { mutableStateOf(false) }

  val locationPermLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
      viewModel.onPermissionsUpdated()
    }

  val btPermLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
      viewModel.onPermissionsUpdated()
    }

  val screenCaptureLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.StartActivityForResult()
    ) { res ->
      val data = res.data
      if (res.resultCode == Activity.RESULT_OK && data != null) {
        viewModel.startScreenMirror(res.resultCode, data)
      } else {
        viewModel.showToast("Izin rekam layar dibatalkan.")
      }
    }

  val launchScreenCapture: () -> Unit = {
    val mpm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    screenCaptureLauncher.launch(mpm.createScreenCaptureIntent())
  }

  val notifPermLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.RequestPermission()
    ) { _ ->
      launchScreenCapture()
    }

  val requestScreenCapture: () -> Unit = {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      launchScreenCapture()
    }
  }

  val requestLocationPermissions: () -> Unit = {
    locationPermLauncher.launch(
      arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
      )
    )
  }

  val requestBluetoothPermissions: () -> Unit = {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      btPermLauncher.launch(
        arrayOf(
          Manifest.permission.BLUETOOTH_SCAN,
          Manifest.permission.BLUETOOTH_CONNECT,
        )
      )
    } else {
      btPermLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
        )
      )
    }
  }

  LaunchedEffect(toastMessage) {
    val msg = toastMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.clearToast()
    }
  }

  // Handle system back button when on non-Home tabs
  if (currentTab != MainTab.HOME && !showModeDashboard) {
    BackHandler { currentTabName = MainTab.HOME.name }
  }

  GesitsDashTheme(
    darkTheme = settings.darkTheme,
    accentTheme = AccentTheme.fromId(settings.accentThemeId),
  ) {
    if (showModeDashboard) {
      ModeDashboardScreen(
        viewModel = viewModel,
        onClose = { showModeDashboard = false },
      )
    } else {
      Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
          CockpitBottomBar(
            currentTab = currentTab,
            onSelectTab = { tab -> currentTabName = tab.name },
          )
        },
      ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          when (currentTab) {
            MainTab.HOME ->
              HomeScreen(
                viewModel = viewModel,
                onNavigateTab = { tab -> currentTabName = tab.name },
                onOpenModeDashboard = { showModeDashboard = true },
                onOpenSpkluSearch = {
                  pendingSpkluOpen = true
                  currentTabName = MainTab.NAVIGASI.name
                },
                onOpenConnectSheet = { slot ->
                  requestBluetoothPermissions()
                  pendingConnectSlot = slot
                  currentTabName = MainTab.AKUN.name
                },
                onOpenGarage = {
                  pendingOpenGarage = true
                  currentTabName = MainTab.AKUN.name
                },
                onOpenMirrorModal = { showMirrorModal = true },
                onRequestLocationPermission = requestLocationPermissions,
              )

            MainTab.DIAGNOSIS -> DiagnosisScreen(viewModel = viewModel)

            MainTab.NAVIGASI ->
              NavigasiScreen(
                viewModel = viewModel,
                openSpkluOnStart = pendingSpkluOpen,
                onSpkluSheetHandled = { pendingSpkluOpen = false },
                onOpenMirrorModal = { showMirrorModal = true },
                onOpenAtMapsConfig = { showAtMapsConfig = true },
                onRequestLocationPermission = requestLocationPermissions,
              )

            MainTab.AKUN ->
              AkunScreen(
                viewModel = viewModel,
                externalConnectSlot = pendingConnectSlot,
                externalOpenGarage = pendingOpenGarage,
                onExternalHandled = {
                  pendingConnectSlot = null
                  pendingOpenGarage = false
                },
                onOpenMirrorModal = { showMirrorModal = true },
                onOpenAtMapsConfig = { showAtMapsConfig = true },
                onRequestBluetoothPermissions = requestBluetoothPermissions,
              )

            MainTab.STATS -> StatsScreen(viewModel = viewModel)
          }
        }
      }
    }

    if (showMirrorModal) {
      MirrorModalSheet(
        viewModel = viewModel,
        mirrorActive = mirrorActive,
        onRequestScreenCapture = requestScreenCapture,
        onDismiss = { showMirrorModal = false },
      )
    }

    if (showAtMapsConfig) {
      AtMapsConfigSheet(
        viewModel = viewModel,
        onDismiss = { showAtMapsConfig = false },
      )
    }
  }
}
