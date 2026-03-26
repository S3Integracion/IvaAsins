# Changelog

## 2026-03-25

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

