package bo.edu.uagrm.psicologia.comun.auditoria;

import bo.edu.uagrm.psicologia.comun.web.Pagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CU-03 Consultar bitácora de auditoría (solo lectura: no existen endpoints de escritura). */
@RestController
@RequestMapping("/api/auditoria")
@Tag(name = "CU-03 Auditoría")
@PreAuthorize("hasAuthority('CONSULTAR_AUDITORIA')")
@Transactional(readOnly = true)
public class AuditoriaController {

    private final BitacoraAuditoriaRepository repositorio;

    public AuditoriaController(BitacoraAuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    public record RegistroAuditoriaDto(Long id, Integer idUsuario, String accion, String entidad, String idEntidad,
                                       LocalDateTime fechaHora, String direccionIp, String detalle) {
        static RegistroAuditoriaDto de(BitacoraAuditoria b) {
            return new RegistroAuditoriaDto(b.getId(), b.getIdUsuario(), b.getAccion(), b.getEntidad(),
                    b.getIdEntidad(), b.getFechaHora(), b.getDireccionIp(), b.getDetalle());
        }
    }

    @GetMapping
    @Operation(summary = "Consultar la bitácora con filtros (más reciente primero)")
    public Pagina<RegistroAuditoriaDto> consultar(
            @RequestParam(required = false) Integer idUsuario,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String idEntidad,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        Specification<BitacoraAuditoria> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (idUsuario != null) p.add(cb.equal(root.get("idUsuario"), idUsuario));
            if (accion != null && !accion.isBlank()) p.add(cb.equal(root.get("accion"), accion));
            if (entidad != null && !entidad.isBlank()) p.add(cb.equal(root.get("entidad"), entidad));
            if (idEntidad != null && !idEntidad.isBlank()) p.add(cb.equal(root.get("idEntidad"), idEntidad));
            if (desde != null) p.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), desde.atStartOfDay()));
            if (hasta != null) p.add(cb.lessThan(root.get("fechaHora"), hasta.plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(Predicate[]::new));
        };
        int tam = Math.min(Math.max(tamano, 1), 100);
        return Pagina.de(repositorio.findAll(spec,
                        PageRequest.of(Math.max(pagina, 0), tam, Sort.by(Sort.Direction.DESC, "fechaHora", "id"))),
                RegistroAuditoriaDto::de);
    }

    @GetMapping("/catalogos")
    @Operation(summary = "Acciones y entidades registradas (para los filtros)")
    public Map<String, List<String>> catalogos() {
        return Map.of("acciones", repositorio.acciones(), "entidades", repositorio.entidades());
    }
}
