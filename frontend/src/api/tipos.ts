// Tipos de los DTOs del backend (módulo de seguridad y auditoría).

export interface Perfil {
  id: number;
  nombreUsuario: string;
  nombreCompleto: string;
  correo: string | null;
  rol: string;
  rolPropio: string;
  autoridadDelegada: string | null;
  cargo: string | null;
  ultimoAcceso: string | null;
  permisos: string[];
}

export interface LoginResponse {
  token: string;
  expira: string;
  perfil: Perfil;
}

export type EstadoCuenta = "ACTIVO" | "BLOQUEADO" | "INACTIVO";

export interface Usuario {
  id: number;
  nombreUsuario: string;
  idPersona: number;
  ci: string;
  nombres: string;
  apellidos: string;
  correo: string | null;
  telefono: string | null;
  idRol: number;
  rol: string;
  cargo: string | null;
  area: string | null;
  estado: EstadoCuenta;
  intentosFallidos: number;
  ultimoAcceso: string | null;
  fechaCreacion: string;
}

export interface Rol {
  id: number;
  nombre: string;
  descripcion: string | null;
  usuariosActivos: number;
  permisos: string[];
}

export interface Permiso {
  id: number;
  codigo: string;
  modulo: string;
  descripcion: string | null;
}

export interface RegistroAuditoria {
  id: number;
  idUsuario: number | null;
  accion: string;
  entidad: string;
  idEntidad: string | null;
  fechaHora: string;
  direccionIp: string | null;
  detalle: string | null;
}

export const NOMBRES_ROL: Record<string, string> = {
  DIRECTOR_CARRERA: "Director de Carrera",
  COORDINADOR_CARRERA: "Coordinador de Carrera",
  APOYO_PROFESIONAL: "Apoyo Profesional",
};

// ---------- Iteración 2: documental y trámites ----------

export interface Destinatario {
  id: number;
  nombre: string;
  cargo: string | null;
  unidad: string;
  institucion: string;
  correo: string | null;
  activo: boolean;
}

export type TipoCorrespondencia = "RECIBIDA" | "EMITIDA";
export type EstadoCorrespondencia = "REGISTRADA" | "DERIVADA" | "EN_TRAMITE" | "RESPONDIDA" | "ARCHIVADA";

export interface Correspondencia {
  id: number;
  codigo: string;
  tipo: TipoCorrespondencia;
  asunto: string;
  descripcion: string | null;
  fechaDocumento: string;
  fechaRegistro: string;
  idDestinatario: number;
  destinatario: string;
  unidad: string;
  estado: EstadoCorrespondencia;
  autorizado: boolean;
  registradoPor: string;
}

export interface Archivo {
  id: number;
  nombreArchivo: string;
  tipoMime: string;
  tamanoBytes: number;
  hashSha256: string;
  fechaCarga: string;
  idUsuarioCarga: number;
  idCorrespondencia: number | null;
  idExpediente: number | null;
  descripcion: string | null;
}

export interface Seguimiento {
  id: number;
  fecha: string;
  estado: string;
  observacion: string | null;
}

export interface CorrespondenciaDetalle {
  correspondencia: Correspondencia;
  adjuntos: Archivo[];
  seguimiento: Seguimiento[];
}

/** HU-06: vínculo entre un oficio original y su respuesta o reiteración, visto desde un oficio. */
export type TipoVinculo = "RESPUESTA" | "REITERACION";

export interface VinculoCorrespondencia {
  id: number;
  tipo: TipoVinculo;
  /** VINCULADO: el otro oficio es la respuesta/reiteración de este · ORIGINAL: el otro es el oficio original. */
  sentido: "VINCULADO" | "ORIGINAL";
  idCorrespondencia: number;
  codigo: string;
  asunto: string;
  tipoOficio: TipoCorrespondencia;
  fechaDocumento: string;
  estado: EstadoCorrespondencia;
  fecha: string;
  registradoPor: string;
}

export interface Requisito {
  id: number;
  descripcion: string;
  obligatorio: boolean;
}

export interface TipoTramite {
  id: number;
  nombre: string;
  descripcion: string | null;
  plazoReferenciaDias: number;
  requiereDictamen: boolean;
  requisitos: Requisito[];
}

export interface Estudiante {
  id: number;
  registro: string;
  nombreCompleto: string;
  ci: string;
  plan: string;
  estado: string;
}

export interface Tramite {
  id: number;
  numero: string;
  idTipoTramite: number;
  tipo: string;
  idEstudiante: number;
  estudiante: string;
  registro: string;
  idExpediente: number | null;
  expediente: string | null;
  fechaRecepcion: string;
  fechaLimite: string;
  fechaCierre: string | null;
  estadoActual: string;
  prioridad: string;
  observaciones: string | null;
  vencido: boolean;
  diasRestantes: number;
  registradoPor: string;
}

export interface DocumentoTramite {
  id: number;
  idRequisito: number | null;
  descripcion: string;
  obligatorio: boolean;
  esRequisito: boolean;
  presentado: boolean;
  verificado: boolean;
  observacion: string | null;
  archivo: Archivo | null;
}

export interface Historial {
  id: number;
  estadoOrigen: string | null;
  estadoDestino: string;
  evento: string;
  idUsuario: number;
  usuario: string | null;
  fechaHora: string;
  observacion: string | null;
}

export interface Accion {
  evento: string;
  etiqueta: string;
  destino: string;
  requiereJustificacion: boolean;
}

export interface Dictamen {
  id: number;
  fecha: string;
  resultado: string;
  fundamento: string;
}

export interface TramiteDetalle {
  tramite: Tramite;
  documentos: DocumentoTramite[];
  historial: Historial[];
  dictamen: Dictamen | null;
  acciones: Accion[];
  requisitosPendientes: number;
  requiereDictamen: boolean;
}

export interface Expediente {
  id: number;
  codigo: string;
  idEstudiante: number | null;
  estudiante: string | null;
  registro: string | null;
  titulo: string;
  descripcion: string | null;
  estado: string;
  fechaApertura: string;
}

export interface ExpedienteDetalle {
  expediente: Expediente;
  tramites: { id: number; numero: string; tipo: string; estadoActual: string; fechaRecepcion: string; fechaLimite: string }[];
  documentos: Archivo[];
}

// ---------- Iteración 3: RPA ----------

export interface ProcesoRpa {
  id: number;
  nombre: string;
  descripcion: string | null;
  version: string;
  estado: string;
  tiempoMaxSegundos: number;
  tiempoManualMinutos: number;
}

export interface Revisor {
  id: number;
  nombreCompleto: string;
  rol: string;
  correo: string | null;
}

export interface Previsualizacion {
  autorizados: number;
  noAutorizados: number;
  idInformeVigente: number | null;
  estadoInformeVigente: string | null;
  ejecucionEnCurso: boolean;
}

export interface EjecucionRpa {
  id: number;
  proceso: string;
  periodoDesde: string;
  periodoHasta: string;
  tipoCorrespondencia: string;
  estado: "PENDIENTE" | "EN_PROCESO" | "COMPLETADA" | "FALLIDA";
  situacion: string;
  idUsuario: number;
  iniciadaPor: string | null;
  idRevisor: number;
  revisor: string | null;
  fechaInicio: string;
  fechaFin: string | null;
  duracionMs: number | null;
  registrosProcesados: number;
  registrosValidos: number;
  erroresDetectados: number;
  idEjecucionOrigen: number | null;
  idReejecucion: number | null;
  idInforme: number | null;
  estadoInforme: string | null;
  estadoCorreo: string | null;
  incidencias: number;
}

export interface PasoRpa {
  paso: string;
  descripcion: string;
  estado: "OK" | "ADVERTENCIA" | "ERROR" | "EN_CURSO";
  origen: "BACKEND" | "TRABAJADOR";
  inicio: string;
  fin: string | null;
  duracionMs: number | null;
  detalle: string | null;
}

export interface IncidenciaRpa {
  id: number;
  idEjecucion: number;
  tipo: string;
  descripcion: string | null;
  estado: string;
  idReejecucion: number | null;
  fechaRegistro: string;
}

export interface EjecucionDetalle {
  ejecucion: EjecucionRpa;
  pasos: PasoRpa[];
  incidencias: IncidenciaRpa[];
  mensajeError: string | null;
  puedeReejecutar: boolean;
  motivoNoReejecutable: string | null;
}

export interface MetricasRpa {
  totalEjecuciones: number;
  completadas: number;
  fallidas: number;
  enCurso: number;
  tasaExito: number;
  duracionPromedioMs: number | null;
  duracionMaximaMs: number | null;
  registrosProcesados: number;
  erroresDetectados: number;
  tiempoManualMinutos: number;
  tiempoAutomatizadoPromedioMin: number;
  ahorroEstimadoHoras: number;
  informesPendientes: number;
  informesAprobados: number;
  informesDevueltos: number;
  correosEnviados: number;
  reejecuciones: number;
}

export interface InformeRpa {
  id: number;
  idEjecucion: number;
  periodoDesde: string;
  periodoHasta: string;
  tipoCorrespondencia: string;
  totalRecibidos: number;
  totalEmitidos: number;
  hashSha256: string | null;
  fechaGeneracion: string;
  estadoRevision: string;
  observacionesRevision: string | null;
  fechaRevision: string | null;
  idRevisor: number;
  revisor: string | null;
  estadoCorreo: string | null;
}

export interface CorreoRpa {
  id: number;
  destinatarios: string[];
  asunto: string;
  cuerpo: string;
  estado: string;
  idUsuarioAutoriza: number | null;
  autorizadoPor: string | null;
  fechaAutorizacion: string | null;
  fechaEnvio: string | null;
  mensajeError: string | null;
  adjunto: string;
}

export interface InformeDetalle {
  informe: InformeRpa;
  detalles: { categoria: string; clave: string; cantidad: number }[];
  correo: CorreoRpa | null;
  incidencias: IncidenciaRpa[];
  puedeAprobar: boolean;
  puedeDevolver: boolean;
  puedeEditarBorrador: boolean;
  puedeReintentarEnvio: boolean;
}

export interface Notificacion {
  tipo: string;
  titulo: string;
  detalle: string | null;
  ruta: string;
  fecha: string | null;
}

// ---------- Iteración 4: académico, institucional y autoridad ----------

export interface Periodo {
  id: number;
  gestion: number;
  semestre: number;
  nombre: string;
  fechaInicio: string;
  fechaFin: string;
  fechaCierreInscripcion: string | null;
  estado: "PLANIFICADO" | "ABIERTO" | "CERRADO";
  grupos: number;
}

export interface ResumenPlanificacion {
  periodo: Periodo;
  materias: number;
  grupos: number;
  cupoTotal: number;
  inscritos: number;
  gruposSinDocente: number;
  gruposLlenos: number;
  ocupacion: number;
}

export interface Materia {
  id: number;
  sigla: string;
  nombre: string;
  plan: string;
  nivel: number;
  horas: number;
}

export interface Grupo {
  id: number;
  idMateria: number;
  sigla: string;
  materia: string;
  plan: string;
  nivel: number;
  idPeriodo: number;
  periodo: string;
  codigo: string;
  cupo: number;
  inscritos: number;
  disponibles: number;
  turno: string;
  horario: string | null;
  aula: string | null;
  docentes: string[];
}

export interface Inscripcion {
  id: number;
  idEstudiante: number;
  registro: string;
  estudiante: string;
  plan: string;
  fechaInscripcion: string;
  estado: string;
  casoEspecial: boolean;
}

export interface Migracion {
  id: number;
  idEstudiante: number;
  registro: string;
  estudiante: string;
  planOrigen: string;
  planDestino: string;
  fecha: string;
  observacion: string | null;
}

export interface Docente {
  id: number;
  codigo: string;
  nombreCompleto: string;
  categoria: string | null;
  dedicacion: string | null;
  especialidad: string | null;
  limiteHoras: number;
  horasAsignadas: number;
}

export interface Asignacion {
  id: number;
  idDocente: number;
  docente: string;
  idGrupo: number;
  grupo: string;
  materia: string;
  periodo: string;
  tipo: string;
  horas: number;
  fechaInicio: string;
  fechaFin: string | null;
  observacion: string | null;
  vigente: boolean;
}

export interface Titulado {
  id: number;
  idEstudiante: number;
  registro: string;
  estudiante: string;
  idTrabajo: number | null;
  tituloTrabajo: string | null;
  gestion: number;
  modalidad: string;
  fechaDefensa: string | null;
  nroDiploma: string | null;
}

export interface TrabajoGrado {
  id: number;
  idEstudiante: number;
  registro: string;
  estudiante: string;
  titulo: string;
  tutor: string | null;
  gestion: number;
  palabrasClave: string | null;
  archivo: Archivo | null;
}

export interface PoaResumen {
  id: number;
  gestion: number;
  objetivo: string;
  estado: string;
  avance: number;
  actividades: number;
  concluidas: number;
  retrasadas: number;
}

export interface ActividadPoa {
  id: number;
  descripcion: string;
  meta: string;
  responsable: string | null;
  fechaInicio: string;
  fechaFin: string;
  avance: number;
  estado: string;
  eventos: number;
}

export interface SeguimientoActividad {
  id: number;
  fecha: string;
  avance: number;
  observacion: string | null;
  evidencia: string | null;
  idEvento: number | null;
}

export interface Evento {
  id: number;
  nombre: string;
  tipo: string;
  fecha: string;
  responsable: string | null;
  idActividad: number | null;
  actividad: string | null;
}

export interface Transferencia {
  id: number;
  idOrigen: number;
  origen: string;
  idDestino: number;
  destino: string;
  rolDestino: string;
  motivo: string;
  resolucion: string | null;
  fechaInicio: string;
  fechaFin: string | null;
  estado: string;
  activaHoy: boolean;
}

// ---------- Iteración 5: inteligencia, reportes y alertas ----------

export interface Dashboard {
  fecha: string;
  tramites: {
    total: number; enCurso: number; vencidos: number; promedioDiasAtencion: number; porcentajeATiempo: number;
    porEstado: { clave: string; valor: number }[];
    porTipo: { tipo: string; total: number; cerrados: number; aTiempo: number; promedioDias: number }[];
  };
  correspondencia: { ultimoMes: number; sinAutorizar: number; porMes: { mes: string; recibidas: number; emitidas: number }[] };
  rpa: MetricasRpa;
  alertas: ResumenAlertas;
  poa: { gestion: number | null; avance: number; actividades: number; concluidas: number; retrasadas: number };
  academico: { periodo: string | null; grupos: number; cupo: number; inscritos: number; ocupacion: number; gruposSinDocente: number };
}

export interface ResumenAlertas {
  abiertas: number; altas: number; medias: number; bajas: number; porPlazo: number; porPrediccion: number;
}

export interface Alerta {
  id: number; idTramite: number; numeroTramite: string; tipoTramite: string; estudiante: string; estadoTramite: string;
  fechaLimite: string; origen: "PLAZO" | "PREDICCION"; nivel: "ALTA" | "MEDIA" | "BAJA"; probabilidad: number | null;
  motivo: string; estado: "ABIERTA" | "EN_ATENCION" | "ATENDIDA"; fechaGeneracion: string; atendidaPor: string | null; fechaAtencion: string | null;
}

export interface AnalisisPredictivo {
  historicos: number; demoradosHistoricos: number; exactitud: number | null; modelo: string; evaluados: number;
  alertasCreadas: number; alertasActualizadas: number;
  riesgos: { idTramite: number; numero: string; tipo: string; estudiante: string; estado: string; fechaLimite: string; probabilidad: number; nivel: string }[];
}

export interface DocumentoOcr { archivo: Archivo; estadoOcr: "PENDIENTE" | "EXTRAIDO" | "VALIDADO"; confianza: number | null; validado: boolean }

export interface ExtraccionOcr {
  id: number; archivo: Archivo; texto: string; confianza: number; validado: boolean; idUsuarioValidacion: number | null; fechaValidacion: string | null;
}

export interface ResultadoBusqueda {
  tipo: "CORRESPONDENCIA" | "TRAMITE" | "DOCUMENTO" | "TRABAJO_GRADO"; id: number; titulo: string; detalle: string;
  fecha: string | null; ruta: string; relevancia: number | null;
}

export interface RespuestaBusqueda { resultados: ResultadoBusqueda[]; tiposConsultados: string[]; motor: string }

export interface RespuestaAsistente {
  intencion: string; confianza: number; texto: string; enlaces: { texto: string; ruta: string }[]; sugerencias: string[];
}
