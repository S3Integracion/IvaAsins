package interfaz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import control.MotorIvaRunner;

public class Principal extends JFrame {

    private static final long serialVersionUID = 1L;

    private JPanel rootPanel;
    private JPanel panelTop;
    private JPanel panelFields;
    private JPanel panelButtons;
    private JPanel panelStatus;
    private JScrollPane scrollPane;
    private JTextField txtBase;
    private JTextField txtReporte;
    private JTable tablePreview;
    private JLabel lblStatus;
    private JButton btnPreview;
    private JButton btnClear;
    private JButton btnBuscarBase;
    private JButton btnBuscarReporte;
    private JButton btnHelp;
    private JMenuBar menuBar;
    private JMenu mnFile;
    private JMenuItem mntmManual;
    private JMenuItem mntmSalir;
    private JMenu mnTitle;

    private File tempPreview;
    private File tempResumen;
    private final MotorIvaRunner runner = new MotorIvaRunner();

    /**
     * Create the application.
     */
    public Principal() {
        $$$setupUI$$$();
        initialize();
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initialize() {
        ensureTablePreview();
        setBounds(100, 100, 1100, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(rootPanel);
        setLocationRelativeTo(null);
        setTitle(resolveWindowTitle());
        if (menuBar != null) {
            setJMenuBar(menuBar);
        }
        if (mntmManual != null) {
            mntmManual.addActionListener(e -> showManual());
        }
        if (mntmSalir != null) {
            mntmSalir.addActionListener(e -> System.exit(0));
        }
        if (btnHelp != null) {
            btnHelp.addActionListener(e -> showManual());
            btnHelp.setToolTipText("Manual de usuario");
        }

        applyFriendlyPalette();
        applyWindowIcon();

        btnBuscarBase.addActionListener(e -> onSelectBase());
        btnBuscarReporte.addActionListener(e -> onSelectReporte());
        btnPreview.addActionListener(e -> onProcess());
        btnClear.addActionListener(e -> onClear());

        installFileDrop(rootPanel);
        installFileDrop(panelTop);
        installFileDrop(scrollPane);
        installFileDrop(tablePreview);
        installFileDrop(txtBase);
        installFileDrop(txtReporte);
    }

    private void onSelectBase() {
        File file = chooseOpenFile("Selecciona la base (.csv o .xlsx)", new String[] { "csv", "xlsx" });
        if (file != null) {
            txtBase.setText(file.getAbsolutePath());
        }
    }

    private void onSelectReporte() {
        File file = chooseOpenFile("Selecciona el reporte .txt", new String[] { "txt" });
        if (file != null) {
            txtReporte.setText(file.getAbsolutePath());
        }
    }

    private void onProcess() {
        File base = getFileFromField(txtBase, "base (.csv o .xlsx)");
        if (base == null) {
            return;
        }
        if (!isCsvOrXlsx(base)) {
            showError("La base debe ser un archivo .csv o .xlsx.");
            return;
        }
        File reporte = getFileFromField(txtReporte, "reporte .txt");
        if (reporte == null) {
            return;
        }
        if (!isTxt(reporte)) {
            showError("El reporte debe ser un archivo .txt.");
            return;
        }
        String sheetName = resolveSheetName(base);
        if (isXlsx(base) && sheetName == null) {
            return;
        }
        runMotor(base, reporte, sheetName);
    }

    private void onClear() {
        txtBase.setText("");
        txtReporte.setText("");
        tablePreview.setModel(new DefaultTableModel());
        lblStatus.setText("Listo. Arrastra archivos .csv/.xlsx y .txt o usa Buscar.");
        tempPreview = null;
        tempResumen = null;
    }

    private void runMotor(File base, File reporte, String sheetName) {
        setButtonsEnabled(false);
        lblStatus.setText("Procesando...");

        SwingWorker<MotorIvaRunner.Resultado, Void> worker = new SwingWorker<MotorIvaRunner.Resultado, Void>() {
            @Override
            protected MotorIvaRunner.Resultado doInBackground() throws Exception {
                File tempDir = new File(System.getProperty("java.io.tmpdir"), "IvaAsins");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                tempPreview = new File(tempDir, "IvaAsins.preview.csv");
                tempResumen = new File(tempDir, "IvaAsins.resumen");
                File reporteOut = new File(base.getParentFile(), "Reporte_Iva_Process.txt");
                return runner.ejecutar(base, reporte, tempPreview, tempResumen, reporteOut, sheetName);
            }

            @Override
            protected void done() {
                setButtonsEnabled(true);
                try {
                    MotorIvaRunner.Resultado resultado = get();
                    if (!resultado.ok) {
                        showError(resultado.mensaje);
                        lblStatus.setText("Error en el proceso.");
                        return;
                    }
                    loadPreview(tempPreview, 100);
                    lblStatus.setText("Base actualizada. Agregados: " + resultado.agregados
                            + " | Modificados: " + resultado.modificados
                            + " | Reporte: Reporte_Iva_Process.txt");
                    showSummaryPopup(resultado);
                } catch (Exception ex) {
                    showError(ex.getMessage());
                    lblStatus.setText("Error en el proceso.");
                }
            }
        };
        worker.execute();
    }
    private File chooseOpenFile(String title, String[] extensions) {
        return chooseFile(title, extensions, false, null);
    }

    private File chooseFile(String title, String[] extensions, boolean save, String defaultName) {
        FileDialog dialog = new FileDialog(this, title, save ? FileDialog.SAVE : FileDialog.LOAD);
        if (defaultName != null) {
            dialog.setFile(defaultName);
        }
        if (!save && extensions != null && extensions.length > 0) {
            dialog.setFilenameFilter((dir, name) -> {
                String lower = name.toLowerCase();
                for (String ext : extensions) {
                    if (lower.endsWith("." + ext.toLowerCase())) {
                        return true;
                    }
                }
                return false;
            });
        }
        dialog.setVisible(true);
        String file = dialog.getFile();
        if (file == null) {
            return null;
        }
        File selected = new File(dialog.getDirectory(), file);
        return selected;
    }

    private File getFileFromField(JTextField field, String label) {
        String path = field.getText().trim();
        if (path.isEmpty()) {
            showError("Selecciona el archivo de " + label + ".");
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            showError("No se encontró el archivo de " + label + ".");
            return null;
        }
        return file;
    }

    private boolean isXlsx(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".xlsx");
    }

    private boolean isCsvOrXlsx(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".csv") || name.endsWith(".xlsx");
    }

    private boolean isTxt(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".txt");
    }

    private String resolveSheetName(File base) {
        if (!isXlsx(base)) {
            return null;
        }
        try {
            List<String> sheets = runner.listarHojas(base);
            if (sheets == null || sheets.isEmpty()) {
                showError("No se encontraron hojas en el archivo XLSX.");
                return null;
            }
            String target = "IVA's Base de Datos";
            for (String sheet : sheets) {
                if (sheet.equalsIgnoreCase(target)) {
                    return sheet;
                }
            }
            Object selection = JOptionPane.showInputDialog(this,
                    "No se encontró la hoja \"IVA's Base de Datos\". Selecciona una hoja:",
                    "Seleccionar hoja", JOptionPane.QUESTION_MESSAGE, null,
                    sheets.toArray(new String[0]), sheets.get(0));
            return selection == null ? null : selection.toString();
        } catch (Exception ex) {
            showError("No se pudieron leer las hojas del XLSX: " + ex.getMessage());
            return null;
        }
    }

    private void loadPreview(File file, int maxRows) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                tablePreview.setModel(new DefaultTableModel());
                return;
            }
            String delimiter = detectDelimiter(headerLine);
            String[] headers = splitLine(headerLine, delimiter);
            if (headers.length > 0 && headers[headers.length - 1].isEmpty()) {
                String[] trimmed = new String[headers.length - 1];
                System.arraycopy(headers, 0, trimmed, 0, trimmed.length);
                headers = trimmed;
            }
            DefaultTableModel model = new DefaultTableModel(headers, 0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < maxRows) {
                String[] row = splitLine(line, delimiter);
                if (row.length > headers.length) {
                    String[] trimmedRow = new String[headers.length];
                    System.arraycopy(row, 0, trimmedRow, 0, headers.length);
                    row = trimmedRow;
                } else if (row.length < headers.length) {
                    String[] padded = new String[headers.length];
                    System.arraycopy(row, 0, padded, 0, row.length);
                    row = padded;
                }
                model.addRow(row);
                count++;
            }
            tablePreview.setModel(model);
        }
    }

    private String detectDelimiter(String line) {
        int tabs = countChar(line, '\t');
        int semis = countChar(line, ';');
        int commas = countChar(line, ',');
        if (tabs >= semis && tabs >= commas) {
            return "\t";
        }
        if (semis >= commas) {
            return ";";
        }
        return ",";
    }

    private int countChar(String line, char ch) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    private String[] splitLine(String line, String delimiter) {
        return line.split(Pattern.quote(delimiter), -1);
    }

    private void setButtonsEnabled(boolean enabled) {
        btnPreview.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private String resolveWindowTitle() {
        if (mnTitle != null) {
            String title = mnTitle.getText();
            if (title != null && !title.trim().isEmpty()) {
                return title.trim();
            }
        }
        return "Iva Asins";
    }

    private void applyFriendlyPalette() {
        Color appBg = new Color(244, 247, 251);
        Color cardBg = new Color(255, 255, 255);
        Color accent = new Color(47, 111, 173);
        Color accentSoft = new Color(221, 231, 242);
        Color headerBg = new Color(234, 242, 248);
        Color border = new Color(197, 212, 227);
        Color textPrimary = new Color(31, 41, 55);
        Color textSecondary = new Color(75, 85, 99);
        Color selectionBg = new Color(214, 229, 247);

        rootPanel.setBackground(appBg);
        panelTop.setBackground(cardBg);
        panelTop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        if (panelFields != null) {
            panelFields.setOpaque(false);
        }
        panelButtons.setOpaque(false);
        panelStatus.setBackground(appBg);
        lblStatus.setForeground(textSecondary);

        styleTextField(txtBase, cardBg, border);
        styleTextField(txtReporte, cardBg, border);
        stylePrimaryButton(btnPreview, accent, Color.WHITE);
        styleSecondaryButton(btnClear, accentSoft, textPrimary, border);
        styleSecondaryButton(btnBuscarBase, accentSoft, textPrimary, border);
        styleSecondaryButton(btnBuscarReporte, accentSoft, textPrimary, border);
        styleHelpButton(btnHelp, accent, Color.WHITE);

        tablePreview.setRowHeight(22);
        tablePreview.setFillsViewportHeight(true);
        tablePreview.setGridColor(border);
        tablePreview.setSelectionBackground(selectionBg);
        tablePreview.setSelectionForeground(textPrimary);
        if (tablePreview.getTableHeader() != null) {
            tablePreview.getTableHeader().setBackground(headerBg);
            tablePreview.getTableHeader().setForeground(textPrimary);
        }
        JScrollPane ownerScrollPane = scrollPane;
        if (ownerScrollPane == null) {
            ownerScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tablePreview);
        }
        if (ownerScrollPane != null) {
            ownerScrollPane.getViewport().setBackground(cardBg);
            ownerScrollPane.setBorder(BorderFactory.createLineBorder(border));
        }
    }

    private void stylePrimaryButton(JButton button, Color background, Color foreground) {
        if (button == null) {
            return;
        }
        button.setBackground(background);
        button.setForeground(foreground);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
    }

    private void styleSecondaryButton(JButton button, Color background, Color foreground, Color border) {
        if (button == null) {
            return;
        }
        button.setBackground(background);
        button.setForeground(foreground);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
    }

    private void styleTextField(JTextField field, Color background, Color border) {
        if (field == null) {
            return;
        }
        field.setBackground(background);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    private void styleHelpButton(JButton button, Color background, Color foreground) {
        if (button == null) {
            return;
        }
        button.setBackground(background);
        button.setForeground(foreground);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        button.setPreferredSize(new Dimension(36, 30));
    }

    private void applyWindowIcon() {
        Path iconPath = resolveIconPath();
        if (iconPath == null) {
            return;
        }
        Image icon = Toolkit.getDefaultToolkit().getImage(iconPath.toString());
        if (icon != null) {
            setIconImage(icon);
        }
    }

    private void showManual() {
        try {
            String content = loadManualContent();
            JTextArea area = new JTextArea(content);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setCaretPosition(0);
            JScrollPane pane = new JScrollPane(area);
            pane.setPreferredSize(new Dimension(760, 520));
            JOptionPane.showMessageDialog(this, pane, "Manual de usuario", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            showError("No se pudo abrir el manual: " + ex.getMessage());
        }
    }

    private String loadManualContent() throws IOException {
        Path manualPath = resolveManualPath();
        if (manualPath == null) {
            throw new FileNotFoundException("No se encontro ManualUsuario.md.");
        }
        return new String(Files.readAllBytes(manualPath), StandardCharsets.UTF_8);
    }

    private Path resolveManualPath() {
        return resolveFilePath("ManualUsuario.md");
    }

    private Path resolveIconPath() {
        return resolveFilePath("IvaAsins.ico");
    }

    private Path resolveFilePath(String fileName) {
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path found = searchUpForFile(cwd, fileName, 6);
        if (found != null) {
            return found;
        }
        try {
            Path codePath = Paths.get(Principal.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath();
            if (Files.isRegularFile(codePath)) {
                codePath = codePath.getParent();
            }
            found = searchUpForFile(codePath, fileName, 6);
            if (found != null) {
                return found;
            }
        } catch (Exception ex) {
            // ignore
        }
        return null;
    }

    private Path searchUpForFile(Path start, String fileName, int maxDepth) {
        Path dir = start;
        for (int i = 0; i < maxDepth && dir != null; i++) {
            Path candidate = dir.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private void showSummaryPopup(MotorIvaRunner.Resultado resultado) {
        int total = resultado.totalReporte;
        int cancelados = resultado.canceladosFilas;
        int canceladosAsins = resultado.canceladosAsins;
        int sinAsin = resultado.sinAsinFilas;
        int duplicadasFilas = resultado.duplicadosFilas;
        int asinUnicos = resultado.asinUnicosReporte;
        int agregados = resultado.agregados;
        int modificados = resultado.modificados;
        int sinCambios = resultado.sinCambios;
        int consolidados = resultado.consolidadosBase;
        int eliminados = resultado.eliminadosBase;
        int baseOriginal = resultado.baseOriginal;
        int baseFinal = resultado.baseFinal;

        StringBuilder lines = new StringBuilder();
        lines.append("RESUMEN DE PROCESO\n\n");
        lines.append(String.format("%-28s %8d%n", "Total filas reporte", total));
        lines.append(String.format("%-28s %8d%n", "Cancelados (filas)", cancelados));
        lines.append(String.format("%-28s %8d%n", "Cancelados (ASIN)", canceladosAsins));
        lines.append(String.format("%-28s %8d%n", "Sin ASIN (filas)", sinAsin));
        lines.append(String.format("%-28s %8d%n", "Duplicados reporte", duplicadasFilas));
        lines.append(String.format("%-28s %8d%n", "ASIN unicos reporte", asinUnicos));
        lines.append("\n");
        lines.append(String.format("%-28s %8d%n", "Agregados nuevos", agregados));
        lines.append(String.format("%-28s %8d%n", "Modificados IVA", modificados));
        lines.append(String.format("%-28s %8d%n", "Sin cambios", sinCambios));
        lines.append(String.format("%-28s %8d%n", "Duplicados base", consolidados));
        lines.append(String.format("%-28s %8d%n", "Eliminados base", eliminados));
        lines.append("\n");
        lines.append(String.format("%-28s %8d%n", "Total base antes", baseOriginal));
        lines.append(String.format("%-28s %8d%n", "Total base despues", baseFinal));
        lines.append("\n");
        lines.append("Se genero Reporte_Iva_Process.txt junto a la base.");

        String html = "<html><pre>" + lines.toString();
        JOptionPane.showMessageDialog(this, html, "Resumen", JOptionPane.INFORMATION_MESSAGE);
    }

    private void installFileDrop(Component component) {
        if (component == null) {
            return;
        }
        // DropTarget is the most reliable way to accept file drags from Windows Explorer.
        component.setDropTarget(new DropTarget(component, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.rejectDrop();
                        return;
                    }
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    boolean ok = assignDroppedFiles(files);
                    dtde.dropComplete(ok);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                    showError("No se pudo importar el archivo: " + ex.getMessage());
                }
            }
        }, true, null));

        if (component instanceof JComponent) {
            // Also keep TransferHandler for cases like paste or non-native DnD.
            ((JComponent) component).setTransferHandler(new TransferHandler() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean canImport(TransferSupport support) {
                if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    return false;
                }
                if (support.isDrop()) {
                    support.setDropAction(DnDConstants.ACTION_COPY);
                }
                return true;
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) {
                    return false;
                }
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    return assignDroppedFiles(files);
                } catch (Exception ex) {
                    showError("No se pudo importar el archivo: " + ex.getMessage());
                    return false;
                }
            }
            });
        }
    }

    private boolean assignDroppedFiles(List<File> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        boolean assigned = false;
        for (File file : files) {
            if (file == null) {
                continue;
            }
            String name = file.getName().toLowerCase();
            if (name.endsWith(".csv") || name.endsWith(".xlsx")) {
                txtBase.setText(file.getAbsolutePath());
                assigned = true;
            } else if (name.endsWith(".txt")) {
                txtReporte.setText(file.getAbsolutePath());
                assigned = true;
            }
        }
        if (!assigned) {
            showError("Solo se aceptan archivos .csv/.xlsx o .txt.");
            return false;
        }
        lblStatus.setText("Archivos cargados por arrastre.");
        return true;
    }

    private void ensureTablePreview() {
        if (tablePreview != null) {
            return;
        }
        tablePreview = new JTable();
        if (scrollPane != null) {
            scrollPane.setViewportView(tablePreview);
        }
    }

    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }

    private void $$$setupUI$$$() {
        rootPanel = new JPanel();
        rootPanel.setLayout(new BorderLayout(10, 10));
        rootPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        panelTop = new JPanel();
        panelTop.setLayout(new BorderLayout(0, 8));
        rootPanel.add(panelTop, BorderLayout.NORTH);
        panelTop.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        panelFields = new JPanel();
        panelFields.setLayout(new GridLayoutManager(2, 3, new Insets(5, 5, 5, 5), -1, -1));
        panelTop.add(panelFields, BorderLayout.CENTER);

        JLabel lblBase = new JLabel();
        lblBase.setText("Base IVA (.csv o .xlsx)");
        panelFields.add(lblBase, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                null, null));

        txtBase = new JTextField();
        txtBase.setColumns(50);
        panelFields.add(txtBase, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK
                        | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        btnBuscarBase = new JButton();
        btnBuscarBase.setText("Buscar");
        btnBuscarBase.setPreferredSize(new Dimension(90, 28));
        panelFields.add(btnBuscarBase, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                null, null));

        JLabel lblReporte = new JLabel();
        lblReporte.setText("Reporte Amazon (.txt)");
        panelFields.add(lblReporte, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                null, null));

        txtReporte = new JTextField();
        txtReporte.setColumns(50);
        panelFields.add(txtReporte, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST,
                GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK
                        | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null));

        btnBuscarReporte = new JButton();
        btnBuscarReporte.setText("Buscar");
        btnBuscarReporte.setPreferredSize(new Dimension(90, 28));
        panelFields.add(btnBuscarReporte, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                null, null));

        panelButtons = new JPanel();
        panelButtons.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panelTop.add(panelButtons, BorderLayout.SOUTH);

        btnPreview = new JButton();
        btnPreview.setText("Procesar");
        panelButtons.add(btnPreview);

        btnClear = new JButton();
        btnClear.setText("Limpiar");
        panelButtons.add(btnClear);

        scrollPane = new JScrollPane();
        rootPanel.add(scrollPane, BorderLayout.CENTER);

        tablePreview = new JTable();
        scrollPane.setViewportView(tablePreview);

        panelStatus = new JPanel();
        panelStatus.setLayout(new BorderLayout());
        panelStatus.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        rootPanel.add(panelStatus, BorderLayout.SOUTH);

        lblStatus = new JLabel();
        lblStatus.setText("Listo. Arrastra archivos .csv/.xlsx y .txt o usa Buscar.");
        panelStatus.add(lblStatus, BorderLayout.CENTER);

        btnHelp = new JButton();
        btnHelp.setText("?");
        panelStatus.add(btnHelp, BorderLayout.EAST);

        menuBar = new JMenuBar();
        mnFile = new JMenu();
        mnFile.setText("File");
        menuBar.add(mnFile);
        mntmManual = new JMenuItem();
        mntmManual.setText("Manual");
        mnFile.add(mntmManual);
        mntmSalir = new JMenuItem();
        mntmSalir.setText("Salir");
        mnFile.add(mntmSalir);
        mnTitle = new JMenu();
        mnTitle.setText("Iva Asins");
        menuBar.add(mnTitle);
        panelTop.add(menuBar, BorderLayout.NORTH);

    }
}
