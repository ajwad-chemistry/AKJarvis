package com.akjarvis.app.data.web

import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale

/**
 * 30+ free, key-less web APIs exposed as assistant skills. Every function
 * returns a ready-to-display answer, or null when the service failed so the
 * caller can degrade gracefully.
 */
object WebTools {

    suspend fun dispatch(kind: String, query: String, cricketApiKey: String = ""): String? =
        runCatching {
            when (kind) {
                "WEATHER" -> weather(query)
                "FORECAST" -> forecast(query)
                "AIR" -> airQuality(query)
                "SUN" -> sunTimes(query)
                "WIKI" -> wikipedia(query)
                "MEANING" -> meaning(query)
                "TRANSLATE" -> translate(query)
                "NEWS" -> hackerNews()
                "BOOK" -> books(query)
                "COUNTRY" -> country(query)
                "PRAYER" -> prayerTimes(query)
                "CRICKET" -> cricketScores(cricketApiKey)
                "CURRENCY" -> currency(query)
                "CRYPTO" -> crypto(query)
                "CRYPTO_TREND" -> cryptoTrending()
                "JOKE" -> joke()
                "QUOTE" -> quote()
                "ADVICE" -> advice()
                "FACT" -> randomFact()
                "CAT" -> catPicture()
                "DOG" -> dogPicture()
                "XKCD" -> xkcd()
                "TIMEZONE" -> timeIn(query)
                "MYIP" -> myIp()
                "SHORTEN" -> shorten(query)
                "PLACE" -> whereIs(query)
                "DISTANCE" -> distance(query)
                "HOLIDAY" -> holidays(query)
                "GITHUB" -> github(query)
                "STACK" -> stackOverflow(query)
                "ISS" -> issPosition()
                "POKEMON" -> pokemon(query)
                "QURAN" -> quranVerse()
                "RESEARCH" -> research(query)
                else -> null
            }
        }.getOrNull()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun cleanCity(value: String) = value
        .replace(Regex("^(?:in|of|at|ka|ki|me|mein|for)\\s+", RegexOption.IGNORE_CASE), "")
        .trim()

    // ---- Geocoding (shared by weather/time/places) -------------------------

    private suspend fun geocode(city: String): Triple<Double, Double, String>? {
        val data = Http.getJson("https://geocoding-api.open-meteo.com/v1/search?name=${enc(city)}&count=1&language=en&format=json")
            ?: return null
        val hit = data.arr("results")?.at(0) ?: return null
        val lat = hit.dbl("latitude") ?: return null
        val lon = hit.dbl("longitude") ?: return null
        val label = listOfNotNull(hit.str("name"), hit.str("country")).joinToString(", ")
        return Triple(lat, lon, label)
    }

    private fun wmoText(code: Int?) = when (code) {
        0 -> "clear sky"; 1, 2 -> "partly cloudy"; 3 -> "overcast"; 45, 48 -> "foggy"
        in 51..57 -> "drizzle"; in 61..67 -> "rain"; in 71..77 -> "snow"
        in 80..82 -> "rain showers"; in 95..99 -> "thunderstorm"
        else -> "unknown"
    }

    // ---- Weather family ----------------------------------------------------

    suspend fun weather(city: String): String? {
        val (lat, lon, label) = geocode(cleanCity(city).ifBlank { "Karachi" }) ?: return null
        val data = Http.getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
        ) ?: return null
        val current = data.obj("current") ?: return null
        return "🌤 Weather in $label: ${current.str("temperature_2m")}°C " +
            "(${wmoText(current.int("weather_code"))}), feels like ${current.str("apparent_temperature")}°C, " +
            "humidity ${current.str("relative_humidity_2m")}%, wind ${current.str("wind_speed_10m")} km/h."
    }

    suspend fun forecast(city: String): String? {
        val (lat, lon, label) = geocode(cleanCity(city).ifBlank { "Karachi" }) ?: return null
        val data = Http.getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=5"
        ) ?: return null
        val daily = data.obj("daily") ?: return null
        val dates = daily.arr("time") ?: return null
        val lines = (0 until minOf(5, dates.size)).mapNotNull { i ->
            val day = dates.at(i).str()?.take(10) ?: return@mapNotNull null
            val max = daily.arr("temperature_2m_max")?.at(i).str() ?: return@mapNotNull null
            val min = daily.arr("temperature_2m_min")?.at(i).str() ?: return@mapNotNull null
            val code = daily.arr("weather_code")?.at(i).str()?.toIntOrNull()
            "• $day: $min–$max°C, ${wmoText(code)}"
        }
        return if (lines.isEmpty()) null else "📅 5-day forecast for $label:\n${lines.joinToString("\n")}"
    }

    suspend fun airQuality(city: String): String? {
        val (lat, lon, label) = geocode(cleanCity(city).ifBlank { "Lahore" }) ?: return null
        val data = Http.getJson(
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon&current=us_aqi,pm2_5"
        ) ?: return null
        val current = data.obj("current") ?: return null
        val aqi = current.int("us_aqi") ?: return null
        val category = when {
            aqi <= 50 -> "Good ✅"; aqi <= 100 -> "Moderate 🙂"; aqi <= 150 -> "Unhealthy for sensitive groups 😷"
            aqi <= 200 -> "Unhealthy 😷"; aqi <= 300 -> "Very unhealthy ☣️"; else -> "Hazardous ☣️"
        }
        return "🌫 Air quality in $label: US AQI $aqi ($category), PM2.5 ${current.str("pm_2_5") ?: current.str("pm2_5")} µg/m³."
    }

    suspend fun sunTimes(city: String): String? {
        val (lat, lon, label) = geocode(cleanCity(city).ifBlank { "Karachi" }) ?: return null
        val data = Http.getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&daily=sunrise,sunset&timezone=auto&forecast_days=1"
        ) ?: return null
        val daily = data.obj("daily") ?: return null
        val sunrise = daily.arr("sunrise")?.at(0).str()?.takeLast(5) ?: return null
        val sunset = daily.arr("sunset")?.at(0).str()?.takeLast(5) ?: return null
        return "🌅 $label — sunrise $sunrise, sunset $sunset (local time)."
    }

    // ---- Knowledge ----------------------------------------------------------

    suspend fun wikipedia(topic: String): String? {
        if (topic.isBlank()) return null
        val search = Http.getJson(
            "https://en.wikipedia.org/w/api.php?action=opensearch&search=${enc(topic)}&limit=1&format=json"
        )
        val title = (search as? kotlinx.serialization.json.JsonArray)?.getOrNull(1)
            ?.let { (it as? kotlinx.serialization.json.JsonArray)?.getOrNull(0)?.str() }
            ?: topic
        val data = Http.getJson("https://en.wikipedia.org/api/rest_v1/page/summary/${enc(title.replace(' ', '_'))}")
            ?: return null
        val extract = data.str("extract") ?: return null
        val url = data.obj("content_urls")?.obj("desktop")?.str("page")
        return "📚 $title: $extract${url?.let { "\n\nSource: $it" } ?: ""}"
    }

    suspend fun meaning(word: String): String? {
        if (word.isBlank()) return null
        val data = Http.getJson("https://api.dictionaryapi.dev/api/v2/entries/en/${enc(word)}")
            ?.arr()?.at(0) ?: return null
        val phonetic = data.str("phonetic") ?: data.arr("phonetics")?.firstOrNull()?.str("text").orEmpty()
        val meaning = data.arr("meanings")?.at(0) ?: return null
        val partOfSpeech = meaning.str("partOfSpeech") ?: ""
        val definition = meaning.arr("definitions")?.at(0)?.str("definition") ?: return null
        val example = meaning.arr("definitions")?.at(0)?.str("example")
        return "📖 \"$word\" ($partOfSpeech) $phonetic\n$definition${example?.let { "\nExample: \"$it\"" } ?: ""}"
    }

    suspend fun translate(query: String): String? {
        // query format: "<text> to <language>"
        val m = Regex("(.+?)\\s*(?:to|into|in)\\s+(\\w+)$", RegexOption.IGNORE_CASE).find(query.trim())
            ?: return translateSimple(query, "ur")
        val text = m.groupValues[1].trim()
        val target = when (m.groupValues[2].lowercase(Locale.ROOT)) {
            "urdu" -> "ur"; "english" -> "en"; "arabic" -> "ar"; "hindi" -> "hi"
            "spanish" -> "es"; "french" -> "fr"; "german" -> "de"; "turkish" -> "tr"
            "chinese" -> "zh"; "japanese" -> "ja"; "pashto" -> "ps"; "punjabi" -> "pa"
            else -> "ur"
        }
        return translateSimple(text, target)
    }

    private suspend fun translateSimple(text: String, target: String): String? {
        if (text.isBlank()) return null
        val data = Http.getJson(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=${enc(text)}"
        ) ?: return null
        val translated = data.at(0)?.arr()?.joinToString("") { it.at(0)?.str().orEmpty() }
        return if (translated.isNullOrBlank()) null else "🌐 Translation: $translated"
    }

    suspend fun hackerNews(): String? {
        val ids = Http.getJson("https://hacker-news.firebaseio.com/v0/topstories.json")
            ?.arr()?.take(5) ?: return null
        val lines = ids.mapNotNull { id ->
            val item = Http.getJson("https://hacker-news.firebaseio.com/v0/item/${id.str()}.json") ?: return@mapNotNull null
            val title = item.str("title") ?: return@mapNotNull null
            val score = item.int("score") ?: 0
            val link = item.str("url") ?: "https://news.ycombinator.com/item?id=${item.int("id")}"
            "• $title (+$score)\n  $link"
        }
        return if (lines.isEmpty()) null else "📰 Top Hacker News:\n${lines.joinToString("\n")}"
    }

    suspend fun books(query: String): String? {
        if (query.isBlank()) return null
        val data = Http.getJson("https://openlibrary.org/search.json?q=${enc(query)}&limit=3&fields=title,author_name,first_publish_year,key")
            ?: return null
        val docs = data.arr("docs") ?: return null
        val lines = docs.mapNotNull { doc ->
            val title = doc.str("title") ?: return@mapNotNull null
            val author = doc.arr("author_name")?.at(0).str() ?: "unknown"
            val year = doc.int("first_publish_year")
            "• \"$title\" by $author${year?.let { " ($it)" } ?: ""}"
        }
        return if (lines.isEmpty()) null else "📚 Books matching \"$query\":\n${lines.joinToString("\n")}"
    }

    suspend fun country(name: String): String? {
        if (name.isBlank()) return null
        val hit = Http.getJson("https://restcountries.com/v3.1/name/${enc(name)}?fields=name,capital,population,region,currencies,flag")
            ?.arr()?.at(0) ?: return null
        val common = hit.obj("name")?.str("common") ?: return null
        val capital = hit.arr("capital")?.at(0).str() ?: "—"
        val population = hit.dbl("population")?.let { String.format(Locale.US, "%,.0f", it) } ?: "—"
        val currency = (hit.obj("currencies") as? kotlinx.serialization.json.JsonObject)?.keys?.firstOrNull() ?: "—"
        return "🏳 $common — capital: $capital, region: ${hit.str("region") ?: "—"}, population: $population, currency: $currency ${hit.str("flag") ?: ""}"
    }

    // ---- Prayer / sports / finance ------------------------------------------

    suspend fun prayerTimes(city: String): String? {
        val clean = city.replace(Regex("^(times?|ke waqt|ka waqt|in|me|mein)\\s*", RegexOption.IGNORE_CASE), "").trim()
        val parts = clean.split(Regex("\\s+in\\s+|,\\s*", RegexOption.IGNORE_CASE), limit = 2)
        val cityName = parts.lastOrNull()?.takeIf { it.isNotBlank() } ?: "Karachi"
        val countryName = if (cityName.equals("Karachi", true) || cityName.equals("Lahore", true) ||
            cityName.equals("Islamabad", true)
        ) "Pakistan" else ""
        val url = buildString {
            append("https://api.aladhan.com/v1/timingsByCity?city=${enc(cityName)}")
            if (countryName.isNotBlank()) append("&country=${enc(countryName)}")
            append("&method=1")
        }
        val timings = Http.getJson(url)?.obj("data")?.obj("timings") ?: return null
        return "🕌 Prayer times for $cityName:\n" + listOf(
            "Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"
        ).joinToString("\n") { "• $it: ${timings.str(it) ?: "—"}" }
    }

    suspend fun cricketScores(apiKey: String): String? {
        if (apiKey.isBlank()) {
            return "🏏 Cricket scores need a free API key: get one at cricapi.com (free tier) and paste it in Settings → Cricket API key."
        }
        val data = Http.getJson("https://api.cricapi.com/v1/currentMatches?apikey=${enc(apiKey)}&offset=0")
            ?: return null
        if (data.str("status") != "success") return "🏏 Cricket service rejected the key. Please check the key in Settings."
        val matches = data.arr("data")?.take(4) ?: return null
        val lines = matches.mapNotNull { m ->
            val name = m.str("name") ?: return@mapNotNull null
            val status = m.str("status") ?: ""
            val score = m.arr("score")?.joinToString("; ") { s ->
                "${s.str("inning") ?: ""}: ${s.str("r") ?: "?"}/${s.str("w") ?: "?"} (${s.str("o") ?: "?"} ov)"
            }
            "• $name\n  $status${score?.let { "\n  $it" } ?: ""}"
        }
        return if (lines.isEmpty()) "🏏 No live matches right now." else "🏏 Live/recent matches:\n${lines.joinToString("\n")}"
    }

    private val currencyWords = mapOf(
        "dollar" to "USD", "dolar" to "USD", "rupee" to "PKR", "rupay" to "PKR", "euro" to "EUR",
        "pound" to "GBP", "riyal" to "SAR", "dirham" to "AED", "yen" to "JPY", "yuan" to "CNY",
        "dinar" to "KWD", "lira" to "TRY", "rupee india" to "INR", "rupiah" to "IDR",
    )

    suspend fun currency(query: String): String? {
        val upper = query.uppercase(Locale.ROOT)
        val codes = Regex("\\b[A-Z]{3}\\b").findAll(upper).map { it.value }
            .filter { it in knownCurrencies }.toList()
        val words = currencyWords.entries.firstOrNull { query.lowercase().contains(it.key) }?.value
        val from = codes.getOrNull(0) ?: words ?: "USD"
        val to = codes.getOrNull(1) ?: if (from == "PKR") "USD" else "PKR"
        val data = Http.getJson("https://open.er-api.com/v6/latest/$from") ?: return null
        val rate = data.obj("rates")?.dbl(to) ?: return null
        return "💱 1 $from = $rate $to (${data.str("last_update")?.take(10) ?: "latest"})."
    }

    private val knownCurrencies = setOf(
        "USD", "PKR", "EUR", "GBP", "INR", "SAR", "AED", "JPY", "CNY", "TRY", "KWD",
        "QAR", "OMR", "BHD", "MYR", "IDR", "AUD", "CAD", "CHF", "SGD", "ZAR", "BDT", "LKR", "AFN", "IRR", "NOK", "SEK", "RUB", "BRL", "MXN",
    )

    private val cryptoNames = mapOf(
        "bitcoin" to "bitcoin", "btc" to "bitcoin", "ethereum" to "ethereum", "eth" to "ethereum",
        "solana" to "solana", "sol" to "solana", "dogecoin" to "dogecoin", "doge" to "dogecoin",
        "xrp" to "ripple", "ripple" to "ripple", "binance" to "binancecoin", "bnb" to "binancecoin",
        "cardano" to "cardano", "ada" to "cardano", "tether" to "tether", "usdt" to "tether",
    )

    suspend fun crypto(query: String): String? {
        val id = cryptoNames.entries.firstOrNull { query.lowercase().contains(it.key) }?.value
            ?: "bitcoin"
        val data = Http.getJson("https://api.coingecko.com/api/v3/simple/price?ids=$id&vs_currencies=usd,pkr&include_24hr_change=true")
            ?: return null
        val entry = data.obj(id) ?: return null
        val usd = entry.dbl("usd") ?: return null
        val change = entry.dbl("usd_24h_change")
        val pkr = entry.dbl("pkr")
        return "₿ ${id.replaceFirstChar { it.uppercase() }}: \$${String.format(Locale.US, "%,.2f", usd)}" +
            (pkr?.let { " (₨${String.format(Locale.US, "%,.0f", it)})" } ?: "") +
            (change?.let { String.format(Locale.US, " — 24h: %+.1f%%", it) } ?: "")
    }

    suspend fun cryptoTrending(): String? {
        val data = Http.getJson("https://api.coingecko.com/api/v3/search/trending") ?: return null
        val coins = data.arr("coins")?.take(5) ?: return null
        val lines = coins.mapNotNull { c ->
            val item = c.obj("item") ?: return@mapNotNull null
            val name = item.str("name") ?: return@mapNotNull null
            val symbol = item.str("symbol") ?: ""
            val rank = item.int("market_cap_rank")
            "• $name ($symbol)${rank?.let { " — rank #$it" } ?: ""}"
        }
        return if (lines.isEmpty()) null else "🔥 Trending crypto:\n${lines.joinToString("\n")}"
    }

    // ---- Fun -----------------------------------------------------------------

    suspend fun joke(): String? {
        val data = Http.getJson("https://v2.jokeapi.dev/joke/Any?safe-mode") ?: return null
        return when (data.str("type")) {
            "single" -> data.str("joke")?.let { "😄 $it" }
            "twopart" -> "😄 ${data.str("setup") ?: return null}\n${data.str("delivery") ?: ""}"
            else -> null
        }
    }

    suspend fun quote(): String? {
        val data = Http.getJson("https://zenquotes.io/api/random")?.arr()?.at(0) ?: return null
        val text = data.str("q") ?: return null
        return "❝ $text — ${data.str("a") ?: "unknown"}"
    }

    suspend fun advice(): String? {
        val data = Http.getJson("https://api.adviceslip.com/advice?ts=${System.currentTimeMillis()}") ?: return null
        return "💡 ${data.obj("slip")?.str("advice") ?: return null}"
    }

    suspend fun randomFact(): String? {
        val data = Http.getJson("https://uselessfacts.jsph.pl/api/v2/facts/random") ?: return null
        return "🤓 ${data.str("text") ?: return null}"
    }

    suspend fun catPicture(): String? {
        val data = Http.getJson("https://cataas.com/cat?json=true") ?: return null
        val id = data.str("_id") ?: return null
        return "🐱 Here is a cat: https://cataas.com/cat/$id"
    }

    suspend fun dogPicture(): String? {
        val data = Http.getJson("https://dog.ceo/api/breeds/image/random") ?: return null
        return "🐶 ${data.str("message") ?: return null}"
    }

    suspend fun xkcd(): String? {
        val data = Http.getJson("https://xkcd.com/info.0.json") ?: return null
        val num = data.int("num") ?: return null
        return "comic #${num}: ${data.str("title") ?: ""}\n${data.str("img") ?: return null}"
    }

    suspend fun pokemon(name: String): String? {
        if (name.isBlank()) return null
        val data = Http.getJson("https://pokeapi.co/api/v2/pokemon/${enc(name.lowercase().trim())}")
            ?: return null
        val id = data.int("id") ?: return null
        val types = data.arr("types")?.mapNotNull { it.obj("type")?.str("name") }.orEmpty()
        val height = data.int("height") ?: 0
        val weight = data.int("weight") ?: 0
        return "⚡ ${name.replaceFirstChar { it.uppercase() }} #$id — types: ${types.joinToString("/")}, " +
            "height ${height / 10.0} m, weight ${weight / 10.0} kg.\n${data.obj("sprites")?.str("front_default") ?: ""}"
    }

    suspend fun issPosition(): String? {
        val data = Http.getJson("https://api.wheretheiss.at/v1/satellites/25544") ?: return null
        val lat = data.dbl("latitude") ?: return null
        val lon = data.dbl("longitude") ?: return null
        val alt = data.dbl("altitude") ?: 0.0
        val vel = data.dbl("velocity") ?: 0.0
        return "🛰 ISS is at ${String.format(Locale.US, "%.2f", lat)}°, ${String.format(Locale.US, "%.2f", lon)}° " +
            "(altitude ${String.format(Locale.US, "%.0f", alt)} km, ${String.format(Locale.US, "%,.0f", vel)} km/h).\n" +
            "https://maps.google.com/?q=$lat,$lon"
    }

    // ---- Utilities -------------------------------------------------------------

    suspend fun timeIn(city: String): String? {
        val clean = city.replace(Regex("^(time|in|ka waqt|kitne baje)\\s*", RegexOption.IGNORE_CASE), "").trim()
        val (lat, lon, label) = geocode(cleanCity(clean).ifBlank { "Karachi" }) ?: return null
        val data = Http.getJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,is_day&timezone=auto"
        ) ?: return null
        val localTime = data.obj("current")?.str("time") ?: return null
        return "🕒 In $label it is ${localTime.take(10)} ${localTime.takeLast(5)} (local time)."
    }

    suspend fun myIp(): String? {
        val data = Http.getJson("https://ipwho.is/") ?: return null
        val isp = data.obj("connection")?.str("isp") ?: "unknown ISP"
        return "🌐 Your IP: ${data.str("ip") ?: return null} — ${data.str("city") ?: "?"}, " +
            "${data.str("country") ?: "?"} ($isp)."
    }

    suspend fun shorten(url: String): String? {
        val clean = url.trim()
        if (!clean.startsWith("http")) return null
        val data = Http.getText("https://is.gd/create.php?format=json&url=${enc(clean)}")
            ?.let { Http.json.parseToJsonElement(it) } ?: return null
        return "🔗 Short link: ${data.str("shorturl") ?: return null}"
    }

    suspend fun whereIs(place: String): String? {
        if (place.isBlank()) return null
        val hit = Http.getJson("https://nominatim.openstreetmap.org/search?q=${enc(place)}&format=json&limit=1")
            ?.arr()?.at(0) ?: return null
        val lat = hit.str("lat") ?: return null
        val lon = hit.str("lon") ?: return null
        return "📍 ${hit.str("display_name") ?: place}\nhttps://maps.google.com/?q=$lat,$lon"
    }

    suspend fun distance(query: String): String? {
        val m = Regex("from\\s+(.+?)\\s+to\\s+(.+)$", RegexOption.IGNORE_CASE).find(query.trim())
            ?: Regex("(.+?)\\s+(?:se|to)\\s+(.+)$", RegexOption.IGNORE_CASE).find(query.trim())
            ?: return null
        val a = geocode(m.groupValues[1].trim()) ?: return null
        val b = geocode(m.groupValues[2].trim()) ?: return null
        val data = Http.getJson(
            "https://router.project-osrm.org/route/v1/driving/${a.second},${a.first};${b.second},${b.first}?overview=false"
        ) ?: return null
        val route = data.arr("routes")?.at(0) ?: return null
        val km = route.dbl("distance")?.div(1000) ?: return null
        val hours = route.dbl("duration")?.div(3600) ?: 0.0
        return "🛣 ${a.third} → ${b.third}: ${String.format(Locale.US, "%.0f", km)} km, " +
            "about ${String.format(Locale.US, "%.1f", hours)} hours by road."
    }

    private val holidayRegions = mapOf(
        "pakistan" to "PK", "india" to "IN", "uae" to "AE", "dubai" to "AE", "usa" to "US",
        "america" to "US", "uk" to "GB", "england" to "GB", "saudi" to "SA", "canada" to "CA",
    )

    suspend fun holidays(query: String): String? {
        val region = holidayRegions.entries.firstOrNull { query.lowercase().contains(it.key) }?.value ?: "PK"
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val list = Http.getJson("https://date.nager.at/api/v3/publicholidays/$year/$region")
            ?.arr() ?: return "🎉 Holiday data not available for this country."
        val today = java.time.LocalDate.now()
        val upcoming = list.mapNotNull { h ->
            val date = java.time.LocalDate.parse(h.str("date") ?: return@mapNotNull null)
            if (date >= today) "• $date — ${h.str("name") ?: ""} / ${h.str("localName") ?: ""}" else null
        }.take(5)
        return if (upcoming.isEmpty()) "🎉 No public holidays left this year." else
            "🎉 Upcoming public holidays:\n${upcoming.joinToString("\n")}"
    }

    suspend fun github(query: String): String? {
        val repoMatch = Regex("repo\\s+([\\w.-]+\\s*/\\s*[\\w.-]+)", RegexOption.IGNORE_CASE).find(query)
        if (repoMatch != null) {
            val slug = repoMatch.groupValues[1].replace(" ", "")
            val data = Http.getJson("https://api.github.com/repos/$slug") ?: return null
            return "🐙 ${data.str("full_name") ?: slug}: ${data.str("description") ?: "no description"}\n" +
                "⭐ ${data.int("stargazers_count") ?: "?"} stars · 🍴 ${data.int("forks_count") ?: "?"} forks · " +
                "language: ${data.str("language") ?: "?"}\n${data.str("html_url") ?: ""}"
        }
        val user = Regex("user\\s+([\\w.-]+)", RegexOption.IGNORE_CASE).find(query)?.groupValues?.get(1)
            ?: query.trim().takeIf { it.matches(Regex("[\\w.-]+")) }
            ?: return null
        val data = Http.getJson("https://api.github.com/users/${enc(user)}") ?: return null
        return "🐙 ${data.str("login") ?: user}: ${data.str("name") ?: ""}\n" +
            "${data.str("bio") ?: "no bio"} · 👥 ${data.int("followers") ?: "?"} followers · " +
            "📦 ${data.int("public_repos") ?: "?"} public repos\n${data.str("html_url") ?: ""}"
    }

    suspend fun stackOverflow(query: String): String? {
        if (query.isBlank()) return null
        val data = Http.getJson(
            "https://api.stackexchange.com/2.3/search/advanced?order=desc&sort=relevance&pagesize=3" +
                "&q=${enc(query)}&site=stackoverflow"
        ) ?: return null
        val items = data.arr("items")?.take(3) ?: return null
        val lines = items.mapNotNull { item ->
            val title = item.str("title") ?: return@mapNotNull null
            "• $title (+${item.int("score") ?: 0}, ${item.int("answer_count") ?: 0} answers)\n  ${item.str("link") ?: ""}"
        }
        return if (lines.isEmpty()) null else "💻 Stack Overflow results:\n${lines.joinToString("\n")}"
    }

    suspend fun quranVerse(): String? {
        val data = Http.getJson("https://api.quran.com/api/v4/verses/random?fields=text_uthmani&translations=131")
            ?.obj("verse") ?: return null
        val arabic = data.str("text_uthmani") ?: return null
        val translation = data.arr("translations")?.at(0)?.str("text").orEmpty()
        val key = data.str("verse_key") ?: ""
        return "☪ $key\n$arabic\n\n$translation"
    }

    /** Research agent: multi-source answer with links (Wikipedia search). */
    suspend fun research(topic: String): String? {
        if (topic.isBlank()) return null
        val data = Http.getJson(
            "https://en.wikipedia.org/w/api.php?action=opensearch&search=${enc(topic)}&limit=3&format=json"
        ) as? kotlinx.serialization.json.JsonArray ?: return null
        val titles = (data.getOrNull(1) as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.str() }.orEmpty()
        val descs = (data.getOrNull(2) as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.str() }.orEmpty()
        val urls = (data.getOrNull(3) as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.str() }.orEmpty()
        if (titles.isEmpty()) return null
        val summary = wikipedia(titles.first())
        val sources = titles.indices.joinToString("\n") { i ->
            "• ${titles[i]}: ${descs.getOrNull(i).orEmpty()}\n  ${urls.getOrNull(i).orEmpty()}"
        }
        return "🔎 Research on \"$topic\":\n\n${summary ?: titles.first()}\n\nSources:\n$sources"
    }
}
