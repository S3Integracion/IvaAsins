package control;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

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
        public String stdout;
    }

    public Resultado ejecutar(File baseFile, File reporteTxt, File previewCsv, File resumenFile, File reporteOutFile,
            String sheetName)
            throws IOException, InterruptedException {
        Path motoresDir = findMotoresDir();
        if (motoresDir == null) {
            throw new FileNotFoundException("No se encontró la carpeta 'motores'. "
                    + "Configura IVASINS_MOTORES o -Divasins.motores, "
                    + "o ajusta el Working Directory al proyecto.");
        }
        Path motorDir = motoresDir.resolve("FormatearIva");
        Path exe = motorDir.resolve("FormatearIva.exe");
        Path py = motorDir.resolve("FormatearIva.py");
        boolean useExe = Files.exists(exe);
        if (!useExe && !Files.exists(py)) {
            throw new FileNotFoundException("No se encontró FormatearIva.exe ni FormatearIva.py en: " + motorDir);
        }

        List<String> cmd = buildCommand(useExe, exe, py);
        cmd.add("--base");
        cmd.add(baseFile.getAbsolutePath());
        cmd.add("--reporte");
        cmd.add(reporteTxt.getAbsolutePath());
        cmd.add("--salida");
        cmd.add(previewCsv.getAbsolutePath());
        cmd.add("--resumen");
        cmd.add(resumenFile.getAbsolutePath());
        if (reporteOutFile != null) {
            cmd.add("--reporte-out");
            cmd.add(reporteOutFile.getAbsolutePath());
        }
        if (sheetName != null && !sheetName.trim().isEmpty()) {
            cmd.add("--sheet");
            cmd.add(sheetName.trim());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        int exitCode = process.waitFor();

        Resultado resultado = new Resultado();
        resultado.stdout = output.toString().trim();
        resultado.preview = previewCsv;
        resultado.resumen = resumenFile;
        resultado.reporte = reporteOutFile;

        if (exitCode != 0) {
            resultado.ok = false;
            resultado.mensaje = resultado.stdout.isEmpty() ? "Error al ejecutar el motor." : resultado.stdout;
            return resultado;
        }

        if (!resumenFile.exists()) {
            resultado.ok = false;
            resultado.mensaje = "No se generó el resumen del proceso.";
            return resultado;
        }

        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(resumenFile.toPath(), StandardCharsets.UTF_8)) {
            props.load(reader);
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

    public List<String> listarHojas(File baseXlsx) throws IOException, InterruptedException {
        Path motoresDir = findMotoresDir();
        if (motoresDir == null) {
            throw new FileNotFoundException("No se encontró la carpeta 'motores'.");
        }
        Path motorDir = motoresDir.resolve("FormatearIva");
        Path exe = motorDir.resolve("FormatearIva.exe");
        Path py = motorDir.resolve("FormatearIva.py");
        boolean useExe = Files.exists(exe);
        if (!useExe && !Files.exists(py)) {
            throw new FileNotFoundException("No se encontró FormatearIva.exe ni FormatearIva.py en: " + motorDir);
        }

        List<String> cmd = buildCommand(useExe, exe, py);
        cmd.add("--base");
        cmd.add(baseXlsx.getAbsolutePath());
        cmd.add("--list-sheets");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> sheets = new ArrayList<>();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
                if (!line.trim().isEmpty()) {
                    sheets.add(line.trim());
                }
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String msg = output.toString().trim();
            throw new IOException(msg.isEmpty() ? "Error al listar hojas." : msg);
        }
        return sheets;
    }

    private List<String> buildCommand(boolean useExe, Path exe, Path py) {
        List<String> cmd = new ArrayList<>();
        if (useExe) {
            cmd.add(exe.toString());
        } else {
            cmd.addAll(resolvePythonCommand());
            cmd.add(py.toString());
        }
        return cmd;
    }

    private List<String> resolvePythonCommand() {
        String cmd = System.getenv("IVASINS_PYTHON");
        if (cmd == null || cmd.trim().isEmpty()) {
            cmd = System.getProperty("ivasins.python");
        }
        if (cmd == null || cmd.trim().isEmpty()) {
            cmd = "python";
        }
        List<String> parts = new ArrayList<>();
        for (String part : cmd.trim().split("\\s+")) {
            if (!part.isEmpty()) {
                parts.add(part);
            }
        }
        return parts;
    }

    private Path findMotoresDir() {
        String custom = System.getenv("IVASINS_MOTORES");
        if (custom == null || custom.trim().isEmpty()) {
            custom = System.getProperty("ivasins.motores");
        }
        if (custom != null && !custom.trim().isEmpty()) {
            Path customPath = Paths.get(custom.trim()).toAbsolutePath();
            if (Files.isDirectory(customPath)) {
                return customPath;
            }
        }

        List<Path> roots = new ArrayList<>();
        roots.add(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
        try {
            Path codePath = Paths.get(MotorIvaRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath();
            if (Files.isRegularFile(codePath)) {
                codePath = codePath.getParent();
            }
            roots.add(codePath);
        } catch (Exception ex) {
            // ignore
        }

        for (Path root : roots) {
            Path found = searchUpForMotores(root, 8);
            if (found != null) {
                return found;
            }
            if (root != null) {
                Path nested = root.resolve("IvaAsins");
                found = searchUpForMotores(nested, 6);
                if (found != null) {
                    return found;
                }
            }
        }
        Path deep = searchDownForMotores(Paths.get(System.getProperty("user.dir")).toAbsolutePath(), 3);
        if (deep != null) {
            return deep;
        }
        return null;
    }

    private Path searchUpForMotores(Path start, int maxDepth) {
        Path dir = start;
        for (int i = 0; i < maxDepth && dir != null; i++) {
            Path motores = dir.resolve("motores");
            if (Files.isDirectory(motores)) {
                return motores;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private Path searchDownForMotores(Path root, int maxDepth) {
        if (root == null) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(root, maxDepth)) {
            return stream.filter(p -> p.getFileName().toString().equalsIgnoreCase("motores"))
                    .filter(Files::isDirectory)
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }
}
