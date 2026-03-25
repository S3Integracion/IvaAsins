package control;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLI compatible con el motor historico para soporte tecnico.
 */
public class FormatearIvaMain {

    public static void main(String[] args) {
        try {
            int exit = run(args);
            System.exit(exit);
        } catch (Exception ex) {
            System.err.println("ERROR: " + ex.getMessage());
            System.exit(1);
        }
    }

    public static int run(String[] args) throws IOException {
        Map<String, String> options = parseOptions(args);

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

        String reporte = options.get("--reporte");
        String salida = options.get("--salida");
        if (reporte == null || reporte.trim().isEmpty()) {
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
        request.reporteTxt = new File(reporte).getAbsoluteFile();
        request.previewCsv = new File(salida).getAbsoluteFile();
        request.resumenFile = new File(resumen).getAbsoluteFile();
        String reportOut = options.get("--reporte-out");
        request.reporteOutFile = reportOut == null || reportOut.trim().isEmpty() ? null
                : new File(reportOut).getAbsoluteFile();
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
}

