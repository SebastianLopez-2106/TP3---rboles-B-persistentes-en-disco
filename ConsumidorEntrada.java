/*
 * Tarea: 1. Consumidor de entradas del árbol.
 */

/** Recibe cada par encontrado, sin tener que guardar una lista de resultados. */
public interface ConsumidorEntrada {
    /** Recibe una clave y la posición de su registro para hacer algo con ellas. */
    void aceptar(long clave, long posicion);
}
