# Spec Delta

## Purpose

Define el contrato observable de `POST /auth/login`: entrada, respuestas de éxito y de error, y el comportamiento de `docs/ENDPOINTS.md` que debe preservarse. El formato y la verificación del token se especifican en `auth-token`.

## ADDED Requirements

### Requirement: Login con credenciales válidas
El sistema SHALL responder `200 OK` a `POST /auth/login` cuando `username` y `password` correspondan a un usuario existente con su contraseña correcta.

#### Scenario: Credenciales de laboratorio válidas
- **WHEN** se envía `{"username":"student","password":"Workshop123!","client":"angular-workshop"}` con `Content-Type: application/json`
- **THEN** la respuesta tiene estado `200`
- **AND** el cuerpo contiene las claves `token`, `expiresIn`, `user` y `debug`

### Requirement: Forma del cuerpo de éxito
El cuerpo de una respuesta `200` SHALL contener `token` (string), `expiresIn` (entero), `user` con `username`, `displayName` y `role`, y `debug`.

#### Scenario: Valores del cuerpo de éxito
- **WHEN** el usuario `student` se autentica correctamente
- **THEN** `expiresIn` es `3600`
- **AND** `user.username` es `"student"`, `user.role` es `"STUDENT"` y `user.displayName` es `"Workshop Student"`
- **AND** `debug` es `"authenticated-with-legacy-flow"`

#### Scenario: Nombre de despliegue escapado
- **WHEN** el `displayName` persistido del usuario contiene caracteres HTML como `<` o `>`
- **THEN** `user.displayName` se devuelve con esos caracteres escapados

### Requirement: Campos obligatorios
El sistema SHALL responder `400 Bad Request` cuando la solicitud sea nula, cuando `username` sea nulo, vacío o en blanco, o cuando `password` sea nula, vacía o en blanco.

#### Scenario: Solicitud nula
- **WHEN** el cuerpo de la solicitud es nulo
- **THEN** la respuesta tiene estado `400`
- **AND** el cuerpo es `{"message":"Request is null"}`

#### Scenario: Username en blanco
- **WHEN** se envía `username` como `"   "` con una contraseña válida
- **THEN** la respuesta tiene estado `400`
- **AND** el cuerpo es `{"message":"Username is required"}`

#### Scenario: Password vacía
- **WHEN** se envía `password` como `""` con un usuario válido
- **THEN** la respuesta tiene estado `400`
- **AND** el cuerpo es `{"message":"Password is required"}`

### Requirement: Credenciales inválidas
El sistema SHALL responder `401 Unauthorized` cuando el usuario no exista o la contraseña no coincida, con el mismo formato de cuerpo en ambos casos y sin campo `token`.

#### Scenario: Contraseña incorrecta
- **WHEN** se envía `username` `"student"` con una contraseña incorrecta
- **THEN** la respuesta tiene estado `401`
- **AND** el cuerpo es `{"message":"Invalid user student or password"}`
- **AND** el cuerpo no contiene el campo `token`

#### Scenario: Usuario inexistente
- **WHEN** se envía un `username` que no existe con cualquier contraseña
- **THEN** la respuesta tiene estado `401`
- **AND** el cuerpo tiene el mismo formato `{"message":"Invalid user <username> or password"}` que en el caso de contraseña incorrecta

### Requirement: Campo client opcional
El sistema SHALL aceptar solicitudes sin `client` o con `client` nulo sin responder error, y SHALL usar `default` como valor de `client` en ese caso.

#### Scenario: Solicitud sin client
- **WHEN** se envían credenciales válidas sin el campo `client`
- **THEN** la respuesta tiene estado `200`
- **AND** el token emitido contiene `client` con valor `"default"` según `auth-token`

### Requirement: Campo client validado
Cuando `client` esté presente y no cumpla `^[A-Za-z0-9._-]{1,64}$`, el sistema SHALL responder `400 Bad Request` sin autenticar al usuario.

#### Scenario: Client con caracteres no permitidos
- **WHEN** se envían credenciales válidas con `client` como `"angular workshop!"`
- **THEN** la respuesta tiene estado `400`
- **AND** el cuerpo es `{"message":"Client is invalid"}`

#### Scenario: Client en blanco
- **WHEN** se envían credenciales válidas con `client` como `""`
- **THEN** la respuesta tiene estado `400`
- **AND** el cuerpo es `{"message":"Client is invalid"}`

#### Scenario: Client válido
- **WHEN** se envían credenciales válidas con `client` como `"angular-workshop"`
- **THEN** la respuesta tiene estado `200`

### Requirement: Error interno sin cambios de contrato
Ante una excepción no controlada durante el login, el sistema SHALL responder `500 Internal Server Error` con `message` `"Unknown error"` y el nombre de la clase de excepción en `exception`.

#### Scenario: Excepción no controlada
- **WHEN** ocurre una excepción no controlada durante el flujo de login
- **THEN** la respuesta tiene estado `500`
- **AND** el cuerpo contiene `"message":"Unknown error"` y `"exception"` con el nombre de la clase de excepción
