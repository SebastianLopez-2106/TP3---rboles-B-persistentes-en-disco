import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class ArbolBMas implements AutoCloseable {

    private static final int MAGIC = 0x41454433;
    private static final int version = 1;
    private int P;
    private int M;
    private int L;
    private int raiz;
    private int cantPaginas = 2;
    private int niveles = 1;
    private long cantClaves = 0L;

    private long pagLeidas;
    private long pagEscritas;
    private RandomAccessFile archivo;


    private ArbolBMas ( RandomAccessFile archivo, int p, int m, int l, int raiz ) {
        this.archivo = archivo;
        P = p; M = m; L = l;
        this.raiz = raiz;
        pagEscritas = pagLeidas = 0;
    } // <-> end ArbolBMas constructor


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


    private void escribirRaizVacia () throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate( P );

        buffer.putInt( 0 );     // nodo hoja 0
        buffer.putInt( 0 );     // cantidad de claves 0
        buffer.putInt( -1 );     // siguiente (-1, no hermano derecho)

        archivo.seek( (long) P * raiz );
        archivo.write( buffer.array() );
    } // <-> end escribirRaizVacia method


    public static ArbolBMas crear ( String ruta, int tamPagina ) throws Exception {

        int m = (tamPagina - 4) / 12;      // 12 = 4 (direccion) + 8 (clave)
        int l = (tamPagina - 12) / 16;      // 16 = 8 (clave) + 8 (posicion o dato)

        // 1. validar P, L y M
        if ( tamPagina < 40 || m < 3 || l < 2 ) { throw new TamPaginaInvalidoException( "¡Tamaño de pagina invalido!" ); }

        // 2. crea el archivo
        RandomAccessFile archivo = new RandomAccessFile( ruta, "rw");
        archivo.setLength( 0 );     // se reinicia el archivo
        archivo.setLength( tamPagina );     // se establece la longitud del archivo 'P'

        // 3. instancia el arbol y escribe los primeros datos
        ArbolBMas arbol = new ArbolBMas ( archivo, tamPagina, m, l, 1 );
        arbol.escribirCabecera();
        arbol.escribirRaizVacia();

        return arbol;
    } // <-> end crear method



    public long paginasLeidas() { return pagLeidas; }

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
