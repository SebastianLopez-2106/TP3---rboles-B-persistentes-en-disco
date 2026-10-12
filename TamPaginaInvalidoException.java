/** No chequeada: elegir un P que no admite nodos es un error de argumento. */
public class TamPaginaInvalidoException extends IllegalArgumentException {
    public TamPaginaInvalidoException(String mensaje) {
        super(mensaje);
    }
}
