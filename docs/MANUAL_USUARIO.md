# Manual de usuario

**Sistema Web de Gestión Administrativa con RPA, Dirección de Carrera de Psicología (UAGRM)**

Este manual explica cómo realizar las tareas habituales en el sistema. Está organizado por rol: cada persona ve en el menú solo las opciones que su rol permite.

---

## 1. Ingreso al sistema

1. Abra el sistema en el navegador. Use la dirección que le indique el área de sistemas.
2. Escriba su **usuario** y su **contraseña** y pulse **Ingresar**.
3. Después de **5 intentos fallidos**, la cuenta se bloquea. Para desbloquearla, comuníquese con la Dirección de Carrera.

La sesión dura 8 horas. Para salir, pulse el ícono de salida (⇥) en la esquina superior derecha.

**Cambiar la contraseña:** pulse el ícono de la llave (🔑) en la parte superior. La nueva contraseña debe tener al menos 8 caracteres, con letras y números.

**Notificaciones:** la campana (🔔) muestra los pendientes que le corresponden. Por ejemplo, un informe RPA asignado para su revisión o un correo que no pudo enviarse.

---

## 2. Apoyo Profesional

### 2.1 Registrar un trámite estudiantil
1. Vaya a **Trámites → Registrar trámite**.
2. Busque al estudiante por registro, CI o nombre.
3. Elija el **tipo de trámite**. El sistema muestra el plazo y los requisitos.
4. Marque los requisitos que el estudiante entregó y pulse **Registrar trámite**. El sistema asigna el número (TRM-AAAA-NNNN) y la fecha límite.
5. En el detalle del trámite puede **adjuntar** el archivo escaneado de cada requisito (PDF, JPG, PNG o DOCX, de hasta 10 MB).

> El trámite no puede pasar a revisión hasta que estén todos los requisitos obligatorios.

### 2.2 Registrar correspondencia
1. Vaya a **Correspondencia → Registrar oficio**.
2. Indique el tipo (recibido o emitido), la fecha, la dependencia y el asunto. El código se asigna solo: DCP-REC o DCP-EMI, seguido de la gestión y el número.
3. Abra el oficio para **adjuntar** el documento escaneado y registrar el **seguimiento** (derivado, en trámite, respondido o archivado).
4. Cuando el oficio esté firmado o visado, pulse **Autorizar**. **Solo la correspondencia autorizada entra al informe mensual del robot.**

### 2.3 Ejecutar el informe mensual de correspondencia (robot RPA)
1. Vaya a **Ejecutar RPA**.
2. Revise el periodo (por defecto, el mes anterior), el tipo de correspondencia y el **revisor**.
3. Agregue los **destinatarios del correo**, eligiéndolos de la lista de dependencias o escribiendo otra dirección.
4. El panel derecho muestra cuántos oficios autorizados se consolidarán. Pulse **Ejecutar consolidación**.
5. Siga el avance paso a paso. Al terminar, el informe queda **pendiente de revisión** y el correo, en **borrador**.

> El robot **nunca envía correos**. El envío ocurre solo cuando el Director lo aprueba.
> Si el periodo ya tiene un informe, el sistema no genera otro.

### 2.4 Documentos digitales y OCR
- **Documentos digitales:** sube y consulta archivos sueltos (resoluciones, actas). Cada archivo queda registrado con su huella SHA-256, que permite comprobar que no fue alterado.
- **OCR:**
  1. Elija un documento escaneado y pulse **Procesar**.
  2. Compare el texto extraído con el original, corrija lo necesario y pulse **Validar texto**.
  3. El texto validado podrá encontrarse con la búsqueda.

### 2.5 Titulados y trabajos de grado
- **Trabajos de grado:** registre el título, el tutor, la gestión y las palabras clave, y adjunte el documento final.
- **Titulados:** registre la modalidad, la fecha de defensa y el número de diploma. Las tesis y proyectos de grado deben vincular su trabajo de grado. Al registrar la titulación, el estudiante pasa al estado *Titulado*.

### 2.6 Consultas con filtros
En **Consultas con filtros** puede buscar correspondencia o trámites combinando texto, estado, tipo, dependencia, prioridad y fechas.

---

## 3. Coordinador de Carrera

### 3.1 Actuaciones sobre trámites
En el detalle de un trámite, los botones disponibles dependen de su estado:

| Acción | Cuándo | Requisito |
|---|---|---|
| **Derivar a revisión** | Recepcionado | Todos los requisitos obligatorios registrados |
| **Observar** | En revisión | Justificación obligatoria (mínimo 10 caracteres) |
| **Registrar subsanación** | Observado | Documentación completa |
| **Archivar** | Aprobado o rechazado | — |

Cada actuación queda en el **historial del trámite**, con la fecha, la hora y el usuario. También puede **verificar** cada requisito y registrar el **dictamen técnico** cuando el tipo de trámite lo exige.

### 3.2 Revisión de resultados RPA
En **Revisión RPA** puede revisar el PDF y el borrador del correo, editar el borrador o **devolver** el informe con observaciones. Para autorizar el envío se necesita al Director.

### 3.3 Historial RPA, incidencias y reejecuciones
**Historial RPA** muestra:
- Las métricas de evaluación: tasa de éxito, duración promedio y comparación con el tiempo manual.
- Cada ejecución, con su registro de pasos y sus incidencias.

**Reejecutar** solo se permite si la ejecución **falló** o si su informe fue **devuelto**, y nunca si el correo ya se envió. La nueva ejecución queda enlazada con la anterior. Las incidencias revisadas pueden **cerrarse** con un comentario.

### 3.4 Gestión académica
- **Planificación:** cree el periodo, copie la oferta de grupos de un periodo anterior y **abra** o **cierre** el periodo. Solo puede haber un periodo abierto a la vez.
- **Grupos y cupos:**
  - Cree grupos y ajuste el cupo; nunca puede quedar por debajo del número de inscritos.
  - En 👥 se inscribe a los estudiantes. Pasada la fecha de cierre de inscripciones, solo se admiten **casos especiales** respaldados por un trámite.
  - Desde la lista de inscritos se registran los **cambios de grupo**.
- **Carga docente:**
  - Cada grupo tiene **un solo** docente responsable.
  - Las **coberturas** exigen registrar el antecedente.
  - El sistema impide superar el límite de horas de cada dedicación: 480 h a tiempo completo, 360 h a medio tiempo y 280 h por horario.
- **Migraciones:** registra el cambio entre los planes 148-1 y 148-2 con sus convalidaciones. El plan del estudiante se actualiza automáticamente.

### 3.5 Eventos, alertas, reportes y búsqueda
- **Eventos y talleres:** registre el evento y, si corresponde, vincúlelo a una actividad del POA.
- **Alertas:**
  - Muestran los trámites con el plazo vencido o próximo a vencer, y los que tienen riesgo de demora según el modelo predictivo.
  - Para **atender** una alerta, indique la acción realizada.
- **Reportes:** elija el reporte y el filtro, y descárguelo en **PDF** (para imprimir) o en **Excel** (para analizar).
- **Búsqueda y asistente:**
  - La búsqueda **avanzada** encuentra coincidencias de palabras.
  - La búsqueda **semántica** encuentra documentos relacionados por su significado.
  - El **asistente** responde preguntas como «¿Cuántos trámites están vencidos?».

---

## 4. Director de Carrera

### 4.1 Dashboard
Al ingresar, el **Inicio** muestra los indicadores de la Carrera:
- Trámites en curso y vencidos, y porcentaje de atención a tiempo.
- Correspondencia de los últimos meses.
- Desempeño del robot.
- Alertas, avance del POA y ocupación de grupos.

Cada gráfico tiene la opción **Ver tabla**.

### 4.2 Aprobar o rechazar trámites
En el detalle de un trámite **en revisión**, pulse **Aprobar** o **Rechazar**. El rechazo exige justificación.

### 4.3 Autorizar el envío del informe RPA
1. Abra el informe desde la notificación o desde **Revisión RPA**.
2. Revise el **PDF** y el **borrador**. Puede corregir los destinatarios, el asunto o el mensaje.
3. Pulse **Aprobar y autorizar envío** y confirme. El sistema registra su autorización (usuario, fecha y hora) y envía el correo con el PDF adjunto.
4. Si el envío falla (por ejemplo, porque el servidor de correo no responde), aparece **Reintentar envío**. Un informe nunca se envía dos veces.

### 4.4 Usuarios, roles y permisos
- **Usuarios:** crear cuentas, cambiar el rol, **bloquear**, **desbloquear** o **desactivar** una cuenta (siempre con motivo) y restablecer contraseñas.
- **Roles y permisos:** marque o desmarque los permisos de cada rol. Los cambios rigen de inmediato.

### 4.5 Transferir la autoridad de Dirección
En **Transferir autoridad**, designe a la persona que actuará como Director durante su ausencia. Indique el motivo, las fechas y la resolución.
- Mientras esté vigente, esa persona podrá aprobar trámites y autorizar envíos.
- Usted puede **revocarla** en cualquier momento.
- Al llegar la fecha de fin, la delegación termina sola.

### 4.6 POA y análisis predictivo
- **POA:** registre el avance de cada actividad con su evidencia. Las actividades vencidas sin concluir se marcan como **retrasadas**.
- **Alertas → Análisis predictivo:** entrena el modelo con el histórico de trámites y muestra el riesgo de demora de cada trámite en curso. Los de riesgo alto o medio generan una alerta.

### 4.7 Auditoría
**Auditoría** registra toda operación sensible: quién la hizo, qué hizo, sobre qué registro, cuándo y desde qué dirección IP. Se puede filtrar por acción, entidad, usuario y fechas. Nadie puede modificarla.

---

## 5. Preguntas frecuentes

| Situación | Qué hacer |
|---|---|
| «La cuenta está bloqueada» | Solicite el desbloqueo a la Dirección de Carrera |
| No veo una opción del menú | Su rol no tiene ese permiso; consulte con la Dirección |
| «Faltan N requisitos obligatorios» al derivar | Registre o adjunte los requisitos pendientes del trámite |
| El robot RPA no genera un informe | El periodo ya tiene un informe vigente: búsquelo en **Revisión RPA** |
| «El servicio inteligente no está disponible» | El OCR, la búsqueda semántica, el asistente o la predicción no responden temporalmente. El resto del sistema funciona con normalidad |
