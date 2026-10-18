/*
 * Tarea: 1. Árbol B+ persistente en disco.
 */
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/** Guarda un índice B+ en páginas de disco, sin mantener el árbol en memoria. */
public class ArbolBMas implements AutoCloseable {
    private static final int MARCA = 0x41454433;
    private static final int VERSION = 1;
    private int tamPagina;
    private int maximoHijos;
    private int maximoPares;
    private int numeroRaiz;
    private int cantPaginas = 2;
    private int cantNiveles = 1;
    private long cantClaves;
    private long pagLeidas;
    private long pagEscritas;
    private RandomAccessFile archivo;

    /** Guarda el archivo abierto y calcula lo que cabe en una página. */
    private ArbolBMas(RandomAccessFile archivo, int tamPagina, int numeroRaiz) {
        this.archivo = archivo;
        this.tamPagina = tamPagina;
        this.numeroRaiz = numeroRaiz;
        // Interno: 12 + 4*M + 8*(M-1). Hoja: 12 + 16*L.
        maximoHijos = (tamPagina - 4) / 12;
        maximoPares = (tamPagina - 12) / 16;
    }

    /** Vuelca los datos generales en la página cero, sin contarlos como E/S de nodos. */
    private void escribirCabecera() throws IOException {
        ByteBuffer contenido = ByteBuffer.allocate(tamPagina);
        contenido.putInt(MARCA);
        contenido.putInt(VERSION);
        contenido.putInt(tamPagina);
        contenido.putInt(maximoHijos);
        contenido.putInt(maximoPares);
        contenido.putInt(numeroRaiz);
        contenido.putInt(cantPaginas);
        contenido.putInt(cantNiveles);
        contenido.putLong(cantClaves);
        archivo.seek(0);
        archivo.write(contenido.array());
    }

    /** Escribe la primera hoja vacía al crear el índice. */
    private void escribirRaizVacia() throws IOException {
        ByteBuffer contenido = ByteBuffer.allocate(tamPagina);
        contenido.putInt(0);
        contenido.putInt(0);
        contenido.putInt(-1);
        archivo.seek((long) tamPagina * numeroRaiz);
        archivo.write(contenido.array());
    }

    /** Crea un índice vacío y reemplaza el archivo si ya existía. */
    public static ArbolBMas crear(String ruta, int tamPagina) throws IOException {
        int maximoHijos = (tamPagina - 4) / 12;
        int maximoPares = (tamPagina - 12) / 16;
        if (tamPagina < 40 || maximoHijos < 3 || maximoPares < 2) {
            throw new TamPaginaInvalidoException("El tamaño de página no admite los nodos mínimos.");
        }
        RandomAccessFile archivo = new RandomAccessFile(ruta, "rw");
        try {
            archivo.setLength(0);
            ArbolBMas arbol = new ArbolBMas(archivo, tamPagina, 1);
            arbol.escribirCabecera();
            arbol.escribirRaizVacia();
            return arbol;
        } catch (IOException excepcion) {
            archivo.close();
            throw excepcion;
        }
    }

    /** Abre un índice leyendo solamente su cabecera y comprobando su tamaño. */
    public static ArbolBMas abrir(String ruta) throws IOException {
        File rutaArchivo = new File(ruta);
        if (!rutaArchivo.exists()) {
            throw new FileNotFoundException("El archivo no existe: " + ruta);
        }
        RandomAccessFile archivo = new RandomAccessFile(ruta, "rw");
        try {
            if (archivo.length() < 40) {
                throw new FormatoInvalidoException("Cabecera incompleta.");
            }
            archivo.seek(0);
            int marca = archivo.readInt();
            int version = archivo.readInt();
            int tamPagina = archivo.readInt();
            int maximoHijos = archivo.readInt();
            int maximoPares = archivo.readInt();
            int numeroRaiz = archivo.readInt();
            int cantPaginas = archivo.readInt();
            int cantNiveles = archivo.readInt();
            long cantClaves = archivo.readLong();
            if (marca != MARCA || version != VERSION
                    || tamPagina < 40 || maximoHijos < 3 || maximoPares < 2
                    || maximoHijos != (tamPagina - 4) / 12
                    || maximoPares != (tamPagina - 12) / 16
                    || cantPaginas < 2 || numeroRaiz < 1 || numeroRaiz >= cantPaginas
                    || cantNiveles < 1 || cantNiveles > cantPaginas - 1 || cantClaves < 0
                    || archivo.length() != (long) cantPaginas * tamPagina) {
                throw new FormatoInvalidoException("La cabecera o el tamaño del índice no es válido.");
            }
            ArbolBMas arbol = new ArbolBMas(archivo, tamPagina, numeroRaiz);
            arbol.cantPaginas = cantPaginas;
            arbol.cantNiveles = cantNiveles;
            arbol.cantClaves = cantClaves;
            return arbol;
        } catch (IOException excepcion) {
            archivo.close();
            throw excepcion;
        }
    }

    /** Inserta un par; si la clave ya está, deja el índice tal como estaba. */
    public boolean insertar(long clave, long posicion) throws IOException {
        ByteBuffer resultado = insertarRecursivo(clave, posicion, numeroRaiz);
        int paginaNueva = resultado.getInt(8);
        if (paginaNueva == 0) {
            return false;
        }
        if (paginaNueva != -2) {
            ByteBuffer contenidoNuevo = ByteBuffer.allocate(tamPagina - 12);
            contenidoNuevo.putInt(0, numeroRaiz);
            contenidoNuevo.putInt(4, paginaNueva);
            contenidoNuevo.putLong(4 * maximoHijos, resultado.getLong(0));
            numeroRaiz = crearNodo(contenidoNuevo, 1, 1, -1);
            cantNiveles++;
        }
        cantClaves++;
        escribirCabecera();
        return true;
    }

    /** Baja hasta la hoja y va resolviendo las divisiones al volver por el camino. */
    private ByteBuffer insertarRecursivo(long clave, long posicion, int numeroPagina) throws IOException {
        ByteBuffer nodo = leerNodo(numeroPagina, true);
        int tipo = nodo.getInt(0);
        int cantidad = nodo.getInt(4);
        int siguiente = nodo.getInt(8);
        boolean lleno = false;
        // Resultado: separador en 0 y página en 8; -2 significa sin división y 0, duplicado.
        ByteBuffer resultado = ByteBuffer.allocate(12);
        resultado.putInt(8, -2);
        long[] claves = new long[cantidad + 2];
        int[] paginas = new int[cantidad + 2];
        long[] posiciones = new long[cantidad + 1];

        if (tipo == 1) {
            // En internos se dejan las claves desde el índice 1, entre sus hijos.
            for (int indice = 0; indice <= cantidad; indice++) {
                paginas[indice] = nodo.getInt(12 + 4 * indice);
                if (indice < cantidad) {
                    claves[indice + 1] = nodo.getLong(12 + 4 * maximoHijos + 8 * indice);
                }
            }
            int izquierda = 1;
            int derecha = cantidad + 1;
            while (izquierda < derecha) {
                int medio = izquierda + (derecha - izquierda) / 2;
                if (claves[medio] <= clave) {
                    izquierda = medio + 1;
                } else {
                    derecha = medio;
                }
            }
            int hijo = izquierda - 1;
            resultado = insertarRecursivo(clave, posicion, paginas[hijo]);
            int paginaNueva = resultado.getInt(8);
            if (paginaNueva == 0 || paginaNueva == -2) {
                return resultado;
            }
            lleno = cantidad == maximoHijos - 1;
            for (int indice = cantidad; indice > hijo; indice--) {
                claves[indice + 1] = claves[indice];
            }
            for (int indice = cantidad + 1; indice > hijo + 1; indice--) {
                paginas[indice] = paginas[indice - 1];
            }
            claves[hijo + 1] = resultado.getLong(0);
            paginas[hijo + 1] = paginaNueva;
            cantidad++;
        } else {
            for (int indice = 0; indice < cantidad; indice++) {
                claves[indice] = nodo.getLong(12 + 16 * indice);
                posiciones[indice] = nodo.getLong(20 + 16 * indice);
            }
            int izquierda = 0;
            int derecha = cantidad;
            while (izquierda < derecha) {
                int medio = izquierda + (derecha - izquierda) / 2;
                if (claves[medio] < clave) {
                    izquierda = medio + 1;
                } else {
                    derecha = medio;
                }
            }
            if (izquierda < cantidad && clave == claves[izquierda]) {
                resultado.putInt(8, 0);
                return resultado;
            }
            lleno = cantidad == maximoPares;
            for (int indice = cantidad; indice > izquierda; indice--) {
                claves[indice] = claves[indice - 1];
                posiciones[indice] = posiciones[indice - 1];
            }
            claves[izquierda] = clave;
            posiciones[izquierda] = posicion;
            cantidad++;
        }

        if (lleno) {
            ByteBuffer contenidoActual = ByteBuffer.allocate(tamPagina - 12);
            ByteBuffer contenidoNuevo = ByteBuffer.allocate(tamPagina - 12);
            if (tipo == 0) {
                int mitad = (cantidad + 1) / 2;
                for (int indice = 0; indice < cantidad; indice++) {
                    if (indice < mitad) {
                        contenidoActual.putLong(claves[indice]);
                        contenidoActual.putLong(posiciones[indice]);
                    } else {
                        contenidoNuevo.putLong(claves[indice]);
                        contenidoNuevo.putLong(posiciones[indice]);
                    }
                }
                // La nueva hoja hereda el siguiente; la anterior apunta a la nueva.
                int paginaNueva = crearNodo(contenidoNuevo, 0, cantidad - mitad, siguiente);
                escribirNodo(numeroPagina, contenidoActual, 0, mitad, paginaNueva);
                resultado.putLong(0, claves[mitad]);
                resultado.putInt(8, paginaNueva);
                return resultado;
            } else {
                int mitad = (cantidad + 2) / 2;
                for (int indice = 0; indice <= cantidad; indice++) {
                    if (indice < mitad) {
                        contenidoActual.putInt(4 * indice, paginas[indice]);
                        if (indice > 0) {
                            contenidoActual.putLong(4 * maximoHijos + 8 * (indice - 1), claves[indice]);
                        }
                    } else {
                        int destino = indice - mitad;
                        contenidoNuevo.putInt(4 * destino, paginas[indice]);
                        if (destino > 0) {
                            contenidoNuevo.putLong(4 * maximoHijos + 8 * (destino - 1), claves[indice]);
                        }
                    }
                }
                int paginaNueva = crearNodo(contenidoNuevo, 1, cantidad - mitad, -1);
                escribirNodo(numeroPagina, contenidoActual, 1, mitad - 1, -1);
                // El separador sale del interno y pasa al padre.
                resultado.putLong(0, claves[mitad]);
                resultado.putInt(8, paginaNueva);
                return resultado;
            }
        }

        ByteBuffer contenidoActual = ByteBuffer.allocate(tamPagina - 12);
        if (tipo == 0) {
            for (int indice = 0; indice < cantidad; indice++) {
                contenidoActual.putLong(claves[indice]);
                contenidoActual.putLong(posiciones[indice]);
            }
        } else {
            for (int indice = 0; indice <= cantidad; indice++) {
                contenidoActual.putInt(4 * indice, paginas[indice]);
                if (indice < cantidad) {
                    contenidoActual.putLong(4 * maximoHijos + 8 * indice, claves[indice + 1]);
                }
            }
        }
        escribirNodo(numeroPagina, contenidoActual, tipo, cantidad, siguiente);
        resultado.putInt(8, -2);
        return resultado;
    }

    /** Agrega una página al final y devuelve su número. */
    private int crearNodo(ByteBuffer contenido, int tipo, int cantidad, int siguiente) throws IOException {
        int numeroPagina = cantPaginas;
        escribirNodo(numeroPagina, contenido, tipo, cantidad, siguiente);
        cantPaginas++;
        return numeroPagina;
    }

    /** Lee una página entera y cuenta la lectura solamente cuando se pide. */
    private ByteBuffer leerNodo(int numeroPagina, boolean contar) throws IOException {
        if (numeroPagina < 1 || numeroPagina >= cantPaginas) {
            throw new FormatoInvalidoException("Referencia fuera del archivo: #" + numeroPagina);
        }
        ByteBuffer contenido = ByteBuffer.allocate(tamPagina);
        archivo.seek((long) tamPagina * numeroPagina);
        archivo.readFully(contenido.array());
        if (contar) {
            pagLeidas++;
        }
        int tipo = contenido.getInt(0);
        int cantidad = contenido.getInt(4);
        if ((tipo != 0 && tipo != 1) || cantidad < 0
                || (tipo == 0 && cantidad > maximoPares)
                || (tipo == 1 && cantidad >= maximoHijos)) {
            throw new FormatoInvalidoException("Tipo o cantidad de claves inválida en #" + numeroPagina);
        }
        return contenido;
    }

    /** Escribe una página completa; los lugares sin usar quedan en cero. */
    private void escribirNodo(int numeroPagina, ByteBuffer contenido, int tipo, int cantidad,
            int siguiente) throws IOException {
        ByteBuffer nodo = ByteBuffer.allocate(tamPagina);
        nodo.putInt(tipo);
        nodo.putInt(cantidad);
        nodo.putInt(siguiente);
        nodo.put(contenido.array());
        archivo.seek((long) tamPagina * numeroPagina);
        archivo.write(nodo.array());
        pagEscritas++;
    }

    /** Devuelve el hijo que corresponde a la clave, usando búsqueda binaria. */
    private int indiceHijo(ByteBuffer nodo, long clave) {
        int izquierda = 0;
        int derecha = nodo.getInt(4);
        while (izquierda < derecha) {
            int medio = izquierda + (derecha - izquierda) / 2;
            if (nodo.getLong(12 + 4 * maximoHijos + 8 * medio) <= clave) {
                izquierda = medio + 1;
            } else {
                derecha = medio;
            }
        }
        return izquierda;
    }

    /** Encuentra el primer par de la hoja cuya clave no es menor que la buscada. */
    private int indicePar(ByteBuffer hoja, long clave) {
        int izquierda = 0;
        int derecha = hoja.getInt(4);
        while (izquierda < derecha) {
            int medio = izquierda + (derecha - izquierda) / 2;
            if (hoja.getLong(12 + 16 * medio) < clave) {
                izquierda = medio + 1;
            } else {
                derecha = medio;
            }
        }
        return izquierda;
    }

    /** Busca una clave leyendo una página por nivel y devuelve su posición o -1. */
    public long buscar(long clave) throws IOException {
        int numeroPagina = numeroRaiz;
        for (int nivel = 1; nivel <= cantNiveles; nivel++) {
            ByteBuffer nodo = leerNodo(numeroPagina, true);
            if (nodo.getInt(0) == 0) {
                int indice = indicePar(nodo, clave);
                if (indice < nodo.getInt(4) && nodo.getLong(12 + 16 * indice) == clave) {
                    return nodo.getLong(20 + 16 * indice);
                }
                return -1;
            }
            numeroPagina = nodo.getInt(12 + 4 * indiceHijo(nodo, clave));
        }
        throw new FormatoInvalidoException("El camino no llega a una hoja en los niveles indicados.");
    }

    /** Devuelve los números de página del camino recorrido hasta la hoja. */
    public int[] caminoBusqueda(long clave) throws IOException {
        int[] camino = new int[cantNiveles];
        int numeroPagina = numeroRaiz;
        for (int nivel = 0; nivel < cantNiveles; nivel++) {
            camino[nivel] = numeroPagina;
            ByteBuffer nodo = leerNodo(numeroPagina, true);
            if (nodo.getInt(0) == 0) {
                if (nivel != cantNiveles - 1) {
                    throw new FormatoInvalidoException("La hoja está en un nivel distinto del indicado.");
                }
                return camino;
            }
            numeroPagina = nodo.getInt(12 + 4 * indiceHijo(nodo, clave));
        }
        throw new FormatoInvalidoException("El camino no llega a una hoja.");
    }

    /** Baja una vez y entrega en orden los pares del rango siguiendo las hojas. */
    public int buscarRango(long desde, long hasta, ConsumidorEntrada consumidor) throws IOException {
        if (desde > hasta) {
            throw new RangoInvalidoException("El límite desde es mayor que hasta.");
        }
        int numeroPagina = numeroRaiz;
        ByteBuffer nodo = leerNodo(numeroPagina, true);
        int nivel = 1;
        while (nodo.getInt(0) == 1) {
            if (nivel >= cantNiveles) {
                throw new FormatoInvalidoException("El descenso supera los niveles indicados.");
            }
            numeroPagina = nodo.getInt(12 + 4 * indiceHijo(nodo, desde));
            nodo = leerNodo(numeroPagina, true);
            nivel++;
        }
        int entregados = 0;
        int indice = indicePar(nodo, desde);
        int hojasLeidas = 1;
        while (true) {
            int cantidad = nodo.getInt(4);
            while (indice < cantidad) {
                long clave = nodo.getLong(12 + 16 * indice);
                if (clave > hasta) {
                    return entregados;
                }
                consumidor.aceptar(clave, nodo.getLong(20 + 16 * indice));
                entregados++;
                indice++;
            }
            int siguiente = nodo.getInt(8);
            // Si ya llegamos al límite, no se lee de más la próxima hoja.
            if (cantidad == 0 || nodo.getLong(12 + 16 * (cantidad - 1)) >= hasta || siguiente == -1) {
                return entregados;
            }
            hojasLeidas++;
            if (hojasLeidas >= cantPaginas) {
                throw new FormatoInvalidoException("La cadena de hojas tiene un ciclo.");
            }
            nodo = leerNodo(siguiente, true);
            if (nodo.getInt(0) != 0) {
                throw new FormatoInvalidoException("El siguiente de una hoja apunta a un interno.");
            }
            indice = 0;
        }
    }

    /** Muestra una página con el formato pedido, sin cambiar los contadores. */
    public String volcarPagina(int numeroPagina) throws IOException {
        ByteBuffer nodo = leerNodo(numeroPagina, false);
        int cantidad = nodo.getInt(4);
        StringBuilder texto = new StringBuilder();
        texto.append('#').append(numeroPagina);
        if (nodo.getInt(0) == 0) {
            texto.append(" H n=").append(cantidad).append(" sig=").append(nodo.getInt(8)).append(" [");
            for (int indice = 0; indice < cantidad; indice++) {
                if (indice > 0) {
                    texto.append(' ');
                }
                texto.append(nodo.getLong(12 + 16 * indice));
                texto.append(':').append(nodo.getLong(20 + 16 * indice));
            }
        } else {
            texto.append(" I n=").append(cantidad).append(" [#").append(nodo.getInt(12));
            for (int indice = 0; indice < cantidad; indice++) {
                texto.append(' ').append(nodo.getLong(12 + 4 * maximoHijos + 8 * indice));
                texto.append(" #").append(nodo.getInt(16 + 4 * indice));
            }
        }
        texto.append(']');
        return texto.toString();
    }

    /** Muestra el árbol de izquierda a derecha, con una línea por nivel. */
    public String volcarArbol() throws IOException {
        StringBuilder texto = new StringBuilder();
        // Se recorre un nivel por vez para no guardar una cola con todo el árbol.
        for (int nivel = 1; nivel <= cantNiveles; nivel++) {
            StringBuilder linea = new StringBuilder();
            volcarNivel(numeroRaiz, 1, nivel, linea);
            if (nivel > 1) {
                texto.append('\n');
            }
            texto.append(nivel).append(": ").append(linea);
        }
        return texto.toString();
    }

    /** Agrega al texto los nodos de un nivel, manteniendo solo el camino en memoria. */
    private void volcarNivel(int numeroPagina, int nivel, int nivelBuscado,
            StringBuilder texto) throws IOException {
        ByteBuffer nodo = leerNodo(numeroPagina, false);
        int tipo = nodo.getInt(0);
        int cantidad = nodo.getInt(4);
        if (nivel == nivelBuscado) {
            if (texto.length() > 0) {
                texto.append(" | ");
            }
            texto.append('#').append(numeroPagina);
            if (tipo == 0) {
                texto.append('[');
            } else {
                texto.append('(');
            }
            for (int indice = 0; indice < cantidad; indice++) {
                if (indice > 0) {
                    texto.append(',');
                }
                if (tipo == 0) {
                    texto.append(nodo.getLong(12 + 16 * indice));
                } else {
                    texto.append(nodo.getLong(12 + 4 * maximoHijos + 8 * indice));
                }
            }
            if (tipo == 0) {
                texto.append(']');
            } else {
                texto.append(')');
            }
        } else if (tipo == 1) {
            for (int indice = 0; indice <= cantidad; indice++) {
                volcarNivel(nodo.getInt(12 + 4 * indice), nivel + 1, nivelBuscado, texto);
            }
        }
    }

    /** Cuenta nodos y calcula sus ocupaciones sin tocar los contadores de E/S. */
    public EstadisticasBMas estadisticas() throws IOException {
        EstadisticasBMas resultado = new EstadisticasBMas();
        long[] ocupados = new long[2];
        acumularEstadisticas(numeroRaiz, 1, resultado, ocupados);
        if (resultado.hojas > 0) {
            resultado.ocupacionHojas = (double) ocupados[0] / ((long) resultado.hojas * maximoPares);
        }
        if (resultado.internos > 0) {
            resultado.ocupacionInternos = (double) ocupados[1] / ((long) resultado.internos * maximoHijos);
        }
        return resultado;
    }

    /** Suma las hojas, internos y lugares usados mientras recorre sus hijos. */
    private void acumularEstadisticas(int numeroPagina, int nivel, EstadisticasBMas resultado,
            long[] ocupados) throws IOException {
        if (nivel > cantNiveles) {
            throw new FormatoInvalidoException("El recorrido supera los niveles indicados.");
        }
        ByteBuffer nodo = leerNodo(numeroPagina, false);
        int cantidad = nodo.getInt(4);
        if (nodo.getInt(0) == 0) {
            resultado.hojas++;
            ocupados[0] += cantidad;
        } else {
            resultado.internos++;
            ocupados[1] += cantidad + 1;
            for (int indice = 0; indice <= cantidad; indice++) {
                acumularEstadisticas(nodo.getInt(12 + 4 * indice), nivel + 1, resultado, ocupados);
            }
        }
    }

    /** Revisa las reglas del árbol y devuelve OK o el primer problema encontrado. */
    public String verificarInvariantes() {
        try {
            if (archivo.length() != (long) cantPaginas * tamPagina) {
                return "El tamaño del archivo no coincide con la cabecera.";
            }
            // Aun con solo dos hijos por interno, hacen falta al menos 2^niveles páginas.
            int maximoNiveles = 0;
            int paginasDisponibles = cantPaginas;
            while (paginasDisponibles > 1) {
                maximoNiveles++;
                paginasDisponibles /= 2;
            }
            if (cantNiveles > maximoNiveles) {
                return "Hay más niveles que los posibles para la cantidad de páginas.";
            }
            // Resumen: nodos, claves, última hoja, siguiente de esa hoja y última clave.
            long[] resumen = new long[5];
            resumen[2] = -1;
            resumen[3] = -1;
            String problema = verificarNodo(numeroRaiz, 1, false, 0, false, 0, resumen);
            if (problema != null) {
                return problema;
            }
            if (resumen[3] != -1) {
                return "La última hoja no termina en -1.";
            }
            if (resumen[1] != cantClaves) {
                return "La cantidad de claves no coincide con la cabecera.";
            }
            // Las cotas de los hijos no se superponen y sus hojas no pueden estar vacías.
            // Por eso, una página repetida fallaría antes al comprobar sus claves o su nivel.
            // Si además contamos todas las páginas de nodo, tampoco puede quedar una suelta.
            if (resumen[0] != cantPaginas - 1) {
                return "Hay páginas que no son alcanzables desde la raíz.";
            }
            return "OK";
        } catch (IOException excepcion) {
            return "No se pudo verificar el índice: " + excepcion.getMessage();
        }
    }

    /** Comprueba un nodo y sus hijos, llevando las cotas y la última hoja visitada. */
    private String verificarNodo(int numeroPagina, int nivel, boolean hayMinimo, long minimo,
            boolean hayMaximo, long maximo, long[] resumen) throws IOException {
        if (nivel > cantNiveles) {
            return "Un camino supera los niveles indicados; puede haber un ciclo.";
        }
        ByteBuffer nodo = leerNodo(numeroPagina, false);
        int tipo = nodo.getInt(0);
        int cantidad = nodo.getInt(4);
        int siguiente = nodo.getInt(8);
        resumen[0]++;
        if (resumen[0] > cantPaginas - 1) {
            return "Se visitan más nodos que páginas; hay una referencia repetida.";
        }
        if (tipo == 0) {
            int minimoPares = 0;
            if (numeroPagina != numeroRaiz) {
                minimoPares = (maximoPares + 1) / 2;
            }
            if (cantidad < minimoPares) {
                return "La hoja #" + numeroPagina + " tiene pocos pares.";
            }
            if (nivel != cantNiveles) {
                return "La hoja #" + numeroPagina + " está en un nivel incorrecto.";
            }
            if (siguiente != -1 && (siguiente < 1 || siguiente >= cantPaginas)) {
                return "La hoja #" + numeroPagina + " tiene un siguiente fuera del archivo.";
            }
        } else {
            int minimoHijos = (maximoHijos + 1) / 2;
            if (numeroPagina == numeroRaiz) {
                minimoHijos = 2;
            }
            if (cantidad + 1 < minimoHijos) {
                return "El interno #" + numeroPagina + " tiene pocos hijos.";
            }
            if (siguiente != -1) {
                return "El interno #" + numeroPagina + " tiene siguiente distinto de -1.";
            }
            if (nivel >= cantNiveles) {
                return "Hay un interno en el nivel reservado para las hojas.";
            }
        }
        long anterior = 0;
        for (int indice = 0; indice < cantidad; indice++) {
            long clave;
            if (tipo == 0) {
                clave = nodo.getLong(12 + 16 * indice);
            } else {
                clave = nodo.getLong(12 + 4 * maximoHijos + 8 * indice);
            }
            if (indice > 0 && clave <= anterior) {
                return "Las claves de #" + numeroPagina + " no son estrictamente crecientes.";
            }
            // Las banderas permiten usar también Long.MIN_VALUE y Long.MAX_VALUE como claves.
            if ((hayMinimo && clave < minimo) || (hayMaximo && clave >= maximo)) {
                return "Una clave de #" + numeroPagina + " no respeta sus separadores.";
            }
            anterior = clave;
        }
        if (tipo == 0) {
            if (resumen[2] != -1) {
                if (resumen[3] != numeroPagina) {
                    return "La cadena de hojas no sigue el orden del árbol en #" + numeroPagina + ".";
                }
                if (cantidad == 0 || nodo.getLong(12) <= resumen[4]) {
                    return "Las hojas no tienen claves estrictamente crecientes entre sí.";
                }
            }
            resumen[1] += cantidad;
            resumen[2] = numeroPagina;
            resumen[3] = siguiente;
            if (cantidad > 0) {
                resumen[4] = nodo.getLong(12 + 16 * (cantidad - 1));
            }
        } else {
            for (int indice = 0; indice <= cantidad; indice++) {
                boolean hijoTieneMinimo = hayMinimo;
                long minimoHijo = minimo;
                boolean hijoTieneMaximo = hayMaximo;
                long maximoHijo = maximo;
                if (indice > 0) {
                    hijoTieneMinimo = true;
                    minimoHijo = nodo.getLong(12 + 4 * maximoHijos + 8 * (indice - 1));
                }
                if (indice < cantidad) {
                    hijoTieneMaximo = true;
                    maximoHijo = nodo.getLong(12 + 4 * maximoHijos + 8 * indice);
                }
                String problema = verificarNodo(nodo.getInt(12 + 4 * indice), nivel + 1,
                        hijoTieneMinimo, minimoHijo, hijoTieneMaximo, maximoHijo, resumen);
                if (problema != null) {
                    return problema;
                }
            }
        }
        return null;
    }

    /** Devuelve cuántos niveles tiene el árbol. */
    public int niveles() { return cantNiveles; }

    /** Devuelve el número de página de la raíz. */
    public int raiz() { return numeroRaiz; }

    /** Devuelve las páginas del archivo, incluida la cabecera. */
    public int cantidadPaginas() { return cantPaginas; }

    /** Devuelve la cantidad de claves que guarda el índice. */
    public long cantidadClaves() { return cantClaves; }

    /** Devuelve el tamaño en bytes de cada página. */
    public int tamPagina() { return tamPagina; }

    /** Devuelve el máximo de hijos que cabe en un interno. */
    public int ordenInterno() { return maximoHijos; }

    /** Devuelve el máximo de pares que cabe en una hoja. */
    public int capacidadHoja() { return maximoPares; }

    /** Devuelve las páginas de nodo leídas desde el último reinicio. */
    public long paginasLeidas() { return pagLeidas; }

    /** Devuelve las páginas de nodo escritas desde el último reinicio. */
    public long paginasEscritas() { return pagEscritas; }

    /** Pone en cero ambos contadores para comenzar una medición. */
    public void reiniciarContadores() {
        pagLeidas = 0;
        pagEscritas = 0;
    }

    /** Guarda la cabecera y cierra el archivo aunque falle esa escritura. */
    @Override
    public void close() throws IOException {
        try {
            escribirCabecera();
        } finally {
            archivo.close();
        }
    }
}
