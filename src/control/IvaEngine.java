package control;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Motor IVA en Java con paridad funcional del flujo Python.
 */
public class IvaEngine {

    public static final String BASE_SHEET_NAME = "Base de Datos IVA Amazon";
    private static final String OUTPUT_FECHA = "FECHA";
    private static final String OUTPUT_ASIN = "ASIN";
    private static final String OUTPUT_IVA = "IVA";

    public static class ProcessRequest {
        public File baseFile;
        public File reporteTxt;
        public List<File> reporteTxts;
        public File previewCsv;
        public File resumenFile;
        public File reporteOutFile;
        public File outputRootDirectory;
        public String sheetName;
    }

    public static class ProcessResult {
        public boolean ok;
        public Map<String, String> resumen;
    }

    private static class BaseRecord {
        String asin;
        String iva;
        List<String> rowValues;

        BaseRecord(String asin, String iva, List<String> rowValues) {
            this.asin = asin;
            this.iva = iva;
            this.rowValues = rowValues;
        }
    }

    private static class OutputLayout {
        Path rootFolder;
        Path yearFolder;
        Path monthFolder;
        Path generatedCsv;
        Path generatedLog;
        String timestamp;
        Path copiedReportTxt;
        List<Path> copiedReportTxts;
    }

    private static class BaseData {
        LinkedHashMap<String, BaseRecord> baseMap;
        List<String> headerFields;
        Map<String, Integer> headerMap;
        String delimiter;
        boolean trailingDelimiter;
        int baseOriginalRows;
        List<String> baseDuplicates;
        String headerLine;
        XlsxContext xlsx;
    }

    private static class XlsxContext {
        String sheetName;
        String sheetEntryPath;
        XlsxZip zip;
        int maxColumn;
    }

    private static class ReportStats {
        LinkedHashMap<String, String> reportMap = new LinkedHashMap<>();
        int duplicateRows;
        int totalRows;
        int reportFilesProcessed;
        int cancelledRows;
        Set<String> cancelledAsins = new LinkedHashSet<>();
        int noAsinRows;
    }

    private static class ReportCandidate {
        String asin;
        String iva;
        Instant timestamp;
        int sourceOrder;

        ReportCandidate(String asin, String iva, Instant timestamp, int sourceOrder) {
            this.asin = asin;
            this.iva = iva;
            this.timestamp = timestamp;
            this.sourceOrder = sourceOrder;
        }
    }

    private static class ApplyStats {
        List<String[]> added = new ArrayList<>();
        List<String[]> modified = new ArrayList<>();
        int unchanged;
        int firstNewIndex;
    }

    public ProcessResult process(ProcessRequest request) throws IOException {
        validateRequest(request);
        List<File> reportFiles = resolveReportFiles(request);
        String processDate = LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));

        String basePath = request.baseFile.getAbsolutePath();
        String ext = extensionOf(basePath);
        boolean isXlsx = ".xlsx".equals(ext);

        BaseData baseData = isXlsx
                ? loadBaseXlsx(request.baseFile, request.sheetName)
                : loadBaseCsv(request.baseFile);

        ReportStats reportStats = loadReports(reportFiles);
        ApplyStats applyStats = applyReportToBase(baseData, reportStats.reportMap, processDate);

        OutputLayout outputLayout = resolveOutputLayout(request.baseFile.toPath(), request.outputRootDirectory);
        writeBaseCsv(outputLayout.generatedCsv, baseData);
        outputLayout.copiedReportTxts = copyReportFiles(reportFiles, outputLayout.monthFolder, outputLayout.timestamp);
        outputLayout.copiedReportTxt = outputLayout.copiedReportTxts.isEmpty() ? null : outputLayout.copiedReportTxts.get(0);

        writePreviewCsv(request.previewCsv.toPath(), baseData, applyStats.firstNewIndex);

        Set<String> cancelledOnly = new LinkedHashSet<>(reportStats.cancelledAsins);
        cancelledOnly.removeAll(reportStats.reportMap.keySet());

        Map<String, String> resumen = buildResumen(baseData, reportStats, applyStats, cancelledOnly.size());
        resumen.put("output_root_folder", outputLayout.rootFolder.toString());
        resumen.put("output_year_folder", outputLayout.yearFolder.toString());
        resumen.put("output_month_folder", outputLayout.monthFolder.toString());
        resumen.put("output_csv", outputLayout.generatedCsv.toString());
        resumen.put("output_log", outputLayout.generatedLog.toString());
        resumen.put("reportes_procesados", Integer.toString(reportStats.reportFilesProcessed));
        resumen.put("output_reporte_amazon", outputLayout.copiedReportTxt == null ? "" : outputLayout.copiedReportTxt.toString());
        resumen.put("output_reportes_amazon", joinPaths(outputLayout.copiedReportTxts));
        writeProperties(request.resumenFile.toPath(), resumen);

        writeReport(
                outputLayout.generatedLog,
                request.baseFile,
                isXlsx ? "XLSX" : "CSV",
                baseData.xlsx != null ? baseData.xlsx.sheetName : null,
                reportFiles,
                outputLayout.generatedCsv,
                outputLayout.copiedReportTxts,
                resumen,
                applyStats.added,
                applyStats.modified,
                cancelledOnly,
                baseData.baseDuplicates,
                applyStats.firstNewIndex,
                processDate);

        ProcessResult result = new ProcessResult();
        result.ok = true;
        result.resumen = resumen;
        return result;
    }

    public List<String> listSheets(File baseXlsx) throws IOException {
        if (baseXlsx == null || !baseXlsx.isFile()) {
            throw new IOException("No existe la base: " + (baseXlsx == null ? "null" : baseXlsx.getAbsolutePath()));
        }
        if (!".xlsx".equals(extensionOf(baseXlsx.getName()))) {
            throw new IOException("El archivo base no es XLSX.");
        }
        XlsxZip zip = XlsxZip.read(baseXlsx.toPath());
        return zip.listSheetNames();
    }

    private void validateRequest(ProcessRequest request) throws IOException {
        if (request == null) {
            throw new IOException("Request vacio.");
        }
        if (request.baseFile == null || !request.baseFile.isFile()) {
            throw new IOException("No existe la base: " + pathOrNull(request.baseFile));
        }
        if (request.previewCsv == null) {
            throw new IOException("Falta archivo de salida preview.");
        }
        if (request.resumenFile == null) {
            throw new IOException("Falta archivo de resumen.");
        }
    }

    private List<File> resolveReportFiles(ProcessRequest request) throws IOException {
        LinkedHashMap<String, File> unique = new LinkedHashMap<>();
        if (request.reporteTxt != null) {
            File absolute = request.reporteTxt.getAbsoluteFile();
            unique.put(absolute.getAbsolutePath(), absolute);
        }
        if (request.reporteTxts != null) {
            for (File reporte : request.reporteTxts) {
                if (reporte == null) {
                    continue;
                }
                File absolute = reporte.getAbsoluteFile();
                unique.putIfAbsent(absolute.getAbsolutePath(), absolute);
            }
        }
        if (unique.isEmpty()) {
            throw new IOException("No existe el reporte: null");
        }

        List<File> files = new ArrayList<>(unique.values());
        for (File file : files) {
            if (!file.isFile()) {
                throw new IOException("No existe el reporte: " + file.getAbsolutePath());
            }
        }
        return files;
    }

    private String pathOrNull(File file) {
        return file == null ? "null" : file.getAbsolutePath();
    }

    private BaseData loadBaseCsv(File baseFile) throws IOException {
        BaseData data = new BaseData();
        String headerLine = readHeaderLine(baseFile.toPath());
        if (headerLine == null || headerLine.isEmpty()) {
            throw new IOException("El CSV base esta vacio.");
        }
        headerLine = stripBom(headerLine);

        String sourceDelimiter = detectDelimiter(headerLine);
        data.delimiter = ",";
        String headerNoEol = stripEol(headerLine);
        data.trailingDelimiter = false;
        data.headerLine = headerNoEol;

        List<String> sourceHeaderFields = splitPreserveAll(headerNoEol, sourceDelimiter);
        Map<String, Integer> sourceHeaderMap = buildHeaderMap(sourceHeaderFields);
        ensureHeaderColumns(sourceHeaderMap, "No se encontro la columna ASIN en el CSV base.",
                "No se encontro la columna IVA en el CSV base.");

        data.headerFields = buildOutputHeader();
        data.headerMap = buildHeaderMap(data.headerFields);

        int outputFechaIdx = data.headerMap.get("fecha");
        int outputAsinIdx = data.headerMap.get("asin");
        int outputIvaIdx = data.headerMap.get("iva");

        Integer sourceFechaIdx = sourceHeaderMap.get("fecha");
        int sourceAsinIdx = sourceHeaderMap.get("asin");
        int sourceIvaIdx = sourceHeaderMap.get("iva");

        data.baseMap = new LinkedHashMap<>();
        data.baseDuplicates = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(baseFile),
                StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            if (first == null) {
                data.baseOriginalRows = 0;
                return data;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                data.baseOriginalRows++;
                List<String> row = splitPreserveAll(line, sourceDelimiter);
                padRow(row, sourceHeaderFields.size());

                String asin = row.get(sourceAsinIdx).trim();
                String iva = row.get(sourceIvaIdx).trim();
                if (asin.isEmpty()) {
                    continue;
                }
                String fecha = sourceFechaIdx == null ? "" : Objects.toString(row.get(sourceFechaIdx), "").trim();

                String asinNorm = asin.toUpperCase(Locale.ROOT);
                String ivaNorm = normalizeIva(iva);
                BaseRecord existing = data.baseMap.get(asinNorm);
                if (existing != null) {
                    data.baseDuplicates.add(asinNorm);
                    if (!"SI".equals(existing.iva) && "SI".equals(ivaNorm)) {
                        existing.iva = "SI";
                        existing.rowValues.set(outputIvaIdx, "SI");
                        if (!fecha.isEmpty()) {
                            existing.rowValues.set(outputFechaIdx, fecha);
                        }
                    }
                    continue;
                }

                List<String> normalizedRow = emptyRow(data.headerFields.size());
                normalizedRow.set(outputFechaIdx, fecha);
                normalizedRow.set(outputAsinIdx, asinNorm);
                normalizedRow.set(outputIvaIdx, ivaNorm);
                data.baseMap.put(asinNorm, new BaseRecord(asinNorm, ivaNorm, normalizedRow));
            }
        }

        return data;
    }

    private BaseData loadBaseXlsx(File baseFile, String selectedSheet) throws IOException {
        XlsxZip zip = XlsxZip.read(baseFile.toPath());
        XlsxZip.SheetRef sheetRef = zip.resolveSheet(selectedSheet == null ? BASE_SHEET_NAME : selectedSheet);

        BaseData data = new BaseData();
        data.delimiter = ",";
        data.trailingDelimiter = false;
        data.baseMap = new LinkedHashMap<>();
        data.baseDuplicates = new ArrayList<>();

        Document sheetDoc = parseXml(zip.requireEntry(sheetRef.path));
        List<String> sharedStrings = zip.readSharedStrings();

        Map<Integer, String> headerMapByIndex = readSheetRowValues(sheetDoc, 1, sharedStrings);
        int sourceMaxColumn = maxColumnIndex(headerMapByIndex.keySet()) + 1;
        if (sourceMaxColumn < 1) {
            sourceMaxColumn = 2;
        }

        List<String> sourceHeaderFields = new ArrayList<>();
        for (int i = 0; i < sourceMaxColumn; i++) {
            sourceHeaderFields.add(Objects.toString(headerMapByIndex.get(i), ""));
        }
        Map<String, Integer> sourceHeaderMap = buildHeaderMap(sourceHeaderFields);
        ensureHeaderColumns(sourceHeaderMap, "No se encontro la columna ASIN en la hoja base.",
                "No se encontro la columna IVA en la hoja base.");

        data.headerFields = buildOutputHeader();
        data.headerMap = buildHeaderMap(data.headerFields);

        int outputFechaIdx = data.headerMap.get("fecha");
        int outputAsinIdx = data.headerMap.get("asin");
        int outputIvaIdx = data.headerMap.get("iva");

        Integer sourceFechaIdx = sourceHeaderMap.get("fecha");
        int sourceAsinIdx = sourceHeaderMap.get("asin");
        int sourceIvaIdx = sourceHeaderMap.get("iva");

        data.baseOriginalRows = 0;
        Element sheetData = findFirstElementByLocalName(sheetDoc.getDocumentElement(), "sheetData");
        if (sheetData != null) {
            NodeList rows = sheetData.getChildNodes();
            for (int i = 0; i < rows.getLength(); i++) {
                Node n = rows.item(i);
                if (n.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element row = (Element) n;
                if (!"row".equals(row.getLocalName())) {
                    continue;
                }
                int rowNumber = parseInt(row.getAttribute("r"), -1);
                if (rowNumber >= 0 && rowNumber < 2) {
                    continue;
                }

                Map<Integer, String> values = readRowValues(row, sharedStrings);
                String asin = Objects.toString(values.get(sourceAsinIdx), "").trim();
                String iva = Objects.toString(values.get(sourceIvaIdx), "").trim();
                if (asin.isEmpty()) {
                    continue;
                }
                String fecha = sourceFechaIdx == null
                        ? ""
                        : Objects.toString(values.get(sourceFechaIdx), "").trim();

                data.baseOriginalRows++;
                String asinNorm = asin.toUpperCase(Locale.ROOT);
                String ivaNorm = normalizeIva(iva);
                BaseRecord existing = data.baseMap.get(asinNorm);
                if (existing != null) {
                    data.baseDuplicates.add(asinNorm);
                    if (!"SI".equals(existing.iva) && "SI".equals(ivaNorm)) {
                        existing.iva = "SI";
                        existing.rowValues.set(outputIvaIdx, "SI");
                        if (!fecha.isEmpty()) {
                            existing.rowValues.set(outputFechaIdx, fecha);
                        }
                    }
                } else {
                    List<String> rowData = emptyRow(data.headerFields.size());
                    rowData.set(outputFechaIdx, fecha);
                    rowData.set(outputAsinIdx, asinNorm);
                    rowData.set(outputIvaIdx, ivaNorm);
                    data.baseMap.put(asinNorm, new BaseRecord(asinNorm, ivaNorm, rowData));
                }
            }
        }

        XlsxContext xlsx = new XlsxContext();
        xlsx.sheetName = sheetRef.name;
        xlsx.sheetEntryPath = sheetRef.path;
        xlsx.zip = zip;
        xlsx.maxColumn = 3;
        data.xlsx = xlsx;
        return data;
    }

    private ReportStats loadReports(List<File> reportFiles) throws IOException {
        ReportStats stats = new ReportStats();
        stats.reportFilesProcessed = reportFiles.size();
        LinkedHashMap<String, ReportCandidate> winners = new LinkedHashMap<>();

        for (int fileIndex = 0; fileIndex < reportFiles.size(); fileIndex++) {
            File reporteTxt = reportFiles.get(fileIndex);
            String headerLine = readHeaderLine(reporteTxt.toPath());
            if (headerLine == null || headerLine.isEmpty()) {
                throw new IOException("El reporte esta vacio: " + reporteTxt.getAbsolutePath());
            }
            headerLine = stripBom(headerLine);

            String delimiter = detectDelimiter(headerLine);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(reporteTxt), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("El reporte no tiene encabezados: " + reporteTxt.getAbsolutePath());
                }
                line = stripBom(line);
                List<String> rawHeaders = splitPreserveAll(line, delimiter);
                List<String> normalized = new ArrayList<>();
                for (String h : rawHeaders) {
                    normalized.add(normalizeHeader(h));
                }
                Map<String, Integer> map = new HashMap<>();
                for (int i = 0; i < normalized.size(); i++) {
                    map.putIfAbsent(normalized.get(i), i);
                }

                List<String> required = Arrays.asList("asin", "item-tax", "order-status");
                List<String> missing = new ArrayList<>();
                for (String req : required) {
                    if (!map.containsKey(req)) {
                        missing.add(req);
                    }
                }
                if (!missing.isEmpty()) {
                    throw new IOException(
                            "Faltan columnas en el reporte " + reporteTxt.getName() + ": " + String.join(", ", missing));
                }

                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    stats.totalRows++;
                    List<String> row = splitPreserveAll(line, delimiter);
                    padRow(row, rawHeaders.size());

                    String status = row.get(map.get("order-status")).trim();
                    if (isCancelled(status)) {
                        stats.cancelledRows++;
                        String cancelledAsin = row.get(map.get("asin")).trim().toUpperCase(Locale.ROOT);
                        if (!cancelledAsin.isEmpty()) {
                            stats.cancelledAsins.add(cancelledAsin);
                        }
                        continue;
                    }

                    String asin = row.get(map.get("asin")).trim();
                    if (asin.isEmpty()) {
                        stats.noAsinRows++;
                        continue;
                    }
                    String asinNorm = asin.toUpperCase(Locale.ROOT);
                    String iva = hasTax(row.get(map.get("item-tax"))) ? "SI" : "NO";
                    Instant timestamp = resolveReportTimestamp(row, map);

                    ReportCandidate candidate = new ReportCandidate(asinNorm, iva, timestamp, fileIndex);
                    ReportCandidate winner = winners.get(asinNorm);
                    if (winner == null) {
                        winners.put(asinNorm, candidate);
                    } else {
                        stats.duplicateRows++;
                        if (isBetterCandidate(candidate, winner)) {
                            winners.put(asinNorm, candidate);
                        }
                    }
                }
            }
        }

        for (ReportCandidate winner : winners.values()) {
            stats.reportMap.put(winner.asin, winner.iva);
        }
        return stats;
    }

    private Instant resolveReportTimestamp(List<String> row, Map<String, Integer> map) {
        String primary = valueAt(row, map.get("last-updated-date"));
        Instant primaryTs = parseReportInstant(primary);
        if (primaryTs != null) {
            return primaryTs;
        }
        String fallback = valueAt(row, map.get("purchase-date"));
        Instant fallbackTs = parseReportInstant(fallback);
        return fallbackTs == null ? Instant.EPOCH : fallbackTs;
    }

    private String valueAt(List<String> row, Integer index) {
        if (index == null || index < 0 || index >= row.size()) {
            return "";
        }
        return Objects.toString(row.get(index), "").trim();
    }

    private Instant parseReportInstant(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ex) {
            // intenta otros formatos comunes
        }
        try {
            return ZonedDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ex) {
            // intenta fecha local sin zona
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private boolean isBetterCandidate(ReportCandidate candidate, ReportCandidate winner) {
        int compare = candidate.timestamp.compareTo(winner.timestamp);
        if (compare > 0) {
            return true;
        }
        if (compare < 0) {
            return false;
        }
        if ("SI".equals(candidate.iva) && "NO".equals(winner.iva)) {
            return true;
        }
        if ("NO".equals(candidate.iva) && "SI".equals(winner.iva)) {
            return false;
        }
        return false;
    }

    private ApplyStats applyReportToBase(BaseData baseData, LinkedHashMap<String, String> reportMap, String processDate) {
        ApplyStats apply = new ApplyStats();
        apply.firstNewIndex = -1;
        LinkedHashMap<String, BaseRecord> baseMap = baseData.baseMap;
        int fechaIdx = baseData.headerMap.get("fecha");
        int asinIdx = baseData.headerMap.get("asin");
        int ivaIdx = baseData.headerMap.get("iva");

        for (Map.Entry<String, String> entry : reportMap.entrySet()) {
            String asin = entry.getKey();
            String iva = entry.getValue();

            BaseRecord existing = baseMap.get(asin);
            if (existing != null) {
                if (!Objects.equals(existing.iva, iva)) {
                    apply.modified.add(new String[] { asin, existing.iva, iva });
                    existing.iva = iva;
                    existing.rowValues.set(ivaIdx, iva);
                    existing.rowValues.set(fechaIdx, processDate);
                } else {
                    apply.unchanged++;
                }
            } else {
                if (apply.firstNewIndex < 0) {
                    apply.firstNewIndex = baseMap.size();
                }
                List<String> newRow = emptyRow(baseData.headerFields.size());
                newRow.set(fechaIdx, processDate);
                newRow.set(asinIdx, asin);
                newRow.set(ivaIdx, iva);
                baseMap.put(asin, new BaseRecord(asin, iva, newRow));
                apply.added.add(new String[] { asin, iva });
            }
        }

        if (apply.firstNewIndex < 0) {
            apply.firstNewIndex = 0;
        }
        return apply;
    }

    private void writeBaseCsv(Path basePath, BaseData baseData) throws IOException {
        ensureParent(basePath);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(basePath.toFile()), StandardCharsets.UTF_8))) {
            writer.write(joinRow(baseData.headerFields, baseData.delimiter, baseData.trailingDelimiter));
            writer.write("\r\n");
            for (BaseRecord record : baseData.baseMap.values()) {
                String serialized = joinRow(record.rowValues, baseData.delimiter, baseData.trailingDelimiter);
                writer.write(serialized);
                writer.write("\r\n");
            }
        }
    }

    private void writeBaseXlsx(Path basePath, BaseData baseData, ApplyStats applyStats) throws IOException {
        XlsxContext xlsx = baseData.xlsx;
        if (xlsx == null) {
            throw new IOException("Contexto XLSX no disponible.");
        }

        Document sheetDoc = parseXml(xlsx.zip.requireEntry(xlsx.sheetEntryPath));
        Element worksheet = sheetDoc.getDocumentElement();
        Element sheetData = findFirstElementByLocalName(worksheet, "sheetData");
        if (sheetData == null) {
            throw new IOException("Hoja XLSX invalida: no contiene sheetData.");
        }

        List<Element> existingRows = childElementsByLocalName(sheetData, "row");
        for (Element row : existingRows) {
            int rowNumber = parseInt(row.getAttribute("r"), -1);
            if (rowNumber >= 2) {
                sheetData.removeChild(row);
            }
        }

        int fechaCol1 = baseData.headerMap.get("fecha") + 1;
        int asinCol1 = baseData.headerMap.get("asin") + 1;
        int ivaCol1 = baseData.headerMap.get("iva") + 1;
        int rowNumber = 2;
        for (BaseRecord record : baseData.baseMap.values()) {
            Element row = sheetDoc.createElementNS(worksheet.getNamespaceURI(), "row");
            row.setAttribute("r", Integer.toString(rowNumber));

            row.appendChild(createInlineStringCell(sheetDoc, worksheet.getNamespaceURI(), fechaCol1, rowNumber,
                    Objects.toString(record.rowValues.get(baseData.headerMap.get("fecha")), "")));
            row.appendChild(createInlineStringCell(sheetDoc, worksheet.getNamespaceURI(), asinCol1, rowNumber, record.asin));
            row.appendChild(createInlineStringCell(sheetDoc, worksheet.getNamespaceURI(), ivaCol1, rowNumber, record.iva));

            sheetData.appendChild(row);
            rowNumber++;
        }

        int lastColumn = Math.max(xlsx.maxColumn, Math.max(fechaCol1, Math.max(asinCol1, ivaCol1)));
        int lastRow = Math.max(1, baseData.baseMap.size() + 1);
        updateDimension(sheetDoc, worksheet.getNamespaceURI(), lastColumn, lastRow);

        updateWorkbookMetadata(xlsx.zip);
        xlsx.zip.putEntry(xlsx.sheetEntryPath, toXmlBytes(sheetDoc));
        xlsx.zip.removeEntry("xl/calcChain.xml");
        removeCalcChainRelationship(xlsx.zip);

        Path tmp = Path.of(basePath.toString() + ".tmp");
        if (Files.exists(tmp)) {
            Files.delete(tmp);
        }
        xlsx.zip.write(tmp);
        Files.move(tmp, basePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private void writePreviewCsv(Path previewPath, BaseData baseData, int startIndex) throws IOException {
        ensureParent(previewPath);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(previewPath.toFile()), StandardCharsets.UTF_8))) {
            String headerLine = joinRow(baseData.headerFields, baseData.delimiter, baseData.trailingDelimiter);
            writer.write(headerLine);
            writer.write("\r\n");

            int idx = 0;
            for (BaseRecord record : baseData.baseMap.values()) {
                if (idx++ < startIndex) {
                    continue;
                }
                writer.write(joinRow(record.rowValues, baseData.delimiter, baseData.trailingDelimiter));
                writer.write("\r\n");
            }
        }
    }

    private Map<String, String> buildResumen(BaseData baseData, ReportStats reportStats, ApplyStats applyStats,
            int cancelledAsins) {
        Map<String, String> resumen = new LinkedHashMap<>();
        resumen.put("ok", "true");
        resumen.put("total_reporte", Integer.toString(reportStats.totalRows));
        resumen.put("duplicados_filas", Integer.toString(reportStats.duplicateRows));
        resumen.put("cancelados_filas", Integer.toString(reportStats.cancelledRows));
        resumen.put("cancelados_asins", Integer.toString(cancelledAsins));
        resumen.put("sin_asin_filas", Integer.toString(reportStats.noAsinRows));
        resumen.put("asin_unicos_reporte", Integer.toString(reportStats.reportMap.size()));
        resumen.put("agregados", Integer.toString(applyStats.added.size()));
        resumen.put("modificados", Integer.toString(applyStats.modified.size()));
        resumen.put("sin_cambios", Integer.toString(applyStats.unchanged));
        resumen.put("consolidados_base", Integer.toString(new LinkedHashSet<>(baseData.baseDuplicates).size()));
        resumen.put("eliminados_base", Integer.toString(baseData.baseDuplicates.size()));
        resumen.put("base_original", Integer.toString(baseData.baseOriginalRows));
        resumen.put("base_final", Integer.toString(baseData.baseMap.size()));
        resumen.put("preview_inicio", Integer.toString(applyStats.firstNewIndex));
        return resumen;
    }

    private void writeProperties(Path path, Map<String, String> data) throws IOException {
        ensureParent(path);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(path.toFile()), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, String> e : data.entrySet()) {
                writer.write(e.getKey() + "=" + e.getValue());
                writer.write('\n');
            }
        }
    }

    private void writeReport(Path reportPath, File baseFile, String baseType, String sheetName, List<File> reportFiles,
            Path generatedCsv, List<Path> copiedReportPaths, Map<String, String> resumen, List<String[]> added,
            List<String[]> modified, Set<String> cancelledOnly, List<String> baseDuplicates, int previewStartIndex,
            String processDate)
            throws IOException {
        ensureParent(reportPath);

        Map<String, Integer> removedCounts = new LinkedHashMap<>();
        for (String asin : baseDuplicates) {
            removedCounts.put(asin, removedCounts.getOrDefault(asin, 0) + 1);
        }

        List<String> lines = new ArrayList<>();
        lines.add("REPORTE IVA PROCESS");
        lines.add("Fecha/Hora: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        lines.add("");
        lines.add("RESUMEN GENERAL");
        lines.add("Base: " + baseFile.getAbsolutePath());
        lines.add("Directorio base origen: " + Objects.toString(baseFile.getParent(), ""));
        lines.add("Tipo base: " + baseType);
        if ("XLSX".equals(baseType)) {
            lines.add("Hoja usada: " + Objects.toString(sheetName, ""));
        }
        lines.add("Reportes inventario procesados: " + reportFiles.size());
        for (int i = 0; i < reportFiles.size(); i++) {
            File report = reportFiles.get(i);
            lines.add("Reporte inventario [" + (i + 1) + "]: " + report.getAbsolutePath());
            lines.add("Directorio reporte origen [" + (i + 1) + "]: " + Objects.toString(report.getParent(), ""));
        }
        lines.add("CSV generado: " + generatedCsv.toString());
        lines.add("Fecha aplicada a altas/modificaciones IVA: " + processDate);
        if (copiedReportPaths == null || copiedReportPaths.isEmpty()) {
            lines.add("Reporte Amazon copiado: ");
        } else {
            for (int i = 0; i < copiedReportPaths.size(); i++) {
                lines.add("Reporte Amazon copiado [" + (i + 1) + "]: " + copiedReportPaths.get(i));
            }
        }
        lines.add("Carpeta de salida del proceso: " + reportPath.getParent().toString());
        lines.add("Total filas en reporte: " + resumen.get("total_reporte"));
        lines.add("Filas canceladas: " + resumen.get("cancelados_filas") + " (ASIN unicos: "
                + resumen.get("cancelados_asins") + ")");
        lines.add("Filas sin ASIN: " + resumen.get("sin_asin_filas"));
        lines.add("Filas duplicadas en reporte: " + resumen.get("duplicados_filas"));
        lines.add("ASIN unicos procesados: " + resumen.get("asin_unicos_reporte"));
        lines.add("Agregados nuevos: " + resumen.get("agregados"));
        lines.add("Modificados (IVA cambiado): " + resumen.get("modificados"));
        lines.add("Sin cambios (IVA igual): " + resumen.get("sin_cambios"));
        lines.add("Duplicados en base consolidados: " + resumen.get("consolidados_base"));
        lines.add("Eliminados de base (filas): " + resumen.get("eliminados_base"));
        lines.add("Total base antes: " + resumen.get("base_original"));
        lines.add("Total base despues: " + resumen.get("base_final"));
        lines.add("Vista previa inicia en fila (sin encabezado): " + (previewStartIndex + 1));
        lines.add("");

        lines.add("PRODUCTOS AGREGADOS (ASIN,IVA)");
        lines.add("ASIN,IVA");
        for (String[] row : added) {
            lines.add(row[0] + "," + row[1]);
        }
        lines.add("");

        lines.add("PRODUCTOS MODIFICADOS (ASIN,IVA_ANTERIOR,IVA_NUEVO)");
        lines.add("ASIN,IVA_ANTERIOR,IVA_NUEVO");
        for (String[] row : modified) {
            lines.add(row[0] + "," + row[1] + "," + row[2]);
        }
        lines.add("");

        lines.add("PRODUCTOS NO PROCESADOS (ASIN,MOTIVO)");
        lines.add("ASIN,MOTIVO");
        List<String> cancelled = new ArrayList<>(cancelledOnly);
        Collections.sort(cancelled);
        for (String asin : cancelled) {
            lines.add(asin + ",CANCELADO");
        }
        int sinAsin = parseInt(resumen.get("sin_asin_filas"), 0);
        if (sinAsin > 0) {
            lines.add(",SIN_ASIN (filas=" + sinAsin + ")");
        }
        lines.add("");

        lines.add("PRODUCTOS ELIMINADOS DE LA BASE (ASIN,ELIMINADOS)");
        lines.add("ASIN,ELIMINADOS");
        for (Map.Entry<String, Integer> e : removedCounts.entrySet()) {
            lines.add(e.getKey() + "," + e.getValue());
        }
        lines.add("");

        if (!baseDuplicates.isEmpty()) {
            lines.add("DUPLICADOS EN BASE CONSOLIDADOS (ASIN)");
            lines.add("ASIN");
            List<String> unique = new ArrayList<>(new LinkedHashSet<>(baseDuplicates));
            unique.sort(String::compareTo);
            lines.addAll(unique);
            lines.add("");
        }

        Files.write(reportPath, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private OutputLayout resolveOutputLayout(Path basePath, File outputRootDirectory) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(DateTimeFormatter.ofPattern("HHmm MM-dd-yyyy"));
        Path detectedOriginRoot = detectExistingOriginRoot(basePath);
        Path targetRootBase;
        if (detectedOriginRoot != null) {
            // Si la base ya esta dentro de una jerarquia valida, se reutiliza ese origen.
            targetRootBase = detectedOriginRoot;
        } else {
            targetRootBase = outputRootDirectory == null
                    ? basePath.getParent()
                    : outputRootDirectory.toPath();
        }
        if (targetRootBase == null) {
            targetRootBase = Path.of(System.getProperty("user.dir"));
        }

        OutputLayout layout = new OutputLayout();
        layout.rootFolder = targetRootBase.resolve("Bases de datos de IVAS");
        layout.yearFolder = layout.rootFolder.resolve(Integer.toString(now.getYear()));
        layout.monthFolder = layout.yearFolder.resolve(monthNameEs(now.getMonth()));
        Files.createDirectories(layout.monthFolder);
        layout.timestamp = timestamp;

        layout.generatedCsv = ensureUnique(layout.monthFolder,
                "Base de Datos IVA Amazon " + timestamp,
                ".csv");
        layout.generatedLog = replaceExtension(layout.generatedCsv, ".log");
        return layout;
    }

    private Path detectExistingOriginRoot(Path basePath) {
        if (basePath == null) {
            return null;
        }
        Path current = basePath.getParent();
        Path selectedRoot = null;

        while (current != null) {
            Path yearFolder = current.getParent();
            Path basesFolder = yearFolder == null ? null : yearFolder.getParent();
            Path rootFolder = basesFolder == null ? null : basesFolder.getParent();

            if (yearFolder != null && basesFolder != null && rootFolder != null
                    && isMonthFolderName(current.getFileName())
                    && isYearFolderName(yearFolder.getFileName())
                    && isBasesFolderName(basesFolder.getFileName())) {
                // Se conserva la coincidencia mas externa (mas cercana al origen de la ruta).
                selectedRoot = rootFolder;
            }

            current = current.getParent();
        }

        return selectedRoot;
    }

    private boolean isBasesFolderName(Path folderName) {
        return folderName != null && "bases de datos de ivas".equalsIgnoreCase(folderName.toString());
    }

    private boolean isYearFolderName(Path folderName) {
        if (folderName == null) {
            return false;
        }
        String value = folderName.toString().trim();
        if (value.length() != 4) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isMonthFolderName(Path folderName) {
        if (folderName == null) {
            return false;
        }
        String month = folderName.toString().trim().toLowerCase(Locale.ROOT);
        return "enero".equals(month)
                || "febrero".equals(month)
                || "marzo".equals(month)
                || "abril".equals(month)
                || "mayo".equals(month)
                || "junio".equals(month)
                || "julio".equals(month)
                || "agosto".equals(month)
                || "septiembre".equals(month)
                || "octubre".equals(month)
                || "noviembre".equals(month)
                || "diciembre".equals(month);
    }

    private List<Path> copyReportFiles(List<File> reportFiles, Path monthFolder, String timestamp) throws IOException {
        List<Path> copied = new ArrayList<>();
        for (int i = 0; i < reportFiles.size(); i++) {
            File report = reportFiles.get(i);
            String baseName = "Reporte de Amazon " + timestamp;
            if (i > 0) {
                baseName += " (" + (i + 1) + ")";
            }
            Path target = resolveUniqueWithBaseName(monthFolder, baseName, ".txt");
            Files.copy(report.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            copied.add(target);
        }
        return copied;
    }

    private Path resolveUniqueWithBaseName(Path parent, String baseName, String extension) {
        Path candidate = parent.resolve(baseName + extension);
        int counter = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + " (" + counter + ")" + extension);
            counter++;
        }
        return candidate;
    }

    private String joinPaths(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return "";
        }
        List<String> raw = new ArrayList<>();
        for (Path path : paths) {
            raw.add(path.toString());
        }
        return String.join("|", raw);
    }

    private Path ensureUnique(Path parent, String baseName, String extension) {
        Path candidate = parent.resolve(baseName + extension);
        int counter = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + " (" + counter + ")" + extension);
            counter++;
        }
        return candidate;
    }

    private Path replaceExtension(Path path, String extension) {
        String name = path.getFileName().toString();
        int idx = name.lastIndexOf('.');
        String clean = idx >= 0 ? name.substring(0, idx) : name;
        return path.getParent().resolve(clean + extension);
    }


    private String monthNameEs(Month month) {
        switch (month) {
            case JANUARY:
                return "Enero";
            case FEBRUARY:
                return "Febrero";
            case MARCH:
                return "Marzo";
            case APRIL:
                return "Abril";
            case MAY:
                return "Mayo";
            case JUNE:
                return "Junio";
            case JULY:
                return "Julio";
            case AUGUST:
                return "Agosto";
            case SEPTEMBER:
                return "Septiembre";
            case OCTOBER:
                return "Octubre";
            case NOVEMBER:
                return "Noviembre";
            case DECEMBER:
            default:
                return "Diciembre";
        }
    }

    private void ensureHeaderColumns(Map<String, Integer> headerMap, String asinError, String ivaError)
            throws IOException {
        if (!headerMap.containsKey("asin")) {
            throw new IOException(asinError);
        }
        if (!headerMap.containsKey("iva")) {
            throw new IOException(ivaError);
        }
    }

    private String readHeaderLine(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return reader.readLine();
        }
    }

    private String extensionOf(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        int idx = p.lastIndexOf('.');
        return idx >= 0 ? p.substring(idx) : "";
    }

    private String detectDelimiter(String sampleLine) {
        List<String> candidates = Arrays.asList("\t", ";", ",", "|");
        String best = ",";
        int bestCount = -1;
        for (String d : candidates) {
            int count = countChar(sampleLine, d.charAt(0));
            if (count > bestCount) {
                bestCount = count;
                best = d;
            }
        }
        return bestCount <= 0 ? "," : best;
    }

    private int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    private String normalizeHeader(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
    }

    private List<String> buildOutputHeader() {
        return Arrays.asList(OUTPUT_FECHA, OUTPUT_ASIN, OUTPUT_IVA);
    }

    private boolean isCancelled(String value) {
        return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).contains("cancel");
    }

    private boolean hasTax(String value) {
        String s = Objects.toString(value, "").trim();
        if (s.isEmpty()) {
            return false;
        }
        try {
            double num = Double.parseDouble(s.replace(",", ""));
            return num > 0;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    private String normalizeIva(String value) {
        String s = Objects.toString(value, "").trim().toUpperCase(Locale.ROOT);
        if (Arrays.asList("SI", "SÍ", "YES", "Y", "1", "TRUE").contains(s)) {
            return "SI";
        }
        if (Arrays.asList("NO", "N", "0", "FALSE").contains(s)) {
            return "NO";
        }
        return s;
    }

    private String stripEol(String line) {
        if (line == null) {
            return "";
        }
        if (line.endsWith("\r\n")) {
            return line.substring(0, line.length() - 2);
        }
        if (line.endsWith("\n") || line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    private String stripBom(String line) {
        if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private List<String> splitPreserveAll(String line, String delimiter) {
        String[] parts = line.split(Pattern.quote(delimiter), -1);
        return new ArrayList<>(Arrays.asList(parts));
    }

    private void padRow(List<String> row, int expected) {
        while (row.size() < expected) {
            row.add("");
        }
    }

    private List<String> emptyRow(int size) {
        List<String> row = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            row.add("");
        }
        return row;
    }

    private String joinRow(List<String> row, String delimiter, boolean trailingDelimiter) {
        String joined = String.join(delimiter, row);
        if (trailingDelimiter && (row.isEmpty() || !row.get(row.size() - 1).isEmpty())) {
            joined += delimiter;
        }
        return joined;
    }

    private Map<String, Integer> buildHeaderMap(List<String> headerFields) {
        Map<String, Integer> headerMap = new LinkedHashMap<>();
        for (int i = 0; i < headerFields.size(); i++) {
            String normalized = normalizeHeader(headerFields.get(i));
            if (!normalized.isEmpty()) {
                headerMap.putIfAbsent(normalized, i);
            }
        }
        return headerMap;
    }

    private void ensureParent(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    private Document parseXml(byte[] xmlBytes) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xmlBytes));
        } catch (ParserConfigurationException | SAXException ex) {
            throw new IOException("No se pudo parsear XML XLSX.", ex);
        }
    }

    private byte[] toXmlBytes(Document doc) throws IOException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException ex) {
            throw new IOException("No se pudo serializar XML XLSX.", ex);
        }
    }

    private Element findFirstElementByLocalName(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private List<Element> childElementsByLocalName(Element parent, String localName) {
        List<Element> list = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                list.add((Element) child);
            }
        }
        return list;
    }

    private Map<Integer, String> readSheetRowValues(Document sheetDoc, int targetRow, List<String> sharedStrings) {
        Element sheetData = findFirstElementByLocalName(sheetDoc.getDocumentElement(), "sheetData");
        if (sheetData == null) {
            return Collections.emptyMap();
        }
        List<Element> rows = childElementsByLocalName(sheetData, "row");
        for (Element row : rows) {
            int rowNumber = parseInt(row.getAttribute("r"), -1);
            if (rowNumber == targetRow) {
                return readRowValues(row, sharedStrings);
            }
        }
        return Collections.emptyMap();
    }

    private Map<Integer, String> readRowValues(Element row, List<String> sharedStrings) {
        Map<Integer, String> values = new HashMap<>();
        NodeList cells = row.getChildNodes();
        for (int i = 0; i < cells.getLength(); i++) {
            Node node = cells.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE || !"c".equals(node.getLocalName())) {
                continue;
            }
            Element cell = (Element) node;
            String ref = cell.getAttribute("r");
            int col = extractColumnIndex(ref);
            if (col < 0) {
                continue;
            }
            values.put(col, readCellValue(cell, sharedStrings));
        }
        return values;
    }

    private String readCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            Element is = findFirstElementByLocalName(cell, "is");
            if (is == null) {
                return "";
            }
            Element t = findFirstElementByLocalName(is, "t");
            return t == null ? "" : Objects.toString(t.getTextContent(), "");
        }

        Element v = findFirstElementByLocalName(cell, "v");
        if (v == null) {
            return "";
        }
        String raw = Objects.toString(v.getTextContent(), "");
        if ("s".equals(type)) {
            int idx = parseInt(raw, -1);
            if (idx >= 0 && idx < sharedStrings.size()) {
                return sharedStrings.get(idx);
            }
            return "";
        }
        return raw;
    }

    private int maxColumnIndex(Set<Integer> columns) {
        int max = -1;
        for (int c : columns) {
            if (c > max) {
                max = c;
            }
        }
        return max;
    }

    private int extractColumnIndex(String cellRef) {
        if (cellRef == null || cellRef.isEmpty()) {
            return -1;
        }
        int idx = 0;
        int value = 0;
        while (idx < cellRef.length()) {
            char ch = cellRef.charAt(idx);
            if (Character.isLetter(ch)) {
                value = value * 26 + (Character.toUpperCase(ch) - 'A' + 1);
                idx++;
            } else {
                break;
            }
        }
        return value > 0 ? value - 1 : -1;
    }

    private String columnToLetters(int oneBasedColumn) {
        int value = oneBasedColumn;
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int rem = (value - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            value = (value - 1) / 26;
        }
        return sb.toString();
    }

    private Element createInlineStringCell(Document doc, String ns, int colOneBased, int rowOneBased, String value) {
        Element c = doc.createElementNS(ns, "c");
        c.setAttribute("r", columnToLetters(colOneBased) + rowOneBased);
        c.setAttribute("t", "inlineStr");

        Element is = doc.createElementNS(ns, "is");
        Element t = doc.createElementNS(ns, "t");
        t.setTextContent(Objects.toString(value, ""));
        is.appendChild(t);
        c.appendChild(is);
        return c;
    }

    private void updateDimension(Document sheetDoc, String ns, int lastColumn, int lastRow) {
        Element worksheet = sheetDoc.getDocumentElement();
        Element dimension = findFirstElementByLocalName(worksheet, "dimension");
        if (dimension == null) {
            dimension = sheetDoc.createElementNS(ns, "dimension");
            Node firstChild = worksheet.getFirstChild();
            if (firstChild != null) {
                worksheet.insertBefore(dimension, firstChild);
            } else {
                worksheet.appendChild(dimension);
            }
        }
        String ref = "A1:" + columnToLetters(lastColumn) + lastRow;
        dimension.setAttribute("ref", ref);
    }

    private void updateWorkbookMetadata(XlsxZip zip) throws IOException {
        Document workbook = parseXml(zip.requireEntry("xl/workbook.xml"));
        Element root = workbook.getDocumentElement();
        String ns = root.getNamespaceURI();

        Element calcPr = findFirstElementByLocalName(root, "calcPr");
        if (calcPr == null) {
            calcPr = workbook.createElementNS(ns, "calcPr");
            root.appendChild(calcPr);
        }
        calcPr.setAttribute("fullCalcOnLoad", "1");
        calcPr.setAttribute("calcMode", "auto");

        Element workbookPr = findFirstElementByLocalName(root, "workbookPr");
        if (workbookPr == null) {
            workbookPr = workbook.createElementNS(ns, "workbookPr");
            root.appendChild(workbookPr);
        }
        workbookPr.setAttribute("updateLinks", "never");
        workbookPr.setAttribute("refreshAllConnections", "0");

        zip.putEntry("xl/workbook.xml", toXmlBytes(workbook));
    }

    private void removeCalcChainRelationship(XlsxZip zip) throws IOException {
        String relsPath = "xl/_rels/workbook.xml.rels";
        if (!zip.hasEntry(relsPath)) {
            return;
        }
        Document rels = parseXml(zip.requireEntry(relsPath));
        Element root = rels.getDocumentElement();
        List<Element> relNodes = childElementsByLocalName(root, "Relationship");
        for (Element rel : relNodes) {
            String type = rel.getAttribute("Type");
            String target = rel.getAttribute("Target");
            if (type.endsWith("/calcChain") || target.endsWith("calcChain.xml")) {
                root.removeChild(rel);
            }
        }
        zip.putEntry(relsPath, toXmlBytes(rels));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(Objects.toString(value, "").trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static class XlsxZip {
        private final LinkedHashMap<String, byte[]> entries;

        private XlsxZip(LinkedHashMap<String, byte[]> entries) {
            this.entries = entries;
        }

        static XlsxZip read(Path path) throws IOException {
            LinkedHashMap<String, byte[]> data = new LinkedHashMap<>();
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(path.toFile()))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = zis.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                    data.put(entry.getName(), out.toByteArray());
                }
            }
            return new XlsxZip(data);
        }

        byte[] requireEntry(String path) throws IOException {
            byte[] data = entries.get(path);
            if (data == null) {
                throw new IOException("No existe entrada XLSX requerida: " + path);
            }
            return data;
        }

        boolean hasEntry(String path) {
            return entries.containsKey(path);
        }

        void putEntry(String path, byte[] data) {
            entries.put(path, data);
        }

        void removeEntry(String path) {
            entries.remove(path);
        }

        List<String> listSheetNames() throws IOException {
            Document workbook = parseWorkbook();
            Element root = workbook.getDocumentElement();
            Element sheets = findFirstElementByLocalNameStatic(root, "sheets");
            if (sheets == null) {
                return Collections.emptyList();
            }
            List<Element> sheetNodes = childElementsByLocalNameStatic(sheets, "sheet");
            List<String> names = new ArrayList<>();
            for (Element s : sheetNodes) {
                names.add(Objects.toString(s.getAttribute("name"), ""));
            }
            return names;
        }

        SheetRef resolveSheet(String requestedName) throws IOException {
            Document workbook = parseWorkbook();
            Document rels = parseWorkbookRels();

            Map<String, String> relMap = new HashMap<>();
            for (Element rel : childElementsByLocalNameStatic(rels.getDocumentElement(), "Relationship")) {
                relMap.put(rel.getAttribute("Id"), rel.getAttribute("Target"));
            }

            Element sheets = findFirstElementByLocalNameStatic(workbook.getDocumentElement(), "sheets");
            if (sheets == null) {
                throw new IOException("Workbook XLSX sin hojas.");
            }
            List<Element> sheetNodes = childElementsByLocalNameStatic(sheets, "sheet");
            if (sheetNodes.isEmpty()) {
                throw new IOException("Workbook XLSX sin hojas.");
            }

            Element selected = null;
            for (Element s : sheetNodes) {
                String name = s.getAttribute("name");
                if (name != null && name.equalsIgnoreCase(requestedName)) {
                    selected = s;
                    break;
                }
            }
            if (selected == null) {
                List<String> names = new ArrayList<>();
                for (Element s : sheetNodes) {
                    names.add(s.getAttribute("name"));
                }
                throw new IOException("HOJA_NO_ENCONTRADA|" + String.join("|", names));
            }

            String relationshipId = selected.getAttributeNS(
                    "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
            if (relationshipId == null || relationshipId.isEmpty()) {
                relationshipId = selected.getAttribute("r:id");
            }
            String target = relMap.get(relationshipId);
            if (target == null || target.isEmpty()) {
                throw new IOException("No se pudo resolver la hoja XLSX seleccionada.");
            }

            String normalizedPath;
            if (target.startsWith("/")) {
                normalizedPath = target.substring(1);
            } else {
                normalizedPath = "xl/" + target;
            }
            normalizedPath = normalizedPath.replace("\\", "/");

            return new SheetRef(selected.getAttribute("name"), normalizedPath);
        }

        List<String> readSharedStrings() throws IOException {
            if (!entries.containsKey("xl/sharedStrings.xml")) {
                return Collections.emptyList();
            }
            Document doc = parseXmlStatic(entries.get("xl/sharedStrings.xml"));
            List<String> values = new ArrayList<>();
            NodeList sis = doc.getDocumentElement().getChildNodes();
            for (int i = 0; i < sis.getLength(); i++) {
                Node node = sis.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE || !"si".equals(node.getLocalName())) {
                    continue;
                }
                values.add(node.getTextContent() == null ? "" : node.getTextContent());
            }
            return values;
        }

        void write(Path target) throws IOException {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(target.toFile()))) {
                for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                    ZipEntry entry = new ZipEntry(e.getKey());
                    zos.putNextEntry(entry);
                    zos.write(e.getValue());
                    zos.closeEntry();
                }
            }
        }

        private Document parseWorkbook() throws IOException {
            return parseXmlStatic(requireEntry("xl/workbook.xml"));
        }

        private Document parseWorkbookRels() throws IOException {
            return parseXmlStatic(requireEntry("xl/_rels/workbook.xml.rels"));
        }

        private static Document parseXmlStatic(byte[] xmlBytes) throws IOException {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                return builder.parse(new ByteArrayInputStream(xmlBytes));
            } catch (ParserConfigurationException | SAXException ex) {
                throw new IOException("No se pudo parsear XML XLSX.", ex);
            }
        }

        private static Element findFirstElementByLocalNameStatic(Element parent, String localName) {
            if (parent == null) {
                return null;
            }
            NodeList children = parent.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                    return (Element) child;
                }
            }
            return null;
        }

        private static List<Element> childElementsByLocalNameStatic(Element parent, String localName) {
            List<Element> list = new ArrayList<>();
            NodeList children = parent.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                    list.add((Element) child);
                }
            }
            return list;
        }

        static class SheetRef {
            final String name;
            final String path;

            SheetRef(String name, String path) {
                this.name = name;
                this.path = path;
            }
        }
    }
}

