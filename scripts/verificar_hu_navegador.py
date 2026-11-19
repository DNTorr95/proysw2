"""
Verificación EN EL NAVEGADOR de las 37 historias de usuario (columna "Web (navegador)" del Plan de Pruebas).

Recorre cada HU con el rol indicado usando Playwright (Chromium, 1440 px de ancho), comprueba el resultado
leyendo la pantalla y guarda al menos una captura por HU en evidencias/hu/HU-XX*.png.
Cada HU se ejecuta en su propio try/except: una falla no detiene a las demás.

Uso (con el sistema levantado con Docker Compose):
    pip install playwright && python -m playwright install chromium
    python scripts/verificar_hu_navegador.py --url http://localhost --mailhog http://localhost:8025
    python scripts/verificar_hu_navegador.py --solo HU-05,HU-06      # solo algunas HU

Notas:
- Solo usa los usuarios de prueba del README y datos ficticios. Los correos solo llegan a MailHog.
- HU-34 (visor de PDF) se ejecuta con el navegador VISIBLE (headless=False): Chromium sin interfaz no trae visor de PDF.
- Cuando la interfaz no ofrece una acción (p. ej. aprobar dos veces), se intenta desde la consola del navegador
  con fetch() y el token de la propia sesión, para registrar la respuesta exacta del servidor.
- El detalle queda en evidencias/hu/resultados.json.
"""
import argparse
import json
import re
import sys
import time
import traceback
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta
from email.header import decode_header, make_header
from pathlib import Path

from playwright.sync_api import Page, expect, sync_playwright

ap = argparse.ArgumentParser()
ap.add_argument("--url", default="http://localhost")
ap.add_argument("--mailhog", default="http://localhost:8025")
ap.add_argument("--solo", default="", help="lista de HU separadas por coma (p. ej. HU-01,HU-05)")
ap.add_argument("--rpa-desde", default="2026-07-01")
ap.add_argument("--rpa-hasta", default="2026-07-31")
args = ap.parse_args()

URL = args.url.rstrip("/")
MH = args.mailhog.rstrip("/")
EVID = Path("evidencias/hu")
ARCH = EVID / "archivos"
DESC = EVID / "descargas"
for d in (EVID, ARCH, DESC):
    d.mkdir(parents=True, exist_ok=True)

CRED = {"director": "Director2026*", "coordinador": "Coordinador2026*", "apoyo": "Apoyo2026*"}
HOY = date.today()
FMT_CORTO = f"{HOY.day}/{HOY.month}/{HOY:%y}"  # formato de fecha corta es-BO que muestra la interfaz
DATOS: dict = {}  # valores compartidos entre HU (códigos, ids)
PLAN: list = []
ABIERTAS: list = []
NAV = {}


# ------------------------------------------------------------------ utilidades
def hu(codigo, rol, ruta, pasos, esperado):
    def deco(fn):
        PLAN.append(dict(codigo=codigo, rol=rol, ruta=ruta, pasos=pasos, esperado=esperado, fn=fn))
        return fn
    return deco


class Sesion:
    """Contexto de navegador independiente (equivale a otra pestaña privada) con un usuario."""

    def __init__(self, usuario=None, clave=None, nav=None, alto=900):
        self.ctx = (nav or NAV["b"]).new_context(viewport={"width": 1440, "height": alto}, locale="es-BO", accept_downloads=True)
        self.ctx.set_default_timeout(20_000)
        self.page = self.ctx.new_page()
        self.consola = []
        self.page.on("console", lambda m: m.type == "error" and self.consola.append(m.text))
        self.page.on("pageerror", lambda e: self.consola.append(f"pageerror: {e}"))
        ABIERTAS.append(self)
        if usuario:
            self.login(usuario, clave or CRED[usuario])

    def login(self, usuario, clave):
        p = self.page
        p.goto(f"{URL}/login")
        p.fill("#usuario", usuario)
        p.fill("#contrasena", clave)
        p.click("button[type=submit]")
        p.wait_for_url(f"{URL}/")
        p.wait_for_load_state("networkidle")

    def salir(self):
        self.page.click("button[aria-label='Cerrar sesión']")
        self.page.wait_for_url(f"{URL}/login")

    def ir(self, ruta):
        self.page.goto(f"{URL}{ruta}")
        self.page.wait_for_load_state("networkidle")

    def cerrar(self):
        try:
            self.ctx.close()
        except Exception:
            pass


def cap(page: Page, codigo: str, suf: str = "", completa=True):
    page.wait_for_timeout(400)
    ruta = EVID / f"{codigo}{suf}.png"
    page.screenshot(path=str(ruta), full_page=completa)
    return ruta.name


def menu(page: Page):
    return [t.strip() for t in page.locator("aside nav a").all_inner_texts()]


def avisos(page: Page, cont=None):
    """Textos de los avisos de error visibles (role=alert)."""
    loc = (cont or page).locator("[role=alert]")
    return [t.strip() for t in loc.all_inner_texts() if t.strip()]


def esperar_aviso(page: Page, cont=None, timeout=15_000):
    loc = (cont or page).locator("[role=alert]").last
    loc.wait_for(state="visible", timeout=timeout)
    return loc.inner_text().strip()


def elegir(page_o_loc, selector: str, texto: str):
    """Selecciona la primera opción cuyo texto contiene 'texto'; devuelve el texto de la opción."""
    op = page_o_loc.locator(f"{selector} option", has_text=texto).first
    v = op.get_attribute("value")
    t = op.inner_text().strip()
    if v is None:  # <option> sin atributo value: su valor es el texto
        page_o_loc.locator(selector).select_option(label=t)
    else:
        page_o_loc.locator(selector).select_option(value=v)
    return t


def dd_de(cont, etiqueta: str):
    return cont.locator("dt", has_text=re.compile(f"^{re.escape(etiqueta)}$")).first.locator("xpath=following-sibling::dd[1]").inner_text().strip()


def fetch_nav(page: Page, metodo: str, ruta: str, cuerpo=None):
    """Petición desde la consola del navegador con el token de la sesión (para acciones que la interfaz no ofrece)."""
    return page.evaluate(
        """async ([m, r, c]) => {
            const t = sessionStorage.getItem('p25.token');
            const resp = await fetch('/api' + r, { method: m, headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + t },
                                                   body: c === null ? undefined : JSON.stringify(c) });
            const txt = await resp.text();
            let mensaje = txt;
            try { mensaje = JSON.parse(txt).mensaje ?? txt; } catch (e) {}
            return { estado: resp.status, mensaje: String(mensaje).slice(0, 400) };
        }""",
        [metodo, ruta, cuerpo],
    )


def con_respuesta(page: Page, patron: str, accion, timeout=15_000):
    """Ejecuta la acción y espera la respuesta GET de la API que dispara (y un instante para que React pinte)."""
    with page.expect_response(lambda r: patron in r.url and r.request.method == "GET", timeout=timeout) as info:
        accion()
    info.value.finished()
    page.wait_for_timeout(300)


def buscar_estudiante(cont, registro: str):
    cont.get_by_label("Buscar estudiante").fill(registro)
    cont.locator("ul button", has_text=registro).first.click()


def subir(cont, archivo: Path):
    cont.locator("input[type=file]").first.set_input_files(str(archivo))


# --- API (solo para consultas auxiliares y para restaurar datos si la interfaz falla) ---
_tok = {}


def api(usuario, metodo, ruta, cuerpo=None):
    if usuario not in _tok:
        r = urllib.request.Request(f"{URL}/api/auth/login", method="POST",
                                   data=json.dumps({"nombreUsuario": usuario, "contrasena": CRED[usuario]}).encode())
        r.add_header("Content-Type", "application/json")
        _tok[usuario] = json.loads(urllib.request.urlopen(r).read())["token"]
    r = urllib.request.Request(f"{URL}/api{ruta}", method=metodo, data=None if cuerpo is None else json.dumps(cuerpo).encode())
    r.add_header("Content-Type", "application/json")
    r.add_header("Authorization", "Bearer " + _tok[usuario])
    try:
        with urllib.request.urlopen(r) as x:
            t = x.read()
            return x.status, (json.loads(t) if t[:1] in (b"{", b"[") else t)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:400]


def _partes(mime):
    for pt in (mime or {}).get("Parts") or []:
        yield pt
        yield from _partes(pt.get("MIME"))


def correos():
    with urllib.request.urlopen(f"{MH}/api/v2/messages?limit=200") as r:
        datos = json.loads(r.read())
    lista = []
    for it in datos.get("items", []):
        h = it["Content"]["Headers"]
        adj = []
        for pt in _partes(it.get("MIME")):
            cd = " ".join(pt["Headers"].get("Content-Disposition", []))
            ct = " ".join(pt["Headers"].get("Content-Type", []))
            if "attachment" in cd or "application/pdf" in ct:
                m = re.search(r'name="?([^";\s]+)', cd + " " + ct)
                adj.append(m.group(1) if m else ct)
        lista.append({"id": it["ID"], "asunto": str(make_header(decode_header(h.get("Subject", [""])[0]))),
                      "para": ", ".join(h.get("To", [])), "creado": it.get("Created"), "adjuntos": sorted(set(adj))})
    return lista


def preparar_archivos():
    pg = NAV["b"].new_page()
    for nombre, titulo in [("hu05-oficio.pdf", "Oficio recibido (prueba HU-05)"), ("hu07-valido.pdf", "Resolución de prueba (HU-07)"),
                           ("hu08-expediente.pdf", "Documento de expediente (HU-08)"), ("hu18-trabajo.pdf", "Trabajo de grado (HU-18)")]:
        pg.set_content(f"<h1>{titulo}</h1><p>Documento PDF pequeño con datos ficticios generado para la verificación en el navegador.</p>")
        pg.pdf(path=str(ARCH / nombre), format="A4")
    (ARCH / "hu07-vacio.pdf").write_bytes(b"")
    (ARCH / "hu07-texto-renombrado.pdf").write_text("Este es un archivo de texto plano renombrado con extension .pdf\n", encoding="utf-8")
    pg.set_viewport_size({"width": 1240, "height": 640})
    pg.set_content("""<body style="background:#fff;margin:60px;font-family:'Times New Roman',serif;color:#111">
        <p style="font-size:34px;font-weight:bold">ACTA DE REUNION DEL CONSEJO DE CARRERA</p>
        <p style="font-size:30px">Se aprueba el convenio de practicas con el Centro Comunitario Arcoiris.</p>
        <p style="font-size:30px">Codigo de control QUETZAL 2026</p>
        <p style="font-size:30px">Santa Cruz de la Sierra, 15 de julio de 2026</p></body>""")
    pg.screenshot(path=str(ARCH / "hu21-acta-escaneada.png"))
    pg.close()


# ================================================================== SEGURIDAD
MENU_ESPERADO = {
    "director": (["Usuarios", "Roles y permisos", "Auditoría", "Transferir autoridad", "Revisión RPA", "Alertas", "Reportes", "POA"],
                 ["Ejecutar RPA", "Documentos digitales", "OCR"]),
    "coordinador": (["Gestión académica", "Revisión RPA", "Historial RPA", "Alertas", "Reportes", "Eventos y talleres", "Expedientes"],
                    ["Usuarios", "Roles y permisos", "Auditoría", "Transferir autoridad", "Ejecutar RPA", "Documentos digitales", "OCR"]),
    "apoyo": (["Ejecutar RPA", "Documentos digitales", "OCR", "Correspondencia", "Trámites", "Titulados y trabajos"],
              ["Usuarios", "Roles y permisos", "Auditoría", "Transferir autoridad", "Revisión RPA", "Alertas", "Reportes", "POA"]),
}


@hu("HU-01", "Director, Coordinador, Apoyo", "/login, /usuarios, /cuenta",
    "Login con los 3 roles y lectura del menú; 5 contraseñas erróneas con coordinador y un 6.º intento con la correcta; "
    "desbloqueo como director en /usuarios; cambio de contraseña de apoyo en /cuenta, ingreso con la nueva y restauración de la original.",
    "Cada rol ve solo las opciones de sus permisos; la cuenta se bloquea al 5.º intento; el director la desbloquea; la contraseña cambia y se restaura.")
def hu01(n):
    obs, ok = [], True
    for u in CRED:
        s = Sesion(u)
        m = menu(s.page)
        deben, no_deben = MENU_ESPERADO[u]
        faltan = [x for x in deben if x not in m]
        sobran = [x for x in no_deben if x in m]
        n[f"menu_{u}"] = m
        cap(s.page, "HU-01", f"_menu_{u}")
        obs.append(f"{u}: {len(m)} opciones ({', '.join(m)})" + (f" · FALTAN {faltan}" if faltan else "") + (f" · SOBRAN {sobran}" if sobran else ""))
        ok &= not faltan and not sobran
        s.cerrar()
    # Bloqueo
    s = Sesion()
    p = s.page
    msgs = []
    for i in range(5):
        p.goto(f"{URL}/login")
        p.fill("#usuario", "coordinador")
        p.fill("#contrasena", "Equivocada2026x")
        p.click("button[type=submit]")
        msgs.append(esperar_aviso(p))
    p.goto(f"{URL}/login")
    p.fill("#usuario", "coordinador")
    p.fill("#contrasena", CRED["coordinador"])
    p.click("button[type=submit]")
    msgs.append(esperar_aviso(p))
    n["mensajes_login"] = msgs
    cap(p, "HU-01", "_bloqueo")
    bloqueo_ok = "bloqueada" in msgs[4] and "bloqueada" in msgs[5]
    ok &= bloqueo_ok
    obs.append(f"Intentos 1-5: «{msgs[0]}» … «{msgs[4]}»; 6.º con la contraseña correcta: «{msgs[5]}»")
    s.cerrar()
    # Desbloqueo
    d = Sesion("director")
    d.ir("/usuarios")
    d.page.get_by_label("Buscar").fill("coordinador")
    fila = d.page.locator("tbody tr", has_text="coordinador").first
    expect(fila).to_contain_text("BLOQUEADO")
    cap(d.page, "HU-01", "_usuario_bloqueado")
    fila.get_by_role("button", name="Desbloquear").click()
    dlg = d.page.get_by_role("dialog")
    dlg.locator("#motivo").fill("Desbloqueo tras verificación de identidad (prueba HU-01)")
    dlg.get_by_role("button", name="Desbloquear").click()
    expect(d.page.locator("tbody tr", has_text="coordinador").first).to_contain_text("ACTIVO")
    cap(d.page, "HU-01", "_desbloqueo")
    d.cerrar()
    c = Sesion("coordinador")  # ingresa de nuevo
    obs.append("Director desbloqueó la cuenta en /usuarios (estado ACTIVO) y el coordinador volvió a ingresar")
    c.cerrar()
    # Cambio de contraseña (apoyo) y restauración
    nueva = "ApoyoTemporal2026x"
    a = Sesion("apoyo")
    a.ir("/cuenta")
    for k, v in (("#actual", CRED["apoyo"]), ("#nueva", nueva), ("#confirmacion", nueva)):
        a.page.fill(k, v)
    a.page.get_by_role("button", name="Guardar").click()
    expect(a.page.get_by_text("La contraseña se cambió correctamente.")).to_be_visible()
    cap(a.page, "HU-01", "_cambio_contrasena")
    a.salir()
    restaurada = False
    try:
        a.login("apoyo", nueva)
        a.ir("/cuenta")
        for k, v in (("#actual", nueva), ("#nueva", CRED["apoyo"]), ("#confirmacion", CRED["apoyo"])):
            a.page.fill(k, v)
        a.page.get_by_role("button", name="Guardar").click()
        expect(a.page.get_by_text("La contraseña se cambió correctamente.")).to_be_visible()
        a.salir()
        a.login("apoyo", CRED["apoyo"])
        restaurada = True
        obs.append("apoyo cambió su contraseña, ingresó con la nueva y la restauró a la original (ingreso verificado)")
    finally:
        if not restaurada:
            ok = False
            obs.append("ATENCIÓN: no se pudo restaurar la contraseña de apoyo por la interfaz")
    cap(a.page, "HU-01")
    return ("Satisfactorio" if ok else "Falla"), " | ".join(obs)


@hu("HU-02", "Director (y Apoyo)", "/roles",
    "Director quita GESTIONAR_DOCUMENTOS al rol Apoyo Profesional y guarda; una sesión de apoyo abierta antes recarga /documentos; "
    "se restaura el permiso; apoyo intenta abrir /roles.",
    "El cambio rige de inmediato (apoyo pierde Documentos digitales sin volver a iniciar sesión) y se restaura; apoyo no puede usar /roles.")
def hu02(n):
    obs, ok = [], True
    _, roles = api("director", "GET", "/roles")
    rol_apoyo = next(r for r in roles if r["nombre"] == "APOYO_PROFESIONAL")
    originales = sorted(rol_apoyo["permisos"])
    a = Sesion("apoyo")  # sesión abierta ANTES del cambio
    a.ir("/documentos")
    expect(a.page.get_by_role("heading", name="Documentos digitales")).to_be_visible()
    d = Sesion("director")
    try:
        d.ir("/roles")
        d.page.get_by_role("tab", name=re.compile("Apoyo Profesional")).click()
        chk = d.page.locator("label", has_text="GESTIONAR_DOCUMENTOS").locator("input[type=checkbox]")
        expect(chk).to_be_checked()
        chk.uncheck()
        d.page.get_by_role("button", name="Guardar cambios").click()
        expect(d.page.get_by_text("Permisos actualizados y registrados en la auditoría.")).to_be_visible()
        cap(d.page, "HU-02", "_permiso_quitado")
        a.page.reload()
        a.page.wait_for_load_state("networkidle")
        sin_acceso = a.page.get_by_text("Acceso no autorizado").is_visible()
        sin_menu = "Documentos digitales" not in menu(a.page)
        cap(a.page, "HU-02", "_apoyo_sin_permiso")
        obs.append(f"Tras quitar GESTIONAR_DOCUMENTOS, la sesión de apoyo ya abierta, al recargar /documentos, muestra "
                   f"«{'Acceso no autorizado' if sin_acceso else a.page.locator('main').inner_text()[:120]}» y el menú "
                   f"{'ya no tiene' if sin_menu else 'TODAVÍA tiene'} «Documentos digitales»")
        ok &= sin_acceso and sin_menu
        # Restaurar
        chk.check()
        d.page.get_by_role("button", name="Guardar cambios").click()
        expect(d.page.get_by_text("Permisos actualizados y registrados en la auditoría.")).to_be_visible()
        a.page.reload()
        a.page.wait_for_load_state("networkidle")
        expect(a.page.get_by_role("heading", name="Documentos digitales")).to_be_visible()
        obs.append("Permiso restaurado: apoyo vuelve a ver Documentos digitales")
    finally:
        _, roles = api("director", "GET", "/roles")
        actuales = sorted(next(r for r in roles if r["nombre"] == "APOYO_PROFESIONAL")["permisos"])
        if actuales != originales:
            api("director", "PUT", f"/roles/{rol_apoyo['id']}/permisos", {"permisos": originales})
            obs.append("ATENCIÓN: los permisos de Apoyo se restauraron por API porque la interfaz no terminó")
            ok = False
    a.ir("/roles")
    texto = a.page.locator("main").inner_text()
    no_disp = "Acceso no autorizado" in texto and "Roles y permisos" not in menu(a.page)
    cap(a.page, "HU-02")
    obs.append(f"apoyo en /roles: «{' '.join(texto.split())[:110]}»; la opción no figura en su menú")
    ok &= no_disp
    return ("Satisfactorio" if ok else "Falla"), " | ".join(obs)


@hu("HU-04", "Director", "/autoridad",
    "Registrar una transferencia al Coordinador con motivo, resolución de respaldo y fechas futuras (mañana a +7 días); "
    "verificar la acción en /auditoria; revocarla para no afectar al director.",
    "La transferencia aparece en el historial como vigente, queda en la auditoría y puede revocarse.")
def hu04(n):
    obs = []
    d = Sesion("director")
    d.ir("/autoridad")
    p = d.page
    elegir(p, "#dest", "Fernando")
    p.fill("#mot", "Comisión oficial de prueba (verificación HU-04)")
    p.fill("#res", "Resolución Directoral 045/2026 (ficticia)")
    ini, fin = HOY + timedelta(days=1), HOY + timedelta(days=7)
    p.fill("#fi", ini.isoformat())
    p.fill("#ff", fin.isoformat())
    p.get_by_role("button", name="Transferir autoridad").click()
    fila = p.locator("tbody tr", has_text="Fernando Quiroga").first
    expect(fila).to_contain_text("vigente")
    obs.append(f"Transferencia registrada: «{' '.join(fila.inner_text().split())}»")
    cap(p, "HU-04", "_registrada")
    d.ir("/auditoria")
    accion = {}
    con_respuesta(p, "/api/auditoria?", lambda: accion.setdefault("t", elegir(p, "select[aria-label='Acción']", "TRANSFERIR_AUTORIDAD")))
    accion = accion["t"]
    primera = p.locator("tbody tr").first
    expect(primera).to_contain_text(accion)
    obs.append(f"Auditoría (acción {accion}): «{' '.join(primera.inner_text().split())[:160]}»")
    cap(p, "HU-04", "_auditoria")
    d.ir("/autoridad")
    p.locator("tbody tr", has_text="Fernando Quiroga").first.get_by_role("button", name="Revocar").click()
    p.get_by_role("dialog").locator("#rm").fill("Retorno anticipado del Director (prueba HU-04)")
    p.get_by_role("dialog").get_by_role("button", name="Revocar").click()
    estado_t = p.locator("tbody tr", has_text="Fernando Quiroga").first.locator("td").nth(4)
    expect(estado_t).to_have_text("revocada")
    obs.append(f"Revocada: «{' '.join(p.locator('tbody tr').first.inner_text().split())}»")
    obs.append("Nota: el respaldo se registra como número de resolución (campo de texto); la pantalla no permite adjuntar un archivo")
    cap(p, "HU-04")
    return "Satisfactorio", " | ".join(obs)


# ================================================================== CORRESPONDENCIA Y DOCUMENTOS
@hu("HU-05", "Apoyo", "/correspondencia",
    "Registrar oficio RECIBIDO con fecha 15/07/2026, dependencia, asunto y CITE; adjuntar un PDF pequeño; autorizarlo para el RPA.",
    "Se genera el código DCP-REC-AAAA-NNNN, el PDF queda adjunto (con SHA-256) y el oficio queda autorizado.")
def hu05(n):
    a = Sesion("apoyo")
    a.ir("/correspondencia")
    p = a.page
    p.get_by_role("button", name="Registrar oficio").click()
    f = p.get_by_role("dialog", name="Registrar oficio")
    f.locator("#tipo").select_option("RECIBIDA")
    f.locator("#fecha").fill("2026-07-15")
    f.locator("#dest").select_option(index=1)
    f.locator("#asunto").fill("Solicitud de información académica (verificación HU-05)")
    f.locator("#desc").fill("CITE: VER/HU-05/2026")
    f.get_by_role("button", name="Guardar").click()
    det = p.get_by_role("dialog", name=re.compile(r"^DCP-REC-"))
    expect(det).to_be_visible()
    codigo = det.get_attribute("aria-label")
    DATOS["oficio"] = codigo
    subir(det, ARCH / "hu05-oficio.pdf")
    expect(det.get_by_text("hu05-oficio.pdf")).to_be_visible()
    det.get_by_role("button", name="Autorizar").click()
    expect(det.get_by_text("Autorizado para RPA")).to_be_visible()
    adj = det.locator("li", has_text="hu05-oficio.pdf").inner_text()
    cap(p, "HU-05")
    ok = re.fullmatch(r"DCP-REC-\d{4}-\d+", codigo or "") is not None
    return ("Satisfactorio" if ok else "Falla"), f"Código generado {codigo}; adjunto: «{' '.join(adj.split())}»; insignia «Autorizado para RPA»"


@hu("HU-06", "Apoyo", "/correspondencia (detalle)",
    "Abrir el oficio de HU-05, registrar seguimiento al estado DERIVADA con observación y buscar la opción para vincular una respuesta o reiteración.",
    "El estado cambia y queda en el seguimiento; el oficio puede vincularse con su respuesta o reiteración.")
def hu06(n):
    a = Sesion("apoyo")
    a.ir("/correspondencia")
    p = a.page
    p.get_by_label("Buscar").fill(DATOS["oficio"])
    p.locator("tbody tr", has_text=DATOS["oficio"]).first.click()
    det = p.get_by_role("dialog", name=DATOS["oficio"])
    det.get_by_label("Nuevo estado").select_option("DERIVADA")
    det.get_by_label("Observación").fill("Derivado a Coordinación para su respuesta (verificación HU-06)")
    det.get_by_role("button", name="Registrar").click()
    expect(det.locator("ol li", has_text="verificación HU-06")).to_be_visible()
    item = det.locator("ol li", has_text="verificación HU-06").inner_text()
    vincular = det.locator("button, select, a", has_text=re.compile("respuesta|reiteraci|vincular", re.I)).count()
    cap(p, "HU-06")
    obtenido = f"Seguimiento registrado: «{' '.join(item.split())}» (estado Derivada). "
    if vincular == 0:
        return "Falla", obtenido + ("Vincular respuesta/reiteración: la pantalla del oficio no ofrece ninguna opción para vincular una respuesta o "
                                    "una reiteración (solo adjuntos, seguimiento de estado, autorizar y editar); tampoco existe en la API.")
    return "Satisfactorio", obtenido + f"Se encontraron {vincular} control(es) de vinculación"


@hu("HU-07", "Apoyo", "/documentos",
    "Subir hu07-valido.pdf, descargarlo y eliminarlo; intentar subir un PDF vacío (0 bytes) y un .txt renombrado a .pdf.",
    "El PDF válido se sube (con SHA-256), se descarga con contenido y se elimina; los dos archivos inválidos se rechazan con un mensaje.")
def hu07(n):
    a = Sesion("apoyo")
    a.ir("/documentos")
    p = a.page
    p.fill("#desc", "Resolución de prueba (verificación HU-07)")
    subir(p, ARCH / "hu07-valido.pdf")
    fila = p.locator("tbody tr", has_text="hu07-valido.pdf").first
    expect(fila).to_be_visible()
    info = " ".join(fila.inner_text().split())
    with p.expect_download() as dl:
        fila.get_by_role("button", name="Descargar").click()
    destino = DESC / "hu07-descargado.pdf"
    dl.value.save_as(destino)
    tam = destino.stat().st_size
    cap(p, "HU-07", "_subido")
    fila.get_by_role("button", name="Eliminar").click()
    p.get_by_role("dialog").get_by_role("button", name="Eliminar").click()
    expect(p.locator("tbody tr", has_text="hu07-valido.pdf")).to_have_count(0)
    subir(p, ARCH / "hu07-vacio.pdf")
    m_vacio = esperar_aviso(p)
    cap(p, "HU-07", "_vacio")
    subir(p, ARCH / "hu07-texto-renombrado.pdf")
    p.wait_for_timeout(1500)
    m_txt = esperar_aviso(p)
    cap(p, "HU-07")
    rechazos = p.locator("tbody tr", has_text=re.compile("hu07-vacio|hu07-texto")).count()
    ok = tam > 0 and rechazos == 0
    return ("Satisfactorio" if ok else "Falla"), (
        f"Subido: «{info}»; descargado {tam} bytes ({'%PDF' if destino.read_bytes()[:4] == b'%PDF' else 'sin cabecera PDF'}); eliminado (la fila desaparece). "
        f"PDF vacío → «{m_vacio}». TXT renombrado → «{m_txt}». Archivos inválidos en la lista: {rechazos}")


@hu("HU-09", "Apoyo", "/tramites/nuevo",
    "Buscar al estudiante 21901222, elegir «Récord académico», prioridad media, marcar los requisitos y registrar.",
    "El trámite queda RECEPCIONADO con número TRM-AAAA-NNNN y fecha límite.")
def hu09(n):
    a = Sesion("apoyo")
    a.ir("/tramites/nuevo")
    p = a.page
    buscar_estudiante(p, "21901222")
    elegir(p, "#tipo", "Récord académico")
    p.fill("#obs", "Solicitud registrada en la verificación en el navegador (HU-09)")
    for c in p.locator("form label input[type=checkbox]").all():
        c.check()
    p.get_by_role("button", name="Registrar trámite").click()
    p.wait_for_url(re.compile(r"/tramites/\d+$"))
    expect(p.get_by_text(re.compile("registrado correctamente"))).to_be_visible()
    numero = p.locator("h1").first.inner_text().strip()
    estado = p.locator("h1 + span").first.inner_text().strip()
    DATOS["tram09"] = {"id": int(p.url.rsplit("/", 1)[1]), "numero": numero}
    cap(p, "HU-09")
    ok = re.fullmatch(r"TRM-\d{4}-\d{4}", numero) is not None and estado == "Recepcionado"
    return ("Satisfactorio" if ok else "Falla"), f"Mensaje «Trámite {numero} registrado correctamente.»; número {numero}; estado {estado}"


@hu("HU-08", "Coordinador", "/expedientes",
    "Abrir un expediente para el estudiante 21901222 (o abrir el existente), vincular su trámite de HU-09 y adjuntar un PDF.",
    "El expediente agrupa el trámite vinculado y el documento adjunto.")
def hu08(n):
    c = Sesion("coordinador")
    c.ir("/expedientes")
    p = c.page
    p.get_by_role("button", name="Abrir expediente").click()
    nuevo = p.get_by_role("dialog", name="Abrir expediente")
    buscar_estudiante(nuevo, "21901222")
    nuevo.locator("#des").fill("Expediente abierto en la verificación HU-08")
    nuevo.get_by_role("button", name="Abrir").click()
    p.wait_for_timeout(1500)
    nota = "nuevo expediente abierto"
    if nuevo.is_visible():  # el backend rechazó abrir otro: se usa el existente
        nota = f"no se abrió uno nuevo («{' '.join(avisos(p, nuevo))}»); se usa el existente"
        nuevo.get_by_role("button", name="Cancelar").click()
        p.get_by_label("Buscar").fill("21901222")
        p.locator("tbody tr", has_text="21901222").first.click()
    det = p.get_by_role("dialog", name=re.compile(r"^EXP-"))
    expect(det).to_be_visible()
    titulo = det.get_attribute("aria-label")
    sel = det.get_by_label("Trámite a vincular")
    num = DATOS.get("tram09", {}).get("numero", "")
    if sel.count() and sel.locator("option", has_text=num).count():
        sel.select_option(value=sel.locator("option", has_text=num).first.get_attribute("value"))
        det.get_by_role("button", name="Vincular").click()
        expect(det.locator("section", has_text="Trámites").get_by_role("link", name=num)).to_be_visible()
    subir(det, ARCH / "hu08-expediente.pdf")
    expect(det.get_by_text("hu08-expediente.pdf")).to_be_visible()
    tramites = det.locator("section").nth(0).locator("li").count()
    docs = det.locator("section").nth(1).locator("li").count()
    cap(p, "HU-08")
    ok = tramites > 0 and docs > 0
    return ("Satisfactorio" if ok else "Falla"), f"{titulo} ({nota}); trámites agrupados: {tramites} (incluye {num}); documentos: {docs} (incluye hu08-expediente.pdf)"


# ================================================================== TRÁMITES
@hu("HU-10", "Apoyo y Coordinador", "/tramites/:id",
    "Apoyo registra un «Cambio de grupo» para 21901444 sin marcar requisitos; abre el detalle; el coordinador intenta «Derivar a revisión» y confirma.",
    "Con requisitos obligatorios pendientes la derivación no está disponible para apoyo y se rechaza para el coordinador; el trámite sigue RECEPCIONADO.")
def hu10(n):
    a = Sesion("apoyo")
    a.ir("/tramites/nuevo")
    p = a.page
    buscar_estudiante(p, "21901444")
    elegir(p, "#tipo", "Cambio de grupo")
    aviso_reg = p.get_by_role("status").filter(has_text="Faltan").inner_text()
    p.get_by_role("button", name="Registrar trámite").click()
    p.wait_for_url(re.compile(r"/tramites/\d+$"))
    idt = p.url.rsplit("/", 1)[1]
    numero = p.locator("h1").first.inner_text().strip()
    pend = p.get_by_text(re.compile(r"obligatorio\(s\) pendiente\(s\)")).inner_text()
    derivar_apoyo = p.get_by_role("button", name="Derivar a revisión").count()
    cap(p, "HU-10", "_apoyo")
    c = Sesion("coordinador")
    c.ir(f"/tramites/{idt}")
    q = c.page
    q.get_by_role("button", name="Derivar a revisión").click()
    dlg = q.get_by_role("dialog")
    adv = dlg.get_by_role("alert").first.inner_text()
    dlg.get_by_role("button", name="Confirmar").click()
    q.wait_for_timeout(1500)
    err = [x for x in avisos(q, dlg) if x != adv]
    cap(q, "HU-10")
    dlg.get_by_role("button", name="Cancelar").click()
    q.reload()
    estado = q.locator("h1 + span").first.inner_text().strip()
    ok = derivar_apoyo == 0 and bool(err) and estado == "Recepcionado"
    return ("Satisfactorio" if ok else "Falla"), (
        f"{numero}: al registrar «{aviso_reg}»; detalle «{pend}»; apoyo no ve «Derivar a revisión» ({derivar_apoyo} botones). "
        f"Coordinador: aviso «{adv}»; al confirmar → «{err[0] if err else 'sin error'}»; estado final {estado}")


@hu("HU-11", "Coordinador y Director", "/tramites/:id",
    "Coordinador abre TRM-2026-0043 (en revisión), intenta Observar sin justificación y revisa los botones; "
    "por consola intenta observar sin justificación y aprobar; Director aprueba TRM-2026-0047 y rechaza TRM-2026-0044 con justificación.",
    "Observar sin justificación se rechaza; el coordinador no tiene Aprobar/Rechazar; el director aprueba uno y rechaza otro (con justificación).")
def hu11(n):
    obs, ok = [], True
    c = Sesion("coordinador")
    c.ir("/tramites/43")
    p = c.page
    botones = [b.strip() for b in p.locator("main .tarjeta").first.locator("button").all_inner_texts()]
    sin_ap = "Aprobar" not in botones and "Rechazar" not in botones
    p.get_by_role("button", name="Observar").click()
    dlg = p.get_by_role("dialog")
    vacio_deshab = dlg.get_by_role("button", name="Confirmar").is_disabled()
    dlg.locator("#just").fill("corto")
    corto_deshab = dlg.get_by_role("button", name="Confirmar").is_disabled()
    cap(p, "HU-11", "_observar_sin_justificacion")
    dlg.get_by_role("button", name="Cancelar").click()
    r_obs = fetch_nav(p, "POST", "/tramites/43/eventos/observar", {"justificacion": ""})
    r_apr = fetch_nav(p, "POST", "/tramites/43/eventos/aprobar", {"justificacion": ""})
    obs.append(f"Coordinador en TRM-2026-0043 ve los botones {botones}; Observar con justificación vacía o de 5 caracteres: botón Confirmar deshabilitado "
               f"({vacio_deshab}/{corto_deshab}); por consola: observar sin justificación → HTTP {r_obs['estado']} «{r_obs['mensaje']}», "
               f"aprobar → HTTP {r_apr['estado']} «{r_apr['mensaje']}»")
    ok &= sin_ap and vacio_deshab and corto_deshab and r_obs["estado"] >= 400 and r_apr["estado"] == 403
    d = Sesion("director")
    d.ir("/tramites/47")
    q = d.page
    q.get_by_role("button", name="Aprobar").click()
    q.get_by_role("dialog").locator("#just").fill("Aprobado en la verificación HU-11")
    q.get_by_role("dialog").get_by_role("button", name="Confirmar").click()
    expect(q.locator("h1 + span").first).to_have_text("Aprobado")
    cap(q, "HU-11", "_aprobado")
    d.ir("/tramites/44")
    q.get_by_role("button", name="Rechazar").click()
    dl2 = q.get_by_role("dialog")
    deshab = dl2.get_by_role("button", name="Confirmar").is_disabled()
    dl2.locator("#just").fill("No adjunta el justificativo exigido para el cambio (verificación HU-11)")
    dl2.get_by_role("button", name="Confirmar").click()
    expect(q.locator("h1 + span").first).to_have_text("Rechazado")
    hist = " ".join(q.locator("section", has=q.get_by_role("heading", name="Historial de estados")).locator("ol > li").first.inner_text().split())
    cap(q, "HU-11")
    obs.append(f"Director: TRM-2026-0047 → Aprobado; TRM-2026-0044 → Rechazar sin texto deshabilitado ({deshab}); con justificación → Rechazado; historial «{hist}»")
    return ("Satisfactorio" if ok and deshab else "Falla"), " | ".join(obs)


@hu("HU-12", "Coordinador", "/tramites, /tramites/:id",
    "Filtrar la lista por estado Observado, luego también por tipo Licencia temporal y por «Solo vencidos»; abrir un trámite y leer el historial.",
    "Los filtros devuelven solo filas que cumplen; el historial muestra estado, fecha, hora, usuario y comentario.")
def hu12(n):
    c = Sesion("coordinador")
    c.ir("/tramites")
    p = c.page
    total0 = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).inner_text()
    con_respuesta(p, "/api/tramites?", lambda: p.get_by_label("Estado").select_option("OBSERVADO"))
    estados = set(t.strip() for t in p.locator("tbody tr td:nth-child(7)").all_inner_texts())
    t1 = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).inner_text()
    con_respuesta(p, "/api/tramites?", lambda: elegir(p, "select[aria-label='Tipo']", "Licencia temporal"))
    tipos = set(t.strip() for t in p.locator("tbody tr td:nth-child(2)").all_inner_texts())
    t2 = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).inner_text()
    con_respuesta(p, "/api/tramites?", lambda: p.get_by_label("Solo vencidos").check())
    t3 = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).inner_text()
    cap(p, "HU-12", "_filtros")
    p.locator("tbody tr").first.click()
    p.wait_for_url(re.compile(r"/tramites/\d+$"))
    expect(p.get_by_role("heading", name="Historial de estados")).to_be_visible()
    hist = p.locator("section", has=p.get_by_role("heading", name="Historial de estados")).locator("ol > li")
    expect(hist.first).to_be_visible()
    items = [" ".join(t.split()) for t in hist.all_inner_texts()]
    cap(p, "HU-12")
    patron = re.compile(r"\d{1,2}/\d{1,2}/\d{2},? \d{1,2}:\d{2}:\d{2}.* · .+ · \w+")
    ok = estados == {"Observado"} and tipos <= {"Licencia temporal"} and items and all(patron.search(i) for i in items)
    return ("Satisfactorio" if ok else "Falla"), (
        f"Sin filtros {total0}; Estado=Observado → {t1} (estados en la tabla: {sorted(estados)}); + Tipo=Licencia temporal → {t2} (tipos: {sorted(tipos)}); "
        f"+ Solo vencidos → {t3}. Historial ({len(items)} entradas): «{items[0] if items else ''}»")


# ================================================================== ACADÉMICO E INSTITUCIONAL
def dato(p, titulo):
    return p.locator("p", has_text=re.compile(f"^{re.escape(titulo)}$")).first.locator("xpath=following-sibling::p[1]").inner_text().strip()


@hu("HU-13", "Coordinador", "/academico (Planificación)",
    "Abrir Planificación del periodo 2026-2 y leer el resumen; planificar un periodo nuevo (2027-1) con «Nuevo».",
    "Se ve la planificación del periodo 2026-2 (materias, grupos, cupos, inscritos, grupos sin docente) y se puede planificar un periodo nuevo.")
def hu13(n):
    c = Sesion("coordinador")
    c.ir("/academico")
    p = c.page
    p.locator("li button", has_text="2026-2").click()
    expect(p.get_by_role("heading", name="Periodo 2026-2")).to_be_visible()
    res = {t: dato(p, t) for t in ["Materias ofertadas", "Grupos", "Cupo total", "Inscritos", "Grupos sin docente", "Grupos llenos"]}
    cap(p, "HU-13", "_2026-2")
    p.get_by_role("button", name="Nuevo").click()
    dlg = p.get_by_role("dialog", name="Planificar nuevo periodo")
    ges, sem = dlg.locator("#ges").input_value(), dlg.locator("#sem").input_value()
    dlg.get_by_role("button", name="Crear").click()
    p.wait_for_timeout(1500)
    nota = ""
    if dlg.is_visible():
        nota = f" (no se creó: «{' '.join(avisos(p, dlg))}»)"
        dlg.get_by_role("button", name="Cancelar").click()
    fila = p.locator("li button", has_text=f"{ges}-{sem}")
    creado = fila.count() > 0
    cap(p, "HU-13")
    return ("Satisfactorio" if creado else "Falla"), (
        f"Periodo 2026-2: {res}. Nuevo periodo {ges}-{sem}: {'creado, figura como ' + fila.first.inner_text().split()[-1] if creado else 'no figura'}{nota}")


@hu("HU-14", "Coordinador", "/academico (Grupos y cupos)",
    "Intentar bajar el cupo de PSI-101 Z2 por debajo de sus inscritos; crear un grupo de PSI-101 con cupo 1; inscribir a 22001481 (caso especial) "
    "e intentar inscribir a 22001703 en el grupo lleno.",
    "El sistema impide un cupo menor a los inscritos y la inscripción que supera el cupo.")
def hu14(n):
    c = Sesion("coordinador")
    c.ir("/academico")
    p = c.page
    p.get_by_role("tab", name="Grupos y cupos").click()
    p.get_by_label("Editar PSI-101 Z2").click()
    dlg = p.get_by_role("dialog")
    dlg.locator("#cup").fill("1")
    dlg.get_by_role("button", name="Guardar").click()
    m_cupo = esperar_aviso(p, dlg)
    cap(p, "HU-14", "_cupo_menor")
    dlg.get_by_role("button", name="Cancelar").click()
    codigo = f"V{int(time.time()) % 90 + 10}"
    p.get_by_role("button", name="Nuevo grupo").click()
    dlg = p.get_by_role("dialog", name="Nuevo grupo")
    op = dlg.locator("#mat option", has_text=re.compile(r"^PSI-101 .*\(148-1\)$")).first
    dlg.locator("#mat").select_option(value=op.get_attribute("value"))
    dlg.locator("#cod").fill(codigo)
    dlg.locator("#cup").fill("1")
    dlg.get_by_role("button", name="Guardar").click()
    expect(dlg).to_be_hidden()
    DATOS["grupo14"] = codigo
    fila = p.locator("tbody tr", has=p.locator("td", has_text=re.compile(f"^{codigo}$")))
    expect(fila).to_contain_text("0/1")
    fila.get_by_role("button", name=re.compile("^Inscritos de")).click()
    ins = p.get_by_role("dialog", name=re.compile("^Inscritos"))
    resultados = []
    for reg in ("22001481", "22001703"):
        buscar_estudiante(ins, reg)
        ins.get_by_label(re.compile("Caso especial")).check()
        ins.get_by_placeholder("Motivo o número de trámite").fill("Adición fuera de fecha respaldada por trámite (HU-14)")
        ins.get_by_role("button", name="Inscribir").click()
        p.wait_for_timeout(1500)
        al = avisos(p, ins)
        resultados.append((reg, al[-1] if al else "inscrito"))
        if al:
            break
    cap(p, "HU-14")
    inscritos = ins.locator("tbody tr", has_text="INSCRITO").count()
    ok = "no puede ser menor" in m_cupo and resultados and resultados[-1][1] != "inscrito" and "cupo" in resultados[-1][1] and inscritos == 1
    return ("Satisfactorio" if ok else "Falla"), (
        f"Cupo 1 en PSI-101 Z2 → «{m_cupo}». Grupo nuevo PSI-101 {codigo} cupo 1 creado; inscripciones: "
        + "; ".join(f"{r} → «{m}»" for r, m in resultados) + f"; inscritos en el grupo: {inscritos}/1")


@hu("HU-15", "Coordinador", "/academico (Migraciones)",
    "Registrar la migración del estudiante 22002147 (plan 148-1) al plan 148-2 con convalidaciones.",
    "La migración queda registrada (148-1 → 148-2) y el plan del estudiante se actualiza.")
def hu15(n):
    c = Sesion("coordinador")
    c.ir("/academico")
    p = c.page
    p.get_by_role("tab", name="Migraciones").click()
    buscar_estudiante(p, "22002147")
    resumen = p.get_by_text(re.compile("Plan actual")).inner_text()
    p.fill("#om", "Convalida PSI-101 y PSI-102 del plan 148-1 (verificación HU-15)")
    p.get_by_role("button", name="Registrar migración").click()
    fila = p.locator("tbody tr", has_text="22002147").first
    expect(fila).to_contain_text("148-1 → 148-2")
    texto = " ".join(fila.inner_text().split())
    buscar_estudiante(p, "22002147")
    plan_cache = p.get_by_text(re.compile("Registro 22002147")).inner_text()
    c.ir("/academico")
    p.get_by_role("tab", name="Migraciones").click()
    buscar_estudiante(p, "22002147")
    plan = p.get_by_text(re.compile("Registro 22002147")).inner_text()
    cap(p, "HU-15")
    nota = "" if plan_cache == plan else f" (en la misma sesión, sin recargar, el buscador seguía mostrando «{plan_cache}» por la caché de la pantalla)"
    return ("Satisfactorio" if "Plan 148-2" in plan else "Falla"), f"Antes: «{resumen}». Fila registrada: «{texto}». Estudiante tras recargar: «{plan}»{nota}"


@hu("HU-16", "Coordinador", "/academico (Carga docente, Planificación)",
    "Leer «Grupos sin docente» del periodo; asignar un docente INTERINO (40 h) al grupo creado en HU-14 (sin docente); volver a leer el indicador.",
    "La asignación queda registrada con su tipo y se actualizan los grupos sin cobertura.")
def hu16(n):
    c = Sesion("coordinador")
    c.ir("/academico")
    p = c.page
    antes = dato(p, "Grupos sin docente")
    cap(p, "HU-16", "_antes")
    p.get_by_role("tab", name="Carga docente").click()
    p.get_by_role("button", name="Asignar").click()
    dlg = p.get_by_role("dialog", name="Asignar carga docente")
    mejor, libre = None, -1
    for op in dlg.locator("#doc option").all()[1:]:
        m = re.search(r"\(([\d.]+)/([\d.]+) h\)", op.inner_text())
        if m and float(m.group(2)) - float(m.group(1)) > libre:
            mejor, libre = op, float(m.group(2)) - float(m.group(1))
    docente = mejor.inner_text().split(" (")[0]
    dlg.locator("#doc").select_option(value=mejor.get_attribute("value"))
    grupo = elegir(dlg, "#gru", f" {DATOS['grupo14']} ")
    dlg.locator("#tip").select_option("INTERINO")
    dlg.locator("#hrs").fill("40")
    dlg.locator("#obs").fill("Interino mientras se designa al titular (verificación HU-16)")
    dlg.get_by_role("button", name="Asignar").click()
    expect(dlg).to_be_hidden()
    fila = p.locator("tbody tr", has_text=docente).filter(has_text="INTERINO").first
    texto = " ".join(fila.inner_text().split())
    c.ir("/academico")
    expect(p.get_by_text("Grupos sin docente", exact=True)).to_be_visible()
    despues = dato(p, "Grupos sin docente")
    cap(p, "HU-16")
    tipos = "TITULAR, INTERINO, COBERTURA"
    ok = int(despues) == int(antes) - 1
    return ("Satisfactorio" if ok else "Falla"), (
        f"Grupos sin docente antes: {antes}; asignado «{grupo}» a {docente} → fila «{texto}»; grupos sin docente después: {despues}. "
        f"Tipos que ofrece la pantalla: {tipos} (la suplencia se registra como COBERTURA)")


@hu("HU-18", "Apoyo", "/institucional (Trabajos de grado)",
    "Registrar un trabajo de grado de 22302480 con título, tutor, gestión y palabras clave; adjuntar el PDF del trabajo.",
    "El trabajo queda en el repositorio con su tutor y su archivo.")
def hu18(n):
    a = Sesion("apoyo")
    a.ir("/institucional")
    p = a.page
    p.get_by_role("button", name="Registrar trabajo").click()
    dlg = p.get_by_role("dialog")
    buscar_estudiante(dlg, "22302480")
    titulo = "Bienestar psicológico en egresados de Psicología (verificación HU-18)"
    DATOS["trabajo18"] = titulo
    dlg.locator("#tit").fill(titulo)
    dlg.locator("#tut").fill("Lic. Carla Méndez Rivero")
    dlg.locator("#gest").fill("2026")
    dlg.locator("#pcl").fill("bienestar, egresados")
    dlg.get_by_role("button", name="Guardar").click()
    expect(dlg.get_by_role("button", name="Adjuntar documento")).to_be_visible()
    subir(dlg, ARCH / "hu18-trabajo.pdf")
    expect(dlg.get_by_text("hu18-trabajo.pdf")).to_be_visible()
    dlg.get_by_role("button", name="Cerrar").click()
    item = p.locator("li", has_text=titulo).first
    expect(item).to_contain_text("hu18-trabajo.pdf")
    texto = " ".join(item.inner_text().split())
    cap(p, "HU-18")
    return "Satisfactorio", f"Registrado en el repositorio: «{texto[:260]}»"


@hu("HU-17", "Apoyo", "/institucional (Titulados)",
    "Registrar la titulación de 22302480 (egresado) en modalidad Tesis, vinculando el trabajo de HU-18, con fecha de defensa, gestión 2026 y diploma.",
    "El titulado aparece con su gestión y modalidad, y el estudiante pasa a TITULADO.")
def hu17(n):
    a = Sesion("apoyo")
    a.ir("/institucional")
    p = a.page
    p.get_by_role("tab", name="Titulados").click()
    p.get_by_role("button", name="Registrar titulación").click()
    dlg = p.get_by_role("dialog", name="Registrar titulación")
    buscar_estudiante(dlg, "22302480")
    dlg.locator("#mod").select_option("TESIS")
    dlg.locator("#tra option", has_text="HU-18").first.wait_for(state="attached")
    elegir(dlg, "#tra", "HU-18")
    dlg.locator("#fd").fill("2026-11-10")
    dlg.locator("#gt").fill("2026")
    dlg.locator("#dip").fill("DIP-2026-0999 (ficticio)")
    dlg.get_by_role("button", name="Registrar").click()
    expect(dlg).to_be_hidden()
    fila = p.locator("tbody tr", has_text="22302480").first
    texto = " ".join(fila.inner_text().split())
    a.ir("/institucional")
    p.get_by_role("tab", name="Titulados").click()
    p.get_by_role("button", name="Registrar titulación").click()
    dlg = p.get_by_role("dialog", name="Registrar titulación")
    buscar_estudiante(dlg, "22302480")
    estado = dlg.get_by_text(re.compile("Registro 22302480")).inner_text()
    cap(p, "HU-17")
    dlg.get_by_role("button", name="Cancelar").click()
    ok = "Tesis" in texto and "2026" in texto and "TITULADO" in estado
    return ("Satisfactorio" if ok else "Falla"), f"Fila: «{texto}». Estudiante ahora: «{estado}»"


@hu("HU-19", "Director", "/poa",
    "Abrir el seguimiento de una actividad no concluida y registrar un avance +10 % con observación y evidencia; comparar el avance global con el promedio.",
    "El avance de la actividad se actualiza y el avance global del POA es el promedio de sus actividades.")
def hu19(n):
    d = Sesion("director")
    d.ir("/poa")
    p = d.page
    g0 = dato(p, "Avance global")
    filas = p.locator("tbody tr")
    objetivo, actual = None, 0
    for i in range(filas.count()):
        f = filas.nth(i)
        av = int(f.locator("td").nth(4).inner_text().replace("%", "").strip())
        if av < 100 and "concluida" not in f.locator("td").nth(5).inner_text():
            objetivo, actual = f, av
            break
    desc = objetivo.locator("td").first.inner_text().split("\n")[0]
    nuevo = min(100, actual + 10)
    objetivo.get_by_role("button", name="Seguimiento").click()
    dlg = p.get_by_role("dialog")
    dlg.locator("#sa").fill(str(nuevo))
    dlg.locator("#so").fill("Avance registrado en la verificación HU-19")
    dlg.locator("#sv").fill("Acta de reunión de seguimiento (ficticia)")
    dlg.get_by_role("button", name="Registrar avance").click()
    expect(dlg.get_by_text("Avance registrado en la verificación HU-19").first).to_be_visible()
    dlg.get_by_role("button", name="Cerrar").click()
    p.wait_for_load_state("networkidle")
    g1 = dato(p, "Avance global")
    vis = [int(t.replace("%", "").strip()) for t in p.locator("tbody tr td:nth-child(5)").all_inner_texts()]
    _, poa = api("director", "GET", "/poa/2026")
    exactos = [float(x["avance"]) for x in poa["actividades"]]
    prom = sum(exactos) / len(exactos)
    cap(p, "HU-19")
    ok = abs(float(g1.replace("%", "").strip()) - round(prom, 1)) < 0.06
    return ("Satisfactorio" if ok else "Falla"), (
        f"Actividad «{desc}»: {actual} % → {nuevo} %. Avance global {g0} → {g1}. Avances en pantalla {vis} (promedio {sum(vis)/len(vis):.1f}); "
        f"promedio exacto de las actividades (API) {prom:.2f} %")


@hu("HU-20", "Coordinador", "/eventos",
    "Crear un taller con fecha futura, responsable y vinculado a la primera actividad del POA 2026.",
    "El evento aparece en «Próximos» con la actividad del POA vinculada.")
def hu20(n):
    c = Sesion("coordinador")
    c.ir("/eventos")
    p = c.page
    p.get_by_role("button", name="Nuevo evento").click()
    dlg = p.get_by_role("dialog", name="Nuevo evento")
    nombre = "Taller de manejo del estrés académico (verificación HU-20)"
    dlg.locator("#en").fill(nombre)
    dlg.locator("#et").select_option("TALLER")
    dlg.locator("#ef").fill((HOY + timedelta(days=10)).isoformat())
    dlg.locator("#er").fill("Lic. Ana Suárez (ficticia)")
    dlg.locator("#ea option").nth(1).wait_for(state="attached")
    dlg.locator("#ea").select_option(index=1)
    act = dlg.locator("#ea option:checked").inner_text()
    dlg.get_by_role("button", name="Guardar").click()
    expect(dlg).to_be_hidden()
    fila = p.locator("section", has_text="Próximos").locator("tbody tr", has_text=nombre).first
    texto = " ".join(fila.inner_text().split())
    cap(p, "HU-20")
    return ("Satisfactorio" if act[:30] in texto else "Falla"), f"Actividad elegida «{act}»; fila en Próximos: «{texto}»"


# ================================================================== OCR, BÚSQUEDA Y ASISTENTE
@hu("HU-21", "Apoyo", "/documentos, /ocr",
    "Subir hu21-acta-escaneada.png (imagen generada con texto) en Documentos digitales; en /ocr pulsar «Procesar».",
    "Se muestra el texto extraído y el nivel de confianza del motor OCR.")
def hu21(n):
    a = Sesion("apoyo")
    a.ir("/documentos")
    p = a.page
    p.fill("#desc", "Acta escaneada de prueba (HU-21)")
    subir(p, ARCH / "hu21-acta-escaneada.png")
    expect(p.locator("tbody tr", has_text="hu21-acta-escaneada.png").first).to_be_visible()
    a.ir("/ocr")
    con_respuesta(p, "/api/ocr/documentos?", lambda: p.get_by_label("Buscar").fill("hu21"))
    fila = p.locator("tbody tr", has_text="hu21-acta-escaneada.png").first
    t0 = time.perf_counter()
    fila.get_by_role("button", name="Procesar").click()
    dlg = p.get_by_role("dialog", name=re.compile("OCR · hu21"))
    conf_loc = dlg.get_by_text(re.compile("Confianza del motor"))
    err_loc = p.locator("main [role=alert]")
    expect(conf_loc.or_(err_loc).first).to_be_visible(timeout=180_000)
    seg = time.perf_counter() - t0
    if not conf_loc.is_visible():
        msg = err_loc.first.inner_text().strip()
        estado = " ".join(p.locator("tbody tr", has_text="hu21-acta-escaneada.png").first.inner_text().split())
        cap(p, "HU-21")
        DATOS["ocr_ok"] = False
        return "Falla", (f"Al pulsar «Procesar» la pantalla muestra «{msg}» (a los {seg:.1f} s); el documento sigue «{estado}». "
                         "Consola del navegador: ver columna de errores (respuesta HTTP del backend).")
    DATOS["ocr_ok"] = True
    conf = conf_loc.inner_text()
    estado = dlg.get_by_text(re.compile("Pendiente de validación|Validado")).first.inner_text()
    texto = dlg.get_by_label("Texto extraído").input_value()
    n["texto_ocr"] = texto
    cap(p, "HU-21")
    ok = bool(texto.strip()) and "QUETZAL" in texto.upper()
    return ("Satisfactorio" if ok else "Falla"), f"Procesado en {seg:.1f} s; «{conf}»; estado «{estado}»; texto extraído: «{' '.join(texto.split())[:200]}»"


def buscar(p, texto, modo="Avanzada"):
    p.get_by_role("tab", name=modo).click()
    p.get_by_label("Texto de búsqueda").fill(texto)
    p.locator("form button", has_text="Buscar").click()
    cab = p.get_by_text(re.compile(r"resultado\(s\) · fuentes consultadas"))
    expect(cab).to_be_visible(timeout=60_000)
    return " ".join(cab.inner_text().split())


@hu("HU-22", "Apoyo", "/ocr, /busqueda",
    "Depende de HU-21: corregir el texto extraído y pulsar «Validar texto»; buscar el texto antes y después de validar.",
    "Antes de validar el texto no aparece en la búsqueda; después de validarlo, sí (con el texto corregido).")
def hu22(n):
    a = Sesion("apoyo")
    p = a.page
    a.ir("/busqueda")
    acceso = " ".join(p.locator("main").inner_text().split())[:90]
    en_menu = "Búsqueda y asistente" in menu(p)
    cap(p, "HU-22", "_busqueda_apoyo")
    a.ir("/ocr")
    con_respuesta(p, "/api/ocr/documentos?", lambda: p.get_by_label("Buscar").fill("hu21"))
    fila = p.locator("tbody tr", has_text="hu21-acta-escaneada.png").first
    estado = " ".join(fila.inner_text().split()) if fila.count() else "documento no encontrado"
    cap(p, "HU-22")
    nota_busq = (f"Además, apoyo (el único rol que puede ver documentos en la búsqueda, por GESTIONAR_DOCUMENTOS) abre /busqueda y ve «{acceso}»; "
                 f"«Búsqueda y asistente» en su menú: {en_menu}.")
    if fila.get_by_role("button", name="Revisar y validar").count() == 0:
        return "No ejecutado", (f"No hay texto extraído que validar porque el OCR de HU-21 falló; fila en /ocr: «{estado}». {nota_busq}")
    return "No ejecutado", f"Fila en /ocr: «{estado}». {nota_busq} (la validación se debe completar manualmente)"


@hu("HU-23", "Coordinador (y Apoyo)", "/busqueda, /consultas",
    "Coordinador: búsqueda avanzada «ansiedad» con tipos Trabajo de grado + Trámite y fechas 01/01/2025-hoy; luego «escaneada» sin filtros. "
    "Apoyo: abre /busqueda y /consultas, y por consola consulta /api/busqueda?texto=escaneada para comparar fuentes.",
    "La búsqueda combina criterios; cada rol solo ve las fuentes que sus permisos permiten.")
def hu23(n):
    c = Sesion("coordinador")
    c.ir("/busqueda")
    p = c.page
    p.get_by_label("Trabajo de grado").check()
    p.get_by_label("Trámite").check()
    p.get_by_label("Desde").fill("2025-01-01")
    p.get_by_label("Hasta").fill(HOY.isoformat())
    cab = buscar(p, "ansiedad")
    tits = [" ".join(t.split()) for t in p.locator("main section li a").all_inner_texts()]
    cap(p, "HU-23", "_combinada")
    p.get_by_label("Trabajo de grado").uncheck()
    p.get_by_label("Trámite").uncheck()
    p.get_by_label("Desde").fill("")
    p.get_by_label("Hasta").fill("")
    cab_c = buscar(p, "escaneada")
    cap(p, "HU-23", "_coordinador")
    a = Sesion("apoyo")
    a.ir("/busqueda")
    acceso = " ".join(a.page.locator("main").inner_text().split())[:60]
    r = a.page.evaluate("""async () => { const t = sessionStorage.getItem('p25.token');
        const x = await fetch('/api/busqueda?texto=escaneada', { headers: { Authorization: 'Bearer ' + t } });
        const j = await x.json(); return { estado: x.status, fuentes: j.tiposConsultados, resultados: (j.resultados || []).map(r => r.tipo + ': ' + r.titulo) }; }""")
    a.ir("/consultas")
    cons = a.page.get_by_role("heading", name="Consultas con filtros").is_visible()
    cap(a.page, "HU-23")
    ok = bool(tits) and "Documento" not in cab_c and "DOCUMENTO" in (r.get("fuentes") or []) and cons
    return ("Satisfactorio" if ok else "Falla"), (
        f"Coordinador, combinada «ansiedad» + Trabajo de grado/Trámite + fechas: {cab}; resultados {tits[:3]}. "
        f"Coordinador «escaneada»: {cab_c} (no consulta Documentos). Apoyo: /busqueda → «{acceso}»; /consultas disponible: {cons}; "
        f"por consola GET /api/busqueda?texto=escaneada → HTTP {r['estado']}, fuentes {r.get('fuentes')}, resultados {r.get('resultados')}")


@hu("HU-24", "Coordinador", "/busqueda (Semántica)",
    "Búsqueda semántica: «preocupación y nervios de los alumnos frente a sus evaluaciones» (no usa las palabras del título).",
    "Encabeza los resultados el trabajo «Ansiedad académica en estudiantes…» aunque la frase no contiene sus palabras.")
def hu24(n):
    c = Sesion("coordinador")
    c.ir("/busqueda")
    p = c.page
    frase = "preocupación y nervios de los alumnos frente a sus evaluaciones"
    cab = buscar(p, frase, "Semántica")
    tits = [" ".join(t.split()) for t in p.locator("main section li a").all_inner_texts()]
    cap(p, "HU-24")
    ok = bool(tits) and "Ansiedad" in tits[0]
    return ("Satisfactorio" if ok else "Falla"), f"{cab}; primeros resultados: {tits[:3]}"


@hu("HU-25", "Coordinador", "/busqueda (Asistente)",
    "Preguntar al asistente: «¿En qué estado está el trámite TRM-2026-0047?», «¿Qué trámites están vencidos?» y «¿Qué requisitos tiene una convalidación?».",
    "El asistente responde cada pregunta con datos del sistema o indica que no tiene la información.")
def hu25(n):
    c = Sesion("coordinador")
    c.ir("/busqueda")
    p = c.page
    burbujas = p.locator("section div.mr-8.rounded-lg")
    respuestas = []
    for q in ["¿En qué estado está el trámite TRM-2026-0047?", "¿Qué trámites están vencidos?", "¿Qué requisitos tiene una convalidación?"]:
        antes = burbujas.count()
        p.get_by_label("Pregunta al asistente").fill(q)
        p.get_by_role("button", name="Enviar").click()
        expect(burbujas).to_have_count(antes + 1, timeout=60_000)
        respuestas.append((q, " ".join(burbujas.last.inner_text().split())))
    n["respuestas"] = respuestas
    cap(p, "HU-25")
    ok = all(r for _, r in respuestas)
    return ("Satisfactorio" if ok else "Falla"), " | ".join(f"{q} → «{r}»" for q, r in respuestas)


# ================================================================== DASHBOARD, REPORTES Y ALERTAS
@hu("HU-26", "Director", "/ (Inicio)",
    "Ingresar como director y leer los indicadores del dashboard.",
    "Se muestran indicadores de trámites, correspondencia, robot RPA, alertas y POA.")
def hu26(n):
    d = Sesion("director")
    p = d.page
    expect(p.get_by_role("heading", name="Dashboard institucional")).to_be_visible()
    claves = ["Trámites en curso", "Atención a tiempo", "Correspondencia del último mes", "Robot RPA", "Alertas abiertas", "POA 2026",
              "Correspondencia por mes", "Trámites por estado"]
    faltan = [k for k in claves if not p.get_by_text(k, exact=True).first.is_visible()]
    tarj = [" ".join(t.split()) for t in p.locator("main > div").nth(1).locator("> div").all_inner_texts()] if p.locator("main > div").count() > 1 else []
    ind = " ".join(p.locator("main").inner_text().split())[:420]
    cap(p, "HU-26")
    return ("Satisfactorio" if not faltan else "Falla"), f"Indicadores visibles: {[k for k in claves if k not in faltan]}; faltan: {faltan}. Texto: «{ind}»"


@hu("HU-27", "Coordinador", "/reportes",
    "Descargar «Trámites estudiantiles» en PDF y «Correspondencia» en Excel (XLSX).",
    "Se descargan los dos archivos y no están vacíos (PDF con cabecera %PDF, XLSX con cabecera ZIP «PK»).")
def hu27(n):
    c = Sesion("coordinador")
    c.ir("/reportes")
    p = c.page
    res = []
    for titulo, boton, ext in (("Trámites estudiantiles", "PDF", "pdf"), ("Correspondencia", "Excel", "xlsx")):
        sec = p.locator("section", has=p.get_by_role("heading", name=titulo, exact=True))
        with p.expect_download(timeout=60_000) as dl:
            sec.get_by_role("button", name=boton).click()
        destino = DESC / f"hu27-{dl.value.suggested_filename}"
        dl.value.save_as(destino)
        cab = destino.read_bytes()[:4]
        res.append((destino.name, destino.stat().st_size, cab))
    cap(p, "HU-27")
    ok = res[0][1] > 0 and res[0][2] == b"%PDF" and res[1][1] > 0 and res[1][2][:2] == b"PK"
    return ("Satisfactorio" if ok else "Falla"), "; ".join(f"{nm}: {sz} bytes (cabecera {cb!r})" for nm, sz, cb in res)


@hu("HU-29", "Director", "/alertas (Análisis predictivo)",
    "Pulsar «Análisis predictivo» y leer el resultado.",
    "Se entrena el modelo con el histórico y se muestra el riesgo de demora de los trámites en curso.")
def hu29(n):
    d = Sesion("director")
    d.ir("/alertas")
    p = d.page
    p.get_by_role("button", name="Análisis predictivo").click()
    sec = p.locator("section", has=p.get_by_role("heading", name="Resultado del análisis predictivo"))
    expect(sec).to_be_visible(timeout=120_000)
    resumen = " ".join(sec.locator("p").first.inner_text().split())
    filas = sec.locator("tbody tr").count()
    primera = " ".join(sec.locator("tbody tr").first.inner_text().split()) if filas else ""
    cap(p, "HU-29")
    return ("Satisfactorio" if filas else "Falla"), f"«{resumen}»; {filas} trámite(s) en la tabla de riesgo; primero: «{primera}»"


@hu("HU-28", "Director", "/alertas",
    "Pulsar «Revisar plazos ahora»; filtrar por origen «Por plazo» y «Predictivas».",
    "Hay alertas por plazo vencido y alertas por predicción.")
def hu28(n):
    d = Sesion("director")
    d.ir("/alertas")
    p = d.page
    p.get_by_role("button", name="Revisar plazos ahora").click()
    msg = p.get_by_text(re.compile("Revisión de plazos:")).inner_text()
    resumen = " ".join(p.locator("main > div").nth(2).inner_text().split()) if p.locator("main > div").count() > 2 else ""
    cap(p, "HU-28", "_revision_plazos")
    cuentas = {}
    for valor, et in (("PLAZO", "Por plazo"), ("PREDICCION", "Predictivas")):
        con_respuesta(p, "/api/alertas?", lambda: p.get_by_label("Origen").select_option(valor))
        cuentas[et] = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).inner_text()
        if valor == "PLAZO":
            cuentas["ejemplo plazo"] = " ".join(p.locator("tbody tr").first.inner_text().split())
            cap(p, "HU-28", "_plazo")
        else:
            cuentas["ejemplo predicción"] = " ".join(p.locator("tbody tr").first.inner_text().split()) if p.locator("tbody tr td").count() > 1 else ""
    cap(p, "HU-28")
    ok = not cuentas["Por plazo"].startswith("0 ") and not cuentas["Predictivas"].startswith("0 ")
    return ("Satisfactorio" if ok else "Falla"), f"«{msg}». Por plazo: {cuentas['Por plazo']} (p. ej. «{cuentas['ejemplo plazo']}»); Predictivas: {cuentas['Predictivas']} (p. ej. «{cuentas['ejemplo predicción']}»)"


@hu("HU-30", "Coordinador (verificación en auditoría por el Director)", "/alertas",
    "Atender la primera alerta abierta con un comentario («Marcar atendida»); filtrar «Atendidas»; el director revisa la auditoría.",
    "La alerta registra quién y cuándo la atendió y deja de figurar como pendiente.")
def hu30(n):
    c = Sesion("coordinador")
    c.ir("/alertas")
    p = c.page
    abiertas0 = p.locator("p", has_text=re.compile("^Abiertas$")).locator("xpath=following-sibling::p[1]").inner_text()
    fila = p.locator("tbody tr").first
    numero = fila.locator("td").nth(1).locator("a").inner_text().strip()
    motivo = fila.locator("td").nth(2).inner_text().strip()
    fila.get_by_role("button", name="Atender").click()
    dlg = p.get_by_role("dialog")
    dlg.locator("#com").fill("Se contactó al estudiante para completar su trámite (verificación HU-30)")
    dlg.get_by_role("button", name="Marcar atendida").click()
    expect(dlg).to_be_hidden()
    p.wait_for_load_state("networkidle")
    abiertas1 = p.locator("p", has_text=re.compile("^Abiertas$")).locator("xpath=following-sibling::p[1]").inner_text()
    con_respuesta(p, "/api/alertas?", lambda: p.get_by_label("Estado").select_option("ATENDIDA"))
    at = p.locator("tbody tr", has_text=numero).filter(has_text="Fernando").first
    at_txt = " ".join(at.inner_text().split()) if at.count() else "no encontrada"
    cap(p, "HU-30")
    d = Sesion("director")
    d.ir("/auditoria")
    accion = {}
    con_respuesta(d.page, "/api/auditoria?", lambda: accion.setdefault("t", elegir(d.page, "select[aria-label='Acción']", "ATENDER_ALERTA")))
    accion = accion["t"]
    aud = " ".join(d.page.locator("tbody tr").first.inner_text().split())
    cap(d.page, "HU-30", "_auditoria")
    ok = int(abiertas1) == int(abiertas0) - 1 and at.count() > 0 and " 2 " in f" {aud} "
    return ("Satisfactorio" if ok else "Falla"), (
        f"Alerta de {numero} («{motivo[:90]}»): abiertas {abiertas0} → {abiertas1}; en «Atendidas»: «{at_txt}»; "
        f"auditoría ({accion}): «{aud[:200]}»")


# ================================================================== RPA
def formulario_rpa(p, desde, hasta):
    p.fill("#desde", desde)
    p.fill("#hasta", hasta)
    p.locator("#tipo").select_option("TODAS")
    elegir(p, "#rev", "Director")
    p.get_by_label("Otro correo").fill("decanato.demo@ejemplo.edu.bo")
    p.get_by_label("Otro correo").press("Enter")
    expect(p.get_by_text("Oficios autorizados (se consolidan)")).to_be_visible()
    p.wait_for_load_state("networkidle")


@hu("HU-31", "Apoyo (y Coordinador)", "/rpa/ejecutar",
    f"Apoyo ejecuta la consolidación de {args.rpa_desde} a {args.rpa_hasta} (revisor Director, destinatario decanato.demo@ejemplo.edu.bo); "
    "intenta una segunda ejecución del mismo periodo (pantalla y consola); el coordinador abre /rpa/ejecutar.",
    "La ejecución termina con el informe pendiente de revisión (se anotan registros y duración); la segunda se rechaza; el coordinador no tiene el botón.")
def hu31(n):
    obs = []
    a = Sesion("apoyo")
    a.ir("/rpa/ejecutar")
    p = a.page
    formulario_rpa(p, args.rpa_desde, args.rpa_hasta)
    autorizados = dd_de(p, "Oficios autorizados (se consolidan)")
    cap(p, "HU-31", "_formulario")
    t0 = time.perf_counter()
    p.get_by_role("button", name="Ejecutar consolidación").click()
    expect(p.get_by_text("Informe generado")).to_be_visible(timeout=300_000)
    seg = time.perf_counter() - t0
    ide = int(re.search(r"#(\d+)", p.locator("h1").inner_text()).group(1))
    dur, proc, val, inc = (dd_de(p, k) for k in ("Duración", "Procesados", "Válidos", "Incidencias"))
    idi = int(p.get_by_role("link", name="Ver informe").get_attribute("href").rsplit("/", 1)[1])
    DATOS["rpa31"] = {"ejecucion": ide, "informe": idi, "duracion": dur, "procesados": proc, "validos": val, "segundos_navegador": round(seg, 1)}
    obs.append(f"Periodo con {autorizados} oficio(s) autorizado(s). Ejecución #{ide} COMPLETADA, informe #{idi} pendiente de revisión: "
               f"duración {dur} (en el navegador, del clic a «Informe generado»: {seg:.1f} s), procesados {proc}, válidos {val}, incidencias {inc}")
    cap(p, "HU-31", "_completada")
    p.get_by_role("button", name="Nueva ejecución").click()
    formulario_rpa(p, args.rpa_desde, args.rpa_hasta)
    aviso = p.get_by_role("alert").filter(has_text="Ya existe el informe").inner_text()
    deshab = p.get_by_role("button", name="Ejecutar consolidación").is_disabled()
    r = fetch_nav(p, "POST", "/rpa/ejecuciones", {"idProceso": 1, "periodoDesde": args.rpa_desde, "periodoHasta": args.rpa_hasta,
                                                  "tipoCorrespondencia": "TODAS", "idRevisor": 1, "destinatariosCorreo": ["decanato.demo@ejemplo.edu.bo"]})
    cap(p, "HU-31", "_segunda_ejecucion")
    obs.append(f"Segunda ejecución: aviso «{aviso}», botón deshabilitado: {deshab}; por consola → HTTP {r['estado']} «{r['mensaje']}»")
    c = Sesion("coordinador")
    c.ir("/rpa/ejecutar")
    q = c.page
    sin_boton = q.get_by_role("button", name="Ejecutar consolidación").count() == 0
    txt = " ".join(q.locator("main").inner_text().split())[:80]
    cap(q, "HU-31")
    obs.append(f"Coordinador en /rpa/ejecutar: «{txt}»; botón «Ejecutar consolidación» presente: {not sin_boton}; en su menú: {'Ejecutar RPA' in menu(q)}")
    ok = deshab and r["estado"] == 409 and sin_boton
    return ("Satisfactorio" if ok else "Falla"), " | ".join(obs)


@hu("HU-35", "Director", "/rpa/revision/:id",
    "En el informe de HU-31 pulsar «Editar» en el borrador, cambiar el asunto y agregar una línea al mensaje, guardar; comprobar el adjunto; revisar MailHog.",
    "El borrador editado se guarda, sigue llevando el PDF adjunto y no se envía nada antes de aprobar.")
def hu35(n):
    d = Sesion("director")
    d.ir(f"/rpa/revision/{DATOS['rpa31']['informe']}")
    p = d.page
    sec = p.locator("section", has=p.get_by_role("heading", name="Borrador de correo"))
    sec.get_by_role("button", name="Editar").click()
    asunto = sec.locator("#asu").input_value() + " (revisado por la Dirección)"
    sec.locator("#asu").fill(asunto)
    sec.locator("#cue").fill(sec.locator("#cue").input_value() + "\n\nNota: informe revisado por la Dirección antes de su envío (HU-35).")
    sec.get_by_role("button", name="Guardar borrador").click()
    expect(sec.get_by_text(asunto)).to_be_visible()
    adj = dd_de(sec, "Adjunto")
    DATOS["asunto35"] = asunto
    julio = [m for m in correos() if "julio" in m["asunto"].lower()]
    cap(p, "HU-35")
    ok = adj.endswith(".pdf") and not julio
    return ("Satisfactorio" if ok else "Falla"), f"Asunto guardado «{asunto}»; adjunto «{adj}»; estado del correo «{p.get_by_text(re.compile('^Correo: ')).inner_text()}»; correos de julio en MailHog: {len(julio)}"


@hu("HU-34", "Director", "/rpa/revision/:id",
    "Abrir el informe de HU-31 con el navegador VISIBLE (headless=False, el visor de PDF no existe sin interfaz) y esperar la vista previa; contar las páginas del PDF.",
    "La vista previa muestra el PDF con la plantilla institucional y su número de páginas.")
def hu34(n):
    b = NAV["pw"].chromium.launch(headless=False)
    try:
        d = Sesion("director", nav=b, alto=1620)
        p = d.page
        d.ir(f"/rpa/revision/{DATOS['rpa31']['informe']}")
        expect(p.locator("iframe[title='Vista previa del informe']")).to_be_visible(timeout=30_000)
        p.wait_for_timeout(8000)
        info = p.evaluate("""async (id) => { const t = sessionStorage.getItem('p25.token');
            const r = await fetch('/api/rpa/informes/' + id + '/pdf', { headers: { Authorization: 'Bearer ' + t } });
            const b = new Uint8Array(await r.arrayBuffer()); let s = ''; for (const x of b) s += String.fromCharCode(x);
            return { estado: r.status, bytes: b.length, cabecera: s.slice(0, 5), paginas: (s.match(/\\/Type\\s*\\/Page[^s]/g) || []).length }; }""",
                          DATOS["rpa31"]["informe"])
        p.screenshot(path=str(EVID / "HU-34.png"))
        DATOS["pdf34"] = info
        ok = info["cabecera"] == "%PDF-" and info["paginas"] >= 1
        return ("Satisfactorio" if ok else "Falla"), (
            f"Vista previa cargada en el visor de PDF del navegador (ejecutado con headless=False). PDF: HTTP {info['estado']}, {info['bytes']} bytes, "
            f"cabecera {info['cabecera']}, {info['paginas']} página(s). La plantilla institucional se verifica en la captura HU-34.png")
    finally:
        b.close()


@hu("HU-32", "Director", "/rpa/revision, /rpa/revision/:id",
    "Abrir el informe de HU-31: estados y MailHog antes; «Aprobar y autorizar envío» → «Aprobar y enviar»; revisar MailHog (pestaña MIME); "
    "intentar aprobar de nuevo (pantalla y consola); devolver el informe de agosto (otra ejecución) con observaciones.",
    "Antes: correo en BORRADOR y nada en MailHog; después: APROBADO/ENVIADO y el correo llega con el PDF; la 2.ª aprobación se rechaza (409); «Devolver» funciona.")
def hu32(n):
    obs, ok = [], True
    inf = DATOS["rpa31"]["informe"]
    det = DATOS.setdefault("hu32", {"ejecucion": DATOS["rpa31"]["ejecucion"], "informe": inf})
    d = Sesion("director")
    d.ir("/rpa/revision")
    p = d.page
    expect(p.locator("tbody tr", has_text=f"#{inf}")).to_be_visible()
    cap(p, "HU-32", "_lista_pendientes")
    d.ir(f"/rpa/revision/{inf}")
    est_inf0 = p.get_by_text(re.compile("^Informe: ")).inner_text()
    est_cor0 = p.get_by_text(re.compile("^Correo: ")).inner_text()
    julio0 = [m for m in correos() if "julio" in m["asunto"].lower()]
    det.update(antes={"informe": est_inf0, "correo": est_cor0, "correos_julio_mailhog": len(julio0), "total_mailhog": len(correos())})
    cap(p, "HU-32", "_antes")
    ok &= "Borrador" in est_cor0 and not julio0
    p.get_by_role("button", name="Aprobar y autorizar envío").click()
    p.get_by_role("dialog").get_by_role("button", name="Aprobar y enviar").click()
    expect(p.get_by_text(re.compile("^Enviado el"))).to_be_visible(timeout=60_000)
    hora_aprob = datetime.now().strftime("%H:%M:%S")
    est_inf1 = p.get_by_text(re.compile("^Informe: ")).inner_text()
    est_cor1 = p.get_by_text(re.compile("^Correo: ")).inner_text()
    enviado = p.get_by_text(re.compile("^Enviado el")).inner_text()
    det.update(despues={"informe": est_inf1, "correo": est_cor1, "aviso": enviado, "hora_aprobacion_local": hora_aprob})
    cap(p, "HU-32", "_aprobado")
    nuevos = []
    for _ in range(20):
        nuevos = [m for m in correos() if "julio" in m["asunto"].lower()]
        if nuevos:
            break
        time.sleep(1)
    if nuevos:
        m = nuevos[0]
        det.update(correo=m)
        obs.append(f"MailHog: llegó «{m['asunto']}» para {m['para']} a las {m['creado']} con adjunto(s) {m['adjuntos']}")
        ok &= any(x.endswith(".pdf") for x in m["adjuntos"])
    else:
        ok = False
        obs.append("MailHog: no llegó el correo de julio en 20 s")
    hay_boton = p.get_by_role("button", name="Aprobar y autorizar envío").count()
    r = fetch_nav(p, "POST", f"/rpa/informes/{inf}/aprobacion")
    det.update(segunda_aprobacion={"boton_visible": bool(hay_boton), "http": r["estado"], "mensaje": r["mensaje"]})
    ok &= hay_boton == 0 and r["estado"] == 409
    obs.append(f"Antes: «{est_inf0}», «{est_cor0}», correos de julio en MailHog {len(julio0)}. Después: «{est_inf1}», «{est_cor1}», «{enviado}». "
               f"2.ª aprobación: botón visible {bool(hay_boton)}; por consola HTTP {r['estado']} «{r['mensaje']}»")
    m = Sesion()
    m.page.goto(MH)
    m.page.locator(".msglist-message", has_text="julio").first.click()
    m.page.wait_for_timeout(800)
    m.page.get_by_text("MIME", exact=True).click()
    m.page.wait_for_timeout(800)
    cap(m.page, "HU-32", "_mailhog", completa=False)
    # Devolver otra ejecución (agosto, pendiente de revisión)
    d.ir("/rpa/revision")
    fila_ago = p.locator("tbody tr", has_text="01/08/2026").first
    if fila_ago.count():
        fila_ago.click()
        p.wait_for_url(re.compile(r"/rpa/revision/\d+$"))
        id_ago = int(p.url.rsplit("/", 1)[1])
        p.get_by_role("button", name="Devolver").click()
        dlg = p.get_by_role("dialog", name="Devolver informe")
        dlg.locator("#obs").fill("Faltan oficios de la última semana del mes; reejecutar (verificación HU-32)")
        dlg.get_by_role("button", name="Devolver").click()
        expect(p.get_by_text(re.compile("^Informe: Devuelto"))).to_be_visible()
        dev = p.get_by_text(re.compile("^Devuelto el")).inner_text()
        DATOS["devuelto"] = {"informe": id_ago, "ejecucion": int(re.search(r"ejecución #(\d+)", p.locator("main").inner_text()).group(1))}
        det["devolucion"] = {"informe": id_ago, "aviso": dev, "estados": [p.get_by_text(re.compile('^Informe: ')).inner_text(), p.get_by_text(re.compile('^Correo: ')).inner_text()]}
        obs.append(f"Devolver informe #{id_ago} (agosto): «{dev}»; {det['devolucion']['estados']}")
        cap(p, "HU-32", "_devuelto")
    else:
        ok = False
        obs.append("No había otro informe pendiente para probar «Devolver»")
    d.ir(f"/rpa/revision/{inf}")
    cap(p, "HU-32")
    return ("Satisfactorio" if ok else "Falla"), " | ".join(obs)


@hu("HU-33", "Apoyo", "/correspondencia, /tramites, /consultas",
    "Aplicar filtros por número/código, tipo, estado y fecha en las tres pantallas; medir el tiempo de cada consulta (desde el cambio del filtro hasta la respuesta).",
    "Cada filtro devuelve las filas correspondientes y cada consulta tarda menos de 3 s.")
def hu33(n):
    a = Sesion("apoyo")
    p = a.page
    tiempos = []

    def medir(nombre, patron, accion):
        t0 = time.perf_counter()
        with p.expect_response(lambda r: patron in r.url and r.request.method == "GET", timeout=15_000) as info:
            accion()
        info.value.finished()
        t = time.perf_counter() - t0
        p.wait_for_timeout(250)
        tot = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).first.inner_text()
        tiempos.append((nombre, round(t, 3), tot))

    a.ir("/correspondencia")
    medir("Correspondencia · código", "/api/correspondencia?", lambda: p.get_by_label("Buscar").fill(DATOS.get("oficio", "DCP-REC")))
    p.get_by_role("button", name="Limpiar").click()
    p.wait_for_load_state("networkidle")
    medir("Correspondencia · tipo Recibida", "/api/correspondencia?", lambda: p.get_by_label("Tipo").select_option("RECIBIDA"))
    medir("Correspondencia · + estado Derivada", "/api/correspondencia?", lambda: p.get_by_label("Estado").select_option("DERIVADA"))
    medir("Correspondencia · + desde 01/08/2026", "/api/correspondencia?", lambda: p.get_by_label("Desde").fill("2026-08-01"))
    medir("Correspondencia · + hasta 31/08/2026", "/api/correspondencia?", lambda: p.get_by_label("Hasta").fill("2026-08-31"))
    cap(p, "HU-33", "_correspondencia")
    a.ir("/tramites")
    medir("Trámites · número", "/api/tramites?", lambda: p.get_by_label("Buscar").fill(DATOS.get("tram09", {}).get("numero", "TRM-2026")))
    p.get_by_role("button", name="Limpiar").click()
    p.wait_for_load_state("networkidle")
    medir("Trámites · estado En revisión", "/api/tramites?", lambda: p.get_by_label("Estado").select_option("EN_REVISION"))
    medir("Trámites · + tipo Licencia temporal", "/api/tramites?", lambda: elegir(p, "select[aria-label='Tipo']", "Licencia temporal"))
    medir("Trámites · + recibido desde 01/09/2026", "/api/tramites?", lambda: p.get_by_label("Recibido desde").fill("2026-09-01"))
    cap(p, "HU-33", "_tramites")
    a.ir("/consultas")
    medir("Consultas correspondencia · tipo Emitida", "/api/consultas/correspondencia", lambda: p.get_by_label("Tipo").select_option("EMITIDA"))
    medir("Consultas correspondencia · + estado Archivada", "/api/consultas/correspondencia", lambda: p.get_by_label("Estado").select_option("ARCHIVADA"))
    medir("Consultas correspondencia · + desde 01/09/2026", "/api/consultas/correspondencia", lambda: p.get_by_label("Desde").fill("2026-09-01"))
    medir("Consultas trámites · pestaña", "/api/consultas/tramites", lambda: p.get_by_role("tab", name="Trámites").click())
    medir("Consultas trámites · estado Observado", "/api/consultas/tramites", lambda: p.get_by_label("Estado").select_option("OBSERVADO"))
    medir("Consultas trámites · + prioridad ALTA", "/api/consultas/tramites", lambda: p.get_by_label("Prioridad").select_option("ALTA"))
    cap(p, "HU-33")
    n["tiempos"] = tiempos
    ok = all(t < 3 for _, t, _ in tiempos)
    return ("Satisfactorio" if ok else "Falla"), "; ".join(f"{nm}: {t:.3f} s → {tot}" for nm, t, tot in tiempos) + f". Máximo {max(t for _, t, _ in tiempos):.3f} s (< 3 s: {ok})"


@hu("HU-36", "Coordinador", "/rpa/historial",
    "Leer las métricas del historial RPA y abrir la ejecución de HU-31.",
    "Se ven duración, registros válidos, incidencias y tasa de éxito, por ejecución y en total.")
def hu36(n):
    c = Sesion("coordinador")
    c.ir("/rpa/historial")
    p = c.page
    met = " ".join(p.locator("main > div").nth(1).inner_text().split())
    cabeceras = [t.strip() for t in p.locator("thead th").all_inner_texts()]
    p.locator("tbody tr", has=p.locator("td", has_text=re.compile(f"^{DATOS['rpa31']['ejecucion']}"))).first.click()
    dlg = p.get_by_role("dialog", name=f"Ejecución RPA #{DATOS['rpa31']['ejecucion']}")
    expect(dlg).to_be_visible()
    datos = {k: dd_de(dlg, k) for k in ("Duración", "Registros válidos")}
    inc = dlg.locator("section", has=p.get_by_role("heading", name="Incidencias")).inner_text()
    cap(p, "HU-36")
    ok = "TASA DE ÉXITO" in met.upper() and "DURACIÓN PROMEDIO" in met.upper() and {"DURACIÓN", "REGISTROS", "ERRORES"} <= {x.upper() for x in cabeceras}
    return ("Satisfactorio" if ok else "Falla"), f"Métricas: «{met}». Columnas: {cabeceras}. Ejecución #{DATOS['rpa31']['ejecucion']}: {datos}; «{' '.join(inc.split())}»"


@hu("HU-37", "Coordinador", "/rpa/historial",
    "Abrir la ejecución cuyo informe se devolvió en HU-32 y pulsar «Reejecutar» con motivo; esperar el resultado; revisar los informes del periodo y MailHog; "
    "abrir la ejecución de julio (correo enviado) para ver si permite reejecutar.",
    "La reejecución queda vinculada a la original, no duplica el informe vigente ni el envío (MailHog sin un segundo correo del periodo).")
def hu37(n):
    obs, ok = [], True
    orig = DATOS["devuelto"]["ejecucion"]
    c = Sesion("coordinador")
    c.ir(f"/rpa/historial?ejecucion={orig}")
    p = c.page
    dlg = p.get_by_role("dialog", name=f"Ejecución RPA #{orig}")
    dlg.get_by_role("button", name="Reejecutar").click()
    m = p.get_by_role("dialog", name=f"Reejecutar la ejecución #{orig}")
    m.locator("#mot").fill("Corrección tras la devolución del Director (verificación HU-37)")
    m.get_by_role("button", name="Reejecutar").click()
    nueva = p.get_by_role("dialog", name=re.compile(r"^Ejecución RPA #(?!%d$)\d+$" % orig))
    expect(nueva).to_be_visible(timeout=30_000)
    idn = int(re.search(r"#(\d+)", nueva.get_attribute("aria-label")).group(1))
    expect(nueva.get_by_text(re.compile("Pendiente de revisión|Fallida|Completada"))).to_be_visible(timeout=300_000)
    vinculo = nueva.get_by_role("button", name=f"Ver ejecución anterior #{orig}").count() > 0
    sit = nueva.locator("span").first.inner_text()
    cap(p, "HU-37", "_reejecucion")
    obs.append(f"Reejecución #{idn} de la #{orig}: situación «{sit}»; botón «Ver ejecución anterior #{orig}»: {vinculo}")
    ok &= vinculo
    nueva.get_by_role("button", name="Cerrar").click()
    c.ir("/rpa/historial")
    fila = " ".join(p.locator("tbody tr", has=p.locator("td", has_text=re.compile(f"^{idn}"))).first.inner_text().split())
    fila_o = " ".join(p.locator("tbody tr", has=p.locator("td", has_text=re.compile(f"^{orig}"))).first.inner_text().split())
    obs.append(f"Historial: «{fila}» / original «{fila_o}»")
    cap(p, "HU-37", "_historial")
    c.ir("/rpa/revision")
    p.get_by_role("tab", name="Todos").click()
    p.wait_for_load_state("networkidle")
    ago = [" ".join(t.split()) for t in p.locator("tbody tr", has_text="01/08/2026").all_inner_texts()]
    vigentes = [x for x in ago if "Devuelto" not in x and "Anulado" not in x]
    cap(p, "HU-37", "_informes")
    obs.append(f"Informes de agosto: {ago} (vigentes: {len(vigentes)})")
    ok &= len(vigentes) <= 1
    lista = correos()
    ago_m = [x["asunto"] for x in lista if "agosto" in x["asunto"].lower()]
    jul_m = [x["asunto"] for x in lista if "julio" in x["asunto"].lower()]
    obs.append(f"MailHog: correos de agosto {len(ago_m)}, de julio {len(jul_m)} (total {len(lista)})")
    ok &= len(ago_m) <= 1 and len(jul_m) == 1
    c.ir(f"/rpa/historial?ejecucion={DATOS['rpa31']['ejecucion']}")
    dj = p.get_by_role("dialog", name=f"Ejecución RPA #{DATOS['rpa31']['ejecucion']}")
    expect(dj).to_be_visible()
    p.wait_for_timeout(800)
    motivo = dj.locator("section").last.inner_text().strip()
    puede = dj.get_by_role("button", name="Reejecutar").count()
    cap(p, "HU-37")
    obs.append(f"Ejecución de julio (correo enviado): botón Reejecutar {'visible' if puede else 'no disponible'}; «{motivo}»")
    ok &= puede == 0
    return ("Satisfactorio" if ok else "Falla"), " | ".join(obs)


@hu("HU-03", "Director (y Coordinador)", "/auditoria",
    "Filtrar por acción LOGIN, entidad USUARIO, ID de usuario 1 y fecha de hoy; buscar botones de edición; intentar DELETE desde la consola; coordinador abre /auditoria.",
    "Los filtros se combinan y las filas cumplen todos; no hay botones de editar ni eliminar; la bitácora no admite escritura; el coordinador no accede.")
def hu03(n):
    d = Sesion("director")
    d.ir("/auditoria")
    p = d.page
    total0 = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).inner_text()
    con_respuesta(p, "/api/auditoria?", lambda: p.get_by_label("Acción").select_option("LOGIN"))
    con_respuesta(p, "/api/auditoria?", lambda: p.get_by_label("Entidad").select_option("USUARIO"))
    con_respuesta(p, "/api/auditoria?", lambda: p.get_by_label("ID de usuario").fill("1"))
    con_respuesta(p, "/api/auditoria?", lambda: p.get_by_label("Desde").fill(HOY.isoformat()))
    con_respuesta(p, "/api/auditoria?", lambda: p.get_by_label("Hasta").fill(HOY.isoformat()))
    total1 = p.get_by_text(re.compile(r"^\d+ registro\(s\)$")).inner_text()
    filas = p.locator("tbody tr")
    malas = []
    for i in range(filas.count()):
        cel = [x.strip() for x in filas.nth(i).locator("td").all_inner_texts()]
        if not (cel[0].startswith(FMT_CORTO) and cel[1] == "1" and cel[2] == "LOGIN" and cel[3] == "USUARIO"):
            malas.append(cel[:4])
    botones = p.locator("main table button").count() + p.locator("main button", has_text=re.compile("Editar|Eliminar")).count()
    r = fetch_nav(p, "DELETE", "/auditoria/1")
    r2 = fetch_nav(p, "PUT", "/auditoria/1", {"detalle": "x"})
    cap(p, "HU-03")
    c = Sesion("coordinador")
    c.ir("/auditoria")
    txt = " ".join(c.page.locator("main").inner_text().split())[:60]
    cap(c.page, "HU-03", "_coordinador")
    ok = not malas and botones == 0 and r["estado"] >= 400 and r2["estado"] >= 400 and "Acceso no autorizado" in txt
    return ("Satisfactorio" if ok else "Falla"), (
        f"Sin filtros {total0}; con acción LOGIN + entidad USUARIO + usuario 1 + desde/hasta {HOY:%d/%m/%Y} → {total1}; filas que no cumplen: {malas or 'ninguna'}. "
        f"Botones de editar/eliminar: {botones}. Consola: DELETE /api/auditoria/1 → HTTP {r['estado']}; PUT → HTTP {r2['estado']}. Coordinador: «{txt}»")


# ------------------------------------------------------------------ ejecución
def main():
    solo = {x.strip().upper() for x in args.solo.split(",") if x.strip()}
    resultados_previos = {}
    archivo = EVID / "resultados.json"
    if solo and archivo.exists():
        previo = json.loads(archivo.read_text(encoding="utf-8"))
        resultados_previos = previo.get("resultados", {})
        DATOS.update(previo.get("datos", {}))
    inicio = datetime.now()
    with sync_playwright() as pw:
        NAV["pw"] = pw
        NAV["b"] = pw.chromium.launch(headless=True)
        preparar_archivos()
        for h in PLAN:
            if solo and h["codigo"] not in solo:
                continue
            for viejo in EVID.glob(f"{h['codigo']}*.png"):
                viejo.unlink()
            ABIERTAS.clear()
            notas = {}
            t0 = time.perf_counter()
            print(f"\n=== {h['codigo']} ({h['rol']}) {h['ruta']}", flush=True)
            try:
                estado, obtenido = h["fn"](notas)
            except Exception as e:
                estado = "Falla"
                obtenido = f"Excepción en el paso automatizado: {type(e).__name__}: {' '.join(str(e).split())[:600]}"
                notas["traza"] = traceback.format_exc()[-1500:]
                for s in reversed(ABIERTAS):
                    try:
                        s.page.screenshot(path=str(EVID / f"{h['codigo']}_error.png"), full_page=True)
                        break
                    except Exception:
                        continue
            consola = []
            for s in ABIERTAS:
                consola += s.consola
                s.cerrar()
            resultados_previos[h["codigo"]] = {
                **{k: h[k] for k in ("codigo", "rol", "ruta", "pasos", "esperado")},
                "obtenido": obtenido, "estado": estado, "segundos": round(time.perf_counter() - t0, 1),
                "capturas": sorted(x.name for x in EVID.glob(f"{h['codigo']}*.png")),
                "consola": consola, "notas": notas,
            }
            print(f"   {estado}: {obtenido[:400]}", flush=True)
            archivo.write_text(json.dumps({"inicio": inicio.isoformat(timespec="seconds"), "fin": datetime.now().isoformat(timespec="seconds"),
                                           "resultados": resultados_previos, "datos": DATOS}, ensure_ascii=False, indent=2, default=str), encoding="utf-8")
        NAV["b"].close()
    r = resultados_previos.values()
    print(f"\nSatisfactorias {sum(x['estado'] == 'Satisfactorio' for x in r)} · Falla {sum(x['estado'] == 'Falla' for x in r)} · "
          f"No ejecutadas {sum(x['estado'] == 'No ejecutado' for x in r)} · detalle en {archivo}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
