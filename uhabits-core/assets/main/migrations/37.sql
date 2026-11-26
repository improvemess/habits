alter table Habits add column created_on integer;
update Habits set created_on = coalesce(
    (select min(timestamp) from Repetitions where Repetitions.habit = Habits.id),
    strftime('%s', 'now', 'start of day') * 1000
);
