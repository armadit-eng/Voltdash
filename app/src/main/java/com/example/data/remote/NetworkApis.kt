package com.example.data.remote

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OpenMeteoApi {
  @GET("v1/forecast")
  suspend fun getForecast(
    @Query("latitude") lat: Double,
    @Query("longitude") lng: Double,
    @Query("current")
    current: String =
      "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m",
    @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,weather_code",
    @Query("forecast_days") days: Int = 1,
    @Query("timezone") timezone: String = "auto",
  ): OpenMeteoResponse

  @GET("v1/elevation")
  suspend fun getElevation(
    @Query("latitude") latitudes: String,
    @Query("longitude") longitudes: String,
  ): OpenMeteoElevationResponse
}

interface NominatimApi {
  @GET("search")
  suspend fun search(
    @Query("q") query: String,
    @Query("format") format: String = "jsonv2",
    @Query("addressdetails") addressDetails: Int = 1,
    @Query("accept-language") lang: String = "id",
    @Query("limit") limit: Int = 8,
    @Query("viewbox") viewbox: String? = null,
  ): List<NominatimPlace>

  @GET("reverse")
  suspend fun reverse(
    @Query("lat") lat: Double,
    @Query("lon") lng: Double,
    @Query("format") format: String = "jsonv2",
    @Query("zoom") zoom: Int = 17,
    @Query("addressdetails") addressDetails: Int = 1,
    @Query("accept-language") lang: String = "id",
  ): NominatimPlace
}

interface OsrmApi {
  @GET("route/v1/driving/{coords}")
  suspend fun route(
    @retrofit2.http.Path("coords", encoded = true) coords: String,
    @Query("geometries") geometries: String = "geojson",
    @Query("steps") steps: Boolean = true,
    @Query("overview") overview: String = "full",
    @Query("alternatives") alternatives: Boolean = true,
  ): OsrmRouteResponse
}

interface OpenChargeMapApi {
  @GET("v3/poi/")
  suspend fun getNearbyStations(
    @Query("output") output: String = "json",
    @Query("latitude") lat: Double,
    @Query("longitude") lng: Double,
    @Query("distance") distanceKm: Double = 25.0,
    @Query("distanceunit") unit: String = "KM",
    @Query("maxresults") maxResults: Int = 35,
    @Query("compact") compact: Boolean = true,
    @Query("verbose") verbose: Boolean = false,
    @Header("X-API-Key") apiKey: String = OCM_KEY,
  ): List<OcmPoi>

  companion object {
    const val OCM_KEY = "458735a8-b4b1-416e-ab59-51843a45c0b4"
  }
}

interface OverpassApi {
  @FormUrlEncoded
  @POST("api/interpreter")
  suspend fun query(@Field("data") data: String): OverpassResponse
}

object NetworkClient {
  private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

  val okHttp: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .addInterceptor { chain ->
        val req =
          chain
            .request()
            .newBuilder()
            .header("User-Agent", "GESITSDashAndroid/1.0 (EV Cockpit)")
            .build()
        chain.proceed(req)
      }
      .build()

  val openMeteo: OpenMeteoApi =
    Retrofit.Builder()
      .baseUrl("https://api.open-meteo.com/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(OpenMeteoApi::class.java)

  val nominatim: NominatimApi =
    Retrofit.Builder()
      .baseUrl("https://nominatim.openstreetmap.org/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(NominatimApi::class.java)

  val osrm: OsrmApi =
    Retrofit.Builder()
      .baseUrl("https://router.project-osrm.org/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(OsrmApi::class.java)

  val openChargeMap: OpenChargeMapApi =
    Retrofit.Builder()
      .baseUrl("https://api.openchargemap.io/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(OpenChargeMapApi::class.java)

  val overpass: OverpassApi =
    Retrofit.Builder()
      .baseUrl("https://overpass-api.de/")
      .client(okHttp)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(OverpassApi::class.java)
}

class OsmTileLoader {
  private val cache = LruCache<String, android.graphics.Bitmap>(180)
  private val inFlight = mutableSetOf<String>()

  fun getCached(z: Int, x: Int, y: Int): ImageBitmap? = cache.get("$z/$x/$y")?.asImageBitmap()

  fun getCachedBitmap(z: Int, x: Int, y: Int): android.graphics.Bitmap? = cache.get("$z/$x/$y")

  suspend fun loadTile(z: Int, x: Int, y: Int): ImageBitmap? = loadTileBitmap(z, x, y)?.asImageBitmap()

  suspend fun loadTileBitmap(z: Int, x: Int, y: Int): android.graphics.Bitmap? {
    val key = "$z/$x/$y"
    cache.get(key)?.let { return it }
    synchronized(inFlight) {
      if (inFlight.contains(key)) return null
      inFlight.add(key)
    }
    return try {
      withContext(Dispatchers.IO) {
        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
        val req = Request.Builder().url(url).build()
        NetworkClient.okHttp.newCall(req).execute().use { resp ->
          if (!resp.isSuccessful) return@withContext null
          val bytes = resp.body?.bytes() ?: return@withContext null
          val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
          cache.put(key, bmp)
          bmp
        }
      }
    } catch (_: Exception) {
      null
    } finally {
      synchronized(inFlight) { inFlight.remove(key) }
    }
  }
}
