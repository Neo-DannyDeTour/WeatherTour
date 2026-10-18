# WeatherTour

> *"I used to look at my thermometer and didn't know what to wear to enjoy my time outside. Now I can see the numbers and corresponding comments. Why two places? I like to hike and want to know the temperature at my destination. The comment section exists so I can see what was happening previously at the same date."*

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)

A minimalist, privacy-focused Android weather application crafted for outdoor enthusiasts, hikers, and daily commuters. WeatherTour pairs live weather metrics with customizable clothing recommendations, multi-location destination comparison, and date-linked personal notes—all in a pure black AMOLED interface.

---

## ✨ Features

- **Pure AMOLED Black Design (`#000000`)**  
  True black background tailored for OLED displays to maximize contrast, readability in direct sunlight, and battery life.

- **Multi-Day Forecasting**  
  Seamlessly toggle between:
  - **Today**: Current temperature, wind speed, relative humidity, and weather condition codes.
  - **Tomorrow**: Dedicated next-day outlook for trip preparation.
  - **Whole Week**: Monday-to-Sunday progression with temperature ranges and condition summaries.
  - *Data Freshness Indicator*: Displays a yellow status triangle if cached data is older than 1 hour.

- **Custom Weather Rules & Emoji Tags**  
  Build your own dynamic advice engine:
  - Define custom conditions (e.g. *Temp Below ≤ 10°C*, *Temp Above ≥ 25°C*, *Rain / Showers*, *Snow*).
  - Assign descriptive advice (e.g. *"Cold weather - wear a warm coat"* or *"Rain expected - take an umbrella"*).
  - Attach up to 5 visual emoji tags from a categorized catalog (knitwear, footwear, scarves, rain gear, hydration, sunglasses).

- **Multi-Destination Tracking & Drag-and-Drop Reordering**  
  Track multiple locations at once (such as your home base and your hiking destination). Long-press and drag any city card to reorder your list instantly.

- **3-Way Location Picker**  
  - 🔍 **Search**: Geocode any city, mountain peak, or point of interest via OpenStreetMap Nominatim.
  - 🧭 **Coords**: Direct input for decimal latitude and longitude.
  - 🗺️ **Map**: Interactive Leaflet / OpenStreetMap map interface to tap and select exact pin coordinates.

- **Calendar Day Notes**  
  Log notes and observations directly against calendar dates to remember what gear worked best or what conditions were like on past trips.

- **Offline-First & Instant Startup**  
  All tracked places, custom rules, weather forecasts, and calendar entries are cached locally via persistent storage so the app works seamlessly even without immediate mobile reception on the trail.

---

## 🔒 Privacy by Design

WeatherTour respects your privacy:
- **Zero Location Tracking**: No background GPS tracking or device location permissions required.
- **No Analytics / Telemetry**: No third-party tracking SDKs, ad networks, or user profiling.
- **Client-Side Only**: Your tracked destinations, rules, and notes never leave your device.
- **Direct API Calls**: Weather forecasts are queried directly from Open-Meteo's open endpoints without intermediary proxy servers.

---

## 🛠️ Tech Stack & Services

- **Language & Framework**: [Kotlin](https://kotlinlang.org/) & [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3.
- **Weather API**: [Open-Meteo](https://open-meteo.com/) (open-source, free weather forecast API, no API key required).
- **Geocoding & Maps**: [OpenStreetMap Nominatim](https://nominatim.openstreetmap.org/) & [Leaflet.js](https://leafletjs.com/).
- **Architecture**: Single-activity Compose architecture, coroutines for asynchronous fetching, zero external heavy database dependencies.

---

## 🚀 Building & Running

### Prerequisites
- **Android Studio**: Ladybug / Meerkat or newer.
- **JDK**: Java 17 or higher.
- **Android SDK**: Compile SDK `36`, Minimum SDK `24` (Android 7.0 Nougat).

### Build from Terminal

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/WeatherTour.git
   cd WeatherTour
   ```

2. **Assemble Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   The debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

3. **Install on a connected device**:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
