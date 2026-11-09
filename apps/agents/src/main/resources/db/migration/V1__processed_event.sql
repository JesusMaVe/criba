-- Un evento ya procesado por un consumidor. La entrega es at-least-once y la clave primaria es lo
-- que vuelve gratis a un duplicado (ver docs/PROPUESTA-ARQUITECTURA.md §3).
create table agents.processed_event (
    consumer     text        not null,
    event_id     uuid        not null,
    processed_at timestamptz not null default now(),
    primary key (consumer, event_id)
);
