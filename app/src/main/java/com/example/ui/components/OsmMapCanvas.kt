package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.data.local.FavoritePlaceEntity
import com.example.data.remote.OsmTileLoader
import com.example.model.GeoPoint
import com.example.model.NavDestination
import com.example.model.RouteOption
import com.example.model.SpkluStation
import com.example.ui.theme.LocalCockpitColors
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan
import kotlinx.coroutines.launch

@Composable
fun OsmMapCanvas(
  tileLoader: OsmTileLoader,
  centerLat: Double,
  centerLng: Double,
  zoom: Int,
  riderLocation: GeoPoint?,
  riderHeadingDeg: Float?,
  routes: List<RouteOption>,
  selectedRouteIndex: Int,
  destination: NavDestination?,
  spkluStations: List<SpkluStation>,
  favorites: List<FavoritePlaceEntity>,
  onPan: (dLat: Double, dLng: Double) -> Unit,
  onZoomChange: (delta: Int) -> Unit,
  onMapTap: (GeoPoint) -> Unit,
  modifier: Modifier = Modifier,
) {
  val cockpit = LocalCockpitColors.current
  val scope = rememberCoroutineScope()
  var tileRevision by remember { mutableIntStateOf(0) }
  var accumZoom by remember { mutableIntStateOf(0) }

  fun latLngToWorldPx(lat: Double, lng: Double, z: Int): Pair<Double, Double> {
    val scale = 256.0 * (1 shl z)
    val x = (lng + 180.0) / 360.0 * scale
    val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
    val y = (1.0 - asinh(tan(latRad)) / PI) / 2.0 * scale
    return x to y
  }

  fun worldPxToLatLng(wx: Double, wy: Double, z: Int): GeoPoint {
    val scale = 256.0 * (1 shl z)
    val lng = (wx / scale) * 360.0 - 180.0
    val n = PI - 2.0 * PI * wy / scale
    val lat = Math.toDegrees(atan(sinh(n)))
    return GeoPoint(lat, lng)
  }

  val clampedZoom = zoom.coerceIn(4, 18)

  LaunchedEffect(centerLat, centerLng, clampedZoom) {
    val (cwX, cwY) = latLngToWorldPx(centerLat, centerLng, clampedZoom)
    val cxTile = floor(cwX / 256.0).toInt()
    val cyTile = floor(cwY / 256.0).toInt()
    val maxTile = (1 shl clampedZoom) - 1
    for (dx in -2..2) {
      for (dy in -3..3) {
        val tx = (cxTile + dx).coerceIn(0, maxTile)
        val ty = (cyTile + dy).coerceIn(0, maxTile)
        if (tileLoader.getCached(clampedZoom, tx, ty) == null) {
          scope.launch {
            val loaded = tileLoader.loadTile(clampedZoom, tx, ty)
            if (loaded != null) tileRevision++
          }
        }
      }
    }
  }

  Box(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(clampedZoom, centerLat, centerLng) {
            detectTransformGestures { _, pan, zoomFactor, _ ->
              val scale = 256.0 * (1 shl clampedZoom)
              val dLng = -(pan.x / scale) * 360.0
              val (_, cwY) = latLngToWorldPx(centerLat, centerLng, clampedZoom)
              val nextLat = worldPxToLatLng(0.0, cwY - pan.y, clampedZoom).lat
              onPan(nextLat - centerLat, dLng)
              if (zoomFactor > 1.25f) {
                accumZoom++
                if (accumZoom >= 2) {
                  accumZoom = 0
                  onZoomChange(1)
                }
              } else if (zoomFactor < 0.8f) {
                accumZoom--
                if (accumZoom <= -2) {
                  accumZoom = 0
                  onZoomChange(-1)
                }
              }
            }
          }
          .pointerInput(clampedZoom, centerLat, centerLng) {
            detectTapGestures { offset ->
              val (cwX, cwY) = latLngToWorldPx(centerLat, centerLng, clampedZoom)
              val tapWx = cwX + (offset.x - size.width / 2.0)
              val tapWy = cwY + (offset.y - size.height / 2.0)
              onMapTap(worldPxToLatLng(tapWx, tapWy, clampedZoom))
            }
          }
    ) {
      @Suppress("UNUSED_VARIABLE")
      val rev = tileRevision
      val w = size.width
      val h = size.height
      val (cwX, cwY) = latLngToWorldPx(centerLat, centerLng, clampedZoom)

      fun toScreen(pt: GeoPoint): Offset {
        val (px, py) = latLngToWorldPx(pt.lat, pt.lng, clampedZoom)
        return Offset((w / 2f + (px - cwX)).toFloat(), (h / 2f + (py - cwY)).toFloat())
      }

      // 1. Render cached OSM tiles
      val minTileX = floor((cwX - w / 2.0) / 256.0).toInt()
      val maxTileX = floor((cwX + w / 2.0) / 256.0).toInt()
      val minTileY = floor((cwY - h / 2.0) / 256.0).toInt()
      val maxTileY = floor((cwY + h / 2.0) / 256.0).toInt()
      val maxIdx = (1 shl clampedZoom) - 1

      for (tx in minTileX..maxTileX) {
        for (ty in minTileY..maxTileY) {
          if (tx !in 0..maxIdx || ty !in 0..maxIdx) continue
          val sx = (w / 2.0 + tx * 256.0 - cwX).roundToInt()
          val sy = (h / 2.0 + ty * 256.0 - cwY).roundToInt()
          val bmp: ImageBitmap? = tileLoader.getCached(clampedZoom, tx, ty)
          if (bmp != null) {
            drawImage(
              image = bmp,
              dstOffset = IntOffset(sx, sy),
              dstSize = IntSize(257, 257),
              alpha = 0.88f,
            )
          } else {
            drawRect(
              color = Color(0xFF0E1B26),
              topLeft = Offset(sx.toFloat(), sy.toFloat()),
              size = androidx.compose.ui.geometry.Size(256f, 256f),
            )
            drawRect(
              color = Color(0xFF1A2D3D),
              topLeft = Offset(sx.toFloat(), sy.toFloat()),
              size = androidx.compose.ui.geometry.Size(256f, 256f),
              style = Stroke(width = 1f),
            )
          }
        }
      }

      // Subtle dark cockpit tint overlay for high legibility
      drawRect(color = Color(0x28040B12))

      // 2. Render alternative routes first, then selected route
      routes.forEachIndexed { idx, route ->
        if (idx == selectedRouteIndex || route.geometry.size < 2) return@forEachIndexed
        val path = Path()
        route.geometry.forEachIndexed { pIdx, gp ->
          val s = toScreen(gp)
          if (pIdx == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
        }
        drawPath(
          path = path,
          color = Color(0xFF607D8B).copy(alpha = 0.6f),
          style = Stroke(width = 12f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }

      routes.getOrNull(selectedRouteIndex)?.let { active ->
        if (active.geometry.size >= 2) {
          val path = Path()
          active.geometry.forEachIndexed { pIdx, gp ->
            val s = toScreen(gp)
            if (pIdx == 0) path.moveTo(s.x, s.y) else path.lineTo(s.x, s.y)
          }
          drawPath(
            path = path,
            color = Color(0xFF041418),
            style = Stroke(width = 18f, cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
          drawPath(
            path = path,
            color = cockpit.accent,
            style = Stroke(width = 11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }
      }

      // 3. Favorite pins
      favorites.forEach { fav ->
        val p = toScreen(GeoPoint(fav.lat, fav.lng))
        drawCircle(color = cockpit.warning, radius = 11f, center = p)
        drawCircle(color = Color.White, radius = 5f, center = p)
      }

      // 4. SPKLU charging station pins
      spkluStations.forEach { st ->
        val p = toScreen(GeoPoint(st.lat, st.lng))
        drawCircle(color = Color(0xFF05131E), radius = 14f, center = p)
        drawCircle(color = cockpit.info, radius = 10f, center = p)
        drawCircle(color = Color.White, radius = 3.5f, center = p)
      }

      // 5. Destination marker
      destination?.let { d ->
        val p = toScreen(GeoPoint(d.lat, d.lng))
        drawCircle(color = cockpit.danger.copy(alpha = 0.25f), radius = 26f, center = p)
        drawCircle(color = cockpit.danger, radius = 13f, center = p)
        drawCircle(color = Color.White, radius = 5f, center = p)
      }

      // 6. Rider location & directional arrow
      riderLocation?.let { rLoc ->
        val p = toScreen(rLoc)
        drawCircle(color = cockpit.accent.copy(alpha = 0.22f), radius = 30f, center = p)
        rotate(degrees = riderHeadingDeg ?: 0f, pivot = p) {
          val arrow =
            Path().apply {
              moveTo(p.x, p.y - 16f)
              lineTo(p.x + 12f, p.y + 12f)
              lineTo(p.x, p.y + 6f)
              lineTo(p.x - 12f, p.y + 12f)
              close()
            }
          drawPath(arrow, color = cockpit.accent)
          drawPath(arrow, color = Color.White, style = Stroke(width = 2.5f))
        }
      }
    }
  }
}
