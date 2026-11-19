package bo.edu.uagrm.psicologia.academico.repositorio;

import bo.edu.uagrm.psicologia.academico.modelo.Grupo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrupoRepository extends JpaRepository<Grupo, Integer> {

    java.util.List<Grupo> findByPeriodoIdOrderByMateriaPlanAscMateriaNivelAscMateriaSiglaAscCodigoAsc(Integer idPeriodo);

    boolean existsByMateriaIdAndPeriodoIdAndCodigo(Integer idMateria, Integer idPeriodo, String codigo);

    long countByPeriodoId(Integer idPeriodo);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select g from Grupo g where g.id = :id")
    java.util.Optional<Grupo> bloquear(@org.springframework.data.repository.query.Param("id") Integer id);
}
