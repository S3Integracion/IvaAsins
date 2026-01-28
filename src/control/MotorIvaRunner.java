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
        public int duplicados;
        public String duplicadosAsin;
        public int procesados;
        public int saltadosBase;
        public int saltadosCancelados;
        public File salida;
        public File resumen;
        public String stdout;
    }

    public Resultado ejecutar(File baseCsv, File reporteTxt, File salidaCsv, File resumenFile)
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

        List<String> cmd = new ArrayList<>();
        if (useExe) {
            cmd.add(exe.toString());
        } else {
            cmd.addAll(resolvePythonCommand());
            cmd.add(py.toString());
        }
        cmd.add("--base");
        cmd.add(baseCsv.getAbsolutePath());
        cmd.add("--reporte");
        cmd.add(reporteTxt.getAbsolutePath());
        cmd.add("--salida");
        cmd.add(salidaCsv.getAbsolutePath());
        cmd.add("--resumen");
        cmd.add(resumenFile.getAbsolutePath());

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
        resultado.salida = salidaCsv;
        resultado.resumen = resumenFile;

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
        resultado.duplicados = parseInt(props.getProperty("duplicados", "0"));
        resultado.duplicadosAsin = props.getProperty("duplicados_asin", "");
        resultado.procesados = parseInt(props.getProperty("procesados", "0"));
        resultado.saltadosBase = parseInt(props.getProperty("saltados_base", "0"));
        resultado.saltadosCancelados = parseInt(props.getProperty("saltados_cancelados", "0"));
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
