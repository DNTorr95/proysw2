package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.AsignacionDocente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsignacionDocenteRepository extends JpaRepository<AsignacionDocente, Integer> {

    java.util.List<AsignacionDocente> findByGrupoPeriodoIdOrderByDocenteCodigoDocenteAsc(Integer idPeriodo);

    java.util.List<AsignacionDocente> findByDocenteIdOrderByFechaInicioDesc(Integer idDocente);

    java.util.List<AsignacionDocente> findByGrupoIdOrderByFechaInicioAsc(Integer idGrupo);

    @org.springframework.data.jpa.repository.Query("""
            select coalesce(sum(a.horasAsignadas), 0) from AsignacionDocente a
            where a.docente.id = :idDocente and a.grupo.periodo.id = :idPeriodo
              and (a.fechaFin is null or a.fechaFin >= current_date)
            """)
    long horasVigentes(@org.springframework.data.repository.query.Param("idDocente") Integer idDocente,
                       @org.springframework.data.repository.query.Param("idPeriodo") Integer idPeriodo);
}
