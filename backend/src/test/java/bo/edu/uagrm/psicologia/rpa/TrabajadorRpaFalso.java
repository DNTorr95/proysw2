package bo.edu.uagrm.psicologia.rpa;

import bo.edu.uagrm.psicologia.comun.almacenamiento.AlmacenamientoService;
import bo.edu.uagrm.psicologia.rpa.trabajador.ContratoTrabajador;
import bo.edu.uagrm.psicologia.rpa.trabajador.TrabajadorRpa;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Doble del trabajador RPA para las pruebas del backend: escribe un PDF real en el volumen y devuelve
 * un resultado con el mismo contrato que el servicio Python. El modo controla el escenario.
 */
public class TrabajadorRpaFalso implements TrabajadorRpa {

    public enum Modo { NORMAL, TIEMPO_EXCEDIDO, CAIDO, FALLA_PLANTILLA }

    public static volatile Modo modo = Modo.NORMAL;
    public static final List<ContratoTrabajador.Solicitud> SOLICITUDES = new CopyOnWriteArrayList<>();

    private final AlmacenamientoService almacenamiento;

    public TrabajadorRpaFalso(AlmacenamientoService almacenamiento) {
        this.almacenamiento = almacenamiento;
    }

    @Override
    public ContratoTrabajador.Resultado ejecutar(ContratoTrabajador.Solicitud s, Duration limite) {
        SOLICITUDES.add(s);
        switch (modo) {
            case TIEMPO_EXCEDIDO -> throw new FalloTrabajador("TIEMPO_EXCEDIDO",
                    "El trabajador RPA no respondió dentro del tiempo máximo (" + limite.toSeconds() + " s)", null);
            case CAIDO -> throw new FalloTrabajador("SERVICIO_NO_DISPONIBLE", "El trabajador RPA no está disponible", null);
            case FALLA_PLANTILLA -> {
                return new ContratoTrabajador.Resultado(s.idEjecucion(), "FALLIDA",
                        new ContratoTrabajador.Resumen(s.registros().size(), 0, 1, 0, 0), List.of(), null, null,
                        List.of(new ContratoTrabajador.Incidencia("FALLA_PLANTILLA", "La plantilla no contiene las variables requeridas: secciones")),
                        List.of(paso("PLANTILLA", "ERROR")), "La plantilla no contiene las variables requeridas: secciones");
            }
            default -> {
                // Consolida como el trabajador real: cuenta por tipo y reporta un duplicado simulado
                int recibidos = (int) s.registros().stream().filter(r -> "RECIBIDA".equals(r.tipo())).count();
                int emitidos = s.registros().size() - recibidos;
                byte[] pdf = ("%PDF-1.4\n% Informe de prueba ejecución " + s.idEjecucion() + "\n%%EOF").getBytes(StandardCharsets.US_ASCII);
                String ruta = "rpa/pruebas/informe-" + s.idEjecucion() + ".pdf";
                try {
                    Path destino = almacenamiento.directorioBase().resolve(ruta);
                    Files.createDirectories(destino.getParent());
                    Files.write(destino, pdf);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return new ContratoTrabajador.Resultado(s.idEjecucion(), "COMPLETADA",
                        new ContratoTrabajador.Resumen(s.registros().size(), s.registros().size(), 1, recibidos, emitidos),
                        List.of(new ContratoTrabajador.Detalle("TIPO", "Recibida", recibidos),
                                new ContratoTrabajador.Detalle("TIPO", "Emitida", emitidos)),
                        new ContratoTrabajador.Informe(ruta, AlmacenamientoService.sha256(pdf), pdf.length),
                        new ContratoTrabajador.Borrador("Informe de correspondencia", "Se adjunta el informe.",
                                s.destinatariosCorreo(), ruta),
                        List.of(new ContratoTrabajador.Incidencia("DUPLICADO", "DCP-REC-X: código duplicado")),
                        List.of(paso("VALIDACION", "ADVERTENCIA"), paso("PDF", "OK"), paso("BORRADOR_CORREO", "OK")), null);
            }
        }
    }

    private static ContratoTrabajador.Paso paso(String nombre, String estado) {
        return new ContratoTrabajador.Paso(nombre, nombre, estado, "2026-11-16T10:00:00.000", "2026-11-16T10:00:00.100", 100, null);
    }
}
