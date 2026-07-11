package com.bholeapps.notifydigest

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.composed
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
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Modifier.hapticClick(onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    clickable {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

@Composable
private fun hapticAction(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

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
        val notificationLines = extractNotificationLines(sbn.notification)
        val mergedText = listOf(text, bigText, subText, summaryText, textLines)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { notificationLines.joinToString("\n") }
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
        val itemId = notificationKey.ifBlank { "${sbn.packageName}:${sbn.postTime}:${sbn.id}" }
        val classification = DigestClassifier.classify(applicationContext, title, mergedText, label)
        val notificationIconPath = saveNotificationBitmap(
            context = applicationContext,
            itemId = itemId,
            kind = "icon",
            bitmap = extractNotificationIconBitmap(applicationContext, sbn.notification),
            maxDimension = 192
        )
        val contentImagePath = saveNotificationBitmap(
            context = applicationContext,
            itemId = itemId,
            kind = "image",
            bitmap = extractNotificationPictureBitmap(applicationContext, sbn.notification),
            maxDimension = 720
        )
        val item = DigestItem(
            id = itemId,
            notificationKey = notificationKey,
            appName = label,
            packageName = sbn.packageName,
            title = title.ifBlank { label },
            text = mergedText,
            timeMillis = sbn.postTime,
            category = classification.category,
            priority = classification.priority,
            notificationIconPath = notificationIconPath.orEmpty(),
            contentImagePath = contentImagePath.orEmpty(),
            lines = notificationLines
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
    val priority: String,
    val notificationIconPath: String = "",
    val contentImagePath: String = "",
    val lines: List<String> = emptyList()
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
    val yesterdayNoiseCount: Int,
    val appCounts: Map<String, Int>,
    val priorityAppCounts: Map<String, Int>,
    val noiseAppCounts: Map<String, Int>
)

private data class PersistedDailyStats(
    val dateKey: String,
    val totalCount: Int,
    val priorityCount: Int,
    val noiseCount: Int,
    val topAppName: String,
    val topAppCount: Int,
    val yesterdayTotalCount: Int,
    val yesterdayNoiseCount: Int,
    val appCounts: Map<String, Int>,
    val priorityAppCounts: Map<String, Int>,
    val noiseAppCounts: Map<String, Int>
) {
    companion object {
        fun empty(dateKey: String) = PersistedDailyStats(
            dateKey = dateKey,
            totalCount = 0,
            priorityCount = 0,
            noiseCount = 0,
            topAppName = "",
            topAppCount = 0,
            yesterdayTotalCount = 0,
            yesterdayNoiseCount = 0,
            appCounts = emptyMap(),
            priorityAppCounts = emptyMap(),
            noiseAppCounts = emptyMap()
        )
    }

    fun toDailyStats() = DailyStats(
        dateKey = dateKey,
        totalCount = totalCount,
        priorityCount = priorityCount,
        noiseCount = noiseCount,
        topAppName = topAppName,
        topAppCount = topAppCount,
        yesterdayTotalCount = yesterdayTotalCount,
        yesterdayNoiseCount = yesterdayNoiseCount,
        appCounts = appCounts,
        priorityAppCounts = priorityAppCounts,
        noiseAppCounts = noiseAppCounts
    )
}

object NotificationStore {
    private const val PREFS = "notify_digest_store"
    private const val KEY_ITEMS = "items"
    private const val KEY_DAILY_STATS = "daily_stats"
    private const val MAX_ITEMS = 180

    fun add(context: Context, item: DigestItem) {
        val existingItems = read(context)
        val duplicateIndex = existingItems.indexOfFirst { it.id == item.id || isSemanticDuplicate(it, item) }
        if (duplicateIndex >= 0) {
            val existing = existingItems[duplicateIndex]
            val updated = existing.copy(
                notificationIconPath = existing.notificationIconPath.ifBlank { item.notificationIconPath },
                contentImagePath = existing.contentImagePath.ifBlank { item.contentImagePath },
                lines = existing.lines.ifEmpty { item.lines }
            )
            if (updated != existing) {
                write(context, existingItems.toMutableList().also { it[duplicateIndex] = updated })
            }
            deleteUnusedNotificationMedia(context, item, updated)
            return
        }

        recordDailyStat(context, item)
        val items = existingItems.toMutableList()
        items.add(0, item)
        val deduped = dedupe(items)
        val kept = deduped.take(MAX_ITEMS)
        deleteNotificationMedia(context, deduped.drop(MAX_ITEMS))
        write(context, kept)
    }

    fun dismiss(context: Context, id: String) {
        val current = read(context)
        val removed = current.filter { it.id == id }
        write(context, current.filterNot { it.id == id })
        deleteNotificationMedia(context, removed)
    }

    fun dismissMany(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        val current = read(context)
        val removed = current.filter { it.id in ids }
        write(context, current.filterNot { it.id in ids })
        deleteNotificationMedia(context, removed)
    }

    fun clearAll(context: Context) {
        write(context, emptyList())
        clearNotificationMedia(context)
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
                val linesJson = item.optJSONArray("lines") ?: JSONArray()
                DigestItem(
                    id = item.optString("id"),
                    notificationKey = item.optString("notificationKey", item.optString("id")),
                    appName = appName,
                    packageName = item.optString("packageName"),
                    title = title,
                    text = text,
                    timeMillis = item.optLong("timeMillis"),
                    category = classification.category,
                    priority = classification.priority,
                    notificationIconPath = item.optString("notificationIconPath"),
                    contentImagePath = item.optString("contentImagePath"),
                    lines = List(linesJson.length()) { lineIndex -> linesJson.optString(lineIndex) }
                        .filter { it.isNotBlank() }
                )
            }
        }.map { dedupe(it) }.getOrDefault(emptyList())
    }

    fun readDailyStats(context: Context, visibleItems: List<DigestItem>): DailyStats {
        val persisted = readPersistedDailyStats(context)
        val visible = buildDailyStats(visibleItems)
        return when {
            visible.totalCount > persisted.totalCount -> {
                val reconciled = persistedFromVisibleStats(visible, visibleItems)
                writeDailyStats(context, reconciled)
                reconciled.toDailyStats()
            }
            persisted.totalCount > 0 -> persisted.toDailyStats()
            else -> visible
        }
    }

    private fun recordDailyStat(context: Context, item: DigestItem) {
        val stats = readPersistedDailyStats(context)
        val itemDay = dayKey(item.timeMillis)
        val today = dayKey(System.currentTimeMillis())
        if (itemDay != today) return

        val appCounts = stats.appCounts.toMutableMap()
        appCounts[item.appName] = (appCounts[item.appName] ?: 0) + 1
        val isSameDay = stats.dateKey == today
        val isPriority = item.priority == "Priority"
        val isNoisy = isNoise(item)
        val priorityAppCounts = stats.priorityAppCounts.toMutableMap()
        if (isPriority) priorityAppCounts[item.appName] = (priorityAppCounts[item.appName] ?: 0) + 1
        val noiseAppCounts = stats.noiseAppCounts.toMutableMap()
        if (isNoisy) noiseAppCounts[item.appName] = (noiseAppCounts[item.appName] ?: 0) + 1
        val topApp = appCounts.maxByOrNull { it.value }
        val updated = PersistedDailyStats(
            dateKey = today,
            totalCount = if (isSameDay) stats.totalCount + 1 else 1,
            priorityCount = if (isSameDay) stats.priorityCount + if (isPriority) 1 else 0 else if (isPriority) 1 else 0,
            noiseCount = if (isSameDay) stats.noiseCount + if (isNoisy) 1 else 0 else if (isNoisy) 1 else 0,
            topAppName = topApp?.key.orEmpty(),
            topAppCount = topApp?.value ?: 0,
            yesterdayTotalCount = if (stats.dateKey == today) stats.yesterdayTotalCount else stats.totalCount,
            yesterdayNoiseCount = if (stats.dateKey == today) stats.yesterdayNoiseCount else stats.noiseCount,
            appCounts = appCounts,
            priorityAppCounts = priorityAppCounts,
            noiseAppCounts = noiseAppCounts
        )
        writeDailyStats(context, updated)
    }

    private fun readPersistedDailyStats(context: Context): PersistedDailyStats {
        val today = dayKey(System.currentTimeMillis())
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DAILY_STATS, "").orEmpty()
        val parsed = runCatching {
            val json = JSONObject(raw)
            PersistedDailyStats(
                dateKey = json.optString("dateKey"),
                totalCount = json.optInt("totalCount"),
                priorityCount = json.optInt("priorityCount"),
                noiseCount = json.optInt("noiseCount"),
                topAppName = json.optString("topAppName"),
                topAppCount = json.optInt("topAppCount"),
                yesterdayTotalCount = json.optInt("yesterdayTotalCount"),
                yesterdayNoiseCount = json.optInt("yesterdayNoiseCount"),
                appCounts = readCountMap(json.optJSONObject("appCounts")),
                priorityAppCounts = readCountMap(json.optJSONObject("priorityAppCounts")),
                noiseAppCounts = readCountMap(json.optJSONObject("noiseAppCounts"))
            )
        }.getOrDefault(PersistedDailyStats.empty(today))

        return if (parsed.dateKey == today) {
            parsed
        } else {
            PersistedDailyStats.empty(today).copy(
                yesterdayTotalCount = parsed.totalCount,
                yesterdayNoiseCount = parsed.noiseCount
            ).also { writeDailyStats(context, it) }
        }
    }

    private fun writeDailyStats(context: Context, stats: PersistedDailyStats) {
        val json = JSONObject()
            .put("dateKey", stats.dateKey)
            .put("totalCount", stats.totalCount)
            .put("priorityCount", stats.priorityCount)
            .put("noiseCount", stats.noiseCount)
            .put("topAppName", stats.topAppName)
            .put("topAppCount", stats.topAppCount)
            .put("yesterdayTotalCount", stats.yesterdayTotalCount)
            .put("yesterdayNoiseCount", stats.yesterdayNoiseCount)
            .put("appCounts", writeCountMap(stats.appCounts))
            .put("priorityAppCounts", writeCountMap(stats.priorityAppCounts))
            .put("noiseAppCounts", writeCountMap(stats.noiseAppCounts))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DAILY_STATS, json.toString())
            .apply()
    }

    private fun readCountMap(json: JSONObject?): Map<String, Int> {
        if (json == null) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        json.keys().forEach { key -> counts[key] = json.optInt(key) }
        return counts
    }

    private fun writeCountMap(counts: Map<String, Int>): JSONObject {
        val json = JSONObject()
        counts.forEach { (appName, count) -> json.put(appName, count) }
        return json
    }

    private fun persistedFromVisibleStats(stats: DailyStats, visibleItems: List<DigestItem>): PersistedDailyStats {
        val appCounts = visibleItems
            .filter { dayKey(it.timeMillis) == stats.dateKey }
            .groupingBy { it.appName }
            .eachCount()
        val priorityAppCounts = visibleItems
            .filter { dayKey(it.timeMillis) == stats.dateKey && it.priority == "Priority" }
            .groupingBy { it.appName }
            .eachCount()
        val noiseAppCounts = visibleItems
            .filter { dayKey(it.timeMillis) == stats.dateKey && isNoise(it) }
            .groupingBy { it.appName }
            .eachCount()
        return PersistedDailyStats(
            dateKey = stats.dateKey,
            totalCount = stats.totalCount,
            priorityCount = stats.priorityCount,
            noiseCount = stats.noiseCount,
            topAppName = stats.topAppName,
            topAppCount = stats.topAppCount,
            yesterdayTotalCount = stats.yesterdayTotalCount,
            yesterdayNoiseCount = stats.yesterdayNoiseCount,
            appCounts = appCounts,
            priorityAppCounts = priorityAppCounts,
            noiseAppCounts = noiseAppCounts
        )
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
                    .put("notificationIconPath", item.notificationIconPath)
                    .put("contentImagePath", item.contentImagePath)
                    .put("lines", JSONArray(item.lines))
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
    var dailyStats by remember { mutableStateOf(NotificationStore.readDailyStats(context, items)) }
    val expandedCards = remember { mutableStateMapOf<String, Boolean>() }

    fun refreshPermissionState(showPromptWhenMissing: Boolean) {
        val enabled = hasNotificationAccess(context)
        hasAccess = enabled
        showPermissionDialog = showPromptWhenMissing && !enabled
        if (enabled) requestListenerRebind(context)
        items = loadInbox(context)
        dailyStats = NotificationStore.readDailyStats(context, items)
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
                dailyStats = NotificationStore.readDailyStats(context, items)
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
                            dailyStats = NotificationStore.readDailyStats(context, items)
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
                            items = items,
                            onEnable = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            onRefresh = {
                                refreshPermissionState(showPromptWhenMissing = true)
                            }
                        )
                    }
                    val filterScopedItems = filteredItems(items, filter)
                    item {
                        FilterRow(
                            selected = filter,
                            items = items,
                            selectedPackage = appFilter,
                            scopedItems = filterScopedItems,
                            appFilter = appFilter,
                            onPick = { filter = it },
                            onPickApp = { appFilter = it }
                        )
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
                                    dailyStats = NotificationStore.readDailyStats(context, items)
                                    listenerStatus = ListenerHealth.read(context)
                                },
                                onClearAll = {
                                    clearAllNotifications(context, items)
                                    items = loadInbox(context)
                                    dailyStats = NotificationStore.readDailyStats(context, items)
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
                                expanded = expandedCards[item.id] == true,
                                onToggleExpanded = { expandedCards[item.id] = expandedCards[item.id] != true },
                                onClick = { selectedItem = item },
                                onOpen = { openSourceApp(context, item) },
                                onDismiss = {
                                    markNotificationDone(context, item)
                                    items = loadInbox(context)
                                    dailyStats = NotificationStore.readDailyStats(context, items)
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
    val enableWithHaptic = hapticAction(onEnable)
    val laterWithHaptic = hapticAction(onLater)
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
                onClick = enableWithHaptic,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Enable", fontWeight = FontWeight.Black) }
        },
        dismissButton = {
            TextButton(onClick = laterWithHaptic) { Text("Later", color = Muted, fontWeight = FontWeight.Bold) }
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
    items: List<DigestItem>,
    onEnable: () -> Unit,
    onRefresh: () -> Unit
) {
    var showPermissionTooltip by remember(hasAccess) { mutableStateOf(!hasAccess) }
    var infoDialog by remember { mutableStateOf<StatInfo?>(null) }

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
                    val enableWithHaptic = hapticAction(onEnable)
                    Button(
                        onClick = enableWithHaptic,
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
                CompactPill("Noise Score $noiseScore", Orange) {
                    infoDialog = StatInfo.NoiseScore
                }
                CompactPill("${dailyStats.totalCount} today", Blue) {
                    infoDialog = StatInfo.Today
                }
                CompactPill("${dailyStats.priorityCount} priority", Red) {
                    infoDialog = StatInfo.Priority
                }
                CompactPill("${dailyStats.noiseCount} noisy", Orange) {
                    infoDialog = StatInfo.Noisy
                }
            }
            Text(comparisonLine, color = Ink, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black, maxLines = 2)
            Text(topAppLine, color = Ink.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
        if (!hasAccess && showPermissionTooltip) {
            PermissionTooltip(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 12.dp)
            )
        }
    }
    infoDialog?.let { statInfo ->
        StatInfoDialog(
            info = statInfo,
            stats = dailyStats,
            items = items,
            onDismiss = { infoDialog = null }
        )
    }
}

@Composable
private fun RefreshIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Orange)
            .hapticClick(onClick),
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

private sealed class StatInfo {
    data object NoiseScore : StatInfo()
    data object Today : StatInfo()
    data object Priority : StatInfo()
    data object Noisy : StatInfo()
}

@Composable
private fun StatInfoDialog(
    info: StatInfo,
    stats: DailyStats,
    items: List<DigestItem>,
    onDismiss: () -> Unit
) {
    val title = when (info) {
        StatInfo.NoiseScore -> "Noise Score"
        StatInfo.Today -> "Today's alerts"
        StatInfo.Priority -> "Priority alerts"
        StatInfo.Noisy -> "Noisy alerts"
    }
    val body = when (info) {
        StatInfo.NoiseScore -> "Noise Score is the share of today's alerts marked noisy. Formula: noisy alerts / total alerts x 100. Today: ${stats.noiseCount} / ${stats.totalCount} = ${if (stats.totalCount == 0) 0 else stats.noiseCount * 100 / stats.totalCount}."
        StatInfo.Today -> "Total notifications captured today across all apps."
        StatInfo.Priority -> "Priority alerts are notifications classified as important, urgent, security-sensitive, money-related, OTP/login, calls, or calendar-like reminders."
        StatInfo.Noisy -> "Noisy alerts are low-value or interruptive notifications, such as promotions, repeated app nudges, some system chatter, or anything classified as Noise."
    }
    val appBreakdownCounts = when (info) {
        StatInfo.NoiseScore, StatInfo.Today -> stats.appCounts
        StatInfo.Priority -> stats.priorityAppCounts
        StatInfo.Noisy -> stats.noiseAppCounts
    }
    val appBreakdown = remember(info, stats) {
        appBreakdownCounts.toList().sortedByDescending { it.second }.take(5)
    }
    val expectedBreakdownCount = when (info) {
        StatInfo.NoiseScore, StatInfo.Today -> stats.totalCount
        StatInfo.Priority -> stats.priorityCount
        StatInfo.Noisy -> stats.noiseCount
    }
    val capturedBreakdownCount = appBreakdownCounts.values.sum()
    val hasCompleteBreakdown = expectedBreakdownCount == 0 || capturedBreakdownCount >= expectedBreakdownCount
    val closeWithHaptic = hapticAction(onDismiss)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(body, color = Ink.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium)
                if (appBreakdown.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Top apps", color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        appBreakdown.forEach { (appName, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(appName, color = Ink.copy(alpha = 0.82f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("$count", color = Muted, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        if (!hasCompleteBreakdown) {
                            Text(
                                "Detailed app breakdown is being retained from new alerts now.",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else if (expectedBreakdownCount > 0) {
                    Text("Detailed app breakdown is being retained from new alerts now.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("No matching alerts yet today.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = closeWithHaptic) {
                Text("Got it", color = Orange, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
private fun CompactPill(label: String, color: Color, onClick: (() -> Unit)? = null) {
    val modifier = Modifier
        .clip(RoundedCornerShape(999.dp))
        .background(color.copy(alpha = 0.10f))
        .then(if (onClick != null) Modifier.hapticClick(onClick) else Modifier)
        .padding(horizontal = 9.dp, vertical = 5.dp)
    Box(
        modifier = modifier
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
    scopedItems: List<DigestItem>,
    appFilter: String,
    onPick: (String) -> Unit,
    onPickApp: (String) -> Unit
) {
    val filters = listOf("All", "Priority", "Calls", "Money", "Messages", "Email", "Orders", "Calendar", "Noise", "System")
    val appNames = items
        .groupBy { it.packageName }
        .mapValues { (_, appItems) -> appItems.first().appName }
    val appCounts = scopedItems
        .groupBy { it.packageName }
        .map { (packageName, appItems) -> AppFilterOption(packageName, appItems.first().appName, appItems.size) }
        .sortedWith(compareByDescending<AppFilterOption> { it.count }.thenBy { it.appName.lowercase() })
    val selectedAppName = if (appFilter == ALL_APPS_FILTER) "All apps" else appNames[appFilter] ?: "Selected app"
    val selectedCount = if (appFilter == ALL_APPS_FILTER) scopedItems.size else scopedItems.count { it.packageName == appFilter }
    var appMenuExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                FilterChip(
                    label = "$selectedAppName $selectedCount",
                    active = appFilter != ALL_APPS_FILTER,
                    onClick = { appMenuExpanded = true },
                    leading = "A"
                )
                DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All apps ${scopedItems.size}", color = Ink, fontWeight = FontWeight.Bold) },
                        leadingIcon = { SourceAppIcon(packageName = ALL_APPS_FILTER, appName = "All", modifier = Modifier.size(28.dp), accent = Orange) },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPickApp(ALL_APPS_FILTER)
                            appMenuExpanded = false
                        }
                    )
                    appCounts.forEach { option ->
                        DropdownMenuItem(
                            text = { Text("${option.appName} ${option.count}", color = Ink, fontWeight = FontWeight.Bold) },
                            leadingIcon = { SourceAppIcon(packageName = option.packageName, appName = option.appName, modifier = Modifier.size(28.dp), accent = Orange) },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPickApp(option.packageName)
                                appMenuExpanded = false
                            }
                        )
                    }
                }
            }
            filters.forEach { filter ->
                val active = selected == filter
                val count = filteredByApp(filteredItems(items, filter), selectedPackage).size
                FilterChip(label = "$filter $count", active = active, onClick = { onPick(filter) })
            }
        }
        if (appFilter != ALL_APPS_FILTER) {
            Text(
                "Filtered by $selectedAppName",
                color = Muted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    leading: String? = null
) {
    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) Ink else Color.White.copy(alpha = 0.82f))
            .border(1.dp, if (active) Ink else Line, RoundedCornerShape(999.dp))
            .hapticClick(onClick)
            .padding(horizontal = if (leading == null) 14.dp else 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Color.White.copy(alpha = 0.16f) else Cream),
                contentAlignment = Alignment.Center
            ) {
                Text(leading, color = if (active) Color.White else Orange, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(label, color = if (active) Color.White else Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
private fun NotificationSourceIcon(item: DigestItem, modifier: Modifier = Modifier, accent: Color = Orange) {
    val notificationBitmap = rememberBitmap(item.notificationIconPath)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (notificationBitmap != null) {
            Image(
                bitmap = notificationBitmap.asImageBitmap(),
                contentDescription = item.appName,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                SourceAppIcon(
                    packageName = item.packageName,
                    appName = item.appName,
                    modifier = Modifier.fillMaxSize(),
                    accent = accent
                )
            }
        } else {
            SourceAppIcon(
                packageName = item.packageName,
                appName = item.appName,
                modifier = Modifier.fillMaxSize(),
                accent = accent
            )
        }
    }
}

@Composable
private fun NotificationContentImage(path: String, modifier: Modifier = Modifier) {
    val bitmap = rememberBitmap(path) ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Notification image",
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Line),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun rememberBitmap(path: String): Bitmap? {
    return remember(path) {
        path.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }
    }
}

@Composable
private fun NotificationDetailDialog(
    item: DigestItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val canOpenApp = remember(item.packageName) { hasLaunchableSourceApp(context, item) }
    val openWithHaptic = hapticAction(onOpen)
    val clearWithHaptic = hapticAction(onClear)
    val dismissWithHaptic = hapticAction(onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NotificationSourceIcon(item = item, modifier = Modifier.size(50.dp), accent = categoryAccent(item.category))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        color = Ink,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${item.appName} - ${formatTime(item.timeMillis)}", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
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
                NotificationContentImage(
                    path = item.contentImagePath,
                    modifier = Modifier.height(220.dp)
                )
                displayNotificationLines(item).forEach { line ->
                    SenderMessageLine(line)
                }
            }
        },
        confirmButton = {
            if (canOpenApp) {
                Button(
                    onClick = openWithHaptic,
                    colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Open app", fontWeight = FontWeight.Black) }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = clearWithHaptic) { Text("Clear", color = Red, fontWeight = FontWeight.Bold) }
                TextButton(onClick = dismissWithHaptic) { Text("Close", color = Muted, fontWeight = FontWeight.Bold) }
            }
        }
    )
}

@Composable
private fun NotificationCard(
    item: DigestItem,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val canOpenApp = remember(item.packageName) { hasLaunchableSourceApp(context, item) }
    val accent = categoryAccent(item.category)
    val displayLines = remember(item.lines, item.text) { displayNotificationLines(item) }
    val previewLine = displayLines.firstOrNull() ?: item.text
    val toggleExpanded = {
        onToggleExpanded()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White)
            .border(1.dp, Line, RoundedCornerShape(26.dp))
            .hapticClick(onClick = toggleExpanded)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NotificationSourceIcon(item = item, modifier = Modifier.size(42.dp), accent = accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = Ink,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.appName} - ${formatTime(item.timeMillis)}",
                    color = Muted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(8.dp))
            ExpandCollapseButton(expanded = expanded, onClick = toggleExpanded)
        }
        if (expanded) {
            Column(
                modifier = Modifier.hapticClick(onClick = onClick),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                displayLines.forEach { line ->
                    SenderMessageLine(line)
                }
            }
            NotificationContentImage(
                path = item.contentImagePath,
                modifier = Modifier
                    .height(150.dp)
                    .hapticClick(onClick = onClick)
            )
            CategoryPill(item.category)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canOpenApp) {
                    SmallAction("Open app", Orange, onOpen)
                }
                SmallAction("Clear", Ink, onDismiss)
            }
        } else {
            if (previewLine.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        SenderMessageLine(previewLine, maxLines = 2, compact = true)
                    }
                    PriorityTag(item.priority)
                }
            }
        }
    }
}

@Composable
private fun ExpandCollapseButton(expanded: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "expand-chevron"
    )
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFFF5F8FC))
            .border(1.dp, Color(0xFFE5ECF5), CircleShape)
            .hapticClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            val strokeWidth = size.width * 0.16f
            drawLine(
                color = Muted,
                start = Offset(size.width * 0.22f, size.height * 0.38f),
                end = Offset(size.width * 0.50f, size.height * 0.66f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Muted,
                start = Offset(size.width * 0.78f, size.height * 0.38f),
                end = Offset(size.width * 0.50f, size.height * 0.66f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SenderMessageLine(line: String, maxLines: Int = Int.MAX_VALUE, compact: Boolean = false) {
    val sender = line.substringBefore(": ", "")
    val message = line.substringAfter(": ", line)
    if (sender.isNotBlank() && message != line) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp)) {
            Text(
                sender,
                color = Ink.copy(alpha = if (compact) 0.88f else 0.92f),
                fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                message,
                color = Ink.copy(alpha = 0.70f),
                fontWeight = FontWeight.Normal,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    } else {
        Text(
            line,
            color = Ink.copy(alpha = 0.72f),
            fontWeight = FontWeight.Normal,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
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
            .hapticClick(onClick = onClick)
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

private fun displayNotificationLines(item: DigestItem): List<String> {
    return item.lines.ifEmpty { listOf(item.text.ifBlank { "No extra notification text." }) }
}

private fun categoryAccent(category: String): Color {
    return when (category) {
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
    val appCounts = todayItems.groupingBy { it.appName }.eachCount()
    val priorityAppCounts = todayItems
        .filter { it.priority == "Priority" }
        .groupingBy { it.appName }
        .eachCount()
    val noiseAppCounts = todayItems
        .filter { isNoise(it) }
        .groupingBy { it.appName }
        .eachCount()
    val topApp = appCounts.maxByOrNull { it.value }

    return DailyStats(
        dateKey = today,
        totalCount = todayItems.size,
        priorityCount = todayItems.count { it.priority == "Priority" },
        noiseCount = todayItems.count { isNoise(it) },
        topAppName = topApp?.key.orEmpty(),
        topAppCount = topApp?.value ?: 0,
        yesterdayTotalCount = yesterdayItems.size,
        yesterdayNoiseCount = yesterdayItems.count { isNoise(it) },
        appCounts = appCounts,
        priorityAppCounts = priorityAppCounts,
        noiseAppCounts = noiseAppCounts
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

private fun hasLaunchableSourceApp(context: Context, item: DigestItem): Boolean {
    return item.packageName.isNotBlank() &&
        context.packageManager.getLaunchIntentForPackage(item.packageName) != null
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

private fun extractNotificationIconBitmap(context: Context, notification: Notification): Bitmap? {
    return runCatching {
        notification.getLargeIcon()?.loadDrawable(context)?.let { drawableToBitmap(it) }
    }.getOrNull() ?: notification.extras.parcelableCompat<Bitmap>("android.largeIcon")
}

private fun extractNotificationPictureBitmap(context: Context, notification: Notification): Bitmap? {
    val extras = notification.extras
    return extras.parcelableCompat<Bitmap>(Notification.EXTRA_PICTURE)
        ?: extras.parcelableCompat<Icon>("android.pictureIcon")?.let { icon ->
            runCatching { icon.loadDrawable(context)?.let { drawableToBitmap(it) } }.getOrNull()
        }
        ?: extractMessagingStyleImageBitmap(context, extras)
}

private fun extractMessagingStyleImageBitmap(context: Context, extras: Bundle): Bitmap? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
    val messages = extras.parcelableArrayCompat(Notification.EXTRA_MESSAGES) ?: return null
    return runCatching {
        Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)
            .firstNotNullOfOrNull { message ->
                val mimeType = message.dataMimeType.orEmpty()
                val dataUri = message.dataUri
                if (mimeType.startsWith("image/") && dataUri != null) {
                    decodeBitmapUri(context, dataUri)
                } else {
                    null
                }
        }
    }.getOrNull()
}

private fun extractNotificationLines(notification: Notification): List<String> {
    val extras = notification.extras
    val messagingLines = extractMessagingStyleLines(extras)
    if (messagingLines.isNotEmpty()) return messagingLines

    val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        ?.map { it.toString() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
    if (textLines.isNotEmpty()) return textLines

    return listOfNotNull(
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    )
        .filter { it.isNotBlank() }
        .distinct()
}

private fun extractMessagingStyleLines(extras: Bundle): List<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return emptyList()
    val messages = extras.parcelableArrayCompat(Notification.EXTRA_MESSAGES) ?: return emptyList()
    return runCatching {
        Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)
            .mapNotNull { message ->
                val text = message.text?.toString().orEmpty()
                val sender = messageSenderName(message)
                when {
                    sender.isNotBlank() && text.isNotBlank() -> "$sender: $text"
                    text.isNotBlank() -> text
                    sender.isNotBlank() && message.dataMimeType?.startsWith("image/") == true -> "$sender: Photo"
                    message.dataMimeType?.startsWith("image/") == true -> "Photo"
                    else -> null
                }
            }
    }.getOrDefault(emptyList())
}

private fun messageSenderName(message: Notification.MessagingStyle.Message): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        message.senderPerson?.name?.toString().orEmpty()
    } else {
        @Suppress("DEPRECATION")
        message.sender?.toString().orEmpty()
    }
}

private fun decodeBitmapUri(context: Context, uri: Uri): Bitmap? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}

private inline fun <reified T> Bundle.parcelableCompat(key: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
}

private fun Bundle.parcelableArrayCompat(key: String): Array<Parcelable>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArray(key, Parcelable::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArray(key)
    }
}

private fun saveNotificationBitmap(
    context: Context,
    itemId: String,
    kind: String,
    bitmap: Bitmap?,
    maxDimension: Int
): String? {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
    val directory = notificationMediaDir(context).apply { mkdirs() }
    val file = File(directory, "${Integer.toHexString(itemId.hashCode())}_$kind.png")
    val largestSide = if (bitmap.width > bitmap.height) bitmap.width else bitmap.height
    val outputBitmap = if (largestSide > maxDimension) {
        val scale = maxDimension.toFloat() / largestSide.toFloat()
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    runCatching {
        FileOutputStream(file).use { outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.getOrElse { return null }
    return file.absolutePath
}

private fun deleteNotificationMedia(context: Context, item: DigestItem) {
    deleteNotificationMedia(context, listOf(item))
}

private fun deleteUnusedNotificationMedia(context: Context, newItem: DigestItem, keptItem: DigestItem) {
    val keptPaths = setOf(keptItem.notificationIconPath, keptItem.contentImagePath)
    val unusedItem = newItem.copy(
        notificationIconPath = newItem.notificationIconPath.takeUnless { it in keptPaths }.orEmpty(),
        contentImagePath = newItem.contentImagePath.takeUnless { it in keptPaths }.orEmpty()
    )
    deleteNotificationMedia(context, unusedItem)
}

private fun deleteNotificationMedia(context: Context, items: List<DigestItem>) {
    val mediaRoot = notificationMediaDir(context).absolutePath
    items.flatMap { listOf(it.notificationIconPath, it.contentImagePath) }
        .filter { it.isNotBlank() }
        .forEach { path ->
            val file = File(path)
            if (file.absolutePath.startsWith(mediaRoot)) {
                runCatching { file.delete() }
            }
        }
}

private fun clearNotificationMedia(context: Context) {
    notificationMediaDir(context).listFiles()?.forEach { file ->
        runCatching { file.delete() }
    }
}

private fun notificationMediaDir(context: Context): File {
    return File(context.filesDir, "notification_media")
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
