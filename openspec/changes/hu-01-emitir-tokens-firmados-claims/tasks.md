# Tasks

Checklist de implementacion del [Issue #12](https://github.com/user-bcp-student46/workshop-auth-backend/issues/12), que sigue siendo la unidad de trabajo del producto. Fuentes aprobadas: [proposal.md](proposal.md), [auth-login](specs/auth-login/spec.md), [auth-token](specs/auth-token/spec.md) y [design.md](design.md). Baseline: [ENDPOINTS.md](../../../docs/ENDPOINTS.md).

Todas las tareas se limitan a `workshop-auth-backend`. Trabajar en la rama actual confirmada `feature/HU-1`, sin crear ni cambiar de rama. No consultar ni modificar consumidores externos. Este archivo no implica que la implementacion haya comenzado: todas las casillas quedan pendientes.

## 1. Caracterizacion previa sin cambios funcionales

Completar y ejecutar este grupo antes de modificar el flujo, el token o la configuracion de produccion. Usar JUnit/REST Assured existentes y dobles Java simples, sin agregar Mockito. Detenerse ante divergencias no previstas, en lugar de cambiar expectativas para ocultarlas.

- [x] 1.1 Preparar fixtures SQLite exclusivas bajo `target`, configurar `AUTH_DB_URL` antes de iniciar la JVM y limpiar solo las filas/archivos propios de tests; verificar que las pruebas usan esa URL y no escriben en la base de laboratorio. La prueba comprueba la URL y la fixture se elimina al finalizar.
- [x] 1.2 Caracterizar login de student y trainer: estado 200, claves y tipos completos, datos persistidos de `user`, `debug` y `expiresIn=3600`; verificar las aserciones ejecutando las pruebas de login.
- [x] 1.3 Caracterizar body nulo y username/password ausentes, nulos, vacios y blancos, incluida la prioridad cuando fallan varios campos; verificar 400 y mensajes exactos con pruebas parametrizadas.
- [x] 1.4 Caracterizar password incorrecta y usuario inexistente; verificar 401, mensaje actual y ausencia de token en ambos casos mediante pruebas automatizadas.
- [x] 1.5 Caracterizar el escape HTML de displayName con una fixture aislada, `client` ausente/nulo/valido y propiedades JSON desconocidas; verificar respuesta y conservacion de los datos persistidos con las pruebas correspondientes.
- [x] 1.6 Caracterizar la estructura Base64 legacy y su expiracion aproximada a 3600 segundos, sin imprimir tokens ni secretos; ejecutar la prueba y dejar identificada esta expectativa como reemplazable al introducir JWT.
- [x] 1.7 Caracterizar el 500 usando dobles que lancen excepciones con mensaje nulo y no nulo; verificar cuerpo y clase actual en ambos casos, dejando explicita la expectativa no nula que cambiara a `Unknown error` segun la spec.
- [x] 1.8 Caracterizar el acceso publico a health y sus campos `status`/`checks`, sin congelar el contenido variable de checks; verificar 200 saludable sin autenticacion.
- [x] 1.9 Ejecutar juntas las clases `AuthResourceTest`, `AuthResourceCharacterizationTest` y `LegacyTokenUtilCharacterizationTest`. Comandos ejecutados en PowerShell dentro de `workshop-auth-backend`: `$env:AUTH_DB_URL = 'jdbc:sqlite:./target/workshop-auth-characterization.db'`; `mvn "-Dtest=AuthResourceTest" test` (baseline: PASS, 1 test); `mvn "-Dtest=AuthResourceTest,AuthResourceCharacterizationTest,LegacyTokenUtilCharacterizationTest" test` (PASS, 18 tests, 0 failures, 0 errors, 0 skipped). Sin valores sensibles en la salida registrada.

## 2. Frontera JWT y tiempo determinista

Depende de 1.9. Este grupo construye y prueba la frontera interna antes de sustituir el emisor del login; no agrega endpoints ni filtros HTTP.

- [x] 2.1 Declarar directamente `org.bitbucket.b_c:jose4j` en el POM, usando version gestionada por el BOM o una version fija compatible revisada; verificar version efectiva, resolucion y compilacion con Maven, sin actualizar dependencias ajenas. Version efectiva del BOM Quarkus 3.27.4: 0.9.6. Validacion: `mvn "-DskipTests" test dependency:tree "-Dincludes=org.bitbucket.b_c:jose4j"` (PASS, exit 0).
- [x] 2.2 Implementar la emision en un servicio de tokens con `Clock` controlable y una constante TTL de 3600; verificar con reloj fijo JWT compacto HS256, `typ=JWT`, claims `sub`, `client`, `iat`, `exp`, `role`, tipos correctos y `exp - iat = 3600`, sin password ni secreto en el payload.
- [x] 2.3 Implementar verificacion interna con HS256 como unico algoritmo permitido y resultados tipados para formato/firma/claims; verificar aceptacion valida y rechazo de secreto distinto, payload/firma alterados, algoritmos no permitidos, `alg=none`, formato invalido y Base64 legacy. Cubrir codificacion no canonica para impedir alteraciones textuales aceptadas.
- [x] 2.4 Exigir individualmente `sub`, `client`, `iat` y `exp` con sus tipos correctos; verificar con tokens firmados pero sin cada claim requerido y con tipos invalidos, para demostrar rechazo por claims y no solo por firma.
- [x] 2.5 Aplicar expiracion con el mismo reloj y sin skew adicional; verificar de forma determinista aceptacion en `exp - 1` y rechazo en `exp` y `exp + 1`, sin sleeps ni excepciones/resultados que incluyan el token. Validacion: `mvn "-Dtest=AuthTokenServiceTest" test` (PASS, 15 tests, 0 failures, 0 errors, 0 skipped).
- [x] 2.6 Documentar en el contrato de endpoints el formato JWT, los claims, HS256, TTL y la diferencia entre la tolerancia de observacion de cinco segundos y la expiracion estricta; verificar coherencia con las pruebas de 2.2-2.5 y la decision tecnica ya registrada en el diseno. Validacion: `git diff --check -- docs/ENDPOINTS.md` (PASS).

## 3. Configuracion del secreto y fallo temprano

Depende de 2.2-2.5. Utilizar solo secretos sinteticos de prueba, nunca valores reales versionados.

- [x] 3.1 Resolver `AUTH_TOKEN_SECRET` mediante configuracion Quarkus y validar eager al arranque: UTF-8 sin trim/decodificacion implicitos, al menos 32 bytes, no vacio/blanco y sin valor publico de laboratorio en produccion; verificar cada regla con pruebas y diagnosticos que no incluyan el valor. Evidencia: `AuthTokenConfiguration` usa `@Startup`; 7 tests unitarios del validador pasan. Validacion: `mvn "-Dtest=AuthTokenSecretValidatorTest,AuthResourceTest,AuthResourceCharacterizationTest" test` (PASS, 24 tests, 0 failures, 0 errors, 0 skipped).
- [x] 3.2 Configurar fallback exclusivamente en perfiles no productivos declarados y permitir override por entorno; verificar login/emision con fallback de laboratorio y con override, ausencia de default global y fallo cerrado de un perfil nuevo sin configuracion. `AuthTokenConfigurationTest` pasó sin variable (fallback `%test`) y con override sintético; el probe de proceso validó el cierre de un perfil `custom` antes de detenerse en conectividad HTTP del escenario válido. El probe completo 3.3 permanece pendiente.
- [ ] 3.3 Agregar pruebas de proceso del artefacto empaquetado mediante `mvn package quarkus:build`: perfil productivo con secreto ausente, vacio, blanco, corto o de laboratorio termina con error antes de atender HTTP; con secreto sintetico valido responde a health y login. Verificar salida/disponibilidad y cerrar cada proceso por su identificador.
- [ ] 3.4 Documentar configuracion de entorno, minimo de bytes, perfiles, fallback exclusivo de laboratorio y errores de arranque en README/contrato de endpoints; verificar que los ejemplos coinciden con las pruebas de proceso y conservan la advertencia de laboratorio no apto para produccion.

## 4. Integracion incremental de login

Depende de los grupos 2 y 3. Conservar las pruebas protegidas del grupo 1; solo cambiar las expectativas deliberadas identificadas por propuesta, specs y diseno.

- [x] 4.1 Introducir el servicio minimo de login que resuelve `client` antes de consultar credenciales, sin normalizar username/password; verificar con dobles ausencia de consulta ante client invalido, `default` para ausente/nulo, caracteres permitidos y limites de longitud 0, 1, 64 y 65, sin trim ni conversion de mayusculas. Evidencia: `AuthLoginServiceTest`; validacion `mvn "-Dtest=AuthLoginServiceTest,AuthTokenServiceTest" test` (PASS, 26 tests, 0 failures, 0 errors, 0 skipped).
- [x] 4.2 Conectar Resource, servicio de login, persistencia existente y servicio de tokens; verificar que solo credenciales validas emiten JWT, `sub`/`role` provienen del usuario persistido y el cuerpo 200 conserva claves, tipos, escape HTML, `debug` y `expiresIn=3600`. Evidencia: `AuthLoginIntegrationTest` verifica respuesta/cuerpo y claims de la identidad persistida.
- [x] 4.3 Incorporar el nuevo 400 `Client is invalid` y mantener prioridad request/username/password/client; verificar por HTTP client invalido, ausente/nulo/valido y regresion completa de 400/401, incluidos casos donde varias entradas fallan. Evidencia: pruebas HTTP de client y prioridad más dobles de `AuthLoginServiceTest` verifican cero consultas a DB para client inválido. Validacion: `mvn "-Dtest=AuthResourceTest,AuthResourceCharacterizationTest,AuthLoginIntegrationTest,AuthLoginServiceTest,AuthTokenServiceTest" test` (PASS, 52 tests, 0 failures, 0 errors, 0 skipped).
- [x] 4.4 Cumplir el 500 aprobado con `message=Unknown error` y clase en `exception`; actualizar explicitamente la expectativa del mensaje no nulo de 1.7 y probar fallos de emision sin respuesta 200 ni token de sustitucion. Validacion: `mvn "-Dtest=AuthResourceCharacterizationTest,AuthLoginIntegrationTest" test` (PASS, 24 tests, 0 failures, 0 errors, 0 skipped).
- [ ] 4.5 Sustituir la prueba temporal legacy de 1.6 por pruebas JWT y retirar el emisor reversible sin modo dual ni fallback; verificar rechazo legacy, ausencia de referencias funcionales al emisor antiguo y paso de las pruebas de tokens y login.
- [x] 4.6 Agregar prueba HTTP que compruebe `iat` contra el intervalo envio/recepcion y mida inmediatamente `abs(expiresIn - (exp - nowObservado)) <= 5`; verificar el umbral real sin ampliarlo para ocultar demoras, ademas de `exp - iat = 3600`. Evidencia: `AuthLoginIntegrationTest.emitsJwtWithPersistedIdentityAndPreservesSuccessBody`; incluida en `mvn test` (PASS, 78 tests, 0 failures, 0 errors, 0 skipped).
- [ ] 4.7 Actualizar ejemplos/cambios de contrato de login: nuevo 400, regex y default de client, JWT incompatible con legacy y mensaje 500 aprobado; verificar consistencia de la documentacion con las pruebas HTTP y conservar los contratos fuera de alcance sin cambios.

## 5. Diagnosticos sin datos sensibles

Depende de la integracion del grupo 4. No refactorizar SQL, hashing, esquema ni manejo global de logs.

- [ ] 5.1 Incorporar eventos auth con nombres/resultados controlados y errores inesperados explicitos; verificar con captura de logs de login valido, invalido, fallo del emisor y token expirado que no aparecen body, headers, tokens, passwords, secretos ni mensajes crudos de excepciones, usando marcadores sinteticos sin imprimirlos al fallar.
- [ ] 5.2 Sustituir de forma acotada los `printStackTrace()` de persistencia que puedan filtrar consultas/credenciales por diagnosticos sanitizados; verificar fallos controlados de inicializacion y consulta sin corrupcion de la base real, sin SQL/datos sensibles en logs y conservando resultados actuales del colaborador.
- [ ] 5.3 Documentar categorias y resultados observables y aportar evidencia redactada de logs para el Issue; verificar que ejemplos y evidencias no contienen valores sensibles y que no se habilita el volcado de requests/responses de REST Assured.

## 6. Validacion integrada y cierre del checklist

Depende de los grupos 1-5. Este grupo integra validaciones ya introducidas; no posterga la escritura de pruebas ni documentacion de etapas previas.

- [ ] 6.1 Ejecutar primero las clases afectadas con un unico selector Maven y despues `mvn verify`; comprobar compilacion, regresion de login/health y reporte JaCoCo con cobertura del codigo modificado de al menos 85%, sin exclusiones nuevas para inflar el resultado. Registrar comandos reales y resultados.
- [ ] 6.2 Ejecutar conjuntamente las pruebas de artefacto empaquetado de 3.3 y el smoke de login JWT/health en un entorno local aislado; verificar base de pruebas, secreto sintetico, expiracion y cierre de todos los procesos auxiliares sin actuar sobre otros repositorios.
- [ ] 6.3 Revisar que formato nuevo y necesidad de reautenticacion, condicion externa de coordinacion de consumidores y rollback seguro quedan documentados segun el diseno; verificar que no se afirma compatibilidad externa no probada ni se agregan cambios de consumidores o un switch legacy.
- [ ] 6.4 Ejecutar `openspec validate hu-01-emitir-tokens-firmados-claims --strict` y `git diff --check`; contrastar el diff con el alcance del Issue y confirmar ausencia de cambios en SQL/hash, esquema, CORS, otros endpoints o dependencias no aprobadas. Marcar casillas solo con evidencia verificable, manteniendo el Issue como unidad de trabajo.
