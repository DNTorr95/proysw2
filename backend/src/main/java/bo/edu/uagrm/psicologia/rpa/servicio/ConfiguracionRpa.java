package bo.edu.uagrm.psicologia.rpa.servicio;

import bo.edu.uagrm.psicologia.rpa.modelo.EjecucionRpa;
import bo.edu.uagrm.psicologia.rpa.repositorio.EjecucionRpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;

/** Ejecutor asíncrono de RPA y recuperación de ejecuciones interrumpidas por un reinicio. */
@Configuration
@EnableAsync
public class ConfiguracionRpa {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionRpa.class);

    @Bean(name = "ejecutorRpa")
    public TaskExecutor ejecutorRpa() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(20);
        ex.setThreadNamePrefix("rpa-");
        ex.initialize();
        return ex;
    }

    /** Si el backend se reinició durante una ejecución, esta queda FALLIDA con incidencia (puede reejecutarse). */
    @EventListener(ApplicationReadyEvent.class)
    public void recuperarInterrumpidas(ApplicationReadyEvent evento) {
        EjecucionRpaRepository repo = evento.getApplicationContext().getBean(EjecucionRpaRepository.class);
        ProcesadorEjecucionRpa procesador = evento.getApplicationContext().getBean(ProcesadorEjecucionRpa.class);
        List<EjecucionRpa> enCurso = repo.findByEstadoIn(List.of(EjecucionRpa.PENDIENTE, EjecucionRpa.EN_PROCESO));
        for (EjecucionRpa e : enCurso) {
            log.warn("Ejecución RPA {} interrumpida por reinicio del sistema; se marca FALLIDA", e.getId());
            procesador.registrarFalloBackend(e.getId(), new ArrayList<>(procesador.deJson(e.getLogPasos())),
                    "ERROR_INTERNO", "Ejecución interrumpida por reinicio del sistema");
        }
    }
}
