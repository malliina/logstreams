create table admins
(
    email      varchar(128)                              not null primary key,
    language   varchar(64)                               not null,
    created_at timestamp(3) default current_timestamp(3) not null
);
