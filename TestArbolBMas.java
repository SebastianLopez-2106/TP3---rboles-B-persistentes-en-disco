import java.io.File;
import java.io.RandomAccessFile;

/*
 * Tarea: pruebas de los doce casos del ejercicio 1.
 */

/** Prueba el árbol con las mismas claves y páginas de la consigna. */
public class TestArbolBMas {
    private static int comprobaciones;
    private static int fallas;

    /** Ejecuta los casos y deja los archivos de prueba en la carpeta indicada.
     * @param argumentos carpeta de pruebas, opcional
     * @throws Exception si falla una operación con los archivos
     */
    public static void main(String[] argumentos) throws Exception {
        File carpeta = new File("pruebas");
        if (argumentos.length > 0) {
            carpeta = new File(argumentos[0]);
        }
        if (!carpeta.isDirectory() && !carpeta.mkdirs()) {
            throw new Exception("No se pudo crear la carpeta de pruebas.");
        }
        String ruta = new File(carpeta, "traza.dat").getPath();
        long[] claves = {50, 20, 80, 10, 30, 60, 90, 40, 70, 25, 35, 45, 15};
        int[] lecturas = {1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 3};
        int[] escrituras = {1, 1, 1, 3, 1, 1, 3, 3, 1, 1, 1, 5, 3};
        int[] paginas = {2, 2, 2, 4, 4, 4, 5, 6, 6, 6, 6, 9, 10};
        String[] dibujos = {
            "1: #1[50]",
            "1: #1[20,50]",
            "1: #1[20,50,80]",
            "1: #3(50)\n2: #1[10,20] | #2[50,80]",
            "1: #3(50)\n2: #1[10,20,30] | #2[50,80]",
            "1: #3(50)\n2: #1[10,20,30] | #2[50,60,80]",
            "1: #3(50,80)\n2: #1[10,20,30] | #2[50,60] | #4[80,90]",
            "1: #3(30,50,80)\n2: #1[10,20] | #5[30,40] | #2[50,60] | #4[80,90]",
            "1: #3(30,50,80)\n2: #1[10,20] | #5[30,40] | #2[50,60,70] | #4[80,90]",
            "1: #3(30,50,80)\n2: #1[10,20,25] | #5[30,40] | #2[50,60,70] | #4[80,90]",
            "1: #3(30,50,80)\n2: #1[10,20,25] | #5[30,35,40] | #2[50,60,70] | #4[80,90]",
            "1: #8(50)\n2: #3(30,40) | #7(80)\n3: #1[10,20,25] | #5[30,35] | #6[40,45] | #2[50,60,70] | #4[80,90]",
            "1: #8(50)\n2: #3(20,30,40) | #7(80)\n3: #1[10,15] | #9[20,25] | #5[30,35] | #6[40,45] | #2[50,60,70] | #4[80,90]"
        };

        ArbolBMas arbol = ArbolBMas.crear(ruta, 60);
        try {
            System.out.println("CASO 1: árbol vacío");
            comprobar("cabecera inicial", arbol.niveles() == 1 && arbol.cantidadPaginas() == 2
                    && arbol.cantidadClaves() == 0 && arbol.raiz() == 1);
            comprobar("capacidades", arbol.tamPagina() == 60 && arbol.ordenInterno() == 4
                    && arbol.capacidadHoja() == 3);
            comprobar("contadores iniciales", arbol.paginasLeidas() == 0 && arbol.paginasEscritas() == 0);
            comprobar("vuelco vacío", arbol.volcarArbol().equals("1: #1[]"));
            comprobar("búsqueda vacía", arbol.buscar(50) == -1 && arbol.paginasLeidas() == 1);
            probarRango(arbol, 1, 10, "", 1);

            System.out.println("CASOS 2 A 6: inserciones, divisiones y crecimiento");
            for (int indice = 0; indice < claves.length; indice++) {
                int nivelesAnteriores = arbol.niveles();
                arbol.reiniciarContadores();
                boolean insertada = arbol.insertar(claves[indice], claves[indice] * 10);
                long leidas = arbol.paginasLeidas();
                long escritas = arbol.paginasEscritas();
                String dibujo = arbol.volcarArbol();
                String invariantes = arbol.verificarInvariantes();
                System.out.println("insertar(" + claves[indice] + ") R=" + leidas + " W=" + escritas
                        + " páginas=" + arbol.cantidadPaginas());
                System.out.println(dibujo);
                System.out.println("Invariantes: " + invariantes);
                comprobar("traza de " + claves[indice], insertada && leidas == lecturas[indice]
                        && leidas == nivelesAnteriores && escritas == escrituras[indice]
                        && arbol.cantidadPaginas() == paginas[indice] && dibujo.equals(dibujos[indice]));
                comprobar("invariantes de " + claves[indice], invariantes.equals("OK"));
                comprobar("diagnóstico sin cambiar contadores", arbol.paginasLeidas() == leidas
                        && arbol.paginasEscritas() == escritas);
            }
            comprobar("crecimiento a tres niveles", arbol.niveles() == 3 && arbol.raiz() == 8);

            System.out.println("CASO 7: extremos y camino de búsqueda");
            arbol.reiniciarContadores();
            comprobar("primera clave", arbol.buscar(10) == 100 && arbol.paginasLeidas() == 3);
            arbol.reiniciarContadores();
            comprobar("última clave", arbol.buscar(90) == 900 && arbol.paginasLeidas() == 3);
            arbol.reiniciarContadores();
            int[] camino = arbol.caminoBusqueda(35);
            comprobar("camino 8, 3, 5", camino.length == 3 && camino[0] == 8
                    && camino[1] == 3 && camino[2] == 5 && arbol.paginasLeidas() == 3);

            System.out.println("CASO 8: clave ausente y duplicado");
            arbol.reiniciarContadores();
            comprobar("clave ausente", arbol.buscar(33) == -1 && arbol.paginasLeidas() == 3);
            arbol.reiniciarContadores();
            comprobar("duplicado sin escrituras", !arbol.insertar(35, 1)
                    && arbol.paginasLeidas() == 3 && arbol.paginasEscritas() == 0
                    && arbol.cantidadClaves() == 13);
            comprobar("posición original del duplicado", arbol.buscar(35) == 350);

            System.out.println("CASOS 9 Y 10: rangos y parada exacta");
            probarRango(arbol, 31, 35, "35", 3);
            probarRango(arbol, 22, 62, "25 30 35 40 45 50 60", 6);
            probarRango(arbol, 20, 45, "20 25 30 35 40 45", 5);
            probarRango(arbol, 35, 35, "35", 3);
            probarRango(arbol, 91, 100, "", 3);
            probarRango(arbol, Long.MIN_VALUE, Long.MAX_VALUE,
                    "10 15 20 25 30 35 40 45 50 60 70 80 90", 8);
            arbol.reiniciarContadores();
            boolean rangoRechazado = false;
            try {
                arbol.buscarRango(9, 5, new Recolector());
            } catch (RangoInvalidoException error) {
                rangoRechazado = true;
            }
            comprobar("rango invertido", rangoRechazado && arbol.paginasLeidas() == 0);

            comprobar("formato de hoja", arbol.volcarPagina(5).equals("#5 H n=2 sig=6 [30:300 35:350]"));
            comprobar("formato de interno", arbol.volcarPagina(3).equals("#3 I n=3 [#1 20 #9 30 #5 40 #6]"));
            EstadisticasBMas estadisticas = arbol.estadisticas();
            comprobar("estadísticas", estadisticas.hojas == 6 && estadisticas.internos == 3
                    && Math.abs(estadisticas.ocupacionHojas - 13.0 / 18.0) < 0.000001
                    && Math.abs(estadisticas.ocupacionInternos - 8.0 / 12.0) < 0.000001);
            comprobar("todos los diagnósticos sin contadores", arbol.paginasLeidas() == 0
                    && arbol.paginasEscritas() == 0);
        } finally {
            arbol.close();
        }

        System.out.println("CASO 11: cierre, cabecera y archivo inválido");
        comprobar("tamaño persistido", new File(ruta).length() == 600);
        arbol = ArbolBMas.abrir(ruta);
        try {
            comprobar("reapertura sin leer nodos", arbol.paginasLeidas() == 0 && arbol.paginasEscritas() == 0);
            comprobar("cabecera persistida", arbol.raiz() == 8 && arbol.niveles() == 3
                    && arbol.cantidadPaginas() == 10 && arbol.cantidadClaves() == 13);
            System.out.println("CASO 12: consultas después de reabrir");
            comprobar("consultas persistidas", arbol.buscar(45) == 450 && arbol.buscar(15) == 150
                    && arbol.paginasLeidas() == 6);
            comprobar("invariantes al reabrir", arbol.verificarInvariantes().equals("OK"));
        } finally {
            arbol.close();
        }

        String rutaInvalida = new File(carpeta, "invalido.dat").getPath();
        RandomAccessFile archivoInvalido = new RandomAccessFile(rutaInvalida, "rw");
        try {
            archivoInvalido.setLength(4);
            archivoInvalido.writeInt(123);
        } finally {
            archivoInvalido.close();
        }
        boolean formatoRechazado = false;
        try {
            ArbolBMas invalido = ArbolBMas.abrir(rutaInvalida);
            invalido.close();
        } catch (FormatoInvalidoException error) {
            formatoRechazado = true;
        }
        comprobar("cabecera inválida", formatoRechazado);
        boolean paginaRechazada = false;
        try {
            ArbolBMas invalido = ArbolBMas.crear(rutaInvalida, 40);
            invalido.close();
        } catch (TamPaginaInvalidoException error) {
            paginaRechazada = true;
        }
        comprobar("tamaño de página inválido", paginaRechazada);
        System.out.println("Comprobaciones: " + comprobaciones + ". Fallas: " + fallas + ".");
        if (fallas > 0) {
            throw new Exception("Hay pruebas que fallaron.");
        }
    }

    /** Muestra si una comprobación dio lo esperado y lleva la cuenta.
     * @param descripcion qué se está comprobando
     * @param correcta si se obtuvo el resultado esperado
     */
    private static void comprobar(String descripcion, boolean correcta) {
        comprobaciones++;
        if (correcta) {
            System.out.println("OK: " + descripcion);
        } else {
            fallas++;
            System.out.println("FALLA: " + descripcion);
        }
    }

    /** Comprueba las claves, sus posiciones y las lecturas de un rango.
     * @param arbol árbol que se está probando
     * @param desde primera clave permitida
     * @param hasta última clave permitida
     * @param esperadas claves que deben salir, separadas por espacios
     * @param lecturasEsperadas páginas que debe leer la operación
     * @throws Exception si no se puede leer el archivo
     */
    private static void probarRango(ArbolBMas arbol, long desde, long hasta,
            String esperadas, int lecturasEsperadas) throws Exception {
        Recolector recolector = new Recolector();
        arbol.reiniciarContadores();
        int cantidad = arbol.buscarRango(desde, hasta, recolector);
        System.out.println("Rango [" + desde + ", " + hasta + "]: " + recolector.claves);
        comprobar("rango y lecturas", recolector.claves.toString().equals(esperadas)
                && cantidad == recolector.cantidad && recolector.posicionesCorrectas
                && arbol.paginasLeidas() == lecturasEsperadas && arbol.paginasEscritas() == 0);
    }

    /** Junta únicamente el pequeño resultado de un rango de prueba. */
    private static class Recolector implements ConsumidorEntrada {
        private StringBuilder claves = new StringBuilder();
        private int cantidad;
        private boolean posicionesCorrectas = true;

        /** Agrega una entrada y revisa que su posición sea diez veces la clave.
         * @param clave clave encontrada
         * @param posicion posición guardada
         */
        @Override
        public void aceptar(long clave, long posicion) {
            if (cantidad > 0) {
                claves.append(' ');
            }
            claves.append(clave);
            cantidad++;
            if (posicion != clave * 10) {
                posicionesCorrectas = false;
            }
        }
    }
}
