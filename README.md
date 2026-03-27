# Iva Asins

Aplicacion para actualizar una base de IVA por ASIN usando un reporte Amazon.
Incluye interfaz grafica y motor de procesamiento 100% Java (sin dependencias externas).

## Componentes
- Interfaz: `src/interfaz/Principal.java`
- Lanzador de motor: `src/control/MotorIvaRunner.java`
- Motor Java: `src/control/IvaEngine.java`
- CLI tecnica: `src/control/FormatearIvaMain.java`

## Dependencias
- Java 11+ para interfaz y motor.

## Flujo general
1. El usuario selecciona una base IVA (.csv o .xlsx) y uno o varios reportes Amazon (.txt).
2. La interfaz ejecuta el motor con los parametros requeridos.
3. El motor consolida datos, genera una nueva base CSV versionada, una previsualizacion y un resumen.
4. La interfaz muestra la vista previa y un resumen en pantalla.

## Entradas
### Base IVA (CSV o XLSX)
- Debe tener columnas `ASIN` e `IVA` (no importa mayusculas o minusculas).
- Se permiten mas columnas, pero el motor solo rellena ASIN e IVA.
- En XLSX se usa la hoja `IVA's Base de Datos` por defecto.
- Los valores de IVA se normalizan a `SI` o `NO` cuando coinciden con variantes comunes.

### Reporte(s) Amazon (TXT)
- Debe incluir los headers: `asin`, `item-tax`, `order-status`.
- El delimitador se detecta automaticamente (tab, ;, , o |).
- Si se cargan rutas repetidas exactas, se deduplican por ruta absoluta.

## Reglas de procesamiento
- Filas con `order-status` que contenga `cancel` se ignoran y se reportan.
- IVA se calcula con `item-tax`:
  - Vacio => `NO`
  - Valor numerico > 0 => `SI`
  - Valor no numerico pero no vacio => `SI`
- Consolidacion entre multiples reportes:
  - Se usa el registro con fecha mas reciente por ASIN.
  - Fecha de referencia: `last-updated-date` (fallback a `purchase-date`).
  - Si hay empate de fecha, se prioriza IVA `SI`.
- Duplicados en base:
  - Se consolida un solo registro por ASIN.
  - Si algun duplicado tiene IVA `SI`, el registro final queda en `SI`.
- Previsualizacion:
  - Se genera desde el primer ASIN agregado.
  - Si no hubo nuevos, la previsualizacion incluye toda la base.
- Actualizacion de base:
  - CSV: se reescribe el archivo conservando el encabezado y el delimitador detectado.
  - XLSX: se vacian las filas de datos y se escriben solo las columnas ASIN e IVA.

## Salidas
- Nueva base CSV (no se sobreescribe la base original) con nombre:
  - `Base de Datos IVA Amazon HHmm MM-dd-yyyy.csv`
- Log del proceso con extension `.log` en la misma carpeta de salida versionada.
- Copia de todos los reportes Amazon `.txt` en la misma carpeta de salida versionada.
- Previsualizacion CSV (ruta definida por la interfaz o CLI).
- Archivo resumen `.resumen` (properties) con contadores y rutas generadas.

Estructura de guardado:
- `Bases de datos de IVAS/<anio>/<Mes>/`
- Ejemplo:
  - `Bases de datos de IVAS/2026/Febrero/Base de Datos IVA Amazon 1842 03-25-2026.csv`

## Interfaz grafica
- Ejecuta `control.Main`.
- Permite arrastrar archivos o usar "Buscar" (incluyendo multi-seleccion de reportes `.txt`).
- Permite definir carpeta raiz opcional para guardar resultados versionados.
- Si la base es XLSX y no existe la hoja por defecto, se solicita elegir una.
- Muestra vista previa (hasta 100 filas) y un resumen del proceso.
- Menu `File -> Manual` abre `ManualUsuario.md`.

## Uso por linea de comandos (motor)
Ejemplo Java:

```bash
java -cp build/java/IvaAsins.jar control.FormatearIvaMain \
  --base "C:\\ruta\\BaseIVA.csv" \
  --reporte "C:\\ruta\\ReporteAmazon_1.txt" \
  --reporte "C:\\ruta\\ReporteAmazon_2.txt" \
  --output-root "C:\\ruta\\DestinoRaiz" \
  --salida "C:\\ruta\\Preview.csv" \
  --resumen "C:\\ruta\\Preview.resumen"
```

Opciones soportadas:
- `--base` (requerido)
- `--reporte` (requerido, repetible)
- `--salida` (requerido)
- `--resumen` (opcional, por defecto `<salida>.resumen`)
- `--output-root` (opcional, carpeta raiz; si se omite usa la carpeta de la base)
- `--sheet` (opcional, nombre de hoja en XLSX)
- `--list-sheets` (lista hojas de un XLSX)

## Configuracion
- No se requiere configuracion de motores externos.

## Notas importantes
- La base original nunca se sobreescribe; siempre se genera un CSV nuevo.
- Se conserva soporte de entrada XLSX y CSV.
- El log ahora se guarda como `.log` junto al CSV generado.
