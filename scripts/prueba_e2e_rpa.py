"""Prueba de punta a punta (punto 12): Apoyo ejecuta RPA -> Director aprueba -> correo en SMTP -> auditoría."""
import json, time, urllib.request, urllib.error, mailbox, sys
from email import policy
# Uso:  python scripts/prueba_e2e_rpa.py <URL base, p. ej. http://localhost> [<carpeta Maildir del SMTP de prueba>]
# Con Docker, los correos se ven en MailHog (http://localhost:8025); el buzón local es opcional.
B = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost").rstrip("/") + "/api"
BUZON = sys.argv[2] if len(sys.argv) > 2 else None
def req(m, path, body=None, tok=None):
    r = urllib.request.Request(B + path, method=m, data=None if body is None else json.dumps(body).encode())
    r.add_header("Content-Type", "application/json")
    if tok: r.add_header("Authorization", "Bearer " + tok)
    try:
        with urllib.request.urlopen(r) as x: t = x.read(); return x.status, (json.loads(t) if t and t[:1] in b"{[" else t)
    except urllib.error.HTTPError as e: return e.code, json.loads(e.read() or b"null")
def login(u, p): return req("POST", "/auth/login", {"nombreUsuario": u, "contrasena": p})[1]["token"]
def correos(): return len(mailbox.Maildir(BUZON, create=True)) if BUZON else "ver MailHog"

print("1) Login Apoyo"); A = login("apoyo", "Apoyo2026*")
_, prev = req("GET", "/rpa/previsualizacion?idProceso=1&desde=2026-10-01&hasta=2026-10-31&tipo=TODAS", tok=A)
print("   oficios autorizados oct/2026:", prev["autorizados"], "· sin autorizar:", prev["noAutorizados"])
print("2) Ejecutar RPA (octubre 2026, revisor Director)")
s, e = req("POST", "/rpa/ejecuciones", {"idProceso": 1, "periodoDesde": "2026-10-01", "periodoHasta": "2026-10-31",
          "tipoCorrespondencia": "TODAS", "idRevisor": 1, "destinatariosCorreo": ["decanato.demo@ejemplo.edu.bo"]}, A)
print("  ", s, "ejecución", e["id"], e["estado"])
t0 = time.time()
while True:
    _, d = req("GET", f"/rpa/ejecuciones/{e['id']}", tok=A)
    if d["ejecucion"]["estado"] in ("COMPLETADA", "FALLIDA"): break
    time.sleep(0.5)
ej = d["ejecucion"]
print(f"   {ej['estado']} en {ej['duracionMs']} ms · válidos {ej['registrosValidos']}/{ej['registrosProcesados']} · situación {ej['situacion']}")
for p in d["pasos"]: print(f"     - [{p['origen'][:4]}] {p['paso']:<22} {p['estado']:<11} {p['duracionMs']} ms")
print("   correos en SMTP tras ejecutar:", correos(), "(debe ser 0: nada se envía sin aprobación)")
if ej["estado"] == "FALLIDA":
    print("   (falló:", d["mensajeError"], ") -> el Coordinador reejecuta (CU-37)")
    C = login("coordinador", "Coordinador2026*")
    s, n = req("POST", f"/rpa/ejecuciones/{e['id']}/reejecucion", {"motivo": "Corrección del cliente HTTP"}, C)
    print("  ", s, "nueva ejecución", n["id"], "origen", n["idEjecucionOrigen"])
    while True:
        _, d = req("GET", f"/rpa/ejecuciones/{n['id']}", tok=A)
        if d["ejecucion"]["estado"] in ("COMPLETADA", "FALLIDA"): break
        time.sleep(0.5)
    ej = d["ejecucion"]
    print(f"   {ej['estado']} en {ej['duracionMs']} ms · válidos {ej['registrosValidos']}/{ej['registrosProcesados']} · situación {ej['situacion']}")
    for p in d["pasos"]: print(f"     - [{p['origen'][:4]}] {p['paso']:<22} {p['estado']:<11} {p['duracionMs']} ms")
    print("   incidencias:", [(i["tipo"], i["descripcion"][:60]) for i in d["incidencias"]])
print("3) Login Director y revisión")
D = login("director", "Director2026*")
_, notif = req("GET", "/notificaciones", tok=D); print("   notificaciones del Director:", [n["titulo"] for n in notif][:3])
idI = ej["idInforme"]
s, pdf = req("GET", f"/rpa/informes/{idI}/pdf", tok=D); print("   PDF vista previa:", s, len(pdf), "bytes, empieza con", pdf[:5])
s, r = req("POST", f"/rpa/informes/{idI}/aprobacion", tok=D)
print("4) Aprobar y autorizar envío ->", s, "informe", r["informe"]["estadoRevision"], "· correo", r["correo"]["estado"],
      "· autoriza", r["correo"]["autorizadoPor"])
time.sleep(1)
md = mailbox.Maildir(BUZON, create=True) if BUZON else {}
print("5) Correos recibidos por el SMTP:", len(md) if BUZON else "ver MailHog en http://localhost:8025")
for k, m in md.items():
    msg = m if hasattr(m, "iter_attachments") else None
    import email; em = email.message_from_bytes(md.get_bytes(k), policy=policy.default)
    adj = [(a.get_filename(), len(a.get_content())) for a in em.iter_attachments()]
    print("   Para:", em["To"], "| Asunto:", em["Subject"], "| Adjuntos:", adj)
s, r2 = req("POST", f"/rpa/informes/{idI}/aprobacion", tok=D); print("   segunda aprobación ->", s, "· correos en SMTP:", correos())
_, aud = req("GET", "/auditoria?tamano=40", tok=D)
print("6) Auditoría:", [a["accion"] for a in aud["contenido"] if a["entidad"] in ("EJECUCION_RPA", "INFORME_MENSUAL", "NOTIFICACION_CORREO")][:8])
