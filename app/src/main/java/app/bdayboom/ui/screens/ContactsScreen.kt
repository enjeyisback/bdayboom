package app.bdayboom.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bdayboom.data.EntryType
import app.bdayboom.data.Person
import app.bdayboom.graph
import app.bdayboom.ui.LocalChrome
import app.bdayboom.ui.Routes
import app.bdayboom.ui.components.AppButton
import app.bdayboom.ui.components.Avatar
import app.bdayboom.ui.components.ButtonKind
import app.bdayboom.ui.components.EmptyState
import app.bdayboom.ui.components.SearchField
import app.bdayboom.ui.components.SectionHeader
import app.bdayboom.ui.components.Tag
import app.bdayboom.ui.components.TagTone
import app.bdayboom.ui.components.appCard
import app.bdayboom.ui.initials
import app.bdayboom.ui.kind
import app.bdayboom.ui.monthName
import app.bdayboom.ui.theme.AppTheme
import app.bdayboom.ui.theme.Radius
import app.bdayboom.ui.theme.Space

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    val g = context.graph
    val chrome = LocalChrome.current
    val c = AppTheme.colors
    val t = AppTheme.type

    val people by g.repo.people.collectAsState(initial = null)
    val groups by g.repo.groups.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ScreenTop("Contacts", tab = true)
        val all = people
        if (all == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Outlined.Contacts,
                    "No contacts yet",
                    "Add birthdays and dates. Everyone you add will show up here as a contact profile.",
                ) {
                    AppButton("Add someone", { chrome.nav.navigate(Routes.edit()) }, kind = ButtonKind.Primary)
                }
            }
            return@Column
        }

        val filtered = all.filter { p ->
            query.isBlank() || p.name.contains(query.trim(), ignoreCase = true) ||
                p.notes.contains(query.trim(), ignoreCase = true) ||
                (p.secondLabel != null && p.secondLabel.contains(query.trim(), ignoreCase = true))
        }.sortedBy { it.name.lowercase() }

        val grouped = filtered.groupBy { it.name.trim().firstOrNull()?.uppercaseChar() ?: '#' }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Spacer(Modifier.height(Space.s2))
                SearchField(query, { query = it }, "Search contacts...", onFilter = {})
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.s4, vertical = Space.s2),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (query.isBlank()) "${all.size} contacts" else "${filtered.size} of ${all.size} contacts",
                        style = t.sm,
                        color = c.textMuted,
                    )
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.SearchOff,
                        "No one matches",
                        "No contact found for \"$query\".",
                    ) {
                        AppButton("Clear search", { query = "" }, kind = ButtonKind.Secondary)
                    }
                }
            } else {
                grouped.forEach { (initial, list) ->
                    item(key = "header-$initial") {
                        Text(
                            text = initial.toString(),
                            style = t.sm.copy(fontWeight = FontWeight.Bold),
                            color = c.accent,
                            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2),
                        )
                    }

                    items(list, key = { it.id }) { person ->
                        ContactRow(
                            person = person,
                            groupName = groups.firstOrNull { it.id == person.groupId }?.name,
                            onClick = { chrome.nav.navigate(Routes.person(person.id)) },
                            onEdit = { chrome.nav.navigate(Routes.edit(person.id)) },
                        )
                        Spacer(Modifier.height(Space.s2))
                    }
                }
            }
            item { Spacer(Modifier.height(Space.s8)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactRow(
    person: Person,
    groupName: String?,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = AppTheme.colors
    val t = AppTheme.type

    val shape = RoundedCornerShape(Radius.md)
    Row(
        Modifier
            .padding(horizontal = Space.s4)
            .fillMaxWidth()
            .appCard(shape)
            .clickable(role = Role.Button, onClickLabel = "View ${person.name}", onClick = onClick)
            .padding(Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(initials = initials(person.name), kind = person.type.kind(), size = 48.dp, photo = person.photoPath)

        Spacer(Modifier.size(Space.s3))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.name, style = t.base.copy(fontWeight = FontWeight.Bold), color = c.text)
                if (groupName != null) {
                    Spacer(Modifier.size(Space.s2))
                    Tag(groupName, TagTone.Neutral)
                }
            }

            Spacer(Modifier.height(Space.s1))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                verticalArrangement = Arrangement.spacedBy(Space.s1),
            ) {
                val primaryType = when (person.type) {
                    EntryType.Birthday -> "🎂 Birthday"
                    EntryType.Anniversary -> "💍 Anniversary"
                    EntryType.Event -> "⭐ Event"
                }
                val primaryDate = "${person.day} ${monthName(person.month, false)}" +
                    (person.year?.let { " $it" } ?: "")
                Tag("$primaryType: $primaryDate", TagTone.Accent)

                if (person.secondMonth != null && person.secondDay != null) {
                    val label = person.secondLabel?.ifBlank { "Anniversary" } ?: "Anniversary"
                    val icon = if (label.contains("anniversary", ignoreCase = true)) "💍" else "📅"
                    val secDate = "${person.secondDay} ${monthName(person.secondMonth, false)}" +
                        (person.secondYear?.let { " $it" } ?: "")
                    Tag("$icon $label: $secDate", TagTone.Celebrate)
                }
            }
        }

        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "Edit ${person.name}", tint = c.textMuted)
        }
    }
}
