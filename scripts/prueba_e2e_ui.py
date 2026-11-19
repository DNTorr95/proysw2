"""
Prueba de punta a punta POR LA INTERFAZ (punto 12), con un navegador real (Playwright):

  login de Apoyo -> ejecutar RPA -> (ningún correo enviado) -> login de Director -> aprobar
  -> correo visible en MailHog con el PDF adjunto -> auditoría registrada.

Guarda capturas numeradas en ./evidencias/ para el documento del proyecto.

Uso (con el sistema levantado con Docker Compose):
    pip install playwright && python -m playwright install chromium
    python scripts/prueba_e2e_ui.py --url http://localhost --mailhog http://localhost:8025 --desde 2026-09-01 --hasta 2026-09-30

El periodo no debe tener ya un informe vigente (con el seed: agosto, septiembre u octubre de 2026).
Sin MailHog (entorno local con otro SMTP) puede usarse --maildir <carpeta Maildir>.
"""

import argparse
import json
import mailbox
import sys
import time
import urllib.request
from email import message_from_bytes, policy
from pathlib import Path

from playwright.sync_api import Page, expect, sync_playwright

p = argparse.ArgumentParser()
p.add_argument("--url", default="http://localhost")
p.add_argument("--mailhog", default="http://localhost:8025")
p.add_argument("--maildir", default=None)
p.add_argument("--desde", default="2026-09-01")
p.add_argument("--hasta", default="2026-09-30")
p.add_argument("--destinatario", default="decanato.demo@ejemplo.edu.bo")
p.add_argument("--evidencias", default="evidencias")
p.add_argument("--visible", action="store_true", help="muestra el navegador")
args = p.parse_args()

EVID = Path(args.evidencias)
EVID.mkdir(exist_ok=True)
paso = 0


def captura(page: Page, nombre: str):
    global paso
    paso += 1
    page.screenshot(path=str(EVID / f"{paso:02d}_{nombre}.png"), full_page=True)
    print(f"  [{paso:02d}] {nombre}")


def correos() -> list[dict]:
    """Mensajes recibidos por el SMTP de prueba: asunto, destinatarios y adjuntos."""
    if args.maildir:
        md = mailbox.Maildir(args.maildir, create=True)
        lista = []
        for k in md.keys():
            m = message_from_bytes(md.get_bytes(k), policy=policy.default)
            lista.append({"asunto": str(m["Subject"]), "para": str(m["To"]),
                          "adjuntos": [a.get_filename() for a in m.iter_attachments()]})
        return lista
    with urllib.request.urlopen(f"{args.mailhog}/api/v2/messages?limit=200") as r:
        datos = json.loads(r.read())
    lista = []
    for it in datos.get("items", []):
        h = it["Content"]["Headers"]
        partes = it.get("MIME", {}).get("Parts", []) if it.get("MIME") else []
        adjuntos = [pt["Headers"].get("Content-Disposition", [""])[0] for pt in partes if "attachment" in str(pt["Headers"].get("Content-Disposition", ""))]
        lista.append({"asunto": message_from_bytes(("Subject: " + h.get("Subject", [""])[0] + "\n\n").encode(), policy=policy.default)["Subject"],
                      "para": ", ".join(h.get("To", [])), "adjuntos": adjuntos})
    return lista


def login(page: Page, usuario: str, clave: str):
    page.goto(f"{args.url}/login")
    page.fill("#usuario", usuario)
    page.fill("#contrasena", clave)
    page.click("button[type=submit]")
    page.wait_for_url(f"{args.url}/")


def salir(page: Page):
    page.click("button[aria-label='Cerrar sesión']")
    page.wait_for_url(f"{args.url}/login")


def main() -> int:
    inicio = time.monotonic()
    with sync_playwright() as pw:
        nav = pw.chromium.launch(headless=not args.visible)
        page = nav.new_page(viewport={"width": 1440, "height": 900}, locale="es-BO")
        errores_js: list[str] = []
        page.on("pageerror", lambda e: errores_js.append(str(e)))
        correos_antes = len(correos())

        print("1. Apoyo Profesional ejecuta la consolidación RPA")
        login(page, "apoyo", "Apoyo2026*")
        page.goto(f"{args.url}/rpa/ejecutar")
        page.fill("#desde", args.desde)
        page.fill("#hasta", args.hasta)
        page.select_option("#rev", label=page.locator("#rev option", has_text="Director").first.inner_text())
        page.fill("input[aria-label='Otro correo']", args.destinatario)
        page.press("input[aria-label='Otro correo']", "Enter")
        expect(page.get_by_text("Oficios autorizados (se consolidan)")).to_be_visible()
        captura(page, "apoyo_formulario_rpa")
        page.click("button:has-text('Ejecutar consolidación')")
        expect(page.get_by_text("Informe generado")).to_be_visible(timeout=300_000)
        captura(page, "apoyo_rpa_completado")
        assert len(correos()) == correos_antes, "Se envió un correo sin aprobación"
        print("   OK: informe en PENDIENTE_REVISION y ningún correo enviado")
        salir(page)

        print("2. Director revisa y autoriza el envío")
        login(page, "director", "Director2026*")
        page.click("button[aria-label^='Notificaciones']")
        page.get_by_text("Informe asignado para su revisión").first.click()
        expect(page.get_by_text("Borrador de correo")).to_be_visible()
        captura(page, "director_revision_informe")
        page.click("button:has-text('Aprobar y autorizar envío')")
        page.click("button:has-text('Aprobar y enviar')")
        expect(page.get_by_text("Enviado el")).to_be_visible(timeout=60_000)
        captura(page, "director_informe_enviado")

        print("3. Verificación del correo en el servidor SMTP")
        nuevos = []
        for _ in range(20):
            nuevos = correos()[: max(0, len(correos()) - correos_antes)] if not args.maildir else correos()[correos_antes:]
            if nuevos:
                break
            time.sleep(1)
        assert len(nuevos) == 1, f"Se esperaba exactamente 1 correo nuevo y hay {len(nuevos)}"
        print(f"   OK: '{nuevos[0]['asunto']}' para {nuevos[0]['para']} con adjunto(s) {nuevos[0]['adjuntos']}")
        if not args.maildir:
            page.goto(args.mailhog)
            page.wait_for_timeout(1500)
            captura(page, "mailhog_correo_recibido")

        print("4. Auditoría registrada")
        page.goto(f"{args.url}/auditoria")
        filtro = page.locator("select[aria-label='Acción']")
        for accion in ["RPA_INICIAR", "RPA_EJECUCION_COMPLETADA", "APROBAR_INFORME", "AUTORIZAR_ENVIO", "CORREO_ENVIADO"]:
            filtro.select_option(accion)
            expect(page.locator("tbody tr").first).to_contain_text(accion)
            if accion == "CORREO_ENVIADO":
                captura(page, "auditoria_correo_enviado")
        print("   OK: RPA_INICIAR, RPA_EJECUCION_COMPLETADA, APROBAR_INFORME, AUTORIZAR_ENVIO y CORREO_ENVIADO registrados")
        nav.close()

    assert not errores_js, f"Errores de JavaScript: {errores_js}"
    print(f"\nPRUEBA DE PUNTA A PUNTA SUPERADA en {time.monotonic() - inicio:.0f} s · evidencias en {EVID.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
