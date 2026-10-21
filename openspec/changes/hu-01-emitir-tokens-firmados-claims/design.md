# Design

## Context

Este documento desarrolla el cambio aprobado para el [Issue #12](https://github.com/user-bcp-student46/workshop-auth-backend/issues/12). La motivacion y el alcance estan en [proposal.md](proposal.md); los contratos objetivo son [auth-login](specs/auth-login/spec.md) y [auth-token](specs/auth-token/spec.md). El baseline es [ENDPOINTS.md](../../../docs/ENDPOINTS.md).

El diseno y su futura implementacion se limitan a `workshop-auth-backend`, sobre `feature/HU-1`. No requieren consultar, modificar ni ejecutar otros repositorios. Este paso crea solo este documento: no modifica codigo, dependencias, configuracion, specs ni genera tareas.

### Estado tecnico verificado

- Java 21, Quarkus 3.27.4, REST Jackson y CDI; Maven, JUnit 5, REST Assured y JaCoCo ya configurados en [pom.xml](../../../pom.xml).
- [AuthResource.java](../../../src/main/java/com/bcp/workshop/auth/AuthResource.java) valida la solicitud, consulta credenciales, emite el token y arma la respuesta dentro de un mismo metodo. No hay servicio de autenticacion separado.
- [LegacyTokenUtil.java](../../../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) concatena usuario, rol, expiracion y un secreto constante, y aplica Base64. No firma ni verifica tokens y consulta el reloj directamente.
- [LoginRequest.java](../../../src/main/java/com/bcp/workshop/auth/LoginRequest.java) ya acepta `client` como string, pero no se utiliza. Jackson acepta propiedades desconocidas.
- [LegacyDatabase.java](../../../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) usa SQLite, consulta credenciales mediante SQL concatenado y lee `AUTH_DB_URL` directamente del entorno. Su manejo de errores usa `printStackTrace()`, con riesgo de registrar consultas o credenciales. No se cambia su modelo de datos ni su mecanismo de autenticacion en esta historia.
- [application.properties](../../../src/main/resources/application.properties) no tiene configuracion de tokens. Los perfiles actuales solo diferencian la interfaz de escucha HTTP.
- La unica prueba fuente presente, [AuthResourceTest.java](../../../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java), comprueba un 200, el username y un token no nulo. No caracteriza errores, expiracion ni el cuerpo completo. Los reportes o clases compiladas en `target` no sustituyen pruebas fuente.

La aprobacion del cambio se toma como ratificacion de D1-D5 de la propuesta: nuevo formato sin compatibilidad legacy, `role` conservado, tolerancia de cinco segundos, reglas de `client` y secreto obligatorio en produccion. D6 se resuelve abajo mediante JWT HS256 con una libreria.

### Diferencia adicional entre codigo y contrato aprobado

El catch actual devuelve `error.getMessage()` si no es nulo y solo usa `Unknown error` como fallback. La spec aprobada exige siempre `Unknown error` para una excepcion no controlada, conservando `exception`. El diseno sigue la spec, pero reconoce que el caso con mensaje no nulo es un cambio observable respecto del codigo, no una preservacion exacta. Se caracteriza antes y se actualiza esa expectativa explicitamente al implementar; no se modifica aqui el baseline ni la spec aprobada.

## Goals / Non-Goals

**Goals**

- Sustituir el token reversible por un formato firmado interoperable, con emision y verificacion comprobables dentro de este backend.
- Separar la responsabilidad criptografica y la orquestacion minima del login sin introducir una arquitectura nueva para toda la aplicacion.
- Hacer deterministas el tiempo, las pruebas de expiracion y la configuracion requerida al arrancar.
- Preservar las respuestas protegidas y hacer visibles los unicos cambios de comportamiento aprobados.
- Evitar que entradas, tokens, secretos o errores de librerias lleguen sin filtrar a logs o respuestas.

**Non-Goals**

- Agregar endpoints, filtros de autenticacion global, sesiones, refresh tokens, revocacion, rotacion automatica o autorizacion por rol.
- Refactorizar SQLite a otra capa de persistencia, corregir SQL o contrasenas de H1, migrar datos o actualizar dependencias ajenas a JWT.
- Implementar cambios en consumidores externos. Solo se documenta el formato y el requisito de coordinacion del despliegue.
- Cambiar CORS, health, tolerancia de Jackson a campos desconocidos o definir ahora JSON malformado y tipos de contenido no soportados.
- Convertir este laboratorio en un sistema apto para produccion. Se mantiene la advertencia del [README.md](../../../README.md).

## Contratos protegidos

| Superficie | Se preserva | Cambio permitido por las specs |
|---|---|---|
| `POST /auth/login` | Publico, JSON, mismas claves de exito y tipos; `user` proviene del usuario persistido; HTML escapado; `debug` actual | `token` pasa a JWT firmado |
| Validacion existente | Solicitud nula, username/password nulos, vacios o blancos: mismos 400 y mensajes, en ese orden | Despues se valida `client`; error nuevo `400 {"message":"Client is invalid"}` antes de consultar credenciales |
| Credenciales | Usuarios de laboratorio, 401 para usuario inexistente o password incorrecta, mismo mensaje y sin token | Ninguno para un `client` valido |
| `client` | Ausente o nulo permite login | Claim `default` en ese caso; valor presente debe cumplir exactamente `^[A-Za-z0-9._-]{1,64}$`, sin trim ni conversion de mayusculas |
| Tiempo | `expiresIn` entero igual a 3600 | `iat` y `exp` numericos, `exp - iat = 3600`; coherencia con reloj de observacion dentro de cinco segundos |
| Error interno | Estado 500 y claves `message` y `exception` | Mensaje fijo `Unknown error` segun spec; se conserva el nombre de clase, no el texto interno |
| Otros endpoints y datos | Health y usuarios persistidos sin cambios | Ninguno |

El `client` identifica al llamante declarado por la solicitud; no prueba su identidad y no concede permisos. El nuevo verificador no se conecta a las rutas publicas existentes.

## Componentes afectados

Estos son cambios futuros, no archivos creados en este paso.

| Componente | Responsabilidad acotada |
|---|---|
| `AuthResource` | Mantener ruta, validaciones de campos obligatorios, mapeo HTTP, forma del body, escape HTML y manejo del 500. Delegar autenticacion y emision; no incorporar criptografia |
| Servicio de login pequeno en el paquete de auth | Resolver/validar `client`, invocar el `LegacyDatabase` existente y emitir solo despues de autenticar. Devolver un resultado tipado o errores de dominio distinguibles para 400/401 |
| Servicio de tokens CDI | Poseer configuracion validada y reloj; emitir y verificar JWT; devolver resultados tipados con claims y motivo de rechazo, nunca un `null` ambiguo |
| `LegacyTokenUtil` | Retirar el uso en login y eliminar la implementacion reversible cuando las pruebas nuevas cubran el reemplazo; no mantener fallback |
| `LegacyDatabase` | Mantener SQL, esquema y resultados actuales. Solo sustituir diagnosticos crudos capaces de filtrar datos por eventos sanitizados en los caminos usados por login |
| `application.properties` y configuracion de arranque | Declarar secreto/perfiles sin fallback productivo y provocar validacion al iniciar, no en el primer login |
| `pom.xml` | Declarar una unica libreria JWT, sin introducir un mecanismo HTTP de seguridad |
| Pruebas y documentacion locales | Caracterizacion, claims, errores, arranque, logs; documentar formato, configuracion, tolerancia y cambio de compatibilidad |

La separacion Resource/servicio/seguridad sigue [REGLAS.md](../../../REGLAS.md) y [GOOD_PRACTICES.md](../../../docs/GOOD_PRACTICES.md), sin reorganizar el resto del proyecto ni crear interfaces o capas de repositorio nuevas.

## Decisions

### 1. JWT compacto firmado con HS256 mediante jose4j

Se adopta JWS compacto con payload JWT y `alg=HS256`, `typ=JWT`. La firma sigue siendo HMAC-SHA-256: usar una libreria JWT y usar HMAC no son alternativas excluyentes. Se elige `org.bitbucket.b_c:jose4j` para construir y verificar el formato, en lugar de implementar JOSE, serializacion firmada o comparacion de firmas a mano.

La dependencia se declara directamente en el POM; se utiliza la version gestionada por el BOM de Quarkus si existe. Si no existe gestion, se fija una version compatible y revisada al implementar, nunca un rango ni una dependencia transitiva implicita. No se instala ni cambia ninguna dependencia durante este paso.

**Alternativas consideradas**

- HMAC con JCA y un formato propio: evita dependencia, pero obliga a definir y mantener serializacion, parser, codificacion y validaciones criptograficas; mayor riesgo y menor interoperabilidad.
- Extension JWT que configura autenticacion HTTP: integra con Quarkus, pero agrega una superficie no necesaria y puede afectar rutas que deben seguir publicas.
- Firma asimetrica: evita compartir capacidad de emision con verificadores, pero requiere claves y distribucion fuera del alcance aprobado.

### 2. Frontera de emision y verificacion interna, sin endpoints nuevos

El servicio de tokens expone operaciones de emision y verificacion dentro del backend. La verificacion permite probar los requisitos de tokens alterados, expirados y legacy sin implementar consumidores externos ni una ruta publica de validacion. No se ejecuta esa verificacion como un filtro sobre login o health.

El verificador fija HS256 como unico algoritmo permitido, comprueba firma con la clave configurada y exige `sub`, `client`, `iat` y `exp` con sus tipos correctos. Los tokens emitidos incluyen tambien `role`. Se rechazan formatos legacy, estructura invalida, claims requeridos ausentes y firmas invalidas. No se acepta `alg=none` ni el algoritmo sugerido libremente por una entrada.

La aceptacion de un token vigente presupone firma, estructura y claims validos; `exp` futuro no basta por si solo. Si se admite codificacion Base64URL no canonica en la libreria, la frontera la rechaza para que una alteracion del texto no se acepte como el mismo token.

Los rechazos esperados se expresan como resultado tipado con un motivo cerrado (formato, firma, claims o expiracion), sin incluir el token ni excepciones crudas. Errores inesperados de emision no producen un token legacy, vacio o una respuesta 200 de sustitucion: siguen el camino de error interno.

**Alternativas:** un verificador solo en tests no daria una frontera reutilizable y coherente para los escenarios de la spec; un endpoint nuevo ampliaria el contrato sin autorizacion.

### 3. Un reloj y una duracion para toda la operacion

El servicio recibe un `java.time.Clock`, UTC en ejecucion y fijo/controlable en tests. Captura una sola vez el instante de emision en segundos Unix: `iat = now`, `exp = iat + TOKEN_TTL_SECONDS`, con constante nombrada igual a 3600.

La emision devuelve el token y la duracion a partir de esa misma constante; la respuesta conserva `expiresIn = 3600`, sin una segunda duracion independiente en el Resource.

La verificacion usa el reloj recibido y aplica `now >= exp` como rechazo. Se configura tolerancia criptografica de reloj en cero para no aceptar tokens despues de su expiracion exacta. La tolerancia de cinco segundos de `expiresIn` no es una extension de validez del token: cubre la diferencia entre emision y observacion.

Para medirla, una prueba HTTP registra los instantes de envio y recepcion y comprueba `iat` contra ese intervalo. La comparacion exacta `abs(expiresIn - (exp - nowObservado)) <= 5` se hace inmediatamente al recibir el body, en una prueba local controlada; demoras mayores se diagnostican, no se ocultan aumentando la tolerancia. Los limites temporales se prueban sin sleeps con un reloj fijo.

**Alternativas:** llamadas separadas a `Instant.now()` y sleeps producen discrepancias y pruebas fragiles; usar cinco segundos de skew en el verificador contradice el limite `now >= exp`.

### 4. Configuracion explicita y validacion eager del secreto

Se utiliza una propiedad de aplicacion resuelta desde `AUTH_TOKEN_SECRET` mediante configuracion Quarkus/MicroProfile. En produccion no hay default. Un componente de arranque fuerza su resolucion y validacion antes de atender HTTP; no se depende de la inicializacion perezosa de un bean `@ApplicationScoped`.

El valor se interpreta como bytes UTF-8, sin Base64 ni trim implicitos, y debe proporcionar al menos 32 bytes para HS256. Los despliegues deben generar un valor aleatorio con entropia suficiente: longitud por si sola no implica seguridad. La validacion tambien rechaza vacio/blancos y el valor publico de laboratorio en produccion. Un error de configuracion indica la variable y el motivo, nunca su valor.

El fallback de laboratorio se declara exclusivamente en configuracion de perfiles no productivos, no en clases Java ni en la propiedad general. La misma variable permite sobreescribirlo en esos perfiles. Perfiles personalizados usados como laboratorio deben declararse explicitamente; un perfil nuevo sin configuracion falla de forma cerrada, no recibe un secreto implicito. No se generan ni se guardan secretos reales en este repositorio.

**Alternativas:** conservar un default global permite su uso accidental en produccion; validar en el primer login viola el fallo temprano; generar un secreto distinto por instancia rompe verificacion entre instancias y reinicios.

La exigencia de 32 bytes es una precondicion criptografica de la decision HS256; una variable meramente definida pero inutilizable no satisface la configuracion valida del escenario de arranque.

### 5. Validacion de client y proteccion del flujo existente

Se mantiene la prioridad actual: request, username, password; luego el servicio resuelve `client` y lo valida antes de acceder a credenciales. Nulo o ausente produce `default`; no se reinterpretan cadenas vacias ni blancas como ausencia. Se prueban los limites 1 y 64 y los rechazos 0 y 65.

El usuario y rol del token se toman del registro autenticado, no de los valores declarados en el body. No se normalizan username/password ni se modifica su consulta. La validacion del cliente es local, no un registro de clientes o una autorizacion.

**Alternativas:** hacer obligatorio `client` rompe el contrato aprobado; trim silencioso o aceptar cualquier string no respeta la spec.

### 6. Diagnosticos sanitizados y errores explicitos

Se reutiliza el sistema de logging del runtime mediante un logger de categoria auth. Se registran eventos con nombre y resultado controlados para fallos relevantes, sin body, DTO completo, headers, token, secreto, password, SQL ni texto crudo de excepciones. Tampoco se incluyen esos datos en excepciones de dominio o en sus representaciones automaticas.

Los motivos esperados de rechazo se distinguen sin lanzar excepciones con entradas sensibles. Los fallos inesperados se registran a nivel error con categoria/tipo seguros, conservan el 500 aprobado y no se silencian ni se convierten en exito.

Los `printStackTrace()` de `LegacyDatabase` deben reemplazarse de forma acotada porque una excepcion SQL puede incluir credenciales concatenadas. Se preserva su resultado actual ante fallo, sin aprovechar esta historia para corregir SQL, persistencia o sembrado. No se imprimen cadenas de causas sin revisar; omitir contenido sensible reduce el detalle del diagnostico y es un trade-off explicito.

**Alternativas:** intentar redactar despues de registrar o filtrar solo el servicio de tokens deja fugas en otros puntos del mismo login; cambiar globalmente el logging excede el alcance.

## Pruebas de caracterizacion antes del cambio critico

Esta fase es una puerta de entrada a la implementacion, no evidencia de tests ya ejecutados. No se crean pruebas ni se ejecuta Maven en este paso documental.

Usar una base SQLite exclusiva de tests bajo `target`, indicando `AUTH_DB_URL` antes de iniciar la JVM, ya que el codigo actual usa `System.getenv()`. No usar ni modificar la base de laboratorio de `data`. Las fixtures adicionales deben tener nombres propios y limpiarse de forma acotada.

| Grupo previo | Evidencia que debe existir antes de sustituir el flujo |
|---|---|
| Login exitoso | Student y trainer: codigos, tipos y claves completas, valores persistidos, `debug` y `expiresIn=3600`; ninguna clave nueva inesperada |
| Campos obligatorios | Body JSON nulo; username y password ausentes, nulos, vacios y blancos; mensajes y orden cuando varios campos fallan |
| Errores de credenciales | Usuario inexistente y password incorrecta con los cuerpos 401 actuales y sin token |
| Presentacion | Fixture aislada con HTML en displayName; se conserva el escape y no se mutan datos persistidos |
| Entrada compatible | `client` ausente/nulo/valido; propiedades desconocidas siguen aceptadas |
| Token legacy | Prueba unitaria de la estructura decodificada y expiracion aproximadamente a 3600 segundos, sin registrar el token ni copiar secretos reales. Esta expectativa se sustituye explicitamente por JWT, no se mantiene como criterio final |
| Errores internos | Pruebas directas del Resource con un doble del colaborador que falle, tanto con mensaje nulo como no nulo. Registrar la diferencia respecto de `Unknown error`; al aplicar la spec, cambiar solo la expectativa del mensaje no nulo |
| Health | Acceso sin autenticacion y respuesta saludable con `status` y `checks`, sin congelar el contenido variable de checks |

Los dobles para fallos y fixtures se construyen con JUnit y colaboradores Java simples; no se agrega Mockito ni se provoca corrupcion real de la base. Si la caracterizacion descubre otra divergencia de contrato no descrita aqui, se detiene esa parte del cambio para resolverla, en lugar de actualizar expectativas masivamente.

## Estrategia incremental y validacion posterior

1. **Congelar el comportamiento:** agregar y ejecutar la caracterizacion previa sin cambiar produccion. Separar las expectativas protegidas de las dos diferencias deliberadas: JWT/`client` y el mensaje 500 aprobado.
2. **Preparar la frontera de tokens:** declarar la libreria, el reloj y la configuracion validada; cubrir emision/verificacion de forma aislada antes de conectar el Resource. Ninguna migracion de datos.
3. **Integrar login de forma acotada:** introducir la orquestacion minima, validar `client`, sustituir el emisor y conservar el mapping HTTP. Eliminar la ruta legacy cuando la cobertura nueva este lista, sin modo dual.
4. **Cerrar configuracion y diagnosticos:** demostrar fallo temprano en perfil productivo y ausencia de datos sensibles en todos los resultados relevantes, incluidos errores de persistencia y del emisor.
5. **Ejecutar regresion y actualizar documentacion:** reflejar JWT, claims, tolerancia, reglas de client, nueva configuracion y advertencia de incompatibilidad en los documentos de este backend.

### Matriz de pruebas del comportamiento nuevo

- **Tokens unitarios:** firma HS256 correcta, secreto distinto, payload/firma alterados, `alg=none` y algoritmos no permitidos, formato invalido/legacy, ausencia individual de cada claim obligatorio y tipos invalidos. Un token re-firmado pero sin un claim debe fallar por claims, no por firma.
- **Datos y tiempo:** `sub`/`role` del usuario persistido, `client` exacto o `default`, `iat` controlado, `exp - iat = 3600`; verificacion en `exp - 1`, `exp` y `exp + 1`, manteniendo los demas elementos validos.
- **HTTP:** matriz de caracterizacion protegida, nuevo 400 de client, sus limites de longitud y caracteres, y coherencia temporal medida con el umbral real de cinco segundos.
- **Arranque:** procesos/perfiles aislados con secreto ausente, vacio, blanco, insuficiente, valor publico de laboratorio y valor de prueba valido; produccion falla antes de readiness si la configuracion no es valida. Un test que solo invoca el validador no demuestra por si solo este requisito.
- **Logs:** capturar solo las categorias del backend durante login valido, invalido, fallo interno y verificacion expirada. Usar marcadores sinteticos para secretos/passwords y buscar tambien el token emitido en memoria; no imprimirlos al fallar la asercion. No habilitar volcado de requests/responses de REST Assured. Probar errores del acceso a datos para cubrir el antiguo `printStackTrace()`.

Usar los runners existentes: selectores Maven `mvn "-Dtest=<clases afectadas>" test` primero; despues `mvn verify` para regresion y reporte JaCoCo. Comprobar cobertura de al menos 85% del codigo modificado segun las reglas del repositorio, sin confundirla con la cobertura global ni excluir caminos nuevos para alcanzar la meta. Las pruebas de arranque deben ejecutar el artefacto empaquetado con `mvn package quarkus:build` antes de lanzar el proceso de prueba y comprobar su disponibilidad o su salida por error; no asumir que `mvn package` solo produce un ejecutable Quarkus con el POM actual.

## Risks / Trade-offs

- **[Formato incompatible y conflicto historico con #9]** -> La aprobacion de #12 prevalece para el token. Documentar la ruptura y bloquear el despliegue si los consumidores no estan preparados; no inspeccionarlos ni modificarlos en este trabajo.
- **[Clave simetrica compartida]** -> Todo verificador con el secreto puede emitir tokens. Distribuirla solo a componentes confiables y nunca al navegador. Firma asimetrica queda fuera de esta historia.
- **[JWT firmado no cifra]** -> Claims legibles; incluir solo sub, client, iat, exp y role. Nunca introducir password, secreto o informacion personal adicional.
- **[SQL y contrasenas vulnerables permanecen]** -> No presentar la nueva firma como seguridad integral ni habilitar exposicion productiva; H1 sigue siendo un trabajo separado.
- **[Desfase de relojes o red lenta]** -> Relojes sincronizados en despliegue, pruebas deterministas y observacion local dentro de cinco segundos. No ampliar silenciosamente la validez.
- **[Fallback de laboratorio]** -> Exclusivo de perfiles configurados como no productivos; rechazo explicito de ese valor en produccion y pruebas de arranque.
- **[Dependencia criptografica adicional]** -> Version fijada/gestionada, revision de vulnerabilidades y pruebas de parser/algoritmo. No actualizar Quarkus ni otras dependencias como efecto lateral.
- **[Menor detalle diagnostico y clase de excepcion visible]** -> Eventos seguros distinguen resultados sin datos crudos. El campo `exception` sigue exponiendo el tipo por contrato aprobado; no se redisenan todos los errores.
- **[Diferencias de baseline en 500]** -> Caracterizar ambos casos antes y dejar explicito que el mensaje fijo responde a la spec, no a una equivalencia del codigo actual.
- **[Sin revocacion ni rotacion automatica]** -> Un token firmado conserva validez hasta su exp salvo cambio de clave; documentar que el cambio manual de clave invalida todos los tokens existentes.

## Migration Plan y rollback

### Despliegue

1. Validar el backend y la documentacion; configurar `AUTH_TOKEN_SECRET` a traves del entorno del despliegue, nunca mediante un archivo versionado.
2. Verificar en un entorno aislado de este backend el arranque con perfil productivo y secreto de prueba, la emision JWT y la verificacion. No modificar ni consultar otros repositorios.
3. Establecer como condicion externa al despliegue que todos los consumidores reconozcan el formato nuevo y que el secreto solo llegue a verificadores confiables. Este documento no certifica esa preparacion.
4. Hacer el corte coordinado de version. Tokens legacy requieren un nuevo login; no se aceptan ni se convierten a JWT.
5. Comprobar health, login con los cuerpos protegidos y eventos sanitizados. Un health UP no demuestra por si solo claims, firma o expiracion.

No hay migracion de esquema ni de filas SQLite, y no se modifica `AUTH_DB_URL`. En un despliegue con varias instancias se requiere la misma clave y version de token para todas; una mezcla de emisores legacy y JWT no es una estrategia de transicion.

### Rollback

- Antes del corte, si fallan las pruebas de arranque, contrato o claims, no desplegar: conservar el artefacto previo y su configuracion.
- Tras el corte, detener la emision nueva y restaurar el artefacto previo junto con la configuracion de consumidores compatible, mediante el procedimiento del entorno. No utilizar un switch de fallback dentro del servicio.
- Volver a la version anterior restaura el token vulnerable; solo es aceptable en el laboratorio aislado descrito por el README. En un entorno expuesto, la opcion segura es suspender el login hasta corregir hacia adelante, no reactivar el formato legacy.
- Tokens del formato retirado dejan de ser utilizables tras el cambio de version; comunicar la necesidad de nuevo login. No existe rollback criptografico que convierta tokens ya emitidos.
- Mantener la base de datos intacta. Una rotacion de clave exige reautenticacion y no revierte cambios de codigo; no reutilizar una clave comprometida para recuperar compatibilidad.
