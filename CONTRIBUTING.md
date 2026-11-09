# Cómo entra un cambio a `main`

Nada entra directo: cada cambio va en una rama y se integra con un pull request cuando la
comprobación **`puerta`** está en verde. No hay excepciones, tampoco para quien administra el
repositorio.

## TDD

1. **Rojo.** Escribe la prueba del comportamiento y mírala fallar por la razón esperada.
2. **Verde.** Escribe lo mínimo para que pase.
3. **Refactoriza** con las pruebas en verde.

Un bug se arregla igual: primero la prueba que lo reproduce.

## Qué comprueba `puerta`

| Comprobación | Qué exige |
| --- | --- |
| `api`, `agents` | `./mvnw verify`: pruebas con PostgreSQL y Kafka reales, modularidad y cobertura mínima |
| `web` | `pnpm typecheck`, `pnpm test` y `pnpm build` |
| `pruebas incluidas` | si una aplicación cambia código, la rama también toca sus pruebas |

CI no puede ver en qué orden escribiste prueba y código: el orden de TDD es un compromiso del
equipo, y `pruebas incluidas` solo detecta el caso de no haber escrito ninguna.

Cobertura mínima, en la propiedad `cobertura.*` de cada `pom.xml`:

| Aplicación | Líneas | Ramas |
| --- | --- | --- |
| `api` | 75 % | 75 % |
| `agents` | 90 % | 85 % |

**Estos números solo suben.** Si una rama necesita bajarlos, lo que falta son pruebas.

## Reglas de las pruebas

- **Infraestructura real.** PostgreSQL y Kafka van con Testcontainers; no se simulan.
- **Sin servicios externos de pago.** CI no tiene ninguna key: el LLM y OpenAlex se prueban con un
  doble detrás de su puerto, y ninguna prueba gasta dinero.
- **Sin pruebas ignoradas** para conseguir el verde (`@Disabled`, `.skip`).

## Antes de abrir el pull request

```bash
cd apps/api && ./mvnw verify
cd apps/agents && ./mvnw verify
cd apps/web && pnpm typecheck && pnpm test && pnpm build
```
