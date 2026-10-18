package com.dannydetour.weathertour

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class CalendarDayNote(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val date: String, // "YYYY-MM-DD"
    val createdAtMillis: Long = System.currentTimeMillis(),
    val text: String
)

private const val ONE_HOUR_MILLIS = 60 * 60 * 1000L
private const val MAX_EMOJIS_PER_RULE = 5

enum class ForecastMode(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    WHOLE_WEEK("Whole Week")
}

enum class AddPlaceTab(val label: String) {
    SEARCH("🔍 Search"),
    COORDS("🧭 Coords"),
    MAP("🗺️ Map")
}

enum class RuleCondition(val label: String) {
    TEMP_BELOW("Temp Below (≤)"),
    TEMP_ABOVE("Temp Above (≥)"),
    TEMP_BETWEEN("Temp Between (↔)"),
    RAIN("Rain / Showers"),
    SNOW("Snow")
}

data class WeatherRule(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val condition: RuleCondition,
    val threshold: Double? = null,
    val thresholdMax: Double? = null,
    val message: String,
    val emojis: List<String> = listOf("💡")
) {
    val emojiDisplay: String
        get() = emojis.joinToString(" ")
}

data class PlaceLocation(
    val id: String = "${System.currentTimeMillis()}_${(0..9999).random()}",
    val name: String,
    val subtitle: String,
    val placeType: String = "📍 Location",
    val latitude: Double,
    val longitude: Double
)

data class DailyForecast(
    val date: String,
    val dayOfWeekOrder: Int, // 1 = Monday ... 7 = Sunday
    val dayName: String,
    val temperature: Double,
    val minTemp: Double,
    val windSpeed: Double,
    val weatherCode: Int
)

data class PlaceWeather(
    val currentTemp: Double,
    val currentWind: Double,
    val currentHumidity: Int,
    val currentCode: Int,
    val tomorrow: DailyForecast?,
    val weekMondayToSunday: List<DailyForecast>,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)

data class WeatherEmojiOption(
    val symbol: String,
    val label: String,
    val keywords: String
)

val WEATHER_EMOJI_CATALOG: List<WeatherEmojiOption> = listOf(
    // Sweaters, Coats & Tops
    WeatherEmojiOption("🧶", "Light Sweater", "light sweater cardigan knitwear jumper pullovers"),
    WeatherEmojiOption("🦺", "Light Sweater Vest", "light sweater vest fleece sleeveless knitwear layer fall"),
    WeatherEmojiOption("👚", "Light Knit Top", "light sweater knit top blouse pullover jumper fall warm"),
    WeatherEmojiOption("🥼", "Long Sleeves Top", "long sleeves sleeves shirt coat pullover cover arms"),
    WeatherEmojiOption("🧥", "Winter Coat / Jacket", "winter sweater heavy coat parka jacket cold"),
    WeatherEmojiOption("🧥❄️", "Heavy Winter Sweater", "winter sweater fleece thermal heavy cold snow"),
    WeatherEmojiOption("👕", "T-Shirt / Light Top", "tshirt shirt warm light top summer"),
    WeatherEmojiOption("🎽", "Tank Top / Singlet", "tank top hot summer sleeveless"),
    WeatherEmojiOption("👔", "Layered Shirt / Long Sleeves", "shirt layer office collar long sleeves"),
    // Boots & Footwear
    WeatherEmojiOption("🥾", "Boots / Hiking Boots", "boots shoes hiking trek trail"),
    WeatherEmojiOption("🥾❄️", "Winter Snow Boots", "snow boots winter warm insulated footwear"),
    WeatherEmojiOption("👢", "Tall / Rain Boots", "tall boots rain waterproof wellies"),
    WeatherEmojiOption("👟", "Sneakers / Trainers", "sneakers shoes running walking dry"),
    WeatherEmojiOption("🩴", "Sandals / Flip-Flops", "sandals flip flops beach hot summer"),
    WeatherEmojiOption("🧦", "Warm Socks", "socks wool warm feet toes"),
    // Cold & Wind Accessories
    WeatherEmojiOption("🧣", "Warm Scarf / Neck Warmer", "scarf neck warmer wind cold winter autumn"),
    WeatherEmojiOption("🧣🍂", "Light Autumn Scarf", "light autumn scarf neck wrap fall warm"),
    WeatherEmojiOption("🧤", "Gloves / Mittens", "gloves mittens hands cold snow"),
    WeatherEmojiOption("🎩", "Warm Beanie / Hat", "beanie hat winter head warm"),
    WeatherEmojiOption("🍂🧢", "Light Autumn Hat", "light autumn hat cap baseball fall head cover"),
    WeatherEmojiOption("🪖", "Beret / Autumn Cap", "beret autumn cap hat head cover fall"),
    WeatherEmojiOption("🧕", "Bandana / Headscarf", "bandana headscarf wrap kerchief neckerchief head cover"),
    WeatherEmojiOption("🤠", "Bandana Neckerchief", "bandana neckerchief neck scarf cowboy wrap"),
    // Rain & Sun Gear
    WeatherEmojiOption("☔", "Umbrella (Rain)", "umbrella rain shower wet waterproof"),
    WeatherEmojiOption("☂️", "Compact Umbrella", "umbrella drizzle pack carry"),
    WeatherEmojiOption("🧢", "Cap / Visor", "cap baseball hat sun shade"),
    WeatherEmojiOption("👒", "Sun Hat", "sun hat wide brim uv summer"),
    WeatherEmojiOption("🕶️", "Sunglasses", "sunglasses shades bright uv sunny eyewear"),
    WeatherEmojiOption("👓", "Glasses / Clear Shades", "glasses shades sunglasses clear eye protection uv"),
    WeatherEmojiOption("🧴", "Sunscreen / UV", "sunscreen spf lotion uv burn"),
    // Bottoms & Outfits
    WeatherEmojiOption("👖", "Long Pants / Jeans", "pants jeans trousers legs cool"),
    WeatherEmojiOption("🩳", "Shorts", "shorts warm summer hot legs"),
    WeatherEmojiOption("👗", "Light Dress", "dress summer warm breeze"),
    // Hydration & Comfort
    WeatherEmojiOption("💧", "Water Drop / Hydrate", "water drop drink hydrate bottle hot moisture"),
    WeatherEmojiOption("🥤", "Water Bottle / Tumbler", "water bottle flask cup tumbler hydrate drink"),
    WeatherEmojiOption("💦", "Water Drops / Splash", "water drop splash rain shower hydrate moisture"),
    WeatherEmojiOption("☕", "Hot Coffee / Cocoa", "coffee hot drink warm mug"),
    WeatherEmojiOption("🍵", "Hot Tea", "tea warm drink cozy"),
    // Outdoor & Mountain Activities
    WeatherEmojiOption("🎿", "Ski Gear", "ski resort snow mountain goggles"),
    WeatherEmojiOption("🏂", "Snowboard Gear", "snowboard mountain powder winter"),
    WeatherEmojiOption("⛸️", "Ice Skates", "ice skates rink winter freeze"),
    // Weather Symbols & Alerts
    WeatherEmojiOption("☀️", "Sunny / Clear", "sun sunny clear hot bright"),
    WeatherEmojiOption("⛅", "Partly Cloudy", "clouds mild pleasant"),
    WeatherEmojiOption("🌧️", "Rainy", "rain wet showers storm"),
    WeatherEmojiOption("⛈️", "Thunderstorm", "thunder storm lightning heavy rain"),
    WeatherEmojiOption("❄️", "Snow / Freezing", "snow flake cold frost ice"),
    WeatherEmojiOption("🧊", "Icy / Slippery", "ice icy slippery freeze road"),
    WeatherEmojiOption("💨", "Windy / Gusts", "wind windy breeze gusts"),
    WeatherEmojiOption("🌫️", "Fog / Low Visibility", "fog mist visibility haze"),
    WeatherEmojiOption("🌡️", "Temperature Alert", "temperature thermometer heat cold"),
    WeatherEmojiOption("💡", "General Tip", "tip idea reminder note")
)

object WeatherStorage {
    private const val PREFS_NAME = "weathertour_persistent_prefs"
    private const val KEY_PLACES = "saved_places_v2"
    private const val KEY_RULES = "saved_rules_v2"
    private const val KEY_WEATHER_CACHE = "saved_weather_cache_v2"
    private const val KEY_FAV_EMOJIS = "saved_fav_emojis_v2"
    private const val KEY_HISTORY_EMOJIS = "saved_history_emojis_v2"
    private const val KEY_CALENDAR_NOTES = "saved_calendar_notes_v2"

    fun savePlaces(context: Context, places: List<PlaceLocation>) {
        println("WeatherStorage: Saving ${places.size} places (preserving custom order)")
        val arr = JSONArray()
        places.forEach { place ->
            arr.put(
                JSONObject().apply {
                    put("id", place.id)
                    put("name", place.name)
                    put("subtitle", place.subtitle)
                    put("placeType", place.placeType)
                    put("latitude", place.latitude)
                    put("longitude", place.longitude)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLACES, arr.toString())
            .apply()
    }

    fun loadPlaces(context: Context): List<PlaceLocation>? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLACES, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<PlaceLocation>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PlaceLocation(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        subtitle = obj.optString("subtitle", ""),
                        placeType = obj.optString("placeType", "📍 Location"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude")
                    )
                )
            }
            println("WeatherStorage: Loaded ${list.size} saved places")
            list
        } catch (e: Exception) {
            println("WeatherStorage: Failed to load places: ${e.message}")
            null
        }
    }

    fun saveRules(context: Context, rules: List<WeatherRule>) {
        println("WeatherStorage: Saving ${rules.size} weather rules")
        val arr = JSONArray()
        rules.forEach { rule ->
            val cleanedEmojis = rule.emojis.take(MAX_EMOJIS_PER_RULE).ifEmpty { listOf("💡") }
            arr.put(
                JSONObject().apply {
                    put("id", rule.id)
                    put("condition", rule.condition.name)
                    if (rule.threshold != null) put("threshold", rule.threshold)
                    if (rule.thresholdMax != null) put("thresholdMax", rule.thresholdMax)
                    put("message", rule.message)
                    put("emojis", JSONArray(cleanedEmojis))
                    put("emoji", cleanedEmojis.first())
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES, arr.toString())
            .apply()
    }

    fun loadRules(context: Context): List<WeatherRule>? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RULES, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<WeatherRule>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val condName = obj.optString("condition", RuleCondition.TEMP_BELOW.name)
                val condition = runCatching { RuleCondition.valueOf(condName) }
                    .getOrDefault(RuleCondition.TEMP_BELOW)

                val loadedEmojis = mutableListOf<String>()
                val emojisArr = obj.optJSONArray("emojis")
                if (emojisArr != null) {
                    for (j in 0 until emojisArr.length()) {
                        val sym = emojisArr.optString(j, "").trim()
                        if (sym.isNotEmpty()) loadedEmojis.add(sym)
                    }
                }
                if (loadedEmojis.isEmpty()) {
                    val legacySingle = obj.optString("emoji", "💡").trim().ifEmpty { "💡" }
                    loadedEmojis.add(legacySingle)
                }

                list.add(
                    WeatherRule(
                        id = obj.getLong("id"),
                        condition = condition,
                        threshold = if (obj.has("threshold")) obj.getDouble("threshold") else null,
                        thresholdMax = if (obj.has("thresholdMax")) obj.getDouble("thresholdMax") else null,
                        message = obj.getString("message"),
                        emojis = loadedEmojis.take(MAX_EMOJIS_PER_RULE)
                    )
                )
            }
            println("WeatherStorage: Loaded ${list.size} saved weather rules")
            list
        } catch (e: Exception) {
            println("WeatherStorage: Failed to load rules: ${e.message}")
            null
        }
    }

    fun saveWeatherCache(context: Context, map: Map<String, PlaceWeather>) {
        println("WeatherStorage: Caching weather data for ${map.size} places")
        val root = JSONObject()
        map.forEach { (placeId, weather) ->
            val obj = JSONObject().apply {
                put("currentTemp", weather.currentTemp)
                put("currentWind", weather.currentWind)
                put("currentHumidity", weather.currentHumidity)
                put("currentCode", weather.currentCode)
                put("fetchedAtMillis", weather.fetchedAtMillis)
                if (weather.tomorrow != null) {
                    put("tomorrow", serializeDailyForecast(weather.tomorrow))
                }
                val weekArr = JSONArray()
                weather.weekMondayToSunday.forEach { day ->
                    weekArr.put(serializeDailyForecast(day))
                }
                put("weekMondayToSunday", weekArr)
            }
            root.put(placeId, obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WEATHER_CACHE, root.toString())
            .apply()
    }

    fun loadWeatherCache(context: Context): Map<String, PlaceWeather> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WEATHER_CACHE, null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, PlaceWeather>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.getJSONObject(key)
                val tomorrow = if (obj.has("tomorrow")) {
                    deserializeDailyForecast(obj.getJSONObject("tomorrow"))
                } else null
                val weekArr = obj.optJSONArray("weekMondayToSunday") ?: JSONArray()
                val weekList = mutableListOf<DailyForecast>()
                for (i in 0 until weekArr.length()) {
                    weekList.add(deserializeDailyForecast(weekArr.getJSONObject(i)))
                }
                result[key] = PlaceWeather(
                    currentTemp = obj.getDouble("currentTemp"),
                    currentWind = obj.getDouble("currentWind"),
                    currentHumidity = obj.getInt("currentHumidity"),
                    currentCode = obj.getInt("currentCode"),
                    tomorrow = tomorrow,
                    weekMondayToSunday = weekList,
                    fetchedAtMillis = obj.optLong("fetchedAtMillis", System.currentTimeMillis())
                )
            }
            println("WeatherStorage: Restored cached weather for ${result.size} places")
            result
        } catch (e: Exception) {
            println("WeatherStorage: Failed to load cached weather: ${e.message}")
            emptyMap()
        }
    }

    private fun serializeDailyForecast(day: DailyForecast): JSONObject {
        return JSONObject().apply {
            put("date", day.date)
            put("dayOfWeekOrder", day.dayOfWeekOrder)
            put("dayName", day.dayName)
            put("temperature", day.temperature)
            put("minTemp", day.minTemp)
            put("windSpeed", day.windSpeed)
            put("weatherCode", day.weatherCode)
        }
    }

    private fun deserializeDailyForecast(obj: JSONObject): DailyForecast {
        return DailyForecast(
            date = obj.getString("date"),
            dayOfWeekOrder = obj.getInt("dayOfWeekOrder"),
            dayName = obj.getString("dayName"),
            temperature = obj.getDouble("temperature"),
            minTemp = obj.getDouble("minTemp"),
            windSpeed = obj.getDouble("windSpeed"),
            weatherCode = obj.getInt("weatherCode")
        )
    }

    fun saveFavoriteEmojis(context: Context, emojis: List<String>) {
        println("WeatherStorage: Saving ${emojis.size} favorite emojis")
        val arr = JSONArray(emojis)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAV_EMOJIS, arr.toString())
            .apply()
    }

    fun loadFavoriteEmojis(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FAV_EMOJIS, null)
            ?: return listOf("🧶", "🧥", "🥾", "☔", "🧣")
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (e: Exception) {
            listOf("🧶", "🧥", "🥾", "☔", "🧣")
        }
    }

    fun saveEmojiHistory(context: Context, emojis: List<String>) {
        println("WeatherStorage: Saving ${emojis.size} recent emojis to history")
        val arr = JSONArray(emojis)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY_EMOJIS, arr.toString())
            .apply()
    }

    fun loadEmojiHistory(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY_EMOJIS, null)
            ?: return listOf("🧶", "☔", "🥾❄️")
        return try {
            val arr = JSONArray(raw)
            List(arr.length()) { i -> arr.getString(i) }
        } catch (e: Exception) {
            listOf("🧶", "☔", "🥾❄️")
        }
    }

    fun saveCalendarNotes(context: Context, notes: List<CalendarDayNote>) {
        println("WeatherStorage: Saving ${notes.size} calendar notes")
        val arr = JSONArray()
        notes.forEach { note ->
            arr.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("date", note.date)
                    put("createdAtMillis", note.createdAtMillis)
                    put("text", note.text)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CALENDAR_NOTES, arr.toString())
            .apply()
    }

    fun loadCalendarNotes(context: Context): List<CalendarDayNote> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CALENDAR_NOTES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<CalendarDayNote>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    CalendarDayNote(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        date = obj.getString("date"),
                        createdAtMillis = obj.optLong("createdAtMillis", System.currentTimeMillis()),
                        text = obj.getString("text")
                    )
                )
            }
            println("WeatherStorage: Loaded ${list.size} saved calendar notes")
            list
        } catch (e: Exception) {
            println("WeatherStorage: Failed to load calendar notes: ${e.message}")
            emptyList()
        }
    }
}

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF161616),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFBB86FC),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF141414),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiaryContainer = Color(0xFF1A241E),
    onTertiaryContainer = Color(0xFF81C995),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF6E6E6E),
    errorContainer = Color(0xFF2D1215),
    onErrorContainer = Color(0xFFF2B8B5)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        println("MainActivity: Starting WeatherTour application in pure black AMOLED mode")
        setContent {
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as Activity).window
                    window.statusBarColor = Color.Black.toArgb()
                    window.navigationBarColor = Color.Black.toArgb()
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = false
                    controller.isAppearanceLightNavigationBars = false
                }
            }

            MaterialTheme(colorScheme = AmoledColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    WeatherScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedMode by remember { mutableStateOf(ForecastMode.TODAY) }

    var trackedPlaces by remember {
        mutableStateOf(
            WeatherStorage.loadPlaces(context) ?: listOf(
                PlaceLocation(
                    id = "default_new_york",
                    name = "New York",
                    subtitle = "40.7128°N, 74.0060°W • United States",
                    placeType = "🏙️ City",
                    latitude = 40.7128,
                    longitude = -74.0060
                ),
                PlaceLocation(
                    id = "default_los_angeles",
                    name = "Los Angeles",
                    subtitle = "34.0522°N, 118.2437°W • United States",
                    placeType = "🏙️ City",
                    latitude = 34.0522,
                    longitude = -118.2437
                )
            )
        )
    }

    var weatherMap by remember {
        mutableStateOf(WeatherStorage.loadWeatherCache(context))
    }

    var weatherRules by remember {
        mutableStateOf(
            WeatherStorage.loadRules(context) ?: listOf(
                WeatherRule(
                    condition = RuleCondition.TEMP_BELOW,
                    threshold = 10.0,
                    message = "Cold weather - wear a warm coat",
                    emojis = listOf("🧥", "🧣", "🧤")
                ),
                WeatherRule(
                    condition = RuleCondition.TEMP_ABOVE,
                    threshold = 25.0,
                    message = "Warm & sunny - stay hydrated",
                    emojis = listOf("👕", "🧢", "🕶️", "💧")
                ),
                WeatherRule(
                    condition = RuleCondition.RAIN,
                    message = "Rain expected - take an umbrella",
                    emojis = listOf("☔", "👢")
                )
            )
        )
    }

    var calendarNotes by remember {
        mutableStateOf(WeatherStorage.loadCalendarNotes(context))
    }

    var favoriteEmojis by remember {
        mutableStateOf(WeatherStorage.loadFavoriteEmojis(context))
    }
    var emojiHistory by remember {
        mutableStateOf(WeatherStorage.loadEmojiHistory(context))
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // State for tap-and-hold drag-and-drop city reordering
    var draggedPlaceId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val cardHeights = remember { mutableStateMapOf<String, Int>() }

    var showAddPlaceDialog by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var ruleBeingEdited by remember { mutableStateOf<WeatherRule?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }

    fun updateTrackedPlaces(newList: List<PlaceLocation>) {
        trackedPlaces = newList
        WeatherStorage.savePlaces(context, newList)
    }

    fun updateWeatherRules(newList: List<WeatherRule>) {
        weatherRules = newList
        WeatherStorage.saveRules(context, newList)
    }

    fun addCalendarNote(date: String, text: String) {
        val newNote = CalendarDayNote(date = date, text = text)
        val updated = listOf(newNote) + calendarNotes
        calendarNotes = updated
        WeatherStorage.saveCalendarNotes(context, updated)
        println("WeatherScreen: Saved calendar note on $date: '$text'")
    }

    fun deleteCalendarNote(noteId: Long) {
        val updated = calendarNotes.filter { it.id != noteId }
        calendarNotes = updated
        WeatherStorage.saveCalendarNotes(context, updated)
        println("WeatherScreen: Deleted calendar note id=$noteId")
    }

    fun recordEmojisUsage(usedEmojis: List<String>) {
        println("WeatherScreen: Recording emojis $usedEmojis in history")
        val updated = (usedEmojis + emojiHistory.filter { it !in usedEmojis }).take(14)
        emojiHistory = updated
        WeatherStorage.saveEmojiHistory(context, updated)
    }

    fun toggleFavoriteEmoji(emoji: String) {
        val updated = if (favoriteEmojis.contains(emoji)) {
            println("WeatherScreen: Removing '$emoji' from favorite emojis")
            favoriteEmojis - emoji
        } else {
            println("WeatherScreen: Adding '$emoji' to favorite emojis")
            listOf(emoji) + favoriteEmojis
        }
        favoriteEmojis = updated
        WeatherStorage.saveFavoriteEmojis(context, updated)
    }

    fun refreshAllWeather() {
        println("WeatherScreen: Refreshing weather for ${trackedPlaces.size} tracked places")
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            val updatedMap = weatherMap.toMutableMap()
            try {
                for (place in trackedPlaces) {
                    val data = fetchWeather(place.latitude, place.longitude)
                    updatedMap[place.id] = data
                    weatherMap = updatedMap.toMap()
                }
                WeatherStorage.saveWeatherCache(context, updatedMap)
                currentTimeMillis = System.currentTimeMillis()
            } catch (e: Exception) {
                errorMessage = "Could not update live weather (${e.localizedMessage ?: "Network error"}). Showing saved forecast."
                println("WeatherScreen Error: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    // Periodically tick currentTimeMillis so the 1-hour yellow warning triangle updates live
    LaunchedEffect(Unit) {
        refreshAllWeather()
        while (true) {
            delay(30_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text("WeatherTour", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(
                        onClick = {
                            println("WeatherScreen: Opening About dialog")
                            showAboutDialog = true
                        }
                    ) {
                        Text("ℹ️", fontSize = 18.sp)
                    }
                    OutlinedButton(
                        onClick = { refreshAllWeather() },
                        enabled = !isLoading,
                        border = BorderStroke(1.dp, Color(0xFF333333)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(if (isLoading) "Updating..." else "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(
                    state = rememberScrollState(),
                    enabled = draggedPlaceId == null // Lock page scroll while dragging a card
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- TOP MODE SELECTOR: TODAY / TOMORROW / WHOLE WEEK ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ForecastMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Button(
                        onClick = {
                            println("WeatherScreen: Switching view mode to ${mode.label}")
                            selectedMode = mode
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF121212),
                            contentColor = if (isSelected) Color.Black else Color.White
                        ),
                        border = if (isSelected) null else BorderStroke(1.dp, Color(0xFF2C2C2C))
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    border = BorderStroke(1.dp, Color(0xFF5C2327)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // --- SECTION 1: TRACKED PLACES ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Tracked Places",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (trackedPlaces.size > 1) {
                        Text(
                            text = "Tap & hold a card to reorder (saved automatically)",
                            fontSize = 12.sp,
                            color = Color(0xFF777777)
                        )
                    }
                }
                Button(
                    onClick = {
                        println("WeatherScreen: Opening Add Place dialog")
                        showAddPlaceDialog = true
                    }
                ) {
                    Text("+")
                }
            }

            if (trackedPlaces.isEmpty()) {
                Text(
                    text = "No places tracked yet. Tap '+' to search, enter coordinates, or pick on the map.",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                trackedPlaces.forEach { place ->
                    key(place.id) {
                        val isDragged = draggedPlaceId == place.id
                        val scale by animateFloatAsState(
                            targetValue = if (isDragged) 1.04f else 1.0f,
                            label = "cardDragScale"
                        )
                        val data = weatherMap[place.id]
                        val isDataStale = data != null &&
                            (currentTimeMillis - data.fetchedAtMillis) >= ONE_HOUR_MILLIS

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragged) 10f else 0f)
                                .onGloballyPositioned { coordinates ->
                                    cardHeights[place.id] = coordinates.size.height
                                }
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationY = if (isDragged) dragOffsetY else 0f
                                    shadowElevation = if (isDragged) 24f else 0f
                                }
                                .pointerInput(place.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            println("WeatherScreen: Started dragging place '${place.name}'")
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedPlaceId = place.id
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            println("WeatherScreen: Finished dragging place '${place.name}' and saved order")
                                            draggedPlaceId = null
                                            dragOffsetY = 0f
                                            WeatherStorage.savePlaces(context, trackedPlaces)
                                        },
                                        onDragCancel = {
                                            draggedPlaceId = null
                                            dragOffsetY = 0f
                                            WeatherStorage.savePlaces(context, trackedPlaces)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            val currentIndex = trackedPlaces.indexOfFirst { it.id == place.id }
                                            if (currentIndex != -1) {
                                                // Move down if dragged past half of the next card
                                                if (dragOffsetY > 0 && currentIndex < trackedPlaces.lastIndex) {
                                                    val nextId = trackedPlaces[currentIndex + 1].id
                                                    val nextStep = (cardHeights[nextId] ?: 320) + 40
                                                    if (dragOffsetY > nextStep * 0.55f) {
                                                        val mutable = trackedPlaces.toMutableList()
                                                        val item = mutable.removeAt(currentIndex)
                                                        mutable.add(currentIndex + 1, item)
                                                        trackedPlaces = mutable
                                                        WeatherStorage.savePlaces(context, mutable)
                                                        dragOffsetY -= nextStep
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        println("WeatherScreen: Moved '${place.name}' down to index ${currentIndex + 1}")
                                                    }
                                                }
                                                // Move up if dragged past half of the previous card
                                                else if (dragOffsetY < 0 && currentIndex > 0) {
                                                    val prevId = trackedPlaces[currentIndex - 1].id
                                                    val prevStep = (cardHeights[prevId] ?: 320) + 40
                                                    if (dragOffsetY < -prevStep * 0.55f) {
                                                        val mutable = trackedPlaces.toMutableList()
                                                        val item = mutable.removeAt(currentIndex)
                                                        mutable.add(currentIndex - 1, item)
                                                        trackedPlaces = mutable
                                                        WeatherStorage.savePlaces(context, mutable)
                                                        dragOffsetY += prevStep
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        println("WeatherScreen: Moved '${place.name}' up to index ${currentIndex - 1}")
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            WeatherCard(
                                place = place,
                                data = data,
                                mode = selectedMode,
                                isDragged = isDragged,
                                isLoading = isLoading,
                                isDataStale = isDataStale,
                                rules = weatherRules,
                                onRemove = {
                                    println("WeatherScreen: Removing place ${place.name}")
                                    updateTrackedPlaces(trackedPlaces.filter { it.id != place.id })
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF222222),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // --- SECTION 2: WEATHER RULES ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weather Rules",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Up to 5 outfit emojis per rule • Tap Edit on any rule",
                        fontSize = 12.sp,
                        color = Color(0xFF777777)
                    )
                }
                Button(
                    onClick = {
                        println("WeatherScreen: Opening Add Rule dialog")
                        ruleBeingEdited = null
                        showRuleDialog = true
                    }
                ) {
                    Text("+")
                }
            }

            if (weatherRules.isEmpty()) {
                Text(
                    text = "No rules configured. Tap '+ Add Rule' to create one.",
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                weatherRules.forEach { rule ->
                    key(rule.id) {
                        WeatherRuleCard(
                            rule = rule,
                            onEditClick = {
                                println("WeatherScreen: Edit button clicked on rule '${rule.message}', opening editor")
                                ruleBeingEdited = rule
                                showRuleDialog = true
                            },
                            onDelete = {
                                println("WeatherScreen: Deleting rule: ${rule.message}")
                                updateWeatherRules(weatherRules.filter { it.id != rule.id })
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF222222),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // --- SECTION 3: CALENDAR & ANNUAL NOTES ---
            CalendarNotesSection(
                notes = calendarNotes,
                onAddNote = { date, text -> addCalendarNote(date, text) },
                onDeleteNote = { noteId -> deleteCalendarNote(noteId) }
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = Color(0xFF0D0D0D),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFCCCCCC),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("WeatherTour", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Surface(
                        color = Color(0xFF1E2838),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "v1.0.0",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
                        border = BorderStroke(1.dp, Color(0xFF262626)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "\"I used to look at my thermometer and didn't know what to wear to enjoy my time outside. Now I can see the numbers and corresponding comments. Why two places? I like to hike and want to know the temperature at my destination. The comment section exists so I can see what was happening previously at the same date.\"",
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color(0xFFE0E0E0),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Core Capabilities", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text("• Pure AMOLED black interface with minimal battery drain", fontSize = 12.sp, color = Color(0xFFB0B0B0))
                        Text("• Customizable weather rules with emoji clothing tags", fontSize = 12.sp, color = Color(0xFFB0B0B0))
                        Text("• Multi-destination comparison with drag & drop reordering", fontSize = 12.sp, color = Color(0xFFB0B0B0))
                        Text("• 3-way location picker: Map, search & direct coordinates", fontSize = 12.sp, color = Color(0xFFB0B0B0))
                        Text("• Date-linked calendar notes for hiking & trip history", fontSize = 12.sp, color = Color(0xFFB0B0B0))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Privacy & License", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text("100% client-side storage. Zero telemetry, tracking, or ads.", fontSize = 12.sp, color = Color(0xFF81C995))
                        Text("Licensed under the MIT License • Copyright © 2026 UnrealDanny", fontSize = 11.sp, color = Color(0xFF888888))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAboutDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (showAddPlaceDialog) {
        AddPlaceDialog(
            onDismiss = { showAddPlaceDialog = false },
            onPlaceSelected = { newPlace ->
                println("WeatherScreen: Added place ${newPlace.name} (${newPlace.latitude}, ${newPlace.longitude})")
                updateTrackedPlaces(trackedPlaces + newPlace)
                showAddPlaceDialog = false
                coroutineScope.launch {
                    try {
                        val data = fetchWeather(newPlace.latitude, newPlace.longitude)
                        val updatedMap = weatherMap + (newPlace.id to data)
                        weatherMap = updatedMap
                        WeatherStorage.saveWeatherCache(context, updatedMap)
                    } catch (e: Exception) {
                        println("WeatherScreen: Failed fetching weather for ${newPlace.name}: ${e.message}")
                    }
                }
            }
        )
    }

    if (showRuleDialog) {
        RuleEditorDialog(
            initialRule = ruleBeingEdited,
            favoriteEmojis = favoriteEmojis,
            emojiHistory = emojiHistory,
            onToggleFavoriteEmoji = { toggleFavoriteEmoji(it) },
            onDismiss = {
                showRuleDialog = false
                ruleBeingEdited = null
            },
            onSave = { savedRule ->
                recordEmojisUsage(savedRule.emojis)
                val updatedRules = if (ruleBeingEdited != null) {
                    println("WeatherScreen: Updating existing rule id=${savedRule.id} (${savedRule.condition} -> ${savedRule.emojiDisplay} ${savedRule.message})")
                    weatherRules.map { if (it.id == savedRule.id) savedRule else it }
                } else {
                    println("WeatherScreen: Saving new rule (${savedRule.condition} -> ${savedRule.emojiDisplay} ${savedRule.message})")
                    weatherRules + savedRule
                }
                updateWeatherRules(updatedRules)
                showRuleDialog = false
                ruleBeingEdited = null
            }
        )
    }
}

@Composable
fun WeatherCard(
    place: PlaceLocation,
    data: PlaceWeather?,
    mode: ForecastMode,
    isDragged: Boolean,
    isLoading: Boolean,
    isDataStale: Boolean,
    rules: List<WeatherRule>,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragged) Color(0xFF141820) else Color(0xFF0A0A0A)
        ),
        border = BorderStroke(
            width = if (isDragged) 2.dp else 1.dp,
            color = if (isDragged) MaterialTheme.colorScheme.primary else Color(0xFF222222)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
        ) {
            // --- HEADER: Place Name, Place Type Below Name, Stale Warning, and Remove Button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = place.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isDataStale) {
                            StaleDataBadge()
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFF1E281F),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = place.placeType,
                                fontSize = 11.sp,
                                color = Color(0xFF81C995),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (isLoading && data != null) {
                            Text(
                                text = "Updating...",
                                fontSize = 11.sp,
                                color = Color(0xFF8AB4F8)
                            )
                        }
                    }
                    if (place.subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = place.subtitle,
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "⋮⋮",
                        fontSize = 16.sp,
                        color = Color(0xFF555555),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Text("✕", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (data != null) {
                when (mode) {
                    ForecastMode.TODAY -> {
                        val activeRules = getTriggeredRules(data.currentTemp, data.currentCode, rules)
                        SingleDayWeatherSection(
                            headlineTemp = "${data.currentTemp}°C",
                            subTempLabel = "Current temperature",
                            weatherCode = data.currentCode,
                            leftDetail = "Wind: ${data.currentWind} km/h",
                            rightDetail = "Humidity: ${data.currentHumidity}%",
                            isDataStale = isDataStale,
                            activeRules = activeRules
                        )
                    }

                    ForecastMode.TOMORROW -> {
                        val tom = data.tomorrow
                        if (tom != null) {
                            val activeRules = getTriggeredRules(tom.temperature, tom.weatherCode, rules)
                            SingleDayWeatherSection(
                                headlineTemp = "${tom.temperature}°C",
                                subTempLabel = "Tomorrow's temperature (Night low: ${tom.minTemp}°C)",
                                weatherCode = tom.weatherCode,
                                leftDetail = "Wind: ${tom.windSpeed} km/h",
                                rightDetail = "${tom.dayName} (${tom.date})",
                                isDataStale = isDataStale,
                                activeRules = activeRules
                            )
                        } else {
                            Text("Tomorrow's forecast is unavailable.", color = Color(0xFF777777))
                        }
                    }

                    ForecastMode.WHOLE_WEEK -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Weekly Forecast (Monday – Sunday)",
                                fontSize = 12.sp,
                                color = Color(0xFF888888)
                            )
                            if (isDataStale) {
                                Text(
                                    text = "⚠️ Data >1h old",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            data.weekMondayToSunday.forEach { day ->
                                val dayRules = getTriggeredRules(day.temperature, day.weatherCode, rules)
                                WeeklyDayItem(day = day, activeRules = dayRules)
                            }
                        }
                    }
                }
            } else if (isLoading) {
                Text(text = "Fetching initial forecast...", color = Color(0xFF777777))
            } else {
                Text(text = "Weather data unavailable. Tap Refresh.", color = Color(0xFF777777))
            }
        }
    }
}

@Composable
fun StaleDataBadge() {
    Surface(
        color = Color(0xFF2B2308),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFFFFC107))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 12.sp
            )
            Text(
                text = ">1h old",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFD54F)
            )
        }
    }
}

@Composable
fun SingleDayWeatherSection(
    headlineTemp: String,
    subTempLabel: String,
    weatherCode: Int,
    leftDetail: String,
    rightDetail: String,
    isDataStale: Boolean,
    activeRules: List<WeatherRule>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = headlineTemp,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                if (isDataStale) {
                    Text(
                        text = "⚠️",
                        fontSize = 22.sp,
                        color = Color(0xFFFFD54F)
                    )
                }
            }
            Text(
                text = subTempLabel,
                fontSize = 12.sp,
                color = Color(0xFF888888)
            )
        }
        Text(
            text = decodeWeatherCode(weatherCode),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFCCCCCC)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    HorizontalDivider(color = Color(0xFF1F1F1F))
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = leftDetail, fontSize = 14.sp, color = Color(0xFFAAAAAA))
        Text(text = rightDetail, fontSize = 14.sp, color = Color(0xFFAAAAAA))
    }

    if (activeRules.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        activeRules.forEach { rule ->
            RuleAlertBanner(emojis = rule.emojis, message = rule.message)
        }
    }
}

@Composable
fun WeeklyDayItem(day: DailyForecast, activeRules: List<WeatherRule>) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF111111),
        border = BorderStroke(1.dp, Color(0xFF1F1F1F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = day.dayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = day.date,
                        fontSize = 12.sp,
                        color = Color(0xFF777777)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${day.temperature}°C",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Low ${day.minTemp}°C",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = decodeWeatherCode(day.weatherCode),
                    fontSize = 13.sp,
                    color = Color(0xFFCCCCCC)
                )
                Text(
                    text = "Wind: ${day.windSpeed} km/h",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }

            if (activeRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                activeRules.forEach { rule ->
                    RuleAlertBanner(emojis = rule.emojis, message = rule.message)
                }
            }
        }
    }
}

@Composable
fun RuleAlertBanner(emojis: List<String>, message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF111914),
        border = BorderStroke(1.dp, Color(0xFF264732)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = emojis.joinToString(" "),
                fontSize = 16.sp
            )
            Text(
                text = message,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF81C995),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun WeatherRuleCard(
    rule: WeatherRule,
    onEditClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        border = BorderStroke(1.dp, Color(0xFF222222))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Condition Title + Message + Top-Right Delete Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val conditionDescription = when (rule.condition) {
                        RuleCondition.TEMP_BELOW -> "If temperature ≤ ${rule.threshold}°C"
                        RuleCondition.TEMP_ABOVE -> "If temperature ≥ ${rule.threshold}°C"
                        RuleCondition.TEMP_BETWEEN -> {
                            val minVal = minOf(rule.threshold ?: 0.0, rule.thresholdMax ?: 0.0)
                            val maxVal = maxOf(rule.threshold ?: 0.0, rule.thresholdMax ?: 0.0)
                            "If temperature between $minVal°C and $maxVal°C"
                        }
                        RuleCondition.RAIN -> "If raining / drizzle / thunderstorm"
                        RuleCondition.SNOW -> "If snowing"
                    }
                    Text(
                        text = conditionDescription,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"${rule.message}\"",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Text(
                        text = "✕",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF888888)
                    )
                }
            }

            // Bottom Row: Up to 5 Emojis on the Left + Edit Button on the Bottom-Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    rule.emojis.forEach { emojiSymbol ->
                        Surface(
                            color = Color(0xFF161616),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF2A2A2A))
                        ) {
                            Text(
                                text = emojiSymbol,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onEditClick,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "✎ Edit",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarNotesSection(
    notes: List<CalendarDayNote>,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    var viewingMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var noteInputText by remember { mutableStateOf("") }
    val today = remember { LocalDate.now() }

    val daysOfWeekLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    // Multi-year notes matching the selected day-of-year (MM-dd)
    val selectedMonthDayKey = "%02d-%02d".format(selectedDate.monthValue, selectedDate.dayOfMonth)
    val dayNotes = remember(notes, selectedDate) {
        notes.filter { it.date.endsWith("-$selectedMonthDayKey") }
            .sortedByDescending { it.createdAtMillis }
    }
    val formattedSelectedDate = "${selectedDate.dayOfMonth} ${selectedDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Title & Subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Calendar & Notes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Pick a day to see all notes written on it across any year",
                    fontSize = 12.sp,
                    color = Color(0xFF777777)
                )
            }
        }

        // Calendar Card containing Month Bar, Weekday Header, Days Grid, and Selected Day Notes
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            border = BorderStroke(1.dp, Color(0xFF222222))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Top Month Navigation Bar ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${viewingMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${viewingMonth.year}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = {
                                val now = LocalDate.now()
                                viewingMonth = YearMonth.of(now.year, now.month)
                                selectedDate = now
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Today",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { viewingMonth = viewingMonth.minusMonths(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(
                                text = "‹",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = { viewingMonth = viewingMonth.plusMonths(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(
                                text = "›",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                // --- 7-Day Weekday Labels (Mon - Sun) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    daysOfWeekLabels.forEach { label ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF777777)
                            )
                        }
                    }
                }

                // --- Monthly Days Grid ---
                val firstOfMonth = viewingMonth.atDay(1)
                val daysInMonth = viewingMonth.lengthOfMonth()
                val leadingSpaces = firstOfMonth.dayOfWeek.value - 1 // 1 = Monday ... 7 = Sunday
                val totalSlots = leadingSpaces + daysInMonth
                val numRows = (totalSlots + 6) / 7

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0 until numRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (col in 0..6) {
                                val slotIndex = row * 7 + col
                                val dayNum = slotIndex - leadingSpaces + 1

                                if (dayNum in 1..daysInMonth) {
                                    val thisDate = viewingMonth.atDay(dayNum)
                                    val monthDayKey = "%02d-%02d".format(thisDate.monthValue, thisDate.dayOfMonth)
                                    val hasNotes = notes.any { it.date.endsWith("-$monthDayKey") }
                                    val isSelected = (selectedDate == thisDate)
                                    val isToday = (thisDate == today)

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> Color(0xFF1E281F)
                                            else -> Color(0xFF131313)
                                        },
                                        border = when {
                                            isSelected -> null
                                            isToday -> BorderStroke(1.dp, Color(0xFF81C995))
                                            else -> BorderStroke(1.dp, Color(0xFF222222))
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clickable { selectedDate = thisDate }
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "$dayNum",
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> Color.Black
                                                    isToday -> Color(0xFF81C995)
                                                    else -> Color.White
                                                }
                                            )
                                            if (hasNotes) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected) Color.Black else MaterialTheme.colorScheme.primary
                                                        )
                                                )
                                            } else {
                                                Spacer(modifier = Modifier.height(5.dp))
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E1E1E), modifier = Modifier.padding(vertical = 4.dp))

                // --- Selected Day Notes Header & Summary ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🗓️ $formattedSelectedDate Notes",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (dayNotes.isEmpty()) {
                                "No notes written on this day in any year"
                            } else {
                                "${dayNotes.size} note${if (dayNotes.size == 1) "" else "s"} written on this day across all years"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }

                    if (dayNotes.isNotEmpty()) {
                        Surface(
                            color = Color(0xFF1A241E),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${dayNotes.size} total",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF81C995),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // --- Add Note Input Bar ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = { noteInputText = it },
                        placeholder = {
                            Text(
                                "Add a note for $formattedSelectedDate...",
                                fontSize = 13.sp,
                                color = Color(0xFF666666)
                            )
                        },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (noteInputText.isNotBlank()) {
                                onAddNote(selectedDate.toString(), noteInputText.trim())
                                noteInputText = ""
                            }
                        },
                        enabled = noteInputText.isNotBlank(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save")
                    }
                }

                // --- Multi-Year Notes List ---
                if (dayNotes.isEmpty()) {
                    Surface(
                        color = Color(0xFF111111),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E1E1E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No notes recorded for $formattedSelectedDate yet. Type a note above to record one for this day!",
                            fontSize = 12.sp,
                            color = Color(0xFF777777),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        dayNotes.forEach { note ->
                            val yearText = note.date.split("-").firstOrNull() ?: "—"
                            Surface(
                                color = Color(0xFF121212),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF222222)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = Color(0xFF182232),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            ) {
                                                Text(
                                                    text = "Year $yearText",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = note.date,
                                                fontSize = 11.sp,
                                                color = Color(0xFF666666)
                                            )
                                        }

                                        Text(
                                            text = note.text,
                                            fontSize = 14.sp,
                                            color = Color.White
                                        )
                                    }

                                    IconButton(
                                        onClick = { onDeleteNote(note.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Text(
                                            text = "✕",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF777777)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RuleEditorDialog(
    initialRule: WeatherRule?,
    favoriteEmojis: List<String>,
    emojiHistory: List<String>,
    onToggleFavoriteEmoji: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (WeatherRule) -> Unit
) {
    val isEditing = initialRule != null

    var selectedCondition by remember {
        mutableStateOf(initialRule?.condition ?: RuleCondition.TEMP_BELOW)
    }
    var thresholdMinText by remember {
        mutableStateOf(initialRule?.threshold?.toString()?.removeSuffix(".0") ?: "12")
    }
    var thresholdMaxText by remember {
        mutableStateOf(initialRule?.thresholdMax?.toString()?.removeSuffix(".0") ?: "18")
    }
    var messageText by remember {
        mutableStateOf(initialRule?.message ?: "light sweater")
    }
    var selectedEmojis by remember {
        mutableStateOf(
            initialRule?.emojis?.take(MAX_EMOJIS_PER_RULE)?.ifEmpty { listOf("🧶") }
                ?: listOf("🧶")
        )
    }
    var focusedEmojiForFav by remember {
        mutableStateOf(selectedEmojis.lastOrNull() ?: "🧶")
    }
    var emojiSearchQuery by remember { mutableStateOf("") }

    fun toggleEmojiSelection(symbol: String) {
        focusedEmojiForFav = symbol
        selectedEmojis = if (selectedEmojis.contains(symbol)) {
            println("RuleEditorDialog: Removing emoji '$symbol' from rule selection")
            selectedEmojis - symbol
        } else if (selectedEmojis.size < MAX_EMOJIS_PER_RULE) {
            println("RuleEditorDialog: Adding emoji '$symbol' to rule selection (${selectedEmojis.size + 1}/$MAX_EMOJIS_PER_RULE)")
            selectedEmojis + symbol
        } else {
            println("RuleEditorDialog: Reached max $MAX_EMOJIS_PER_RULE emojis, replacing last slot with '$symbol'")
            selectedEmojis.dropLast(1) + symbol
        }
    }

    val needsSingleThreshold = selectedCondition == RuleCondition.TEMP_BELOW ||
        selectedCondition == RuleCondition.TEMP_ABOVE
    val needsBetweenThreshold = selectedCondition == RuleCondition.TEMP_BETWEEN

    val filteredCatalog = remember(emojiSearchQuery) {
        val q = emojiSearchQuery.trim().lowercase()
        if (q.isEmpty()) {
            WEATHER_EMOJI_CATALOG
        } else {
            WEATHER_EMOJI_CATALOG.filter {
                it.label.lowercase().contains(q) ||
                    it.keywords.lowercase().contains(q) ||
                    it.symbol.contains(q)
            }
        }
    }

    val parsedMin = thresholdMinText.replace(",", ".").trim().toDoubleOrNull()
    val parsedMax = thresholdMaxText.replace(",", ".").trim().toDoubleOrNull()

    val validThresholds = when {
        needsSingleThreshold -> parsedMin != null
        needsBetweenThreshold -> parsedMin != null && parsedMax != null
        else -> true
    }
    val canSave = messageText.isNotBlank() && validThresholds && selectedEmojis.isNotEmpty()

    fun trySave() {
        if (canSave) {
            onSave(
                WeatherRule(
                    id = initialRule?.id ?: (System.currentTimeMillis() + (0..9999).random()),
                    condition = selectedCondition,
                    threshold = if (needsSingleThreshold || needsBetweenThreshold) parsedMin else null,
                    thresholdMax = if (needsBetweenThreshold) parsedMax else null,
                    message = messageText.trim(),
                    emojis = selectedEmojis.take(MAX_EMOJIS_PER_RULE)
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
                Text(
                    text = if (isEditing) "Edit Rule" else "New Rule",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Button(
                    onClick = { trySave() },
                    enabled = canSave,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isEditing) "Update" else "Save",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("1. Condition type:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = (selectedCondition == RuleCondition.TEMP_BELOW),
                            onClick = {
                                selectedCondition = RuleCondition.TEMP_BELOW
                                if (!isEditing) {
                                    messageText = "light sweater"
                                    selectedEmojis = listOf("🧶", "👖")
                                }
                            },
                            label = { Text("Below °C", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = (selectedCondition == RuleCondition.TEMP_ABOVE),
                            onClick = {
                                selectedCondition = RuleCondition.TEMP_ABOVE
                                if (!isEditing) {
                                    messageText = "Stay hydrated & wear a cap"
                                    selectedEmojis = listOf("🧢", "🕶️", "💧")
                                }
                            },
                            label = { Text("Above °C", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = (selectedCondition == RuleCondition.TEMP_BETWEEN),
                            onClick = {
                                selectedCondition = RuleCondition.TEMP_BETWEEN
                                if (!isEditing) {
                                    messageText = "light sweater"
                                    selectedEmojis = listOf("🧶", "👖", "👟")
                                }
                            },
                            label = { Text("Between", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = (selectedCondition == RuleCondition.RAIN),
                            onClick = {
                                selectedCondition = RuleCondition.RAIN
                                if (!isEditing) {
                                    messageText = "Take an umbrella & rain boots"
                                    selectedEmojis = listOf("☔", "🧥", "👢")
                                }
                            },
                            label = { Text("🌧 Rain") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = (selectedCondition == RuleCondition.SNOW),
                            onClick = {
                                selectedCondition = RuleCondition.SNOW
                                if (!isEditing) {
                                    messageText = "Warm jacket & snow boots"
                                    selectedEmojis = listOf("🧥❄️", "🥾❄️", "🧣", "🧤")
                                }
                            },
                            label = { Text("❄️ Snow") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (needsSingleThreshold) {
                    OutlinedTextField(
                        value = thresholdMinText,
                        onValueChange = { thresholdMinText = it },
                        label = { Text("Temperature (°C)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (needsBetweenThreshold) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = thresholdMinText,
                            onValueChange = { thresholdMinText = it },
                            label = { Text("Min (°C)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = thresholdMaxText,
                            onValueChange = { thresholdMaxText = it },
                            label = { Text("Max (°C)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message to display") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 2
                )

                HorizontalDivider(color = Color(0xFF222222))

                // --- EMOJI OUTFIT TRAY (UP TO 5 EMOJIS) ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "2. Selected Garb Emojis (${selectedEmojis.size}/$MAX_EMOJIS_PER_RULE)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Tap an emoji below to add/remove (tap slot to remove)",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }

                    val isFav = favoriteEmojis.contains(focusedEmojiForFav)
                    OutlinedButton(
                        onClick = { onToggleFavoriteEmoji(focusedEmojiForFav) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isFav) Color(0xFFFFD54F) else Color(0xFF333333)
                        )
                    ) {
                        Text(
                            text = if (isFav) "★ $focusedEmojiForFav" else "☆ $focusedEmojiForFav",
                            fontSize = 11.sp,
                            color = if (isFav) Color(0xFFFFD54F) else Color.White
                        )
                    }
                }

                // 5-Slot Interactive Tray
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (slotIndex in 0 until MAX_EMOJIS_PER_RULE) {
                        val emojiInSlot = selectedEmojis.getOrNull(slotIndex)
                        val isFilled = emojiInSlot != null
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isFilled) Color(0xFF182232) else Color(0xFF121212),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isFilled) MaterialTheme.colorScheme.primary else Color(0xFF262626)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clickable(enabled = isFilled) {
                                    if (emojiInSlot != null) {
                                        println("RuleEditorDialog: Removed emoji '$emojiInSlot' via tray slot tap")
                                        selectedEmojis = selectedEmojis.filterIndexed { idx, _ -> idx != slotIndex }
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (emojiInSlot != null) {
                                    Text(text = emojiInSlot, fontSize = 18.sp)
                                } else {
                                    Text(
                                        text = "${slotIndex + 1}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF444444)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- EMOJI SEARCH BAR ---
                OutlinedTextField(
                    value = emojiSearchQuery,
                    onValueChange = { emojiSearchQuery = it },
                    label = { Text("🔍 Search emojis (boots, sweater, coat, gloves...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // --- FAVORITE EMOJIS ROW ---
                if (favoriteEmojis.isNotEmpty()) {
                    Text(
                        text = "★ Favorites",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFD54F)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        favoriteEmojis.forEach { emoji ->
                            EmojiQuickChip(
                                emoji = emoji,
                                isSelected = selectedEmojis.contains(emoji),
                                onClick = { toggleEmojiSelection(emoji) }
                            )
                        }
                    }
                }

                // --- RECENT HISTORY ROW ---
                if (emojiHistory.isNotEmpty()) {
                    Text(
                        text = "🕒 Recently Used",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFAAAAAA)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        emojiHistory.forEach { emoji ->
                            EmojiQuickChip(
                                emoji = emoji,
                                isSelected = selectedEmojis.contains(emoji),
                                onClick = { toggleEmojiSelection(emoji) }
                            )
                        }
                    }
                }

                // --- ALL WEATHER & CLOTHING EMOJIS LIST ---
                Text(
                    text = "Weather & Clothing Emojis (${filteredCatalog.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFAAAAAA)
                )

                if (filteredCatalog.isEmpty()) {
                    Text(
                        text = "No matching weather emojis found.",
                        fontSize = 12.sp,
                        color = Color(0xFF777777)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        filteredCatalog.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowItems.forEach { option ->
                                    val isChosen = selectedEmojis.contains(option.symbol)
                                    val isOptionFav = favoriteEmojis.contains(option.symbol)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isChosen) Color(0xFF1C2838) else Color(0xFF141414),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isChosen) MaterialTheme.colorScheme.primary else Color(0xFF262626)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                toggleEmojiSelection(option.symbol)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(text = option.symbol, fontSize = 17.sp)
                                                Text(
                                                    text = option.label,
                                                    fontSize = 11.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = if (isOptionFav) "★" else "☆",
                                                fontSize = 13.sp,
                                                color = if (isOptionFav) Color(0xFFFFD54F) else Color(0xFF555555),
                                                modifier = Modifier
                                                    .padding(start = 4.dp)
                                                    .clickable { onToggleFavoriteEmoji(option.symbol) }
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = null
    )
}

@Composable
fun EmojiQuickChip(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF1C2838) else Color(0xFF141414),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF292929)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = emoji,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun AddPlaceDialog(
    onDismiss: () -> Unit,
    onPlaceSelected: (PlaceLocation) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(AddPlaceTab.SEARCH) }

    // --- Tab 1: Search State ---
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceLocation>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // --- Tab 2: Coordinates State ---
    var customName by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var isResolvingCoords by remember { mutableStateOf(false) }
    var coordsError by remember { mutableStateOf<String?>(null) }

    // --- Tab 3: Map Picker State ---
    var pickedMapLat by remember { mutableStateOf<Double?>(null) }
    var pickedMapLon by remember { mutableStateOf<Double?>(null) }
    var pickedMapPlace by remember { mutableStateOf<PlaceLocation?>(null) }
    var isResolvingMapTap by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = Color(0xFF0A0A0A),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Add Location", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AddPlaceTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        Button(
                            onClick = {
                                println("AddPlaceDialog: Switched tab to ${tab.label}")
                                activeTab = tab
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF161616),
                                contentColor = if (selected) Color.Black else Color.White
                            ),
                            border = if (selected) null else BorderStroke(1.dp, Color(0xFF2C2C2C))
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                when (activeTab) {
                    AddPlaceTab.SEARCH -> {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("e.g. New York, London, Paris") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (searchQuery.trim().length >= 2) {
                                    println("AddPlaceDialog: Searching Nominatim for '$searchQuery'")
                                    coroutineScope.launch {
                                        isSearching = true
                                        searchError = null
                                        try {
                                            searchResults = searchPlacesNominatim(searchQuery.trim())
                                            if (searchResults.isEmpty()) {
                                                searchError = "No matching places found. Try a shorter name."
                                            }
                                        } catch (e: Exception) {
                                            searchError = "Search failed: ${e.localizedMessage}"
                                        } finally {
                                            isSearching = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSearching && searchQuery.trim().length >= 2,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSearching) "Searching..." else "Find Place")
                        }

                        if (isSearching) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        if (searchError != null) {
                            Text(
                                text = searchError ?: "",
                                color = Color(0xFFF2B8B5),
                                fontSize = 13.sp
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            searchResults.forEach { location ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF141414),
                                    border = BorderStroke(1.dp, Color(0xFF292929)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPlaceSelected(location) }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = location.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = Color(0xFF1E281F),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = location.placeType,
                                                fontSize = 11.sp,
                                                color = Color(0xFF81C995),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = location.subtitle,
                                            fontSize = 12.sp,
                                            color = Color(0xFF888888)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AddPlaceTab.COORDS -> {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            label = { Text("Place Name (optional - auto-detected)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = latInput,
                                onValueChange = { latInput = it },
                                label = { Text("Latitude") },
                                placeholder = { Text("40.7128") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = lonInput,
                                onValueChange = { lonInput = it },
                                label = { Text("Longitude") },
                                placeholder = { Text("-74.0060") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (coordsError != null) {
                            Text(
                                text = coordsError ?: "",
                                color = Color(0xFFF2B8B5),
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = {
                                val lat = latInput.replace(",", ".").trim().toDoubleOrNull()
                                val lon = lonInput.replace(",", ".").trim().toDoubleOrNull()
                                if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                                    coordsError = "Enter valid coordinates: Latitude (-90..90), Longitude (-180..180)"
                                    return@Button
                                }
                                coordsError = null
                                println("AddPlaceDialog: Adding place by coordinates ($lat, $lon)")
                                coroutineScope.launch {
                                    isResolvingCoords = true
                                    try {
                                        val resolved = reverseGeocodeNominatim(lat, lon)
                                        val finalPlace = if (customName.isNotBlank()) {
                                            resolved.copy(
                                                name = customName.trim(),
                                                placeType = "🧭 Coordinates"
                                            )
                                        } else {
                                            resolved
                                        }
                                        onPlaceSelected(finalPlace)
                                    } finally {
                                        isResolvingCoords = false
                                    }
                                }
                            },
                            enabled = !isResolvingCoords,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isResolvingCoords) "Resolving location..." else "Add by Coordinates")
                        }
                    }

                    AddPlaceTab.MAP -> {
                        Text(
                            text = "Tap anywhere on the map to drop a pin:",
                            fontSize = 12.sp,
                            color = Color(0xFFAAAAAA)
                        )

                        MapPickerView(
                            onLocationPicked = { lat, lon ->
                                println("AddPlaceDialog: Map tapped at ($lat, $lon)")
                                pickedMapLat = lat
                                pickedMapLon = lon
                                coroutineScope.launch {
                                    isResolvingMapTap = true
                                    try {
                                        pickedMapPlace = reverseGeocodeNominatim(lat, lon)
                                    } finally {
                                        isResolvingMapTap = false
                                    }
                                }
                            }
                        )

                        if (isResolvingMapTap) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        val preview = pickedMapPlace
                        if (preview != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF141414),
                                border = BorderStroke(1.dp, Color(0xFF292929)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = preview.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = preview.subtitle,
                                        fontSize = 11.sp,
                                        color = Color(0xFF888888)
                                    )
                                }
                            }

                            Button(
                                onClick = { onPlaceSelected(preview) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Selected Map Location")
                            }
                        } else if (pickedMapLat != null && pickedMapLon != null && !isResolvingMapTap) {
                            Button(
                                onClick = {
                                    val lat = pickedMapLat ?: return@Button
                                    val lon = pickedMapLon ?: return@Button
                                    onPlaceSelected(
                                        PlaceLocation(
                                            name = "Pinned Location",
                                            subtitle = "%.4f°N, %.4f°E".format(lat, lon),
                                            placeType = "🗺️ Map Pin",
                                            latitude = lat,
                                            longitude = lon
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Pinned Coordinates")
                            }
                        }
                    }
                }
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun MapPickerView(
    onLocationPicked: (Double, Double) -> Unit
) {
    val currentCallback by rememberUpdatedState(onLocationPicked)

    val mapHtml = remember {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                html, body, #map { height: 100%; margin: 0; padding: 0; background: #0a0a0a; }
                .leaflet-tile { filter: brightness(0.85) contrast(1.1); }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map').setView([40.7128, -74.0060], 10);
                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 18,
                    attribution: '© OpenStreetMap'
                }).addTo(map);
                var marker = null;
                map.on('click', function(e) {
                    var lat = e.latlng.lat;
                    var lng = e.latlng.lng;
                    if (marker) {
                        marker.setLatLng(e.latlng);
                    } else {
                        marker = L.marker(e.latlng).addTo(map);
                    }
                    AndroidBridge.onMapClicked(lat, lng);
                });
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF2E2E2E)),
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onMapClicked(lat: Double, lng: Double) {
                                post {
                                    currentCallback(lat, lng)
                                }
                            }
                        },
                        "AndroidBridge"
                    )
                    loadDataWithBaseURL(
                        "https://openstreetmap.org",
                        mapHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun getTriggeredRules(temperature: Double, weatherCode: Int, rules: List<WeatherRule>): List<WeatherRule> {
    return rules.filter { rule ->
        when (rule.condition) {
            RuleCondition.TEMP_BELOW -> {
                val limit = rule.threshold ?: return@filter false
                temperature <= limit
            }
            RuleCondition.TEMP_ABOVE -> {
                val limit = rule.threshold ?: return@filter false
                temperature >= limit
            }
            RuleCondition.TEMP_BETWEEN -> {
                val a = rule.threshold ?: return@filter false
                val b = rule.thresholdMax ?: return@filter false
                val low = minOf(a, b)
                val high = maxOf(a, b)
                temperature in low..high
            }
            RuleCondition.RAIN -> isRainCode(weatherCode)
            RuleCondition.SNOW -> isSnowCode(weatherCode)
        }
    }
}

fun isRainCode(code: Int): Boolean {
    return code in 51..67 || code in 80..82 || code in 95..99
}

fun isSnowCode(code: Int): Boolean {
    return code in 71..77 || code in 85..86
}

suspend fun reverseGeocodeNominatim(latitude: Double, longitude: Double): PlaceLocation {
    return withContext(Dispatchers.IO) {
        val endpoint = "https://nominatim.openstreetmap.org/reverse?lat=$latitude&lon=$longitude&format=json&addressdetails=1"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WeatherTourAndroidApp/1.0 (com.dannydetour.weathertour)")
            setRequestProperty("Accept-Language", "en")
            connectTimeout = 6000
            readTimeout = 6000
        }

        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
                val item = JSONObject(response)
                val displayName = item.optString("display_name", "")
                val parts = displayName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val mainName = item.optString("name", "").ifBlank {
                    parts.firstOrNull() ?: "Pinned Location"
                }
                val regionInfo = if (parts.size > 1) parts.drop(1).take(2).joinToString(", ") else "Map Selection"
                val subtitle = "%.4f°N, %.4f°E • %s".format(latitude, longitude, regionInfo)

                PlaceLocation(
                    name = mainName,
                    subtitle = subtitle,
                    placeType = "📍 Map Pin",
                    latitude = latitude,
                    longitude = longitude
                )
            } else {
                PlaceLocation(
                    name = "Location (%.3f, %.3f)".format(latitude, longitude),
                    subtitle = "%.4f°N, %.4f°E".format(latitude, longitude),
                    placeType = "🧭 Coordinates",
                    latitude = latitude,
                    longitude = longitude
                )
            }
        } catch (e: Exception) {
            PlaceLocation(
                name = "Location (%.3f, %.3f)".format(latitude, longitude),
                subtitle = "%.4f°N, %.4f°E".format(latitude, longitude),
                placeType = "🧭 Coordinates",
                latitude = latitude,
                longitude = longitude
            )
        } finally {
            connection.disconnect()
        }
    }
}

suspend fun searchPlacesNominatim(query: String): List<PlaceLocation> {
    return withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val endpoint = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=6&addressdetails=1&extratags=1"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "WeatherTourAndroidApp/1.0 (com.dannydetour.weathertour)")
            setRequestProperty("Accept-Language", "en")
            connectTimeout = 8000
            readTimeout = 8000
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }

            val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val resultsArray = JSONArray(response)
            val places = mutableListOf<PlaceLocation>()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val lat = item.getString("lat").toDouble()
                val lon = item.getString("lon").toDouble()

                val osmClass = item.optString("class", "")
                val osmType = item.optString("type", "")
                val friendlyType = when {
                    osmType == "ski_resort" || osmClass == "winter_sports" -> "🎿 Ski Resort"
                    osmType == "peak" || osmType == "volcano" -> "⛰️ Mountain Peak"
                    osmClass == "leisure" || osmClass == "tourism" -> "🌲 Resort"
                    osmClass == "place" -> "🏙️ ${osmType.replaceFirstChar { it.uppercase() }}"
                    else -> "📍 Location"
                }

                val displayName = item.optString("display_name", "")
                val parts = displayName.split(",").map { it.trim() }
                val mainName = item.optString("name", "").ifBlank { parts.firstOrNull() ?: "Unknown" }
                val regionInfo = if (parts.size > 1) parts.drop(1).take(2).joinToString(", ") else ""
                val coordSubtitle = "%.4f°N, %.4f°E • %s".format(lat, lon, regionInfo)

                places.add(
                    PlaceLocation(
                        name = mainName,
                        subtitle = coordSubtitle,
                        placeType = friendlyType,
                        latitude = lat,
                        longitude = lon
                    )
                )
            }
            places
        } finally {
            connection.disconnect()
        }
    }
}

suspend fun fetchWeather(latitude: Double, longitude: Double): PlaceWeather {
    return withContext(Dispatchers.IO) {
        val endpoint = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,wind_speed_10m_max" +
            "&timezone=auto&forecast_days=14"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val response = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")

            val times = daily.getJSONArray("time")
            val maxTemps = daily.getJSONArray("temperature_2m_max")
            val minTemps = daily.getJSONArray("temperature_2m_min")
            val codes = daily.getJSONArray("weather_code")
            val maxWinds = daily.getJSONArray("wind_speed_10m_max")

            val allForecastDays = mutableListOf<DailyForecast>()
            for (i in 0 until times.length()) {
                val dateStr = times.getString(i)
                val parsedDate = LocalDate.parse(dateStr)
                val dayOrder = parsedDate.dayOfWeek.value // 1 (Monday) to 7 (Sunday)
                val dayName = parsedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

                val dayTemp = maxTemps.getDouble(i)
                val lowTemp = minTemps.getDouble(i)

                allForecastDays.add(
                    DailyForecast(
                        date = dateStr,
                        dayOfWeekOrder = dayOrder,
                        dayName = dayName,
                        temperature = dayTemp,
                        minTemp = lowTemp,
                        windSpeed = maxWinds.getDouble(i),
                        weatherCode = codes.getInt(i)
                    )
                )
            }

            val tomorrowForecast = allForecastDays.getOrNull(1)
            val weekMonToSun = allForecastDays
                .take(7)
                .sortedBy { it.dayOfWeekOrder }

            PlaceWeather(
                currentTemp = current.getDouble("temperature_2m"),
                currentWind = current.getDouble("wind_speed_10m"),
                currentHumidity = current.getInt("relative_humidity_2m"),
                currentCode = current.getInt("weather_code"),
                tomorrow = tomorrowForecast,
                weekMondayToSunday = weekMonToSun,
                fetchedAtMillis = System.currentTimeMillis()
            )
        } finally {
            connection.disconnect()
        }
    }
}

fun decodeWeatherCode(code: Int): String {
    return when (code) {
        0 -> "☀️ Clear sky"
        1, 2, 3 -> "⛅ Partly cloudy"
        45, 48 -> "🌫 Fog"
        51, 53, 55, 56, 57 -> "🌦 Drizzle"
        61, 63, 65, 66, 67 -> "🌧 Rain"
        71, 73, 75, 77 -> "❄️ Snow"
        80, 81, 82 -> "🌧 Rain showers"
        85, 86 -> "❄️ Snow showers"
        95, 96, 99 -> "⛈ Thunderstorm"
        else -> "☁️ Overcast"
    }
}
