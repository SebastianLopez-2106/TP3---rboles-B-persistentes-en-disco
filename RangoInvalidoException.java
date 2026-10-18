/*
 * Tarea: 1. Excepción por rango inválido.
 */

/** No es chequeada porque invertir los límites es un error en los argumentos. */
public class RangoInvalidoException extends IllegalArgumentException {
    /** Explica qué límites se recibieron al revés. */
    public RangoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
