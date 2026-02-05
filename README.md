# Iva Asins

Aplicacion para actualizar una base de IVA por ASIN usando un reporte Amazon.
Incluye interfaz grafica (Java) y un motor de procesamiento (FormatearIva.exe o FormatearIva.py).

## Componentes
- Interfaz: `src/interfaz/Principal.java`
- Lanzador de motor: `src/control/MotorIvaRunner.java`
- Motor: `motores/FormatearIva/FormatearIva.py` (o `FormatearIva.exe`)

## Dependencias
- Java 8+ para la interfaz grafica.
- Python 3 si se usa `FormatearIva.py` (openpyxl se carga desde `motores/FormatearIva/vendor`).
- Si existe `FormatearIva.exe`, se usa ese ejecutable y no se requiere Python.

## Flujo general
1. El usuario selecciona una base IVA (.csv o .xlsx) y un reporte Amazon (.txt).
2. La interfaz ejecuta el motor con los parametros requeridos.
3. El motor actualiza la base, genera una previsualizacion y un resumen.
4. La interfaz muestra la vista previa y un resumen en pantalla.

## Entradas
### Base IVA (CSV o XLSX)
- Debe tener columnas `ASIN` e `IVA` (no importa mayusculas o minusculas).
- Se permiten mas columnas, pero el motor solo rellena ASIN e IVA.
- En XLSX se usa la hoja `IVA's Base de Datos` por defecto.
- Los valores de IVA se normalizan a `SI` o `NO` cuando coinciden con variantes comunes.

### Reporte Amazon (TXT)
- Debe incluir los headers: `asin`, `item-tax`, `order-status`.
- El delimitador se detecta automaticamente (tab, ;, , o |).

## Reglas de procesamiento
- Filas con `order-status` que contenga `cancel` se ignoran y se reportan.
- IVA se calcula con `item-tax`:
  - Vacio => `NO`
  - Valor numerico > 0 => `SI`
  - Valor no numerico pero no vacio => `SI`
- Duplicados en reporte:
  - Si un ASIN aparece varias veces y alguna fila tiene IVA `SI`, el ASIN queda con `SI`.
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
- Base actualizada en el mismo archivo.
- `Reporte_Iva_Process.txt` en la carpeta de la base (detalle del proceso).
- Previsualizacion CSV (ruta definida por la interfaz o CLI).
- Archivo resumen `.resumen` (properties) con contadores del proceso.

## Interfaz grafica
- Ejecuta `control.Main`.
- Permite arrastrar archivos o usar "Buscar".
- Si la base es XLSX y no existe la hoja por defecto, se solicita elegir una.
- Muestra vista previa (hasta 100 filas) y un resumen del proceso.
- Menu `File -> Manual` abre `ManualUsuario.md`.

## Uso por linea de comandos (motor)
Ejemplo con Python:

```bash
python motores/FormatearIva/FormatearIva.py \
  --base "C:\\ruta\\BaseIVA.csv" \
  --reporte "C:\\ruta\\ReporteAmazon.txt" \
  --salida "C:\\ruta\\Preview.csv" \
  --resumen "C:\\ruta\\Preview.resumen" \
  --reporte-out "C:\\ruta\\Reporte_Iva_Process.txt"
```

Opciones soportadas:
- `--base` (requerido)
- `--reporte` (requerido)
- `--salida` (requerido)
- `--resumen` (opcional, por defecto `<salida>.resumen`)
- `--reporte-out` (opcional)
- `--sheet` (opcional, nombre de hoja en XLSX)
- `--list-sheets` (lista hojas de un XLSX)

## Configuracion
- `IVASINS_MOTORES` o `-Divasins.motores`: ruta a la carpeta `motores`.
- `IVASINS_PYTHON` o `-Divasins.python`: comando de Python a usar si no hay `.exe`.
Si no se configura, el lanzador busca la carpeta `motores` desde el directorio de trabajo,
desde la ubicacion del ejecutable y en subdirectorios cercanos.

## Notas importantes
- La base se actualiza en el mismo archivo. Se recomienda hacer copia antes de procesar.
- En XLSX solo se rellenan ASIN e IVA; otras columnas pueden quedar vacias.
