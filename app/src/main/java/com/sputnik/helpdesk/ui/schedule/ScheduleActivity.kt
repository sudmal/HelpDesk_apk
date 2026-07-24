package com.sputnik.helpdesk.ui.schedule

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sputnik.helpdesk.data.api.RetrofitClient
import com.sputnik.helpdesk.data.model.DayInfo
import com.sputnik.helpdesk.data.model.MemberSchedule
import com.sputnik.helpdesk.data.model.MonthSchedule
import com.sputnik.helpdesk.data.prefs.Prefs
import com.sputnik.helpdesk.databinding.ActivityScheduleBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding

    // Table dimensions (lazy — context available only after onCreate)
    private val cellW by lazy { (30 * resources.displayMetrics.density).toInt() }
    private val rowH  by lazy { (38 * resources.displayMetrics.density).toInt() }
    private val nameW by lazy { (108 * resources.displayMetrics.density).toInt() }
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()

    private val today = LocalDate.now()

    // Colors
    private val colorWork    = Color.parseColor("#10B981")
    private val colorOff     = Color.parseColor("#374151")
    private val colorHoliday = Color.parseColor("#7C3AED")
    private val colorMeRow   = Color.parseColor("#1F2937")
    private val colorHeader  = Color.parseColor("#1A1A1A")
    private val colorToday   = Color.parseColor("#FACC15")  // жёлтый фон сегодня

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Расписание выходов"

        loadSchedule()
    }

    private fun loadSchedule() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.create(Prefs(this@ScheduleActivity))
                    .getSchedule()

                supportActionBar?.subtitle = response.brigade.name

                binding.progressSchedule.visibility = View.GONE
                binding.scrollContent.visibility = View.VISIBLE

                response.months.forEach { month ->
                    addMonthSection(month)
                }
            } catch (e: HttpException) {
                showError(when (e.code()) {
                    403  -> "Нет доступа к бригаде"
                    404  -> "Вы не состоите ни в одной бригаде"
                    else -> "Ошибка сервера ${e.code()}"
                })
            } catch (_: Exception) {
                showError("Нет соединения с сервером")
            }
        }
    }

    private fun showError(msg: String) {
        binding.progressSchedule.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvError.text = msg
    }

    private fun addMonthSection(month: MonthSchedule) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp() }
        }

        // Month title
        container.addView(TextView(this).apply {
            text = formatMonthTitle(month.month)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(4.dp(), 4.dp(), 4.dp(), 6.dp())
        })

        // Table: [names column] + [HorizontalScrollView with days]
        val tableRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        // -- Names column --
        val namesCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(nameW, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Header placeholder (2 rows: day number + dow)
        namesCol.addView(makeCell("", nameW, rowH, colorHeader, Color.TRANSPARENT, false))
        namesCol.addView(makeCell("", nameW, rowH, colorHeader, Color.TRANSPARENT, false))

        month.members.forEach { member ->
            namesCol.addView(makeName(member))
        }
        tableRow.addView(namesCol)

        // -- Days section --
        val daysInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Row 1: day numbers
        val dayNumRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colorHeader)
        }
        month.days.forEach { d ->
            dayNumRow.addView(makeHeaderCell(d.day.toString(), d))
        }
        daysInner.addView(dayNumRow)

        // Row 2: day-of-week abbreviations
        val dowRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colorHeader)
        }
        month.days.forEach { d ->
            dowRow.addView(makeHeaderCell(d.dow, d))
        }
        daysInner.addView(dowRow)

        // Member rows
        month.members.forEach { member ->
            daysInner.addView(makeMemberRow(month.days, member))
        }

        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(daysInner)
        }
        tableRow.addView(hScroll)

        val todayIdx = month.days.indexOfFirst { it.date == today.toString() }
        if (todayIdx >= 0) {
            hScroll.post {
                val offset = ((todayIdx - 2) * cellW).coerceAtLeast(0)
                hScroll.scrollTo(offset, 0)
            }
        }

        container.addView(tableRow)
        binding.layoutMonths.addView(container)
    }

    private fun makeHeaderCell(text: String, day: DayInfo): TextView {
        val isToday = day.date == today.toString()
        val bg = if (isToday) colorToday else colorHeader
        val color = when {
            isToday       -> Color.parseColor("#1E293B")
            day.isHoliday -> colorHoliday
            day.isWeekend -> Color.parseColor("#F97316")
            else          -> Color.parseColor("#9E9E9E")
        }
        return makeCell(text, cellW, rowH, bg, color, isToday).apply {
            textSize = 10f
        }
    }

    private fun makeMemberRow(days: List<DayInfo>, member: MemberSchedule): LinearLayout {
        val bg = if (member.is_me) colorMeRow else Color.TRANSPARENT
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(bg)
            days.forEach { d ->
                val cellColor = cellColor(d, member.schedule)
                addView(makeCell("", cellW, rowH, cellColor, Color.TRANSPARENT, false))
            }
        }
    }

    private fun makeName(member: MemberSchedule): TextView {
        val bg = if (member.is_me) colorMeRow else Color.parseColor("#1A1A1A")
        val style = if (member.is_me) Typeface.BOLD else Typeface.NORMAL
        return TextView(this).apply {
            text = member.name.take(14)
            textSize = 11f
            setTypeface(null, style)
            setTextColor(if (member.is_me) Color.WHITE else Color.parseColor("#D1D5DB"))
            setBackgroundColor(bg)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp(), 0, 4.dp(), 0)
            layoutParams = LinearLayout.LayoutParams(nameW, rowH)
            isSingleLine = true
        }
    }

    private fun makeCell(
        text: String,
        w: Int, h: Int,
        bgColor: Int,
        textColor: Int,
        bold: Boolean,
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = 11f
        setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(textColor)
        setBackgroundColor(bgColor)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(w, h).apply {
            setMargins(1.dp(), 1.dp(), 0, 0)
        }
    }

    private fun cellColor(day: DayInfo, schedule: Map<String, String>): Int {
        if (day.isHoliday) return colorHoliday
        return when (schedule[day.date] ?: "work") {
            "off" -> colorOff
            else  -> colorWork
        }
    }

    private fun formatMonthTitle(month: String): String = runCatching {
        LocalDate.parse("$month-01")
            .format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")))
            .replaceFirstChar { it.uppercase() }
    }.getOrDefault(month)

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
