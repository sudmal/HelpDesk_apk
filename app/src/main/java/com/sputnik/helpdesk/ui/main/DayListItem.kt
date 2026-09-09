package com.sputnik.helpdesk.ui.main

import com.sputnik.helpdesk.data.db.ConnectionRequestEntity
import com.sputnik.helpdesk.data.db.TicketEntity

/**
 * One row in a day tab (Просрочено/Сегодня/Завтра). Regular tickets and назначенные
 * заявки на подключение живут в одной ленте — как на вебе и в PWA. Подключение
 * ведёт только в карточку [com.sputnik.helpdesk.ui.connections.ConnectionDetailActivity],
 * свайп-жестов у него нет.
 */
sealed class DayListItem {
    data class TicketItem(val ticket: TicketEntity) : DayListItem()
    data class ConnectionItem(val connection: ConnectionRequestEntity) : DayListItem()

    /** Время выезда — общий ключ для сортировки объединённой ленты. */
    val scheduledAt: String
        get() = when (this) {
            is TicketItem -> ticket.scheduledAt ?: ""
            is ConnectionItem -> connection.scheduledAt ?: ""
        }

    /** Адрес — ключ для режима сортировки «По адресу». */
    val address: String
        get() = when (this) {
            is TicketItem -> ticket.addressFull ?: ""
            is ConnectionItem -> connection.addressString ?: ""
        }
}
