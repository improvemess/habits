alter table Habits add column calendar_period integer not null default 0;
update Habits set calendar_period = 1 where type = 1 and (freq_den = 7 or freq_den = 30 or freq_den = 31);
