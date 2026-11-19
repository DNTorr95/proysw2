// Etiquetas y colores de estados compartidos por las pantallas.

type Color = "verde" | "rojo" | "ambar" | "gris" | "azul";

export const ESTADOS_TRAMITE: Record<string, { etiqueta: string; color: Color }> = {
  RECEPCIONADO: { etiqueta: "Recepcionado", color: "gris" },
  EN_REVISION: { etiqueta: "En revisión", color: "azul" },
  OBSERVADO: { etiqueta: "Observado", color: "ambar" },
  APROBADO: { etiqueta: "Aprobado", color: "verde" },
  RECHAZADO: { etiqueta: "Rechazado", color: "rojo" },
  ARCHIVADO: { etiqueta: "Archivado", color: "gris" },
};

export const ESTADOS_CORRESPONDENCIA: Record<string, { etiqueta: string; color: Color }> = {
  REGISTRADA: { etiqueta: "Registrada", color: "gris" },
  DERIVADA: { etiqueta: "Derivada", color: "azul" },
  EN_TRAMITE: { etiqueta: "En trámite", color: "ambar" },
  RESPONDIDA: { etiqueta: "Respondida", color: "verde" },
  ARCHIVADA: { etiqueta: "Archivada", color: "gris" },
};

export const PRIORIDADES: Record<string, Color> = { ALTA: "rojo", MEDIA: "ambar", BAJA: "gris" };

export function formatoTamano(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

export function formatoFecha(iso: string | null | undefined) {
  if (!iso) return "—";
  const [a, m, d] = iso.substring(0, 10).split("-");
  return `${d}/${m}/${a}`;
}

/** Tipos admitidos por el backend (CU-07). */
export const ACEPTA_ARCHIVOS = ".pdf,.jpg,.jpeg,.png,.docx";
export const MAX_BYTES = 10 * 1024 * 1024;

export const SITUACION_RPA: Record<string, { etiqueta: string; color: Color }> = {
  PENDIENTE: { etiqueta: "Pendiente", color: "gris" },
  EN_PROCESO: { etiqueta: "En proceso", color: "azul" },
  FALLIDA: { etiqueta: "Fallida", color: "rojo" },
  COMPLETADA: { etiqueta: "Completada", color: "verde" },
  PENDIENTE_REVISION: { etiqueta: "Pendiente de revisión", color: "ambar" },
  APROBADA: { etiqueta: "Aprobada", color: "verde" },
  DEVUELTA: { etiqueta: "Devuelta", color: "ambar" },
  ENVIADA: { etiqueta: "Enviada", color: "verde" },
  ERROR_ENVIO: { etiqueta: "Error de envío", color: "rojo" },
};

export const ESTADOS_INFORME: Record<string, { etiqueta: string; color: Color }> = {
  PENDIENTE_REVISION: { etiqueta: "Pendiente de revisión", color: "ambar" },
  APROBADO: { etiqueta: "Aprobado", color: "verde" },
  DEVUELTO: { etiqueta: "Devuelto", color: "rojo" },
  ANULADO: { etiqueta: "Anulado", color: "gris" },
};

export const ESTADOS_CORREO: Record<string, { etiqueta: string; color: Color }> = {
  BORRADOR: { etiqueta: "Borrador", color: "gris" },
  AUTORIZADO: { etiqueta: "Autorizado", color: "azul" },
  ENVIADO: { etiqueta: "Enviado", color: "verde" },
  ERROR: { etiqueta: "Error de envío", color: "rojo" },
  ANULADO: { etiqueta: "Anulado", color: "gris" },
};

export function formatoDuracion(ms: number | null | undefined) {
  if (ms === null || ms === undefined) return "—";
  if (ms < 1000) return `${ms} ms`;
  if (ms < 60_000) return `${(ms / 1000).toFixed(1)} s`;
  return `${Math.floor(ms / 60_000)} min ${Math.round((ms % 60_000) / 1000)} s`;
}

/** Fecha local de hoy (aaaa-mm-dd). No usar toISOString(): devuelve la fecha en UTC (en Bolivia, UTC-4, cambia de día a las 20:00). */
export function hoyLocal(dias = 0): string {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
