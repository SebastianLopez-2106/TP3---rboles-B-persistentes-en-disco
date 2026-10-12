import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class ArbolBMas implements AutoCloseable {

    // archivos de cabecera :::::::::::::::::::::::::
    private static final int MAGIC = 0x41454433;
    private static final int version = 1;
    private int P;
    private int M;
    private int L;
    private int raiz;
    private int cantPaginas = 2;
    private int niveles = 1;
    private long cantClaves = 0L;
    // archivos de cabecera :::::::::::::::::::::::::

    private long pagLeidas;
    private long pagEscritas;
    private RandomAccessFile archivo;


    /** constructor */
    private ArbolBMas ( RandomAccessFile archivo, int p, int m, int l, int raiz ) {
        this.archivo = archivo;
        P = p; M = m; L = l;
        this.raiz = raiz;
        pagEscritas = pagLeidas = 0;
    } // <-> end ArbolBMas constructor


    /** escribe la cabecera del arbol con los datos de la clase */
    private void escribirCabecera () throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate( P );

        buffer.putInt( MAGIC );
        buffer.putInt( version );
        buffer.putInt( P );
        buffer.putInt( M );
        buffer.putInt( L );
        buffer.putInt( raiz );
        buffer.putInt( cantPaginas );
        buffer.putInt( niveles );
        buffer.putLong( cantClaves );

        archivo.seek( 0 );
        archivo.write( buffer.array() );
    } // <-> end escribirCabecera method


    /** escribe una raiz vacia (solo se utiliza al crear el arbol) */
    private void escribirRaizVacia () throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate( P );

        buffer.putInt( 0 );     // nodo hoja 0
        buffer.putInt( 0 );     // cantidad de claves 0
        buffer.putInt( -1 );     // siguiente (-1, no hermano derecho)

        archivo.seek( (long) P * raiz );
        archivo.write( buffer.array() );
    } // <-> end escribirRaizVacia method


    /** crea el arbol e instancia ArbolBMas @return ArbolBMas instance */
    public static ArbolBMas crear ( String ruta, int tamPagina ) throws Exception {

        int m = (tamPagina - 4) / 12;      // 12 = 4 (direccion) + 8 (clave)
        int l = (tamPagina - 12) / 16;      // 16 = 8 (clave) + 8 (posicion o dato)

        // 1. validar P, L y M
        if ( tamPagina < 40 || m < 3 || l < 2 ) { throw new TamPaginaInvalidoException( "¡Tamaño de pagina invalido!" ); }

        // 2. crea el archivo
        RandomAccessFile archivo = new RandomAccessFile( ruta, "rw");

        try {
            archivo.setLength( 0 );     // se reinicia el archivo
            archivo.setLength( tamPagina );     // se establece la longitud del archivo 'P'

            // 3. instancia el arbol y escribe los primeros datos
            ArbolBMas arbol = new ArbolBMas ( archivo, tamPagina, m, l, 1 );
            arbol.escribirCabecera();
            arbol.escribirRaizVacia();

            return arbol;
        } catch ( Exception e) { archivo.close(); throw e;}
    } // <-> end crear method


    /** abre un arbol existente e instancia un objeto ArbolBMas con dicho arbol */
    public static ArbolBMas abrir ( String ruta ) throws Exception {

        // comprobar si existe el archivo
        File path = new File(ruta);

        if (!path.exists()) { throw new FileNotFoundException("El archivo no existe"); }

        RandomAccessFile archivo = new RandomAccessFile(ruta, "rw");

        try {

            if (archivo.length() < 40) { throw new FormatoInvalidoException("Cabecera incompleta"); }

            archivo.seek( 0 );
            int magic = archivo.readInt();
            int v = archivo.readInt();
            int p = archivo.readInt();
            int m = archivo.readInt();
            int l = archivo.readInt();
            int r = archivo.readInt();
            int cantP = archivo.readInt();
            int cantNiveles = archivo.readInt();
            long cantC = archivo.readLong();


            // validar formato
            if ( MAGIC != magic || version != v
                || p < 40 || m < 3 || l < 2
                || l != (p-12)/16 || m != (p-4)/12
                || cantP < 2
                || r < 1 || r >= cantP
                || cantNiveles < 1 || cantNiveles > cantP - 1
                || cantC < 0
                || archivo.length() != (long) cantP * p
            ) { throw new FormatoInvalidoException( "¡Formato invalido!" ); }



            // instancia el objeto de ArbolBMas que contiene el archivo
            ArbolBMas arbol = new ArbolBMas( archivo, p, m, l, r );

            arbol.cantPaginas = cantP;
            arbol.niveles = cantNiveles;
            arbol.cantClaves = cantC;

            return arbol;

        } catch ( Exception e ) { archivo.close(); throw e; } // si falla cerrar el archivo

    } // <-> end abrir method



    public boolean insertar ( long clave, long posicion ) throws Exception {

        ByteBuffer list = insertarRecursivo( clave, posicion, raiz );
        int page = list.getInt(8);

        if ( page == 0 ) { return false; }

        if ( page != -2 ) {    // si se partio la raiz, se crea una nueva
            ByteBuffer newNode = ByteBuffer.allocate( P - 12 );

            newNode.putInt( 0, raiz );
            newNode.putInt( 4, page );
            newNode.putLong( 4*M, list.getLong(0) );

            raiz = crearNodo( newNode, 1, 1, -1 );
            niveles++;
        }

        cantClaves++;
        escribirCabecera();
        return true;
    } // <-> end insertar method


    private ByteBuffer insertarRecursivo ( long clave, long posicion, int page ) throws IOException {

        ByteBuffer nodo = leerNodo( page );

        int tipo = nodo.getInt(0);
        int n = nodo.getInt(4);
        int siguiente = nodo.getInt(8);
        boolean lleno = false;

        ByteBuffer list = ByteBuffer.allocate( 8 + 4 );    // por si se divide, clave (8) + pag (4)
        list.putInt( 8, -2 );    // -2 si se inserta sin partir, 0 si ya existe, pagina nueva si se parte

        long [] claves = new long[ n + 2 ];  // claves, el +1 es para tener la primera clave vacia y que coincida con el indice de sus datos
                                            // otro +1 permite insertar antes de partir
        int [] paginas = new int[ n + 2 ];   // para nodos internos
        long [] dato = new long[ n + 1 ];    // para nodos hojas


        // 1. separar los datos (los nodos interno y las hojas se leen distinto)
        if ( tipo == 1 ) {      // si es un nodo interno o de direccionamiento ( se recorre )

            claves[0] = -1;    // esta posicion no se utiliza para comparar claves

            for ( int i = 0; i <= n; i++ ) {
                paginas[i] = nodo.getInt( 12 + 4*i );
                if ( i < n ) { claves[i+1] = nodo.getLong( 12 + 4*M + 8*i ); }
            }

            // recorrer
            int izquierda = 1;
            int derecha = n + 1;

            while ( izquierda < derecha ) {
                int medio = izquierda + (derecha - izquierda)/2;
                if ( claves[medio] <= clave ) { izquierda = medio + 1; }
                else { derecha = medio; }
            }

            int hijo = izquierda - 1;
            list = insertarRecursivo( clave, posicion, paginas[hijo] );    // llamada recursivo
            int paginaNueva = list.getInt(8);

            // el padre solo inserta si el hijo se partio
            if ( paginaNueva == 0 || paginaNueva == -2 ) { return list; }

            if ( n == M-1 ) { lleno = true; }   // si esta lleno el nodo interno

            for ( int i = n; i > hijo; i-- ) { claves[i+1] = claves[i]; }
            for ( int i = n + 1; i > hijo + 1; i-- ) { paginas[i] = paginas[i-1]; }

            claves[hijo+1] = list.getLong(0);
            paginas[hijo+1] = paginaNueva;
            n++;


        } else {                // si es uno nodo hoja

            for ( int i = 0; i < n; i++ ) {
                claves[i]   = nodo.getLong( 12 + 16*i );
                dato[i]     = nodo.getLong( 12 + 8 + 16*i );
            }

            int izquierda = 0;
            int derecha = n;

            while ( izquierda < derecha ) {
                int medio = izquierda + (derecha - izquierda)/2;
                if ( claves[medio] < clave ) { izquierda = medio + 1; }
                else { derecha = medio; }
            }

            if ( izquierda < n && clave == claves[izquierda] ) {
                list.putInt( 8, 0 );    // 0 si ya existe
                return list;
            }

            if ( n == L ) { lleno = true; }     // si esta lleno la hoja

            for ( int i = n; i > izquierda; i-- ) {
                claves[i] = claves[i-1];
                dato[i] = dato[i-1];
            }

            claves[izquierda] = clave;
            dato[izquierda] = posicion;
            n++;
        } // end if - else


        // si esta lleno
        if ( lleno ) {
            ByteBuffer thisNode = ByteBuffer.allocate( P - 12 );
            ByteBuffer newNode = ByteBuffer.allocate( P - 12 );

            int l = 0;

            if ( tipo == 0 ) { // si es una hoja se trabaja con claves[] y dato[]

                int mitad = (n + 1)/2;    // la mitad izquierda conserva el par extra si n es impar

                for ( int i = 0; i < n; i++ ) {

                    if ( l < mitad ) {   // se guardan en este nodo
                        thisNode.putLong( claves[i] ); thisNode.putLong( dato[i] );
                        l++;

                    } else {            // se guardan en el nuevo nodo (se parte)
                        newNode.putLong( claves[i] ); newNode.putLong( dato[i] );
                    }
                } // end for

                int paginaNueva = crearNodo( newNode, tipo, n - mitad, siguiente );
                escribirNodo( page, thisNode, tipo, mitad, paginaNueva );

                list.putLong( 0, claves[mitad] );
                list.putInt( 8, paginaNueva );

                return list;


            } else {    // si es un nodo interno se trabaja con claves[] y paginas[]

                int mitad = (n + 2)/2;    // cantidad de hijos que conserva el nodo original

                for ( int i = 0; i <= n; i++ ) {

                    if ( i < mitad ) { // se guarda en este nodo
                        thisNode.putInt( 4*i, paginas[i] );
                        if ( i > 0 ) { thisNode.putLong( 4*M + 8*(i-1), claves[i] ); }

                    } else {
                        int j = i - mitad;
                        newNode.putInt( 4*j, paginas[i] );
                        if ( j > 0 ) { newNode.putLong( 4*M + 8*(j-1), claves[i] ); }
                    }
                } // end for

                int paginaNueva = crearNodo( newNode, tipo, n - mitad, -1 );
                escribirNodo( page, thisNode, tipo, mitad - 1, -1 );

                list.putLong( 0, claves[mitad] );    // la clave del medio sube, no queda en los internos
                list.putInt( 8, paginaNueva );

                return list;
            }
        }


        // si hay espacio, se escribe el nodo sin partir
        ByteBuffer thisNode = ByteBuffer.allocate( P - 12 );

        if ( tipo == 0 ) {
            for ( int i = 0; i < n; i++ ) {
                thisNode.putLong( claves[i] ); thisNode.putLong( dato[i] );
            }
        } else {
            for ( int i = 0; i <= n; i++ ) {
                thisNode.putInt( 4*i, paginas[i] );
                if ( i < n ) { thisNode.putLong( 4*M + 8*i, claves[i+1] ); }
            }
        }

        escribirNodo( page, thisNode, tipo, n, siguiente );
        list.putInt( 8, -2 );
        return list;
    } // <-> end insertarRecursivo method


    private int crearNodo ( ByteBuffer buffer, int tipo, int nClaves, int siguiente ) throws IOException {

        int page = cantPaginas;
        ByteBuffer nodo = ByteBuffer.allocate( P );

        nodo.putInt( tipo );       // 1 (interno) - 0 (hoja)
        nodo.putInt( nClaves );    // cantidad de claves
        nodo.putInt( siguiente );  // numero del nodo hermano (-1 si es interno)
        nodo.put( buffer.array() );// se escriben el resto de pares clave-valor

        archivo.seek( (long) P * page );  // movemos el cursor al final de archivo
        archivo.write( nodo.array() );

        cantPaginas++;  // aumento de contador de paginas           (cabecera)
        pagEscritas++;  // aumento de contador de paginas escritas   (varible del objeto)
        return page;
    }


    private ByteBuffer leerNodo ( int page ) throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate( P );
        archivo.seek( (long) P * page );
        archivo.readFully( buffer.array() );
        pagLeidas++;
        return buffer;
    } // <-> end leerNodo method


    private void escribirNodo ( int page, ByteBuffer buffer, int tipo, int nClaves, int siguiente ) throws IOException {
        ByteBuffer nodo = ByteBuffer.allocate( P );

        nodo.putInt( tipo );       // 1 (interno) - 0 (hoja)
        nodo.putInt( nClaves );    // cantidad de claves
        nodo.putInt( siguiente );  // numero del nodo hermano (-1 si es interno)
        nodo.put( buffer.array() );// se escriben el resto de pares clave-valor

        archivo.seek( (long) P * page );
        archivo.write( nodo.array() );

        pagEscritas++;
    }


    /** @return cantidad de paginas leias */
    public long paginasLeidas() { return pagLeidas; }

    /** @return cantidad de paginas escritas */
    public long paginasEscritas() { return pagEscritas; }


    @Override
    public void close() throws Exception {
        try {
            escribirCabecera();
        } finally {
            archivo.close();
        }
    }

} // end ArbolBMas class
