package interfaz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Pattern;

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
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import control.MotorIvaRunner;

public class Principal {

	private JFrame frame;
	private JTextField txtBase;
	private JTextField txtReporte;
	private JTable tablePreview;
	private JLabel lblStatus;
	private JButton btnPreview;
	private JButton btnExport;
	private JButton btnClear;

	private File tempOutput;
	private File tempResumen;
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
					window.frame.setVisible(true);
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
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.setTitle("Iva Asins");
		frame.setBounds(100, 100, 1100, 720);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(new BorderLayout(10, 10));
		frame.setLocationRelativeTo(null);

		Color appBg = new Color(245, 246, 248);
		Color cardBg = new Color(255, 255, 255);
		frame.getContentPane().setBackground(appBg);

		JMenuBar menuBar = new JMenuBar();
		frame.setJMenuBar(menuBar);

		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);

		JMenuItem mntmManual = new JMenuItem("Manual");
		mnFile.add(mntmManual);

		JMenuItem mntmSalir = new JMenuItem("Salir");
		mntmSalir.addActionListener(e -> System.exit(0));
		mnFile.add(mntmSalir);

		JMenuItem mntmTitle = new JMenuItem("Iva Asins");
		menuBar.add(mntmTitle);

		JPanel panelTop = new JPanel();
		panelTop.setBorder(new EmptyBorder(12, 12, 12, 12));
		panelTop.setBackground(cardBg);
		panelTop.setLayout(new GridBagLayout());

		JLabel lblBase = new JLabel("Base IVA (.csv)");
		GridBagConstraints gbcLblBase = new GridBagConstraints();
		gbcLblBase.insets = new Insets(5, 5, 5, 5);
		gbcLblBase.fill = GridBagConstraints.HORIZONTAL;
		gbcLblBase.gridx = 0;
		gbcLblBase.gridy = 0;
		gbcLblBase.weightx = 0;
		panelTop.add(lblBase, gbcLblBase);

		txtBase = new JTextField();
		txtBase.setBackground(Color.WHITE);
		GridBagConstraints gbcTxtBase = new GridBagConstraints();
		gbcTxtBase.insets = new Insets(5, 5, 5, 5);
		gbcTxtBase.fill = GridBagConstraints.HORIZONTAL;
		gbcTxtBase.gridx = 1;
		gbcTxtBase.gridy = 0;
		gbcTxtBase.weightx = 1.0;
		panelTop.add(txtBase, gbcTxtBase);

		JButton btnBuscarBase = new JButton("Buscar");
		GridBagConstraints gbcBtnBuscarBase = new GridBagConstraints();
		gbcBtnBuscarBase.insets = new Insets(5, 5, 5, 5);
		gbcBtnBuscarBase.fill = GridBagConstraints.HORIZONTAL;
		gbcBtnBuscarBase.gridx = 2;
		gbcBtnBuscarBase.gridy = 0;
		gbcBtnBuscarBase.weightx = 0;
		panelTop.add(btnBuscarBase, gbcBtnBuscarBase);

		JLabel lblReporte = new JLabel("Reporte Amazon (.txt)");
		GridBagConstraints gbcLblReporte = new GridBagConstraints();
		gbcLblReporte.insets = new Insets(5, 5, 5, 5);
		gbcLblReporte.fill = GridBagConstraints.HORIZONTAL;
		gbcLblReporte.gridx = 0;
		gbcLblReporte.gridy = 1;
		gbcLblReporte.weightx = 0;
		panelTop.add(lblReporte, gbcLblReporte);

		txtReporte = new JTextField();
		txtReporte.setBackground(Color.WHITE);
		GridBagConstraints gbcTxtReporte = new GridBagConstraints();
		gbcTxtReporte.insets = new Insets(5, 5, 5, 5);
		gbcTxtReporte.fill = GridBagConstraints.HORIZONTAL;
		gbcTxtReporte.gridx = 1;
		gbcTxtReporte.gridy = 1;
		gbcTxtReporte.weightx = 1.0;
		panelTop.add(txtReporte, gbcTxtReporte);

		JButton btnBuscarReporte = new JButton("Buscar");
		GridBagConstraints gbcBtnBuscarReporte = new GridBagConstraints();
		gbcBtnBuscarReporte.insets = new Insets(5, 5, 5, 5);
		gbcBtnBuscarReporte.fill = GridBagConstraints.HORIZONTAL;
		gbcBtnBuscarReporte.gridx = 2;
		gbcBtnBuscarReporte.gridy = 1;
		gbcBtnBuscarReporte.weightx = 0;
		panelTop.add(btnBuscarReporte, gbcBtnBuscarReporte);

		JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		panelButtons.setOpaque(false);
		btnPreview = new JButton("Previsualizar");
		btnExport = new JButton("Exportar");
		btnClear = new JButton("Limpiar");
		panelButtons.add(btnPreview);
		panelButtons.add(btnExport);
		panelButtons.add(btnClear);

		GridBagConstraints gbcPanelButtons = new GridBagConstraints();
		gbcPanelButtons.insets = new Insets(5, 5, 5, 5);
		gbcPanelButtons.fill = GridBagConstraints.HORIZONTAL;
		gbcPanelButtons.gridx = 0;
		gbcPanelButtons.gridy = 2;
		gbcPanelButtons.gridwidth = 3;
		gbcPanelButtons.weightx = 1.0;
		panelTop.add(panelButtons, gbcPanelButtons);

		frame.getContentPane().add(panelTop, BorderLayout.NORTH);

		tablePreview = new JTable();
		tablePreview.setRowHeight(22);
		tablePreview.setFillsViewportHeight(true);
		tablePreview.setGridColor(new Color(225, 228, 232));
		JScrollPane scrollPane = new JScrollPane(tablePreview);
		scrollPane.getViewport().setBackground(Color.WHITE);
		frame.getContentPane().add(scrollPane, BorderLayout.CENTER);

		JPanel panelStatus = new JPanel(new BorderLayout());
		panelStatus.setBorder(new EmptyBorder(5, 10, 10, 10));
		panelStatus.setBackground(appBg);
		lblStatus = new JLabel("Listo. Arrastra archivos .csv y .txt o usa Buscar.");
		panelStatus.add(lblStatus, BorderLayout.CENTER);
		frame.getContentPane().add(panelStatus, BorderLayout.SOUTH);

		btnBuscarBase.addActionListener(e -> onSelectBase());
		btnBuscarReporte.addActionListener(e -> onSelectReporte());
		btnPreview.addActionListener(e -> onPreview());
		btnExport.addActionListener(e -> onExport());
		btnClear.addActionListener(e -> onClear());

		setupDragAndDrop(frame.getRootPane());
		setupDragAndDrop(txtBase);
		setupDragAndDrop(txtReporte);
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
			int option = JOptionPane.showConfirmDialog(frame,
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
		lastBase = null;
		lastReporte = null;
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
				return runner.ejecutar(base, reporte, tempOutput, tempResumen);
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
					}
					if (exportDestino != null) {
						if (exportTempTo(exportDestino)) {
							lblStatus.setText("Archivo exportado: " + exportDestino.getName());
						} else {
							return;
						}
					}
					if (resultado.duplicados > 0) {
						showDuplicatesPopup(resultado);
					}
					if (!showPreview && exportDestino == null) {
						lblStatus.setText("Listo. Filas generadas: " + resultado.procesados);
					}
				} catch (Exception ex) {
					showError(ex.getMessage());
					lblStatus.setText("Error en el proceso.");
				}
			}
		};
		worker.execute();
	}

	private boolean exportTempTo(File destino) {
		try {
			Files.copy(tempOutput.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
			JOptionPane.showMessageDialog(frame, "Archivo exportado correctamente.");
			return true;
		} catch (IOException ex) {
			showError("No se pudo exportar el archivo: " + ex.getMessage());
			return false;
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
		FileDialog dialog = new FileDialog(frame, title, save ? FileDialog.SAVE : FileDialog.LOAD);
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
	}

	private void showError(String message) {
		JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
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
		JOptionPane.showMessageDialog(frame, msg.toString(), "Duplicados", JOptionPane.WARNING_MESSAGE);
	}

	private void setupDragAndDrop(JComponent component) {
		component.setTransferHandler(new TransferHandler() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean canImport(TransferSupport support) {
				return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
						&& (support.getDropAction() & DnDConstants.ACTION_COPY) != 0;
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
					boolean assigned = false;
					for (File file : files) {
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
				} catch (Exception ex) {
					showError("No se pudo importar el archivo: " + ex.getMessage());
					return false;
				}
			}
		});
	}
}
