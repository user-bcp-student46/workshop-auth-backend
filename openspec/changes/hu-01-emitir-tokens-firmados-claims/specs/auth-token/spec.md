# Spec Delta

## Purpose

Define el formato observable del token que emite el login: firma verificable, claims mínimos, expiración, configuración del secreto de firma y ausencia de datos sensibles en logs. No fija el algoritmo de firma ni la librería; esa decisión se documenta en `design.md`.

## ADDED Requirements

### Requirement: Token firmado verificable
El token emitido por un login exitoso SHALL ser verificable por un consumidor que conozca `AUTH_TOKEN_SECRET`, y cualquier alteración de su contenido o de su firma SHALL hacer que la verificación falle.

#### Scenario: Token válido verificado con el secreto configurado
- **WHEN** se verifica el token de un login exitoso con el mismo `AUTH_TOKEN_SECRET`
- **THEN** la verificación es correcta

#### Scenario: Token alterado
- **WHEN** se modifica un carácter del payload o de la firma de un token emitido y se verifica
- **THEN** la verificación falla

#### Scenario: Token verificado con otro secreto
- **WHEN** se verifica un token emitido con un secreto distinto del configurado
- **THEN** la verificación falla

### Requirement: Claims mínimos obligatorios
El token SHALL contener los claims `sub`, `client`, `iat` y `exp`, expresados `iat` y `exp` como segundos desde la época Unix.

#### Scenario: Token con claims mínimos
- **WHEN** el usuario `student` se autentica con `client` `"angular-workshop"`
- **THEN** el token contiene `sub`, `client`, `iat` y `exp`

#### Scenario: Token sin uno de los claims mínimos
- **WHEN** un token verificado carece del claim `client`
- **THEN** la verificación falla

### Requirement: Valores de los claims
El claim `sub` SHALL ser el `username` autenticado, el claim `client` SHALL ser el valor validado de la solicitud, y `exp` SHALL ser igual a `iat` más 3600 segundos.

#### Scenario: Valores tras login válido
- **WHEN** el usuario `student` se autentica con `client` `"angular-workshop"`
- **THEN** `sub` es `"student"`, `client` es `"angular-workshop"` y `exp - iat` es `3600`

#### Scenario: Client ausente
- **WHEN** el login se realiza sin el campo `client`
- **THEN** el claim `client` del token es `"default"`

### Requirement: Claim role conservado
El token SHALL incluir el claim `role` con el mismo valor que `user.role` de la respuesta de login.

#### Scenario: Rol en el token
- **WHEN** el usuario `student` se autentica correctamente
- **THEN** el claim `role` del token es `"STUDENT"`, igual a `user.role`

### Requirement: Rechazo de tokens expirados
Un verificador SHALL rechazar cualquier token cuyo instante actual sea igual o posterior a `exp`, y SHALL aceptar tokens cuyo `exp` sea posterior al instante actual.

#### Scenario: Token expirado
- **WHEN** se verifica un token cuyo claim `exp` es anterior al instante actual
- **THEN** la verificación falla por expiración

#### Scenario: Token vigente
- **WHEN** se verifica un token cuyo claim `exp` es posterior al instante actual y la firma es válida
- **THEN** la verificación es correcta

### Requirement: Coherencia de expiresIn
El campo `expiresIn` de la respuesta de login SHALL coincidir con `exp - now` del token emitido, con una tolerancia de 5 segundos.

#### Scenario: expiresIn dentro de la tolerancia
- **WHEN** se recibe una respuesta de login exitosa y se compara `expiresIn` con `exp - now` en el momento de la comparación
- **THEN** la diferencia absoluta es menor o igual a 5 segundos

### Requirement: Rechazo del formato anterior
Un verificador SHALL rechazar los tokens del formato anterior, Base64 de `username|role|exp|secreto` sin firma.

#### Scenario: Token de formato legado
- **WHEN** se verifica un token en el formato Base64 de `username|role|exp|secreto`
- **THEN** la verificación falla

### Requirement: Secreto obligatorio en producción
En el perfil de producción, el servicio SHALL NOT arrancar si `AUTH_TOKEN_SECRET` no está definido o está vacío, y el mensaje de error SHALL NOT incluir el valor de ningún secreto.

#### Scenario: Arranque en producción sin secreto
- **WHEN** el servicio arranca con el perfil de producción y sin `AUTH_TOKEN_SECRET`
- **THEN** el arranque termina con error antes de atender solicitudes

#### Scenario: Arranque en producción con secreto
- **WHEN** el servicio arranca con el perfil de producción y `AUTH_TOKEN_SECRET` definido
- **THEN** el servicio atiende solicitudes de login

### Requirement: Secreto de laboratorio solo fuera de producción
En perfiles no productivos sin `AUTH_TOKEN_SECRET`, el servicio SHALL firmar con un valor de laboratorio documentado como de uso exclusivo de laboratorio, y el valor de laboratorio SHALL NOT usarse en producción.

#### Scenario: Login en perfil de laboratorio sin variable
- **WHEN** el servicio se ejecuta en un perfil no productivo sin `AUTH_TOKEN_SECRET` y se realiza un login válido
- **THEN** la respuesta es `200` y el token es verificable con el valor de laboratorio documentado

### Requirement: Sin datos sensibles en logs
Los logs emitidos durante el login SHALL NOT contener el valor del token, el valor de `AUTH_TOKEN_SECRET` ni la contraseña enviada, en ningún resultado (éxito, credenciales inválidas o token expirado).

#### Scenario: Logs tras login válido, login inválido y verificación expirada
- **WHEN** se ejecutan un login válido, un login con contraseña incorrecta y la verificación de un token expirado
- **THEN** ninguna línea de log contiene el token emitido, el valor de `AUTH_TOKEN_SECRET` ni las contraseñas enviadas
