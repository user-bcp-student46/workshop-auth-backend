# Workshop Auth Backend — Java Quarkus

> **Laboratorio intencionalmente vulnerable.** Ejecutar únicamente en local, repositorios privados y ambientes aislados. No desplegar en Internet, CERT o PROD.

Backend Quarkus que autentica usuarios de laboratorio almacenados en SQLite y entrega un token consumido por el frontend Angular y la API Python.

## Ejecución

Requisitos: JDK 21 y Maven 3.9+.

```bash
mvn quarkus:dev
```

Servicio: `http://localhost:8080`

Credenciales locales:

- `student` / `Workshop123!`
- `trainer` / `Trainer123!`

Las contraseñas se guardan con PBKDF2-HMAC-SHA256 (600 000 iteraciones, sal aleatoria por usuario) en formato `pbkdf2_sha256$<iteraciones>$<sal>$<clave>`.

## Migración de contraseñas

Al inicializar la base (`LegacyDatabase.initialize`), el servicio:

1. crea la tabla `legacy_users` si no existe y siembra `student` y `trainer` con contraseñas ya hasheadas, solo cuando no existen;
2. convierte en sitio cualquier contraseña que no esté en formato `pbkdf2_sha256$...` (por ejemplo, filas de una base anterior en texto plano), en una sola transacción;
3. si la migración falla, registra un error `SEVERE` y los usuarios con contraseña sin hashear no podrán iniciar sesión hasta que se corrija.

La migración es idempotente. Antes de ejecutar la versión con hash sobre una base existente (`./data/workshop.db` por defecto), haga una copia de seguridad del archivo.

Las pruebas usan la variable `AUTH_DB_URL` configurada en Surefire (`target/test-workshop.db`) y no tocan `./data`.

## Endpoints

- `POST /auth/login`
- `GET /q/health`

## Propósito de la rama `main`

La rama contiene deuda técnica y de seguridad deliberada: token reversible, secreto hardcodeado, CORS permisivo, método largo, manejo genérico de excepciones y dependencias con CVEs. El login ya usa consulta parametrizada y contraseñas hasheadas.
