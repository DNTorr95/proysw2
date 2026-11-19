package bo.edu.uagrm.psicologia.rpa.servicio;

import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.comun.auditoria.AuditoriaService;
import bo.edu.uagrm.psicologia.comun.config.PropiedadesApp;
import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.comun.error.RecursoNoEncontradoException;
import bo.edu.uagrm.psicologia.rpa.modelo.InformeMensual;
import bo.edu.uagrm.psicologia.rpa.modelo.NotificacionCorreo;
import bo.edu.uagrm.psicologia.rpa.repositorio.NotificacionCorreoRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Envío SMTP/TLS del informe aprobado. Es el ÚNICO punto del sistema que envía correo y exige que la
 * notificación esté AUTORIZADA por una persona. Bloquea la fila durante el envío: una notificación
 * ENVIADA nunca vuelve a enviarse.
 */
@Service
public class EnvioCorreoService {

    private static final Logger log = LoggerFactory.getLogger(EnvioCorreoService.class);

    private final NotificacionCorreoRepository correos;
    private final JavaMailSender mail;
    private final AlmacenamientoService almacenamiento;
    private final AuditoriaService auditoria;
    private final String remitente;

    public EnvioCorreoService(NotificacionCorreoRepository correos, JavaMailSender mail,
                              AlmacenamientoService almacenamiento, AuditoriaService auditoria, PropiedadesApp props) {
        this.correos = correos;
        this.mail = mail;
        this.almacenamiento = almacenamiento;
        this.auditoria = auditoria;
        this.remitente = props.correo() == null ? "no-responder@ejemplo.edu.bo" : props.correo().remitente();
    }

    /** Nombre del PDF adjunto tal como lo recibe el destinatario. */
    public static String nombreAdjunto(InformeMensual informe) {
        return "informe-correspondencia-" + informe.getPeriodoDesde().format(DateTimeFormatter.ISO_DATE)
                + "_" + informe.getPeriodoHasta().format(DateTimeFormatter.ISO_DATE) + ".pdf";
    }

    /** Envía el correo autorizado del informe. Devuelve el estado final (ENVIADO o ERROR). */
    @Transactional
    public String enviar(Integer idInforme, Integer idUsuario) {
        NotificacionCorreo n = correos.bloquearPorInforme(idInforme)
                .orElseThrow(() -> new RecursoNoEncontradoException("Correo del informe", idInforme));
        if (NotificacionCorreo.ENVIADO.equals(n.getEstado())) {
            return n.getEstado();   // idempotente: nunca se envía dos veces
        }
        if (!n.listoParaEnviar()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "El correo no está autorizado (estado " + n.getEstado() + "); no se envía sin aprobación humana");
        }
        InformeMensual informe = n.getInforme();
        try {
            byte[] pdf = almacenamiento.leer(informe.getRutaPdf());
            if (informe.getHashSha256() != null && !informe.getHashSha256().equals(AlmacenamientoService.sha256(pdf))) {
                throw new IllegalStateException("La integridad del PDF no pudo verificarse (hash distinto)");
            }
            MimeMessage m = mail.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, StandardCharsets.UTF_8.name());
            h.setFrom(remitente);
            h.setTo(n.listaDestinatarios().toArray(String[]::new));
            h.setSubject(n.getAsunto());
            h.setText(n.getCuerpo(), false);
            h.addAttachment(nombreAdjunto(informe), new ByteArrayResource(pdf), "application/pdf");
            mail.send(m);
            n.marcarEnviado();
            auditoria.registrarComo(idUsuario, "CORREO_ENVIADO", "NOTIFICACION_CORREO", n.getId(),
                    "Informe " + informe.getId() + " enviado a " + n.getDestinatarios());
            log.info("correo enviado informe={} destinatarios={}", informe.getId(), n.getDestinatarios());
        } catch (MailException | MessagingException | IllegalStateException | NegocioException e) {
            n.marcarError(e.getMessage());
            auditoria.registrarComo(idUsuario, "CORREO_ERROR", "NOTIFICACION_CORREO", n.getId(),
                    "Informe " + informe.getId() + ": " + e.getMessage());
            log.warn("No se pudo enviar el correo del informe {}: {}", informe.getId(), e.getMessage());
        }
        return n.getEstado();
    }
}
