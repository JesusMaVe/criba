# Criba

Tablero colaborativo de investigación donde cada nodo —paper, artículo o página— conserva su
procedencia, y cada decisión de aceptarlo o descartarlo queda registrada con su motivo.

**Principio rector: la IA sugiere, la persona decide.** Ningún agente cambia el estado de un nodo.

## Estado

Etapa 0 en construcción: esqueleto caminante del API con eventos en Kafka y tablero
sincronizado por WebSocket. Ver `docs/PROPUESTA-ARQUITECTURA.md` §9.

## Estructura

```
apps/
  api/          Spring Boot 4.1 — monolito modular (Modulith 2.1)
  web/          SPA TanStack Router + Query
compose.yaml    PostgreSQL 18 + Kafka 4.3 (KRaft)
docs/
  Arquitectura — App de research transparente con IA (Java + Kafka).md   el qué y el por qué
  PROPUESTA-ARQUITECTURA.md                                              el cómo y el estado
```

## Requisitos

- Docker con Compose
- JDK 25 (`/usr/libexec/java_home -v 25`); el wrapper se encarga de Maven
- Node 24+ y pnpm (`npm i -g pnpm`)

## Arranque

```bash
docker compose up -d          # PostgreSQL 18 y Kafka 4.3
cd apps/api && ./mvnw spring-boot:run
cd apps/web && pnpm install && pnpm dev
```

## Reglas del repositorio

1. **La API es la única escritora de las tablas de auditoría.** Los agentes proponen con eventos;
   la API valida y persiste.
2. **Ningún módulo consume un tópico que su propio deployable produce.** Kafka es integración, no
   un bus interno: los eventos de dominio viajan en proceso.
3. **Toda llamada que gasta la key del usuario reserva su peor caso antes de hacerse**, contra el
   presupuesto de la operación y el diario de la key (ver §4 de la propuesta).
4. Commits en imperativo y con alcance: `feat(board): registra decisiones con motivo obligatorio`.

## Documentación

- `docs/Arquitectura …md` — propuesta de valor, conceptos de dominio, ADR 1–12.
- `docs/PROPUESTA-ARQUITECTURA.md` — arquitectura de arranque: tres planos, entrega at-least-once,
  control del gasto, etapas y ADR 13–23.
