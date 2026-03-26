# Manual de usuario - Iva Asins

## Que hace el programa
Procesa tu base de IVA usando un reporte Amazon sin sobrescribir el archivo original. El programa genera una nueva base CSV versionada, un log del proceso y una copia del reporte Amazon.

## Antes de empezar
- Verifica que la base tenga las columnas ASIN e IVA.
- Ten a mano el reporte Amazon en formato .txt.
- Opcional: define una carpeta raiz donde quieras guardar los resultados versionados.

## Pasos para procesar
1. Abre el programa.
2. Selecciona la base IVA (.csv o .xlsx) con el boton "Buscar" o arrastrando el archivo.
3. Selecciona el reporte Amazon (.txt) con el boton "Buscar" o arrastrando el archivo.
4. Si la base es XLSX y se muestra una lista de hojas, elige la hoja correcta.
5. Opcional: selecciona "Carpeta raiz de guardado".
6. Presiona "Procesar" y espera a que termine.
6. Revisa la vista previa y el resumen que aparece en pantalla.

## Donde quedan los resultados
- Se crea una carpeta `Bases de datos de IVAS` en la ruta raiz seleccionada (o en la carpeta de la base si no defines una).
- Dentro se generan subcarpetas por anio y mes (segun fecha del sistema).
- Se crea un CSV nuevo: `Base de Datos IVA Amazon HHmm MM-dd-yyyy.csv`.
- Se crea un log del proceso con extension `.log`.
- Se guarda tambien una copia del reporte Amazon con nombre: `Reporte de Amazon HHmm MM-dd-yyyy.txt`.
- La tabla muestra una vista previa de los registros agregados. Si no hubo nuevos, puede mostrar toda la base.

## Buenas practicas
- No cierres el programa mientras esta procesando.
- Si tu base tiene mas columnas, el CSV generado conserva la estructura y actualiza ASIN/IVA para los registros procesados.

## Preguntas frecuentes
**El programa elimina productos cancelados?**
No. Los pedidos cancelados solo se ignoran en el calculo, pero no se eliminan de la base.

**Puedo usar una base con mas columnas?**
Si, pero el programa solo completa ASIN e IVA. Las otras columnas pueden quedar vacias.

**No encuentro el reporte generado.**
Busca dentro de `Bases de datos de IVAS/<anio>/<Mes>/` en la ruta raiz configurada.
