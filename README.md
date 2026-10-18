# Trabajo práctico 3: árbol B+ en disco

## Compilación y ejecución

Se comprobó con Java 21, sin bibliotecas externas, y también compiló con compatibilidad para Java 8. Desde esta carpeta:

```text
javac -encoding UTF-8 *.java
java TestArbolBMas
java BMasApp construir title.basics.tsv
java BMasApp buscar tt0111161
java BMasApp rango 100000 100999 10
java BMasApp secuencial 111161
java BMasApp info
java BMasApp verificar
java BMasApp pagina 345
```

Cada comando de `BMasApp` es una ejecución separada. Las consultas usan únicamente `datos.dat` e `indice.dat`. La construcción reemplaza esos archivos; `--aleatorio` mezcla solamente el orden de inserción del índice. Se usan las primeras 300.000 filas, sin encabezado. El TSV se obtiene del [sitio oficial de IMDb](https://datasets.imdbws.com/); el TSV y los `.dat` se excluyen de la entrega.

Para repetir las tablas completas:

```text
java ExperimentoBMas sintetico
java ExperimentoBMas imdb title.basics.tsv
java ExperimentoBMas busquedas
```

Los dos últimos comandos comparten los archivos de `mediciones/imdb-creciente`; la variante aleatoria queda en `mediciones/imdb-aleatorio`. Para ensayar antes: `java ExperimentoBMas sintetico 1000` y `java ExperimentoBMas imdb title.basics.tsv 1000 128`. Los tiempos dependen de la computadora y de la caché del sistema operativo. No hay caché propia de páginas.

## Decisiones y análisis

**Capacidad.** Una hoja usa `12 + 16L` bytes: `L = piso((P - 12)/16)`. Un interno usa `12 + 4M + 8(M - 1) = 12M + 4`: `M = piso((P - 4)/12)`.

| P | M | L | Bytes libres de hoja llena | Bytes libres de interno lleno |
|---:|---:|---:|---:|---:|
| 60 | 4 | 3 | 0 | 8 |
| 4096 | 341 | 255 | 4 | 0 |

La cabecera usa 40 bytes; sobran 20 y 4056 respectivamente. `M` y `L` difieren porque las hojas guardan pares de dos `long`, mientras los internos guardan hijos `int` y separadores `long`. Se conserva el formato binario original y la lógica de inserción: al dividir una hoja se copia el separador; al dividir un interno se mueve hacia el padre. Las páginas nuevas se agregan al final, de abajo hacia arriba.

**Niveles.** Para `N > 0`, con páginas llenas hacen falta al menos `1 + max(0, techo(log_M(N/L)))` niveles. Sean `a = techo(M/2)` y `b = techo(L/2)`. Un árbol con `h >= 2` necesita como mínimo `2*b*a^(h-2)` claves; por eso `h <= 2 + piso(log_a(N/(2*b)))` cuando `N >= 2*b`. Para menos claves solo es posible una raíz hoja. El árbol vacío tiene un nivel. Estas cotas respetan que la raíz interna puede tener solo dos hijos.

En A, los niveles creciente/aleatorio son P=128: 8/7; P=256: 6/6; P=512: 5/5; P=1024: 4/4; P=2048: 4/3; P=4096: 3/3. Todos respetan las cotas. En B, los dos índices tienen tres niveles, el único valor permitido por las cotas para N=300000 y P=4096. Un ABB perfectamente balanceado necesita 20 niveles para un millón de claves y 19 para 300000.

**Lecturas y escrituras.** Buscar lee exactamente `h` páginas, tanto si encuentra como si no. Insertar lee el camino anterior a la operación y escribe `2s + 1` nodos, donde `s` es la cantidad de divisiones. Un duplicado lee `h` y escribe cero. La cabecera no entra en esos contadores. Las búsquedas tienen Θ(log_M N) accesos y Θ(log N) comparaciones para `N` creciente; desplazar pares al insertar agrega trabajo dentro de la página. Se conserva solamente el camino de páginas; los diagnósticos no alteran las mediciones.

**Ocupación.** Sea `q = techo((L + 1)/2)`. Con inserción creciente, cada hoja que se divide conserva `q` pares y ya no recibe claves. Si `N <= L`, hay una hoja; si `N > L`, hay `2 + piso((N - L - 1)/q)` hojas. Para los seis tamaños de la tabla A, `L` es impar y esto equivale a `max(1, piso(N/q))`: se predicen 250000, 125000, 62500, 31250, 15625 y 7812 hojas. Su ocupación se acerca a `q/L`, algo mayor al 50 %. El azar vuelve a llenar hojas ya divididas y suele aprovechar mejor el espacio.

A coincide con las seis predicciones. Con P=128, el azar pasa de 250000 a 196962 hojas y reduce un nivel; con P=2048 también reduce un nivel. En B, ambos índices conservan tres niveles, pero el aleatorio ocupa un 23.03 % menos de bytes. Las primeras 300000 claves de esta descarga de IMDb llegan en orden numérico creciente; por eso su construcción directa deja hojas casi a la mitad.

**Secuencial e índice.** La búsqueda secuencial encuentra una clave en su posición dentro del archivo; una ausente obliga a revisar los `N` registros. Su costo medio con consultas existentes uniformes y su peor caso son Θ(N). El índice necesita Θ(log_M N) páginas y lee un registro de datos cuando encuentra la clave.

C muestra 3 páginas por consulta B+, con 1 registro leído si existe y 0 si falta. La secuencial revisó en promedio 152493.28 registros para las 50 consultas existentes y 300000 para las 50 ausentes. Compara solo clave y largo y salta el texto de las filas descartadas; cada registro inspeccionado cuenta.

**Rangos.** Si se visitan `t` hojas, se leen exactamente `h - 1 + t` páginas. Con ocupación media `u` de las hojas recorridas, aproximadamente `t = s/(uL) + O(1)` para `s` resultados, dejando aparte los extremos. En el peor caso se usa el mínimo `b` de pares por hoja: el costo es Θ(h + s/L), incluyendo el descenso para rangos vacíos. Se baja una sola vez y se sigue `siguiente`; si la última clave de una hoja alcanza el límite, no se lee otra. Hacer `s` búsquedas separadas cuesta `s*h` páginas.

En D, con ancho 10 se entregaron en promedio 9.56 registros usando 3.03 páginas; con ancho 10000, 9351.75 registros y 75.99 páginas. En este último caso, búsquedas separadas costarían en promedio 28055.25 páginas. El recorrido compartido evita repetir los internos.

## Comprobaciones realizadas

La clase de prueba imprime los doce casos, la traza completa, sus contadores y las invariantes después de cada inserción. Resultado: **70 comprobaciones, 0 fallas**. Se comprobaron además claves extremas de `long`, siete tamaños de página, orden aleatorio y diez daños de archivo: **12.767 comprobaciones, 0 fallas**. Se probaron UTF-8 y los siete comandos de la aplicación en ejecuciones separadas. Las tablas siguientes son salida del programa con los tamaños completos pedidos, sin modificar sus valores.

## Tablas medidas

Medidas el 17/10/2026 con Java 21. Los porcentajes y tiempos se redondean aquí para facilitar la lectura; los archivos de salida conservan los valores originales.

**A. Un millón de claves, semilla 2026.**

| P | M | L | Orden | Niveles | Hojas | Ocup. hojas % | Ocup. internos % |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 128 | 10 | 7 | creciente | 8 | 250000 | 57.14 | 60.00 |
| 128 | 10 | 7 | aleatorio | 7 | 196962 | 72.53 | 71.69 |
| 256 | 21 | 15 | creciente | 6 | 125000 | 53.33 | 52.39 |
| 256 | 21 | 15 | aleatorio | 6 | 94394 | 70.63 | 69.88 |
| 512 | 42 | 31 | creciente | 5 | 62500 | 51.61 | 52.40 |
| 512 | 42 | 31 | aleatorio | 5 | 46245 | 69.75 | 67.65 |
| 1024 | 85 | 63 | creciente | 4 | 31250 | 50.79 | 50.66 |
| 1024 | 85 | 63 | aleatorio | 4 | 22847 | 69.48 | 62.82 |
| 2048 | 170 | 127 | creciente | 4 | 15625 | 50.39 | 50.54 |
| 2048 | 170 | 127 | aleatorio | 3 | 11438 | 68.84 | 58.09 |
| 4096 | 341 | 255 | creciente | 3 | 7812 | 50.20 | 50.09 |
| 4096 | 341 | 255 | aleatorio | 3 | 5627 | 69.69 | 57.18 |

**B. Primeras 300000 filas reales de IMDb.**

| Medida | Creciente | Aleatorio |
| --- | --- | --- |
| Registros | 300000 | 300000 |
| Bytes de datos | 27100907 | 27100907 |
| Bytes de índice | 9658368 | 7434240 |
| P | 4096 | 4096 |
| M | 341 | 341 |
| L | 255 | 255 |
| Niveles | 3 | 3 |
| Páginas | 2358 | 1815 |
| Hojas | 2343 | 1805 |
| Internos | 14 | 9 |
| Ocupación de hojas | 50.21 % | 65.18 % |
| Ocupación de internos | 49.35 % | 59.07 % |
| Páginas escritas | 304708 | 303622 |
| Segundos | 13.577 | 13.868 |

**C. Consultas sobre el índice creciente.**

| Método | Consultas | Páginas/consulta | Registros/consulta | Microsegundos/consulta |
| --- | --- | --- | --- | --- |
| B+ existentes | 1000 | 3.00 | 1.00 | 49.20 |
| B+ inexistentes | 1000 | 3.00 | 0.00 | 20.70 |
| Secuencial existentes | 50 | 0.00 | 152493.28 | 892883.62 |
| Secuencial inexistentes | 50 | 0.00 | 300000.00 | 1766111.44 |

**D. Cien rangos por ancho, continuando el mismo generador aleatorio.**

| Ancho | Registros/consulta | Páginas/consulta | Hojas/consulta | Microsegundos/consulta |
| --- | --- | --- | --- | --- |
| 10 | 9.56 | 3.03 | 1.03 | 31.68 |
| 100 | 95.36 | 3.70 | 1.70 | 46.97 |
| 1000 | 956.33 | 10.44 | 8.44 | 175.35 |
| 10000 | 9351.75 | 75.99 | 73.99 | 643.83 |

## Uso de IA

Se utilizó Codex para completar y revisar este trabajo. Esta sección describe la asistencia efectivamente realizada; los integrantes todavía deben indicar sus propias modificaciones y poder explicar el código.

| Parte | Origen y asistencia en esta preparación |
|---|---|
| Formato, cálculo de M y L, creación y apertura | Presentes en el código recibido; revisados con IA. El cálculo también se realiza en el constructor. |
| División de hoja, interno y raíz nueva | Presentes en el código recibido; lógica conservada, nombres y explicaciones revisados con IA. |
| Buscar y camino de búsqueda | Generados con IA y comprobados contra la traza. |
| Buscar por rango | Generado con IA y comprobado con las lecturas y la parada exactas. |
| Persistencia y cierre | Base recibida conservada; firmas de error y documentación revisadas con IA. |
| Invariantes, vuelcos y estadísticas | Generados con IA, revisados y comprobados con archivos válidos y dañados. |
| Clase de prueba | Generada con IA a partir de los doce casos y sus resultados esperados. |
| ArchivoDatos y BMasApp | Generados con IA y comprobados con archivos y ejecuciones separadas. |
| ExperimentoBMas | Generado con IA; se ejecutaron las cuatro tablas y se revisó la secuencia aleatoria. |
| Análisis de este documento | Redactado con IA usando las fórmulas de los materiales y las mediciones reales. |

No se afirma que los alumnos hayan escrito o modificado manualmente estas partes. El origen manual o asistido del código inicial debe declararlo su autor. Antes de entregar, cada integrante debe revisar, adaptar y explicar su contribución.

El pedido principal solicitó completar la base existente conservando su lógica, con Java sencillo, nombres en español, Javadoc coloquial, comentarios y únicamente los métodos y algoritmos de los materiales. Se resume en español para respetar el idioma solicitado; el pedido original fue escrito en inglés. Fragmentos textuales de los pedidos usados durante la asistencia:

> Revisa de forma independiente el código original C:/Users/sebas/Desktop/TP3/ArbolBMas.java y las tres excepciones en ese directorio. Busca defectos reales de inserción, formato binario, contadores y reapertura; señala los cambios mínimos imprescindibles respetando su lógica central. No edites archivos. Todas tus respuestas deben estar en español. Próximamente te enviaré la consigna extraída.

> Usuario exige extrema sencillez, todos identificadores propios/documentación en español, Javadoc coloquial para cada método/constructor, sin lambdas/streams/colecciones java.util ni Arrays/arraycopy; Random, Scanner permitidos.

> Debe imprimir tablas A (sintetico N defecto1000000, P128,256,512,1024,2048,4096 creciente y aleatorio FisherYates2026), B imdb construcción 300000 dos órdenes, C búsquedas, D rangos con Random continuidad exacta especificada.

Cambios y decisiones de la revisión asistida: conservar las divisiones originales; cambiar los nombres propios en inglés; agregar las operaciones faltantes; usar arreglos nativos y consumidores explícitos; separar los diagnósticos de los contadores; comprobar los extremos de `long` sin sumar ni restar a las cotas; evitar listas de páginas y verificar con memoria de un camino. No se añadieron eliminación ni caché, porque son ampliaciones opcionales.

Para adjuntar el pedido inicial literalmente habría que conservar su inglés; aquí se priorizó la exigencia del solicitante de que la documentación esté en español.
