package control;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio CLI compatible con el motor historico para soporte tecnico.
 * El punto de entrada oficial del programa es {@link Main}.
 */
public class FormatearIvaMain {


    public static int run(String[] args) throws IOException {
        Map<String, String> options = parseOptions(args);
        List<String> reportes = parseRepeatedOption(args, "--reporte");

        String base = options.get("--base");
        if (base == null || base.trim().isEmpty()) {
            throw new IOException("Falta --base");
        }

        IvaEngine engine = new IvaEngine();
        File baseFile = new File(base).getAbsoluteFile();

        if (options.containsKey("--list-sheets")) {
            List<String> sheets = engine.listSheets(baseFile);
            for (String sheet : sheets) {
                System.out.println(sheet);
            }
            return 0;
        }

        String salida = options.get("--salida");
        if (reportes.isEmpty()) {
            throw new IOException("Falta --reporte");
        }
        if (salida == null || salida.trim().isEmpty()) {
            throw new IOException("Falta --salida");
        }

        String resumen = options.get("--resumen");
        if (resumen == null || resumen.trim().isEmpty()) {
            resumen = salida + ".resumen";
        }

        IvaEngine.ProcessRequest request = new IvaEngine.ProcessRequest();
        request.baseFile = baseFile;
        request.reporteTxts = dedupeReportFiles(reportes);
        request.reporteTxt = request.reporteTxts.isEmpty() ? null : request.reporteTxts.get(0);
        request.previewCsv = new File(salida).getAbsoluteFile();
        request.resumenFile = new File(resumen).getAbsoluteFile();
        String outputRoot = options.get("--output-root");
        request.outputRootDirectory = outputRoot == null || outputRoot.trim().isEmpty() ? null
                : new File(outputRoot).getAbsoluteFile();
        // Compatibilidad: se conserva la opcion aunque el motor ahora genera automaticamente el log versionado.
        request.reporteOutFile = null;
        request.sheetName = options.get("--sheet");

        engine.process(request);
        System.out.println("OK");
        return 0;
    }

    private static Map<String, String> parseOptions(String[] args) throws IOException {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--list-sheets".equals(arg)) {
                options.put(arg, "true");
                continue;
            }
            if (arg.startsWith("--")) {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IOException("Falta valor para " + arg);
                }
                options.put(arg, args[++i]);
                continue;
            }
            throw new IOException("Argumento no soportado: " + arg);
        }
        return options;
    }

    private static List<String> parseRepeatedOption(String[] args, String option) throws IOException {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (!option.equals(args[i])) {
                continue;
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IOException("Falta valor para " + option);
            }
            values.add(args[++i]);
        }
        return values;
    }

    private static List<File> dedupeReportFiles(List<String> reportPaths) {
        LinkedHashMap<String, File> unique = new LinkedHashMap<>();
        for (String path : reportPaths) {
            if (path == null || path.trim().isEmpty()) {
                continue;
            }
            File absolute = new File(path.trim()).getAbsoluteFile();
            unique.putIfAbsent(absolute.getAbsolutePath(), absolute);
        }
        return new ArrayList<>(unique.values());
    }
}

