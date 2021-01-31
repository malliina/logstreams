create table USERS
(
    USER      varchar(100) not null primary key,
    PASS_HASH varchar(254) not null
) charset = utf8mb4;

create table TOKENS
(
    USER  text not null,
    TOKEN text not null
) charset = utf8mb4;

create table LOGS
(
    ID         bigint auto_increment primary key,
    APP        varchar(100)                              not null,
    ADDRESS    text                                      not null,
    TIMESTAMP  timestamp(3) default CURRENT_TIMESTAMP(3) not null on update CURRENT_TIMESTAMP(3),
    MESSAGE    text                                      not null,
    LOGGER     text                                      not null,
    THREAD     text                                      not null,
    LEVEL      bigint                                    not null,
    STACKTRACE text                                      null,
    ADDED      timestamp(3) default CURRENT_TIMESTAMP(3) not null,
    constraint FK_LOG_USER foreign key (APP) references USERS (USER) on update cascade
) charset = utf8mb4;

create index LOGS_ADDED_IDX on LOGS (ADDED);

create index LOGS_APP_ADDED_IDX on LOGS (APP, ADDED);
