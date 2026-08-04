package com.marcogn.gamereviewer.data.local

import androidx.room.TypeConverter
import com.marcogn.gamereviewer.data.local.entity.ProConType
import com.marcogn.gamereviewer.domain.model.BacklogHistoryEventType
import com.marcogn.gamereviewer.domain.model.BacklogItemStatus
import com.marcogn.gamereviewer.domain.model.ReviewStatus
import java.time.Instant
import java.time.LocalDate

class Converters {

    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromEpochMilli(epochMilli: Long?): Instant? = epochMilli?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun instantToEpochMilli(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromStatusName(name: String): ReviewStatus = ReviewStatus.valueOf(name)

    @TypeConverter
    fun statusToName(status: ReviewStatus): String = status.name

    @TypeConverter
    fun fromProConTypeName(name: String): ProConType = ProConType.valueOf(name)

    @TypeConverter
    fun proConTypeToName(type: ProConType): String = type.name

    @TypeConverter
    fun fromBacklogItemStatusName(name: String): BacklogItemStatus = BacklogItemStatus.valueOf(name)

    @TypeConverter
    fun backlogItemStatusToName(status: BacklogItemStatus): String = status.name

    @TypeConverter
    fun fromBacklogHistoryEventTypeName(name: String): BacklogHistoryEventType = BacklogHistoryEventType.valueOf(name)

    @TypeConverter
    fun backlogHistoryEventTypeToName(type: BacklogHistoryEventType): String = type.name
}
