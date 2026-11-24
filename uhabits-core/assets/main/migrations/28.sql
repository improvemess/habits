create table HabitPauses (
    id integer primary key autoincrement,
    habit integer not null references habits(id),
    startTimestamp integer not null,
    endTimestamp integer not null);
create index idx_habit_pauses_habit on HabitPauses(habit);
