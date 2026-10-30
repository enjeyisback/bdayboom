package app.bdayboom.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Layout
import app.bdayboom.ui.theme.Radius
import app.bdayboom.ui.theme.Space
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// ---------------------------------------------------------------- surfaces

/** 2dp accent ring when this element or a child has keyboard/D-pad focus. */
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = AppTheme.colors.accent
    this
        .onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(2.dp, ring, shape) else Modifier)
}

/**
 * Card surface. Light: white on cream with a warm coral-tinted shadow.
 * Dark: twilight fill, 1dp specular border, deep shadow (Stitch "glass" without the blur).
 */
fun Modifier.appCard(shape: Shape = RoundedCornerShape(Radius.lg), fill: Color? = null): Modifier = composed {
    val c = AppTheme.colors
    val dark = AppTheme.isDark
    val tint = if (dark) Color.Black else Color(0xFFFF6B6B)
    this
        .shadow(if (dark) 10.dp else 6.dp, shape, ambientColor = tint.copy(alpha = if (dark) 0.6f else 0.18f), spotColor = tint.copy(alpha = if (dark) 0.6f else 0.22f))
        .clip(shape)
        .background(fill ?: c.surface)
        .then(if (dark) Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), shape) else Modifier)
}

// ---------------------------------------------------------------- buttons

enum class ButtonKind {
    /** accent fill */ Primary,
    /** light: peach fill, dark: amber ghost */ Secondary,
    /** violet tint ("Gift guide") */ Tonal,
    /** grey fill ("Call", "Cancel") */ Neutral,
    /** text only */ Text,
    /** destructive */ Danger,
    /** gradient, full-width save action */ Cta,
    /** on the spotlight hero: light = white pill, dark = amber */ OnHero,
    /** translucent pill on the hero */ OnHeroGhost,
}

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = AppTheme.colors
    val dark = AppTheme.isDark
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.pill)
    var border: BorderStroke? = null
    var brush: Brush? = null
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.accent to c.onAccent
        ButtonKind.Secondary -> if (dark) {
            border = BorderStroke(1.dp, c.accent.copy(alpha = 0.4f)); c.surface to c.accent
        } else c.accentSoft to c.onAccentSoft
        ButtonKind.Tonal -> c.highlightSoft to c.onHighlightSoft
        ButtonKind.Neutral -> c.surface2 to c.text
        ButtonKind.Text -> Color.Transparent to c.textMuted
        ButtonKind.Danger -> c.danger to c.onDanger
        ButtonKind.Cta -> {
            brush = Brush.horizontalGradient(listOf(c.ctaStart, c.ctaEnd)); Color.Unspecified to c.onCta
        }
        ButtonKind.OnHero -> if (dark) c.accent to c.onAccent else Color.White to c.accent
        ButtonKind.OnHeroGhost -> {
            border = BorderStroke(1.dp, c.onHero.copy(alpha = 0.25f)); c.onHero.copy(alpha = 0.14f) to c.onHero
        }
    }
    val big = kind == ButtonKind.Cta
    Row(
        modifier
            .heightIn(min = if (big) 56.dp else Layout.touchMin)
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (kind == ButtonKind.Cta || kind == ButtonKind.Primary) Modifier.shadow(if (big) 8.dp else 2.dp, shape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
            .clip(shape)
            .then(if (brush != null) Modifier.background(brush!!) else Modifier.background(bg))
            .then(if (border != null) Modifier.border(border!!, shape) else Modifier)
            .focusRing(shape)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = "Loading" }
            .padding(horizontal = if (kind == ButtonKind.Text) Space.s3 else Space.s5, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = fg)
                Spacer(Modifier.width(Space.s2))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(18.dp), tint = fg)
                Spacer(Modifier.width(Space.s2))
            }
            Text(
                label,
                style = if (big) t.name else t.sm.copy(fontWeight = FontWeight.Bold),
                color = fg,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------- chips & tags

/** Filter / choice pill. Selected = solid accent (coral in light, amber in dark). */
@Composable
fun AppChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.pill)
    Row(
        modifier
            .heightIn(min = Layout.touchMin)
            .padding(vertical = 6.dp)
            .heightIn(min = Layout.chipHeight)
            .clip(shape)
            .background(if (selected) c.accent else c.surface2)
            .focusRing(shape)
            .clickable(role = Role.Checkbox, onClickLabel = label, onClick = onClick)
            .semantics { stateDescription = if (selected) "Selected" else "Not selected" }
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) c.onAccent else c.text
        Text(label, style = t.sm.copy(fontWeight = FontWeight.SemiBold), color = fg)
        if (icon != null) {
            Spacer(Modifier.width(Space.s1))
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
        }
    }
}

@Composable
fun ChipRow(labels: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.s4),
        horizontalArrangement = Arrangement.spacedBy(Space.s2),
    ) { labels.forEach { AppChip(it, it == selected, { onSelect(it) }) } }
}

enum class TagTone { Accent, Celebrate, Highlight, Neutral, OnHero }

/** Small non-interactive pill: "Turning 28", "Annual", "Gift ordered", "TODAY'S SPOTLIGHT". */
@Composable
fun Tag(text: String, tone: TagTone = TagTone.Neutral, icon: ImageVector? = null, caps: Boolean = false, solid: Boolean = false) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val (bg, fg) = when (tone) {
        TagTone.Accent -> if (solid) c.accent to c.onAccent else c.accentSoft to c.onAccentSoft
        TagTone.Celebrate -> if (solid) c.celebrate to c.onCelebrate else c.celebrateSoft to c.onCelebrateSoft
        TagTone.Highlight -> if (solid) c.highlight to c.onHighlight else c.highlightSoft to c.onHighlightSoft
        TagTone.Neutral -> c.surface2 to c.textMuted
        TagTone.OnHero -> c.onHero.copy(alpha = 0.16f) to c.onHero
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .padding(horizontal = Space.s3, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = fg)
            Spacer(Modifier.width(Space.s1))
        }
        Text(if (caps) text.uppercase() else text, style = if (caps) t.micro else t.label, color = fg)
    }
}

// ---------------------------------------------------------------- headers & search

@Composable
fun AppTopBar(title: String, subtitle: String? = null, navigation: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Row(
        Modifier.fillMaxWidth().heightIn(min = Layout.topBar).padding(horizontal = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigation != null) navigation() else Spacer(Modifier.width(Space.s2))
        Column(Modifier.weight(1f).padding(horizontal = Space.s2)) {
            Text(title, style = t.name, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle.uppercase(), style = t.micro, color = c.accent)
        }
        actions()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: String? = null) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Row(
        modifier.fillMaxWidth().padding(start = Space.s4, end = Space.s4, top = Space.s6, bottom = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.accent)
            Spacer(Modifier.width(Space.s2))
        }
        Text(title, style = t.lg, color = c.text, modifier = Modifier.weight(1f).semantics { heading() })
        if (trailing != null) Text(trailing, style = t.label, color = c.textMuted)
    }
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, onFilter: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.pill)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = t.sm,
        placeholder = { Text(placeholder, style = t.sm, color = c.textMuted, maxLines = 1) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = c.textMuted) },
        trailingIcon = {
            Box(
                Modifier.size(Layout.touchMin).clickable(role = Role.Button, onClickLabel = "Sort and filter", onClick = onFilter),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(Radius.sm)).background(c.surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Tune, "Sort and filter", Modifier.size(18.dp), tint = c.text)
                }
            }
        },
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = if (AppTheme.isDark) c.border else Color.Transparent,
            cursorColor = c.accent,
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = Space.s4).appCard(shape),
    )
}

// ---------------------------------------------------------------- people

enum class EntryKind { Birthday, Anniversary, Event }

@Composable
fun Avatar(
    initials: String,
    kind: EntryKind = EntryKind.Birthday,
    size: Dp = Layout.avatar,
    ring: Color? = null,
    badge: ImageVector? = null,
    photo: String? = null,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val image by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    Box(Modifier.size(size + if (ring != null) 6.dp else 0.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape).padding(3.dp) else Modifier)
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accentSoft, c.highlightSoft))),
            contentAlignment = Alignment.Center,
        ) {
            val img = image
            if (img != null) {
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else when (kind) {
                EntryKind.Birthday -> Text(
                    initials.take(2).uppercase(),
                    style = if (size >= 72.dp) t.xl else t.title,
                    color = c.onAccentSoft,
                )
                EntryKind.Anniversary -> Icon(Icons.Outlined.FavoriteBorder, null, tint = c.onAccentSoft)
                EntryKind.Event -> Icon(Icons.Outlined.Event, null, tint = c.onAccentSoft)
            }
        }
        if (badge != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(c.celebrate).border(2.dp, c.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(badge, null, Modifier.size(12.dp), tint = c.onCelebrate) }
        }
    }
}

/** "12 Days" pill with the date under it. Tomorrow = celebrate, ≤7 = accent, far (>180) = quiet. */
@Composable
fun CountdownPill(days: Int, date: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val (bg, fg) = when {
        days == 0 -> c.celebrate to c.onCelebrate
        days == 1 -> c.celebrateSoft to c.onCelebrateSoft
        days <= 7 -> c.accentSoft to c.onAccentSoft
        days > 180 -> c.surface2 to c.textMuted
        else -> c.surface2 to c.text
    }
    Column(modifier.widthIn(min = 64.dp), horizontalAlignment = Alignment.End) {
        Text(
            when (days) { 0 -> "Today"; 1 -> "Tomorrow"; else -> "$days Days" },
            style = t.label.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).background(bg).padding(horizontal = Space.s3, vertical = 5.dp),
        )
        Spacer(Modifier.height(Space.s1))
        Text(date, style = t.xs, color = c.textMuted)
    }
}

data class PersonRowModel(
    val id: String,
    val name: String,
    val meta: String,
    val group: String,
    val date: String,
    val spoken: String,
    val kind: EntryKind = EntryKind.Birthday,
    val daysLeft: Int,
    val photo: String? = null,
)

private fun initialsOf(name: String) = name.split(" ").mapNotNull { w -> w.firstOrNull { it.isLetter() } }.joinToString("")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonRow(model: PersonRowModel, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.lg)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onDelete()
            false // the list removes the row; snapping back lets Undo restore it in place
        },
        positionalThreshold = { total -> total * 0.4f },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier.padding(horizontal = Space.s4, vertical = 6.dp),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(shape).background(c.danger).padding(horizontal = Space.s6),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Outlined.Delete, null, tint = c.onDanger) }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .appCard(shape)
                .focusRing(shape)
                .clickable(onClickLabel = "Open", role = Role.Button, onClick = onOpen)
                .clearAndSetSemantics {
                    contentDescription = model.spoken
                    customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true })
                }
                .heightIn(min = Layout.rowMinHeight)
                .padding(Space.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(initialsOf(model.name), model.kind, photo = model.photo)
            Spacer(Modifier.width(Space.s3))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = t.name, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(model.meta, style = t.sm, color = c.textMuted, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (model.kind == EntryKind.Anniversary) Icons.Outlined.FavoriteBorder else Icons.Outlined.Cake, null, Modifier.size(13.dp), tint = c.textMuted)
                    Spacer(Modifier.width(Space.s1))
                    Text(model.group, style = t.xs, color = c.textMuted)
                }
            }
            CountdownPill(model.daysLeft, model.date)
        }
    }
}

/** Today's hero card. Light: coral gradient. Dark: plum-to-twilight with an amber/violet edge. */
@Composable
fun SpotlightCard(
    name: String,
    turning: String,
    message: String,
    onWish: () -> Unit,
    onGift: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String = "Send gift",
    secondaryIcon: ImageVector = Icons.Outlined.Cake,
    photo: String? = null,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val dark = AppTheme.isDark
    val shape = RoundedCornerShape(Radius.xl)
    Column(
        modifier
            .padding(horizontal = Space.s4)
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = if (dark) c.accent else c.heroStart, spotColor = if (dark) c.accent else c.heroStart)
            .clip(shape)
            .background(Brush.linearGradient(listOf(c.heroStart, c.heroEnd)))
            .then(if (dark) Modifier.border(1.dp, Brush.linearGradient(listOf(c.accent.copy(alpha = 0.7f), c.highlight.copy(alpha = 0.5f))), shape) else Modifier)
            .semantics(mergeDescendants = true) {}
            .padding(Space.s5),
    ) {
        Tag("Today's spotlight", TagTone.OnHero, caps = true)
        Spacer(Modifier.height(Space.s4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(initialsOf(name), size = 64.dp, ring = if (dark) c.accent else c.onHero, badge = Icons.Outlined.Cake, photo = photo)
            Spacer(Modifier.width(Space.s4))
            Column {
                Text(name, style = t.xl, color = c.onHero, maxLines = 2)
                Spacer(Modifier.height(Space.s1))
                Tag(turning, TagTone.Celebrate, solid = true)
            }
        }
        Spacer(Modifier.height(Space.s3))
        Text(message, style = t.sm, color = c.onHero.copy(alpha = 0.9f))
        Spacer(Modifier.height(Space.s4))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
            AppButton("Send wish", onWish, Modifier.weight(1f), ButtonKind.OnHero, Icons.Outlined.Mail)
            AppButton(secondaryLabel, onGift, Modifier.weight(1f), ButtonKind.OnHeroGhost, secondaryIcon)
        }
    }
}

// ---------------------------------------------------------------- banners

@Composable
fun StatBanner(text: String, tag: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        modifier
            .padding(horizontal = Space.s4)
            .fillMaxWidth()
            .appCard(shape, fill = if (AppTheme.isDark) c.surface else c.celebrateSoft)
            .padding(horizontal = Space.s3, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(c.celebrate), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = c.onCelebrate)
        }
        Spacer(Modifier.width(Space.s3))
        Text(text, style = t.sm.copy(fontWeight = FontWeight.SemiBold), color = c.text, modifier = Modifier.weight(1f))
        Tag(tag, TagTone.Accent)
    }
}

enum class BannerKind { Info, Warning }

@Composable
fun Banner(text: String, kind: BannerKind = BannerKind.Info, onClick: (() -> Unit)? = null) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.pill)
    val (bg, fg) = if (kind == BannerKind.Info) c.accent to c.onAccent else c.celebrateSoft to c.onCelebrateSoft
    Row(
        Modifier
            .padding(horizontal = Space.s4)
            .fillMaxWidth()
            .heightIn(min = Layout.touchMin)
            .clip(shape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Space.s4, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (kind == BannerKind.Warning) {
            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(Space.s2))
        }
        Text(text, style = t.sm.copy(fontWeight = FontWeight.Bold), color = fg, textAlign = TextAlign.Center)
    }
}

// ---------------------------------------------------------------- forms

@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    trailing: ImageVector? = null,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(modifier) {
        Text(label, style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1, bottom = Space.s2))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            isError = error != null,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = t.base,
            placeholder = placeholder?.let { { Text(it, style = t.base, color = c.textMuted) } },
            trailingIcon = trailing?.let { { Icon(it, null, tint = c.textMuted) } },
            supportingText = error?.let { { Text(it, style = t.xs) } },
            shape = RoundedCornerShape(Radius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = c.surface2,
                unfocusedContainerColor = c.surface2,
                disabledContainerColor = c.surface2.copy(alpha = 0.5f),
                errorContainerColor = c.surface2,
                focusedBorderColor = c.accent,
                unfocusedBorderColor = if (AppTheme.isDark) c.border else Color.Transparent,
                disabledBorderColor = Color.Transparent,
                cursorColor = c.accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Month / Day / Year select. Opens a picker; here only the field. */
@Composable
fun SelectField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.md)
    Column(modifier) {
        Text(label, style = t.label, color = c.textMuted, modifier = Modifier.padding(start = Space.s1, bottom = Space.s2))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(c.surface2)
                .then(if (AppTheme.isDark) Modifier.border(1.dp, c.border, shape) else Modifier)
                .focusRing(shape)
                .clickable(role = Role.DropdownList, onClickLabel = "Choose $label", onClick = onClick)
                .padding(horizontal = Space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = t.base, color = c.text, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, tint = c.textMuted)
        }
    }
}

@Composable
fun appSwitchColors() = AppTheme.colors.let { c ->
    SwitchDefaults.colors(
        checkedTrackColor = c.accent,
        checkedThumbColor = c.onAccent,
        checkedBorderColor = c.accent,
        uncheckedTrackColor = c.surface2,
        uncheckedThumbColor = c.borderInput,
        uncheckedBorderColor = c.borderInput,
    )
}

/** Reminder cadence row: check circle, two lines, trailing tag or switch. Also the settings row. */
@Composable
fun ReminderRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    useSwitch: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(if (checked && !useSwitch) c.accentSoft.copy(alpha = if (AppTheme.isDark) 0.6f else 0.55f) else c.surface2)
            .focusRing(shape)
            .clickable(role = if (useSwitch) Role.Switch else Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .padding(horizontal = Space.s3, vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = c.onAccentSoft)
            }
        } else {
            Box(
                Modifier.size(24.dp).clip(CircleShape)
                    .background(if (checked) c.accent else Color.Transparent)
                    .border(if (checked) 0.dp else 2.dp, c.borderInput, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (checked) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = c.onAccent) }
        }
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.sm.copy(fontWeight = FontWeight.Bold), color = c.text)
            Text(subtitle, style = t.xs, color = c.textMuted)
        }
        when {
            useSwitch -> Switch(checked = checked, onCheckedChange = null, colors = appSwitchColors())
            tag != null -> Tag(tag, TagTone.Celebrate, solid = true)
        }
    }
}

@Composable
fun SettingsItem(label: String, value: String? = null, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (value != null) 72.dp else 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) c.accent else c.textFaint)
            Spacer(Modifier.width(Space.s4))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = t.base, color = if (enabled) c.text else c.textMuted)
            if (value != null) Text(value, style = t.sm, color = c.textMuted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = c.textMuted)
    }
}

@Composable
fun SelectableRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = Space.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked, onCheckedChange = null, enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.onAccent, uncheckedColor = c.borderInput),
        )
        Spacer(Modifier.width(Space.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = t.base, color = if (enabled) c.text else c.textMuted)
            Text(subtitle, style = t.sm, color = c.textMuted)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, actions: @Composable () -> Unit = {}) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(
        Modifier.fillMaxWidth().padding(Space.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.celebrateSoft, c.accentSoft))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(44.dp), tint = c.accent) }
        Text(title, style = t.lg, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(body, style = t.sm, color = c.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.s1))
        actions()
    }
}

// ---------------------------------------------------------------- bottom nav

data class NavDest(val label: String, val icon: ImageVector)

val MainDestinations = listOf(
    NavDest("Home", Icons.Outlined.Home),
    NavDest("Calendar", Icons.Outlined.CalendarMonth),
    NavDest("Contacts", Icons.Outlined.Contacts),
    NavDest("Settings", Icons.Outlined.Settings),
)

@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    Column(Modifier.background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = Layout.bottomBar).padding(horizontal = Space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainDestinations.forEachIndexed { i, d ->
                if (i == 2) AddButton(onAdd, Modifier.weight(1f))
                val sel = selected == i
                val fg = if (sel) c.accent else c.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = Layout.touchMin)
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable(role = Role.Tab, onClick = { onSelect(i) })
                        .semantics { stateDescription = if (sel) "Selected" else "" }
                        .padding(vertical = Space.s2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(d.icon, null, tint = fg)
                    Text(d.label, style = t.label.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium), color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** Raised rounded-rect add button without text. */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(Radius.md)
    Box(
        modifier
            .heightIn(min = Layout.touchMin)
            .clip(RoundedCornerShape(Radius.md))
            .clickable(role = Role.Button, onClickLabel = "Add birthday", onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "Add birthday" }
            .padding(vertical = Space.s2),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 54.dp, height = 38.dp)
                .shadow(6.dp, shape, ambientColor = c.accent, spotColor = c.accent)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(c.ctaStart, c.ctaEnd)))
                .focusRing(shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = c.onCta)
        }
    }
}

// ---------------------------------------------------------------- calendar

/** "‹  October 2025  ›" with "4 friends celebrating" under it. */
@Composable
fun MonthHeader(month: YearMonth, celebrating: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val locale = Locale.getDefault()
    @Composable
    fun arrow(icon: ImageVector, label: String, onClick: () -> Unit) = Box(
        Modifier.size(Layout.touchMin).clip(CircleShape).background(c.surface2).clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.text) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s3, vertical = Space.s2), verticalAlignment = Alignment.CenterVertically) {
        arrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous month", onPrev)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(month.month.getDisplayName(DateTextStyle.FULL, locale) + " " + month.year, style = t.lg, color = c.text)
            Text(
                "$celebrating ${if (celebrating == 1) "friend" else "friends"} celebrating".uppercase(),
                style = t.micro, color = c.accent,
            )
        }
        arrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next month", onNext)
    }
}

enum class DotKind { Upcoming, Milestone, Planned }

@Composable
private fun dotColor(k: DotKind) = when (k) {
    DotKind.Upcoming -> AppTheme.colors.accent
    DotKind.Milestone -> AppTheme.colors.celebrate
    DotKind.Planned -> AppTheme.colors.highlight
}

@Composable
fun MonthGrid(
    month: YearMonth,
    dots: Map<Int, List<DotKind>>,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek,
) {
    val c = AppTheme.colors
    val t = AppTheme.type
    val locale = Locale.getDefault()
    val days = (0 until 7).map { firstDay.plus(it.toLong()) }
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value - firstDay.value + 7) % 7
    val start = first.minusDays(offset.toLong())
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    Column(Modifier.padding(horizontal = Space.s3)) {
        Row {
            days.forEach {
                Text(
                    it.getDisplayName(DateTextStyle.NARROW, locale),
                    style = t.label, color = c.textMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = Space.s2),
                )
            }
        }
        for (week in 0 until weeks) {
            Row {
                for (d in 0 until 7) {
                    val date = start.plusDays((week * 7 + d).toLong())
                    val inMonth = date.month == month.month
                    val marks = if (inMonth) dots[date.dayOfMonth].orEmpty() else emptyList()
                    val isSel = date == selected
                    val spoken = date.dayOfWeek.getDisplayName(DateTextStyle.FULL, locale) + " " + date.dayOfMonth + " " +
                        date.month.getDisplayName(DateTextStyle.FULL, locale) +
                        if (marks.isNotEmpty()) ", ${marks.size} ${if (marks.size == 1) "celebration" else "celebrations"}" else ""
                    Column(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.9f)
                            .clip(CircleShape)
                            .clickable(enabled = inMonth, role = Role.Button) { onSelect(date) }
                            .clearAndSetSemantics { contentDescription = spoken },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .then(if (isSel) Modifier.shadow(10.dp, CircleShape, ambientColor = c.accent, spotColor = c.accent) else Modifier)
                                .clip(CircleShape)
                                .then(if (isSel) Modifier.background(Brush.linearGradient(listOf(c.ctaStart, c.ctaEnd))) else Modifier)
                                .then(if (date == today && !isSel) Modifier.border(2.dp, c.accent, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${date.dayOfMonth}",
                                style = t.sm.copy(fontWeight = if (isSel || marks.isNotEmpty()) FontWeight.Bold else FontWeight.Medium),
                                color = when {
                                    isSel -> c.onCta
                                    inMonth -> c.text
                                    else -> c.textFaint
                                },
                            )
                        }
                        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!isSel) marks.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(dotColor(it))) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Space.s3), horizontalArrangement = Arrangement.Center) {
            listOf(DotKind.Upcoming to "Upcoming", DotKind.Milestone to "Milestone", DotKind.Planned to "Party planned").forEach { (k, l) ->
                Row(Modifier.padding(horizontal = Space.s2), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor(k)))
                    Spacer(Modifier.width(Space.s1))
                    Text(l, style = t.label, color = c.textMuted)
                }
            }
        }
    }
}
