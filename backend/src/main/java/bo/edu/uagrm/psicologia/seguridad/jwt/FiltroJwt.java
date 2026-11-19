package bo.edu.uagrm.psicologia.seguridad.jwt;

import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import bo.edu.uagrm.psicologia.seguridad.repositorio.UsuarioRepository;
import bo.edu.uagrm.psicologia.seguridad.servicio.AutoridadService;
import bo.edu.uagrm.psicologia.seguridad.servicio.UsuarioAutenticado;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Autentica cada petición a partir del token Bearer.
 * Los permisos se leen de la base en cada petición: si el Director cambia los permisos
 * de un rol o bloquea una cuenta, el cambio rige de inmediato sin esperar a que expire el token.
 */
@Component
public class FiltroJwt extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarios;
    private final TransactionTemplate tx;
    private final AutoridadService autoridad;

    public FiltroJwt(JwtService jwtService, UsuarioRepository usuarios, TransactionTemplate tx, AutoridadService autoridad) {
        this.jwtService = jwtService;
        this.usuarios = usuarios;
        this.tx = tx;
        this.autoridad = autoridad;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            jwtService.validar(cabecera.substring(7)).ifPresent(this::autenticar);
        }
        chain.doFilter(request, response);
    }

    private void autenticar(Integer idUsuario) {
        UsuarioAutenticado principal = tx.execute(estado -> usuarios.findById(idUsuario)
                .filter(Usuario::estaActivo)
                .map(autoridad::principalDe)   // rol y permisos efectivos (incluye autoridad delegada, CU-04)
                .orElse(null));
        if (principal == null) {
            return;
        }
        List<SimpleGrantedAuthority> autoridades = principal.permisos().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        autoridades.add(new SimpleGrantedAuthority("ROLE_" + principal.rol()));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, Set.copyOf(autoridades));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
