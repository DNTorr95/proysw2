package bo.edu.uagrm.psicologia.busqueda;

import bo.edu.uagrm.psicologia.PruebaIntegracionBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Búsquedas por texto con pg_trgm (V6). Cada consulta usa la misma expresión que genera Hibernate
 * (lower(...) LIKE '%texto%'); el plan debe poder resolverla con el índice GIN de trigramas.
 * Con los pocos datos de prueba PostgreSQL prefiere leer la tabla completa, por eso se desactiva el
 * recorrido secuencial solo dentro de una transacción que luego se revierte.
 */
@EnabledIf(PruebaIntegracionBase.CONDICION)
class IndicesTrigramaIntegracionTest extends PruebaIntegracionBase {

    @Autowired
    private DataSource fuente;

    private List<String> plan(String sql) throws Exception {
        try (Connection c = fuente.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("SET LOCAL enable_seqscan = off");
                List<String> lineas = new ArrayList<>();
                try (ResultSet r = s.executeQuery("EXPLAIN " + sql)) {
                    while (r.next()) lineas.add(r.getString(1));
                }
                return lineas;
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void extensionEIndicesCreados() throws Exception {
        try (Connection c = fuente.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("select count(*) from pg_extension where extname = 'pg_trgm'")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(1);
            }
            try (ResultSet r = s.executeQuery("select count(*) from pg_indexes where indexname like 'ix_trgm_%' and indexdef like '%gin_trgm_ops%'")) {
                r.next();
                assertThat(r.getInt(1)).isEqualTo(17);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiterString = " :: ", quoteCharacter = '"', value = {
            "ix_trgm_correspondencia_asunto      :: select id_correspondencia from correspondencia c where lower(c.asunto) like '%cobertura%'",
            "ix_trgm_correspondencia_codigo      :: select id_correspondencia from correspondencia c where lower(c.codigo) like '%rec-2026%'",
            "ix_trgm_correspondencia_descripcion :: select id_correspondencia from correspondencia c where lower(coalesce(c.descripcion,'')) like '%cite%'",
            "ix_trgm_destinatario_unidad         :: select id_destinatario from destinatario d where lower(d.unidad) like '%decanato%'",
            "ix_trgm_tramite_numero              :: select id_tramite from tramite t where lower(t.numero) like '%2026-004%'",
            "ix_trgm_tramite_observaciones       :: select id_tramite from tramite t where lower(coalesce(t.observaciones,'')) like '%ficticio%'",
            "ix_trgm_persona_nombre_completo     :: select id_persona from persona p where lower((p.nombres||' '||p.apellidos)) like '%vargas%'",
            "ix_trgm_estudiante_registro         :: select e.id_persona from estudiante e where e.registro like '%2190%'",
            "ix_trgm_expediente_titulo           :: select id_expediente from expediente x where lower(x.titulo) like '%estudiante%'",
            "ix_trgm_archivo_nombre              :: select id_archivo from archivo_adjunto a where lower(a.nombre_archivo) like '%acta%'",
            "ix_trgm_archivo_descripcion         :: select id_archivo from archivo_adjunto a where lower(coalesce(a.descripcion,'')) like '%resolucion%'",
            "ix_trgm_ocr_texto                   :: select id_extraccion from extraccion_ocr e where lower(e.texto_extraido) like '%convenio%'",
            "ix_trgm_trabajo_titulo              :: select id_trabajo from trabajo_grado t where lower(t.titulo) like '%ansiedad%'",
            "ix_trgm_trabajo_palabras_clave      :: select id_trabajo from trabajo_grado t where lower(coalesce(t.palabras_clave,'')) like '%autoestima%'",
            "ix_trgm_trabajo_tutor               :: select id_trabajo from trabajo_grado t where lower(coalesce(t.tutor,'')) like '%lic%'",
    })
    void laBusquedaConLikeUsaElIndiceDeTrigramas(String indice, String sql) throws Exception {
        List<String> p = plan(sql);
        assertThat(String.join("\n", p)).as("plan de: %s", sql).contains(indice).doesNotContain("Seq Scan");
    }

    @Test
    void laBusquedaAvanzadaSigueEncontrandoLosMismosResultados() throws Exception {
        mvc.perform(get("/api/busqueda").param("texto", "ansiedad").header("Authorization", bearer(tokenCoordinador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[?(@.tipo == 'TRABAJO_GRADO')].titulo")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Ansiedad"))));
    }
}
