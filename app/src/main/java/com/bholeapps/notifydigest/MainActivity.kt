package com.bholeapps.notifydigest

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.delay
import org.tensorflow.lite.task.text.nlclassifier.NLClassifier
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Orange = Color(0xFFFC8019)
private val OrangeDeep = Color(0xFFE95800)
private val Cream = Color(0xFFFFF4E8)
private val Paper = Color(0xFFFFFBF6)
private val Ink = Color(0xFF171A21)
private val Muted = Color(0xFF727987)
private val Line = Color(0xFFEEDFD0)
private val Green = Color(0xFF1C9B6A)
private val Blue = Color(0xFF2475D6)
private val Red = Color(0xFFE64646)
private val Yellow = Color(0xFFF4B740)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NotifyDigestApp() }
    }
}

class DigestNotificationListener : NotificationListenerService() {
    companion object {
        private var activeService: DigestNotificationListener? = null

        fun cancelNotificationFromShade(key: String): Boolean {
            if (key.isBlank()) return false
            val service = activeService ?: return false
            return runCatching {
                service.cancelNotification(key)
                true
            }.getOrDefault(false)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeService = this
        ListenerHealth.markConnected(applicationContext)
    }

    override fun onListenerDisconnected() {
        if (activeService === this) activeService = null
        ListenerHealth.markDisconnected(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it.toString() }
            .orEmpty()
        val mergedText = listOf(text, bigText, subText, summaryText, textLines)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        if (title.isBlank() && mergedText.isBlank()) {
            ListenerHealth.markIgnored(applicationContext, sbn.packageName)
            return
        }
        val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            ListenerHealth.markIgnored(applicationContext, sbn.packageName)
            return
        }

        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)

        val notificationKey = sbn.key
        val classification = DigestClassifier.classify(applicationContext, title, mergedText, label)
        val item = DigestItem(
            id = notificationKey.ifBlank { "${sbn.packageName}:${sbn.postTime}:${sbn.id}" },
            notificationKey = notificationKey,
            appName = label,
            packageName = sbn.packageName,
            title = title.ifBlank { label },
            text = mergedText,
            timeMillis = sbn.postTime,
            category = classification.category,
            priority = classification.priority
        )
        NotificationStore.add(context = applicationContext, item = item)
        ListenerHealth.markCaptured(applicationContext, label)
    }
}

data class DigestItem(
    val id: String,
    val notificationKey: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timeMillis: Long,
    val category: String,
    val priority: String
)

data class DigestClassification(
    val category: String,
    val priority: String
)

data class TfliteClassificationSignal(
    val category: String?,
    val categoryScore: Float,
    val priority: String?,
    val priorityScore: Float
)

object DigestClassifier {
    private val amountPattern = Regex("""(?:\u20B9|rs\.?|inr)\s?\d+|\b\d+(?:\.\d+)?\s?(?:rs|inr)\b""")
    private val otpPattern = Regex("""\b(?:otp|one time password|verification code|security code)\b|\b\d{4,8}\b.*\b(?:otp|code|verification)\b|\b(?:otp|code|verification)\b.*\b\d{4,8}\b""")
    private val timePattern = Regex("""\b(?:[01]?\d|2[0-3])(?::[0-5]\d)?\s?(?:am|pm)?\b""")
    private val phonePattern = Regex("""\+?\d[\d -]{7,}\d""")

    private val criticalTerms = listOf(
        "otp", "one time password", "verification", "login", "password", "security",
        "urgent", "fraud", "blocked", "suspicious", "unauthorized", "account locked",
        "failed login", "verification code"
    )
    private val promoTerms = listOf(
        "sale", "offer", "coupon", "deal", "discount", "cashback", "subscribe", "promo",
        "shop now", "limited time", "ends tonight", "flat", "reward", "rewards",
        "coins", "streak", "recommended", "trending", "watch now", "discover"
    )

    fun classify(title: String, text: String, app: String): DigestClassification {
        val raw = normalizedInput(title, text, app)
        val categoryScores = listOf(
            CategoryScore("Calls", scoreCalls(raw)),
            CategoryScore("Money", scoreMoney(raw)),
            CategoryScore("Email", scoreEmail(raw)),
            CategoryScore("Messages", scoreMessages(raw)),
            CategoryScore("Orders", scoreOrders(raw)),
            CategoryScore("Calendar", scoreCalendar(raw)),
            CategoryScore("Noise", scoreNoise(raw)),
            CategoryScore("System", scoreSystem(raw))
        )
        val best = categoryScores.maxByOrNull { it.score }
        val category = if ((best?.score ?: 0) >= 2) best?.category ?: "Other" else "Other"

        return DigestClassification(
            category = category,
            priority = semanticPriority(raw, category)
        )
    }

    fun classify(context: Context, title: String, text: String, app: String): DigestClassification {
        val raw = normalizedInput(title, text, app)
        val semantic = classify(title, text, app)
        val modelSignal = OnDeviceTextClassifier.classify(context.applicationContext, raw)
        return mergeModelSignal(semantic, modelSignal)
    }

    fun category(title: String, text: String, app: String): String {
        return classify(title, text, app).category
    }

    fun priority(title: String, text: String, app: String): String {
        return classify(title, text, app).priority
    }

    private fun semanticPriority(raw: String, category: String): String {
        val criticalScore = scoreTerms(raw, criticalTerms) * 3 + if (otpPattern.containsMatchIn(raw)) 5 else 0
        val noiseScore = scoreNoise(raw)
        val transactionScore = scoreTerms(raw, listOf("debited", "credited", "paid", "received", "sent", "spent", "transaction", "txn", "upi", "bank", "card", "wallet")) +
            if (amountPattern.containsMatchIn(raw)) 3 else 0
        val timeBoundScore = scoreTerms(raw, listOf("today", "now", "soon", "starts", "starting", "reminder", "alarm", "meeting", "appointment", "deadline", "due"))

        return when {
            criticalScore >= 3 -> "Priority"
            category == "Calls" && hasAny(raw, listOf("missed call", "incoming call", "voice call", "video call", "call from", "called you", "voicemail")) -> "Priority"
            category == "Money" && transactionScore >= 4 && noiseScore < 4 -> "Priority"
            category == "Calendar" && timeBoundScore >= 2 -> "Priority"
            category == "Orders" && hasAny(raw, listOf("out for delivery", "arriving today", "arriving now", "delivered", "delayed", "cancelled", "boarding", "gate", "pickup", "ride arriving")) -> "Priority"
            noiseScore >= 4 -> "Noise"
            else -> "Later"
        }
    }

    private fun mergeModelSignal(
        semantic: DigestClassification,
        modelSignal: TfliteClassificationSignal?
    ): DigestClassification {
        if (modelSignal == null) return semantic

        val category = if (modelSignal.category != null && modelSignal.categoryScore >= 0.75f) {
            modelSignal.category
        } else {
            semantic.category
        }
        val priority = when {
            semantic.priority == "Priority" -> "Priority"
            modelSignal.priority != null && modelSignal.priorityScore >= 0.80f -> modelSignal.priority
            else -> semantic.priority
        }

        return DigestClassification(category = category, priority = priority)
    }

    private fun normalizedInput(title: String, text: String, app: String): String {
        return "$title $text $app".lowercase(Locale.getDefault()).replace(Regex("\\s+"), " ").trim()
    }

    private fun scoreCalls(raw: String): Int {
        var score = scoreTerms(raw, listOf("missed call", "incoming call", "outgoing call", "phone call", "voice call", "video call", "call from", "called you", "voicemail")) * 3
        score += scoreTerms(raw, listOf("dialer", "truecaller", "caller")) * 2
        if (phonePattern.containsMatchIn(raw) && hasAny(raw, listOf("call", "missed", "caller"))) score += 2
        return score
    }

    private fun scoreMoney(raw: String): Int {
        var score = scoreTerms(raw, listOf("debited", "credited", "paid", "payment", "received", "sent", "spent", "refund", "transaction", "txn", "upi", "bank", "account", "card", "wallet", "balance")) * 2
        if (amountPattern.containsMatchIn(raw)) score += 4
        if (hasAny(raw, listOf("bill due", "invoice", "statement", "autopay", "mandate"))) score += 2
        if (scoreNoise(raw) >= 5 && !amountPattern.containsMatchIn(raw)) score -= 2
        return score.coerceAtLeast(0)
    }

    private fun scoreEmail(raw: String): Int {
        var score = scoreTerms(raw, listOf("gmail", "outlook", "yahoo mail", "email", "e-mail", "mail", "inbox", "subject:", "from:")) * 2
        if ("newsletter" in raw) score += 1
        return score
    }

    private fun scoreMessages(raw: String): Int {
        var score = scoreTerms(raw, listOf("whatsapp", "telegram", "messenger", "sms", "chat", "message", "new message", "sent you", "replied", "dm")) * 2
        if (hasAny(raw, listOf("instagram", "signal", "messages"))) score += 2
        return score
    }

    private fun scoreOrders(raw: String): Int {
        return scoreTerms(
            raw,
            listOf(
                "delivered", "arriving", "out for delivery", "order", "shipment", "delivery",
                "ride", "trip", "ticket", "booking", "pnr", "boarding", "gate", "pickup",
                "zomato", "swiggy", "amazon", "flipkart", "uber", "ola"
            )
        ) * 2
    }

    private fun scoreCalendar(raw: String): Int {
        var score = scoreTerms(raw, listOf("calendar", "meeting", "reminder", "alarm", "event", "schedule", "appointment", "deadline", "due", "starts in")) * 2
        if (timePattern.containsMatchIn(raw) && hasAny(raw, listOf("today", "tomorrow", "meeting", "reminder", "appointment", "event"))) score += 2
        return score
    }

    private fun scoreNoise(raw: String): Int {
        var score = scoreTerms(raw, promoTerms) * 2
        if (hasAny(raw, listOf("newsletter", "marketing", "sponsored", "ad ", "ads ", "promotion"))) score += 2
        return score
    }

    private fun scoreSystem(raw: String): Int {
        return scoreTerms(
            raw,
            listOf(
                "android system", "system ui", "battery", "storage", "download", "update",
                "backup", "permission", "wifi", "bluetooth", "charging", "screenshot",
                "sync", "usb", "do not disturb", "device care"
            )
        ) * 2
    }

    private fun scoreTerms(raw: String, terms: List<String>): Int {
        return terms.count { it in raw }
    }

    private fun hasAny(raw: String, terms: List<String>): Boolean {
        return terms.any { it in raw }
    }

    private data class CategoryScore(
        val category: String,
        val score: Int
    )
}

object OnDeviceTextClassifier {
    private const val MODEL_FILE = "text_classification_v2.tflite"
    private val categoryLabels = mapOf(
        "calls" to "Calls",
        "call" to "Calls",
        "money" to "Money",
        "finance" to "Money",
        "banking" to "Money",
        "email" to "Email",
        "mail" to "Email",
        "messages" to "Messages",
        "message" to "Messages",
        "orders" to "Orders",
        "order" to "Orders",
        "calendar" to "Calendar",
        "event" to "Calendar",
        "noise" to "Noise",
        "promotion" to "Noise",
        "promo" to "Noise",
        "system" to "System"
    )
    private val priorityLabels = mapOf(
        "priority" to "Priority",
        "important" to "Priority",
        "urgent" to "Priority",
        "noise" to "Noise",
        "promotion" to "Noise",
        "promo" to "Noise",
        "normal" to "Later",
        "later" to "Later"
    )

    @Volatile
    private var classifier: NLClassifier? = null
    @Volatile
    private var supportsDigestLabels: Boolean? = null

    fun classify(context: Context, raw: String): TfliteClassificationSignal? {
        if (raw.isBlank()) return null
        if (supportsDigestLabels == false) return null

        return runCatching {
            val categories = getClassifier(context).classify(raw)
            var bestCategory: String? = null
            var bestCategoryScore = 0f
            var bestPriority: String? = null
            var bestPriorityScore = 0f

            categories.forEach { category ->
                val label = category.label.trim().lowercase(Locale.getDefault())
                val score = category.score
                val mappedCategory = categoryLabels[label]
                if (mappedCategory != null && score > bestCategoryScore) {
                    bestCategory = mappedCategory
                    bestCategoryScore = score
                }
                val mappedPriority = priorityLabels[label]
                if (mappedPriority != null && score > bestPriorityScore) {
                    bestPriority = mappedPriority
                    bestPriorityScore = score
                }
            }

            if (bestCategory == null && bestPriority == null) {
                supportsDigestLabels = false
                null
            } else {
                supportsDigestLabels = true
                TfliteClassificationSignal(
                    category = bestCategory,
                    categoryScore = bestCategoryScore,
                    priority = bestPriority,
                    priorityScore = bestPriorityScore
                )
            }
        }.getOrNull()
    }

    private fun getClassifier(context: Context): NLClassifier {
        classifier?.let { return it }
        return synchronized(this) {
            classifier ?: NLClassifier.createFromFile(context, MODEL_FILE).also { classifier = it }
        }
    }
}

data class DailyStats(
    val dateKey: String,
    val totalCount: Int,
    val priorityCount: Int,
    val noiseCount: Int,
    val topAppName: String,
    val topAppCount: Int,
    val yesterdayTotalCount: Int,
    val yesterdayNoiseCount: Int
)

object NotificationStore {
    private const val PREFS = "notify_digest_store"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 180

    fun add(context: Context, item: DigestItem) {
        val items = read(context)
            .filterNot { it.id == item.id || isSemanticDuplicate(it, item) }
            .toMutableList()
        items.add(0, item)
        write(context, dedupe(items).take(MAX_ITEMS))
    }

    fun dismiss(context: Context, id: String) {
        write(context, read(context).filterNot { it.id == id })
    }

    fun dismissMany(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        write(context, read(context).filterNot { it.id in ids })
    }

    fun clearAll(context: Context) {
        write(context, emptyList())
    }

    fun read(context: Context): List<DigestItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val title = item.optString("title")
                val text = item.optString("text")
                val appName = item.optString("appName")
                val classification = DigestClassifier.classify(context, title, text, appName)
                DigestItem(
                    id = item.optString("id"),
                    notificationKey = item.optString("notificationKey", item.optString("id")),
                    appName = appName,
                    packageName = item.optString("packageName"),
                    title = title,
                    text = text,
                    timeMillis = item.optLong("timeMillis"),
                    category = classification.category,
                    priority = classification.priority
                )
            }
        }.map { dedupe(it) }.getOrDefault(emptyList())
    }

    private fun dedupe(items: List<DigestItem>): List<DigestItem> {
        return items.fold(emptyList()) { kept, item ->
            if (kept.any { it.id == item.id || isSemanticDuplicate(it, item) }) kept else kept + item
        }
    }

    private fun isSemanticDuplicate(first: DigestItem, second: DigestItem): Boolean {
        val sameContent = first.packageName == second.packageName &&
            normalized(first.title) == normalized(second.title) &&
            normalized(first.text) == normalized(second.text)
        val closeInTime = abs(first.timeMillis - second.timeMillis) <= 5_000L
        return sameContent && closeInTime
    }

    private fun normalized(value: String): String {
        return value.trim().lowercase().replace(Regex("\\s+"), " ")
    }
    private fun write(context: Context, items: List<DigestItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("notificationKey", item.notificationKey)
                    .put("appName", item.appName)
                    .put("packageName", item.packageName)
                    .put("title", item.title)
                    .put("text", item.text)
                    .put("timeMillis", item.timeMillis)
                    .put("category", item.category)
                    .put("priority", item.priority)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }
}

object ListenerHealth {
    private const val PREFS = "listener_health"
    private const val KEY_CONNECTED = "connected"
    private const val KEY_LAST_EVENT = "last_event"
    private const val KEY_LAST_APP = "last_app"
    private const val KEY_LAST_TIME = "last_time"

    fun markConnected(context: Context) = write(context, true, "Listener connected", "")
    fun markDisconnected(context: Context) = write(context, false, "Listener disconnected", "")
    fun markCaptured(context: Context, appName: String) = write(context, true, "Captured", appName)
    fun markIgnored(context: Context, packageName: String) = write(context, true, "Ignored empty notification", packageName)

    fun read(context: Context): ListenerStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ListenerStatus(
            connected = prefs.getBoolean(KEY_CONNECTED, false),
            lastEvent = prefs.getString(KEY_LAST_EVENT, "Waiting for first notification").orEmpty(),
            lastApp = prefs.getString(KEY_LAST_APP, "").orEmpty(),
            lastTime = prefs.getLong(KEY_LAST_TIME, 0L)
        )
    }

    private fun write(context: Context, connected: Boolean, event: String, appName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONNECTED, connected)
            .putString(KEY_LAST_EVENT, event)
            .putString(KEY_LAST_APP, appName)
            .putLong(KEY_LAST_TIME, System.currentTimeMillis())
            .apply()
    }
}

data class ListenerStatus(
    val connected: Boolean,
    val lastEvent: String,
    val lastApp: String,
    val lastTime: Long
)


@Composable
fun NotifyDigestApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    var showPermissionDialog by remember { mutableStateOf(!hasAccess) }
    var filter by remember { mutableStateOf("All") }
    var appFilter by remember { mutableStateOf(ALL_APPS_FILTER) }
    var items by remember { mutableStateOf(loadInbox(context)) }
    var listenerStatus by remember { mutableStateOf(ListenerHealth.read(context)) }
    var selectedItem by remember { mutableStateOf<DigestItem?>(null) }
    val dailyStats = remember(items) { buildDailyStats(items) }

    fun refreshPermissionState(showPromptWhenMissing: Boolean) {
        val enabled = hasNotificationAccess(context)
        hasAccess = enabled
        showPermissionDialog = showPromptWhenMissing && !enabled
        if (enabled) requestListenerRebind(context)
        items = loadInbox(context)
        listenerStatus = ListenerHealth.read(context)
    }

    LaunchedEffect(Unit) {
        refreshPermissionState(showPromptWhenMissing = true)
    }

    LaunchedEffect(hasAccess) {
        while (true) {
            delay(2_000)
            if (hasAccess) {
                items = loadInbox(context)
                listenerStatus = ListenerHealth.read(context)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionState(showPromptWhenMissing = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFF0DF), Paper, Color(0xFFFFFAF3))))
            ) {
                if (showPermissionDialog && !hasAccess) {
                    NotificationAccessDialog(
                        onEnable = {
                            showPermissionDialog = false
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onLater = { showPermissionDialog = false }
                    )
                }
                selectedItem?.let { item ->
                    NotificationDetailDialog(
                        item = item,
                        onDismiss = { selectedItem = null },
                        onOpen = {
                            openSourceApp(context, item)
                            selectedItem = null
                        },
                        onClear = {
                            markNotificationDone(context, item)
                            selectedItem = null
                            items = loadInbox(context)
                            listenerStatus = ListenerHealth.read(context)
                        }
                    )
                }
                BackgroundBlob()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { TopBar() }
                    item {
                        HeroCard(
                            hasAccess = hasAccess,
                            dailyStats = dailyStats,
                            onEnable = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            onRefresh = {
                                refreshPermissionState(showPromptWhenMissing = true)
                            },
                            onClearTodayNoise = {
                                val todayNoise = items.filter { isToday(it.timeMillis) && isNoise(it) }
                                clearTodayNoiseNotifications(context, todayNoise)
                                items = loadInbox(context)
                                listenerStatus = ListenerHealth.read(context)
                            }
                        )
                    }
                    val filterScopedItems = filteredItems(items, filter)
                    item {
                        FilterRow(
                            selected = filter,
                            items = items,
                            selectedPackage = appFilter,
                            onPick = { filter = it }
                        )
                    }
                    if (items.isNotEmpty()) {
                        item {
                            AppFilterRow(
                                selectedPackage = appFilter,
                                items = items,
                                scopedItems = filterScopedItems,
                                onPick = { appFilter = it }
                            )
                        }
                    }
                    val visible = filteredByApp(filterScopedItems, appFilter)
                    if (items.isNotEmpty()) {
                        item {
                            InboxActions(
                                itemCount = items.size,
                                visibleCount = visible.size,
                                currentFilterLabel = currentFilterLabel(filter, appFilter, items),
                                onClearVisible = {
                                    clearVisibleNotifications(context, visible)
                                    items = loadInbox(context)
                                    listenerStatus = ListenerHealth.read(context)
                                },
                                onClearAll = {
                                    clearAllNotifications(context, items)
                                    items = loadInbox(context)
                                    listenerStatus = ListenerHealth.read(context)
                                }
                            )
                        }
                    }
                    if (visible.isEmpty()) {
                        item { EmptyState(hasAccess) }
                    } else {
                        items(visible, key = { it.id }) { item ->
                            NotificationCard(
                                item = item,
                                onClick = { selectedItem = item },
                                onOpen = { openSourceApp(context, item) },
                                onDismiss = {
                                    markNotificationDone(context, item)
                                    items = loadInbox(context)
                                    listenerStatus = ListenerHealth.read(context)
                                }
                            )
                        }
                    }
                    item { PrivacyCard() }
                    item { Spacer(Modifier.height(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ListenerStatusCard(hasAccess: Boolean, status: ListenerStatus) {
    val statusText = when {
        !hasAccess -> "Permission not enabled"
        status.connected -> "Listener active"
        else -> "Permission enabled. Waiting for Android to connect listener."
    }
    val detail = when {
        status.lastApp.isNotBlank() -> "${status.lastEvent}: ${status.lastApp}"
        status.lastTime > 0L -> status.lastEvent
        else -> "Open notification settings once if this stays stuck."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (hasAccess && status.connected) Green else Orange)
        )
        Column(Modifier.weight(1f)) {
            Text(statusText, color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NotificationAccessDialog(onEnable: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = Color.White,
        title = {
            Text("Turn on notification access", color = Ink, fontWeight = FontWeight.Black)
        },
        text = {
            Text(
                "NotifyDigest needs notification access to build your smart notifications inbox. Everything stays on this phone.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onEnable,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Enable", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("Later", color = Muted, fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconMark(modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("NotifyDigest", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Smart notification inbox", color = Muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeroCard(
    hasAccess: Boolean,
    dailyStats: DailyStats,
    onEnable: () -> Unit,
    onRefresh: () -> Unit,
    onClearTodayNoise: () -> Unit
) {
    var showPermissionTooltip by remember(hasAccess) { mutableStateOf(!hasAccess) }

    LaunchedEffect(hasAccess) {
        if (!hasAccess) {
            showPermissionTooltip = true
            delay(5_000)
            showPermissionTooltip = false
        } else {
            showPermissionTooltip = false
        }
    }

    val topAppLine = if (dailyStats.topAppName.isNotBlank()) {
        "${dailyStats.topAppName} is loudest today with ${dailyStats.topAppCount} alerts."
    } else {
        "No noisy app has shown up yet."
    }
    val noiseScore = if (dailyStats.totalCount == 0) 0 else (dailyStats.noiseCount * 100 / dailyStats.totalCount)
    val comparisonLine = when {
        dailyStats.totalCount == 0 && dailyStats.yesterdayTotalCount == 0 -> "Open once a day. See what disturbed you."
        dailyStats.yesterdayTotalCount == 0 -> "First digest day. Tomorrow, we compare the chaos."
        dailyStats.totalCount > dailyStats.yesterdayTotalCount -> "${dailyStats.totalCount - dailyStats.yesterdayTotalCount} more alerts than yesterday."
        dailyStats.totalCount < dailyStats.yesterdayTotalCount -> "${dailyStats.yesterdayTotalCount - dailyStats.totalCount} fewer alerts than yesterday."
        else -> "Same alert load as yesterday."
    }
    val roast = when {
        dailyStats.totalCount == 0 -> "Quiet phone. Suspiciously peaceful."
        dailyStats.noiseCount == 0 -> "Clean day so far. Your notifications are behaving."
        noiseScore >= 50 -> "Your phone is doing a lot of shouting today."
        else -> "Manageable chaos. We can work with this."
    }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color.White, Color(0xFFFFF7EE))))
                .border(1.dp, Line, RoundedCornerShape(24.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MonsterEatingNotifications(modifier = Modifier.size(48.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Feed me notifications", color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 2)
                    Text("Daily Notification Digest", color = Muted, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                if (!hasAccess) {
                    Button(
                        onClick = onEnable,
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Enable", fontWeight = FontWeight.Black) }
                } else {
                    RefreshIconButton(onClick = onRefresh)
                }
            }
            Text(roast, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactPill("Noise Score $noiseScore", Orange)
                CompactPill("${dailyStats.totalCount} today", Blue)
                CompactPill("${dailyStats.priorityCount} priority", Red)
                CompactPill("${dailyStats.noiseCount} noisy", Orange)
            }
            Text(comparisonLine, color = Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, maxLines = 2)
            Text(topAppLine, color = Ink.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (dailyStats.noiseCount > 0) {
                SmallAction("Clear today's noise", Orange, onClearTodayNoise)
            }
        }
        if (!hasAccess && showPermissionTooltip) {
            PermissionTooltip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 12.dp)
            )
        }
    }
}

@Composable
private fun RefreshIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Orange)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            drawArc(
                color = Color.White,
                startAngle = 35f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
                size = Size(size.width * 0.76f, size.height * 0.76f),
                style = Stroke(width = size.width * 0.13f, cap = StrokeCap.Round)
            )
            val arrow = Path().apply {
                moveTo(size.width * 0.86f, size.height * 0.10f)
                lineTo(size.width * 0.98f, size.height * 0.38f)
                lineTo(size.width * 0.68f, size.height * 0.32f)
                close()
            }
            drawPath(arrow, Color.White)
        }
    }
}

@Composable
private fun PermissionTooltip(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.End
    ) {
        Canvas(
            modifier = Modifier
                .padding(end = 34.dp)
                .size(width = 18.dp, height = 8.dp)
        ) {
            val triangle = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(triangle, Ink)
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Ink)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("?", color = Orange, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Text(
                "Tap Enable. Your digest stays on-device.",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CompactPill(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun InboxActions(
    itemCount: Int,
    visibleCount: Int,
    currentFilterLabel: String,
    onClearVisible: () -> Unit,
    onClearAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("$itemCount saved alerts", color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Text("$visibleCount in $currentFilterLabel", color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (visibleCount > 0 && visibleCount < itemCount) {
            SmallAction("Clear filtered", Orange, onClearVisible)
        }
        SmallAction("Clear all", Red, onClearAll)
    }
}

@Composable
private fun FilterRow(
    selected: String,
    items: List<DigestItem>,
    selectedPackage: String,
    onPick: (String) -> Unit
) {
    val filters = listOf("All", "Priority", "Calls", "Money", "Messages", "Email", "Orders", "Calendar", "Noise", "System")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val active = selected == filter
            val count = filteredByApp(filteredItems(items, filter), selectedPackage).size
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) Ink else Color.White)
                    .border(1.dp, if (active) Ink else Line, RoundedCornerShape(999.dp))
                    .clickable { onPick(filter) }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("$filter $count", color = if (active) Color.White else Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AppFilterRow(
    selectedPackage: String,
    items: List<DigestItem>,
    scopedItems: List<DigestItem>,
    onPick: (String) -> Unit
) {
    val appNames = items
        .groupBy { it.packageName }
        .mapValues { (_, appItems) -> appItems.first().appName }
    val appCounts = scopedItems
        .groupBy { it.packageName }
        .map { (packageName, appItems) -> AppFilterOption(packageName, appItems.first().appName, appItems.size) }
        .sortedWith(compareByDescending<AppFilterOption> { it.count }.thenBy { it.appName.lowercase() })
    val selectedAppName = if (selectedPackage == ALL_APPS_FILTER) {
        "All apps"
    } else {
        appNames[selectedPackage] ?: "Selected app"
    }
    val selectedCount = if (selectedPackage == ALL_APPS_FILTER) {
        scopedItems.size
    } else {
        scopedItems.count { it.packageName == selectedPackage }
    }
    val selectedLabel = "$selectedAppName $selectedCount"
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.TopStart)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .border(1.dp, Line, RoundedCornerShape(20.dp))
                .clickable { expanded = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SourceAppIcon(packageName = selectedPackage, appName = selectedAppName, modifier = Modifier.size(34.dp), accent = Orange)
            Column(Modifier.weight(1f)) {
                Text("Filter by app", color = Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(selectedLabel, color = Ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Change", color = Orange, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All apps ${scopedItems.size}", color = Ink, fontWeight = FontWeight.Bold) },
                leadingIcon = { SourceAppIcon(packageName = ALL_APPS_FILTER, appName = "All", modifier = Modifier.size(28.dp), accent = Orange) },
                onClick = {
                    onPick(ALL_APPS_FILTER)
                    expanded = false
                }
            )
            appCounts.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.appName} ${option.count}", color = Ink, fontWeight = FontWeight.Bold) },
                    leadingIcon = { SourceAppIcon(packageName = option.packageName, appName = option.appName, modifier = Modifier.size(28.dp), accent = Orange) },
                    onClick = {
                        onPick(option.packageName)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SourceAppIcon(packageName: String, appName: String, modifier: Modifier = Modifier, accent: Color = Orange) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching {
            if (packageName == ALL_APPS_FILTER) null else drawableToBitmap(context.packageManager.getApplicationIcon(packageName))
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = appName,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(appName.take(1).ifBlank { "A" }.uppercase(), color = accent, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun NotificationDetailDialog(
    item: DigestItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                Text("${item.appName} - ${formatTime(item.timeMillis)}", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryPill(item.category)
                    PriorityTag(item.priority)
                }
                Text(
                    item.text.ifBlank { "No extra notification text." },
                    color = Ink.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Open app", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) { Text("Clear", color = Red, fontWeight = FontWeight.Bold) }
                TextButton(onClick = onDismiss) { Text("Close", color = Muted, fontWeight = FontWeight.Bold) }
            }
        }
    )
}

@Composable
private fun NotificationCard(item: DigestItem, onClick: () -> Unit, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val accent = when (item.category) {
        "Money" -> Green
        "Calls" -> Color(0xFF0F8B8D)
        "Messages" -> Blue
        "Email" -> Color(0xFFD94A38)
        "Orders" -> Yellow
        "Calendar" -> Color(0xFF7E57C2)
        "Noise" -> Orange
        "System" -> Color(0xFF607D8B)
        else -> Ink
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                SourceAppIcon(packageName = item.packageName, appName = item.appName, modifier = Modifier.size(42.dp), accent = accent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.appName} - ${formatTime(item.timeMillis)}", color = Muted, style = MaterialTheme.typography.labelMedium)
            }
            PriorityTag(item.priority)
        }
        if (item.text.isNotBlank()) {
            Text(item.text, color = Ink.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        CategoryPill(item.category)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallAction("Open app", Orange, onOpen)
            SmallAction("Clear", Ink, onDismiss)
        }
    }
}

@Composable
private fun CategoryPill(category: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Cream)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("Category: " + category, color = OrangeDeep, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
    }
}






@Composable
private fun PriorityTag(priority: String) {
    val color = when (priority) {
        "Priority" -> Red
        "Noise" -> Orange
        else -> Blue
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(displayPriority(priority), color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SmallAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState(hasAccess: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(28.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MonsterEatingNotifications(modifier = Modifier.size(96.dp))
        Text(if (hasAccess) "No notifications yet" else "Notification access needed", color = Ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            if (hasAccess) "Real notifications will appear here after they arrive. Pull refresh to reload the inbox." else "Enable notification access to start capturing real alerts. No dummy cards are shown.",
            color = Muted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PrivacyCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Cream)
            .border(1.dp, Line, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text("Private by design", color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text("Your notification digest stays on this phone. No account, no cloud upload, no selling your alerts.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackgroundBlob() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(Orange.copy(alpha = 0.11f), radius = 280f, center = Offset(size.width * 0.94f, size.height * 0.08f))
        drawCircle(Color(0xFFFFCF9B).copy(alpha = 0.24f), radius = 190f, center = Offset(size.width * 0.06f, size.height * 0.36f))
    }
}

@Composable
private fun MonsterEatingNotifications(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "monster")
    val pulse by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "monster-pulse"
    )
    Image(
        painter = painterResource(id = R.drawable.notifydigest_app_icon),
        contentDescription = "NotifyDigest mascot",
        modifier = modifier
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .clip(RoundedCornerShape(24.dp)),
        contentScale = ContentScale.Crop
    )
}


@Composable
private fun AppIconMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.notifydigest_app_icon),
        contentDescription = "NotifyDigest",
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun StatPill(label: String, background: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, color = textColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
    }
}

private const val ALL_APPS_FILTER = "__all_apps__"

data class AppFilterOption(
    val packageName: String,
    val appName: String,
    val count: Int
)

private fun displayPriority(priority: String): String {
    return when (priority) {
        "Later" -> "Normal"
        else -> priority
    }
}

private fun filteredItems(items: List<DigestItem>, filter: String): List<DigestItem> {
    return when (filter) {
        "Priority" -> items.filter { it.priority == "Priority" }
        "Noise" -> items.filter { isNoise(it) }
        "All" -> items
        else -> items.filter { it.category == filter }
    }
}

private fun isNoise(item: DigestItem): Boolean {
    return item.priority == "Noise" || item.category == "Noise"
}

private fun filteredByApp(items: List<DigestItem>, packageName: String): List<DigestItem> {
    return if (packageName == ALL_APPS_FILTER) items else items.filter { it.packageName == packageName }
}

private fun currentFilterLabel(filter: String, appFilter: String, items: List<DigestItem>): String {
    val appLabel = if (appFilter == ALL_APPS_FILTER) {
        "all apps"
    } else {
        items.firstOrNull { it.packageName == appFilter }?.appName ?: "selected app"
    }
    return if (filter == "All") appLabel else "$filter / $appLabel"
}

private fun buildDailyStats(items: List<DigestItem>): DailyStats {
    val now = System.currentTimeMillis()
    val today = dayKey(now)
    val yesterday = dayKey(now - 24L * 60L * 60L * 1000L)
    val todayItems = items.filter { dayKey(it.timeMillis) == today }
    val yesterdayItems = items.filter { dayKey(it.timeMillis) == yesterday }
    val topApp = todayItems
        .groupingBy { it.appName }
        .eachCount()
        .maxByOrNull { it.value }

    return DailyStats(
        dateKey = today,
        totalCount = todayItems.size,
        priorityCount = todayItems.count { it.priority == "Priority" },
        noiseCount = todayItems.count { isNoise(it) },
        topAppName = topApp?.key.orEmpty(),
        topAppCount = topApp?.value ?: 0,
        yesterdayTotalCount = yesterdayItems.size,
        yesterdayNoiseCount = yesterdayItems.count { isNoise(it) }
    )
}

private fun loadInbox(context: Context): List<DigestItem> {
    return NotificationStore.read(context)
}

private fun clearVisibleNotifications(context: Context, items: List<DigestItem>) {
    items.forEach { item ->
        DigestNotificationListener.cancelNotificationFromShade(item.notificationKey)
    }
    NotificationStore.dismissMany(context, items.map { it.id }.toSet())
    Toast.makeText(context, "Cleared filtered notifications", Toast.LENGTH_SHORT).show()
}

private fun clearTodayNoiseNotifications(context: Context, items: List<DigestItem>) {
    if (items.isEmpty()) {
        Toast.makeText(context, "No noise to clear right now", Toast.LENGTH_SHORT).show()
        return
    }
    items.forEach { item ->
        DigestNotificationListener.cancelNotificationFromShade(item.notificationKey)
    }
    NotificationStore.dismissMany(context, items.map { it.id }.toSet())
    Toast.makeText(context, "Cleared today's noisy alerts", Toast.LENGTH_SHORT).show()
}

private fun clearAllNotifications(context: Context, items: List<DigestItem>) {
    items.forEach { item ->
        DigestNotificationListener.cancelNotificationFromShade(item.notificationKey)
    }
    NotificationStore.clearAll(context)
    Toast.makeText(context, "Cleared all saved notifications", Toast.LENGTH_SHORT).show()
}


private fun markNotificationDone(context: Context, item: DigestItem) {
    val clearedFromShade = DigestNotificationListener.cancelNotificationFromShade(item.notificationKey)
    NotificationStore.dismiss(context, item.id)
    val message = if (clearedFromShade) {
        "Cleared from inbox and notification shade"
    } else {
        "Cleared from NotifyDigest inbox"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun openSourceApp(context: Context, item: DigestItem) {
    DigestNotificationListener.cancelNotificationFromShade(item.notificationKey)
    val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
    if (intent == null) {
        Toast.makeText(context, "Source app is not installed", Toast.LENGTH_SHORT).show()
    } else {
        context.startActivity(intent)
    }
}

private fun requestListenerRebind(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        runCatching {
            NotificationListenerService.requestRebind(ComponentName(context, DigestNotificationListener::class.java))
        }
    }
}

private fun hasNotificationAccess(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val expected = ComponentName(context, DigestNotificationListener::class.java).flattenToString()
    return flat.split(':').any { it.equals(expected, ignoreCase = true) }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

private fun dayKey(timeMillis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timeMillis))
}

private fun isToday(timeMillis: Long): Boolean {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = timeMillis }
    return now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) &&
        now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun formatTime(timeMillis: Long): String {
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeMillis))
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun NotifyDigestPreview() {
    NotifyDigestApp()
}
