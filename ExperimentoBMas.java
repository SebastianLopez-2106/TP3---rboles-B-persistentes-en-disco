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
import java.io.File;
import java.io.IOException;
import java.util.Random;

/** Construye los árboles y muestra las cuatro tablas pedidas en el trabajo. */
public class ExperimentoBMas {

    /**
     * Elige la medición y toma los tamaños indicados en la línea de comandos.
     * Los tamaños menores sirven para probar antes de hacer la medición completa.
     */
    public static void main(String[] argumentos) throws Exception {
        if (argumentos.length == 0) {
            mostrarUso();
            return;
        }

        if (argumentos[0].equals("sintetico")) {
            if (argumentos.length > 3) {
                mostrarUso();
                return;
            }
            int cantidad = 1000000;
            String directorio = "mediciones/sintetico";
            if (argumentos.length >= 2) {
                cantidad = Integer.parseInt(argumentos[1]);
            }
            if (argumentos.length == 3) {
                directorio = argumentos[2];
            }
            if (cantidad <= 0) {
                throw new IllegalArgumentException("La cantidad de claves debe ser positiva.");
            }
            medirEstructura(cantidad, directorio);
        } else if (argumentos[0].equals("imdb")) {
            if (argumentos.length < 2 || argumentos.length > 5) {
                mostrarUso();
                return;
            }
            int filas = 300000;
            int tamPagina = 4096;
            String directorio = "mediciones";
            if (argumentos.length >= 3) {
                filas = Integer.parseInt(argumentos[2]);
            }
            if (argumentos.length >= 4) {
                tamPagina = Integer.parseInt(argumentos[3]);
            }
            if (argumentos.length == 5) {
                directorio = argumentos[4];
            }
            if (filas <= 0) {
                throw new IllegalArgumentException("La cantidad de filas debe ser positiva.");
            }
            medirConstruccion(argumentos[1], filas, tamPagina, directorio);
        } else if (argumentos[0].equals("busquedas")) {
            if (argumentos.length > 2) {
                mostrarUso();
                return;
            }
            String directorio = "mediciones/imdb-creciente";
            if (argumentos.length == 2) {
                directorio = argumentos[1];
            }
            medirConsultas(directorio);
        } else {
            mostrarUso();
        }
    }

    /** Muestra cómo pedir cada tabla y cómo hacer primero una prueba pequeña. */
    private static void mostrarUso() {
        System.out.println("java ExperimentoBMas sintetico [claves=1000000] [directorio=mediciones/sintetico]");
        System.out.println("java ExperimentoBMas imdb ruta.tsv [filas=300000] [tamPagina=4096] [directorio=mediciones]");
        System.out.println("java ExperimentoBMas busquedas [directorio=mediciones/imdb-creciente]");
        System.out.println("Prueba pequeña: java ExperimentoBMas sintetico 1000");
        System.out.println("Prueba pequeña: java ExperimentoBMas imdb ruta.tsv 1000 128");
        System.out.println("Las tablas usan punto decimal y punto y coma para separar columnas.");
    }

    /** Prepara la carpeta donde se guardarán los archivos de la medición. */
    private static void prepararDirectorio(String ruta) throws IOException {
        File directorio = new File(ruta);
        if (!directorio.isDirectory() && !directorio.mkdirs()) {
            throw new IOException("No se pudo crear el directorio: " + ruta);
        }
    }

    /** Construye los doce árboles sintéticos y muestra su estructura en la tabla A. */
    private static void medirEstructura(int cantidad, String directorio) throws Exception {
        prepararDirectorio(directorio);
        int[] tamanos = {128, 256, 512, 1024, 2048, 4096};
        long[] mezcladas = new long[cantidad];
        for (int indice = 0; indice < cantidad; indice++) {
            mezcladas[indice] = (long) indice + 1;
        }

        // Es la misma permutación para todos los tamaños de página.
        Random azar = new Random(2026);
        for (int indice = cantidad - 1; indice > 0; indice--) {
            int elegido = azar.nextInt(indice + 1);
            long auxiliar = mezcladas[indice];
            mezcladas[indice] = mezcladas[elegido];
            mezcladas[elegido] = auxiliar;
        }

        System.out.println("Tabla A;claves=" + cantidad);
        System.out.println("P;M;L;niveles_creciente;hojas_creciente;ocupacionHojas_creciente;ocupacionInternos_creciente;niveles_aleatorio;hojas_aleatorio;ocupacionHojas_aleatorio;ocupacionInternos_aleatorio");
        for (int indiceTamano = 0; indiceTamano < tamanos.length; indiceTamano++) {
            int tamPagina = tamanos[indiceTamano];
            String fila = "";
            for (int orden = 0; orden < 2; orden++) {
                String nombreOrden = "creciente";
                if (orden == 1) {
                    nombreOrden = "aleatorio";
                }
                String ruta = new File(directorio,
                        "indice-" + tamPagina + "-" + nombreOrden + ".dat").getPath();
                ArbolBMas arbol = ArbolBMas.crear(ruta, tamPagina);
                try {
                    for (int indice = 0; indice < cantidad; indice++) {
                        long clave = (long) indice + 1;
                        if (orden == 1) {
                            clave = mezcladas[indice];
                        }
                        if (!arbol.insertar(clave, clave)) {
                            throw new IOException("Se repitió una clave en la construcción sintética.");
                        }
                    }
                    comprobarArbol(arbol);
                    EstadisticasBMas estadisticas = arbol.estadisticas();
                    if (orden == 0) {
                        fila = tamPagina + ";" + arbol.ordenInterno() + ";" + arbol.capacidadHoja();
                    }
                    fila = fila + ";" + arbol.niveles() + ";" + estadisticas.hojas
                            + ";" + estadisticas.ocupacionHojas + ";" + estadisticas.ocupacionInternos;
                } finally {
                    arbol.close();
                }
            }
            System.out.println(fila);
        }
    }

    /** Construye los dos índices de IMDb y muestra sus tamaños y costos en la tabla B. */
    private static void medirConstruccion(String rutaTsv, int filas, int tamPagina,
                                         String directorio) throws Exception {
        System.out.println("Tabla B");
        System.out.println("orden;registros;bytes_datos;bytes_indice;P;M;L;niveles;paginas;hojas;internos;ocupacionHojas;ocupacionInternos;paginas_escritas;segundos");
        for (int orden = 0; orden < 2; orden++) {
            boolean aleatorio = false;
            String nombreOrden = "creciente";
            if (orden == 1) {
                aleatorio = true;
                nombreOrden = "aleatorio";
            }
            String carpeta = new File(directorio, "imdb-" + nombreOrden).getPath();
            prepararDirectorio(carpeta);
            File archivoDatos = new File(carpeta, "datos.dat");
            File archivoIndice = new File(carpeta, "indice.dat");

            // El tiempo incluye la lectura del TSV, los datos y la construcción del índice.
            long inicio = System.nanoTime();
            long escritas = BMasApp.construir(rutaTsv, filas, tamPagina, aleatorio,
                    archivoDatos.getPath(), archivoIndice.getPath());
            long transcurrido = System.nanoTime() - inicio;

            // La verificación y las estadísticas se hacen fuera de la medición de tiempo.
            ArbolBMas arbol = ArbolBMas.abrir(archivoIndice.getPath());
            try {
                comprobarArbol(arbol);
                if (arbol.cantidadClaves() != filas) {
                    throw new IOException("La cantidad de claves no coincide con las filas solicitadas.");
                }
                EstadisticasBMas estadisticas = arbol.estadisticas();
                System.out.println(nombreOrden + ";" + filas + ";" + archivoDatos.length()
                        + ";" + archivoIndice.length() + ";" + arbol.tamPagina()
                        + ";" + arbol.ordenInterno() + ";" + arbol.capacidadHoja()
                        + ";" + arbol.niveles() + ";" + arbol.cantidadPaginas()
                        + ";" + estadisticas.hojas + ";" + estadisticas.internos
                        + ";" + estadisticas.ocupacionHojas + ";" + estadisticas.ocupacionInternos
                        + ";" + escritas + ";" + transcurrido / 1000000000.0);
            } finally {
                arbol.close();
            }
        }
    }

    /** Comprueba el árbol y detiene la medición si encuentra algo fuera de lugar. */
    private static void comprobarArbol(ArbolBMas arbol) throws Exception {
        String resultado = arbol.verificarInvariantes();
        if (!resultado.equals("OK")) {
            throw new IOException("El árbol no pasó la verificación: " + resultado);
        }
    }

    /** Revisa con búsqueda binaria si una clave ya está en el arreglo ordenado. */
    private static boolean contieneClave(long[] claves, long clave) {
        int izquierda = 0;
        int derecha = claves.length - 1;
        while (izquierda <= derecha) {
            int medio = izquierda + (derecha - izquierda) / 2;
            if (claves[medio] == clave) {
                return true;
            }
            if (claves[medio] < clave) {
                izquierda = medio + 1;
            } else {
                derecha = medio - 1;
            }
        }
        return false;
    }

    /** Junta las consultas pedidas y mide búsquedas y rangos en el índice creciente. */
    private static void medirConsultas(String directorio) throws Exception {
        ArbolBMas arbol = ArbolBMas.abrir(new File(directorio, "indice.dat").getPath());
        try {
            ArchivoDatos datos = ArchivoDatos.abrir(new File(directorio, "datos.dat").getPath());
            try {
                comprobarArbol(arbol);
                if (arbol.cantidadClaves() <= 0 || arbol.cantidadClaves() > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("La cantidad de claves no cabe en el arreglo de la medición.");
                }
                final long[] claves = new long[(int) arbol.cantidadClaves()];
                final int[] cantidadGuardada = {0};
                arbol.buscarRango(Long.MIN_VALUE, Long.MAX_VALUE, new ConsumidorEntrada() {
                    /** Guarda una clave más, sin cargar sus datos en memoria. */
                    public void aceptar(long clave, long posicion) {
                        claves[cantidadGuardada[0]] = clave;
                        cantidadGuardada[0]++;
                    }
                });
                if (cantidadGuardada[0] != claves.length) {
                    throw new IOException("El recorrido no entregó todas las claves del índice.");
                }

                long minimo = claves[0];
                long maximo = claves[claves.length - 1];
                // IMDb usa claves positivas; estas comprobaciones evitan desbordar las cuentas.
                if (minimo < 0 || maximo > Long.MAX_VALUE - 10000) {
                    throw new IllegalArgumentException("Estas mediciones esperan identificadores positivos de IMDb.");
                }
                long amplitud = maximo - minimo + 1;
                if (amplitud <= claves.length) {
                    throw new IllegalArgumentException("No hay claves inexistentes entre la primera y la última clave.");
                }

                // Este generador se mantiene hasta terminar la tabla D: nunca se reinicia.
                Random azar = new Random(2026);
                long[] existentes = new long[1000];
                long[] inexistentes = new long[1000];
                for (int indice = 0; indice < existentes.length; indice++) {
                    existentes[indice] = claves[azar.nextInt(claves.length)];
                }
                int cantidadFaltantes = 0;
                while (cantidadFaltantes < inexistentes.length) {
                    long candidata = minimo + (long) (azar.nextDouble() * amplitud);
                    if (!contieneClave(claves, candidata)) {
                        inexistentes[cantidadFaltantes] = candidata;
                        cantidadFaltantes++;
                    }
                }

                System.out.println("Tabla C");
                System.out.println("metodo;consultas;paginas_indice_por_consulta;registros_leidos_por_consulta;microsegundos_por_consulta");
                medirBusquedas("B+ existentes", arbol, datos, existentes, 1000, false);
                medirBusquedas("B+ inexistentes", arbol, datos, inexistentes, 1000, false);
                medirBusquedas("Secuencial existentes", arbol, datos, existentes, 50, true);
                medirBusquedas("Secuencial inexistentes", arbol, datos, inexistentes, 50, true);
                medirRangos(arbol, claves, azar);
            } finally {
                datos.close();
            }
        } finally {
            arbol.close();
        }
    }

    /** Mide un grupo de búsquedas y muestra sus promedios en una fila de la tabla C. */
    private static void medirBusquedas(String nombre, ArbolBMas arbol, ArchivoDatos datos,
                                      long[] consultas, int cantidad, boolean secuencial) throws Exception {
        arbol.reiniciarContadores();
        datos.reiniciarContadores();
        long inicio = System.nanoTime();
        for (int indice = 0; indice < cantidad; indice++) {
            if (secuencial) {
                // La búsqueda secuencial ya lee cada registro hasta encontrarlo o terminar.
                datos.busquedaSecuencial(consultas[indice]);
            } else {
                long posicion = arbol.buscar(consultas[indice]);
                if (posicion != -1) {
                    datos.leer(posicion);
                }
            }
        }
        long transcurrido = System.nanoTime() - inicio;
        System.out.println(nombre + ";" + cantidad
                + ";" + (double) arbol.paginasLeidas() / cantidad
                + ";" + (double) datos.registrosLeidos() / cantidad
                + ";" + transcurrido / (1000.0 * cantidad));
    }

    /** Mide cien rangos de cada ancho y muestra cuánto cuesta recorrer sus hojas. */
    private static void medirRangos(ArbolBMas arbol, long[] claves, Random azar) throws Exception {
        int[] anchos = {10, 100, 1000, 10000};
        long[] inicios = new long[100];
        ConsumidorEntrada consumidor = new ConsumidorEntrada() {
            /** Recibe el par; para esta tabla alcanza con contar lo que retorna el rango. */
            public void aceptar(long clave, long posicion) {
                // No se leen registros de datos: se mide solamente el recorrido del índice.
            }
        };

        System.out.println("Tabla D");
        System.out.println("ancho;registros_entregados_por_consulta;paginas_leidas_por_consulta;hojas_leidas_por_consulta;microsegundos_por_consulta");
        for (int indiceAncho = 0; indiceAncho < anchos.length; indiceAncho++) {
            int ancho = anchos[indiceAncho];
            for (int indice = 0; indice < inicios.length; indice++) {
                inicios[indice] = claves[azar.nextInt(claves.length)];
            }
            long entregados = 0;
            arbol.reiniciarContadores();
            long inicio = System.nanoTime();
            for (int indice = 0; indice < inicios.length; indice++) {
                entregados = entregados + arbol.buscarRango(inicios[indice],
                        inicios[indice] + ancho - 1, consumidor);
            }
            long transcurrido = System.nanoTime() - inicio;
            double paginasPorConsulta = (double) arbol.paginasLeidas() / inicios.length;
            double hojasPorConsulta = paginasPorConsulta - (arbol.niveles() - 1);
            System.out.println(ancho + ";" + (double) entregados / inicios.length
                    + ";" + paginasPorConsulta + ";" + hojasPorConsulta
                    + ";" + transcurrido / (1000.0 * inicios.length));
        }
    }
}
