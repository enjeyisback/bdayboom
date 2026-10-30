package app.bdayboom.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.Data

/** Reads birthday and anniversary events from the phone's contacts. Needs READ_CONTACTS. */
object ContactsReader {

    // "1990-05-12", "--05-12", "19900512", optionally followed by a time part.
    private val iso = Regex("""^(\d{4}|-)-?(\d{2})-?(\d{2})""")

    fun parse(raw: String): Triple<Int?, Int, Int>? {
        val m = iso.find(raw.trim()) ?: return null
        val year = m.groupValues[1].toIntOrNull()?.takeIf { it in 1800..2200 }
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        if (!Dates.isValid(month, day)) return null
        return Triple(year, month, day)
    }

    fun read(context: Context): List<ContactBirthday> {
        val out = mutableListOf<ContactBirthday>()
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.LOOKUP_KEY, Data.DISPLAY_NAME, Event.START_DATE, Event.TYPE),
            "${Data.MIMETYPE} = ? AND ${Event.TYPE} IN (?, ?)",
            arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString(), Event.TYPE_ANNIVERSARY.toString()),
            "${Data.DISPLAY_NAME} COLLATE NOCASE",
        )?.use { cur ->
            while (cur.moveToNext()) {
                val key = cur.getString(0) ?: continue
                val name = cur.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val (year, month, day) = parse(cur.getString(2) ?: continue) ?: continue
                val anniversary = cur.getInt(3) == Event.TYPE_ANNIVERSARY
                out += ContactBirthday(
                    key = if (anniversary) "$key#anniversary" else key,
                    name = name,
                    type = if (anniversary) EntryType.Anniversary else EntryType.Birthday,
                    month = month, day = day, year = year,
                )
            }
        }
        return out.distinctBy { it.key }
    }

    /** How many contact birthdays are new or have a different date than what was imported. */
    fun newOrChanged(context: Context, imported: Map<String, Person>): Int = read(context).count { c ->
        val e = imported[c.key]
        e == null || e.month != c.month || e.day != c.day || e.year != c.year
    }
}
