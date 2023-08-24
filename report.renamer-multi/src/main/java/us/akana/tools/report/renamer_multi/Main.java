package us.akana.tools.report.renamer_multi;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;

public class Main {

	static JFrame options;
	static JLabel info = new JLabel();
	static final Pattern IE_REGEX = Pattern.compile("IE\\d{3}|IA\\d{3}|JS\\d{3}");
	//static final Pattern YEAR_REGEX = Pattern.compile("(\\d{2},\\s*)(20\\d{2})"); // ##, 20##
	static final Pattern YEAR_REGEX = Pattern.compile("(\\d{2},\\s*|\\w+\\s*)(20\\d{2})"); // above or asd 20##
	static final Pattern SUB_SITE_REGEX = Pattern.compile("\\D*(\\d+)\\D*(?!.*\\d)");

	static File[] selectedFiles = new File[3]; // 0 = input, 1 = output, 2 = location hierarchy spreadsheet

	static XSSFWorkbook workbook;
	static XSSFSheet mainSheet;

	static Tesseract tesseract;

	static FileWriter writeToInfo;

	static final String[] REPORT_TYPES = { "Space Utilization Reports", "EISA Reports", "AHERA Reports",
			"FCA App B Reports" };
	static final String[] REPORT_STRINGS = { "Space Utilization Report", "EISA Building Report", "AHERA Report",
			"FCA Sub-Site Report App B" };

	static int selectedType = 0;

	/**
	 * Entry class
	 * 
	 * @param args Unused
	 */
	public static void main(String[] args) {
		openWindow();
	}

	/**
	 * Constructs the GUI
	 */
	static void openWindow() {
		options = new JFrame("Rename reports tool");
		options.setSize(800, 700);
		options.setLayout(new GridBagLayout());
		options.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		options.add(new JLabel("Select an input directory:"),
				new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		JButton selectInput = new SelectButton(0, true);
		options.add(selectInput,
				new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		options.add(new JLabel("Select an output directory:"),
				new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		JButton selectOutput = new SelectButton(1, true);
		options.add(selectOutput,
				new GridBagConstraints(1, 1, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		options.add(new JLabel("Select location hierarchy spreadsheet (*.xlsx):"),
				new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		JButton selectSheet = new SelectButton(2, false);
		options.add(selectSheet,
				new GridBagConstraints(1, 2, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		options.add(new JLabel("Select what type of reports we're renaming:"),
				new GridBagConstraints(0, 3, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		final JComboBox<String> reportTypeSelect = new JComboBox<>(REPORT_TYPES);
		options.add(reportTypeSelect,
				new GridBagConstraints(1, 3, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		JButton cancel = new JButton("Close");
		options.add(cancel,
				new GridBagConstraints(0, 4, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		final JButton run = new JButton("Run");
		options.add(run,
				new GridBagConstraints(1, 4, 1, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		options.add(info,
				new GridBagConstraints(0, 5, 2, 1, 0, 0, GridBagConstraints.CENTER, 0, new Insets(0, 0, 0, 0), 0, 0));

		reportTypeSelect.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectedType = reportTypeSelect.getSelectedIndex();
			}
		});

		cancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});

		run.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (selectedFiles[0] != null && selectedFiles[1] != null && selectedFiles[2] != null
						&& selectedFiles[2].getName().toLowerCase().endsWith(".xlsx")) {
					final File input = selectedFiles[0];
					final File output = selectedFiles[1];
					selectedType = reportTypeSelect.getSelectedIndex();
					options.pack();

					SwingWorker<Boolean, Void> sw = new SwingWorker<Boolean, Void>() {
						@Override
						protected Boolean doInBackground() throws Exception {
							info.setText(
									"<html><div style='text-align: center;'>Initializing rename-info.txt...</html>");
							options.pack();
							initInfo(output);
							info.setText("<html><div style='text-align: center;'>Loading Tesseract OCR...</html>");
							options.pack();
							loadTess();
							info.setText("<html><div style='text-align: center;'>Opening Location Sheet...</html>");
							options.pack();
							openSheet(selectedFiles[2]);
							info.setText("<html><div style='text-align: center;'>Copying reports...</html>");
							options.pack();
							if (selectedType != 1)
								renameReports(input, output);
							else
								renameEISA(input, output);
							info.setText(
									"<html><div style='text-align: center;'>All done!<br>See rename-info.txt in the output directory for more information.</html>");
							options.pack();
							writeToInfo.close();
							run.setEnabled(true);
							return true;
						}

						protected void done() {
							try {
								get();
							} catch (InterruptedException e) {
								e.printStackTrace();
							} catch (ExecutionException e) {
								e.getCause().printStackTrace();
								JOptionPane.showMessageDialog(options,
										String.format("Unexpected Problem:\n%s", e.getCause().toString()), "Error",
										JOptionPane.ERROR_MESSAGE);
							}
						}
					};
					run.setEnabled(false);
					sw.execute();
				} else {
					info.setText(
							"<html><div style='text-align: center;'>Please select directories for input and output<br>and a file of type *.xlsx for the location<br>hierarchy spreadsheet.</html>");
					options.pack();
				}
			}
		});

		options.pack();
		options.setVisible(true);
	}

	/**
	 * Initializes info document to log to
	 * 
	 * @param output Directory to create
	 */
	static void initInfo(File output) {
		try {
			File infoFile = new File(String.format("%s\\rename-info.txt", output.getAbsolutePath()));
			if (infoFile.exists())
				infoFile.delete();
			infoFile.createNewFile();
			writeToInfo = new FileWriter(String.format("%s\\rename-info.txt", output.getAbsolutePath()));
			writeToInfo.write(String.format(
					"Info log for report renaming tool run %s\nBelow will be any potential errors encountered when running. If it's empty, everything went well.\n\n",
					LocalDateTime.now().toString()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Initializes Tesseract, the OCR library used to get the year from the first
	 * page of reports
	 */
	static void loadTess() {
		File tmpFolder = LoadLibs.extractTessResources("win32-x86-64");
		System.setProperty("java.library.path", tmpFolder.getPath());
		tesseract = new Tesseract();
		tesseract.setLanguage("eng");
		try {
			// Can't directly access local resources when packaged into JAR, have to make
			// temp file and read from there
			// Side note - this caused like 4 hours of headache
			Path dataDirectory = Files.createTempDirectory("data");
			InputStream stream = Thread.currentThread().getContextClassLoader()
					.getResourceAsStream("data/eng.traineddata");
			Files.copy(stream, new File(dataDirectory.toString() + "/eng.traineddata").toPath());
			tesseract.setDatapath(dataDirectory.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Finds all correctly placed pdfs within the input directory and copies them,
	 * renamed, to the output directory
	 * 
	 * @param input  Directory containing all the reports
	 * @param output Directory to copy renamed reports to
	 * @throws IOException if error writing to info file
	 */
	static void renameReports(File input, File output) throws IOException {
		File[] inputContents = input.listFiles();
		for (File file : inputContents) {
			if (file.isDirectory()) {
				Matcher match = IE_REGEX.matcher(file.getName());
				if (match.find()) {
					final String IE_NUM = file.getName().substring(match.start(), match.end());
					File[] locContents = file.listFiles(new ReportFileFilter());
					if (locContents.length == 1) {
						File[] reportContent = locContents[0].listFiles();
						if (reportContent.length == 2) {
							boolean checkFirst = reportContent[0].getName().equals("Final");
							File[] firstFolderList = reportContent[(checkFirst) ? 0 : 1].listFiles(new PDFFileFilter());
							if (firstFolderList.length > 0) {
								if (firstFolderList.length > 1)
									writeToInfo.write(String.format(
											"- Multiple PDFs found. Using the first, confirm this is correct. Site: %s.\n",
											IE_NUM));
								copyFile(firstFolderList[0], IE_NUM, output);
							} else {
								File[] secondFolderList = reportContent[(checkFirst) ? 1 : 0]
										.listFiles(new PDFFileFilter());
								if (secondFolderList.length > 0) {
									if (secondFolderList.length > 1)
										writeToInfo.write(String.format(
												"- Multiple PDFs found. Using the first, confirm this is correct. Site: %s.\n",
												IE_NUM));
									copyFile(secondFolderList[0], IE_NUM, output);
								} else
									writeToInfo.write(String.format(
											"- Final and draft folder both missing pdf: %s. Skipping...\n",
											locContents[0]));
							}
						} else
							writeToInfo.write(String.format(
									"- Reports doesn't contain Draft and Final directories: %s. Skipping...\n",
									locContents[0]));
					} else
						writeToInfo
								.write(String.format("- Directory missing reports directory: %s. Skipping...\n", file));
				} else
					writeToInfo
							.write(String.format("- Found foreign directory in input folder: %s. Skipping...\n", file));
			} else
				writeToInfo.write(String.format("- Found non-directory item in input folder: %s. Skipping...\n", file));
		}
	}

	static void renameEISA(File input, File output) throws IOException {
		File[] inputContents = input.listFiles();
		for (File file : inputContents) {
			if (file.isDirectory()) {
				Matcher match = IE_REGEX.matcher(file.getName());
				if (match.find()) {
					final String IE_NUM = file.getName().substring(match.start(), match.end());
					File[] locContents = file.listFiles(new CleanFileFilter());
					if (locContents.length < 1) {
						for (File locDir : file.listFiles()) {
							if (locDir.isDirectory()) {
								for (File finalDir : locDir.listFiles(new CleanFileFilter())) {
									for (File report : finalDir.listFiles(new PDFFileFilter())) {
										copyEISAFile(report, IE_NUM, output);
									}
								}
							}
						}
					} else if (locContents.length == 1) {
						for (File finalDir : locContents[0].listFiles()) {
							if (finalDir.isDirectory()) {
								for (File report : finalDir.listFiles(new PDFFileFilter())) {
									copyEISAFile(report, IE_NUM, output);
								}
							} else if (finalDir.getName().toLowerCase().endsWith(".pdf")) {
								copyEISAFile(finalDir, IE_NUM, output);
							}
						}
					} else
						writeToInfo.write(
								String.format("- Multiple 'Clean and send to client folders: %s. Skipping\n", file));
				} else
					writeToInfo
							.write(String.format("- Found foreign directory in input folder: %s. Skipping...\n", file));
			} else
				writeToInfo.write(String.format("- Found non-directory item in input folder: %s. Skipping\n", file));
		}
	}

	static void copyEISAFile(File file, String site, File output) throws IOException {
		// DATA TO GET:
		String year, siteId = site, locId, maximoId, siteName, subSite;

		Matcher match = SUB_SITE_REGEX.matcher(file.getName());
		match.find();
		try {
			subSite = match.group(1);
		} catch (IllegalStateException e) {
			writeToInfo
					.write(String.format("- SubSite Number not found in filename: %s for site %s, skipping...\n", file.getName(), site));
			return;
		}

		int num = mainSheet.getPhysicalNumberOfRows();
		int foundRow;
		for (foundRow = 6; foundRow < num; foundRow++)
			if (mainSheet.getRow(foundRow).getCell(3).getRichStringCellValue().getString().equals(site))
				break;

		if (foundRow != num) {
			while (mainSheet.getRow(foundRow).getCell(3).getRichStringCellValue().getString().equals(site)
					&& !mainSheet.getRow(foundRow).getCell(19).getRichStringCellValue().getString().equals(subSite))
				foundRow++;

			if (!mainSheet.getRow(foundRow).getCell(19).getRichStringCellValue().getString().equals(subSite)) {
				writeToInfo.write(String.format("- Sub-Site not found in sheet: %s -> %s, for file %s. Skipping...\n", site, subSite, file.getName()));
				return;
			}

			locId = mainSheet.getRow(foundRow).getCell(5).getRichStringCellValue().getString();
			maximoId = mainSheet.getRow(foundRow).getCell(8).getRichStringCellValue().getString();
			siteName = mainSheet.getRow(foundRow).getCell(4).getRichStringCellValue().getString();

			try {
				PDDocument activePDF = PDDocument.load(file);
				PDFRenderer renderedDoc = new PDFRenderer(activePDF);
				String pg1Text = getText(renderedDoc.renderImage(0));
				activePDF.close();
				Matcher yearMatch = YEAR_REGEX.matcher(pg1Text);
				yearMatch.find();
				try {
					year = yearMatch.group(2);

					FileUtils.copyFile(file,
							new File(String.format("%s\\%s_%s_%s_%s_%s_%s_%s.pdf", output.getAbsolutePath(), year,
									siteId, locId, maximoId, REPORT_STRINGS[selectedType],
									siteName.replace("/", "-").replace("\\", "-"), subSite)));
				} catch (IllegalStateException e) {
					year = "YYYY";

					FileUtils.copyFile(file, new File(String.format("%s\\MISSING YEARS\\%s_%s_%s_%s_%s_%s_%s.pdf",
							output.getAbsolutePath(), year, siteId, locId, maximoId, REPORT_STRINGS[selectedType],
							siteName.replace("/", "-").replace("\\", "-"), subSite)));

					writeToInfo.write(String.format(
							"- Tesseract couldn't find a year on page one of %s for site %s. It's been copied to MISSING YEARS with the remainder of the data, input the year manually.\n",
							file.getName(), site));
				}
			} catch (IOException e) {
				writeToInfo.write(String.format("- Error loading PDF for site %s, skipping...", site));
				e.printStackTrace();
			}

		} else
			writeToInfo.write(String.format("- %s not found in sheet, skipping report...\n", site));

	}

	/**
	 * Performs the copying of the given file to the output directory, getting all
	 * relevant information from other sources
	 * 
	 * @param file   The PDF file to copy
	 * @param site   The site ID relevant to the given pdf
	 * @param output The directory to copy to
	 */
	static void copyFile(File file, String site, File output) throws IOException {
		// DATA TO GET:
		String year, siteId = site, locId, maximoId, siteName;

		int num = mainSheet.getPhysicalNumberOfRows();
		int foundRow;
		for (foundRow = 6; foundRow < num; foundRow++)
			if (mainSheet.getRow(foundRow).getCell(3).getRichStringCellValue().getString().equals(site))
				break;
		if (foundRow != num) {
			locId = mainSheet.getRow(foundRow).getCell(5).getRichStringCellValue().getString();
			maximoId = mainSheet.getRow(foundRow).getCell(8).getRichStringCellValue().getString();
			siteName = mainSheet.getRow(foundRow).getCell(4).getRichStringCellValue().getString();

			try {
				PDDocument activePDF = PDDocument.load(file);
				PDFRenderer renderedDoc = new PDFRenderer(activePDF);
				String pg1Text = getText(renderedDoc.renderImage(0));
				activePDF.close();
				Matcher match = YEAR_REGEX.matcher(pg1Text);
				match.find();
				try {
					year = match.group(2);

					FileUtils.copyFile(file,
							new File(String.format("%s\\%s_%s_%s_%s_%s_%s.pdf", output.getAbsolutePath(), year, siteId,
									locId, maximoId, REPORT_STRINGS[selectedType],
									siteName.replace("/", "-").replace("\\", "-"))));
				} catch (IllegalStateException e) {
					year = "YYYY";

					FileUtils.copyFile(file,
							new File(String.format("%s\\MISSING YEARS\\%s_%s_%s_%s_%s_%s.pdf", output.getAbsolutePath(),
									year, siteId, locId, maximoId, REPORT_STRINGS[selectedType],
									siteName.replace("/", "-").replace("\\", "-"))));

					writeToInfo.write(String.format(
							"- Tesseract couldn't find a year on page one of %s for site %s. It's been copied to MISSING YEARS with the remainder of the data, input the year manually.\n",
							file.getName(), site));
				}
			} catch (IOException e) {
				writeToInfo.write(String.format("- Error loading PDF for site %s, skipping...", site));
				e.printStackTrace();
			}
		} else
			writeToInfo.write(String.format("- %s not found in sheet, skipping report...\n", site));
	}

	/**
	 * Extracts text from the given image using Tesseract OCR
	 * 
	 * @param img the image to find text in
	 * @return the extracted text
	 */
	static String getText(BufferedImage img) {
		try {
			String ret = tesseract.doOCR(img);
			return ret;
		} catch (TesseractException e) {
			e.printStackTrace();
			return "ERROR";
		}
	}

	/**
	 * Initializes the XSSF Workbook containing all the location hierarchy details
	 * 
	 * @param sheetFile the input .xlsx location hierarchy file
	 */
	static void openSheet(File sheetFile) {
		try {
			workbook = new XSSFWorkbook(sheetFile);
			mainSheet = workbook.getSheetAt(0);
		} catch (InvalidFormatException | IOException e) {
			e.printStackTrace();
		}
	}
}

/**
 * A file select button using swing - opens a file selection when clicked,
 * changes its text to reflect the seleted file
 * 
 * @author Jaden Unruh
 */
@SuppressWarnings("serial")
class SelectButton extends JButton {
	SelectButton(final int whichSelect, final boolean isDir) {
		super("Select...");
		this.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				SwingWorker<Boolean, Void> sw = new SwingWorker<Boolean, Void>() {
					@Override
					protected Boolean doInBackground() throws Exception {
						JFileChooser fc = new JFileChooser();
						if (isDir)
							fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
						int returnVal = fc.showOpenDialog(Main.options);
						if (returnVal == JFileChooser.APPROVE_OPTION) {
							Main.selectedFiles[whichSelect] = fc.getSelectedFile();
							rename(fc.getSelectedFile().getName());
						}
						deSelected();
						return null;
					}
				};
				selected();
				sw.execute();
			}
		});
	}

	/**
	 * Disables the button when selected
	 */
	void selected() {
		this.setEnabled(false);
	}

	/**
	 * Re-enables the button when deselected, and repacks Main.options to reflect
	 * the button's new width
	 */
	void deSelected() {
		this.setEnabled(true);
		Main.options.pack();
	}

	/**
	 * Renames the button to the given text
	 * 
	 * @param text String to rename to
	 */
	void rename(String text) {
		this.setText(text);
	}
}

/**
 * A file filter that only accepts files named 0n Report or 0n Reports, where n
 * is any digit
 * 
 * @author Jaden Unruh
 */
class ReportFileFilter implements FilenameFilter {
	@Override
	public boolean accept(File arg0, String arg1) {
		return Pattern.matches("0\\d{1} Reports?", arg1);
		// return arg1.equals("04 Report");
	}
}

/**
 * A file filter that only accepts "*.pdf" files
 * 
 * @author Jaden Unruh
 */
class PDFFileFilter implements FilenameFilter {
	@Override
	public boolean accept(File arg0, String arg1) {
		return arg1.toLowerCase().endsWith(".pdf");
	}
}

/**
 * A file filter that only accepts files with exactly "Clean and send to client"
 * in their names
 * 
 * @author Jaden
 *
 */
class CleanFileFilter implements FilenameFilter {
	@Override
	public boolean accept(File arg0, String arg1) {
		return arg1.contains("Clean and send to client") && arg0.isDirectory();
	}
}