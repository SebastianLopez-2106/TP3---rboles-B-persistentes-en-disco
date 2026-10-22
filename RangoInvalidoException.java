/*Grupo: g24 | TP3 - U4 (Árboles B+ persistentes en disco)
**Integrantes:**
- Lopez Guerreros, Sebastian Alejandro, 6.153.672, TQ
- Oviedo Fernandez, Blas Nazario, 7.037.075, TQ
* Declaración de Honor:
 * • Nosotros Sebastian Lopez y Blas Oviedo:
 * • No hemos discutido el código fuente de nuestra tarea con ningún otro
 *   grupo, solo con el Profesor o el AER.
 * • No hemos usado código obtenido de otro estudiante o de cualquier otra
 *   fuente no autorizada, modificada o no modificada.
 * • Cualquier código o documentación utilizada en nuestro programa
 *   obtenido de fuentes, tales como libros o notas de curso, han sido claramente
 *   indicada en nuestra tarea
*/
/** No es chequeada porque invertir los límites es un error en los argumentos. */
public class RangoInvalidoException extends IllegalArgumentException {
    /** Explica qué límites se recibieron al revés. */
    public RangoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
