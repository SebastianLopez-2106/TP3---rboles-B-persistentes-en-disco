/*
 * Trabajo Practico 3, Unidad 4. Tarea: Ejercicio 2, BMasApp.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/** Permite construir el indice y consultarlo en ejecuciones separadas. */
public class BMasApp {

    /** Atiende un comando y muestra sus resultados o explica el problema encontrado. */
    public static void main(String[] argumentos) {
        try {
            if (argumentos.length == 0) {
                mostrarAyuda();
                return;
            }

            String comando = argumentos[0];
            if (comando.equals("construir")) {
                if (argumentos.length < 2 || argumentos.length > 5) {
                    throw new IllegalArgumentException(
                            "Uso: java BMasApp construir archivo.tsv [filas] [tamPagina] [--aleatorio]");
                }
                int filas = 300000;
                int tamPagina = 4096;
                boolean aleatorio = false;
                int numeros = 0;
                for (int indice = 2; indice < argumentos.length; indice++) {
                    if (argumentos[indice].equals("--aleatorio")) {
                        if (aleatorio) {
                            throw new IllegalArgumentException("La opcion --aleatorio esta repetida.");
                        }
                        aleatorio = true;
                    } else {
                        if (numeros == 0) {
                            filas = Integer.parseInt(argumentos[indice]);
                        } else if (numeros == 1) {
                            tamPagina = Integer.parseInt(argumentos[indice]);
                        } else {
                            throw new IllegalArgumentException("Sobran argumentos para construir.");
                        }
                        numeros++;
                    }
                }
                long inicio = System.nanoTime();
                long escritas = construir(argumentos[1], filas, tamPagina, aleatorio,
                        "datos.dat", "indice.dat");
                long duracion = System.nanoTime() - inicio;
                System.out.println("Registros: " + filas);
                System.out.println("Tamano de datos.dat: " + new File("datos.dat").length() + " bytes");
                System.out.println("Tamano de indice.dat: " + new File("indice.dat").length() + " bytes");
                System.out.println("Paginas escritas: " + escritas);
                System.out.println("Tiempo de construccion: " + duracion / 1000000000.0 + " segundos");
            } else if (comando.equals("buscar")) {
                if (argumentos.length != 2) {
                    throw new IllegalArgumentException("Uso: java BMasApp buscar clave");
                }
                long clave = convertirClave(argumentos[1]);
                ArbolBMas arbol = ArbolBMas.abrir("indice.dat");
                try {
                    ArchivoDatos datos = ArchivoDatos.abrir("datos.dat");
                    try {
                        // El camino se obtiene aparte para medir solamente buscar.
                        int[] camino = arbol.caminoBusqueda(clave);
                        arbol.reiniciarContadores();
                        long posicion = arbol.buscar(clave);
                        System.out.print("paginas leidas: " + arbol.paginasLeidas() + " (");
                        for (int indice = 0; indice < camino.length; indice++) {
                            if (indice > 0) {
                                System.out.print(" -> ");
                            }
                            System.out.print("#" + camino[indice]);
                        }
                        System.out.println(")");
                        if (posicion == -1) {
                            System.out.println("La clave no existe.");
                        } else {
                            System.out.println("pos=" + posicion + " " + datos.leer(posicion));
                        }
                    } finally {
                        datos.close();
                    }
                } finally {
                    arbol.close();
                }
            } else if (comando.equals("rango")) {
                if (argumentos.length < 3 || argumentos.length > 4) {
                    throw new IllegalArgumentException("Uso: java BMasApp rango desde hasta [mostrar]");
                }
                long desde = convertirClave(argumentos[1]);
                long hasta = convertirClave(argumentos[2]);
                int mostrar = 10;
                if (argumentos.length == 4) {
                    mostrar = Integer.parseInt(argumentos[3]);
                }
                if (mostrar < 0) {
                    throw new IllegalArgumentException("La cantidad a mostrar no puede ser negativa.");
                }
                ArbolBMas arbol = ArbolBMas.abrir("indice.dat");
                try {
                    ArchivoDatos datos = ArchivoDatos.abrir("datos.dat");
                    try {
                        MostradorRango consumidor = new MostradorRango(datos, mostrar);
                        arbol.reiniciarContadores();
                        int cantidad = arbol.buscarRango(desde, hasta, consumidor);
                        if (consumidor.problema != null) {
                            throw consumidor.problema;
                        }
                        System.out.println("Registros entregados: " + cantidad);
                        System.out.println("Registros mostrados: " + consumidor.mostrados);
                        System.out.println("Paginas leidas: " + arbol.paginasLeidas());
                    } finally {
                        datos.close();
                    }
                } finally {
                    arbol.close();
                }
            } else if (comando.equals("secuencial")) {
                if (argumentos.length != 2) {
                    throw new IllegalArgumentException("Uso: java BMasApp secuencial clave");
                }
                long clave = convertirClave(argumentos[1]);
                ArchivoDatos datos = ArchivoDatos.abrir("datos.dat");
                try {
                    datos.reiniciarContadores();
                    long posicion = datos.busquedaSecuencial(clave);
                    // Guardamos la medicion antes de leer otra vez la linea para mostrarla.
                    long revisados = datos.registrosLeidos();
                    if (posicion == -1) {
                        System.out.println("La clave no existe.");
                    } else {
                        System.out.println("pos=" + posicion + " " + datos.leer(posicion));
                    }
                    System.out.println("Registros leidos por la busqueda: " + revisados);
                } finally {
                    datos.close();
                }
            } else if (comando.equals("info") || comando.equals("verificar")
                    || comando.equals("pagina")) {
                int esperados = 1;
                if (comando.equals("pagina")) {
                    esperados = 2;
                }
                if (argumentos.length != esperados) {
                    throw new IllegalArgumentException("Argumentos incorrectos para " + comando + ".");
                }
                ArbolBMas arbol = ArbolBMas.abrir("indice.dat");
                try {
                    if (comando.equals("info")) {
                        EstadisticasBMas estadisticas = arbol.estadisticas();
                        System.out.println("P: " + arbol.tamPagina());
                        System.out.println("M: " + arbol.ordenInterno());
                        System.out.println("L: " + arbol.capacidadHoja());
                        System.out.println("Raiz: " + arbol.raiz());
                        System.out.println("Niveles: " + arbol.niveles());
                        System.out.println("Claves: " + arbol.cantidadClaves());
                        System.out.println("Paginas: " + arbol.cantidadPaginas());
                        System.out.println("Hojas: " + estadisticas.hojas);
                        System.out.println("Internos: " + estadisticas.internos);
                        System.out.println("Ocupacion de hojas: " + estadisticas.ocupacionHojas);
                        System.out.println("Ocupacion de internos: " + estadisticas.ocupacionInternos);
                    } else if (comando.equals("verificar")) {
                        System.out.println(arbol.verificarInvariantes());
                    } else {
                        System.out.println(arbol.volcarPagina(Integer.parseInt(argumentos[1])));
                    }
                } finally {
                    arbol.close();
                }
            } else {
                throw new IllegalArgumentException("Comando desconocido: " + comando);
            }
        } catch (NumberFormatException problema) {
            System.err.println("Los valores numericos deben ser enteros dentro del rango permitido.");
            System.exit(1);
        } catch (Exception problema) {
            System.err.println("No se pudo completar el comando: " + problema.getMessage());
            System.exit(1);
        }
    }

    /** Construye ambos archivos sin guardar en memoria el contenido del TSV. */
    public static long construir(String rutaTsv, int filas, int tamPagina, boolean aleatorio,
            String rutaDatos, String rutaIndice) throws Exception {
        if (filas <= 0) {
            throw new IllegalArgumentException("La cantidad de filas debe ser positiva.");
        }
        if (tamPagina < 40 || (tamPagina - 12) / 16 < 2 || (tamPagina - 4) / 12 < 3) {
            throw new TamPaginaInvalidoException("El tamano de pagina no admite una hoja y un interno.");
        }
        File fuente = new File(rutaTsv).getCanonicalFile();
        File destinoDatos = new File(rutaDatos).getCanonicalFile();
        File destinoIndice = new File(rutaIndice).getCanonicalFile();
        if (fuente.equals(destinoDatos) || fuente.equals(destinoIndice)
                || destinoDatos.equals(destinoIndice)) {
            throw new IllegalArgumentException("El TSV, los datos y el indice necesitan rutas distintas.");
        }

        // Primero revisamos solo las filas pedidas, antes de reemplazar los archivos anteriores.
        BufferedReader lector = new BufferedReader(new InputStreamReader(
                new FileInputStream(fuente), StandardCharsets.UTF_8));
        try {
            String encabezado = lector.readLine();
            if (encabezado == null || !encabezado.startsWith("tconst\t")) {
                throw new IOException("El TSV no tiene el encabezado esperado de IMDb.");
            }
            for (int numeroFila = 1; numeroFila <= filas; numeroFila++) {
                String linea = lector.readLine();
                if (linea == null) {
                    throw new IOException("Se pidieron " + filas + " filas, pero el TSV tiene solo "
                            + (numeroFila - 1) + ".");
                }
                int separador = linea.indexOf('\t');
                if (separador < 3 || !linea.startsWith("tt")) {
                    throw new IOException("La fila " + numeroFila + " no comienza con un tconst valido.");
                }
                convertirClave(linea.substring(0, separador));
            }
        } finally {
            lector.close();
        }

        long[] claves = null;
        long[] posiciones = null;
        if (aleatorio) {
            // Solo la variante aleatoria necesita recordar los pares clave-posicion.
            claves = new long[filas];
            posiciones = new long[filas];
        }
        lector = new BufferedReader(new InputStreamReader(
                new FileInputStream(fuente), StandardCharsets.UTF_8));
        try {
            ArchivoDatos datos = ArchivoDatos.crear(rutaDatos);
            try {
                ArbolBMas arbol = ArbolBMas.crear(rutaIndice, tamPagina);
                try {
                    lector.readLine(); // El encabezado no se guarda como un registro.
                    for (int indice = 0; indice < filas; indice++) {
                        String linea = lector.readLine();
                        if (linea == null) {
                            throw new IOException("El TSV cambio durante la construccion; los archivos quedaron incompletos.");
                        }
                        int separador = linea.indexOf('\t');
                        if (separador < 3 || !linea.startsWith("tt")) {
                            throw new IOException("El TSV cambio durante la construccion; hay una fila invalida.");
                        }
                        long clave = convertirClave(linea.substring(0, separador));
                        long posicion = datos.agregar(clave, linea);
                        if (aleatorio) {
                            claves[indice] = clave;
                            posiciones[indice] = posicion;
                        } else if (!arbol.insertar(clave, posicion)) {
                            throw new IOException("El TSV contiene una clave repetida: " + clave);
                        }
                    }
                    if (aleatorio) {
                        Random azar = new Random(2026);
                        // Fisher-Yates: los dos arreglos se intercambian juntos.
                        for (int indice = filas - 1; indice > 0; indice--) {
                            int elegido = azar.nextInt(indice + 1);
                            long auxiliar = claves[indice];
                            claves[indice] = claves[elegido];
                            claves[elegido] = auxiliar;
                            auxiliar = posiciones[indice];
                            posiciones[indice] = posiciones[elegido];
                            posiciones[elegido] = auxiliar;
                        }
                        for (int indice = 0; indice < filas; indice++) {
                            if (!arbol.insertar(claves[indice], posiciones[indice])) {
                                throw new IOException("El TSV contiene una clave repetida: " + claves[indice]);
                            }
                        }
                    }
                    return arbol.paginasEscritas();
                } finally {
                    arbol.close();
                }
            } finally {
                datos.close();
            }
        } finally {
            lector.close();
        }
    }

    /** Acepta tanto el identificador con tt como su numero solo. */
    private static long convertirClave(String texto) {
        if (texto.startsWith("tt")) {
            if (texto.length() == 2) {
                throw new IllegalArgumentException("Falta el numero despues de tt.");
            }
            for (int indice = 2; indice < texto.length(); indice++) {
                if (texto.charAt(indice) < '0' || texto.charAt(indice) > '9') {
                    throw new IllegalArgumentException("El identificador debe tener tt seguido de digitos.");
                }
            }
            return Long.parseLong(texto.substring(2));
        }
        return Long.parseLong(texto);
    }

    /** Recuerda los comandos disponibles cuando no se indica ninguno. */
    private static void mostrarAyuda() {
        System.out.println("java BMasApp construir archivo.tsv [filas=300000] [tamPagina=4096] [--aleatorio]");
        System.out.println("java BMasApp buscar clave");
        System.out.println("java BMasApp rango desde hasta [mostrar=10]");
        System.out.println("java BMasApp secuencial clave");
        System.out.println("java BMasApp info");
        System.out.println("java BMasApp verificar");
        System.out.println("java BMasApp pagina numero");
    }

    /** Muestra unas pocas fichas sin guardar todos los resultados del rango. */
    private static class MostradorRango implements ConsumidorEntrada {
        private final ArchivoDatos datos;
        private final int limite;
        private int mostrados;
        private IOException problema;

        /** Recuerda el archivo de datos y cuantas fichas se pueden mostrar. */
        private MostradorRango(ArchivoDatos datos, int limite) {
            this.datos = datos;
            this.limite = limite;
            mostrados = 0;
            problema = null;
        }

        /** Lee y muestra la ficha si todavia no llegamos al limite pedido. */
        @Override
        public void aceptar(long clave, long posicion) {
            if (mostrados < limite && problema == null) {
                try {
                    System.out.println("pos=" + posicion + " " + datos.leer(posicion));
                    mostrados++;
                } catch (IOException dificultad) {
                    // La interfaz no permite lanzar IOException: se informa al terminar el rango.
                    problema = dificultad;
                }
            }
        }
    }
}
