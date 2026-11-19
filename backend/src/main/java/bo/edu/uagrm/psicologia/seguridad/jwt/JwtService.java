package bo.edu.uagrm.psicologia.seguridad.jwt;

import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.seguridad.modelo.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/** Emisión y validación de tokens JWT (HS256, expiración configurable: 8 h por defecto). */
@Service
public class JwtService {

    private static final String EMISOR = "proyecto25-psicologia-uagrm";

    private final SecretKey clave;
    private final long expiracionHoras;

    public JwtService(PropiedadesApp props) {
        byte[] bytes = props.seguridad().jwtSecreto().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.expiracionHoras = props.seguridad().jwtExpiracionHoras();
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(expiracionHoras, ChronoUnit.HOURS);
        String token = Jwts.builder()
                .issuer(EMISOR)
                .subject(String.valueOf(usuario.getId()))
                .claim("usuario", usuario.getNombreUsuario())
                .claim("rol", usuario.getRol().getNombre())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(clave)
                .compact();
        return new TokenEmitido(token, expira);
    }

    /** Devuelve el id de usuario si el token es válido, no expiró y lo emitió este sistema. */
    public Optional<Integer> validar(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).requireIssuer(EMISOR).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(Integer.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TokenEmitido(String token, Instant expira) {
    }
}
