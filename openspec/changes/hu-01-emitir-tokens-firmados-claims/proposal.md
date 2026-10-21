# Proposal

**Issue de referencia:** #12 — "HU-01: Emitir tokens firmados con expiración y claims mínimos" (fuente de alcance y criterios de aceptación).
**Historia de origen:** [HU-01.md](../../../HU-01.md).
**Baseline de comportamiento:** [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md).

## Why

El login de `POST /auth/login` emite hoy un token Base64 de `username|role|exp|secreto`, con el secreto embebido en el código (`WORKSHOP_SECRET_2026` en `LegacyTokenUtil.java`). Quien conozca el formato y el secreto puede fabricar tokens válidos; el token no identifica el cliente ni el instante de emisión, y no existe una verificación de firma ni de expiración que un consumidor pueda aplicar. Issue #12 pide cerrar esa brecha antes de que la API consumidora dependa del formato.

**Discrepancia detectada:** el Issue y `HU-01.md` describen una implementación que "firma con HMAC y lee `AUTH_TOKEN_SECRET`". En la rama actual (`feature/HU-1`) esa implementación no existe: no hay HMAC ni `AUTH_TOKEN_SECRET` en `src/`, `docs/` ni `README.md`. El contrato real es el de `docs/ENDPOINTS.md`, por lo que esta propuesta parte de ese baseline y trata la firma como un cambio a introducir.

## Cambios frente al baseline (`docs/ENDPOINTS.md`)

| Aspecto | Baseline (ENDPOINTS.md / código actual) | Issue #12 | Tratamiento en esta propuesta |
|---|---|---|---|
| Formato del token | Base64 de `username`, `role`, `exp` y secreto; no es firmado | Firmado, con claims `sub`, `client`, `iat`, `exp` | **BREAKING** |
| `client` | Opcional; no participa en la lógica | Se obtiene, valida e incluye en el token | Cambio de comportamiento (ver D4) |
| Secreto | Embebido en el código | Lectura desde `AUTH_TOKEN_SECRET`; sin default fuera de laboratorio | **BREAKING** en producción sin variable |
| `expiresIn` | Fijo en 3600 | Coherente con `exp - now` dentro de una tolerancia documentada | Se mantiene 3600; se añade verificación de coherencia |
| Logs | No especificado | Sin tokens, secretos ni valores sensibles | Nuevo requisito |

**Conflicto con Issue #9 (H1):** #9 exige que el formato `base64(usuario|rol|expiración|secreto)` y su consumo por la API Python no cambien. #12 cambia ese formato. Ambos requisitos no pueden cumplirse a la vez; ver D1.

## What Changes

- El login emite un token firmado que contiene `sub`, `client`, `iat` y `exp` (más `role`, ver D2). **BREAKING**: cambia el formato del token.
- El valor `client` de la solicitud se valida y se refleja en el token.
- `expiresIn` sigue siendo 3600 y debe coincidir con `exp - now` dentro de una tolerancia documentada (D3).
- El secreto de firma se configura con `AUTH_TOKEN_SECRET`. En producción, la ausencia de la variable impide el arranque. El valor de laboratorio solo existe en perfiles no productivos y queda documentado como exclusivo de laboratorio (D5). **BREAKING** para despliegues productivos sin variable.
- Los logs del login no contienen tokens, secretos ni contraseñas.
- Pruebas automatizadas para login válido, credenciales inválidas, token expirado, claims y ausencia de datos sensibles en logs.
- La decisión entre HMAC y una librería JWT se documenta en `design.md`; esta propuesta no la toma (D6).

## Alcance

- `POST /auth/login`: emisión del token, validación de `client`, coherencia de `expiresIn`.
- Configuración de `AUTH_TOKEN_SECRET` por perfil.
- Redacción de logs relacionados con el token y el secreto.
- Pruebas que cubran los criterios de aceptación del Issue #12.
- Actualización de [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md) y de la documentación de configuración.

## Fuera de alcance

- H1 (#9): consulta de login parametrizada y hash de contraseñas.
- H2 (autorización y SQL en la API Python), H3 (token reversible, salvo lo que #12 exige sobre el formato), H4 (subida de archivos), H5 (dependencias y CI).
- Errores 500: su cuerpo sigue exponiendo mensaje y clase de excepción, como en el baseline.
- Reflejo del `username` en el 401.
- Cambios en el frontend y en la API Python, incluida la validación del token en esa API.
- Nuevo endpoint de validación, revocación, refresh tokens y rotación de secretos.
- `GET /q/health`.
- Migración de datos de `legacy_users`.

## Comportamiento que debe preservarse

Tomado de [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md), salvo lo que el Issue #12 cambia explícitamente:

- `POST /auth/login` responde 200, 400 y 401 con los mismos códigos y el mismo formato de cuerpo.
- Cuerpo de éxito con las claves `token`, `expiresIn`, `user` (`username`, `displayName`, `role`) y `debug` (`authenticated-with-legacy-flow`).
- `displayName` se devuelve escapado en HTML.
- Mensajes 400: `Request is null`, `Username is required`, `Password is required`.
- Mensaje 401: `Invalid user <username> or password`, idéntico para usuario inexistente y contraseña incorrecta.
- Las credenciales de laboratorio existentes (`student` / `Workshop123!`) siguen funcionando.
- `client` sigue siendo opcional: su ausencia no produce 400.
- `GET /q/health` no cambia.

No se especifica en el baseline el comportamiento ante JSON malformado o `Content-Type` distinto de `application/json` (pregunta abierta en ENDPOINTS.md); esta propuesta no lo fija.

## Decisiones pendientes

Estas decisiones se proponen aquí para revisión. Las specs las reflejan como supuestos.

- **D1 — Conflicto con #9 sobre el formato del token.** Propuesta: #12 prevalece para el formato. La API Python que consume el token debe actualizarse en coordinación o en una HU aparte. Confirmar antes de `design`.
- **D2 — Claim `role`.** Propuesta: incluirlo además de los claims mínimos, para no perder el dato que hoy transporta el token.
- **D3 — Tolerancia de `expiresIn`.** Propuesta: 5 segundos, documentada en ENDPOINTS.md.
- **D4 — `client` ausente o inválido.** Propuesta: ausente o `null` → 200 con `client` = `default`. Presente y fuera de `^[A-Za-z0-9._-]{1,64}$` (incluye cadena en blanco) → 400 `Client is invalid`. Es un 400 nuevo, que se documenta como extensión del contrato.
- **D5 — Secreto por perfil.** Propuesta: en producción, sin `AUTH_TOKEN_SECRET` el servicio no arranca. En perfiles dev/test, un valor de laboratorio documentado como exclusivo de laboratorio.
- **D6 — HMAC frente a librería JWT.** Se decide y documenta en `design.md`. Las specs son neutrales a esa elección.

## Capabilities

### New Capabilities
- `auth-login`: contrato observable de `POST /auth/login` (entrada, respuestas de éxito y error, comportamiento preservado).
- `auth-token`: formato, claims, firma, expiración y secreto del token emitido, y redacción de datos sensibles en logs.

### Modified Capabilities
- Ninguna. `openspec/specs/` no contiene specs previas; el contrato actual vive en `docs/ENDPOINTS.md` y se incorpora como requisitos `ADDED` en `auth-login`.

## Impact

- **Código:** `LegacyTokenUtil.java` (emisión del token), el flujo de login en `AuthResource.java`, `application.properties` (perfiles y secreto) y las pruebas de `src/test/` (`AuthResourceTest` y nuevas pruebas de claims, expiración y logs).
- **Configuración:** nueva variable obligatoria `AUTH_TOKEN_SECRET` en producción.
- **Documentación:** `docs/ENDPOINTS.md` (formato del token, `client`, tolerancia de `expiresIn`, nuevo 400) y la documentación de configuración.
- **Consumidores:** la API Python y el frontend que interpreten el token deben adaptarse al nuevo formato (BREAKING; ver D1).
- **Dependencias:** ninguna decidida aquí; cualquier librería se evalúa en `design.md`.
