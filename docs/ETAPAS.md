# Etapas

Cada etapa termina en algo que se puede demostrar. Las tres primeras forman el MVP.

> **Este archivo es la única fuente de verdad sobre la numeración.** Los demás documentos y los
> comentarios del código apuntan aquí. Si una etapa cambia de alcance, se cambia aquí y en ningún
> otro sitio.

## Resumen

| # | Etapa | Estado | Qué demuestra |
| --- | --- | --- | --- |
| 1 | Esqueleto | **completa** (salvo Redis, innecesario todavía) | un comando es un hecho visible: Postgres, Kafka y el WebSocket dicen lo mismo |
| 2 | Agente buscador | siguiente | los nodos aparecen solos desde OpenAlex, deduplicados, dentro de un presupuesto |
| 3 | Criterios y evaluación | pendiente | la IA propone con evidencia verificable y la persona decide |
| 4 | Equipo | pendiente | varias personas sobre el mismo tablero, con roles |
| 5 | Más vistas | pendiente | aristas de cita, términos, línea de tiempo y embudo |
| 6 | Web general y síntesis | pendiente | fuentes no académicas y borrador de revisión |

---

## Etapa 1 — Esqueleto

**Objetivo.** Que el circuito completo sea real antes de que tenga contenido: comando por REST,
transacción en PostgreSQL, evento externalizado a Kafka, hecho reenviado por WebSocket y tablero
actualizado en otra pestaña.

**Incluye**

- [x] `compose.yaml` con PostgreSQL 18 y Kafka 4.3 en KRaft, un nodo
- [x] `criba-api` como monolito modular: `board`, `realtime`, `shared`
- [x] Esquema `V1`: `board`, `node`, `node_decision`, `criteria_version`
- [x] Sobre de evento con `eventId`, `seq`, `schemaVersion`, `actor`, `correlationId`
- [x] Externalización a `board.events` con clave `board_id` y retención de 30 días
- [x] Máquina de estados con motivo obligatorio al descartar y transiciones probadas
- [x] Secuencia por tablero dentro de la misma transacción
- [x] Idempotencia de decisiones por `idempotencyKey`
- [x] STOMP en `/topic/boards/{boardId}`
- [x] Lienzo SVG con pan, zoom, selección y estilo por estado
- [x] Vista de tabla, barra lateral con conteo por estado y panel de detalle
- [x] Historial de decisiones por nodo
- [x] Interfaz vestida según `docs/DISENO.md`

**Fuera a propósito**

- Redis: solo hace falta como caché de OpenAlex, y eso es Etapa 2.
- Outbox propio: lo hace el registro de publicaciones de Modulith.
- Aristas y posiciones calculadas: Etapa 5.

**Definición de hecho** (verificada el 8 de noviembre de 2026)

1. `docker compose up -d` levanta Postgres y Kafka, y el API conecta.
2. `POST /api/boards/{id}/nodes` responde 201 y el tópico `board.events` muestra el evento con su
   forma final.
3. Dos pestañas: cambiar el estado en una se refleja en la otra en menos de un segundo.
4. `POST .../decisions` sin `reason` devuelve 422 y no escribe nada.
5. `ApplicationModules.verify()` pasa y las transiciones tienen prueba.

---

## Etapa 2 — Agente buscador

**Objetivo.** Que los nodos lleguen solos, en orden y sin cobrarle de más al usuario.

Se construye en tres cortes, cada uno utilizable:

**2.1 Consumidor esqueleto.** `criba-agents` como segundo deployable: consume `agent.tasks`,
deduplica en `processed_event`, hace ack y no hace nada más. Es el worker real sin lógica, y
demuestra consumer groups, offsets, rebalanceo y deduplicación.

**2.2 Planificador y conector de OpenAlex.** El planificador divide la pregunta en subconsultas; el
conector las ejecuta con rate limit y normaliza DOI y URL.

**2.3 Aprobación y presupuestos.** `SearchPlanned` → aprobación con `maxUsd` y `maxNodes` →
`SearchRequested`. Reserva atómica contra el presupuesto de la operación y el diario de la key,
barredor de reservas huérfanas y `PAUSED_BUDGET` (ver `PROPUESTA-ARQUITECTURA.md` §4).

**Qué demuestra.** Los nodos aparecen en el tablero conforme llegan, deduplicados por DOI, y ninguna
llamada se hace sin reserva previa.

---

## Etapa 3 — Criterios y evaluación

**Objetivo.** Que la transparencia se sostenga con un LLM real.

- Criterios versionados en dos capas: reglas automáticas y preguntas de juicio.
- Evaluador con salida estructurada y evidencia validada contra el abstract.
- Heatmap fuentes × criterios.
- Aceptar o descartar con motivo (la mecánica ya existe desde la Etapa 1).
- Exportación a BibTeX y RIS.

**Decisiones abiertas antes de empezar:** la escala de respuesta (`Sí` / `Parcial` / `No` frente a
0–1) y si la sugerencia la calcula el código a partir de la importancia de cada criterio. Ver
`docs/DISENO.md`.

---

## Etapa 4 — Equipo

Miembros y roles por tablero, presencia y comentarios en nodos. Hasta aquí el tablero es de una
persona: `actor` ya viaja en cada evento, así que no hay que reescribir historia.

---

## Etapa 5 — Más vistas

Aristas de cita y co-citación, posiciones calculadas en `views`, nube de términos como filtro, línea
de tiempo y embudo por estado.

---

## Etapa 6 — Web general y síntesis

Conector de búsqueda web con la key del usuario, verificación de citas y borrador de revisión de
literatura.

---

## Nota de numeración

Los borradores anteriores de `PROPUESTA-ARQUITECTURA.md` usaban una numeración propia
(`0`, `0.5`, `1`, `2`, `3`) que no coincidía con la de este archivo. Queda descartada:

- «Etapa 0» del borrador → **Etapa 1**
- «Etapa 0.5» del borrador → **2.1**
- «Etapa 1» del borrador mezclaba dos cosas: el lienzo ya está en la **Etapa 1**; criterios y
  heatmap son la **Etapa 3**
- «Etapa 2» del borrador → **Etapa 2**, con los presupuestos en **2.3**
- «Etapa 3» del borrador → **Etapa 3**
