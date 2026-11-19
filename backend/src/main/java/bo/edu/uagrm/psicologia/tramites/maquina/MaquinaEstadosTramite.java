package bo.edu.uagrm.psicologia.tramites.maquina;

import bo.edu.uagrm.psicologia.comun.error.NegocioException;
import bo.edu.uagrm.psicologia.tramites.modelo.EstadoTramite;
import bo.edu.uagrm.psicologia.tramites.modelo.TransicionEstado;
import bo.edu.uagrm.psicologia.tramites.repositorio.EstadoTramiteRepository;
import bo.edu.uagrm.psicologia.tramites.repositorio.TransicionEstadoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineBuilder;
import org.springframework.statemachine.config.configurers.StateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Máquina de estados del trámite implementada con Spring State Machine.
 * <p>
 * Las transiciones permitidas se cargan desde la tabla {@code transicion_estado}; las guardas están en
 * {@link GuardasTramite}. Por cada actuación se construye una máquina posicionada en el estado actual
 * del trámite y se le envía el evento: si la máquina lo acepta, devuelve el estado destino.
 * Esta clase no persiste nada: el servicio de trámites aplica el resultado y escribe historial y auditoría.
 */
@Component
public class MaquinaEstadosTramite {

    static final String CABECERA_CONTEXTO = "contexto";

    private final Supplier<List<TransicionEstado>> fuenteTransiciones;
    private final Supplier<List<EstadoTramite>> fuenteEstados;
    private final AtomicReference<Definicion> cache = new AtomicReference<>();

    @Autowired
    public MaquinaEstadosTramite(TransicionEstadoRepository transiciones, EstadoTramiteRepository estados) {
        this(transiciones::findAll, estados::findAll);
    }

    /** Constructor para pruebas unitarias sin base de datos. */
    public MaquinaEstadosTramite(Supplier<List<TransicionEstado>> transiciones, Supplier<List<EstadoTramite>> estados) {
        this.fuenteTransiciones = transiciones;
        this.fuenteEstados = estados;
    }

    private record Definicion(List<TransicionEstado> transiciones, Set<String> estados) {
    }

    /** Resultado de una transición aceptada. */
    public record Resultado(String origen, String destino, String evento, Integer idTransicion) {
    }

    /**
     * Dispara un evento sobre un trámite que está en {@code estadoActual}.
     *
     * @throws NegocioException 409 si la transición no existe desde ese estado, 422 si una guarda la rechaza
     */
    @Transactional(readOnly = true)
    public Resultado disparar(String estadoActual, String evento, ContextoTransicion contexto) {
        Definicion def = definicion();
        Optional<TransicionEstado> transicion = def.transiciones().stream()
                .filter(t -> t.getOrigen().getNombre().equals(estadoActual) && t.getEvento().equals(evento))
                .findFirst();
        if (transicion.isEmpty()) {
            throw new NegocioException(HttpStatus.CONFLICT,
                    "La acción '" + evento + "' no está permitida para un trámite en estado " + estadoActual);
        }

        AtomicReference<String> motivoRechazo = new AtomicReference<>();
        StateMachine<String, String> maquina = construir(def, estadoActual, motivoRechazo);
        try {
            maquina.startReactively().block();
            StateMachineEventResult<String, String> r = maquina.sendEvent(Mono.just(
                    MessageBuilder.withPayload(evento).setHeader(CABECERA_CONTEXTO, contexto).build())).blockLast();
            // Spring State Machine informa ACCEPTED aunque una guarda bloquee la transición;
            // por eso también se verifica que la máquina haya llegado efectivamente al estado destino.
            String esperado = transicion.get().getDestino().getNombre();
            String alcanzado = maquina.getState() == null ? null : maquina.getState().getId();
            boolean aceptada = r != null && r.getResultType() == StateMachineEventResult.ResultType.ACCEPTED
                    && motivoRechazo.get() == null && esperado.equals(alcanzado);
            if (!aceptada) {
                throw new NegocioException(HttpStatus.UNPROCESSABLE_ENTITY,
                        Optional.ofNullable(motivoRechazo.get()).orElse("La transición fue rechazada por la máquina de estados"));
            }
            return new Resultado(estadoActual, alcanzado, evento, transicion.get().getId());
        } finally {
            maquina.stopReactively().block();
        }
    }

    /** Eventos definidos desde un estado (para mostrar las acciones posibles en la interfaz). */
    @Transactional(readOnly = true)
    public List<String> eventosDesde(String estado) {
        return definicion().transiciones().stream()
                .filter(t -> t.getOrigen().getNombre().equals(estado))
                .map(TransicionEstado::getEvento).distinct().toList();
    }

    /** Evento -> estado destino, para las transiciones que salen de un estado (en orden del catálogo). */
    @Transactional(readOnly = true)
    public java.util.Map<String, String> destinosDesde(String estado) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        definicion().transiciones().stream()
                .filter(t -> t.getOrigen().getNombre().equals(estado))
                .forEach(t -> m.putIfAbsent(t.getEvento(), t.getDestino().getNombre()));
        return m;
    }

    /** Vuelve a leer las transiciones desde la base (si el catálogo se modifica). */
    public void recargar() {
        cache.set(null);
    }

    private Definicion definicion() {
        Definicion d = cache.get();
        if (d == null) {
            List<TransicionEstado> transiciones = List.copyOf(fuenteTransiciones.get());
            Set<String> estados = new LinkedHashSet<>();
            fuenteEstados.get().forEach(e -> estados.add(e.getNombre()));
            d = new Definicion(transiciones, estados);
            cache.set(d);
        }
        return d;
    }

    private StateMachine<String, String> construir(Definicion def, String estadoInicial, AtomicReference<String> motivoRechazo) {
        if (!def.estados().contains(estadoInicial)) {
            throw new NegocioException(HttpStatus.CONFLICT, "Estado de trámite desconocido: " + estadoInicial);
        }
        try {
            StateMachineBuilder.Builder<String, String> b = StateMachineBuilder.builder();
            b.configureConfiguration().withConfiguration().autoStartup(false).machineId("tramite");
            StateConfigurer<String, String> estados = b.configureStates().withStates().initial(estadoInicial);
            estados.states(new LinkedHashSet<>(def.estados()));

            StateMachineTransitionConfigurer<String, String> t = b.configureTransitions();
            List<TransicionEstado> lista = new ArrayList<>(def.transiciones());
            for (TransicionEstado tr : lista) {
                String evento = tr.getEvento();
                t.withExternal()
                        .source(tr.getOrigen().getNombre())
                        .target(tr.getDestino().getNombre())
                        .event(evento)
                        .guard(ctx -> {
                            ContextoTransicion c = (ContextoTransicion) ctx.getMessageHeader(CABECERA_CONTEXTO);
                            Optional<String> motivo = GuardasTramite.evaluar(evento, c);
                            motivo.ifPresent(motivoRechazo::set);
                            return motivo.isEmpty();
                        });
            }
            return b.build();
        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo construir la máquina de estados del trámite", e);
        }
    }
}
