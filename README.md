# IvaAsins

## Descripción general
IvaAsins es una aplicación de escritorio (Java Swing + WindowBuilder) que procesa un reporte de Amazon Seller en formato `.txt` y genera un archivo CSV con las columnas `ASIN`, `SKU` e `IVA`. La lógica pesada de datos se ejecuta en motores Python (o `.exe` generados desde esos motores) y Java actúa como interfaz y orquestador.

El objetivo principal es identificar productos con IVA, excluir pedidos cancelados y exportar un archivo listo para control fiscal o inventario.

## Funcionalidad principal
- **Entrada 1:** CSV base de IVA con encabezados `ASIN;SKU;IVA;`.
- **Entrada 2:** Reporte Amazon Seller `.txt` delimitado (tab/; / ,) con columnas que incluyen `asin`, `sku`, `item-tax` y `order-status`.
- **Salida:** CSV `Asins_Taxes.csv` con los mismos encabezados y delimitador que el CSV base.

### Reglas de negocio
- Se procesan **todas** las filas del reporte cuyo `order-status` **no** esté cancelado.
- La columna `IVA` se llena con:
  - **SI** si `item-tax` tiene monto
  - **NO** si `item-tax` está vacío o es 0
- Si un ASIN aparece repetido en el mismo reporte:
  - Se **prioriza** el registro con `IVA=SI`.
  - Se muestra un **popup** informando duplicados detectados.

## Arquitectura
- **Java (Swing/WindowBuilder)**
  - UI, selección de archivos, drag & drop, vista previa y exportación.
- **Python (motor)**
  - Parsing de datos, filtros, deduplicación, generación del CSV final.

## Estructura del proyecto
```
IvaAsins/
├─ src/
│  ├─ interfaz/
│  │  └─ Principal.java
│  ├─ control/
│  │  └─ MotorIvaRunner.java
│  ├─ entidad/
│  └─ control/
├─ motores/
│  └─ FormatearIva/
│     ├─ FormatearIva.py
│     └─ FormatearIva.exe (opcional)
├─ Recursos/ (solo para pruebas locales)
└─ bin/
```

## Requisitos
- **Java 21+** (configurado en VS Code o Eclipse)
- **Python 3** si se usa `.py` directamente
- (Opcional) `.exe` del motor generado desde Python

## Motores
Java busca el motor en `motores/FormatearIva/`:
- Si existe `FormatearIva.exe`, se usa **primero**.
- Si no existe, se usa `FormatearIva.py`.

### Variables de entorno útiles
- `IVASINS_MOTORES`: ruta directa a la carpeta `motores`.
- `IVASINS_PYTHON`: comando Python alternativo (ej: `py -3`).

## Vista previa
La aplicación muestra en la tabla **las primeras 100 filas** del archivo generado para previsualización rápida. El exportado contiene **todas** las filas no canceladas.

## Exportación
La exportación genera el archivo final `Asins_Taxes.csv` en el directorio elegido por el usuario. Se puede exportar **sin previsualizar**: el motor se ejecuta y guarda directamente.

## Resolución de problemas
### “Unbound classpath container” (VS Code/Eclipse)
Configura un JDK válido (ej: JavaSE-21). En VS Code se recomienda:
```
.vscode/settings.json
{
  "java.jdt.ls.java.home": "C:\\Program Files\\Java\\jdk-21",
  "java.configuration.runtimes": [
    {
      "name": "JavaSE-21",
      "path": "C:\\Program Files\\Java\\jdk-21",
      "default": true
    }
  ]
}
```

### “No se encontró la carpeta 'motores'”
- Asegúrate de ejecutar desde la carpeta del proyecto `IvaAsins/`.
- O define `IVASINS_MOTORES` con la ruta correcta.

## Compilación rápida (CLI)
```
javac -encoding UTF-8 -d bin src\interfaz\Principal.java src\control\MotorIvaRunner.java
```

## Ejecución rápida (CLI)
```
java -cp bin interfaz.Principal
```

## Autor
Proyecto guiado y automatizado con asistencia de Codex.
