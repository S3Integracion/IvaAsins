# Changelog

## 2026-03-25

### Actualizacion: normalizacion de columnas y fecha por cambio de IVA
- La salida CSV se normaliza a 3 columnas fijas: `FECHA,ASIN,IVA`.
- Se elimina la dependencia de columna `SKU` vacia.
- `FECHA` usa fecha local del sistema con formato `MM/dd/yyyy`.
- `FECHA` solo se actualiza en ASIN de reporte cuando hubo alta nueva o cambio de estado de IVA.
- El guardado ahora usa carpeta diaria dentro del arbol: `Bases de datos de IVAS/<anio>/<Mes>/<MM-dd-yyyy>/`.

### Actualizacion mayor: procesamiento de multiples reportes Amazon
- El motor acepta uno o varios `--reporte` y consolida por ASIN en una sola corrida.
- Si un ASIN aparece en multiples reportes, gana la fila con fecha mas reciente:
  - prioridad `last-updated-date`
  - fallback `purchase-date`
  - empate: prioridad IVA `SI`.
- Reportes de entrada duplicados por ruta absoluta se deduplican antes de procesar.
- Se copian todos los reportes usados a la carpeta versionada:
  - primer archivo sin sufijo
  - desde el segundo: `(... (2), (3), ...)`.
- UI actualizada para multi-seleccion de reportes y despliegue de lista completa de rutas.
- CLI actualizada para `--reporte` repetible.

### Actualizacion mayor: salidas versionadas sin sobrescribir base
- Se cambia el flujo para no modificar la base original y generar siempre un nuevo CSV:
  - `Base de Datos IVA Amazon HHmm MM-dd-yyyy.csv`.
- Los artefactos generados usan timestamp local con formato `HHmm MM-dd-yyyy`.
- La copia del reporte se guarda como: `Reporte de Amazon HHmm MM-dd-yyyy.txt`.
- Se crea estructura de guardado automatica por fecha:
  - `Bases de datos de IVAS/<anio>/<Mes>/`.
- Se genera reporte de proceso en formato `.log` y se copia el reporte Amazon `.txt` en la misma carpeta de salida.
- El log incluye trazabilidad de origen y destino (rutas de base/reporte de entrada y artefactos generados).
- Se mantiene el procesamiento y soporte de entrada `.csv` y `.xlsx`.

### UI y CLI
- Interfaz actualizada para permitir seleccionar carpeta raiz de guardado (opcional).
- Mensajes y resumen en pantalla muestran rutas reales de artefactos generados.
- CLI actualizada con `--output-root` para definir directorio raiz de salida versionada.

### Sincronizacion de formularios
- `src/interfaz/Principal.form` sincronizado con `src/interfaz/Principal.java`:
  - nueva fila para `txtSalida` y `btnBuscarSalida`.

