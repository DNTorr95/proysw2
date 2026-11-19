package bo.edu.uagrm.psicologia.comun.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Configuración propia del sistema (prefijo "app" en application.yml). */
@ConfigurationProperties(prefix = "app")
public record PropiedadesApp(Seguridad seguridad, Servicios servicios, Almacenamiento almacenamiento, Correo correo, Rpa rpa) {

    public record Seguridad(String jwtSecreto, int jwtExpiracionHoras, int maxIntentosFallidos,
                            List<String> corsOrigenes) {
    }

    public record Servicios(String clave, String rpaUrl, String iaUrl) {
    }

    public record Almacenamiento(String directorio) {
    }

    public record Correo(String remitente) {
    }

    public record Rpa(int margenTiempoSegundos) {
    }
}
