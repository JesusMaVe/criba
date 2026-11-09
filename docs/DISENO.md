# Diseño — referencia de implementación

Export del diseño de producto: dos pantallas a 1440 px. El archivo `.html` es el export completo
(autocontenido, con las pantallas empaquetadas); los PNG son lo que se ve.

```
docs/diseno/
  tablero-de-nodos.png   lienzo + panel de detalle del nodo
  criterios.png          reglas automáticas + tabla de preguntas para la IA
  diseno.html            export original, por si hay que reabrir la fuente
```

## Tokens

| Token | Valor | Uso |
| --- | --- | --- |
| `--bg` | `#FAF9F5` | fondo del lienzo y de la app |
| `--surface` | `#FFFFFF` | paneles, tarjetas de nodo |
| `--surface-2` | `#F0F0ED` | bloques de cita y superficies hundidas |
| `--ink` | `#1C1C1A` | texto principal |
| `--ink-soft` | `#3A3A36` | texto secundario |
| `--muted` | `#6B6B66` | etiquetas, metadatos |
| `--line` | `#E7E7E4` | bordes y separadores |
| `--line-strong` | `#CFCFCA` | borde discontinuo del candidato |
| `--accent` | `#2F4A6D` | nodo definitivo, botón primario |
| `--accent-deep` | `#1F3350` | estados activos / hover |
| `--accent-soft` | `#D5DCE6` | pastilla «Parcial», borde de preseleccionado |

Tipografía: **Inter, system-ui**. Cuerpo a 12–13 px; títulos de pantalla 19 px; título del nodo en el
panel 16 px. Tracking ligeramente negativo en los títulos grandes. Radios de 8–10 px en tarjetas y
999 px en pastillas.

## Estructura

**Tablero de nodos** — barra superior (migas + `Compartir` + menú), tres zonas: barra lateral de
248 px con la pregunta, el conteo por estado y dos accesos (`Criterios versión 3`, `Nueva búsqueda`);
lienzo flexible con conmutador `Grafo | Tabla`, nota al pie «Las líneas son citas entre fuentes» y
zoom `− +`; panel derecho de 340 px con el nodo seleccionado y las acciones `Aceptar` / `Descartar…`.

**Criterios** — sin panel derecho. Cabecera con versión y enlace `Ver cambios`, y el aviso
«1 cambio sin guardar · re-evalúa 26 fuentes» junto a `Descartar` / `Guardar versión 4`. Dos
secciones: **1. Reglas** (automáticas, sin IA) con años, tipo y acceso; **2. Preguntas para la IA**
en tabla de fuentes × preguntas, con la importancia de cada pregunta (`Obligatoria` / `Deseable`) y
respuestas `Sí` / `Parcial` / `No`.

## El estado se ve antes de leerse

| Estado | Borde | Fondo | Texto |
| --- | --- | --- | --- |
| CANDIDATO | 1 px discontinuo `--line-strong` | `--surface` | `--muted` |
| PRESELECCIONADO | 2 px `--accent` | `--surface` | `--ink` |
| ACEPTADO | 1 px `--ink` | `--surface` | `--ink` |
| DEFINITIVO | ninguno | `--accent` | blanco |
| DESCARTADO | — | oculto por defecto, con contador «17 · ocultos» | — |

Cada tarjeta lleva su icono de estado junto a la etiqueta, el título en negrita y el año abajo.

## De la pantalla al modelo

| Diseño | Dónde vive | Estado |
| --- | --- | --- |
| Conteo por estado | `node.state` agrupado | existe |
| Título, año, venue | `node` | existe |
| `Ver fuente` | `node.doi` / `node.url` | existe, falta exponer `url` en la vista |
| `La IA sugiere` + titular | `node_evaluation.suggestion` | existe, faltan las etiquetas |
| Motivo de ≤ 2 frases | `node_evaluation.reason` | existe |
| Cita del abstract + `Ver en el abstract` | `node_evaluation.evidence` | existe; requiere resaltar el fragmento literal en `node.abstract` |
| `Respuestas a tus criterios` | `node_evaluation.scores` | **cambia**: la escala es tri-estado, no 0–1 |
| `Obligatoria` / `Deseable` | `criteria_version.criteria[].importance` | **falta** |
| `Descartados 17 · ocultos` | filtro por estado en la vista | **falta la regla explícita** |
| `Guardar versión 4` | `criteria_version` v+1 | existe |
| `Ver cambios` | comparación entre versiones | **falta endpoint** |
| `Historial (3)` | `node_decision` | existe, falta endpoint |
| `Compartir` | miembros y roles (Etapa 4) | fuera del MVP |

## Decisiones que el diseño obliga a cerrar

1. **La escala es `Sí` / `Parcial` / `No`, no un 0–1.** Propuesta: guardar el enum en `scores` y
   derivar un valor numérico solo para ordenar. Un enum se valida, se explica y se pinta igual en el
   heatmap; un flotante del LLM no.
2. **La sugerencia debería calcularla el código, no el modelo.** Con `Obligatoria` / `Deseable` la
   regla es explícita: si falla una obligatoria, `DESCARTAR`; si solo fallan deseables, `DUDOSO`; si
   se cumplen todas, `MANTENER`. El LLM responde las preguntas y la app decide qué recomendar, igual
   que las vistas se calculan con código (ADR 4). Así dos corridas dan la misma recomendación.
3. **Falta el costo en el aviso de re-evaluación.** «re-evalúa 26 fuentes» dice el volumen pero no
   el precio, y el §4 exige mostrar el estimado antes de gastar. Cabría «re-evalúa 26 fuentes ·
   ≈ $0.04 de $2.00 del día».
4. **Qué cuenta como «activa».** El aviso de re-evaluación, la tabla y el heatmap deben usar el
   mismo criterio: nodos no descartados, o no descartados y no definitivos. Hoy el diseño usa 26 en
   dos sitios y la barra lateral suma 29.

## Implementado en la Etapa 1

| Pieza | Estado |
| --- | --- |
| Barra superior con migas, `Compartir` (copia el enlace) y menú | hecho |
| Barra lateral con pregunta, conteo por estado y descartados ocultos | hecho |
| Lienzo SVG con pan, zoom, selección y estilo por estado | hecho, sin aristas todavía |
| Vista de tabla | hecho |
| Panel de detalle con estado, metadatos, fuente, abstract e historial | hecho |
| `Aceptar` / `Descartar…` con motivo obligatorio en un diálogo | hecho |
| `Nueva búsqueda` con su diálogo y aviso de búsqueda en curso | hecho (Etapa 2) |
| Pantalla de criterios, aviso de re-evaluación y bloque de evaluación | pendiente (Etapas 2 y 3) |

Capturas del resultado en `docs/capturas/`.
