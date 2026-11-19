"""Validación, consolidación y clasificación de la correspondencia (lógica pura, sin E/S)."""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field
from datetime import date

from .modelos import Detalle, Incidencia, RegistroCorrespondencia

CAMPOS_OBLIGATORIOS = ("codigo", "tipo", "asunto", "fechaDocumento", "dependencia")
TIPOS_VALIDOS = {"RECIBIDA", "EMITIDA"}
ETIQUETA_ESTADO = {
    "REGISTRADA": "Registrada", "DERIVADA": "Derivada", "EN_TRAMITE": "En trámite",
    "RESPONDIDA": "Respondida", "ARCHIVADA": "Archivada",
}


@dataclass
class RegistroValido:
    codigo: str
    tipo: str
    asunto: str
    fecha: date
    estado: str
    dependencia: str


@dataclass
class Consolidado:
    validos: list[RegistroValido] = field(default_factory=list)
    incidencias: list[Incidencia] = field(default_factory=list)
    detalle: list[Detalle] = field(default_factory=list)
    total_recibidos: int = 0
    total_emitidos: int = 0


def _fecha(valor: str | None) -> date | None:
    if not valor:
        return None
    try:
        return date.fromisoformat(valor[:10])
    except ValueError:
        return None


def validar(registros: list[RegistroCorrespondencia], desde: date, hasta: date,
            tipo_filtro: str = "TODAS", hoy: date | None = None) -> Consolidado:
    """Aplica las reglas de calidad y devuelve los registros válidos más las incidencias.

    - Campos obligatorios: código, tipo, asunto, fecha y dependencia.
    - Duplicados: el mismo código aparece más de una vez (se conserva la primera aparición).
    - Fechas inválidas: no interpretables, fuera del periodo o futuras.
    - Tipo inválido o que no corresponde al filtro solicitado.
    """
    hoy = hoy or date.today()
    resultado = Consolidado()
    vistos: set[str] = set()

    for i, r in enumerate(registros, start=1):
        ref = r.codigo or f"registro #{i}"
        faltantes = [c for c in CAMPOS_OBLIGATORIOS if not (getattr(r, c) or "").strip()]
        if faltantes:
            resultado.incidencias.append(Incidencia(
                tipo="CAMPO_OBLIGATORIO", descripcion=f"{ref}: faltan los campos {', '.join(faltantes)}"))
            continue

        codigo = r.codigo.strip()
        if codigo in vistos:
            resultado.incidencias.append(Incidencia(
                tipo="DUPLICADO", descripcion=f"{codigo}: código duplicado; se consolidó una sola vez"))
            continue

        tipo = r.tipo.strip().upper()
        if tipo not in TIPOS_VALIDOS:
            resultado.incidencias.append(Incidencia(
                tipo="CAMPO_OBLIGATORIO", descripcion=f"{codigo}: tipo de correspondencia inválido ({r.tipo})"))
            continue
        if tipo_filtro != "TODAS" and tipo != tipo_filtro:
            resultado.incidencias.append(Incidencia(
                tipo="CAMPO_OBLIGATORIO", descripcion=f"{codigo}: tipo {tipo} no corresponde al filtro {tipo_filtro}"))
            continue

        f = _fecha(r.fechaDocumento)
        if f is None:
            resultado.incidencias.append(Incidencia(
                tipo="FECHA_INVALIDA", descripcion=f"{codigo}: fecha no interpretable ({r.fechaDocumento})"))
            continue
        if f > hoy:
            resultado.incidencias.append(Incidencia(
                tipo="FECHA_INVALIDA", descripcion=f"{codigo}: fecha futura ({f.isoformat()})"))
            continue
        if not (desde <= f <= hasta):
            resultado.incidencias.append(Incidencia(
                tipo="FECHA_INVALIDA",
                descripcion=f"{codigo}: fecha {f.isoformat()} fuera del periodo {desde.isoformat()} a {hasta.isoformat()}"))
            continue

        vistos.add(codigo)
        resultado.validos.append(RegistroValido(
            codigo=codigo, tipo=tipo, asunto=r.asunto.strip(), fecha=f,
            estado=(r.estado or "REGISTRADA").strip().upper(), dependencia=r.dependencia.strip()))

    _clasificar(resultado, desde, hasta)
    return resultado


def _clave_fecha(f: date, desde: date, hasta: date) -> str:
    """Periodos de más de 31 días se agrupan por mes; los demás, por semana ISO."""
    if (hasta - desde).days > 31:
        return f"{f.year}-{f.month:02d}"
    lunes = date.fromordinal(f.toordinal() - f.weekday())
    return f"Semana del {lunes.strftime('%d/%m/%Y')}"


def _clasificar(c: Consolidado, desde: date, hasta: date) -> None:
    por_fecha = Counter(_clave_fecha(r.fecha, desde, hasta) for r in c.validos)
    por_tipo = Counter("Recibida" if r.tipo == "RECIBIDA" else "Emitida" for r in c.validos)
    por_estado = Counter(ETIQUETA_ESTADO.get(r.estado, r.estado) for r in c.validos)
    por_dependencia = Counter(r.dependencia for r in c.validos)

    def ordenar(contador: Counter, por_clave: bool = False):
        return sorted(contador.items(), key=(lambda kv: kv[0]) if por_clave else (lambda kv: (-kv[1], kv[0])))

    # Orden cronológico real (las claves "Semana del dd/mm/aaaa" no se ordenan bien como texto)
    primera_fecha: dict[str, date] = {}
    for r in c.validos:
        k = _clave_fecha(r.fecha, desde, hasta)
        primera_fecha[k] = min(primera_fecha.get(k, r.fecha), r.fecha)
    for clave, n in sorted(por_fecha.items(), key=lambda kv: primera_fecha[kv[0]]):
        c.detalle.append(Detalle(categoria="FECHA", clave=clave, cantidad=n))
    for clave, n in ordenar(por_tipo):
        c.detalle.append(Detalle(categoria="TIPO", clave=clave, cantidad=n))
    for clave, n in ordenar(por_estado):
        c.detalle.append(Detalle(categoria="ESTADO", clave=clave, cantidad=n))
    for clave, n in ordenar(por_dependencia):
        c.detalle.append(Detalle(categoria="DEPENDENCIA", clave=clave[:150], cantidad=n))

    c.total_recibidos = por_tipo.get("Recibida", 0)
    c.total_emitidos = por_tipo.get("Emitida", 0)
