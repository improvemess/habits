package org.isoron.uhabits.core.models.sqlite

import kotlinx.coroutines.test.runTest
import org.isoron.platform.time.getToday
import org.isoron.uhabits.core.BaseUnitTest
import org.isoron.uhabits.core.models.Pause
import org.isoron.uhabits.core.test.HabitFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SQLitePauseListTest : BaseUnitTest() {
    private val today = getToday()

    private suspend fun initDb(): SQLModelFactory {
        val db = buildMemoryDatabase()
        val factory = SQLModelFactory(db)
        modelFactory = factory
        habitList = SQLiteHabitList(factory)
        fixtures = HabitFixtures(factory, habitList)
        return factory
    }

    @Test
    fun testPausesArePersistedWithTitle() = runTest {
        val factory = initDb()
        val habit = fixtures.createEmptyHabit()
        habit.pauses.add(Pause(today.plus(1), today.plus(3), "Vacation"))
        habit.pauses.add(Pause(today.plus(10), today.plus(11)))

        // A fresh list loads the pauses from the database again
        val reloaded = SQLiteHabitList(factory).getById(habit.id!!)!!
        assertEquals(
            listOf(
                Pause(today.plus(1), today.plus(3), "Vacation"),
                Pause(today.plus(10), today.plus(11), "")
            ),
            reloaded.pauses.getAll()
        )
    }

    @Test
    fun testRemovedPauseIsGoneAfterReload() = runTest {
        val factory = initDb()
        val habit = fixtures.createEmptyHabit()
        val pause = Pause(today.plus(1), today.plus(3))
        habit.pauses.add(pause)
        habit.pauses.remove(pause)
        assertTrue(SQLiteHabitList(factory).getById(habit.id!!)!!.pauses.isEmpty())
    }

    @Test
    fun testPausesAreDeletedWithTheirHabit() = runTest {
        val factory = initDb()
        val habit = fixtures.createEmptyHabit()
        habit.pauses.add(Pause(today.plus(1), today.plus(3)))
        habitList.remove(habit)
        val rows = factory.pauseRepository.findAllByHabitId(habit.id!!)
        assertTrue(rows.isEmpty())
    }
}
