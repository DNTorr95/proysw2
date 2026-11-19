package bo.edu.uagrm.psicologia.comun.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Proyecto 25 - API de Gestión Administrativa con RPA",
                version = "1.0.0",
                description = "API REST de la Dirección de Carrera de Psicología (UAGRM). "
                        + "Autenticarse en POST /api/auth/login y usar el token en 'Authorize'."),
        security = @SecurityRequirement(name = "jwt"))
@SecurityScheme(name = "jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
