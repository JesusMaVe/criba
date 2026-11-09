# Propuesta de arquitectura — Arranque de Criba (revisión 3)

Nov 8, 2026 · reemplaza las revisiones 1 y 2. El documento original (`Arquitectura — App de research transparente con IA (Java + Kafka).md`) sigue siendo el *qué* y el *por qué*; esto es el *cómo*.

**Revisión 2 — cuatro correcciones de fondo:**

1. **La auditoría canónica pasa a PostgreSQL.** La v1 decía "el log canónico es Kafka" y a la vez ponía 90 días de retención con `delete`. El día 91 la auditoría desaparecía.
2. **La semántica de entrega es at-least-once.** Modulith guarda el registro en la transacción, pero **el envío a Kafka es asíncrono y posterior al commit**. Hay duplicados, y el consumidor deduplica.
3. **El gasto del usuario es un riesgo de primer orden**, no una nota al pie de riesgos.
4. **De 1 a 2 deployables**, para que el lado del consumidor de Kafka sea real y no un `@Externalized`.

**Revisión 3 — cierra las cuatro preguntas abiertas y arregla el mecanismo de reserva.** El presupuesto de dos niveles queda como lo planteaste, con tres huecos corregidos en §4.5: los tokens de entrada no se conocen antes de la llamada, el redondeo a 6 decimales subestima en modelos baratos, y `max_tokens` acota la salida pero truncar también se cobra. Y un cuarto, específico de las búsquedas: el estimado aprobado **no acota** cuántos nodos van a llegar (§4.7).

---

## 1. Veredicto

**Se queda:** el dominio (la IA sugiere, la persona decide; historial inmutable, criterios versionados, procedencia por nodo), las dos puertas (REST para comandos, WebSocket para hechos), las vistas calculadas con código, y `board` como única autoridad del estado.

**Se corrige:** dónde vive la auditoría (§2), la semántica de entrega (§3), el control del gasto (§4), y cuántos deployables hay (§5).

---

## 2. Dónde vive cada cosa: tres planos, no uno

El error de la v1 fue meter las tres cosas en Kafka. Son tres problemas con tres dueños distintos.

| Plano | Qué guarda | Dónde vive | Garantía |
| --- | --- | --- | --- |
| **Historia de decisiones** (auditoría) | por qué un nodo está en el tablero: quién decidió, cuándo, con qué motivo, contra qué versión de criterios, con qué evidencia, y qué evaluaciones se rechazaron | PostgreSQL: `node_decision`, `node_evaluation`, `criteria_version`, `node_provenance` | **permanente**, append-only, backup + PITR |
| **Estado actual** | cómo se ve el tablero ahora | PostgreSQL: `board`, `node` | reconstruible; proyección mutable |
| **Integración** | trabajo async entre la API y el worker de agentes | Kafka: `agent.tasks`, `agent.results` | **7 días**, at-least-once |

Fuera de los tres planos queda `board.events`: el rastro de todo lo que la API publica. No es la auditoría y hoy nadie lo consume; se detalla en §5, incluida la condición para apagarlo.

**Lo que esto resuelve:** la retención de Kafka deja de ser una decisión de durabilidad y pasa a ser una perilla operativa. La contradicción de los 90 días desaparece porque ya no hay nada que archivar: el archivo es Postgres, que es donde ibas a hacer las consultas de la vista de auditoría (filtros por tablero, por actor, por fecha, joins con `node`).

**Lo que esto corrige del ADR 1:** su justificación actual —"el log retenido permite replay y reconstruir el historial"— deja de ser cierta. Las razones reales:

- Es la frontera correcta para trabajo lento, facturable y reintentable, sin acoplar la disponibilidad de la API a la del proveedor de LLM.
- Permite un consumidor propio con su ciclo de vida, su concurrencia y su DLQ, separado del proceso web.
- Aprender el lado del consumidor es un objetivo explícito del proyecto. Decirlo no debilita el ADR; lo hace honesto.

**Un requisito que la v1 no tenía:** "inmutable" tiene que ser un hecho, no una convención. El rol de la aplicación va con `GRANT SELECT, INSERT` sobre las tablas de auditoría y sin `UPDATE`/`DELETE`. Si un `UPDATE` accidental puede tocar `node_decision`, la auditoría es una promesa de palabra. Y la regla de modelado que la sostiene: **todo hecho visible para el usuario que tenga un "por qué" produce una fila inmutable en la misma transacción**. Lo que no lo tenga es estado, no historia — y si algún día necesita historia propia (por ejemplo, cambios de título del tablero), necesita su tabla, no un `updated_at`.

---

## 3. La entrega es at-least-once, y eso se diseña

**Qué garantiza Modulith exactamente.** Con `spring-modulith-events-jdbc` más `spring-modulith-events-kafka`:

1. Al publicar un evento dentro de una transacción, se inserta una fila en `event_publication` **en esa misma transacción**, una por listener y una por externalización.
2. Al hacer commit, un listener asíncrono envía a Kafka.
3. Cuando Kafka confirma (`acks=all`), se marca la publicación como completada en otra transacción.

El hueco está entre 2 y 3, y también en cualquier fallo de 2. Si el proceso muere después de que Kafka aceptó el mensaje pero antes de marcar completada, la publicación queda incompleta y se reenvía: **duplicado**. Es una ventana angosta, no teórica.

Detalles que hay que dejar escritos:

- `spring.modulith.events.externalization.serialize-externalization=true` preserva el orden de un mismo tablero. **No** evita duplicados; son dos problemas distintos.
- `spring.modulith.events.republish-outstanding-events-on-restart` está en `false` por defecto. Déjalo así (§4 explica por qué el reenvío automático es peligroso con dinero de por medio).
- `spring.modulith.events.completion-mode=DELETE` en las publicaciones completadas, o la tabla crece sin techo. Modulith expone `CompletedEventPublications` para purgar por antigüedad.
- El `event_id` se genera **una vez**, al construir el evento, no al serializarlo. Si se regenera en un reenvío, la deduplicación del otro lado no sirve de nada.

**La consecuencia operativa:** todo consumidor deduplica por `event_id`, y no con un `select` previo (hay carrera) sino con la restricción:

```sql
insert into processed_event(consumer, event_id, processed_at)
values (:consumer, :eventId, now())
on conflict (consumer, event_id) do nothing;
-- 0 filas → ya se procesó, se hace ack y se sigue
```

Insertar en la **misma transacción** que el efecto secundario. Con eso un duplicado es gratis y el consumidor es idempotente de verdad, no "casi siempre".

---

## 4. El dinero del usuario: reserva, límites y reanudación

### 4.1 El escenario que hay que evitar

El usuario aprueba una operación. Llega una tarea de evaluación. El worker llama al proveedor, la llamada se completa y cuesta $0.02. El proceso se reinicia —un deploy, un `docker compose down`, un OOM— antes de que la publicación se marque completada. Al arrancar, la publicación se reenvía. Segunda llamada, otros $0.02. Nadie se entera hasta la factura del usuario, que es justo la persona a la que le prometiste transparencia.

Con la key del usuario y sin crédito propio, esto no es un bug de rendimiento: es el producto cobrándole de más por algo que no puede ver.

### 4.2 Las dos reglas que no se negocian

**Nunca una llamada dentro de una transacción.** El listener hace tres pasos separados: reclama y reserva (transacción corta, commit), llama al proveedor (sin transacción), liquida y guarda el resultado (transacción corta). Una transacción abierta 60 segundos agota el pool y bloquea filas.

**Para el dinero, at-most-once.** Kafka da at-least-once; es correcto para escrituras idempotentes y catastrófico para llamadas facturadas. Por eso: **ack después del reclamo, no después del cobro.** Si el proceso muere a mitad de la llamada, el registro de Kafka se pierde pero queda la fila del intento en `IN_FLIGHT`, y se recupera desde Postgres de forma explícita. Un trabajo perdido y rehacible es mejor que un cobro duplicado que nadie ve.

### 4.3 Idempotencia por intento

`(node_id, criteria_version, attempt)` único, con `status` (`CLAIMED`, `IN_FLIGHT`, `COMPLETED`, `FAILED`, `ABANDONED`, `FAILED_TRUNCATED`) y `provider_request_id` para reconciliar contra el panel del proveedor. El reclamo es un `insert ... on conflict do update ... where status in ('FAILED','ABANDONED')`: reintentar es posible, repetir no. La fila la escribe el worker; `node_evaluation` —la de auditoría— la escribe la API cuando recibe `NodeScored` (§5).

### 4.4 Dos presupuestos, una transacción

La regla, tal como iría en el ADR:

> Toda llamada que gasta la key del usuario reserva su costo máximo, en una sola transacción, contra dos presupuestos: el de la operación que la originó y el diario de esa API key. Si cualquiera de los dos no alcanza, la llamada no se hace.

| Nivel | Quién lo fija | Para qué sirve | ¿MVP? |
| --- | --- | --- | --- |
| Operación (una búsqueda o una re-evaluación) | el usuario, al aprobar el plan con su estimación | que nunca se gaste más de lo que vio y aprobó | sí |
| Diario por API key | el usuario, en su perfil, con un valor por defecto | proteger su cartera aunque haya bugs, reinicios o varias operaciones a la vez | sí |
| Por tablero | — | — | **no** |

Sin límite por tablero, por el ADR 8: cada acción la paga quien la lanza con su propia key, y un tablero compartido no tiene un pagador único. Si algún día hay planes de equipo, se agrega como tercer nivel con la misma mecánica. Y "operación" y no "búsqueda" porque cambiar los criterios también gasta: re-evalúa todos los nodos activos, y pasa por la misma puerta.

**Una consecuencia que tu texto no cubre: dónde viven esas dos tablas.** Como solo el worker gasta, solo el worker puede reservar de forma atómica; si las pusiera la API habría que inventar una llamada síncrona por cada evaluación. Recomiendo que `key_daily_budget` y `operation_budget` vivan en el esquema del worker, y que los límites **viajen en la tarea**: el payload de `agent.tasks` lleva `BudgetPolicy{ approvedUsd, maxNodes, dailyLimitUsd }`, y el worker hace *upsert* del límite diario al recibirla. El perfil sigue editándose en la API; lo que cambia es que la API no es quien lo aplica. Las operaciones nuevas y los aumentos de límite viajan como eventos nuevos, no como actualizaciones en sitio: es el mismo contrato de antes, con estado que se transporta.

### 4.5 El peor caso tiene que ser un tope de verdad

La reserva es el peor caso: `tokens_entrada × precio_entrada + max_tokens × precio_salida`, y como `max_tokens` es un tope duro, el costo real no puede pasar de lo reservado. Esa frase es casi cierta. Tres matices, y cada uno es una fuga:

**(a) Los tokens de entrada no se conocen antes de la llamada.** Dependen del tokenizador, del chat template del proveedor y de lo que el proveedor añada por su cuenta. Si el real supera el estimado, `real > reservado` y el límite diario se puede rebasar. Arreglo: estimar con margen y tratarlo como techo.

```
estimado_entrada = countTokens(prompt)      // jtokkit si el modelo es de la familia de tiktoken
reserva = ceil((estimado_entrada × 1.15) + 64) × p_entrada + max_tokens × p_salida
```

El `+64` es el sobrecoste fijo del template; el 15% es colchón. Y un guardia: si al liquidar `real > reservado`, se liquida igual con el real —nunca se maquilla el libro— y se emite `RESERVATION_OVERRUN` con el delta, para ajustar el margen. Si no hay tokenizador para el modelo, `ceil(caracteres / 2.5)` en español, no `/3.5`: el español tokeniza peor y subestimar aquí significa reservar de menos.

**(b) El redondeo a 6 decimales subestima en modelos baratos.** `numeric(12,6)` está bien para el total y mal para el precio por token: $0.15 por millón es 0.00000015, que a 6 decimales es cero. Arreglo: guardar el precio **por millón** (`numeric(12,6)`, que es como lo publican los proveedores) y calcular `tokens × precio / 1_000_000` con escala alta, redondeando **una sola vez y hacia arriba** (`CEILING`). Hacia arriba en la reserva y en la liquidación, para que `real ≤ reservado` siga siendo cierto: `ceil(real) ≤ ceil(max)` porque `real ≤ max`. Ser conservador con un guardián de dinero es la dirección correcta.

**(c) `max_tokens` acota la salida, y truncar también se cobra.** Si el modelo se topa con el límite, devuelve JSON truncado: pagaste y no tienes resultado. Arreglo: dimensionar `max_tokens` a partir del esquema (una evaluación de 12 campos con motivo de dos frases cabe en ~1,024 tokens; deja margen), tratar `finish_reason=length` como `FAILED_TRUNCATED` —se liquida con el costo real y **no** se reintenta solo—, y verificar si el proveedor cobra los tokens de razonamiento dentro de `max_tokens` o aparte, porque en ese caso el tope real es menor de lo que crees.

### 4.6 La mecánica, con los detalles que faltaban

Todo esto ocurre en una transacción antes de la llamada, en este orden, siempre el mismo:

```sql
-- 1. El intento. Si ya existe uno COMPLETED, se abandona: no se vuelve a pagar.
insert into llm_attempt (operation_id, node_id, criteria_version, attempt, status)
values (:op, :node, :cv, :attempt, 'CLAIMED')
on conflict (node_id, criteria_version, attempt) do nothing
returning id;                                   -- 0 filas → revisar el existente

-- 2. La fila del día tiene que existir antes de tocarla. Sin esto, la primera
--    operación del día falla con BUDGET_EXHAUSTED por un UPDATE sin filas.
insert into key_daily_budget (credential_id, day, limit_usd, spent, reserved)
values (:cred, :hoy, :limite_del_perfil, 0, 0)
on conflict (credential_id, day) do nothing;

-- 3. Presupuesto diario de la key. Siempre primero: es la fila más caliente
--    y un orden constante evita deadlocks entre operaciones concurrentes.
update key_daily_budget
   set reserved = reserved + :max
 where credential_id = :cred and day = :hoy
   and spent + reserved + :max <= limit_usd
returning reserved;                             -- 0 filas → BUDGET_EXHAUSTED

-- 4. Presupuesto de la operación. Si falla, rollback de todo.
update operation_budget
   set reserved = reserved + :max
 where operation_id = :op
   and spent + reserved + :max <= approved_usd
returning reserved;                             -- 0 filas → BUDGET_EXHAUSTED

-- 5. El intento pasa a IN_FLIGHT con su reserva anotada. Commit. Y ahora sí, la llamada.
```

Liquidar, al terminar la llamada sea cual sea el resultado, en una transacción nueva y en el mismo orden:

```sql
update key_daily_budget set spent = spent + :real, reserved = reserved - :max
 where credential_id = :cred and day = :hoy;
update operation_budget set spent = spent + :real, reserved = reserved - :max
 where operation_id = :op;
update llm_attempt set status = :status, spent_usd = :real, reserved_usd = 0
 where id = :attemptId;
```

Tres comprobaciones que conviene no omitir:

- `check (reserved >= 0)` y `check (spent >= 0)` en ambas tablas. Una reserva que se libera dos veces deja el número en negativo y ya no cuadra nunca.
- Un reintento **seguro** (los de §4.9) liquida en 0 y vuelve a reservar en el siguiente intento: así la reserva nunca se fuga por un `429`.
- Una vista de invariante que tiene que devolver **cero filas**: `reserved` del día igual a la suma de reservas de los intentos `CLAIMED`/`IN_FLIGHT`. Es la única forma de detectar una fuga sin esperar a que el usuario note que no puede buscar.

`day` es un `date` UTC, no un timestamp. `limit_usd` se copia del perfil al crearse la fila del día; cambiarlo hoy afecta la fila de hoy (bajar el límite por debajo de lo gastado simplemente bloquea) y no toca las anteriores. En la interfaz, la hora de reinicio se muestra en local: con día UTC, en Ciudad de México el corte cae a las 18:00, y eso el usuario lo tiene que ver, no adivinar.

### 4.7 Presupuesto agotado, y el cuarto hueco: las búsquedas

Cuando falla un presupuesto, la operación pasa a `PAUSED_BUDGET`. Los nodos ya evaluados se quedan; el usuario puede subir el límite y reanudar. Nunca se reintenta sola. Y el heatmap tiene que marcar explícitamente los nodos sin evaluar *por presupuesto*, no dejarlos en blanco como si no existieran: la transparencia también aplica a lo que no se hizo.

**El cuarto hueco:** `approved_usd` no acota una búsqueda, porque cuántos nodos trae OpenAlex no se sabe al aprobar. Si se aprueban $0.50 y llegan 200 nodos, o se gasta más de lo aprobado o hay que frenar a medias: la promesa "nunca más de lo que viste" es falsa en el caso más probable.

Arreglo: la aprobación fija **`maxUsd` y `maxNodes`**. Las tareas de evaluación se generan hasta el tope de nodos, y al alcanzarlo la operación va a `PAUSED_BUDGET` con "30 de 47 evaluados: ampliar". Es un mecanismo, no dos, y sin puerta nueva: reutiliza exactamente el estado y la reanudación que ya existen.

Reanudar tiene dos reglas: se re-estima y se re-reserva, y **solo se generan tareas para nodos sin evaluación `COMPLETED` en la versión de criterios vigente**. Sin la segunda, reanudar vuelve a pagar por lo ya evaluado, que es el error que este documento entero intenta evitar.

### 4.8 Reservas huérfanas

Si el proceso muere entre la reserva y la liquidación, la reserva queda viva y el presupuesto del día se ve mermado sin explicación. Va un barredor: `@Scheduled` cada minuto, busca intentos `CLAIMED`/`IN_FLIGHT` más viejos que el timeout de la llamada más un margen (5 minutos), los pasa a `ABANDONED` y libera **las dos** reservas en la misma transacción. Idempotente por `attempt_id` y condicionado al `status` (si ya se liquidó, no hace nada), porque dos barredores o un reinicio a mitad no deben liberar dos veces. El costo de esa llamada se asume perdido: es el mismo riesgo aceptado del §4.2, ahora con un número que aparece en la interfaz en lugar de esconderse.

### 4.9 Reintentar solo lo que no cobra

| Respuesta | ¿Reintentar? | Por qué |
| --- | --- | --- |
| Conexión rechazada, DNS, TLS | sí | la petición no llegó |
| `429` / rate limit | sí, con backoff | no se generó nada |
| `5xx` sin cuerpo | sí, acotado | probablemente no se generó nada |
| **Timeout de lectura** | **no** | el proveedor puede seguir generando y cobrando |
| `200` con JSON inválido o truncado | no | ya se cobró; `REJECTED_SCHEMA` o `FAILED_TRUNCATED` |

Un timeout de lectura es la trampa: parece un error de red y es una factura.

Y la concurrencia, que es la otra forma de pasarse del límite: `spring.threads.virtual.enabled=true` da virtual threads, y con `@Async` los da **sin límite** —la forma más rápida de descubrir el rate limit del proveedor. `Bulkhead` de Resilience4j de 4–8 por instancia, timeouts explícitos (conexión 5 s, lectura 60–90 s) y un deadline por tarea. Y la razón por la que el trabajo lento vive en el worker: si tardas más de `max.poll.interval.ms` sin hacer `poll`, el grupo te expulsa, hay rebalanceo y el registro se reentrega.

### 4.10 El proveedor de modelo

Decisión cerrada: **salida estructurada nativa con JSON Schema, sin tool calling**, y la idempotencia resuelta en la base. Con eso el proveedor deja de ser una decisión de arquitectura, con dos excepciones que sí lo son:

1. **Contar los tokens de entrada** para la reserva. Necesita tokenizador o heurística por modelo; es el único lugar donde el proveedor se mete en §4.5.
2. **Si el modo estricto es nativo o emulado con prompting.** Cambia la tasa de fallos y el comportamiento al truncar, y eso afecta cuánto se reintenta.

Así que el puerto es `ModelProvider.structured(prompt, jsonSchema, model, maxOutputTokens) → { json, usage, finishReason }` más `countInputTokens(...)`, y el criterio de aceptación de un proveedor para el MVP no es una preferencia sino dos condiciones: **modo estricto de esquema y `usage` en la respuesta**. Si le falta cualquiera de las dos, no entra, no importa qué key tengas a mano.

Detalles de implementación que ahorran una tarde:

- El esquema va con `additionalProperties: false` y todos los campos requeridos; los opcionales se modelan como nulables. En Java eso choca con `Optional` en records de salida estructurada: usa un record con campos nulables solo para el borde y un record de dominio aparte.
- Valida el JSON contra el esquema **de todas formas**. Que el proveedor lo garantice no es una razón para confiar.
- Sin streaming. La contabilidad necesita el `usage` final, y la salida estructurada necesita el JSON completo.
- Precios en tabla versionada: `llm_price(provider, model, input_per_mtok, output_per_mtok, effective_from)`. Cada liquidación guarda `price_version`, para que un cambio de precio no reescriba la historia. Modelo desconocido ⇒ **no se llama**: fallar cerrado, nunca adivinar.
- Cuando el volumen duela (Etapa 3), agrupar 10 nodos por llamada baja el costo de forma notable, a cambio de que la idempotencia pase a ser por lote y la validación de evidencia por nodo se complique.

### 4.11 Resumen de controles

| Control | Previene |
| --- | --- |
| Reclamo antes de llamar | doble cobro por reintento |
| Ack temprano + ledger | reentrega de trabajo facturado |
| Reserva al peor caso, con margen de entrada | pasarse del límite aprobado o del diario |
| Redondeo hacia arriba | subestimar en modelos baratos |
| `maxNodes` junto a `maxUsd` | que una búsqueda se salga por volumen |
| Política de reintento por tipo de error | cobrar dos veces por un timeout |
| Barredor idempotente | reservas huérfanas tras un crash |
| Bulkhead + timeouts | 429 en cascada y llamadas huérfanas |
| Vista de invariante + `RESERVATION_OVERRUN` | que todo lo anterior falle en silencio |

---

## 5. El consumidor es real: dos deployables

**Por qué la regla de la v1 estaba mal.** Era correcta para minimizar trabajo y equivocada para este proyecto. Con "ningún módulo consume un tópico que este deployable produce", en el MVP no había un solo consumidor. Elegiste Kafka por razones que incluyen aprender consumer groups, particiones, idempotencia, DLQ y reintentos. Un diseño que las deja fuera no cumple el objetivo, por limpio que sea.

**Por qué dos deployables es además lo correcto técnicamente:**

- El trabajo de agentes es lento, facturable y limitado por rate limit externo. No debe compartir JVM, thread pool ni *health check* con el proceso que atiende al navegador.
- **La API queda como única escritora de las tablas de auditoría.** El worker no toca la base de la API: propone con eventos y la API valida y persiste. Sin base compartida, sin transacción distribuida, y "la IA sugiere, la persona decide" deja de depender de la disciplina de un programador.
- El worker consume `agent.tasks` y publica `agent.results`; la API consume `agent.results`. Ambas partes tienen consumer group, offsets, reintentos y DLQ.

```
┌──────────────── navegador ────────────────┐
│  SPA TanStack: REST + STOMP               │
└────────┬───────────────────────┬──────────┘
         │                       │
┌────────▼───────────────────────▼──────────────────────┐
│ criba-api — Spring Boot 4.1, monolito modular         │
│ board · views · realtime · shared                     │
│ única escritora de auditoría       group: api-results │
└──────┬──────────────────────────────────▲─────────────┘
       │ agent.tasks                      │ agent.results
       ▼                                  │
┌──────────────────────────────────────────┴────────────┐
│ criba-agents — Spring Boot 4.1                        │
│ planner · connectors · evaluator · budget             │
│ group: agents                                         │
└───────────────────────────────────────────────────────┘
              PostgreSQL (2 esquemas) · Kafka 4.3
```

### Tópicos

| Tópico | Clave | Contenido | Produce | Consume | Retención |
| --- | --- | --- | --- | --- | --- |
| `agent.tasks` | `board_id` | comandos al worker: `SearchRequested`, `ScoreRequested` + `BudgetPolicy` | api | agents (`agents`) | 7 días, `delete` |
| `agent.results` | `board_id` | propuestas: `SearchPlanned`, `SourceFound`, `NodeScored`, `NodeEvaluationRejected`, `BudgetExhausted`, `SearchFailed` | agents | api (`api-results`) | 7 días, `delete` |
| `agent.tasks.dlq` | `board_id` | tareas que fallaron de forma no recuperable | api (recoverer) | — | 14 días |
| `agent.results.dlq` | `board_id` | resultados ilegibles tras reintentos | agents (recoverer) | — | 14 días |

`board.events` **sigue existiendo**, con otro papel: el rastro de todo lo que la API publica, útil para depurar y para reprocesar una ventana con un prompt nuevo. Nadie lo consume hoy y eso está bien — con una condición: si en la Etapa 4 sigue sin consumidor, se apaga. Un tópico que nadie lee y que no se cuestiona es exactamente lo que conviene no dejar crecer. Retención 30 días, `delete`.

### Qué se aprende, en concreto

- Asignación de particiones y rebalanceo: qué pasa al matar un worker a mitad de un lote, y por qué `max.poll.interval.ms` importa más que el timeout del cliente HTTP.
- Diferencia entre *at-least-once* y *efectivamente una vez*: no se compra con configuración, se construye con una restricción única en la base.
- Reintento bloqueante vs. tópicos de reintento: por qué el segundo no bloquea la partición y cuándo el primero basta.
- DLQ que no es un basurero: qué headers hacen falta (`kafka_dlt-exception-fqcn`, tópico original, partición, offset) para poder reprocesar.
- Orden por clave: qué garantiza `board_id` y qué no, y por qué paralelizar dentro de una partición rompe el orden que creías tener.
- Lag como métrica de producto, no solo de infraestructura.

---

## 6. Otras correcciones

| # | Problema en el documento | Arreglo |
| --- | --- | --- |
| 1 | Comandos del usuario por Kafka ⇒ el `422` llega por WebSocket tres segundos después | validación síncrona en el `POST`; un hecho, un evento; se elimina `board.commands` |
| 2 | `NodeScored` en `agent.results` y en `board.events` con el mismo nombre | `NodeScored` (propuesta, descartable) vs `NodeEvaluationRecorded` (persistido, inmutable) |
| 3 | El flujo gasta créditos y tokens antes de que el usuario vea el plan | `SearchPlanned` → aprobación con `maxUsd` **y `maxNodes`** → `SearchRequested` con `BudgetPolicy` |
| 4 | `NodeMoved` en el log de auditoría | la posición es estado, no historia: `PATCH /layout` sin evento, difusión por canal efímero |
| 5 | Sin secuencia por tablero | `board.seq` en la misma transacción del hecho; el cliente descarta `seq <= lastSeq` |
| 6 | Datos de OpenAlex desactualizados | la key **no** es obligatoria; multiplica ×10 el presupuesto (≈$1/día) y sí hace falta para cursor paging; 30 req/s con key; `meta.cost_usd` y `X-RateLimit-*` ya traen la contabilidad |
| 7 | Evaluaciones rechazadas sin registro | `NodeEvaluationRejected` con el fragmento que el modelo intentó citar; estados `REJECTED_EVIDENCE`, `REJECTED_SCHEMA`, `FAILED_TIMEOUT` |
| 8 | OpenAlex y el LLM mezclados en la misma contabilidad | OpenAlex se paga con la key de la app: `app_budget` aparte, sin tocar los presupuestos del usuario |

La compactación de `board.events` de la v1 queda sin objeto: ningún tópico se compacta. Todos se borran por antigüedad, porque el archivo está en Postgres y no en el log.

---

## 7. Contratos

```java
public record EventEnvelope<T extends DomainEvent>(
    UUID eventId,            // idempotencia; se genera una sola vez, nunca al reenviar
    UUID boardId,            // partición y autorización
    long seq,                // orden por tablero; sale de Postgres, no de un contador en memoria
    int schemaVersion,       // 1 desde el primero
    String type,             // "board.NodeDecisionMade", "agent.NodeScored"
    Actor actor,             // AGENT{id, model, promptVersion} — hoy hay un solo actor
    UUID correlationId,      // un tablero, una búsqueda, una tarde de depuración
    UUID causationId,        // el evento que lo provocó
    Instant occurredAt,
    T payload
) {}
```

- JSON, sin Schema Registry. La validación es un test de contrato en CI que serializa cada fixture y lo compara contra el JSON Schema generado desde los records.
- Solo cambios retrocompatibles: campos nuevos opcionales, nunca renombrar ni cambiar tipo.
- `actor` desde el día uno aunque sea fijo: cuando llegue OAuth, el log ya tiene la columna correcta y no hay que reescribir historia.
- `correlation_id` viaja en el header de Kafka para que las trazas crucen el proceso y el tópico.
- Los eventos de resultado llevan `costUsd` y `tokens` para que la API pueda proyectar el gasto sin consultar al worker.

---

## 8. Datos

Dos esquemas en la misma instancia de PostgreSQL: `criba` (API) y `agents` (worker). Ningún proceso escribe en el esquema del otro.

| Esquema | Tabla | Cambio | Motivo |
| --- | --- | --- | --- |
| criba | tablas de auditoría | `GRANT SELECT, INSERT` al rol de la app; sin `UPDATE` ni `DELETE` | "inmutable" como hecho, no como costumbre |
| criba | `board` | + `seq bigint not null default 0`, con `update ... returning` | orden y descarte de duplicados en el cliente |
| criba | `node` | `unique (board_id, natural_key)`; `natural_key` = DOI en minúsculas, `openalex_id` o URL canónica | deduplicar es una restricción, no un `if` |
| criba | `node_evaluation` | + `attempt`, `status`, `provider_request_id`, `cost_usd`; `unique (node_id, criteria_version, attempt)` | reintentos y rechazos son hechos distintos (§4.3) |
| criba | `node_decision` | + `idempotency_key uuid unique` | doble clic no produce dos decisiones |
| criba | `operation` | nueva: `kind` (SEARCH, REEVALUATION), `credential_id` **copiado al aprobar**, `max_usd`, `max_nodes`, `status`, `spent_usd` (proyección) | la key que paga es la de quien lanza la acción, aunque se reanude después |
| criba | `processed_event` | nueva: (`consumer`, `event_id`), PK compuesta | hay consumidores reales (§3, §5) |
| criba | `event_publication` | la crea Modulith; `completion-mode=DELETE` + purga | no crece sin techo |
| criba | `outbox`, `board_event_log` | **no se crean** | las reemplaza Modulith y las tablas de auditoría |
| agents | `processed_event` | nueva: (`consumer`, `event_id`), PK compuesta | el worker deduplica en su propio esquema (§3, §5) |
| agents | `llm_attempt` | nueva: `operation_id`, `node_id`, `criteria_version`, `attempt`, `status`, `day`, `reserved_usd`, `spent_usd`, `provider_request_id`, `price_version` | idempotencia y reserva por intento; `day` es lo que permite cuadrar con el presupuesto diario |
| agents | `operation_budget` | nueva: `approved_usd`, `spent`, `reserved` | límite de la operación (§4.4) |
| agents | `key_daily_budget` | nueva: (`credential_id`, `day`) PK, `limit_usd`, `spent`, `reserved` | límite diario por key (§4.4) |
| agents | `llm_price` | nueva: `provider`, `model`, `input_per_mtok`, `output_per_mtok`, `effective_from` | precios versionados, costo reproducible |
| agents | `app_budget` | nueva: `provider` (OPENALEX), `day`, `spent_usd`, `limit_usd` | costo de la app, separado del del usuario |

Máquina de estados del nodo, sin cambios, con una precisión: `descartado` no es un sumidero. La decisión guarda `from_state`, así que revertir es una decisión nueva con `from = descartado`. Transición fuera de la tabla ⇒ `409`, un test por par permitido y uno por par prohibido.

`operation.max_usd` y `operation_budget.approved_usd` son el mismo número con dos dueños: uno es lo que el usuario aprobó y se muestra en la interfaz, el otro es lo que se aplica al reservar. Es duplicación deliberada y hay que tratarla como tal: si divergen, manda el segundo y suena una alarma, porque significa que una tarea viajó sin su `BudgetPolicy` o que se aprobó un aumento que no llegó al worker.

Estados de `operation`: `PLANNED` → `APPROVED` → `RUNNING` → `COMPLETED`, con `PAUSED_BUDGET`, `CANCELLED` y `FAILED` como salidas. Solo `PAUSED_BUDGET` es reanudable.

---

## 9. Etapas

El plan por etapas vive en **[`ETAPAS.md`](ETAPAS.md)**, que es la única fuente de verdad sobre la
numeración. Aquí solo queda lo que la arquitectura condiciona:

- **Etapa 1 (Esqueleto)** — completada. El circuito comando → Postgres → Kafka → WebSocket está
  verificado con dos pestañas reales.
- **Etapa 2 (Agente buscador)** — se construye en tres cortes: el consumidor esqueleto (`2.1`), el
  planificador con OpenAlex (`2.2`) y la aprobación con presupuestos (`2.3`, §4 de este documento).
  El primer corte es el que hace real el lado del consumidor de Kafka.
- **Etapa 3 (Criterios y evaluación)** — depende de dos decisiones que siguen abiertas: la escala de
  respuesta (`Sí` / `Parcial` / `No` frente a 0–1) y si la sugerencia la calcula el código a partir
  de la importancia de cada criterio (`DISENO.md` §Decisiones).

---

## 10. Stack, con las versiones verificadas hoy

| Pieza | Versión | Nota |
| --- | --- | --- |
| Spring Boot | **4.1.0** | la estable que sirve start.spring.io; 4.2 está en RC1 |
| Spring Modulith | **2.1.1** | corresponde a Boot 4.1.x según el BOM de Initializr |
| Spring AI | **2.0.1** | GA; soporta Boot ≥4.0 <4.2. La 2.1 solo existe como milestone |
| Resilience4j | fijada explícitamente en el pom | bulkhead, time limiter y circuit breaker en las llamadas al LLM |
| Java | **25 (Temurin)** | el único LTS ≥21 instalado; JEP 491 importa porque todo es I/O bloqueante |
| Build | **Maven Wrapper** | no hay `mvn` instalado en la máquina |
| PostgreSQL | **18.6** | `JSONB`, índices únicos parciales, `PITR` para la auditoría |
| Kafka | **4.3.2** (`apache/kafka`) | un nodo KRaft; fija la versión, no uses `latest` |
| Redis | **8.10** | solo caché de OpenAlex, desde la Etapa 2 |
| Front | TanStack Router + Query + Table + Form, Vite | sin SSR; **no** TanStack Start |
| JS | **pnpm 10** vía `npm i -g pnpm` | Node 26 ya no trae corepack |

---

## 11. Decisiones cerradas y lo que queda

**Cerradas en esta revisión:**

1. **`ModelProvider` con salida estructurada nativa (JSON Schema) y sin tool calling**; la idempotencia se resuelve en la base. Qué proveedor entra primero es una decisión de qué key tienes a mano, con el criterio de aceptación de §4.10.
2. **`actor` fijo.** Sin login en el MVP, pero con `actor` en el envelope desde el evento número uno.
3. **Solo local.** Consecuencia concreta que hay que escribir: las keys del usuario van en variables de entorno, no en la base, y no hay cifrado en reposo. El disparador para cambiarlo es explícito: el día que haya una URL pública, las llaves se mudan a la base con *envelope encryption* y dejan de existir en logs. Y aunque la key viva en el entorno, la fila `credential` existe igual (id, provider, límites) para que el modelo de datos no se rehaga después.
4. **Dos presupuestos obligatorios: por operación y diario por API key.** Sin límite por tablero, por el ADR 8. "Operación" y no "búsqueda", porque cambiar criterios también gasta.

**Pendiente, y conviene decidirlo antes de la Etapa 2:**

1. **Valor por defecto del límite diario.** Propongo **$2.00** con mínimo $0.50: cubre varias decenas de evaluaciones con un modelo pequeño y evita que un bucle descontrolado llegue a una factura que duela. Que ningún número sea invisible: el diálogo de aprobación muestra el límite, lo gastado y lo que quedaría.
2. **`max_tokens` por tipo de llamada** (`max_nodes` por operación ya está decidido: **50** por defecto). Son los dos números que traducen "cuánto puede costar" a "cuánto reservo"; si quedan en el aire, el estimado no sirve.
3. **Contra qué proveedor desarrollas.** No cambia la arquitectura, pero sí los fallos que vas a probar. Elige el que tenga modo estricto de esquema, no el más cómodo.

---

## 12. Lo que no haría todavía

| No | Cuándo |
| --- | --- |
| Seis deployables | cuando un módulo tenga un cuello de botella medido, no antes |
| Bus de comandos del usuario (`board.commands`) | nunca para interacción directa |
| Schema Registry / Avro | Etapa 5, si hay consumidores fuera del equipo |
| Outbox propio | nunca: Modulith ya lo hace, con la puerta abierta a Namastack si hacen falta más garantías |
| OAuth2 / JWT | cuando haya una URL pública, no antes |
| CRDT (Yjs) | si los conflictos duelen de verdad |
| pgvector | Etapa 5; OpenAlex ya trae conceptos y referencias |
| Kubernetes | cuando haya hosting real |
| Prometheus / Grafana / OTel | Etapa 3; `/actuator/metrics` ya da el lag mientras tanto |
| Spring Cloud Stream | oculta Kafka justo cuando estás aprendiendo Kafka |
| Agrupar llamadas al LLM (10 nodos por request) | Etapa 3, cuando exista una línea base de costo por operación |

---

## 13. ADRs para agregar

| # | Decisión | Consecuencia |
| --- | --- | --- |
| 13 | La auditoría canónica vive en tablas inmutables de PostgreSQL; Kafka es integración | Kafka pasa a ser reemplazable; la durabilidad depende del backup |
| 14 | Entrega at-least-once; el consumidor deduplica por `event_id` en la misma transacción | `processed_event` y una restricción única por consumidor |
| 15 | Para trabajo facturado, ack temprano y ledger propio: nunca at-least-once sobre el dinero | se prefiere trabajo perdido y recuperable a cobro duplicado |
| 16 | `criba-agents` es un deployable separado para trabajo lento y facturable | dos consumer groups reales y un esquema por servicio |
| 17 | Comandos por REST con validación síncrona | un hecho, un evento; se elimina `board.commands` |
| 18 | La posición de un nodo es estado, no historia | `node.x/y` fuera de la auditoría |
| 19 | Evaluaciones rechazadas son hechos de primera clase | la transparencia incluye lo que el agente intentó y no pudo |
| 20 | `board.events` se apaga si en la Etapa 4 nadie lo consume | evita tópicos de adorno |
| 21 | Todo gasto reserva su peor caso contra el presupuesto de la operación y el diario de la key, en una transacción | sin reserva previa no hay llamada; el límite es un tope, no un aviso |
| 22 | Salida estructurada nativa, sin tool calling; el proveedor es configuración | el criterio de entrada es modo estricto + `usage`, no preferencia |
| 23 | Solo local: llaves en el entorno, sin cifrado en reposo | el disparador de cambio es el primer despliegue público |

---

## 14. Cierre

La v1 tenía un defecto de método más que de contenido: optimizó trabajo para un proyecto cuyo objetivo incluye aprender, y llegó a una arquitectura defendible en una revisión e inservible para lo que se está construyendo. La v2 separó los planos y escribió lo que no se ve. La v3 cierra el último hueco grande, que era el dinero: con la reserva al peor caso corregida, el tope de nodos junto al de dólares y el barredor de reservas huérfanas, la frase "nunca vas a gastar más de lo que aprobaste" deja de ser una intención y pasa a ser una propiedad que se puede probar con un test.
