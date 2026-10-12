import java.io.IOException;

/** Chequeada: un archivo externo puede estar danado y el llamador debe atenderlo. */
public class FormatoInvalidoException extends IOException {
    public FormatoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
