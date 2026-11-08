-- Estado actual del tablero (proyección mutable).
-- La historia de decisiones vive en las tablas append-only al final de este archivo.

create table board (
    id                 uuid primary key,
    title              text        not null,
    research_question  text,
    owner_id           text        not null,
    -- Secuencia por tablero: ordena los eventos y serializa los comandos de un mismo tablero.
    seq                bigint      not null default 0,
    created_at         timestamptz not null default now()
);

create table node (
    id          uuid primary key,
    board_id    uuid        not null references board (id) on delete cascade,
    kind        text        not null check (kind in ('PAPER', 'WEB')),
    -- Identidad normalizada para deduplicar entre fuentes: doi:<doi>, openalex:<id> o title:<slug>:<año>.
    natural_key text        not null,
    doi         text,
    openalex_id text,
    url         text,
    title       text        not null,
    abstract    text,
    year        int,
    venue       text,
    state       text        not null,
    x           double precision,
    y           double precision,
    created_at  timestamptz not null default now(),
    constraint node_natural_key_unique unique (board_id, natural_key)
);

create index node_board_state_idx on node (board_id, state);

create table criteria_version (
    id         uuid        not null primary key,
    board_id   uuid        not null references board (id) on delete cascade,
    version    int         not null,
    criteria   jsonb       not null,
    created_by text        not null,
    created_at timestamptz not null default now(),
    constraint criteria_version_unique unique (board_id, version)
);

-- Auditoría: append-only. La inmutabilidad se aplica con un rol de aplicación sin
-- UPDATE/DELETE (ver docs/PROPUESTA-ARQUITECTURA.md §2); en local el usuario es el dueño.

create table node_decision (
    id              uuid        primary key,
    node_id         uuid        not null references node (id) on delete cascade,
    board_id        uuid        not null references board (id) on delete cascade,
    from_state      text        not null,
    to_state        text        not null,
    reason          text,
    actor_id        text        not null,
    criteria_version int,
    idempotency_key uuid,
    -- Secuencia del evento que produjo esta decisión: permite responder de forma
    -- idempotente al cliente que repite el comando.
    seq             bigint      not null,
    decided_at      timestamptz not null default now()
);

create index node_decision_node_idx on node_decision (node_id, decided_at desc);
create unique index node_decision_idempotency_idx
    on node_decision (node_id, idempotency_key)
    where idempotency_key is not null;
