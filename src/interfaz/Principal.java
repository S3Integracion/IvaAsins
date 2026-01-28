package interfaz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
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
    private JButton btnExport;
    private JButton btnClear;
    private JButton btnExportRechazados;
    private JButton btnBuscarBase;
    private JButton btnBuscarReporte;

    private File tempOutput;
    private File tempResumen;
    private File tempRechazados;
    private File lastBase;
    private File lastReporte;
    private final MotorIvaRunner runner = new MotorIvaRunner();

    /**
     * Launch the application.
     */
    public static void main(String[] args) {
        applyModernStyle();
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    Principal window = new Principal();
                    window.setVisible(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void applyModernStyle() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // ignore
        }
        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("Button.font", base);
        UIManager.put("Label.font", base);
        UIManager.put("TextField.font", base);
        UIManager.put("Table.font", base);
        UIManager.put("TableHeader.font", base.deriveFont(Font.BOLD));
        UIManager.put("Menu.font", base);
        UIManager.put("MenuItem.font", base);
        UIManager.put("ScrollPane.font", base);
        UIManager.put("OptionPane.font", base);
        UIManager.put("OptionPane.messageFont", base);
        UIManager.put("OptionPane.buttonFont", base);
    }

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
        setTitle("Iva Asins");
        setBounds(100, 100, 1100, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(rootPanel);
        setLocationRelativeTo(null);

        Color appBg = new Color(245, 246, 248);
        Color cardBg = new Color(255, 255, 255);
        rootPanel.setBackground(appBg);
        panelTop.setBackground(cardBg);
        if (panelFields != null) {
            panelFields.setOpaque(false);
        }
        panelButtons.setOpaque(false);
        panelStatus.setBackground(appBg);

        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        JMenu mnFile = new JMenu("File");
        menuBar.add(mnFile);

        JMenuItem mntmManual = new JMenuItem("Manual");
        mnFile.add(mntmManual);

        JMenuItem mntmSalir = new JMenuItem("Salir");
        mntmSalir.addActionListener(e -> System.exit(0));
        mnFile.add(mntmSalir);

        JMenuItem mntmTitle = new JMenuItem("Iva Asins");
        menuBar.add(mntmTitle);

        tablePreview.setRowHeight(22);
        tablePreview.setFillsViewportHeight(true);
        tablePreview.setGridColor(new Color(225, 228, 232));
        JScrollPane ownerScrollPane = scrollPane;
        if (ownerScrollPane == null) {
            ownerScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tablePreview);
        }
        if (ownerScrollPane != null) {
            ownerScrollPane.getViewport().setBackground(Color.WHITE);
        }

        btnBuscarBase.addActionListener(e -> onSelectBase());
        btnBuscarReporte.addActionListener(e -> onSelectReporte());
        btnPreview.addActionListener(e -> onPreview());
        btnExport.addActionListener(e -> onExport());
        btnClear.addActionListener(e -> onClear());
        btnExportRechazados.addActionListener(e -> onExportRechazados());

        installFileDrop(rootPanel);
        installFileDrop(panelTop);
        installFileDrop(scrollPane);
        installFileDrop(tablePreview);
        installFileDrop(txtBase);
        installFileDrop(txtReporte);

        updateRechazadosButtonEnabled(false);
    }

    private void onSelectBase() {
        File file = chooseOpenFile("Selecciona el CSV base", "csv");
        if (file != null) {
            txtBase.setText(file.getAbsolutePath());
        }
    }

    private void onSelectReporte() {
        File file = chooseOpenFile("Selecciona el reporte .txt", "txt");
        if (file != null) {
            txtReporte.setText(file.getAbsolutePath());
        }
    }

    private void onPreview() {
        File base = getFileFromField(txtBase, "CSV base");
        if (base == null) {
            return;
        }
        File reporte = getFileFromField(txtReporte, "reporte .txt");
        if (reporte == null) {
            return;
        }

        runMotor(base, reporte, true, null);
    }

    private void onExport() {
        File base = getFileFromField(txtBase, "CSV base");
        if (base == null) {
            return;
        }
        File reporte = getFileFromField(txtReporte, "reporte .txt");
        if (reporte == null) {
            return;
        }

        File destino = chooseSaveFile("Guardar Asins_Taxes.csv", "Asins_Taxes.csv", "csv");
        if (destino == null) {
            return;
        }
        if (destino.exists()) {
            int option = JOptionPane.showConfirmDialog(this,
                    "El archivo ya existe. ¿Deseas reemplazarlo?", "Confirmar",
                    JOptionPane.YES_NO_OPTION);
            if (option != JOptionPane.YES_OPTION) {
                return;
            }
        }

        if (isPreviewValid(base, reporte)) {
            if (exportTempTo(destino)) {
                lblStatus.setText("Archivo exportado: " + destino.getName());
            }
            return;
        }

        runMotor(base, reporte, false, destino);
    }

    private void onClear() {
        txtBase.setText("");
        txtReporte.setText("");
        tablePreview.setModel(new DefaultTableModel());
        lblStatus.setText("Listo. Arrastra archivos .csv y .txt o usa Buscar.");
        tempOutput = null;
        tempResumen = null;
        tempRechazados = null;
        lastBase = null;
        lastReporte = null;
        updateRechazadosButtonEnabled(false);
    }

    private void runMotor(File base, File reporte, boolean showPreview, File exportDestino) {
        setButtonsEnabled(false);
        lblStatus.setText("Procesando...");

        SwingWorker<MotorIvaRunner.Resultado, Void> worker = new SwingWorker<MotorIvaRunner.Resultado, Void>() {
            @Override
            protected MotorIvaRunner.Resultado doInBackground() throws Exception {
                File tempDir = new File(System.getProperty("java.io.tmpdir"), "IvaAsins");
                if (!tempDir.exists()) {
                    tempDir.mkdirs();
                }
                tempOutput = new File(tempDir, "Asins_Taxes.csv");
                tempResumen = new File(tempDir, "Asins_Taxes.resumen");
                tempRechazados = new File(tempDir, "Asins_Taxes.rechazados.csv");
                return runner.ejecutar(base, reporte, tempOutput, tempResumen, tempRechazados);
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
                    lastBase = base;
                    lastReporte = reporte;
                    if (showPreview) {
                        loadPreview(tempOutput, 100);
                        lblStatus.setText("Listo. Filas generadas: " + resultado.procesados);
                        showSummaryPopup(resultado);
                    }
                    if (exportDestino != null) {
                        if (exportTempTo(exportDestino)) {
                            lblStatus.setText("Archivo exportado: " + exportDestino.getName());
                        } else {
                            return;
                        }
                    }
                    // El resumen ya incluye duplicados y cancelados.
                    if (!showPreview && exportDestino == null) {
                        lblStatus.setText("Listo. Filas generadas: " + resultado.procesados);
                    }
                    updateRechazadosButtonEnabled(resultado.rechazadosTotal > 0);
                } catch (Exception ex) {
                    showError(ex.getMessage());
                    lblStatus.setText("Error en el proceso.");
                    updateRechazadosButtonEnabled(false);
                }
            }
        };
        worker.execute();
    }

    private void onExportRechazados() {
        if (tempRechazados == null || !tempRechazados.exists()) {
            showError("No hay archivo de no procesados disponible. Ejecuta una previsualización primero.");
            return;
        }
        File destino = chooseSaveFile("Guardar Asins_Rechazados.csv", "Asins_Rechazados.csv", "csv");
        if (destino != null) {
            exportRechazadosTo(destino, tempRechazados);
        }
    }

    private boolean exportTempTo(File destino) {
        try {
            Files.copy(tempOutput.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(this, "Archivo exportado correctamente.");
            return true;
        } catch (IOException ex) {
            showError("No se pudo exportar el archivo: " + ex.getMessage());
            return false;
        }
    }

    private boolean exportRechazadosTo(File destino, File origen) {
        if (origen == null || !origen.exists()) {
            showError("No se encontró el archivo de rechazados.");
            return false;
        }
        if (destino.exists()) {
            int option = JOptionPane.showConfirmDialog(this,
                    "El archivo ya existe. ¿Deseas reemplazarlo?", "Confirmar",
                    JOptionPane.YES_NO_OPTION);
            if (option != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        try {
            Files.copy(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JOptionPane.showMessageDialog(this, "Archivo de rechazados exportado correctamente.");
            return true;
        } catch (IOException ex) {
            showError("No se pudo exportar el archivo de rechazados: " + ex.getMessage());
            return false;
        }
    }

    private void updateRechazadosButtonEnabled(boolean enabled) {
        if (btnExportRechazados != null) {
            btnExportRechazados.setEnabled(enabled && tempRechazados != null && tempRechazados.exists());
        }
    }

    private boolean isPreviewValid(File base, File reporte) {
        return tempOutput != null && tempOutput.exists()
                && sameFile(base, lastBase)
                && sameFile(reporte, lastReporte);
    }

    private boolean sameFile(File a, File b) {
        if (a == null || b == null) {
            return false;
        }
        return a.getAbsolutePath().equalsIgnoreCase(b.getAbsolutePath());
    }

    private File chooseOpenFile(String title, String extension) {
        return chooseFile(title, extension, false, null);
    }

    private File chooseSaveFile(String title, String defaultName, String extension) {
        return chooseFile(title, extension, true, defaultName);
    }

    private File chooseFile(String title, String extension, boolean save, String defaultName) {
        FileDialog dialog = new FileDialog(this, title, save ? FileDialog.SAVE : FileDialog.LOAD);
        if (defaultName != null) {
            dialog.setFile(defaultName);
        }
        if (!save && extension != null) {
            dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith("." + extension));
        }
        dialog.setVisible(true);
        String file = dialog.getFile();
        if (file == null) {
            return null;
        }
        File selected = new File(dialog.getDirectory(), file);
        if (save && extension != null && !selected.getName().toLowerCase().endsWith("." + extension)) {
            selected = new File(selected.getParentFile(), selected.getName() + "." + extension);
        }
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
        btnExport.setEnabled(enabled);
        btnClear.setEnabled(enabled);
        if (btnExportRechazados != null) {
            btnExportRechazados.setEnabled(enabled && tempRechazados != null && tempRechazados.exists());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showDuplicatesPopup(MotorIvaRunner.Resultado resultado) {
        StringBuilder msg = new StringBuilder();
        msg.append("Se encontraron ASIN duplicados en el reporte.\n");
        msg.append("Se conservaron los que tienen IVA=SI.\n");
        msg.append("Total de duplicados: ").append(resultado.duplicados);
        String list = resultado.duplicadosAsin == null ? "" : resultado.duplicadosAsin.trim();
        if (!list.isEmpty()) {
            String[] items = list.split(",");
            int limit = Math.min(items.length, 10);
            msg.append("\nEjemplos: ");
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    msg.append(", ");
                }
                msg.append(items[i].trim());
            }
            if (items.length > limit) {
                msg.append(" ...");
            }
        }
        JOptionPane.showMessageDialog(this, msg.toString(), "Duplicados", JOptionPane.WARNING_MESSAGE);
    }

    private void showSummaryPopup(MotorIvaRunner.Resultado resultado) {
        int total = resultado.totalReporte;
        int cancelados = resultado.saltadosCancelados;
        int sinAsin = resultado.sinAsinFilas;
        int duplicadasFilas = resultado.duplicadosFilas;
        int exportado = resultado.procesados;

        StringBuilder lines = new StringBuilder();
        lines.append("RESUMEN DE REPORTE\n\n");
        lines.append(String.format("%-28s %8d%n", "Total filas en reporte", total));
        lines.append(String.format("%-28s %8d%n", "(-) Cancelados (filas)", cancelados));
        lines.append(String.format("%-28s %8d%n", "(-) Sin ASIN (filas)", sinAsin));
        lines.append(String.format("%-28s %8d%n", "(-) Filas duplicadas", duplicadasFilas));
        lines.append(String.format("%-28s %8s%n", "----------------------------", ""));
        lines.append(String.format("%-28s %8d%n", "(=) Total exportado", exportado));
        lines.append("\n");
        lines.append(String.format("%-28s %8d%n", "ASIN duplicados (únicos)", resultado.duplicados));
        lines.append(String.format("%-28s %8d%n", "Match con BD (ASIN)", resultado.matchBd));
        if (resultado.rechazadosTotal > 0) {
            lines.append(String.format("%-28s %8d%n", "CSV no procesados", resultado.rechazadosTotal));
        }
        lines.append("\n");
        lines.append("Nota: duplicados se cuentan por ASIN único; se conserva 1 (IVA=SI).");
        lines.append("\nPara exportar no procesados usa el botón correspondiente.");

        String html = "<html><pre>" + lines.toString() + "</pre></html>";
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
            if (name.endsWith(".csv")) {
                txtBase.setText(file.getAbsolutePath());
                assigned = true;
            } else if (name.endsWith(".txt")) {
                txtReporte.setText(file.getAbsolutePath());
                assigned = true;
            }
        }
        if (!assigned) {
            showError("Solo se aceptan archivos .csv o .txt.");
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
        panelTop.add(panelFields, BorderLayout.NORTH);

        JLabel lblBase = new JLabel();
        lblBase.setText("Base IVA (.csv)");
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
        btnPreview.setText("Previsualizar");
        panelButtons.add(btnPreview);

        btnExport = new JButton();
        btnExport.setText("Exportar");
        panelButtons.add(btnExport);

        btnClear = new JButton();
        btnClear.setText("Limpiar");
        panelButtons.add(btnClear);

        btnExportRechazados = new JButton();
        btnExportRechazados.setText("Exportar no procesados");
        panelButtons.add(btnExportRechazados);

        scrollPane = new JScrollPane();
        rootPanel.add(scrollPane, BorderLayout.CENTER);

        tablePreview = new JTable();
        scrollPane.setViewportView(tablePreview);

        panelStatus = new JPanel();
        panelStatus.setLayout(new BorderLayout());
        panelStatus.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        rootPanel.add(panelStatus, BorderLayout.SOUTH);

        lblStatus = new JLabel();
        lblStatus.setText("Listo. Arrastra archivos .csv y .txt o usa Buscar.");
        panelStatus.add(lblStatus, BorderLayout.CENTER);

    }
}
