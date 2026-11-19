// Cliente HTTP de la API. El token JWT viaja solo en la cabecera Authorization (nunca en la URL).

const CLAVE_TOKEN = "p25.token";

export function obtenerToken(): string | null {
  return sessionStorage.getItem(CLAVE_TOKEN);
}

export function guardarToken(token: string | null) {
  if (token) sessionStorage.setItem(CLAVE_TOKEN, token);
  else sessionStorage.removeItem(CLAVE_TOKEN);
}

/** Error de la API con el mensaje en español que devuelve el backend. */
export class ErrorApi extends Error {
  constructor(
    public estado: number,
    mensaje: string,
    public detalles?: Record<string, string> | null,
  ) {
    super(mensaje);
  }
}

let alExpirarSesion: () => void = () => {};
export function registrarAlExpirarSesion(fn: () => void) {
  alExpirarSesion = fn;
}

type Opciones = { metodo?: string; cuerpo?: unknown; params?: Record<string, string | number | undefined | null> };

export async function api<T>(ruta: string, { metodo = "GET", cuerpo, params }: Opciones = {}): Promise<T> {
  const url = new URL("/api" + ruta, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") url.searchParams.set(k, String(v));
    });
  }
  const cabeceras: Record<string, string> = { Accept: "application/json" };
  const token = obtenerToken();
  if (token) cabeceras.Authorization = `Bearer ${token}`;
  if (cuerpo !== undefined) cabeceras["Content-Type"] = "application/json";

  let resp: Response;
  try {
    resp = await fetch(url, { method: metodo, headers: cabeceras, body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo) });
  } catch {
    throw new ErrorApi(0, "No se pudo conectar con el servidor. Verifique su conexión.");
  }

  if (resp.status === 401 && token && !ruta.startsWith("/auth/login")) {
    guardarToken(null);
    alExpirarSesion();
  }
  if (!resp.ok) {
    let mensaje = `Error ${resp.status}`;
    let detalles: Record<string, string> | null = null;
    try {
      const datos = await resp.json();
      mensaje = datos.mensaje ?? mensaje;
      detalles = datos.detalles ?? null;
    } catch {
      /* respuesta sin cuerpo JSON */
    }
    throw new ErrorApi(resp.status, mensaje, detalles);
  }
  if (resp.status === 204) return undefined as T;
  return (await resp.json()) as T;
}

async function lanzarError(resp: Response): Promise<never> {
  let mensaje = `Error ${resp.status}`;
  let detalles: Record<string, string> | null = null;
  try {
    const datos = await resp.json();
    mensaje = datos.mensaje ?? mensaje;
    detalles = datos.detalles ?? null;
  } catch {
    /* sin cuerpo JSON */
  }
  if (resp.status === 401 && obtenerToken()) {
    guardarToken(null);
    alExpirarSesion();
  }
  throw new ErrorApi(resp.status, mensaje, detalles);
}

/** Sube un archivo (multipart/form-data, campo "archivo") con parámetros opcionales. */
export async function subirArchivo<T>(ruta: string, archivo: File, params?: Record<string, string | undefined>): Promise<T> {
  const url = new URL("/api" + ruta, window.location.origin);
  Object.entries(params ?? {}).forEach(([k, v]) => v && url.searchParams.set(k, v));
  const datos = new FormData();
  datos.append("archivo", archivo);
  const token = obtenerToken();
  const resp = await fetch(url, { method: "POST", body: datos, headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!resp.ok) return lanzarError(resp);
  return (await resp.json()) as T;
}

/** Descarga o abre un documento usando el token en la cabecera (nunca en la URL). */
export async function abrirDocumento(idArchivo: number, nombre: string, enLinea = false) {
  const token = obtenerToken();
  const resp = await fetch(`/api/documentos/${idArchivo}/contenido?enLinea=${enLinea}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!resp.ok) return lanzarError(resp);
  const blob = await resp.blob();
  const url = URL.createObjectURL(blob);
  if (enLinea) {
    window.open(url, "_blank", "noopener");
  } else {
    const a = document.createElement("a");
    a.href = url;
    a.download = nombre;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/** Respuesta paginada estándar del backend. */
export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

/** Obtiene un recurso binario (p. ej. el PDF de un informe) como URL local temporal. */
export async function obtenerUrlBlob(ruta: string): Promise<string> {
  const token = obtenerToken();
  const resp = await fetch("/api" + ruta, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!resp.ok) return lanzarError(resp);
  return URL.createObjectURL(await resp.blob());
}
