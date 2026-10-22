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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Guarda las lineas de IMDb en disco y permite recuperarlas por su posicion. */
public class ArchivoDatos implements AutoCloseable {

    private final RandomAccessFile archivo;
    private long registrosLeidos;

    /** Recuerda el archivo abierto y empieza con el contador en cero. */
    private ArchivoDatos(RandomAccessFile archivo) {
        this.archivo = archivo;
        registrosLeidos = 0;
    }

    /** Crea un archivo vacio; si ya existia, borra su contenido anterior. */
    public static ArchivoDatos crear(String ruta) throws IOException {
        RandomAccessFile archivo = new RandomAccessFile(ruta, "rw");
        try {
            archivo.setLength(0);
            return new ArchivoDatos(archivo);
        } catch (IOException problema) {
            archivo.close();
            throw problema;
        }
    }

    /** Abre los datos existentes para leerlos sin cambiarlos. */
    public static ArchivoDatos abrir(String ruta) throws IOException {
        return new ArchivoDatos(new RandomAccessFile(ruta, "r"));
    }

    /** Agrega una linea al final y devuelve donde empieza su registro. */
    public long agregar(long clave, String linea) throws IOException {
        byte[] contenido = linea.getBytes(StandardCharsets.UTF_8);
        long posicion = archivo.length();
        archivo.seek(posicion);
        // Los enteros se escriben desde el byte más significativo al menos significativo.
        archivo.writeLong(clave);
        archivo.writeInt(contenido.length);
        archivo.write(contenido);
        return posicion;
    }

    /** Va directamente al registro indicado y devuelve su linea completa. */
    public String leer(long posicion) throws IOException {
        long tamano = archivo.length();
        if (posicion < 0 || posicion > tamano - 12) {
            throw new IOException("La posicion no contiene un registro completo: " + posicion);
        }

        archivo.seek(posicion);
        archivo.readLong(); // La clave ocupa ocho bytes antes del largo de la linea.
        int largo = archivo.readInt();
        if (largo < 0 || largo > tamano - posicion - 12) {
            throw new IOException("El largo del registro es invalido en la posicion " + posicion);
        }
        byte[] contenido = new byte[largo];
        archivo.readFully(contenido);
        registrosLeidos++;
        return new String(contenido, StandardCharsets.UTF_8);
    }

    /** Revisa las claves desde el principio hasta encontrar la buscada o terminar. */
    public long busquedaSecuencial(long clave) throws IOException {
        long posicion = 0;
        long tamano = archivo.length();
        while (posicion < tamano) {
            if (tamano - posicion < 12) {
                throw new IOException("Hay un registro incompleto en la posicion " + posicion);
            }
            archivo.seek(posicion);
            long claveActual = archivo.readLong();
            int largo = archivo.readInt();
            if (largo < 0 || largo > tamano - posicion - 12) {
                throw new IOException("El largo del registro es invalido en la posicion " + posicion);
            }
            registrosLeidos++;
            if (claveActual == clave) {
                return posicion;
            }
            // Para comparar claves no hace falta armar el texto de cada pelicula.
            posicion = posicion + 12 + largo;
        }
        return -1;
    }

    /** Dice cuantos registros se revisaron desde el ultimo reinicio. */
    public long registrosLeidos() {
        return registrosLeidos;
    }

    /** Deja en cero el contador para empezar otra medicion. */
    public void reiniciarContadores() {
        registrosLeidos = 0;
    }

    /** Devuelve cuantos bytes ocupa el archivo de datos. */
    public long tamano() throws IOException {
        return archivo.length();
    }

    /** Cierra el archivo cuando ya terminamos de usarlo. */
    @Override
    public void close() throws IOException {
        archivo.close();
    }
}
