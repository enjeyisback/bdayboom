package app.bdayboom.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.bdayboom.MainActivity
import app.bdayboom.R
import app.bdayboom.data.EntryType
import app.bdayboom.data.Upcoming
import app.bdayboom.graph
import app.bdayboom.ui.milestone
import app.bdayboom.ui.shortDate
import app.bdayboom.ui.theme.DarkColors
import app.bdayboom.ui.theme.LightColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Day/night colours from the same tokens as the app (Glance follows the phone's theme). */
private object W {
    val text = ColorProvider(LightColors.text, DarkColors.text)
    val muted = ColorProvider(LightColors.textMuted, DarkColors.textMuted)
    val accent = ColorProvider(LightColors.accent, DarkColors.accent)
    val onSoft = ColorProvider(LightColors.onAccentSoft, DarkColors.onAccentSoft)
    val onHero = ColorProvider(Color.White, Color.White)
    val onHeroMuted = ColorProvider(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0.85f))
}

private suspend fun loadUpcoming(context: Context): List<Upcoming> = withContext(Dispatchers.IO) {
    val g = context.graph
    val rule = g.settings.now().feb29
    val today = LocalDate.now()
    g.repo.peopleNow().map { Upcoming.of(it, today, rule) }.sortedWith(compareBy({ it.daysLeft }, { it.person.name.lowercase() }))
}

private fun openApp(context: Context) =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

private fun openPerson(context: Context, id: String) =
    openApp(context).putExtra(MainActivity.EXTRA_ENTRY, id).setData(Uri.parse("birthdays://person/$id"))

private fun countdown(days: Int) = when (days) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> "$days days"
}

/** S21 widget 1: the next few people, 4×2. */
class UpcomingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val list = loadUpcoming(context).take(4)
        provideContent { UpcomingContent(context, list) }
    }
}

@Composable
private fun UpcomingContent(context: Context, list: List<Upcoming>) {
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_bg)).padding(horizontal = 14.dp, vertical = 12.dp)
            .clickable(actionStartActivity(openApp(context))),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_stat_cake), null, GlanceModifier.size(16.dp), colorFilter = ColorFilter.tint(W.accent))
            Spacer(GlanceModifier.width(6.dp))
            Text("Coming up", style = TextStyle(color = W.text, fontSize = 15.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (list.isEmpty()) {
            Text("No birthdays yet", style = TextStyle(color = W.text, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Text("Tap to add someone", style = TextStyle(color = W.muted, fontSize = 12.sp))
            return@Column
        }
        list.forEach { u ->
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionStartActivity(openPerson(context, u.person.id))),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(u.person.name, style = TextStyle(color = W.text, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    val meta = listOfNotNull(shortDate(u.next), milestone(u)).joinToString(" · ")
                    Text(meta, style = TextStyle(color = W.muted, fontSize = 12.sp), maxLines = 1)
                }
                val soon = u.daysLeft <= 7
                Box(
                    GlanceModifier.background(ImageProvider(if (soon) R.drawable.widget_pill_soon else R.drawable.widget_pill))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(countdown(u.daysLeft), style = TextStyle(color = if (soon) W.onSoft else W.text, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

/** S21 widget 2: one big countdown to the next person, 2×2. */
class NextWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val next = loadUpcoming(context).firstOrNull()
        provideContent { NextContent(context, next) }
    }
}

@Composable
private fun NextContent(context: Context, u: Upcoming?) {
    val click = if (u != null) openPerson(context, u.person.id) else openApp(context)
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_hero_bg)).padding(14.dp)
            .clickable(actionStartActivity(click)),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (u == null) {
            Text("No birthdays yet", style = TextStyle(color = W.onHero, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            Text("Tap to add", style = TextStyle(color = W.onHeroMuted, fontSize = 12.sp))
            return@Column
        }
        Text("NEXT UP", style = TextStyle(color = W.onHeroMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold))
        if (u.daysLeft == 0) {
            Text("Today", style = TextStyle(color = W.onHero, fontSize = 30.sp, fontWeight = FontWeight.Bold))
        } else {
            Text("${u.daysLeft}", style = TextStyle(color = W.onHero, fontSize = 36.sp, fontWeight = FontWeight.Bold))
            Text(if (u.daysLeft == 1) "day" else "days", style = TextStyle(color = W.onHeroMuted, fontSize = 12.sp))
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(u.person.name, style = TextStyle(color = W.onHero, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        val sub = when (u.person.type) {
            EntryType.Event -> shortDate(u.next)
            else -> listOfNotNull(shortDate(u.next), milestone(u)).joinToString(" · ")
        }
        Text(sub, style = TextStyle(color = W.onHeroMuted, fontSize = 11.sp), maxLines = 1)
    }
}

class UpcomingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpcomingWidget()
}

class NextWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWidget()
}

/** Redraws both widgets; called after every reschedule (data change, alarm, boot, daily worker). */
object WidgetRefresh {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun all(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                UpcomingWidget().updateAll(app)
                NextWidget().updateAll(app)
            }
        }
    }
}
