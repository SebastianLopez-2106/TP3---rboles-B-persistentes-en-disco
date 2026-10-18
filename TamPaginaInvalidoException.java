/*
 * Tarea: 1. Excepción por tamaño de página inválido.
 */

/** No es chequeada porque elegir un tamaño que no admite nodos es un error de argumento. */
public class TamPaginaInvalidoException extends IllegalArgumentException {
    /** Explica por qué el tamaño elegido no sirve para este árbol. */
    public TamPaginaInvalidoException(String mensaje) {
        super(mensaje);
    }
}
