Grupo: g24 | TP3 - U4 (Árboles B+ persistentes en disco)
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

### 1. Qué se hizo manualmente y qué con IA

Distribuimos las tareas entre Sebastián López y Blas Oviedo. Utilizamos IA para consultar dudas, obtener propuestas de implementación y revisar algunas partes del trabajo.

| Parte | Origen | Detalle / quién de nosotros |
|---|---|---|
| Formato de página y cálculo de M y L | *manual* | Sebastián dedujo las fórmulas a partir de los tamaños de cada campo y calculó los valores para páginas de 60 y 4096 bytes. |
| buscar y caminoBusqueda | *manual* | Blas implementó el descenso y la búsqueda dentro de cada página. Después agregó el arreglo que guarda los números de página recorridos. |
| Split de hoja | *con ayuda de IA* | Blas consultó cómo actualizar los enlaces al dividir una hoja intermedia. Implementó la división y la comprobó siguiendo la traza del enunciado. |
| Split de interno y raíz nueva | *generada por IA y modificada* | Sebastián partió de una propuesta de IA y ajustó la distribución de hijos y la asignación de páginas para respetar el orden indicado en la consigna. |
| buscarRango | *generada por IA y modificada* | Blas adaptó la propuesta para bajar una sola vez y continuar por las hojas. También ajustó la parada cuando la última clave coincide con el límite superior. |
| Persistencia (crear / abrir / close) | *con ayuda de IA* | Sebastián implementó el manejo del archivo y consultó qué validaciones convenía realizar al abrirlo. Agregó controles de cabecera y longitud. |
| verificarInvariantes | *generada por IA y modificada* | Ambos adaptamos una propuesta inicial para comprobar también la cadena de hojas y mantener separados los contadores de diagnóstico. |
| TestArbolBMas | *con ayuda de IA* | Blas organizó los doce casos del enunciado. Usó IA para revisar posibles casos faltantes y agregó comprobaciones de duplicados y límites de rangos. |
| ArchivoDatos y BMasApp | *generada por IA y modificada* | Sebastián adaptó la lectura y escritura en UTF-8 y organizó los comandos para que las consultas pudieran ejecutarse usando los archivos ya construidos. |
| ExperimentoBMas | *con ayuda de IA* | Blas organizó las mediciones y consultó dudas sobre los promedios. Revisó el uso de la semilla 2026 y la continuidad del generador entre las tablas C y D. |
| Análisis de este README | *con ayuda de IA* | Ambos redactamos las conclusiones a partir de las tablas. Usamos IA para revisar las explicaciones y después las reformulamos con nuestras palabras. |

### 2. Herramienta y prompts

**Split de hoja — Blas Oviedo**

1. “Explicame algo, si una clave sube al padre por que tambien queda en la hoja? no seria tener el mismo dato dos veces?”

2. “Con las primeras inserciones me anda, pero cuando agrego 40 se me corta el recorrido de las hojas. Te paso mi metodo, fijate si estoy pisando el enlace antes de guardarlo.”

3. “Podes revisar esta parte sin reescribir todo? quiero saber si estoy dejando la cantidad correcta de pares a cada lado cuando se llena la hoja.”

**Split de interno y raíz nueva — Sebastián López**

4. “La division de hojas ya tenemos, pero nos trabamos cuando se llena el padre. Me ayudas con una primera version del split de interno? usa arreglos normales y nada de listas.”

5. “En el codigo que me pasaste no entiendo donde cortar los hijos. Si tengo 5 hijos, quedan 3 de un lado y 2 del otro? mostrame con numeritos.”

6. “Pregunta medio basica, cuando se divide la raiz por que hay que crear otra? no puedo seguir usando la misma pagina como raiz?”

7. “Al insertar 45 me funciona la busqueda pero los numeros de pagina no coinciden con el pdf. Tendrian que salir primero la hoja 6, despues el interno 7 y al final la raiz 8. Ayudame a ubicar donde estoy creando las paginas antes de tiempo.”



**`buscarRango` — Blas Oviedo**

8. “Necesito una primera version de buscarRango usando las hojas enlazadas. Tiene que recibir desde, hasta y el consumidor que pide el enunciado. No uses ArrayList ni esas cosas.”

10. “Con el rango de 20 a 45 me devuelve bien las claves pero cuenta una pagina de mas. Puede ser que este pasando a la siguiente hoja aunque ya encontre el 45?”

11. “En tu propuesta estas guardando todos los resultados y despues los recorres. Podemos cambiar eso para llamar al consumidor a medida que aparecen? asi no tenemos que guardar todo.”


**Persistencia — Sebastián López**

12. “Si ya escribo las paginas durante insertar, para que sirve guardar otra vez la cabecera en close? no estaria haciendo lo mismo dos veces?”

13. “Ya hice abrir leyendo la cabecera, pero estoy comprobando solo la marca. Que otros datos tendria que validar segun la consigna? no quiero abrir un archivo cualquiera por accidente.”

14. “Cierro el programa y cuando lo vuelvo a ejecutar me aparece vacio. Puede ser que este llamando crear en vez de abrir? te paso el inicio del programa.”


**`verificarInvariantes` — ambos integrantes**

15. “Nos falta el verificador. Podes proponer una base recursiva que revise orden, ocupacion y niveles? acordate que no podemos cargar todo el arbol en memoria.”

16. “Si todas las claves estan ordenadas ya significa que el arbol esta bien? o puede estar ordenado y tener algun hijo apuntando donde no corresponde?”

17. “Probamos con Long.MIN_VALUE y nos falla una comparacion. Estamos usando limite menos uno para pasar la cota. Puede desbordarse eso? ayudanos a hacerlo sin sumar ni restar.”

**`TestArbolBMas` — Blas Oviedo**

18. “Ya arme las pruebas siguiendo los 12 puntos del pdf. Podes revisar si realmente estoy comprobando cada cosa o si hay casos donde solo imprimo el resultado y no verifico nada?”

19. “Para la insercion de 45 espero dos divisiones y una raiz nueva. Las escrituras tendrian que dar 5? quiero entender la cuenta antes de poner ese numero como esperado.”

20. “Que prueba chiquita puedo agregar para saber si buscarRango se pasa de largo? quiero un ejemplo donde termine justo en la ultima clave de una hoja.”

**`ArchivoDatos` y `BMasApp` — Sebastián López**

21. “Con titulos que tienen ñ o tildes me queda mal el largo. Estoy usando la cantidad de caracteres del String, pero tendria que usar la cantidad de bytes? explicame esa diferencia.”

22. “En rango quiero mostrar solo los primeros 10, pero contar todos los resultados. Si dejo de llamar al consumidor despues de 10 estoy cortando tambien la busqueda? me estoy mezclando ahi.”

23. “La consulta buscar me funciona solo si antes ejecuto construir en el mismo programa. Como tendria que separar los comandos para que buscar abra los .dat y no vuelva a leer el TSV?”

24. “Para mezclar el indice tengo un arreglo de claves y otro de posiciones. Si intercambio una clave tambien tengo que intercambiar su posicion verdad? mostrame un ejemplo porque me estan saliendo peliculas que no corresponden.”

**`ExperimentoBMas` — Blas Oviedo**

25. “Che, puse new Random(2026) al principio de cada tabla para que salga igual siempre. Pero en C y D dice que hay que seguir con el mismo generador, entonces lo estoy reiniciando mal? explicame donde tendria que crearlo.”

26. “Estoy sacando el promedio de paginas leidas y me da 2 aunque algunas busquedas leen 3. Tengo los dos numeros como int, puede ser por eso o estoy sumando cualquier cosa?”

27. “Si uso semilla 2026 significa que siempre voy a buscar las mismas claves? no termino de entender para que sirve la semilla, explicamelo con un ejemplo chico.”

28. “Ya hice las mediciones pero una ejecucion tarda mas que otra y las paginas leidas dan igual. Para comparar los tamaños de pagina me fijo en el tiempo o en los accesos? quiero entender que estoy midiendo.”

29. “Te paso el bucle que hice para las consultas. Podes revisar si estoy reiniciando bien los contadores entre una busqueda y otra? me parece que el promedio me esta acumulando lo anterior.”

**Análisis del README — ambos integrantes**

30. “Escribimos que con paginas mas grandes el arbol tiene menos niveles porque entran mas claves. Esta bien explicado o nos falta mencionar la cantidad de hijos de los internos? revisá esa idea.”

31. “En el analisis pusimos que las hojas quedan siempre al 50% despues de insertar en orden. Aver, eso pasa justo cuando se dividen o siguen asi despues? creo que mezclamos las dos cosas.”


32. “Te paso este parrafo del README que escribimos entre los dos. Podes marcar las partes confusas o que afirman demasiado? queremos corregirlo nosotros, sobre todo donde decimos que una configuracion es siempre mejor.”

### 3. Cambios que introdujimos sobre lo que propuso la IA

Las consultas a la IA nos sirvieron como punto de partida y para revisar dudas. Después ajustamos las propuestas y nuestras implementaciones en los siguientes aspectos:

1. **Enlaces al dividir una hoja — Blas.** Corregimos el orden de actualización del campo `siguiente`. Primero guardamos el enlace anterior, después se lo asignamos a la hoja nueva y finalmente enlazamos la hoja original con la nueva. Así evitamos perder la conexión con las hojas que ya estaban a la derecha, como ocurría al insertar 40.

2. **División de internos y asignación de páginas — Sebastián.** Adaptamos la propuesta para que el interno izquierdo conserve `techo((M + 1)/2)` hijos y el derecho reciba los restantes. La clave que sube al padre se retira del interno. También ordenamos la creación de páginas de abajo hacia arriba: hoja nueva, interno nuevo y raíz nueva. Este ajuste permite reproducir la asignación de las páginas 6, 7 y 8 al insertar 45.

3. **Recorrido por rango — Blas.** Reemplazamos la lista intermedia de resultados por llamadas directas a `ConsumidorEntrada`, porque no se permiten colecciones y no hace falta guardar todos los pares. Además, ajustamos la parada: si la última clave de la hoja ya alcanza el límite superior, no se lee la siguiente. Con el rango de 20 a 45 se evita una lectura innecesaria.

4. **Apertura del índice — Sebastián.** Ampliamos la validación inicial, que solamente comprobaba la marca del archivo. Agregamos controles de versión, tamaño de página, correspondencia de M y L y longitud del archivo según la cantidad de páginas declarada. Esto permite rechazar cabeceras incompatibles y archivos truncados antes de ejecutar una consulta.

5. **Verificación de invariantes — ambos.** Extendimos la propuesta para comparar la cadena de hojas con el orden obtenido al recorrer el árbol. También separamos las lecturas de diagnóstico de los contadores usados en las mediciones. Para las cotas de las claves, reemplazamos las expresiones que restaban uno al límite por comparaciones directas, evitando desbordamientos con `Long.MIN_VALUE`.

6. **Archivo de datos y comandos — Sebastián.** Corregimos el largo de los registros para guardar la cantidad de bytes en UTF-8, en lugar de la cantidad de caracteres del `String`. En la consulta por rango, separamos el límite de registros mostrados del total de resultados encontrados. También adaptamos los comandos para abrir los `.dat` en cada ejecución y ajustamos la mezcla aleatoria para intercambiar siempre la clave junto con su posición.

7. **Pruebas — Blas.** Ampliamos las comprobaciones para que no se limitaran a imprimir resultados. Agregamos verificaciones de claves duplicadas y rangos que terminan exactamente en la última clave de una hoja. Para la inserción de 45, relacionamos las escrituras esperadas con las dos divisiones de la traza: `2 * 2 + 1 = 5`. El objetivo fue comprobar tanto los resultados como los accesos a páginas.

8. **Mediciones — Blas.** Corregimos el reinicio del generador aleatorio: las consultas de las tablas C y D continúan con el mismo `Random(2026)`. Ajustamos los promedios para realizar la división con decimales y reiniciamos los contadores al comenzar cada medición. Así evitamos perder la parte decimal o acumular accesos de operaciones anteriores.

9. **Explicaciones del README — ambos.** Después de la revisión con IA, reformulamos nuestras conclusiones sobre la altura y la ocupación. Aclaramos que el máximo de hijos influye en los niveles y que la inserción creciente deja las hojas cerca de la mitad de su capacidad, no siempre exactamente al 50 %. También quitamos afirmaciones de que una configuración es siempre mejor y relacionamos cada conclusión con las tablas, distinguiendo los tiempos de ejecución de las páginas leídas.
