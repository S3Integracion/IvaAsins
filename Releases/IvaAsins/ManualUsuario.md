# Manual de usuario - Iva Asins

## Que hace el programa
Actualiza tu base de IVA usando un reporte Amazon. El resultado queda en el mismo archivo de la base y el programa genera un reporte con el detalle del proceso.

## Antes de empezar
- Ten una copia de tu base, porque el archivo se actualiza.
- Verifica que la base tenga las columnas ASIN e IVA.
- Ten a mano el reporte Amazon en formato .txt.

## Pasos para procesar
1. Abre el programa.
2. Selecciona la base IVA (.csv o .xlsx) con el boton "Buscar" o arrastrando el archivo.
3. Selecciona el reporte Amazon (.txt) con el boton "Buscar" o arrastrando el archivo.
4. Si la base es XLSX y se muestra una lista de hojas, elige la hoja correcta.
5. Presiona "Procesar" y espera a que termine.
6. Revisa la vista previa y el resumen que aparece en pantalla.

## Donde quedan los resultados
- La base se actualiza en el mismo archivo que seleccionaste.
- Se crea un archivo `Reporte_Iva_Process.txt` en la misma carpeta de la base.
- La tabla muestra una vista previa de los registros agregados. Si no hubo nuevos, puede mostrar toda la base.

## Buenas practicas
- Trabaja siempre con una copia de respaldo.
- No cierres el programa mientras esta procesando.
- Si tu base tiene mas columnas, recuerda que el programa solo rellena ASIN e IVA; las demas pueden quedar en blanco.

## Preguntas frecuentes
**El programa elimina productos cancelados?**
No. Los pedidos cancelados solo se ignoran en el calculo, pero no se eliminan de la base.

**Puedo usar una base con mas columnas?**
Si, pero el programa solo completa ASIN e IVA. Las otras columnas pueden quedar vacias.

**No encuentro el reporte generado.**
Busca `Reporte_Iva_Process.txt` en la misma carpeta donde esta la base.
