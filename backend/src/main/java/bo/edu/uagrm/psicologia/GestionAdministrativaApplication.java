package bo.edu.uagrm.psicologia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Sistema Web de Gestión Administrativa con RPA
 * Dirección de Carrera de Psicología - UAGRM (Proyecto 25).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GestionAdministrativaApplication {

    public static void main(String[] args) {
        SpringApplication.run(GestionAdministrativaApplication.class, args);
    }
}
