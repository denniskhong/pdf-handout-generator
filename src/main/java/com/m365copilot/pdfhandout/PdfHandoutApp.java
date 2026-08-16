/*
 * PDF Handout Generator
 *
 * A cross-platform Swing desktop application that opens a PDF, previews
 * multiple source pages on each output sheet, and generates a new handout PDF
 * directly with Apache PDFBox.
 *
 * This Swing edition deliberately avoids JavaFX. Swing and AWT are provided by
 * the standard Java desktop module, so Maven can create one executable fat JAR
 * that runs on Linux, Windows, and macOS with Java 21 or newer.
 *
 * Layout convention:
 *   Columns x Rows. For example, 2 x 3 means two columns and three rows.
 *
 * Features:
 *   - A3, A4, A5, Letter, and Legal output paper.
 *   - Portrait or landscape output orientation.
 *   - Custom Columns x Rows layout.
 *   - Vector-preserving PDF output via imported PDF Form XObjects.
 *   - Optional frames around slides.
 *   - Optional left alignment inside each grid cell.
 *   - Optional source-page number below each slide.
 *   - Optional Ink saver mode that removes detected backgrounds and renders
 *     all identifiable PDF text in black.
 *     changes dark text-like content to black.
 *   - Conventional File and Help menus.
 *   - Vertical output-sheet scrollbar.
 *   - Source and generated output-sheet counts.
 *
 * Creator: M365 Copilot (GPT-5 reasoning model)
 * Vibe-coder: Dennis Khong
 *
 * Version: 1.0.0
 * Date: 2026-08-16
 * License: MIT
 */
package com.m365copilot.pdfhandout;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

/** Main Swing window, preview controller, and PDF handout generator. */
public final class PdfHandoutApp extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final String APP_NAME = "PDF Handout Generator";
    private static final String APP_VERSION = "1.0.0";
    private static final String PROJECT_URL =
            "https://github.com/denniskhong/pdf-handout-generator";
    private static final float PREVIEW_DPI = 96.0f;
    private static final float OUTPUT_MARGIN = 18.0f;
    private static final float NUMBER_GAP = 3.0f;
    private static final float NUMBER_FONT_SIZE = 9.0f;
    private static final float NUMBER_AREA = NUMBER_GAP + NUMBER_FONT_SIZE + 3.0f;
    private static final double CELL_GAP_FRACTION = 0.035;

    /*
     * Ink-saver output is rasterised intentionally because a PDF does not
     * identify semantic objects such as "background picture" or "text" in a
     * uniform way. 300 DPI remains sharp for typical lecture handouts while
     * making reliable pixel-level background removal possible.
     */
    private static final float INK_SAVER_DPI = 300.0f;
    private static final int WHITE_THRESHOLD = 242;
    private static final int DARK_TEXT_THRESHOLD = 135;
    private static final int BACKGROUND_DISTANCE = 42;

    private PDDocument document;
    private PDFRenderer renderer;
    private Path inputPath;
    private boolean changingScrollBar;

    /** Access-ordered cache of preview images; bounded to limit memory usage. */
    private final Map<Integer, BufferedImage> pageImageCache =
            new LinkedHashMap<>(32, 0.75f, true) {
                private static final long serialVersionUID = 1L;
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest) {
                    return size() > 24;
                }
            };

    private final JComboBox<PaperSize> paperBox = new JComboBox<>(PaperSize.values());
    private final JComboBox<PageOrientation> orientationBox =
            new JComboBox<>(PageOrientation.values());
    private final JSpinner columnsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 8, 1));
    private final JSpinner rowsSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 8, 1));
    private final JCheckBox frameCheck = new JCheckBox("Frame each slide", true);
    private final JCheckBox leftAlignCheck =
            new JCheckBox("Left-align slides in each cell", false);
    private final JCheckBox slideNumbersCheck = new JCheckBox("Add slide numbers", false);
    private final JCheckBox inkSaverCheck = new JCheckBox("Ink saver", false);
    private final JTextField inputField = new JTextField();
    private final JTextField outputField = new JTextField();
    private final JLabel pageInfoLabel = new JLabel("No PDF loaded");
    private final JLabel sheetPositionLabel = new JLabel("Sheet 1 of 1", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("Ready");
    private final JTextArea logArea = new JTextArea(5, 20);
    private final JButton generateButton = new JButton("Generate PDF");
    private final JScrollBar sheetScrollBar = new JScrollBar(JScrollBar.VERTICAL, 1, 1, 1, 2);
    private final PreviewPanel previewPanel = new PreviewPanel();
    private JMenuItem saveMenuItem;

    /** Constructs the complete Swing interface. */
    public PdfHandoutApp() {
        super(APP_NAME);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(920, 700));
        setSize(1120, 860);
        setLocationByPlatform(true);

        configureControls();
        setJMenuBar(buildMenuBar());
        setContentPane(buildContentPane());
    }

    /** Configures control state and event handling. */
    private void configureControls() {
        inputField.setEditable(false);
        outputField.setToolTipText("Destination PDF file");
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        generateButton.setEnabled(false);
        sheetScrollBar.setEnabled(false);
        sheetScrollBar.setUnitIncrement(1);
        sheetScrollBar.setBlockIncrement(1);

        paperBox.setSelectedItem(PaperSize.A4);
        orientationBox.setSelectedItem(PageOrientation.PORTRAIT);

        paperBox.addActionListener(event -> layoutChanged());
        orientationBox.addActionListener(event -> layoutChanged());
        columnsSpinner.addChangeListener(event -> layoutChanged());
        rowsSpinner.addChangeListener(event -> layoutChanged());
        frameCheck.addActionListener(event -> previewPanel.repaint());
        leftAlignCheck.addActionListener(event -> previewPanel.repaint());
        slideNumbersCheck.addActionListener(event -> previewPanel.repaint());
        inkSaverCheck.setToolTipText(
                "Removes detected backgrounds and renders identifiable PDF text in black");
        inkSaverCheck.addActionListener(event -> previewPanel.repaint());

        sheetScrollBar.addAdjustmentListener(event -> {
            if (changingScrollBar) {
                return;
            }
            updateSheetPositionLabel();
            requestVisiblePreviewPages();
            previewPanel.repaint();
        });

        generateButton.addActionListener(event -> generateHandout());
    }

    /** Builds a conventional menu bar attached to the JFrame. */
    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem openItem = new JMenuItem("Open PDF...");
        openItem.setMnemonic(KeyEvent.VK_O);
        openItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_O, menuShortcutMask()));
        openItem.addActionListener(event -> chooseInputPdf());

        saveMenuItem = new JMenuItem("Save Handout As...");
        saveMenuItem.setMnemonic(KeyEvent.VK_S);
        saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_S, menuShortcutMask()));
        saveMenuItem.setEnabled(false);
        saveMenuItem.addActionListener(event -> {
            if (chooseOutputPdf()) {
                generateHandout();
            }
        });

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setMnemonic(KeyEvent.VK_X);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(
                KeyEvent.VK_Q, menuShortcutMask()));
        exitItem.addActionListener(event -> dispose());

        fileMenu.add(openItem);
        fileMenu.add(saveMenuItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        JMenuItem aboutItem = new JMenuItem("About PDF Handout Generator");
        aboutItem.addActionListener(event -> showAboutDialog());
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    /** Returns the OS-appropriate menu shortcut modifier (Ctrl or Command). */
    private static int menuShortcutMask() {
        try {
            return java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (java.awt.HeadlessException exception) {
            return InputEvent.CTRL_DOWN_MASK;
        }
    }

    /** Builds all content below the JFrame's native Swing menu bar. */
    private JPanel buildContentPane() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(buildFileBar(), BorderLayout.NORTH);

        // BorderLayout stretches WEST components vertically. Wrap the options
        // panel in another BorderLayout and place it at NORTH so the controls
        // remain anchored at the top of the available column.
        JPanel optionsColumn = new JPanel(new BorderLayout());
        optionsColumn.add(buildOptionsPanel(), BorderLayout.NORTH);
        root.add(optionsColumn, BorderLayout.WEST);

        root.add(buildPreviewArea(), BorderLayout.CENTER);
        root.add(buildBottomPanel(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildFileBar() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.add(new JLabel("Input:"), BorderLayout.WEST);
        panel.add(inputField, BorderLayout.CENTER);
        JButton openButton = new JButton("Open PDF...");
        openButton.addActionListener(event -> chooseInputPdf());
        panel.add(openButton, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildOptionsPanel() {
        /*
         * Fix only the preferred width, never the preferred height. The old
         * 290 x 400 preferred size clipped the lower labels when the current
         * font, look-and-feel, or display scaling needed more than 400 pixels.
         * Swing now calculates the full natural height from all child controls.
         */
        JPanel outer = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                Dimension naturalSize = super.getPreferredSize();
                return new Dimension(290, naturalSize.height);
            }
        };
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));

        JLabel heading = new JLabel("Layout");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18.0f));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        outer.add(heading);
        outer.add(Box.createVerticalStrut(12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints label = constraints(0, 0);
        GridBagConstraints field = constraints(1, 0);
        field.weightx = 1.0;
        field.fill = GridBagConstraints.HORIZONTAL;
        addFormRow(form, label, field, 0, "Paper size:", paperBox);
        addFormRow(form, label, field, 1, "Orientation:", orientationBox);
        addFormRow(form, label, field, 2, "Columns:", columnsSpinner);
        addFormRow(form, label, field, 3, "Rows:", rowsSpinner);
        outer.add(form);
        outer.add(Box.createVerticalStrut(12));

        for (JCheckBox box : List.of(frameCheck, leftAlignCheck, slideNumbersCheck, inkSaverCheck)) {
            box.setAlignmentX(LEFT_ALIGNMENT);
            outer.add(box);
            outer.add(Box.createVerticalStrut(6));
        }

        JLabel help = new JLabel("<html>Left alignment removes the extra horizontal "
                + "centering offset inside each grid cell.</html>");
        help.setForeground(Color.DARK_GRAY);
        help.setAlignmentX(LEFT_ALIGNMENT);
        outer.add(help);
        outer.add(Box.createVerticalStrut(12));
        outer.add(new JSeparator());
        outer.add(Box.createVerticalStrut(12));

        JLabel convention = new JLabel("<html>Convention: Columns x Rows<br>"
                + "Example: 2 x 3 = 6 slides per sheet</html>");
        convention.setForeground(Color.DARK_GRAY);
        convention.setAlignmentX(LEFT_ALIGNMENT);
        outer.add(convention);
        outer.add(Box.createVerticalStrut(12));
        outer.add(new JSeparator());
        outer.add(Box.createVerticalStrut(12));
        pageInfoLabel.setAlignmentX(LEFT_ALIGNMENT);
        outer.add(pageInfoLabel);
        return outer;
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 8);
        return c;
    }

    private static void addFormRow(JPanel panel, GridBagConstraints label,
                                   GridBagConstraints field, int row,
                                   String text, java.awt.Component component) {
        GridBagConstraints l = (GridBagConstraints) label.clone();
        GridBagConstraints f = (GridBagConstraints) field.clone();
        l.gridy = row;
        f.gridy = row;
        panel.add(new JLabel(text), l);
        panel.add(component, f);
    }

    private JPanel buildPreviewArea() {
        JPanel area = new JPanel(new BorderLayout(6, 6));
        previewPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        area.add(previewPanel, BorderLayout.CENTER);
        area.add(sheetScrollBar, BorderLayout.EAST);
        area.add(sheetPositionLabel, BorderLayout.SOUTH);
        return area;
    }

    private JPanel buildBottomPanel() {
        JPanel outer = new JPanel(new GridBagLayout());

        GridBagConstraints separatorConstraints = new GridBagConstraints();
        separatorConstraints.gridx = 0;
        separatorConstraints.gridy = 0;
        separatorConstraints.gridwidth = 3;
        separatorConstraints.weightx = 1.0;
        separatorConstraints.fill = GridBagConstraints.HORIZONTAL;
        separatorConstraints.insets = new Insets(0, 0, 8, 0);
        outer.add(new JSeparator(), separatorConstraints);

        // Use the same three-column structure as the input row: label, field,
        // button. The output field therefore begins at the same left position
        // as the input field instead of being inset by BoxLayout behaviour.
        GridBagConstraints outputLabel = constraints(0, 1);
        outputLabel.insets = new Insets(0, 0, 8, 8);
        outer.add(new JLabel("Output:"), outputLabel);

        GridBagConstraints outputFieldConstraints = constraints(1, 1);
        outputFieldConstraints.weightx = 1.0;
        outputFieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        outputFieldConstraints.insets = new Insets(0, 0, 8, 8);
        outer.add(outputField, outputFieldConstraints);

        JButton browseButton = new JButton("Save as...");
        browseButton.addActionListener(event -> chooseOutputPdf());
        GridBagConstraints browseConstraints = constraints(2, 1);
        browseConstraints.insets = new Insets(0, 0, 8, 0);
        outer.add(browseButton, browseConstraints);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actionRow.add(generateButton);
        actionRow.add(Box.createHorizontalStrut(12));
        actionRow.add(statusLabel);
        GridBagConstraints actionConstraints = new GridBagConstraints();
        actionConstraints.gridx = 0;
        actionConstraints.gridy = 2;
        actionConstraints.gridwidth = 3;
        actionConstraints.weightx = 1.0;
        actionConstraints.fill = GridBagConstraints.HORIZONTAL;
        actionConstraints.anchor = GridBagConstraints.WEST;
        actionConstraints.insets = new Insets(0, 0, 8, 0);
        outer.add(actionRow, actionConstraints);

        GridBagConstraints logConstraints = new GridBagConstraints();
        logConstraints.gridx = 0;
        logConstraints.gridy = 3;
        logConstraints.gridwidth = 3;
        logConstraints.weightx = 1.0;
        logConstraints.fill = GridBagConstraints.HORIZONTAL;
        outer.add(logArea, logConstraints);
        return outer;
    }

    /** Opens and loads a PDF on a background SwingWorker. */
    private void chooseInputPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF documents", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        closeDocument();
        inputPath = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        inputField.setText(inputPath.toString());
        outputField.setText(defaultOutputPath(inputPath).toString());
        synchronized (pageImageCache) {
            pageImageCache.clear();
        }
        setBusy(true, "Loading PDF...");

        new SwingWorker<PDDocument, Void>() {
            @Override
            protected PDDocument doInBackground() throws Exception {
                return Loader.loadPDF(inputPath.toFile());
            }

            @Override
            protected void done() {
                try {
                    document = get();
                    renderer = new PDFRenderer(document);
                    renderer.setSubsamplingAllowed(true);
                    setBusy(false, "Loaded");
                    generateButton.setEnabled(true);
                    saveMenuItem.setEnabled(true);
                    updateSheetRange();
                    requestVisiblePreviewPages();
                    previewPanel.repaint();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    handleLoadFailure(exception);
                } catch (ExecutionException exception) {
                    handleLoadFailure(exception.getCause());
                }
            }
        }.execute();
    }

    private void handleLoadFailure(Throwable exception) {
        inputPath = null;
        setBusy(false, "Load failed");
        showError("Could not open PDF", exception);
        previewPanel.repaint();
    }

    /** Selects a destination and returns false if the chooser is cancelled. */
    private boolean chooseOutputPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save generated handout");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF documents", "pdf"));
        if (!outputField.getText().isBlank()) {
            chooser.setSelectedFile(new File(outputField.getText()));
        }
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        String path = chooser.getSelectedFile().getAbsolutePath();
        outputField.setText(path.toLowerCase().endsWith(".pdf") ? path : path + ".pdf");
        return true;
    }

    private static Path defaultOutputPath(Path input) {
        String name = input.getFileName().toString();
        int dot = name.toLowerCase().lastIndexOf(".pdf");
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return input.resolveSibling(stem + "-handout.pdf");
    }

    private void layoutChanged() {
        updateSheetRange();
        requestVisiblePreviewPages();
        previewPanel.repaint();
    }

    private int columns() {
        return ((Number) columnsSpinner.getValue()).intValue();
    }

    private int rows() {
        return ((Number) rowsSpinner.getValue()).intValue();
    }

    private int calculateOutputSheetCount() {
        int sourcePages = document == null ? 0 : document.getNumberOfPages();
        int pagesPerSheet = columns() * rows();
        return Math.max(1, (sourcePages + pagesPerSheet - 1) / pagesPerSheet);
    }

    private int currentSheet() {
        return Math.max(1, sheetScrollBar.getValue());
    }

    /** JScrollBar maximum is exclusive because its visible amount is included. */
    private void updateSheetRange() {
        int sheets = calculateOutputSheetCount();
        int current = Math.min(currentSheet(), sheets);
        changingScrollBar = true;
        try {
            sheetScrollBar.setValues(current, 1, 1, sheets + 1);
            sheetScrollBar.setEnabled(sheets > 1);
        } finally {
            changingScrollBar = false;
        }
        updatePageCountLabel();
        updateSheetPositionLabel();
    }

    private void updatePageCountLabel() {
        if (document == null) {
            pageInfoLabel.setText("No PDF loaded");
        } else {
            pageInfoLabel.setText("<html>Source: " + document.getNumberOfPages()
                    + " pages<br>Output: " + calculateOutputSheetCount() + " sheets</html>");
        }
    }

    private void updateSheetPositionLabel() {
        sheetPositionLabel.setText("Sheet " + currentSheet()
                + " of " + calculateOutputSheetCount());
    }

    /** Renders uncached pages for the current output sheet in the background. */
    private void requestVisiblePreviewPages() {
        if (document == null || renderer == null) {
            return;
        }
        int firstPage = (currentSheet() - 1) * columns() * rows();
        int end = Math.min(document.getNumberOfPages(), firstPage + columns() * rows());
        List<Integer> missing = new ArrayList<>();
        synchronized (pageImageCache) {
            for (int page = firstPage; page < end; page++) {
                if (!pageImageCache.containsKey(page)) {
                    missing.add(page);
                }
            }
        }
        if (missing.isEmpty()) {
            return;
        }

        new SwingWorker<Map<Integer, BufferedImage>, Void>() {
            @Override
            protected Map<Integer, BufferedImage> doInBackground() throws Exception {
                Map<Integer, BufferedImage> rendered = new LinkedHashMap<>();
                for (int pageIndex : missing) {
                    BufferedImage image = renderer.renderImageWithDPI(
                            pageIndex, PREVIEW_DPI, ImageType.RGB);
                    rendered.put(pageIndex, image);
                }
                return rendered;
            }

            @Override
            protected void done() {
                try {
                    synchronized (pageImageCache) {
                        pageImageCache.putAll(get());
                    }
                    previewPanel.repaint();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    log("Preview error: " + exception.getCause());
                }
            }
        }.execute();
    }

    /** Validates options and generates the handout on a background worker. */
    private void generateHandout() {
        if (document == null || inputPath == null) {
            showError("No input PDF", new IllegalStateException("Open a PDF first."));
            return;
        }
        if (outputField.getText().isBlank()) {
            showError("No output PDF", new IllegalStateException("Choose an output path."));
            return;
        }

        final Path output;
        try {
            output = Path.of(outputField.getText()).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            showError("Invalid output path", exception);
            return;
        }
        if (output.equals(inputPath)) {
            showError("Unsafe output path",
                    new IllegalArgumentException("The output must differ from the input."));
            return;
        }
        if (output.getParent() != null && !Files.isDirectory(output.getParent())) {
            showError("Invalid output folder",
                    new IllegalArgumentException("The output folder does not exist."));
            return;
        }

        HandoutOptions options = currentOptions();
        logArea.setText("Generating directly with Apache PDFBox...\n");
        setBusy(true, "Generating...");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return createHandout(document, output, options);
            }

            @Override
            protected void done() {
                try {
                    int generatedSheets = get();
                    setBusy(false, "Created " + output.getFileName()
                            + " (" + generatedSheets + " sheets)");
                    pageInfoLabel.setText("<html>Source: " + document.getNumberOfPages()
                            + " pages<br>Generated: " + generatedSheets + " sheets</html>");
                    log("Created " + output);
                    log(generatedSheets + " output sheets written.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    handleGenerationFailure(exception);
                } catch (ExecutionException exception) {
                    handleGenerationFailure(exception.getCause());
                }
            }
        }.execute();
    }

    private void handleGenerationFailure(Throwable exception) {
        setBusy(false, "Generation failed");
        showError("Could not generate handout", exception);
    }

    private HandoutOptions currentOptions() {
        return new HandoutOptions(
                (PaperSize) paperBox.getSelectedItem(),
                (PageOrientation) orientationBox.getSelectedItem(),
                columns(), rows(), frameCheck.isSelected(),
                leftAlignCheck.isSelected(), slideNumbersCheck.isSelected(),
                inkSaverCheck.isSelected());
    }

    private void setBusy(boolean busy, String status) {
        generateButton.setEnabled(!busy && document != null);
        if (saveMenuItem != null) {
            saveMenuItem.setEnabled(!busy && document != null);
        }
        statusLabel.setText(status);
    }

    private void log(String message) {
        logArea.append(message + System.lineSeparator());
    }

    /** Shows application credits and a clickable project-repository link. */
    private void showAboutDialog() {
        String html = "<html><body style='font-family:sans-serif; width:360px'>"
                + "<h2>" + APP_NAME + " v" + APP_VERSION + "</h2>"
                + "<p>Create multiple-slides-per-page PDF handouts.</p>"
                + "<p><b>Creator:</b> M365 Copilot (GPT-5 reasoning model)<br>"
                + "<b>Vibe-coder:</b> Dennis Khong</p>"
                + "<p><b>License:</b> MIT</p>"
                + "<p><b>Project repository:</b><br>"
                + "<a href='" + PROJECT_URL + "'>" + PROJECT_URL + "</a></p>"
                + "</body></html>";

        JEditorPane content = new JEditorPane("text/html", html);
        content.setEditable(false);
        content.setOpaque(false);
        content.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        content.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                openProjectUrl(event.getURL() == null
                        ? PROJECT_URL : event.getURL().toExternalForm());
            }
        });

        JOptionPane.showMessageDialog(this, content,
                "About " + APP_NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Opens the repository in the user's default browser. */
    private void openProjectUrl(String url) {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException(
                        "Opening web links is not supported on this system.");
            }
            Desktop.getDesktop().browse(new URI(url));
        } catch (IOException | URISyntaxException | UnsupportedOperationException exception) {
            showError("Could not open project repository", exception);
        }
    }

    private void showError(String heading, Throwable exception) {
        String detail = exception == null ? "Unknown error" : exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = exception.toString();
        }
        log(heading + ": " + detail);
        JOptionPane.showMessageDialog(this, detail, heading, JOptionPane.ERROR_MESSAGE);
    }

    private void closeDocument() {
        if (document != null) {
            try {
                document.close();
            } catch (IOException exception) {
                log("Warning while closing PDF: " + exception);
            } finally {
                document = null;
                renderer = null;
            }
        }
    }

    @Override
    public void dispose() {
        closeDocument();
        super.dispose();
    }

    /** Custom Swing component that paints the paper and cached source pages. */
    private final class PreviewPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        PreviewPanel() {
            setBackground(new Color(216, 216, 216));
            setMinimumSize(new Dimension(200, 200));
            setPreferredSize(new Dimension(650, 560));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (document == null) {
                    g.setColor(Color.DARK_GRAY);
                    g.drawString("Open a PDF to begin", 24, 32);
                    return;
                }
                paintSheet(g);
            } finally {
                g.dispose();
            }
        }

        private void paintSheet(Graphics2D g) {
            HandoutOptions options = currentOptions();
            PDRectangle paper = options.paper().rectangle(options.orientation());
            double canvasPadding = 20.0;
            double scale = Math.min(
                    (getWidth() - 2 * canvasPadding) / paper.getWidth(),
                    (getHeight() - 2 * canvasPadding) / paper.getHeight());
            if (!Double.isFinite(scale) || scale <= 0) {
                return;
            }

            double paperWidth = paper.getWidth() * scale;
            double paperHeight = paper.getHeight() * scale;
            double paperX = (getWidth() - paperWidth) / 2.0;
            double paperY = (getHeight() - paperHeight) / 2.0;
            g.setColor(Color.WHITE);
            g.fillRect((int) paperX, (int) paperY,
                    (int) Math.ceil(paperWidth), (int) Math.ceil(paperHeight));
            g.setColor(Color.GRAY);
            g.drawRect((int) paperX, (int) paperY,
                    (int) Math.ceil(paperWidth), (int) Math.ceil(paperHeight));

            double margin = OUTPUT_MARGIN * scale;
            double usableWidth = paperWidth - 2 * margin;
            double usableHeight = paperHeight - 2 * margin;
            double cellWidth = usableWidth / options.columns();
            double cellHeight = usableHeight / options.rows();
            double gap = Math.min(cellWidth, cellHeight) * CELL_GAP_FRACTION;
            double numberArea = options.slideNumbers() ? NUMBER_AREA * scale : 0.0;
            int firstPage = (currentSheet() - 1) * options.columns() * options.rows();

            for (int row = 0; row < options.rows(); row++) {
                for (int column = 0; column < options.columns(); column++) {
                    int pageIndex = firstPage + row * options.columns() + column;
                    if (pageIndex >= document.getNumberOfPages()) {
                        continue;
                    }
                    BufferedImage image;
                    synchronized (pageImageCache) {
                        image = pageImageCache.get(pageIndex);
                    }
                    double cellX = paperX + margin + column * cellWidth;
                    double cellY = paperY + margin + row * cellHeight;
                    double availableWidth = cellWidth - 2 * gap;
                    double availableHeight = cellHeight - 2 * gap - numberArea;
                    if (image == null) {
                        g.setColor(new Color(238, 238, 238));
                        g.fillRect((int) (cellX + gap), (int) (cellY + gap),
                                (int) availableWidth, (int) availableHeight);
                        g.setColor(Color.DARK_GRAY);
                        g.drawString("Loading page " + (pageIndex + 1) + "...",
                                (int) (cellX + gap + 8), (int) (cellY + gap + 18));
                        continue;
                    }

                    double imageScale = Math.min(availableWidth / image.getWidth(),
                            availableHeight / image.getHeight());
                    double imageWidth = image.getWidth() * imageScale;
                    double imageHeight = image.getHeight() * imageScale;
                    double imageX = options.leftAlign()
                            ? cellX + gap
                            : cellX + gap + (availableWidth - imageWidth) / 2.0;
                    double imageY = cellY + gap + (availableHeight - imageHeight) / 2.0;

                    BufferedImage displayImage = options.inkSaver()
                            ? createInkSaverImage(image, document, pageIndex)
                            : image;
                    g.drawImage(displayImage, (int) Math.round(imageX), (int) Math.round(imageY),
                            (int) Math.round(imageWidth), (int) Math.round(imageHeight), null);
                    if (options.frame()) {
                        g.setColor(Color.BLACK);
                        g.setStroke(new BasicStroke(1.0f));
                        g.drawRect((int) Math.round(imageX), (int) Math.round(imageY),
                                (int) Math.round(imageWidth), (int) Math.round(imageHeight));
                    }
                    if (options.slideNumbers()) {
                        String number = Integer.toString(pageIndex + 1);
                        Font numberFont = getFont().deriveFont(
                                Math.max(8.0f, NUMBER_FONT_SIZE * (float) scale));
                        g.setFont(numberFont);
                        int textWidth = g.getFontMetrics().stringWidth(number);
                        int numberX = (int) Math.round(imageX + (imageWidth - textWidth) / 2.0);
                        int numberY = (int) Math.round(imageY + imageHeight
                                + NUMBER_GAP * scale + g.getFontMetrics().getAscent());
                        g.setColor(Color.BLACK);
                        g.drawString(number, numberX, numberY);
                    }
                }
            }
        }
    }

    /**
     * Produces a print-friendly image by removing a border-connected dominant
     * background and forcing every identifiable PDF text glyph to black,
     * regardless of its original colour.
     *
     * <p>The algorithm is conservative: it regards only colours similar to the
     * dominant border colour as background. Saturated foreground objects are
     * retained. Ink-saver output is rasterised at 300 DPI; normal output remains
     * vector-preserving.</p>
     */
    private static BufferedImage createInkSaverImage(
            BufferedImage source, PDDocument pdf, int pageIndex) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        int background = estimateBorderBackground(source);
        List<Rectangle2D.Float> textBoxes = extractTextBoxes(pdf, pageIndex);
        PDRectangle cropBox = pdf.getPage(pageIndex).getCropBox();
        int rotation = Math.floorMod(pdf.getPage(pageIndex).getRotation(), 360);
        float logicalPageWidth = rotation == 90 || rotation == 270
                ? cropBox.getHeight() : cropBox.getWidth();
        float logicalPageHeight = rotation == 90 || rotation == 270
                ? cropBox.getWidth() : cropBox.getHeight();
        double textScaleX = width / Math.max(1.0, logicalPageWidth);
        double textScaleY = height / Math.max(1.0, logicalPageHeight);
        int bgR = (background >>> 16) & 0xff;
        int bgG = (background >>> 8) & 0xff;
        int bgB = background & 0xff;
        int backgroundLuminance = (299 * bgR + 587 * bgG + 114 * bgB) / 1000;
        boolean darkBackground = backgroundLuminance < 175;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int r = (rgb >>> 16) & 0xff;
                int g = (rgb >>> 8) & 0xff;
                int b = rgb & 0xff;
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                int luminance = (299 * r + 587 * g + 114 * b) / 1000;
                int bgDistance = Math.abs(r - bgR) + Math.abs(g - bgG) + Math.abs(b - bgB);

                int saturation = max - min;
                boolean nearBackground = bgDistance <= BACKGROUND_DISTANCE * 3;
                boolean alreadyNearWhite = r >= WHITE_THRESHOLD
                        && g >= WHITE_THRESHOLD && b >= WHITE_THRESHOLD;
                boolean darkLowSaturation = luminance <= DARK_TEXT_THRESHOLD
                        && saturation <= 70;

                /*
                 * White and pale text on a dark slide was previously classified
                 * as "already white" before it could become black. Recognise
                 * light, low-saturation pixels that contrast strongly with a
                 * dark detected background as text-like foreground first.
                 * Anti-aliased edge pixels are included by the luminance range.
                 */
                boolean lightTextOnDarkBackground = darkBackground
                        && !nearBackground
                        && luminance >= 155
                        && saturation <= 75
                        && bgDistance > BACKGROUND_DISTANCE * 4;

                /*
                 * PDFTextStripper supplies semantic glyph bounds for genuine
                 * PDF text. Any non-background mark inside those bounds is
                 * forced to black, irrespective of its original colour.
                 */
                boolean identifiableTextPixel = isInsideTextBox(
                        x, y, textBoxes, textScaleX, textScaleY)
                        && !nearBackground
                        && !alreadyNearWhite;

                if (identifiableTextPixel
                        || lightTextOnDarkBackground || darkLowSaturation) {
                    result.setRGB(x, y, 0x000000);
                } else if (nearBackground || alreadyNearWhite) {
                    result.setRGB(x, y, 0xffffff);
                } else {
                    // Preserve coloured foreground graphics, but lighten very
                    // pale fills so that they consume less ink.
                    if (luminance > 220) {
                        r = 255 - (255 - r) / 3;
                        g = 255 - (255 - g) / 3;
                        b = 255 - (255 - b) / 3;
                    }
                    result.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
        }
        return result;
    }

    /** Extracts top-left-oriented glyph bounds for all identifiable PDF text. */
    private static List<Rectangle2D.Float> extractTextBoxes(
            PDDocument pdf, int pageIndex) {
        List<Rectangle2D.Float> boxes = new ArrayList<>();
        try {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition text) {
                    float width = Math.max(0.5f, text.getWidthDirAdj());
                    float height = Math.max(0.5f, text.getHeightDir());
                    float x = text.getXDirAdj();
                    float y = text.getYDirAdj() - height;
                    boxes.add(new Rectangle2D.Float(x, y, width, height));
                    super.processTextPosition(text);
                }
            };
            stripper.setStartPage(pageIndex + 1);
            stripper.setEndPage(pageIndex + 1);
            stripper.getText(pdf);
        } catch (IOException exception) {
            // Background removal can still proceed if semantic text extraction fails.
        }
        return boxes;
    }

    /** Tests whether a rendered-image pixel lies inside an extracted glyph box. */
    private static boolean isInsideTextBox(
            int x, int y, List<Rectangle2D.Float> boxes,
            double scaleX, double scaleY) {
        double pageX = x / scaleX;
        double pageY = y / scaleY;
        for (Rectangle2D.Float box : boxes) {
            if (box.contains(pageX, pageY)) {
                return true;
            }
        }
        return false;
    }

    /** Estimates a dominant background colour from evenly sampled border pixels. */
    private static int estimateBorderBackground(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int samples = 96;
        long rTotal = 0;
        long gTotal = 0;
        long bTotal = 0;
        long count = 0;

        for (int i = 0; i < samples; i++) {
            int x = (int) ((long) i * Math.max(1, width - 1) / Math.max(1, samples - 1));
            int y = (int) ((long) i * Math.max(1, height - 1) / Math.max(1, samples - 1));
            int[] values = {
                    image.getRGB(x, 0), image.getRGB(x, height - 1),
                    image.getRGB(0, y), image.getRGB(width - 1, y)
            };
            for (int rgb : values) {
                rTotal += (rgb >>> 16) & 0xff;
                gTotal += (rgb >>> 8) & 0xff;
                bTotal += rgb & 0xff;
                count++;
            }
        }

        int r = (int) (rTotal / count);
        int g = (int) (gTotal / count);
        int b = (int) (bTotal / count);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Creates the handout with PDFBox. Normal output preserves source pages as
     * vector forms; ink-saver output uses 300-DPI processed images.
     */
    private static int createHandout(PDDocument source, Path output,
                                     HandoutOptions options) throws IOException {
        try (PDDocument target = new PDDocument()) {
            LayerUtility layerUtility = new LayerUtility(target);
            PDType1Font numberFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDRectangle outputBox = options.paper().rectangle(options.orientation());
            int pagesPerSheet = options.columns() * options.rows();
            int sheetCount = (source.getNumberOfPages() + pagesPerSheet - 1) / pagesPerSheet;

            for (int sheet = 0; sheet < sheetCount; sheet++) {
                PDPage outputPage = new PDPage(outputBox);
                target.addPage(outputPage);
                try (PDPageContentStream content = new PDPageContentStream(target, outputPage)) {
                    float usableWidth = outputBox.getWidth() - 2 * OUTPUT_MARGIN;
                    float usableHeight = outputBox.getHeight() - 2 * OUTPUT_MARGIN;
                    float cellWidth = usableWidth / options.columns();
                    float cellHeight = usableHeight / options.rows();
                    float cellGap = Math.min(cellWidth, cellHeight)
                            * (float) CELL_GAP_FRACTION;
                    float numberArea = options.slideNumbers() ? NUMBER_AREA : 0.0f;

                    for (int row = 0; row < options.rows(); row++) {
                        for (int column = 0; column < options.columns(); column++) {
                            int pageIndex = sheet * pagesPerSheet
                                    + row * options.columns() + column;
                            if (pageIndex >= source.getNumberOfPages()) {
                                continue;
                            }

                            float sourceWidth;
                            float sourceHeight;
                            PDFormXObject form = null;
                            BufferedImage inkImage = null;

                            if (options.inkSaver()) {
                                PDFRenderer inkRenderer = new PDFRenderer(source);
                                BufferedImage rendered = inkRenderer.renderImageWithDPI(
                                        pageIndex, INK_SAVER_DPI, ImageType.RGB);
                                inkImage = createInkSaverImage(rendered, source, pageIndex);
                                sourceWidth = inkImage.getWidth();
                                sourceHeight = inkImage.getHeight();
                            } else {
                                form = layerUtility.importPageAsForm(source, pageIndex);
                                PDRectangle box = form.getBBox();
                                sourceWidth = box.getWidth();
                                sourceHeight = box.getHeight();
                            }

                            float cellX = OUTPUT_MARGIN + column * cellWidth;
                            float cellBottom = outputBox.getHeight() - OUTPUT_MARGIN
                                    - (row + 1) * cellHeight;
                            float availableWidth = cellWidth - 2 * cellGap;
                            float availableHeight = cellHeight - 2 * cellGap - numberArea;
                            float scale = Math.min(availableWidth / sourceWidth,
                                                   availableHeight / sourceHeight);
                            float slideWidth = sourceWidth * scale;
                            float slideHeight = sourceHeight * scale;
                            float slideX = options.leftAlign()
                                    ? cellX + cellGap
                                    : cellX + cellGap + (availableWidth - slideWidth) / 2.0f;
                            float contentBottom = cellBottom + cellGap + numberArea;
                            float slideY = contentBottom
                                    + (availableHeight - slideHeight) / 2.0f;

                            if (options.inkSaver()) {
                                PDImageXObject imageObject = LosslessFactory.createFromImage(
                                        target, inkImage);
                                content.drawImage(imageObject, slideX, slideY,
                                        slideWidth, slideHeight);
                            } else {
                                PDRectangle box = form.getBBox();
                                content.saveGraphicsState();
                                content.transform(new Matrix(scale, 0, 0, scale,
                                        slideX - box.getLowerLeftX() * scale,
                                        slideY - box.getLowerLeftY() * scale));
                                content.drawForm(form);
                                content.restoreGraphicsState();
                            }

                            if (options.frame()) {
                                content.setStrokingColor(0);
                                content.setLineWidth(0.5f);
                                content.addRect(slideX, slideY, slideWidth, slideHeight);
                                content.stroke();
                            }
                            if (options.slideNumbers()) {
                                String number = Integer.toString(pageIndex + 1);
                                float textWidth = numberFont.getStringWidth(number)
                                        / 1000.0f * NUMBER_FONT_SIZE;
                                float numberX = slideX + (slideWidth - textWidth) / 2.0f;
                                float numberY = slideY - NUMBER_GAP - NUMBER_FONT_SIZE;
                                content.beginText();
                                content.setNonStrokingColor(0);
                                content.setFont(numberFont, NUMBER_FONT_SIZE);
                                content.newLineAtOffset(numberX, numberY);
                                content.showText(number);
                                content.endText();
                            }
                        }
                    }
                }
            }
            target.save(output.toFile());
            return sheetCount;
        }
    }

    /** Installs the operating system's Swing appearance where available. */
    private static void installSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Swing's cross-platform appearance remains a safe fallback.
        }
    }

    /** Application entry point. */
    public static void main(String[] args) {
        installSystemLookAndFeel();
        SwingUtilities.invokeLater(() -> new PdfHandoutApp().setVisible(true));
    }

    private enum PaperSize {
        A3("A3", 841.89f, 1190.55f),
        A4("A4", 595.28f, 841.89f),
        A5("A5", 419.53f, 595.28f),
        LETTER("Letter", 612.0f, 792.0f),
        LEGAL("Legal", 612.0f, 1008.0f);

        private final String displayName;
        private final float widthPoints;
        private final float heightPoints;

        PaperSize(String displayName, float widthPoints, float heightPoints) {
            this.displayName = displayName;
            this.widthPoints = widthPoints;
            this.heightPoints = heightPoints;
        }

        PDRectangle rectangle(PageOrientation orientation) {
            return orientation == PageOrientation.LANDSCAPE
                    ? new PDRectangle(heightPoints, widthPoints)
                    : new PDRectangle(widthPoints, heightPoints);
        }

        @Override public String toString() { return displayName; }
    }

    private enum PageOrientation {
        PORTRAIT("Portrait"), LANDSCAPE("Landscape");
        private final String displayName;
        PageOrientation(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    private record HandoutOptions(PaperSize paper, PageOrientation orientation,
                                  int columns, int rows, boolean frame,
                                  boolean leftAlign, boolean slideNumbers,
                                  boolean inkSaver) { }
}
