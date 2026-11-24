create table Shares (id integer primary key autoincrement, share_id text not null unique, habit_uuid text not null, server_url text not null, share_key text not null, history_start integer not null, ended integer not null default 0, created_at integer not null);
create index idx_shares_habit_uuid on Shares(habit_uuid);
create table ShareOutbox (share integer not null, kind text not null, timestamp integer not null default 0, updated_at integer not null, primary key (share, kind, timestamp));
