package org.isoron.uhabits.core.database

import org.isoron.platform.io.Database
import org.isoron.platform.io.PreparedStatement
import org.isoron.platform.io.StepResult
import org.isoron.platform.io.queryLong
import org.isoron.platform.io.run
import org.isoron.uhabits.core.models.StreakUnit

data class HabitData(
    var id: Long? = null,
    var name: String = "",
    var description: String = "",
    var freqNum: Int = 1,
    var freqDen: Int = 1,
    var color: Int = 0,
    var position: Int = 0,
    var reminderHour: Int? = null,
    var reminderMin: Int? = null,
    var reminderDays: Int = 0,
    var highlight: Int = 0,
    var archived: Int = 0,
    var type: Int = 0,
    var targetValue: Double = 0.0,
    var targetType: Int = 0,
    var unit: String = "",
    var uuid: String? = null,
    var category: String = "",
    var stepSize: Double = 1.0,
    var negativeSince: Long? = null,
    var calendarPeriod: Int = 0,
    var streakUnit: Int = StreakUnit.AUTO_ID
)

class HabitRepository(private val db: Database) {
    private val findAllStmt by lazy {
        db.prepareStatement(
            """SELECT id, name, description, freq_num, freq_den, color,
               position, reminder_hour, reminder_min, reminder_days, highlight,
               archived, type, target_value, target_type, unit, uuid, category, step_size, negative_since, calendar_period, streak_unit
               FROM Habits ORDER BY position"""
        )
    }

    private val insertStmt by lazy {
        db.prepareStatement(
            """INSERT INTO Habits(name, description, freq_num, freq_den,
               color, position, reminder_hour, reminder_min, reminder_days,
               highlight, archived, type, target_value, target_type, unit, uuid, category, step_size, negative_since, calendar_period, streak_unit)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        )
    }

    private val insertWithIdStmt by lazy {
        db.prepareStatement(
            """INSERT INTO Habits(id, name, description, freq_num, freq_den,
               color, position, reminder_hour, reminder_min, reminder_days,
               highlight, archived, type, target_value, target_type, unit, uuid, category, step_size, negative_since, calendar_period, streak_unit)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
        )
    }

    private val updateStmt by lazy {
        db.prepareStatement(
            """UPDATE Habits SET name=?, description=?, freq_num=?,
               freq_den=?, color=?, position=?, reminder_hour=?, reminder_min=?,
               reminder_days=?, highlight=?, archived=?, type=?, target_value=?,
               target_type=?, unit=?, uuid=?, category=?, step_size=?, negative_since=?, calendar_period=?, streak_unit=? WHERE id=?"""
        )
    }

    private val deleteStmt by lazy {
        db.prepareStatement("DELETE FROM Habits WHERE id = ?")
    }

    fun findAll(): List<HabitData> {
        findAllStmt.reset()
        val results = mutableListOf<HabitData>()
        while (findAllStmt.step() == StepResult.ROW) {
            results.add(readRow(findAllStmt))
        }
        return results
    }

    fun insert(data: HabitData): Long {
        if (data.id != null) {
            insertWithIdStmt.reset()
            insertWithIdStmt.bindLong(1, data.id!!)
            bindForInsert(insertWithIdStmt, data, offset = 1)
            insertWithIdStmt.step()
            return data.id!!
        }
        insertStmt.reset()
        bindForInsert(insertStmt, data)
        insertStmt.step()
        return db.queryLong("SELECT last_insert_rowid()")
    }

    fun update(data: HabitData) {
        updateStmt.reset()
        bindForInsert(updateStmt, data)
        updateStmt.bindLong(22, data.id!!)
        updateStmt.step()
    }

    fun delete(id: Long) {
        deleteStmt.reset()
        deleteStmt.bindLong(1, id)
        deleteStmt.step()
    }

    fun execSQL(sql: String) = db.run(sql)

    fun execSQL(sql: String, bind: PreparedStatement.() -> Unit) = db.run(sql, bind)

    private fun bindForInsert(stmt: PreparedStatement, data: HabitData, offset: Int = 0) {
        val o = offset
        stmt.bindText(1 + o, data.name)
        stmt.bindText(2 + o, data.description)
        stmt.bindInt(3 + o, data.freqNum)
        stmt.bindInt(4 + o, data.freqDen)
        stmt.bindInt(5 + o, data.color)
        stmt.bindInt(6 + o, data.position)
        if (data.reminderHour != null) stmt.bindInt(7 + o, data.reminderHour!!) else stmt.bindNull(7 + o)
        if (data.reminderMin != null) stmt.bindInt(8 + o, data.reminderMin!!) else stmt.bindNull(8 + o)
        stmt.bindInt(9 + o, data.reminderDays)
        stmt.bindInt(10 + o, data.highlight)
        stmt.bindInt(11 + o, data.archived)
        stmt.bindInt(12 + o, data.type)
        stmt.bindReal(13 + o, data.targetValue)
        stmt.bindInt(14 + o, data.targetType)
        stmt.bindText(15 + o, data.unit)
        if (data.uuid != null) stmt.bindText(16 + o, data.uuid!!) else stmt.bindNull(16 + o)
        stmt.bindText(17 + o, data.category)
        stmt.bindReal(18 + o, data.stepSize)
        if (data.negativeSince != null) stmt.bindLong(19 + o, data.negativeSince!!) else stmt.bindNull(19 + o)
        stmt.bindInt(20 + o, data.calendarPeriod)
        stmt.bindInt(21 + o, data.streakUnit)
    }

    private fun readRow(stmt: PreparedStatement): HabitData {
        return HabitData(
            id = stmt.getLong(0),
            name = stmt.getText(1),
            description = stmt.getText(2),
            freqNum = stmt.getInt(3),
            freqDen = stmt.getInt(4),
            color = stmt.getInt(5),
            position = stmt.getInt(6),
            reminderHour = stmt.getIntOrNull(7),
            reminderMin = stmt.getIntOrNull(8),
            reminderDays = stmt.getInt(9),
            highlight = stmt.getInt(10),
            archived = stmt.getInt(11),
            type = stmt.getInt(12),
            targetValue = stmt.getReal(13),
            targetType = stmt.getInt(14),
            unit = stmt.getText(15),
            uuid = stmt.getTextOrNull(16),
            category = stmt.getText(17),
            stepSize = stmt.getReal(18),
            negativeSince = stmt.getLongOrNull(19),
            calendarPeriod = stmt.getInt(20),
            streakUnit = stmt.getInt(21)
        )
    }
}
