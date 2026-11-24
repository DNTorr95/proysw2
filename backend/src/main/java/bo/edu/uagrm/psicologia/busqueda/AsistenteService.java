package bo.edu.uagrm.psicologia.busqueda;

import bo.edu.uagrm.psicologia.academico.dto.AcademicoDtos.ResumenPlanificacionDto;
import bo.edu.uagrm.psicologia.academico.modelo.PeriodoAcademico;
import bo.edu.uagrm.psicologia.academico.repositorio.PeriodoAcademicoRepository;
import bo.edu.uagrm.psicologia.academico.servicio.PlanificacionService;
import bo.edu.uagrm.psicologia.comun.ia.ClienteServiciosIa;
import bo.edu.uagrm.psicologia.documental.modelo.Correspondencia;
import bo.edu.uagrm.psicologia.documental.modelo.SeguimientoCorrespondencia;
import bo.edu.uagrm.psicologia.institucional.modelo.Evento;
import bo.edu.uagrm.psicologia.institucional.modelo.ActividadPoa;
import bo.edu.uagrm.psicologia.institucional.modelo.Poa;
import bo.edu.uagrm.psicologia.institucional.repositorio.PoaRepository;
import bo.edu.uagrm.psicologia.reportes.alertas.AlertaService;
import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import bo.edu.uagrm.psicologia.rpa.repositorio.InformeMensualRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioActual;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import bo.edu.uagrm.psicologia.tramites.modelo.EstadoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.TipoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.Tramite;
import bo.edu.uagrm.psicologia.tramites.repositorio.TipoTramiteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CU-25 Asistente: servicios-ia interpreta la pregunta (intención + entidades) y el backend ejecuta la
 * consulta AUTORIZADA para el usuario y redacta la respuesta. Si el usuario no tiene el permiso de la
 * consulta, el asistente lo indica en lugar de responder.
 */
@Service
public class AsistenteService {

    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ES = Locale.forLanguageTag("es");

    private final ClienteServiciosIa ia;
    private final EntityManager em;
    private final TipoTramiteRepository tipos;
    private final InformeMensualRepository informes;
    private final PoaRepository poas;
    private final AlertaService alertas;
    private final PeriodoAcademicoRepository periodos;
    private final PlanificacionService planificacion;

    public AsistenteService(ClienteServiciosIa ia, EntityManager em, TipoTramiteRepository tipos,
                            InformeMensualRepository informes, PoaRepository poas, AlertaService alertas,
                            PeriodoAcademicoRepository periodos, PlanificacionService planificacion) {
        this.ia = ia;
        this.em = em;
        this.tipos = tipos;
        this.informes = informes;
        this.poas = poas;
        this.alertas = alertas;
        this.periodos = periodos;
        this.planificacion = planificacion;
    }

    public record Enlace(String texto, String ruta) {
    }

    public record Respuesta(String intencion, double confianza, String texto, List<Enlace> enlaces, List<String> sugerencias) {
    }

    private record Contenido(String texto, List<Enlace> enlaces) {
        static Contenido de(String texto) {
            return new Contenido(texto, List.of());
        }
    }

    @Transactional(readOnly = true)
    public Respuesta responder(String pregunta) {
        UsuarioAutenticado u = UsuarioActual.obtener();
        JsonNode r = ia.post("/interno/asistente", Map.of("pregunta", pregunta));
        String intencion = r.path("intencion").asText("DESCONOCIDA");
        JsonNode ent = r.path("entidades");
        Contenido c = switch (intencion) {
            case "ESTADO_TRAMITE" -> estadoTramite(u, ent);
            case "TRAMITES_VENCIDOS" -> vencidos(u);
            case "TRAMITES_POR_ESTADO" -> porEstado(u);
            case "REQUISITOS_TRAMITE" -> requisitos(ent);
            case "CORRESPONDENCIA_PERIODO" -> correspondencia(u, ent);
            case "INFORMES_RPA" -> informesRpa(u);
            case "AVANCE_POA" -> poa(u);
            case "ALERTAS" -> alertas(u);
            case "ESTADO_CORRESPONDENCIA" -> estadoCorrespondencia(u, ent);
            case "TRAMITES_ESTUDIANTE" -> tramitesEstudiante(u, ent);
            case "RESUMEN_ACADEMICO" -> resumenAcademico(u);
            case "EVENTOS_PROXIMOS" -> eventosProximos(u);
            case "AYUDA" -> Contenido.de("Puedo responder consultas sobre: " + String.join("; ", capacidades(u)) + ".");
            default -> Contenido.de("No entendí la consulta. Pruebe con alguna de las sugerencias o use la búsqueda avanzada.");
        };
        return new Respuesta(intencion, r.path("confianza").asDouble(), c.texto(), c.enlaces(), sugerencias(u));
    }

    // ------------------------------------------------------------------ intenciones

    private Contenido estadoTramite(UsuarioAutenticado u, JsonNode ent) {
        if (!u.tienePermiso("CONSULTAR_TRAMITES") && !u.tienePermiso("REGISTRAR_TRAMITE")) return sinPermiso("trámites");
        String numero = ent.path("numeroTramite").asText(null);
        if (numero == null) return Contenido.de("Indique el número del trámite, por ejemplo: «estado del trámite TRM-2026-0005».");
        List<Tramite> t = em.createQuery("select t from Tramite t join fetch t.tipoTramite join fetch t.estudiante e join fetch e.persona where upper(t.numero) = :n", Tramite.class)
                .setParameter("n", normalizarNumero(numero)).getResultList();
        if (t.isEmpty()) return Contenido.de("No encontré el trámite " + numero + ".");
        Tramite x = t.get(0);
        String plazo = x.estaVencido() ? "con el plazo VENCIDO desde el " + x.getFechaLimite().format(F)
                : "con fecha límite el " + x.getFechaLimite().format(F);
        return new Contenido("El trámite " + x.getNumero() + " (" + x.getTipoTramite().getNombre() + ", " + x.getEstudiante().nombreCompleto()
                + ") está en estado " + x.getEstadoActual().replace('_', ' ') + ", " + plazo + ".",
                List.of(new Enlace("Ver seguimiento de " + x.getNumero(), "/tramites/" + x.getId())));
    }

    private Contenido vencidos(UsuarioAutenticado u) {
        if (!u.tienePermiso("CONSULTAR_TRAMITES")) return sinPermiso("trámites");
        List<Tramite> lista = em.createQuery("""
                select t from Tramite t join fetch t.tipoTramite where t.fechaCierre is null and t.estadoActual <> 'ARCHIVADO'
                  and t.fechaLimite < :hoy order by t.fechaLimite""", Tramite.class).setParameter("hoy", LocalDate.now()).getResultList();
        if (lista.isEmpty()) return Contenido.de("No hay trámites con el plazo vencido.");
        String detalle = lista.stream().limit(5).map(t -> t.getNumero() + " (" + t.getTipoTramite().getNombre() + ", venció el " + t.getFechaLimite().format(F) + ")")
                .collect(Collectors.joining("; "));
        return new Contenido("Hay " + lista.size() + " trámite(s) con el plazo vencido. Los más antiguos: " + detalle + ".",
                List.of(new Enlace("Ver trámites vencidos", "/tramites?vencidos=true")));
    }

    private Contenido porEstado(UsuarioAutenticado u) {
        if (!u.tienePermiso("CONSULTAR_TRAMITES")) return sinPermiso("trámites");
        List<Object[]> filas = em.createQuery("select t.estadoActual, count(t) from Tramite t group by t.estadoActual order by count(t) desc", Object[].class).getResultList();
        String detalle = filas.stream().map(f -> f[1] + " " + f[0].toString().replace('_', ' ').toLowerCase()).collect(Collectors.joining(", "));
        long enCurso = filas.stream().filter(f -> List.of(EstadoTramite.RECEPCIONADO, EstadoTramite.EN_REVISION, EstadoTramite.OBSERVADO).contains(f[0].toString()))
                .mapToLong(f -> (Long) f[1]).sum();
        return new Contenido("Trámites por estado: " + detalle + ". En curso: " + enCurso + ".", List.of(new Enlace("Ir a trámites", "/tramites")));
    }

    private Contenido requisitos(JsonNode ent) {
        String clave = ent.path("tipoTramite").asText(null);
        List<TipoTramite> lista = tipos.findByActivoTrueOrderByNombre();
        if (clave == null) {
            return Contenido.de("Tipos de trámite disponibles: " + lista.stream().map(t -> t.getNombre() + " (" + t.getPlazoReferenciaDias() + " días)")
                    .collect(Collectors.joining("; ")) + ". Pregunte por uno en particular para ver sus requisitos.");
        }
        return lista.stream().filter(t -> sinTildes(t.getNombre()).contains(clave)).findFirst()
                .map(t -> Contenido.de("Para «" + t.getNombre() + "» se requiere: " + t.getRequisitos().stream()
                        .map(r -> r.getDescripcion() + (r.isObligatorio() ? "" : " (opcional)")).collect(Collectors.joining("; "))
                        + ". Plazo de referencia: " + t.getPlazoReferenciaDias() + " día(s)" + (t.isRequiereDictamen() ? "; requiere dictamen técnico." : ".")))
                .orElse(Contenido.de("No encontré ese tipo de trámite."));
    }

    private Contenido correspondencia(UsuarioAutenticado u, JsonNode ent) {
        if (!u.tienePermiso("CONSULTAR_CORRESPONDENCIA") && !u.tienePermiso("GESTIONAR_CORRESPONDENCIA")) return sinPermiso("correspondencia");
        LocalDate hoy = LocalDate.now();
        int mes = ent.path("mes").asInt(hoy.getMonthValue());
        int anio = ent.path("anio").asInt(mes > hoy.getMonthValue() ? hoy.getYear() - 1 : hoy.getYear());
        LocalDate desde = LocalDate.of(anio, mes, 1);
        LocalDate hasta = desde.withDayOfMonth(desde.lengthOfMonth());
        List<Correspondencia> lista = em.createQuery("select c from Correspondencia c where c.fechaDocumento between :d and :h", Correspondencia.class)
                .setParameter("d", desde).setParameter("h", hasta).getResultList();
        long recibidas = lista.stream().filter(c -> c.getTipo() == Correspondencia.Tipo.RECIBIDA).count();
        long autorizadas = lista.stream().filter(Correspondencia::isAutorizado).count();
        String nombreMes = desde.getMonth().getDisplayName(TextStyle.FULL, ES);
        return new Contenido("En " + nombreMes + " de " + anio + " se registraron " + lista.size() + " oficio(s): " + recibidas + " recibido(s) y "
                + (lista.size() - recibidas) + " emitido(s); " + autorizadas + " autorizado(s) para el informe RPA.",
                List.of(new Enlace("Ver correspondencia", "/correspondencia")));
    }

    private Contenido informesRpa(UsuarioAutenticado u) {
        if (!u.tienePermiso("CONSULTAR_RPA") && !u.tienePermiso("REVISAR_RPA") && !u.tienePermiso("AUTORIZAR_ENVIO_RPA")) return sinPermiso("resultados RPA");
        var pendientes = informes.buscar(InformeMensual.PENDIENTE_REVISION, PageRequest.of(0, 5)).getContent();
        if (pendientes.isEmpty()) return Contenido.de("No hay informes RPA pendientes de revisión.");
        List<Enlace> enlaces = new ArrayList<>();
        pendientes.forEach(i -> enlaces.add(new Enlace("Informe #" + i.getId() + " (" + i.getPeriodoDesde().format(F) + " – " + i.getPeriodoHasta().format(F) + ")",
                "/rpa/revision/" + i.getId())));
        return new Contenido("Hay " + informes.countByEstadoRevision(InformeMensual.PENDIENTE_REVISION) + " informe(s) RPA pendiente(s) de revisión.", enlaces);
    }

    private Contenido poa(UsuarioAutenticado u) {
        if (!u.tienePermiso("CONSULTAR_POA") && !u.tienePermiso("GESTIONAR_POA")) return sinPermiso("el POA");
        List<Poa> lista = poas.findAllByOrderByGestionDesc();
        if (lista.isEmpty()) return Contenido.de("No hay un POA registrado.");
        Poa p = lista.get(0);
        long retrasadas = p.getActividades().stream().filter(a -> ActividadPoa.RETRASADA.equals(a.getEstado())).count();
        return new Contenido("El POA " + p.getGestion() + " tiene un avance global de " + p.avance() + " % en " + p.getActividades().size()
                + " actividad(es); " + retrasadas + " retrasada(s).", List.of(new Enlace("Ver POA", "/poa")));
    }

    private Contenido alertas(UsuarioAutenticado u) {
        if (!u.tienePermiso("CONSULTAR_ALERTAS") && !u.tienePermiso("ATENDER_ALERTAS")) return sinPermiso("alertas");
        var r = alertas.resumen();
        return new Contenido("Hay " + r.abiertas() + " alerta(s) abierta(s): " + r.altas() + " de nivel alto y " + r.medias() + " medio; "
                + r.porPlazo() + " por plazo y " + r.porPrediccion() + " predictiva(s).", List.of(new Enlace("Ver alertas", "/alertas")));
    }

    private Contenido estadoCorrespondencia(UsuarioAutenticado u, JsonNode ent) {
        if (!u.tienePermiso("CONSULTAR_CORRESPONDENCIA") && !u.tienePermiso("GESTIONAR_CORRESPONDENCIA")
                && !u.tienePermiso("SEGUIMIENTO_CORRESPONDENCIA")) return sinPermiso("correspondencia");
        String codigo = ent.path("codigoCorrespondencia").asText(null);
        if (codigo == null) return Contenido.de("Indique el código del oficio, por ejemplo: «estado del oficio DCP-REC-2026-0012».");
        List<Correspondencia> c = em.createQuery("select c from Correspondencia c join fetch c.destinatario where upper(c.codigo) = :c", Correspondencia.class)
                .setParameter("c", normalizarNumero(codigo)).getResultList();
        if (c.isEmpty()) return Contenido.de("No encontré el oficio " + codigo + ".");
        Correspondencia x = c.get(0);
        List<SeguimientoCorrespondencia> seg = em.createQuery(
                        "select s from SeguimientoCorrespondencia s where s.idCorrespondencia = :id order by s.fecha desc, s.id desc", SeguimientoCorrespondencia.class)
                .setParameter("id", x.getId()).setMaxResults(1).getResultList();
        String ultimo = seg.isEmpty() || seg.get(0).getObservacion() == null ? ""
                : " Último seguimiento (" + seg.get(0).getFecha().toLocalDate().format(F) + "): " + seg.get(0).getObservacion() + ".";
        return new Contenido("El oficio " + x.getCodigo() + " (" + (x.getTipo() == Correspondencia.Tipo.RECIBIDA ? "recibido de " : "emitido a ")
                + x.getDestinatario().getUnidad() + ": «" + x.getAsunto() + "») está en estado " + x.getEstado().name().replace('_', ' ')
                + (x.isAutorizado() ? " y autorizado para el informe RPA." : " y sin autorizar.") + ultimo,
                List.of(new Enlace("Ir a correspondencia", "/correspondencia")));
    }

    private Contenido tramitesEstudiante(UsuarioAutenticado u, JsonNode ent) {
        if (!u.tienePermiso("CONSULTAR_TRAMITES") && !u.tienePermiso("REGISTRAR_TRAMITE")) return sinPermiso("trámites");
        String registro = ent.path("registro").asText(null);
        if (registro == null) return Contenido.de("Indique el registro del estudiante (8 dígitos), por ejemplo: «trámites del estudiante 21901222».");
        List<Tramite> lista = em.createQuery("""
                select t from Tramite t join fetch t.tipoTramite join fetch t.estudiante e join fetch e.persona
                where e.registro = :r order by t.fechaRecepcion desc""", Tramite.class).setParameter("r", registro).getResultList();
        if (lista.isEmpty()) return Contenido.de("No encontré trámites del estudiante con registro " + registro + ".");
        String detalle = lista.stream().limit(5).map(t -> t.getNumero() + " (" + t.getTipoTramite().getNombre() + ", "
                + t.getEstadoActual().replace('_', ' ').toLowerCase() + ")").collect(Collectors.joining("; "));
        List<Enlace> enlaces = lista.stream().limit(5).map(t -> new Enlace(t.getNumero(), "/tramites/" + t.getId())).toList();
        return new Contenido(lista.get(0).getEstudiante().nombreCompleto() + " (registro " + registro + ") tiene " + lista.size()
                + " trámite(s): " + detalle + (lista.size() > 5 ? "; …" : "") + ".", enlaces);
    }

    private Contenido resumenAcademico(UsuarioAutenticado u) {
        if (!u.tienePermiso("CONSULTAR_ACADEMICO") && !u.tienePermiso("GESTIONAR_PLANIFICACION")) return sinPermiso("la información académica");
        return periodos.findFirstByEstadoOrderByGestionDescSemestreDesc(PeriodoAcademico.ABIERTO)
                .map(p -> {
                    ResumenPlanificacionDto r = planificacion.resumen(p.getId());
                    return new Contenido("En el periodo " + r.periodo().nombre() + " hay " + r.grupos() + " grupo(s) de " + r.materias()
                            + " materia(s), con " + r.inscritos() + " inscrito(s) en " + r.cupoTotal() + " cupos (ocupación " + r.ocupacion()
                            + " %); " + r.gruposSinDocente() + " grupo(s) sin docente y " + r.gruposLlenos() + " lleno(s).",
                            List.of(new Enlace("Ir a gestión académica", "/academico")));
                })
                .orElse(Contenido.de("No hay un periodo académico abierto."));
    }

    private Contenido eventosProximos(UsuarioAutenticado u) {
        if (!u.tienePermiso("CONSULTAR_INSTITUCIONAL") && !u.tienePermiso("GESTIONAR_EVENTOS")
                && !u.tienePermiso("CONSULTAR_POA") && !u.tienePermiso("GESTIONAR_POA")) return sinPermiso("los eventos");
        List<Evento> lista = em.createQuery("select e from Evento e where e.fecha >= :hoy order by e.fecha", Evento.class)
                .setParameter("hoy", LocalDate.now()).setMaxResults(5).getResultList();
        if (lista.isEmpty()) return Contenido.de("No hay eventos ni talleres programados desde hoy.");
        return new Contenido("Próximos eventos: " + lista.stream().map(e -> e.getNombre() + " (" + e.getTipo().toLowerCase() + ", "
                + e.getFecha().format(F) + ")").collect(Collectors.joining("; ")) + ".", List.of(new Enlace("Ver eventos y talleres", "/eventos")));
    }

    // ------------------------------------------------------------------ apoyo

    private static Contenido sinPermiso(String tema) {
        return Contenido.de("Su rol no tiene permiso para consultar " + tema + ".");
    }

    private static List<String> capacidades(UsuarioAutenticado u) {
        List<String> c = new ArrayList<>(List.of("requisitos y plazos de los trámites"));
        if (u.tienePermiso("CONSULTAR_TRAMITES")) c.addAll(List.of("estado de un trámite", "trámites de un estudiante", "trámites vencidos", "trámites por estado"));
        if (u.tienePermiso("CONSULTAR_CORRESPONDENCIA")) c.addAll(List.of("correspondencia de un mes", "estado de un oficio"));
        if (u.tienePermiso("CONSULTAR_ACADEMICO")) c.add("grupos, cupos y docentes del periodo abierto");
        if (u.tienePermiso("CONSULTAR_INSTITUCIONAL") || u.tienePermiso("CONSULTAR_POA")) c.add("próximos eventos y talleres");
        if (u.tienePermiso("CONSULTAR_RPA")) c.add("informes RPA pendientes");
        if (u.tienePermiso("CONSULTAR_POA")) c.add("avance del POA");
        if (u.tienePermiso("CONSULTAR_ALERTAS")) c.add("alertas abiertas");
        return c;
    }

    private static List<String> sugerencias(UsuarioAutenticado u) {
        List<String> s = new ArrayList<>(List.of("¿Qué requisitos necesito para una reincorporación?"));
        if (u.tienePermiso("CONSULTAR_TRAMITES")) s.add("¿Cuántos trámites están vencidos?");
        if (u.tienePermiso("CONSULTAR_CORRESPONDENCIA")) s.add("¿Cuánta correspondencia hubo en octubre?");
        if (u.tienePermiso("CONSULTAR_POA")) s.add("¿Cómo va el avance del POA?");
        if (u.tienePermiso("CONSULTAR_RPA")) s.add("¿Hay informes RPA pendientes de revisión?");
        if (u.tienePermiso("CONSULTAR_ACADEMICO")) s.add("¿Cuántos grupos sin docente hay?");
        if (u.tienePermiso("CONSULTAR_INSTITUCIONAL")) s.add("¿Qué eventos vienen?");
        return s;
    }

    /** TRM-2026-5 -> TRM-2026-0005 y DCP-REC-2026-7 -> DCP-REC-2026-0007 (la última parte numérica va con 4 dígitos). */
    static String normalizarNumero(String numero) {
        String n = numero.toUpperCase();
        int i = n.lastIndexOf('-');
        String ultimo = n.substring(i + 1);
        return i > 0 && ultimo.matches("\\d{1,4}") ? n.substring(0, i + 1) + String.format("%04d", Integer.parseInt(ultimo)) : n;
    }

    static String sinTildes(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
