/*
 * Tarea: 1. Excepción por formato inválido.
 */
import java.io.IOException;

/** Es chequeada porque un archivo externo puede estar dañado y hay que atenderlo. */
public class FormatoInvalidoException extends IOException {
    /** Guarda el motivo por el que el archivo no tiene el formato esperado. */
    public FormatoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
