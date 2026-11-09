-- Un evento ya procesado por un consumidor de la API. La entrega es at-least-once: la clave primaria
-- vuelve gratis a un duplicado (ver docs/PROPUESTA-ARQUITECTURA.md §3).
create table processed_event (
    consumer     text        not null,
    event_id     uuid        not null,
    processed_at timestamptz not null default now(),
    primary key (consumer, event_id)
);
