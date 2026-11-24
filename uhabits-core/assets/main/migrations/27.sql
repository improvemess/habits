create table OwnerIdentity (id integer primary key check (id = 1), owner_id text not null, owner_secret text not null);
