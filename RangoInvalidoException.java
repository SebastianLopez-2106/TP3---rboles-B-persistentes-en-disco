/** No chequeada: invertir los limites es un error en los argumentos del programa. */
public class RangoInvalidoException extends IllegalArgumentException {
    public RangoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
