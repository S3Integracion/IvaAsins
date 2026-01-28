# Manual de Usuario — IvaAsins

## 1. Introducción
IvaAsins es una herramienta para filtrar y formatear reportes de Amazon Seller, identificando si un producto tiene IVA y exportando un CSV limpio y compatible con tu base de datos. Está diseñada para:
- Reducir errores manuales
- Mantener consistencia en el formato
- Procesar miles de filas en segundos

El programa utiliza:
- **Java Swing (UI)** para la interacción con el usuario.
- **Python (motor de datos)** para procesamiento avanzado.

---

## 2. Archivos requeridos

### 2.1 CSV Base de IVA
Archivo de referencia que debe contener los encabezados:
```
ASIN;SKU;IVA;
```
Características:
- Delimitado por `;` (punto y coma).
- Puede contener muchas filas previas.

### 2.2 Reporte Amazon Seller (.txt)
Archivo de exportación de pedidos. Normalmente contiene:
- Columnas separadas por tabulador (`\t`) o comas
- Encabezados como: `asin`, `sku`, `item-tax`, `order-status`

---

## 3. Interfaz principal
La ventana principal muestra:
- Campo para seleccionar **Base IVA (.csv)**
- Campo para seleccionar **Reporte Amazon (.txt)**
- Botones:
  - **Previsualizar**
  - **Exportar**
  - **Limpiar**
- Tabla de vista previa (muestra primeras 100 filas)
- Barra de estado

---

## 4. Flujo de uso recomendado

### Paso 1: Cargar archivos
Puedes hacerlo de dos formas:
1. **Buscar** usando el botón (abre el selector nativo del sistema).
2. **Arrastrar y soltar** el archivo sobre la ventana.

### Paso 2: Previsualizar (opcional)
Presiona **Previsualizar** para:
- Ejecutar el motor
- Ver las primeras 100 filas generadas

### Paso 3: Exportar
Presiona **Exportar** para:
- Elegir la ubicación de guardado
- Generar el archivo final `Asins_Taxes.csv`

*Nota: Exportar puede hacerse sin previsualizar.*

---

## 5. Reglas de procesamiento

### 5.1 Cancelados
Cualquier pedido cuyo `order-status` contenga “cancel” (Cancelled, Canceled, etc.) será **excluido**.

### 5.2 IVA
La columna IVA se calcula así:
- **SI** si el campo `item-tax` tiene un monto
- **NO** si está vacío o es 0

### 5.3 Duplicados de ASIN
Si el mismo ASIN aparece más de una vez:
- Se conserva el registro con `IVA=SI`
- Se notifica al usuario mediante un popup

---

## 6. Vista previa
La tabla solo muestra **100 filas** por rendimiento. El archivo exportado contiene **todas** las filas válidas.

---

## 7. Resultados esperados
El archivo exportado:
- Se llama `Asins_Taxes.csv`
- Tiene los mismos encabezados que el CSV base
- Está delimitado por `;`
- Contiene solo pedidos no cancelados

Ejemplo:
```
ASIN;SKU;IVA;
B0BKNSYRC7;AS-ABCD-4327;SI;
B081TMDLXF;aloma_084;NO;
```

---

## 8. Errores comunes y soluciones

### Error: “No se encontró la carpeta 'motores'”
Solución:
- Ejecuta el programa desde la carpeta raíz del proyecto.
- O define la variable `IVASINS_MOTORES` con la ruta correcta.

### Error: “Unbound classpath container”
Solución:
- Configura un JDK válido en tu IDE (recomendado Java 21).

---

## 9. Preguntas frecuentes (FAQ)

**¿Por qué solo veo 100 filas?**
La vista previa está limitada a 100 por rendimiento. La exportación incluye todo.

**¿Puedo exportar sin previsualizar?**
Sí. Exportar ejecuta el motor directamente.

**¿Qué pasa si hay un ASIN repetido?**
Se conserva el que tenga IVA=SI.

**¿Puedo usar motores .exe en lugar de .py?**
Sí. El programa usa primero el `.exe` si existe.

---

## 10. Contacto / Soporte
Si encuentras errores o deseas nuevas funciones, documenta:
- Captura del error
- Archivos de entrada usados (sin datos sensibles)
- Detalles del entorno (versión Java / OS)

