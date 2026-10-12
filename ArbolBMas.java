import java.io.File;
import java.io.FileNotFoundException;
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
