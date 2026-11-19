// Menú lateral por módulos. Cada opción declara los permisos que la habilitan:
// se muestra solo si el usuario tiene al menos uno. Las opciones con "disponible: false"
// pertenecen a iteraciones futuras y se muestran deshabilitadas.

export interface OpcionMenu {
  ruta: string;
  etiqueta: string;
  icono: string;
  permisos: string[];
  disponible: boolean;
}

export interface SeccionMenu {
  titulo: string;
  opciones: OpcionMenu[];
}

/**
 * Permisos que dan acceso a /busqueda. CONSULTAR_FILTROS habilita la búsqueda avanzada, igual que en el
 * backend (GET /api/busqueda); sin él, el Apoyo Profesional no podía abrir la pantalla (HU-22).
 */
export const PERMISOS_BUSQUEDA = ["BUSQUEDA_AVANZADA", "CONSULTAR_FILTROS", "BUSQUEDA_SEMANTICA", "USAR_ASISTENTE"];

export const MENU: SeccionMenu[] = [
  {
    titulo: "General",
    opciones: [{ ruta: "/", etiqueta: "Inicio", icono: "LayoutDashboard", permisos: [], disponible: true }],
  },
  {
    titulo: "Trámites y documental",
    opciones: [
      { ruta: "/tramites", etiqueta: "Trámites", icono: "FileCheck", permisos: ["REGISTRAR_TRAMITE", "CONSULTAR_TRAMITES", "EJECUTAR_ACTUACION_TRAMITE", "APROBAR_RECHAZAR_TRAMITE"], disponible: true },
      { ruta: "/correspondencia", etiqueta: "Correspondencia", icono: "Mail", permisos: ["GESTIONAR_CORRESPONDENCIA", "CONSULTAR_CORRESPONDENCIA", "SEGUIMIENTO_CORRESPONDENCIA"], disponible: true },
      { ruta: "/expedientes", etiqueta: "Expedientes", icono: "FolderOpen", permisos: ["GESTIONAR_EXPEDIENTES", "CONSULTAR_EXPEDIENTES"], disponible: true },
      { ruta: "/documentos", etiqueta: "Documentos digitales", icono: "Files", permisos: ["GESTIONAR_DOCUMENTOS"], disponible: true },
      { ruta: "/consultas", etiqueta: "Consultas con filtros", icono: "ListFilter", permisos: ["CONSULTAR_FILTROS"], disponible: true },
    ],
  },
  {
    titulo: "Automatización RPA",
    opciones: [
      { ruta: "/rpa/ejecutar", etiqueta: "Ejecutar RPA", icono: "Bot", permisos: ["EJECUTAR_RPA"], disponible: true },
      { ruta: "/rpa/revision", etiqueta: "Revisión RPA", icono: "ClipboardCheck", permisos: ["REVISAR_RPA", "AUTORIZAR_ENVIO_RPA"], disponible: true },
      { ruta: "/rpa/historial", etiqueta: "Historial RPA", icono: "History", permisos: ["CONSULTAR_RPA", "GESTIONAR_REEJECUCION_RPA"], disponible: true },
    ],
  },
  {
    titulo: "Académico e institucional",
    opciones: [
      { ruta: "/academico", etiqueta: "Gestión académica", icono: "GraduationCap", permisos: ["GESTIONAR_PLANIFICACION", "GESTIONAR_MATERIAS_GRUPOS", "GESTIONAR_MIGRACIONES", "GESTIONAR_CARGA_DOCENTE", "CONSULTAR_ACADEMICO"], disponible: true },
      { ruta: "/institucional", etiqueta: "Titulados y trabajos", icono: "Award", permisos: ["GESTIONAR_TITULADOS", "GESTIONAR_TRABAJOS_GRADO", "CONSULTAR_INSTITUCIONAL"], disponible: true },
      { ruta: "/poa", etiqueta: "POA", icono: "Target", permisos: ["GESTIONAR_POA", "CONSULTAR_POA"], disponible: true },
      { ruta: "/eventos", etiqueta: "Eventos y talleres", icono: "CalendarDays", permisos: ["GESTIONAR_EVENTOS", "CONSULTAR_INSTITUCIONAL", "CONSULTAR_POA"], disponible: true },
    ],
  },
  {
    titulo: "Inteligencia y reportes",
    opciones: [
      { ruta: "/ocr", etiqueta: "OCR", icono: "ScanText", permisos: ["PROCESAR_OCR", "VALIDAR_OCR"], disponible: true },
      { ruta: "/busqueda", etiqueta: "Búsqueda y asistente", icono: "Search", permisos: PERMISOS_BUSQUEDA, disponible: true },
      { ruta: "/alertas", etiqueta: "Alertas", icono: "BellRing", permisos: ["CONSULTAR_ALERTAS", "ATENDER_ALERTAS"], disponible: true },
      { ruta: "/reportes", etiqueta: "Reportes", icono: "BarChart3", permisos: ["GENERAR_REPORTES"], disponible: true },
    ],
  },
  {
    titulo: "Administración",
    opciones: [
      { ruta: "/usuarios", etiqueta: "Usuarios", icono: "Users", permisos: ["GESTIONAR_USUARIOS"], disponible: true },
      { ruta: "/roles", etiqueta: "Roles y permisos", icono: "ShieldCheck", permisos: ["GESTIONAR_ROLES_PERMISOS"], disponible: true },
      { ruta: "/auditoria", etiqueta: "Auditoría", icono: "ScrollText", permisos: ["CONSULTAR_AUDITORIA"], disponible: true },
      { ruta: "/autoridad", etiqueta: "Transferir autoridad", icono: "ArrowRightLeft", permisos: ["TRANSFERIR_AUTORIDAD"], disponible: true },
    ],
  },
];

/** Filtra el menú según los permisos del usuario (oculta secciones vacías). */
export function menuPara(permisos: string[]): SeccionMenu[] {
  return MENU.map((s) => ({
    ...s,
    opciones: s.opciones.filter((o) => o.permisos.length === 0 || o.permisos.some((p) => permisos.includes(p))),
  })).filter((s) => s.opciones.length > 0);
}
