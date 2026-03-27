package control;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class MotorIvaRunner {

    public static class Resultado {
        public boolean ok;
        public String mensaje;
        public int totalReporte;
        public int duplicadosFilas;
        public int canceladosFilas;
        public int canceladosAsins;
        public int sinAsinFilas;
        public int asinUnicosReporte;
        public int agregados;
        public int modificados;
        public int sinCambios;
        public int consolidadosBase;
        public int eliminadosBase;
        public int baseOriginal;
        public int baseFinal;
        public int previewInicio;
        public File preview;
        public File resumen;
        public File reporte;
        public File baseGeneradaCsv;
        public File reporteAmazonCopiado;
        public List<File> reportesAmazonCopiados = new ArrayList<>();
        public File carpetaSalida;
        public String stdout;
    }

    public Resultado ejecutar(File baseFile, File reporteTxt, File outputRootDirectory, File previewCsv,
            File resumenFile, String sheetName)
            throws IOException {
        return ejecutar(baseFile,
                reporteTxt == null ? new ArrayList<>() : Arrays.asList(reporteTxt),
                outputRootDirectory,
                previewCsv,
                resumenFile,
                sheetName);
    }

    public Resultado ejecutar(File baseFile, List<File> reportesTxt, File outputRootDirectory, File previewCsv,
            File resumenFile, String sheetName)
            throws IOException {
        IvaEngine engine = new IvaEngine();
        IvaEngine.ProcessRequest request = new IvaEngine.ProcessRequest();
        request.baseFile = baseFile;
        request.reporteTxts = reportesTxt;
        if (reportesTxt != null && !reportesTxt.isEmpty()) {
            request.reporteTxt = reportesTxt.get(0);
        }
        request.previewCsv = previewCsv;
        request.resumenFile = resumenFile;
        request.reporteOutFile = null;
        request.outputRootDirectory = outputRootDirectory;
        request.sheetName = sheetName;

        Resultado resultado = new Resultado();
        resultado.stdout = "OK";
        resultado.preview = previewCsv;
        resultado.resumen = resumenFile;

        engine.process(request);

        if (!resumenFile.exists()) {
            resultado.ok = false;
            resultado.mensaje = "No se generó el resumen del proceso.";
            return resultado;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(resumenFile)) {
            props.load(in);
        }

        resultado.ok = Boolean.parseBoolean(props.getProperty("ok", "true"));
        resultado.totalReporte = parseInt(props.getProperty("total_reporte", "0"));
        resultado.duplicadosFilas = parseInt(props.getProperty("duplicados_filas", "0"));
        resultado.canceladosFilas = parseInt(props.getProperty("cancelados_filas", "0"));
        resultado.canceladosAsins = parseInt(props.getProperty("cancelados_asins", "0"));
        resultado.sinAsinFilas = parseInt(props.getProperty("sin_asin_filas", "0"));
        resultado.asinUnicosReporte = parseInt(props.getProperty("asin_unicos_reporte", "0"));
        resultado.agregados = parseInt(props.getProperty("agregados", "0"));
        resultado.modificados = parseInt(props.getProperty("modificados", "0"));
        resultado.sinCambios = parseInt(props.getProperty("sin_cambios", "0"));
        resultado.consolidadosBase = parseInt(props.getProperty("consolidados_base", "0"));
        resultado.eliminadosBase = parseInt(props.getProperty("eliminados_base", "0"));
        resultado.baseOriginal = parseInt(props.getProperty("base_original", "0"));
        resultado.baseFinal = parseInt(props.getProperty("base_final", "0"));
        resultado.previewInicio = parseInt(props.getProperty("preview_inicio", "0"));
        resultado.baseGeneradaCsv = toFileOrNull(props.getProperty("output_csv"));
        resultado.reporte = toFileOrNull(props.getProperty("output_log"));
        resultado.reporteAmazonCopiado = toFileOrNull(props.getProperty("output_reporte_amazon"));
        resultado.reportesAmazonCopiados = splitFiles(props.getProperty("output_reportes_amazon"));
        if ((resultado.reporteAmazonCopiado == null) && !resultado.reportesAmazonCopiados.isEmpty()) {
            resultado.reporteAmazonCopiado = resultado.reportesAmazonCopiados.get(0);
        }
        resultado.carpetaSalida = toFileOrNull(props.getProperty("output_month_folder"));
        resultado.mensaje = "OK";
        return resultado;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    public List<String> listarHojas(File baseXlsx) throws IOException {
        IvaEngine engine = new IvaEngine();
        List<String> sheets = engine.listSheets(baseXlsx);
        return sheets == null ? new ArrayList<>() : sheets;
    }

    private File toFileOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new File(value.trim());
    }

    private List<File> splitFiles(String value) {
        List<File> files = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return files;
        }
        String[] parts = value.split("\\|");
        for (String part : parts) {
            String path = part == null ? "" : part.trim();
            if (!path.isEmpty()) {
                files.add(new File(path));
            }
        }
        return files;
    }
}
