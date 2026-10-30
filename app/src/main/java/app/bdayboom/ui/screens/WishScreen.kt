package app.bdayboom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import app.bdayboom.data.AppSettings
import app.bdayboom.data.EntryType
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.AppTextField
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.fillWish
import app.bdayboom.ui.readyWishes
import app.bdayboom.ui.shareText
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Radius
import app.bdayboom.ui.theme.Space
import app.bdayboom.ui.wishName
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * S09/S10 wishes: edit the message, pick a ready-made one, or reuse your own. With [personId] it's "send a wish to
 * X" and names are filled in; without, it's the wish library from the drawer.
 */
@Composable
fun WishScreen(personId: String) {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val t = AppTheme.type
    val person by remember(personId) { if (personId.isEmpty()) flowOf(null) else g.repo.person(personId) }.collectAsState(initial = null)
    val settings by g.settings.flow.collectAsState(initial = AppSettings())
    var text by rememberSaveable { mutableStateOf("") }

    val p = person
    LaunchedEffect(p?.id) {
        if (text.isEmpty()) text = fillWish(readyWishes.getValue(p?.type ?: EntryType.Birthday).first(), p)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTop(if (p != null) "Send a wish" else "Wishes", p?.name ?: "Ready-made and your own")
        ScrollScreen {
            SectionHeader("Your message", icon = Icons.Outlined.EditNote)
            Column(Modifier.padding(horizontal = Space.s4).fillMaxWidth().appCard().padding(Space.s4), verticalArrangement = Arrangement.spacedBy(Space.s3)) {
                AppTextField("Message", text, { text = it }, minLines = 4)
                AppButton("Send", { shareText(context, text.trim()) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Send, enabled = text.isNotBlank())
                AppButton("Save to my wishes", {
                    // Saved as a template so it works for anyone: their name goes back to {name}.
                    val template = if (p != null) text.trim().replace(wishName(p), "{name}") else text.trim()
                    scope.launch {
                        g.settings.addWish(template)
                        chrome.snackbar.showSnackbar("Saved to your wishes")
                    }
                }, Modifier.fillMaxWidth(), ButtonKind.Secondary, Icons.Outlined.BookmarkAdd, enabled = text.isNotBlank())
            }

            if (settings.myWishes.isNotEmpty()) {
                SectionHeader("My wishes", icon = Icons.Outlined.Favorite, trailing = "${settings.myWishes.size}")
                settings.myWishes.forEach { w ->
                    WishCard(fillWish(w, p), { text = fillWish(w, p) }) {
                        IconButton({
                            scope.launch {
                                g.settings.removeWish(w)
                                val r = chrome.snackbar.showSnackbar("Wish removed", actionLabel = "Undo")
                                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) g.settings.addWish(w)
                            }
                        }) { Icon(Icons.Outlined.Delete, "Remove this wish", tint = c.textMuted) }
                    }
                }
            }

            // With a person: only wishes for their kind of day. In the library: all of them.
            val kinds = p?.let { listOf(it.type) } ?: EntryType.entries
            kinds.forEach { kind ->
                SectionHeader(
                    when (kind) { EntryType.Birthday -> "Birthday wishes"; EntryType.Anniversary -> "Anniversary wishes"; EntryType.Event -> "Other days" },
                    icon = Icons.Outlined.Mail,
                )
                readyWishes.getValue(kind).forEach { w -> WishCard(fillWish(w, p), { text = fillWish(w, p) }) }
            }
            Spacer(Modifier.height(Space.s8))
        }
    }
}

/** One wish; tapping it puts it in the message box. */
@Composable
private fun WishCard(text: String, onUse: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = AppTheme.colors
    Row(
        Modifier
            .padding(horizontal = Space.s4, vertical = Space.s1)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .appCard()
            .clickable(role = Role.Button, onClickLabel = "Use this wish", onClick = onUse)
            .padding(start = Space.s4, top = Space.s3, bottom = Space.s3, end = Space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = AppTheme.type.base, color = c.text, modifier = Modifier.weight(1f).padding(end = Space.s2))
        trailing()
    }
}
