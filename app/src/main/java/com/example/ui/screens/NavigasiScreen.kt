package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.NavDestination
import com.example.ui.components.CockpitCard
import com.example.ui.components.ElevationProfileCanvas
import com.example.ui.components.HoldToConfirmButton
import com.example.ui.components.OsmMapCanvas
import com.example.ui.theme.LocalCockpitColors
import com.example.viewmodel.DashboardViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigasiScreen(
  viewModel: DashboardViewModel,
  openSpkluOnStart: Boolean = false,
  onSpkluSheetHandled: () -> Unit = {},
  onOpenMirrorModal: () -> Unit = {},
  onOpenAtMapsConfig: () -> Unit = {},
  onRequestLocationPermission: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val gps by viewModel.gpsState.collectAsStateWithLifecycle()
  val eff by viewModel.effective.collectAsStateWithLifecycle()
  val nav by viewModel.navState.collectAsStateWithLifecycle()
  val favorites by viewModel.favorites.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val mirrorActive by viewModel.mirrorActive.collectAsStateWithLifecycle()

  // Default map center: Jakarta (Monas) until GPS locks
  var mapLat by remember { mutableDoubleStateOf(gps.location?.lat ?: -6.1754) }
  var mapLng by remember { mutableDoubleStateOf(gps.location?.lng ?: 106.8272) }
  var mapZoom by remember { mutableIntStateOf(15) }
  var autoFollow by remember { mutableStateOf(true) }

  var showSpkluSheet by remember { mutableStateOf(false) }
  var showFavSheet by remember { mutableStateOf(false) }
  var showPlannerSheet by remember { mutableStateOf(false) }

  LaunchedEffect(gps.location) {
    val loc = gps.location
    if (loc != null && autoFollow) {
      mapLat = loc.lat
      mapLng = loc.lng
    }
  }

  LaunchedEffect(openSpkluOnStart) {
    if (openSpkluOnStart) {
      showSpkluSheet = true
      viewModel.searchNearbySpklu()
      onSpkluSheetHandled()
    }
  }

  if (nav.rideLockActive) {
    BackHandler { /* Locked deliberately during ride; hold button to unlock */ }
  }

  Box(modifier = modifier.fillMaxSize()) {
    // 1. Real Interactive OSM Map
    OsmMapCanvas(
      tileLoader = viewModel.tileLoader,
      centerLat = mapLat,
      centerLng = mapLng,
      zoom = mapZoom,
      riderLocation = gps.location,
      riderHeadingDeg = gps.headingDeg,
      routes = nav.routes,
      selectedRouteIndex = nav.selectedRouteIndex,
      destination = nav.destination,
      spkluStations = nav.spkluStations,
      favorites = favorites,
      onPan = { dLat, dLng ->
        autoFollow = false
        mapLat = (mapLat + dLat).coerceIn(-85.0, 85.0)
        mapLng = (mapLng + dLng).coerceIn(-180.0, 180.0)
      },
      onZoomChange = { delta -> mapZoom = (mapZoom + delta).coerceIn(4, 18) },
      onMapTap = { pt ->
        viewModel.selectDestination(
          NavDestination("Pin Peta (${String.format(Locale.US, "%.4f, %.4f", pt.lat, pt.lng)})", pt.lat, pt.lng)
        )
      },
      modifier = Modifier.fillMaxSize(),
    )

    // 2. Top Search OR Active Turn-by-Turn Navigation Instruction Bar
    Column(
      modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (nav.isNavigating && nav.activeRoute != null) {
        val route = nav.activeRoute!!
        val step = route.steps.getOrNull(nav.activeStepIndex)
        Surface(
          modifier = Modifier.fillMaxWidth().testTag("turn_by_turn_banner"),
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
          border = BorderStroke(1.5.dp, cockpit.accent),
          tonalElevation = 6.dp,
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            val turnIcon =
              when {
                step?.maneuverModifier?.contains("left") == true -> Icons.Filled.TurnLeft
                step?.maneuverModifier?.contains("right") == true -> Icons.Filled.TurnRight
                step?.maneuverModifier?.contains("uturn") == true -> Icons.Filled.UTurnLeft
                else -> Icons.Filled.Straight
              }
            Box(
              modifier =
                Modifier.size(48.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(cockpit.accent.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(turnIcon, contentDescription = "Arah Belok", tint = cockpit.accent, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
              val distStr =
                if (nav.distToNextStepM >= 1000.0) {
                  String.format(Locale.US, "%.1f km", nav.distToNextStepM / 1000.0)
                } else {
                  "${nav.distToNextStepM.roundToInt()} m"
                }
              Text(distStr, style = MaterialTheme.typography.headlineLarge, color = cockpit.accent, fontWeight = FontWeight.Bold)
              Text(
                step?.instruction ?: "Ikuti rute menuju ${nav.destination?.name ?: "tujuan"}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            }
            IconButton(
              onClick = { viewModel.toggleVoiceGuidance() },
              modifier = Modifier.size(48.dp).testTag("nav_voice_toggle"),
            ) {
              Icon(
                if (nav.voiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = "Suara Panduan",
                tint = if (nav.voiceEnabled) cockpit.accent else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(
              onClick = { viewModel.stopNavigation() },
              modifier = Modifier.size(48.dp).testTag("nav_stop_btn"),
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Hentikan Navigasi", tint = cockpit.danger)
            }
          }
        }
      } else {
        // Destination Search Box
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
          tonalElevation = 6.dp,
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              OutlinedTextField(
                value = nav.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Cari tujuan, alamat, atau koordinat…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchDestination(nav.searchQuery) }),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = cockpit.accent,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
                modifier = Modifier.weight(1f).testTag("nav_search_input"),
              )
              Button(
                onClick = { viewModel.searchDestination(nav.searchQuery) },
                modifier = Modifier.heightIn(min = 52.dp).testTag("nav_search_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
              ) {
                if (nav.isSearching) {
                  CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                  Icon(Icons.Filled.Search, contentDescription = "Cari Lokasi")
                }
              }
            }

            // Quick chips: SPKLU, Favorit, Multi-Stop, GPS Permission
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = showSpkluSheet,
                onClick = {
                  showSpkluSheet = true
                  viewModel.searchNearbySpklu()
                },
                label = { Text("SPKLU Terdekat") },
                leadingIcon = { Icon(Icons.Filled.EvStation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("nav_spklu_chip"),
              )
              FilterChip(
                selected = showFavSheet,
                onClick = { showFavSheet = true },
                label = { Text("Favorit (${favorites.size})") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("nav_fav_chip"),
              )
              FilterChip(
                selected = showPlannerSheet,
                onClick = { showPlannerSheet = true },
                label = { Text("Multi-Stop (${nav.multiStops.size})") },
                leadingIcon = { Icon(Icons.Filled.Route, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("nav_multistop_chip"),
              )
              FilterChip(
                selected = mirrorActive,
                onClick = onOpenMirrorModal,
                label = { Text(if (mirrorActive) "Mirror Aktif" else "Mirror Layar") },
                leadingIcon = { Icon(Icons.Filled.Cast, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("nav_mirror_chip"),
              )
              FilterChip(
                selected = settings.atMapsEnabled,
                onClick = onOpenAtMapsConfig,
                label = { Text("AT+MAPS") },
                leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("nav_atmaps_chip"),
              )
              if (!gps.hasPermission) {
                FilterChip(
                  selected = false,
                  onClick = onRequestLocationPermission,
                  label = { Text("Aktifkan GPS") },
                  leadingIcon = { Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
              }
            }
          }
        }

        // Search Results Dropdown
        if (nav.searchResults.isNotEmpty()) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(8.dp)) {
              nav.searchResults.take(5).forEach { place ->
                Row(
                  modifier =
                    Modifier.fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .clickable {
                        autoFollow = false
                        mapLat = place.lat
                        mapLng = place.lng
                        viewModel.selectDestination(NavDestination(place.shortName, place.lat, place.lng))
                      }
                      .padding(horizontal = 8.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(place.shortName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                      place.displayName,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
          }
        }

        nav.routeError?.let { err ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = cockpit.danger.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, cockpit.danger),
          ) {
            Text(err, style = MaterialTheme.typography.bodySmall, color = cockpit.danger, modifier = Modifier.padding(10.dp))
          }
        }
      }
    }

    // 3. Right Floating Map Controls & Left Speedometer HUD
    Row(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(start = 12.dp, end = 12.dp, bottom = if (nav.activeRoute != null) 210.dp else 14.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Bottom,
    ) {
      // Speedometer + Battery Mini HUD
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
        border = BorderStroke(1.2.dp, cockpit.accent.copy(alpha = 0.55f)),
        tonalElevation = 6.dp,
      ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
          Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = eff.speedKmh?.let { "${it.roundToInt()}" } ?: "—",
              style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
            )
            Text("km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 5.dp))
          }
          Text(
            text = "SOC ${eff.combinedSocPct?.roundToInt() ?: "—"}% • ~${String.format(Locale.US, "%.1f", eff.rangeKm)} km",
            style = MaterialTheme.typography.labelSmall,
            color = cockpit.accent,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      // Map Zoom + Recenter + Ride Lock buttons
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MapFabButton(Icons.Filled.Add, "Zoom In", Modifier.testTag("map_zoom_in")) {
          mapZoom = (mapZoom + 1).coerceIn(4, 18)
        }
        MapFabButton(Icons.Filled.Remove, "Zoom Out", Modifier.testTag("map_zoom_out")) {
          mapZoom = (mapZoom - 1).coerceIn(4, 18)
        }
        MapFabButton(Icons.Filled.MyLocation, "Pusatkan GPS", Modifier.testTag("map_recenter")) {
          autoFollow = true
          gps.location?.let {
            mapLat = it.lat
            mapLng = it.lng
          }
        }
        MapFabButton(Icons.Filled.Lock, "Kunci Layar Berkendara", Modifier.testTag("map_ride_lock")) {
          viewModel.setRideLock(true)
        }
      }
    }

    // 4. Bottom Route Card (Distance, Duration, Energy Physics, Elevation Profile, Start Nav)
    nav.activeRoute?.let { route ->
      Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, cockpit.accent.copy(alpha = 0.4f)),
        tonalElevation = 8.dp,
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (nav.routes.size > 1) {
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              nav.routes.forEachIndexed { idx, r ->
                FilterChip(
                  selected = idx == nav.selectedRouteIndex,
                  onClick = { viewModel.selectRouteOption(idx) },
                  label = { Text("${r.label} (${String.format(Locale.US, "%.1f", r.distanceKm)} km)") },
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = nav.destination?.name ?: "Tujuan",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val est = route.energyEstimate
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "${String.format(Locale.US, "%.1f", route.distanceKm)} km • ${route.durationMin.roundToInt()} mnt" +
                    (est?.let { " • Est ${it.netWh.roundToInt()} Wh" } ?: ""),
                  style = MaterialTheme.typography.bodySmall,
                  color = cockpit.accent,
                  fontWeight = FontWeight.SemiBold,
                )
                est?.socAtArrivalPct?.let { arrivalSoc ->
                  val badgeColor =
                    when {
                      arrivalSoc < 15f -> cockpit.danger
                      arrivalSoc < 30f -> cockpit.warning
                      else -> cockpit.accent
                    }
                  Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
                  ) {
                    Text(
                      text = "Sisa ${arrivalSoc.roundToInt()}%",
                      style = MaterialTheme.typography.labelSmall,
                      color = badgeColor,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                }
              }
            }
            nav.destination?.let { d ->
              IconButton(
                onClick = { viewModel.saveFavoriteDestination(d.name, d.lat, d.lng) },
                modifier = Modifier.size(48.dp).testTag("nav_save_fav_btn"),
              ) {
                Icon(Icons.Filled.Bookmark, contentDescription = "Simpan ke Favorit", tint = cockpit.warning)
              }
            }
          }

          if (route.elevationSamples.size >= 2) {
            ElevationProfileCanvas(samples = route.elevationSamples)
            route.energyEstimate?.let { est ->
              Text(
                "Tanjakan max ${String.format(Locale.US, "%.1f", est.maxClimbPct)}% • Naik +${est.totalAscentM.roundToInt()}m • Regen +${est.regenWh.roundToInt()} Wh",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!nav.isNavigating) {
              Button(
                onClick = { viewModel.startNavigation() },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("start_nav_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
              ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Mulai Navigasi")
              }
            } else {
              OutlinedButton(
                onClick = { viewModel.stopNavigation() },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
              ) {
                Text("Selesai Rute")
              }
            }
          }
        }
      }
    }

    // 5. Ride Lock Overlay
    if (nav.rideLockActive) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(Color(0xF0050C12))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
          Icon(Icons.Filled.Lock, contentDescription = "Terkunci", tint = cockpit.accent, modifier = Modifier.size(48.dp))
          Text("Mode Kunci Berkendara", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
          Text(
            eff.speedKmh?.let { "${it.roundToInt()} km/h" } ?: "0 km/h",
            style = MaterialTheme.typography.displayLarge,
            color = cockpit.accent,
          )
          Text(
            "Baterai ${eff.combinedSocPct?.roundToInt() ?: "—"}% • Sisa ~${String.format(Locale.US, "%.1f", eff.rangeKm)} km",
            style = MaterialTheme.typography.titleMedium,
          )
          HoldToConfirmButton(
            label = "Tahan 1.5 dtk untuk Buka Kunci",
            holdDurationMs = 1500L,
            containerColor = cockpit.accent,
            contentColor = Color.White,
            onConfirmed = { viewModel.setRideLock(false) },
            modifier = Modifier.fillMaxWidth().testTag("ride_lock_unlock_btn"),
          )
        }
      }
    }
  }

  // SPKLU Finder Bottom Sheet
  if (showSpkluSheet) {
    ModalBottomSheet(onDismissRequest = { showSpkluSheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("SPKLU Terdekat", style = MaterialTheme.typography.titleLarge)
          OutlinedButton(onClick = { viewModel.searchNearbySpklu() }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Refresh")
          }
        }
        if (nav.spkluLoading) {
          Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = cockpit.accent)
          }
        } else if (nav.spkluStations.isEmpty()) {
          Text(
            nav.spkluError ?: "Belum ada hasil SPKLU. Pastikan GPS & internet aktif.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
          ) {
            items(nav.spkluStations, key = { it.id }) { st ->
              CockpitCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(st.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                      "${st.operatorName} • ${st.connectorSummary}" +
                        (st.powerKw?.let { " • ${it.roundToInt()} kW" } ?: ""),
                      style = MaterialTheme.typography.bodySmall,
                      color = cockpit.info,
                    )
                    Text(
                      "${String.format(Locale.US, "%.1f", st.distanceKm)} km • ${st.source}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Button(
                    onClick = {
                      showSpkluSheet = false
                      autoFollow = false
                      mapLat = st.lat
                      mapLng = st.lng
                      viewModel.selectDestination(NavDestination(st.name, st.lat, st.lng))
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("spklu_go_${st.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
                  ) {
                    Text("Rute")
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Favorites Sheet
  if (showFavSheet) {
    ModalBottomSheet(onDismissRequest = { showFavSheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("Lokasi Favorit", style = MaterialTheme.typography.titleLarge)
        if (favorites.isEmpty()) {
          Text(
            "Belum ada lokasi favorit. Cari tujuan atau ketuk peta lalu tekan ikon bookmark.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          favorites.forEach { fav ->
            CockpitCard(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(fav.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                  Text(
                    String.format(Locale.US, "%.4f, %.4f", fav.lat, fav.lng),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Row {
                  Button(
                    onClick = {
                      showFavSheet = false
                      viewModel.selectDestination(NavDestination(fav.name, fav.lat, fav.lng))
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                  ) {
                    Text("Rute")
                  }
                  IconButton(
                    onClick = { viewModel.deleteFavorite(fav) },
                    modifier = Modifier.size(48.dp),
                  ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Hapus Favorit", tint = cockpit.danger)
                  }
                }
              }
            }
          }
        }
        Spacer(Modifier.height(16.dp))
      }
    }
  }

  // Multi-Stop Trip Planner Sheet
  if (showPlannerSheet) {
    ModalBottomSheet(onDismissRequest = { showPlannerSheet = false }) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text("Perencana Rute Multi-Stop", style = MaterialTheme.typography.titleLarge)
        nav.destination?.let { d ->
          OutlinedButton(
            onClick = { viewModel.addMultiStop(d.name, d.lat, d.lng) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
          ) {
            Text("+ Tambah '${d.name}' sebagai Pemberhentian")
          }
        }
        if (nav.multiStops.isEmpty()) {
          Text(
            "Pilih tujuan di peta atau pencarian, lalu tambahkan ke daftar Multi-Stop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          nav.multiStops.forEachIndexed { idx, st ->
            CockpitCard(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("${idx + 1}. ${st.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.removeMultiStop(st.id) }, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Filled.Delete, contentDescription = "Hapus Stop", tint = cockpit.danger)
                }
              }
            }
          }
          Button(
            onClick = {
              showPlannerSheet = false
              viewModel.calculateMultiStopRoute()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cockpit.accent),
          ) {
            Text("Hitung Rute Multi-Stop")
          }
        }
        Spacer(Modifier.height(18.dp))
      }
    }
  }
}

@Composable
private fun MapFabButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    modifier = modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    tonalElevation = 4.dp,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
  }
}
