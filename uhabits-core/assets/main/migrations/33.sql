alter table Habits add column calendar_week integer not null default 0;
update Habits set calendar_week = 1 where type = 1 and freq_den = 7;
