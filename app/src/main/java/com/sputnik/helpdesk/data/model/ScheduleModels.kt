package com.sputnik.helpdesk.data.model

data class ScheduleResponse(
    val brigade: BrigadeInfo,
    val months: List<MonthSchedule>,
    val synced_at: String?,
)

data class BrigadeInfo(val id: Int, val name: String)

data class MonthSchedule(
    val month: String,
    val days: List<DayInfo>,
    val members: List<MemberSchedule>,
    val my_schedule: Map<String, String>,
)

data class DayInfo(
    val date: String,
    val day: Int,
    val dow: String,
    val isWeekend: Boolean,
    val isHoliday: Boolean,
    val holidayName: String?,
)

data class MemberSchedule(
    val id: Int,
    val name: String,
    val is_me: Boolean,
    val schedule: Map<String, String>,
)
