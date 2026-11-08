# Arquitectura — App de research transparente con IA (Java + Kafka)

Nov 8, 2026 · @Jesus Marentes

## Resumen y objetivos

La app es un tablero colaborativo de nodos (papers, artículos y páginas web) que un agente de IA llena y evalúa. El investigador decide qué nodos aceptar o descartar hasta quedarse con un conjunto definitivo. El backend es Java 21 + Spring Boot 4, y todo cambio es un evento en Kafka: ese log es la base de la transparencia.

**Problema.** Las herramientas de research con IA entregan un informe, pero no muestran por qué se eligió o descartó cada fuente. Las de mapeo de literatura (Litmaps, ResearchRabbit, Connected Papers) dibujan redes de citas, pero rara vez explican por qué cada paper está ahí.

**Propuesta de valor.**

- Cada nodo guarda su procedencia: qué consulta lo trajo, de qué fuente, qué agente lo evaluó y con qué evidencia.
- Cada decisión de aceptar o descartar queda registrada con su motivo y es reversible.
- Los criterios cambian conforme madura la idea, y cada cambio crea una versión comparable.
- El equipo ve el mismo tablero en tiempo real, como una pizarra.

**Usuarios.** Investigadores y equipos de investigación que trabajan sobre literatura académica y la web general.

**Alcance del MVP.** Búsqueda en OpenAlex, tablero de nodos con estados, criterios versionados, evaluación por un agente, heatmap de nodos × criterios y tablero compartido. Fuera del MVP: búsqueda web general, verificación automática de citas, edición colaborativa fina (CRDT) y exportación de informes.

## Conceptos de dominio

El nodo es la unidad central: todo lo demás existe para crearlo, evaluarlo o decidir sobre él.

| Concepto | Qué es | Reglas clave |
| --- | --- | --- |
| Tablero (board) | Espacio de trabajo de una investigación | Tiene miembros con rol (dueño, editor, lector) y una pregunta de investigación |
| Nodo | Un paper, artículo o página web | Identificado por DOI, ID de OpenAlex o URL normalizada; sin duplicados por tablero |
| Estado del nodo | `candidato` → `preseleccionado` → `aceptado` → `definitivo`, o `descartado` | Solo una persona cambia el estado; descartar exige motivo y es reversible |
| Criterios | Reglas de inclusión y exclusión (años, idioma, método, dataset público…) | Versionados: cada cambio crea la versión N+1, nunca se sobrescribe |
| Evaluación | Score por criterio, sugerencia (mantener, descartar o dudoso), motivo y fragmento de evidencia | La produce un agente para una versión concreta de criterios; es consultiva |
| Arista | Relación entre nodos: cita, co-citación, similitud o concepto compartido | Calculada desde metadatos, no inventada por el LLM |
| Procedencia | Consulta, fuente, agente, modelo, versión de prompt y fecha | Se guarda en cada nodo y cada evaluación |
| Decisión | Cambio de estado hecho por una persona | Registra quién, cuándo, motivo y versión de criterios vigente |

**Principio rector: la IA sugiere, la persona decide.** Ningún agente cambia el estado de un nodo. Esto mantiene la responsabilidad en el investigador y hace auditable cada paso.

## Vista general de la arquitectura

&#91;embedded content: arquitectura · 2 puertas, Kafka, 4 servicios\]

El navegador usa dos puertas: REST para enviar comandos y WebSocket para recibir eventos. Detrás, los servicios solo se comunican por Kafka, y `board-service` es el único que escribe el estado del tablero en PostgreSQL.

## Servicios y responsabilidades

Son seis servicios Spring Boot que solo se hablan por Kafka, salvo las llamadas síncronas del navegador al API. Para el MVP pueden vivir en un monorepo y desplegarse como un modular monolith; la separación por eventos permite partirlos después.

| Servicio | Responsabilidad | Consume | Produce | Datos propios |
| --- | --- | --- | --- | --- |
| `api-gateway` | REST para el front, autenticación (JWT/OAuth2), validación de comandos | — | Comandos: `SearchRequested`, `CriteriaChanged`, `NodeDecisionMade` | — |
| `board-service` | Fuente de verdad del tablero: aplica comandos, valida reglas de estado, deduplica nodos | Comandos y resultados de agentes | `NodeCreated`, `NodeStateChanged`, `CriteriaVersionCreated` | PostgreSQL: tableros, nodos, criterios, decisiones |
| `source-connectors` | Llama a OpenAlex, Semantic Scholar, Crossref y arXiv; normaliza metadatos | `SearchPlanned` | `SourceFound` | Caché de respuestas (Redis) |
| `agent-orchestrator` | Agentes de planeación, evaluación y etiquetado (Spring AI) | `SearchRequested`, `NodeCreated`, `CriteriaVersionCreated` | `SearchPlanned`, `NodeScored`, `ClusterLabeled` | Prompts versionados, uso de tokens |
| `analytics-service` | Calcula aristas, posiciones iniciales del grafo, heatmap, nube de términos y línea de tiempo con código, no con el LLM | Eventos de nodos y evaluaciones | `ViewComputed` | Proyecciones de vistas |
| `realtime-gateway` | WebSocket hacia los navegadores, presencia y reenvío de eventos por tablero | Todos los eventos de tablero | — | Sesiones activas |

**Por qué un `board-service` único como autoridad.** Las reglas del ciclo de vida (quién puede aceptar, que descartar exija motivo) viven en un solo lugar. Los agentes y conectores solo proponen; el tablero decide qué se persiste.

## Modelo de eventos (Kafka)

Todos los tópicos usan `board_id` como clave de partición, así los eventos de un tablero llegan en orden a cada consumidor. Kafka 4.x funciona solo en modo KRaft; en local basta un nodo combinado (controlador y broker).

| Tópico | Eventos | Productor | Retención |
| --- | --- | --- | --- |
| `board.commands` | `SearchRequested`, `CriteriaChanged`, `NodeDecisionMade` | `api-gateway` | 7 días |
| `board.events` | `NodeCreated`, `NodeStateChanged`, `CriteriaVersionCreated`, `NodeScored` (persistido) | `board-service` | Indefinida o compactada por nodo: es el log de auditoría |
| `agent.tasks` | `SearchPlanned`, `ScoreRequested` | `agent-orchestrator`, `board-service` | 7 días |
| `agent.results` | `SourceFound`, `NodeScored`, `ClusterLabeled` | Conectores y agentes | 7 días |
| `views.computed` | `ViewComputed` (grafo, heatmap, nube, timeline) | `analytics-service` | Compactado por `board_id + vista` |
| `*.dlq` | Mensajes que fallaron tras los reintentos | Cada consumidor | 14 días |

**Flujo principal.**

1. El usuario lanza una búsqueda: `SearchRequested`.
2. El agente planificador divide la pregunta en subconsultas: `SearchPlanned` × N.
3. Los conectores consultan las APIs en paralelo: `SourceFound`.
4. El tablero deduplica y crea nodos: `NodeCreated`.
5. El agente evaluador puntúa cada nodo contra la versión vigente de criterios: `NodeScored`.
6. El usuario cambia criterios: `CriteriaVersionCreated`, y se re-evalúan los nodos activos.
7. El usuario acepta o descarta: `NodeStateChanged`.
8. Las vistas se recalculan: `ViewComputed`.

**Garantías.**

- Entrega at-least-once en todos los consumidores; cada evento lleva `event_id` (UUID) y los consumidores son idempotentes.
- El `board-service` escribe en PostgreSQL y publica con el patrón **outbox** para no perder eventos entre la base y Kafka.
- Reintentos con backoff exponencial y, al agotarse, envío a `*.dlq`.
- Esquema de eventos en JSON Schema o Avro con versión en el encabezado; los cambios son retrocompatibles.
- Cada evento lleva `board_id`, `actor` (persona o agente), `occurred_at` y `correlation_id` para trazar una búsqueda de punta a punta.

## Modelo de datos (PostgreSQL)

PostgreSQL guarda el estado actual del tablero como proyección de los eventos, más un historial inmutable de decisiones y evaluaciones. Si la proyección se corrompe, se reconstruye leyendo `board.events` desde el inicio.

| Tabla | Columnas principales | Notas |
| --- | --- | --- |
| `board` | `id`, `title`, `research_question`, `owner_id`, `created_at` |  |
| `board_member` | `board_id`, `user_id`, `role` | Rol: owner, editor, viewer |
| `node` | `id`, `board_id`, `kind` (paper, web), `doi`, `openalex_id`, `url`, `title`, `abstract`, `year`, `venue`, `state`, `x`, `y` | Índice único por `board_id` + identificador normalizado; `x`, `y` = posición en la pizarra |
| `node_provenance` | `node_id`, `query`, `source_api`, `retrieved_at`, `raw_ref` | Una fila por cada vez que una búsqueda lo encontró |
| `criteria_version` | `board_id`, `version`, `criteria` (JSONB), `created_by`, `created_at` | Inmutable; la versión vigente es la mayor |
| `node_evaluation` | `node_id`, `criteria_version`, `scores` (JSONB por criterio), `suggestion`, `reason`, `evidence`, `model`, `prompt_version`, `tokens` | Inmutable; una por nodo y versión |
| `node_decision` | `node_id`, `from_state`, `to_state`, `reason`, `actor_id`, `criteria_version`, `decided_at` | Inmutable; es la historia de cada nodo |
| `edge` | `board_id`, `source_node`, `target_node`, `kind`, `weight` | Cita, co-citación, similitud |
| `outbox` | `id`, `aggregate_id`, `type`, `payload`, `created_at`, `published_at` | Patrón outbox |
| `processed_event` | `consumer`, `event_id` | Idempotencia de consumidores |

Para similitud semántica entre nodos se puede añadir una columna `embedding` con pgvector. No es necesario en el MVP: OpenAlex ya trae conceptos y referencias para calcular aristas.

## Agentes de IA

Los agentes siguen el patrón orquestador-trabajadores que usan los sistemas de deep research: un planificador divide la pregunta y varios trabajadores la atienden en paralelo. Aquí cada paso es un evento visible en el tablero, no un proceso oculto.

| Agente | Entrada | Salida estructurada | Notas |
| --- | --- | --- | --- |
| Planificador | Pregunta de investigación + criterios vigentes | Lista de subconsultas, cada una con fuente objetivo y motivo | El usuario puede editar el plan antes de ejecutarlo |
| Evaluador | Metadatos y abstract del nodo + criterios | Score 0–1 por criterio, sugerencia, motivo de ≤ 2 frases, fragmento textual de evidencia | El fragmento debe existir literalmente en el abstract; si no, la evaluación se rechaza |
| Etiquetador | Grupos de nodos ya calculados por código | Nombre corto de cada cluster | No calcula clusters ni números |
| Sintetizador (después del MVP) | Nodos definitivos | Borrador de revisión de literatura con citas a nodos | Solo cita nodos aceptados |

**Reglas de implementación.**

- Spring AI 2.x con `ChatClient` y salida estructurada a records de Java; LangChain4j queda como alternativa si se necesita orquestación más compleja.
- La app no ofrece IA propia: cada usuario conecta su proveedor (solo por API key). Un ModelProvider por proveedor implementa la misma interfaz, y el tablero usa el proveedor de quien lanza la búsqueda.
- Prompts versionados en el repositorio; cada evaluación guarda `prompt_version` y `model`.
- Control de costo: límite de tokens por tablero y por búsqueda, conteo por evento y re-evaluación solo de nodos activos cuando cambian los criterios.
- Temperatura baja en el evaluador para que dos corridas den resultados comparables.

## Fuentes de datos externas

OpenAlex es la fuente principal del MVP; las demás se agregan como conectores intercambiables detrás de una misma interfaz `SourceConnector`.

| Fuente | Uso en la app | Acceso | Etapa |
| --- | --- | --- | --- |
| [OpenAlex](https://developers.openalex.org/api-reference/authentication.md) | Búsqueda, metadatos, referencias, conceptos | API key gratuita obligatoria desde febrero de 2026; 100,000 créditos al día; consultas por ID o DOI gratis | MVP |
| Semantic Scholar | TLDR, recomendaciones, intención de citas | API gratuita con key; confirmar límites en la documentación oficial | Etapa 3 |
| Crossref | Metadatos canónicos por DOI | API pública | Etapa 3 |
| arXiv | Preprints de CS, física y matemáticas | API pública | Etapa 3 |
| Búsqueda web | Páginas no académicas | Key del usuario: Exa, Brave Search o Tavily | Etapa 6 |

**Reglas.**

- Un rate limiter global por fuente (Resilience4j) y caché de respuestas en Redis.
- Solo se guarda texto completo de artículos open access y se respeta su licencia; del resto se guardan metadatos y abstract.
- Normalización de DOI y URL para deduplicar entre fuentes.

## Colaboración en tiempo real y vistas

En el MVP la colaboración es server-authoritative: el navegador envía comandos al API y recibe los eventos del tablero por WebSocket. No hace falta un CRDT mientras los conflictos sean raros.

**Tiempo real.**

- `realtime-gateway` con Spring WebSocket (STOMP), un canal por tablero: `/topic/boards/{boardId}`.
- Consume `board.events` y `views.computed`, y reenvía solo a los miembros del tablero.
- Presencia (quién está viendo) y cursores van por un canal efímero, sin pasar por Kafka.
- Con varias réplicas del gateway, cada una usa su propio consumer group; si el volumen crece, Redis Pub/Sub reparte entre réplicas.
- Mover un nodo en la pizarra es un comando `NodeMoved` con *debounce*; gana la última escritura.
- Si luego se necesita edición simultánea fina, se evalúa un CRDT (por ejemplo Yjs) dentro del mismo módulo del lienzo.

**Vistas.** Cada vista se calcula con código en `analytics-service`; el LLM solo pone nombres a los clusters.

| Vista | Cálculo | Para qué sirve | Etapa |
| --- | --- | --- | --- |
| Grafo de nodos | Referencias de OpenAlex (cita) y co-citación; posiciones iniciales calculadas en el backend y dibujadas en SVG propio | Ver cómo se relacionan los papers; es el lienzo principal | MVP |
| Heatmap nodos × criterios | Scores de `node_evaluation` para la versión vigente | Decidir qué aceptar o descartar | MVP |
| Nube de términos | TF-IDF sobre títulos y abstracts de nodos activos | Filtrar nodos por término | Etapa 5 |
| Línea de tiempo | Nodos por año de publicación | Ver la evolución del campo | Etapa 5 |
| Embudo | Conteo de nodos por estado | Ver cuánto se ha depurado | Etapa 5 |

**Front.** TypeScript con TanStack y sin librería de grafos ni de gráficas:

- **TanStack Router (o Start)** para las rutas, por ejemplo `/boards/$boardId`.
- **TanStack Query** guarda el estado del tablero; cada evento que llega por WebSocket actualiza la caché con `setQueryData`, sin volver a pedir todo.
- **TanStack Table** para el heatmap (una columna por criterio, color con CSS) y la lista de nodos.
- **TanStack Form** para el formulario de criterios duros.
- **Módulo `board-canvas` propio:** SVG con un `<g>` por nodo, pan y zoom con un `transform`, arrastre con eventos de puntero que envían `NodeMoved`. El resto de la app solo usa su interfaz (`addNode`, `setState`, `highlight`, `fitView`), así que el render puede cambiar sin tocar nada más.
- La línea de tiempo y el embudo son SVG simple; la nube de términos son chips con tamaño según frecuencia que filtran nodos.

## Requisitos no funcionales

| Área | Decisión |
| --- | --- |
| Seguridad | Spring Security con OAuth2/JWT; autorización por rol en cada tablero; la API key de OpenAlex vive en un gestor de secretos; las keys de LLM y de búsqueda web de cada usuario se cifran en reposo (envelope encryption con una llave maestra en KMS o Vault), nunca se registran en logs ni viajan en eventos de Kafka, y el usuario puede revocarlas |
| Observabilidad | Micrometer + Prometheus + Grafana para métricas; OpenTelemetry para trazas que cruzan Kafka con `correlation_id`; logs estructurados en JSON. Métricas clave: lag de consumidores, latencia de búsqueda, tokens por tablero |
| Escalabilidad | Escalar consumidores por partición; 6 particiones por tópico al inicio; virtual threads de Java 21 para llamadas a APIs externas |
| Resiliencia | Resilience4j (circuit breaker, retry, rate limiter) en conectores y LLM; DLQ por consumidor; outbox en el tablero |
| Pruebas | JUnit 5 + Mockito; Testcontainers con Kafka y PostgreSQL para pruebas de integración; contratos de eventos verificados en CI |
| Despliegue | Docker Compose en local (Kafka KRaft, PostgreSQL, Redis, servicios); después Kubernetes o ECS; CI/CD con GitHub Actions |
| Privacidad | Los tableros son privados por defecto; los datos del usuario no se usan para entrenar modelos (verificar la política del proveedor elegido) |

## Decisiones, riesgos y preguntas abiertas

**Decisiones de arquitectura (ADRs).**

| # | Decisión | Alternativa descartada | Motivo |
| --- | --- | --- | --- |
| 1 | Kafka como bus y log de auditoría | RabbitMQ | El log retenido permite replay y reconstruir el historial; RabbitMQ borra el mensaje al consumirlo |
| 2 | `board-service` único como autoridad del estado | Que cada agente escriba en la base | Reglas del ciclo de vida en un solo lugar |
| 3 | La IA sugiere, la persona decide | Descarte automático por score | Responsabilidad y transparencia para el investigador |
| 4 | Vistas calculadas con código | Que el LLM genere los diagramas | Reproducibles y sin números inventados |
| 5 | WebSocket server-authoritative en el MVP | CRDT (Yjs) desde el inicio | Menos complejidad; los conflictos de edición serán raros al inicio |
| 6 | Spring AI 2.x | LangChain4j | Integración nativa con Spring Boot 4; LangChain4j queda como plan B |
| 7 | Modular monolith en el MVP | Microservicios desplegados por separado | Menos infraestructura para una persona; los límites por eventos ya permiten separarlos |

**ADR 8 — El usuario trae su propio proveedor de LLM.** La app no paga ni revende IA: cada usuario conecta su API key. Consecuencias: el costo de tokens lo asume el usuario, así que la app debe mostrar una estimación antes de cada búsqueda y el consumo real después; los agentes se prueban contra varios proveedores; y en un tablero compartido se usa el proveedor de quien lanza cada acción.

**ADR 9 — El usuario trae también su key de búsqueda web.** Igual que con el LLM, la app no paga búsquedas: el usuario conecta su key de Exa, Brave Search o Tavily. Cada una es un `SourceConnector` más, y la key se guarda cifrada como las del LLM. Si el usuario no conecta ninguna, la búsqueda se limita a fuentes académicas.

**ADR 10 — Criterios en dos capas: formulario y lenguaje natural.** Los criterios duros (años, idioma, tipo de documento, open access) van en un formulario, se evalúan con código y, cuando OpenAlex lo permite, se aplican como filtros de la consulta. Los criterios de juicio ("usa un dataset público") se escriben en texto, cada uno con nombre propio, y solo esos los evalúa el LLM. Cada criterio es una columna del heatmap.

**ADR 11 — Exportación a BibTeX y RIS.** Los nodos definitivos se exportan a `.bib` (LaTeX, Zotero, JabRef) y `.ris` (EndNote, Mendeley, Zotero). Los metadatos salen de OpenAlex y, con DOI, se completan con Crossref. También se exporta el historial de decisiones en CSV para documentar la metodología.

**ADR 12 — Sin librería de grafos en el front.** El lienzo es SVG propio y el layout inicial se calcula en `analytics-service` (Java, por ejemplo con JGraphT); las posiciones se guardan en `node.x` y `node.y`. Motivo: el tablero va reduciendo nodos (de cientos a los definitivos), una cantidad que SVG maneja bien, y así el front no depende de React Flow ni de Cytoscape.js. Se paga con trabajo propio en accesibilidad, gestos táctiles y extras como el minimapa. Criterio para revisar: si un tablero pasa de unos 1,000 nodos visibles o el arrastre deja de ser fluido, el render del módulo `board-canvas` cambia de SVG a Canvas.

**Riesgos.**

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| El LLM inventa motivos o evidencia | Se pierde la confianza en la app | Validar que el fragmento de evidencia exista literalmente en el abstract |
| Costo de tokens al re-evaluar | Gasto alto con tableros grandes | Re-evaluar solo nodos activos; límite por tablero; modelo pequeño para evaluar |
| Límites de las APIs académicas | Búsquedas lentas o fallidas | Rate limiter, caché y búsquedas por ID o DOI cuando sea posible |
| Complejidad de Kafka para un proyecto personal | Avance lento | Empezar con un solo broker KRaft y pocos tópicos |
| Licencias del texto completo | Riesgo legal | Guardar solo open access y metadatos |

**Preguntas abiertas.**

- ¿Qué proveedores de LLM (por API key) se soportan en el MVP?
- ¿Qué API de búsqueda web se soporta primero: Exa, Brave Search o Tavily?
- ¿Qué campos tendrá el formulario de criterios en el MVP?
- ¿Vale la pena agregar después la sincronización directa con Zotero (con la key de Zotero del usuario)?

## Plan por etapas

Cada etapa termina en algo que se puede demostrar; las tres primeras forman el MVP.

1. **Esqueleto.** Docker Compose con Kafka KRaft, PostgreSQL y Redis; `board-service` con outbox; lienzo SVG propio (board-canvas) con nodos creados a mano; eventos visibles en vivo por WebSocket.
2. **Agente buscador.** Planificador + conector de OpenAlex; los nodos aparecen en el tablero conforme llegan; deduplicación por DOI.
3. **Criterios y evaluación.** Criterios versionados, agente evaluador con evidencia validada, heatmap y acciones de aceptar o descartar con motivo; exportación a BibTeX y RIS.
4. **Equipo.** Miembros y roles, presencia, comentarios en nodos.
5. **Más vistas.** Aristas de cita y co-citación, nube de términos como filtro, línea de tiempo, embudo por estado.
6. **Web general y síntesis.** Conector de búsqueda web con la key del usuario, verificación de citas y borrador de revisión de literatura.

## Fuentes

- [OpenAlex: autenticación y créditos](https://developers.openalex.org/api-reference/authentication.md)
- [PyAlex: API key obligatoria desde febrero de 2026](https://github.com/J535D165/pyalex)
- [Versiones de Apache Kafka](https://mungomash.com/software/kafka/versions/)
- [Spring AI 2.0 vs LangChain4j](https://besthub.dev/articles/spring-ai-2-0-vs-langchain4j-which-should-you-choose-f140d2869e7e)
- [React Flow: colaboración multijugador](https://reactflow.nodejs.cn/learn/advanced-use/multiplayer)
- [Open Deep Research (LangChain)](https://www.langchain.com/blog/open-deep-research)
- [Sistema multiagente de research de Anthropic (resumen)](https://blog.bytebytego.com/p/how-anthropic-built-a-multi-agent)
- [Herramientas de mapeo de literatura](https://intuitionlabs.ai/articles/ai-literature-mapping-tools-guide)
