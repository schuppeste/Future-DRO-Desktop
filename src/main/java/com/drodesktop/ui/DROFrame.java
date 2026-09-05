package com.drodesktop.ui;

import com.drodesktop.DRO;
import com.drodesktop.i18n.Messages;
import com.drodesktop.model.Vector3;
import com.drodesktop.service.DroCalculator;
import com.drodesktop.service.SerialDroReceiver;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

public class DROFrame extends JFrame {
    private static final String[] MENU_BUTTONS = {
        "X0", "Y0", "Z0",
        "ZERO", "REF", "SER",
        "D/R", "LIST", "KAL", "IST", "DIFF", "EXIT"
    };
    private static final Path REFERENCE_LIST_DIRECTORY = Path.of("referenzlisten");

    private final DRO dro = new DRO();
    private final SerialDroReceiver serialReceiver = new SerialDroReceiver();
    private final Preferences preferences = Preferences.userNodeForPackage(DROFrame.class);
    private final DecimalFormat fmt = new DecimalFormat("0.###");

    private final SevenSegmentLabel xLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel yLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel zLabel = new SevenSegmentLabel();
    private final JLabel xAxisLabel = new JLabel("X:");
    private final JLabel yAxisLabel = new JLabel("Y:");
    private final JLabel zAxisLabel = new JLabel("Z:");
    private final JLabel statusLabel = new JLabel(Messages.get("status.ready"));
    private final SevenSegmentLabel targetXLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel targetYLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel targetZLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel istXLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel istYLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel istZLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel actionXLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel actionYLabel = new SevenSegmentLabel();
    private final SevenSegmentLabel actionZLabel = new SevenSegmentLabel();
    private final JTextField xKeypadDisplay = new JTextField();
    private final JTextField yKeypadDisplay = new JTextField();
    private final JTextField zKeypadDisplay = new JTextField();
    private final StringBuilder keypadInput = new StringBuilder();
    private JButton toolCompensationButton;
    private JButton istModeButton;
    private JButton diffModeButton;
    private String selectedInputAxis = "X";
    private JPanel menuPanel;
    private JTextField selectedReferenceField;
    private JTextField referenceXField;
    private JTextField referenceYField;
    private JTextField referenceZField;
    private JTextField selectedToolValueField;
    private Runnable submitToolValueAction;
    private Runnable saveReferencePointAction;
    private JDialog referenceListDialog;
    private JTable referencePointView;
    private JLabel referenceIndexLabel;
    private JTextField referenceTableIndexField;
    private int referenceEntryStep;
    private int referenceListIndex = -1;
    private MainDisplayMode mainDisplayMode = MainDisplayMode.IST;
    private boolean toolCompensationEnabled;
    private double toolRadiusMm;
    private boolean toolValueIsDiameter = true;
    private EmptyCoordinateMode emptyCoordinateMode = EmptyCoordinateMode.FREE;

    private enum EmptyCoordinateMode {
        FREE("FREI"),
        ZERO("NULL"),
        PREVIOUS("VORHER");

        private final String label;

        EmptyCoordinateMode(String label) {
            this.label = label;
        }
    }

    private enum MainDisplayMode {
        IST, DIFF
    }

    public DROFrame() {
        super(Messages.get("app.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(18, 22, 27));

        JPanel main = new JPanel(new BorderLayout(16, 16));
        main.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        main.setBackground(new Color(18, 22, 27));

        JPanel readoutPanel = createReadoutPanel();
        readoutPanel.setPreferredSize(new Dimension(470, 0));

        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 0, 8));
        rightPanel.setBackground(new Color(18, 22, 27));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel upperPanel = new JPanel(new BorderLayout(0, 8));
        upperPanel.setBackground(new Color(18, 22, 27));
        menuPanel = createFixedVerticalButtonPanel();
        upperPanel.add(menuPanel, BorderLayout.CENTER);
        upperPanel.add(createIstInputPanel(), BorderLayout.SOUTH);
        rightPanel.add(upperPanel);
        rightPanel.add(createTouchNumpad());
        setDisplayMode(MainDisplayMode.IST);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, readoutPanel, rightPanel);
        splitPane.setBorder(null);
        splitPane.setDividerSize(12);
        splitPane.setResizeWeight(0.42);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(false);

        main.add(splitPane, BorderLayout.CENTER);
        main.add(createReferenceNavigationPanel(), BorderLayout.SOUTH);

        add(main, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateAxisFontSizes();
                syncReferenceListDialogBounds();
            }

            @Override
            public void componentMoved(ComponentEvent event) {
                syncReferenceListDialogBounds();
            }
        });
        refreshDisplay();
    }

    public void prepareFullscreen() {
        setUndecorated(true);
    }

    public void startFullscreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        device.setFullScreenWindow(this);
    }

    private GridBagConstraints createRightPanelConstraints(int row, double weightY) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1.0;
        constraints.weighty = weightY;
        constraints.fill = GridBagConstraints.BOTH;
        return constraints;
    }

    private JPanel createReferenceNavigationPanel() {
        JPanel navigationPanel = new JPanel(new GridBagLayout());
        navigationPanel.setBackground(new Color(18, 22, 27));

        JButton previousButton = createMainNavigationButton("<", Messages.get("nav.tooltip.previous"));
        previousButton.addActionListener(e -> browseReferencePoint(-1));

        Font indexFont = new Font(Font.SANS_SERIF, Font.BOLD, 22);
        JLabel indexLabel = new JLabel("", SwingConstants.CENTER);
        indexLabel.setForeground(new Color(240, 245, 250));
        indexLabel.setFont(indexFont);
        BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        FontMetrics indexMetrics = measureImage.getGraphics().getFontMetrics(indexFont);
        int indexLabelWidth = indexMetrics.stringWidth("000 / 000") + 16;
        indexLabel.setPreferredSize(new Dimension(indexLabelWidth, 64));
        referenceIndexLabel = indexLabel;

        JButton nextButton = createMainNavigationButton(">", Messages.get("nav.tooltip.next"));
        nextButton.addActionListener(e -> browseReferencePoint(1));

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.weightx = 1.0;
        buttonConstraints.fill = GridBagConstraints.BOTH;
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.weightx = 0.0;
        labelConstraints.fill = GridBagConstraints.VERTICAL;
        labelConstraints.insets = new Insets(0, 12, 0, 12);

        navigationPanel.add(previousButton, buttonConstraints);
        navigationPanel.add(indexLabel, labelConstraints);
        navigationPanel.add(nextButton, buttonConstraints);

        updateReferenceIndexLabel();
        return navigationPanel;
    }

    private void updateReferenceIndexLabel() {
        int size = dro.getReferenceList().size();
        String indexText = referenceListIndex >= 0 && referenceListIndex < size
            ? (referenceListIndex + 1) + " / " + size
            : "";
        if (referenceIndexLabel != null) {
            referenceIndexLabel.setText(indexText);
        }
        if (referenceTableIndexField != null) {
            referenceTableIndexField.setText(referenceListIndex >= 0 && referenceListIndex < size
                ? String.valueOf(referenceListIndex + 1)
                : "");
        }
    }

    private JButton createMainNavigationButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        configureFillButtonFont(button);
        button.setPreferredSize(new Dimension(0, 64));
        button.setBackground(new Color(54, 66, 79));
        button.setForeground(new Color(240, 245, 250));
        button.setBorder(BorderFactory.createLineBorder(new Color(124, 142, 158), 1));
        return button;
    }

    private JPanel createReadoutPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(96, 114, 130), 2), Messages.get("panel.dro"), 0, 0,
                new Font(Font.SANS_SERIF, Font.BOLD, 18), new Color(203, 225, 255)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.setBackground(new Color(23, 29, 36));
        panel.setForeground(new Color(230, 236, 243));

        JPanel axisRows = new JPanel(new GridLayout(3, 1, 10, 10));
        axisRows.setBackground(new Color(23, 29, 36));
        axisRows.add(createAxisRow(xAxisLabel, xLabel));
        axisRows.add(createAxisRow(yAxisLabel, yLabel));
        axisRows.add(createAxisRow(zAxisLabel, zLabel));
        panel.add(axisRows, BorderLayout.CENTER);
        panel.add(createActionPanel(), BorderLayout.SOUTH);

        xLabel.setForeground(new Color(104, 196, 255));
        yLabel.setForeground(new Color(118, 219, 160));
        zLabel.setForeground(new Color(255, 170, 120));
        istXLabel.setForeground(new Color(104, 196, 255));
        istYLabel.setForeground(new Color(118, 219, 160));
        istZLabel.setForeground(new Color(255, 170, 120));
        actionXLabel.setForeground(new Color(255, 210, 112));
        actionYLabel.setForeground(new Color(255, 210, 112));
        actionZLabel.setForeground(new Color(255, 210, 112));
        targetXLabel.setForeground(new Color(104, 196, 255));
        targetYLabel.setForeground(new Color(104, 196, 255));
        targetZLabel.setForeground(new Color(104, 196, 255));
        xLabel.setDisplayScale(2);
        yLabel.setDisplayScale(2);
        zLabel.setDisplayScale(2);

        return panel;
    }

    private JPanel createActionPanel() {
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        actionPanel.setPreferredSize(new Dimension(0, 220));
        actionPanel.setBackground(new Color(18, 24, 31));
        actionPanel.setBorder(BorderFactory.createLineBorder(new Color(95, 112, 128), 1));
        actionPanel.add(createActionValueRow("IST", istXLabel, istYLabel, istZLabel, new Color(203, 225, 255)));
        actionPanel.add(createActionValueRow("DIFF", actionXLabel, actionYLabel, actionZLabel, new Color(255, 210, 112)));
        return actionPanel;
    }

    private JPanel createActionValueRow(String mode, SevenSegmentLabel xValue, SevenSegmentLabel yValue,
            SevenSegmentLabel zValue, Color color) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(new Color(18, 24, 31));

        JPanel valuesPanel = new JPanel(new GridLayout(1, 3, 8, 0));
        valuesPanel.setBackground(new Color(18, 24, 31));
        valuesPanel.add(createCompactAxisValue("X", xValue));
        valuesPanel.add(createCompactAxisValue("Y", yValue));
        valuesPanel.add(createCompactAxisValue("Z", zValue));

        JLabel modeLabel = new JLabel(mode, SwingConstants.CENTER);
        modeLabel.setPreferredSize(new Dimension(52, 0));
        configureFillLabelFont(modeLabel);
        modeLabel.setForeground(color);
        row.add(modeLabel, BorderLayout.WEST);
        row.add(valuesPanel, BorderLayout.CENTER);
        return row;
    }

    private JPanel createCompactAxisValue(String axis, SevenSegmentLabel valueLabel) {
        JPanel valuePanel = new JPanel(new BorderLayout(4, 0));
        valuePanel.setBackground(new Color(18, 24, 31));

        JLabel axisLabel = new JLabel(axis, SwingConstants.CENTER);
        axisLabel.setPreferredSize(new Dimension(16, 0));
        configureFillLabelFont(axisLabel);
        axisLabel.setForeground(new Color(203, 225, 255));
        valuePanel.add(axisLabel, BorderLayout.WEST);

        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setOpaque(false);
        valuePanel.add(valueLabel, BorderLayout.CENTER);
        return valuePanel;
    }

    private JPanel createAxisRow(JLabel axisLabel, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setBackground(new Color(34, 42, 52));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(8, 12, 8, 12),
            BorderFactory.createLineBorder(new Color(95, 112, 128), 1)
        ));

        axisLabel.setForeground(new Color(220, 236, 255));
        configureFillLabelFont(axisLabel);
        axisLabel.setHorizontalAlignment(SwingConstants.LEFT);
        axisLabel.setPreferredSize(new Dimension(110, 0));

        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setForeground(new Color(236, 247, 255));
        valueLabel.setBackground(new Color(34, 42, 52));
        valueLabel.setOpaque(true);

        row.add(axisLabel, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.CENTER);
        return row;
    }

    private JPanel createFixedVerticalButtonPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 3, 8, 8));
        panel.setBackground(new Color(23, 29, 36));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(96, 114, 130), 2), Messages.get("panel.menu"), 0, 0,
                new Font(Font.SANS_SERIF, Font.BOLD, 14), new Color(203, 225, 255)),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        for (int index = 0; index < MENU_BUTTONS.length; index++) {
            String label = MENU_BUTTONS[index];
            JButton button = createMenuButton(label);
            configureMenuAction(button, label);
            panel.add(button);
        }

        return panel;
    }

    private JButton createMenuButton(String label) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        configureFillButtonFont(button);
        button.setBackground(new Color(54, 66, 79));
        button.setForeground(new Color(240, 245, 250));
        button.setBorder(BorderFactory.createLineBorder(new Color(124, 142, 158), 1));
        button.setEnabled(!label.isEmpty());
        if ("IST".equals(label)) {
            istModeButton = button;
        } else if ("DIFF".equals(label)) {
            diffModeButton = button;
        }
        return button;
    }

    private void configureMenuAction(JButton button, String label) {
        switch (label) {
            case "<" -> button.addActionListener(e -> browseReferencePoint(-1));
            case ">" -> button.addActionListener(e -> browseReferencePoint(1));
            case "X0" -> button.addActionListener(e -> { dro.setZeroX(); statusLabel.setText(Messages.get("status.axis.zero", "X")); refreshDisplay(); });
            case "Y0" -> button.addActionListener(e -> { dro.setZeroY(); statusLabel.setText(Messages.get("status.axis.zero", "Y")); refreshDisplay(); });
            case "Z0" -> button.addActionListener(e -> { dro.setZeroZ(); statusLabel.setText(Messages.get("status.axis.zero", "Z")); refreshDisplay(); });
            case "ZERO" -> button.addActionListener(e -> { dro.setZeroAll(); statusLabel.setText(Messages.get("status.all.zero")); refreshDisplay(); });
            case "REF" -> button.addActionListener(e -> showReferenceListDialog());
            case "IST" -> button.addActionListener(e -> setDisplayMode(MainDisplayMode.IST));
            case "DIFF" -> button.addActionListener(e -> setDisplayMode(MainDisplayMode.DIFF));
            case "SER" -> button.addActionListener(e -> showSerialConnectionDialog());
            case "LIST" -> button.addActionListener(e -> showReferenceListFileMenu());
            case "KAL" -> button.addActionListener(e -> showCalibrationDialog());
            case "EXIT" -> button.addActionListener(e -> leaveFullscreen());
            case "D/R" -> {
                toolCompensationButton = button;
                button.addActionListener(e -> showToolCompensationDialog());
            }
            default -> { }
        }
    }

    private void showReferenceListFileMenu() {
        try {
            Files.createDirectories(REFERENCE_LIST_DIRECTORY);
        } catch (IOException ex) {
            statusLabel.setText(Messages.get("status.referenceListFolder.unavailable"));
            return;
        }

        JDialog dialog = new JDialog(this, Messages.get("dialog.referenceLists.title"), true);
        DefaultListModel<String> listModel = new DefaultListModel<>();
        refreshReferenceFileList(listModel);
        JList<String> fileList = new JList<>(listModel);
        fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(fileList);

        JButton saveButton = new JButton(Messages.get("button.saveAs"));
        saveButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(dialog, Messages.get("dialog.referenceLists.nameQuery"), Messages.get("dialog.referenceLists.defaultName"));
            if (name != null && !name.isBlank()) {
                saveReferenceListCsv(referenceListPath(name));
                refreshReferenceFileList(listModel);
            }
        });
        JButton loadButton = new JButton(Messages.get("button.load"));
        loadButton.addActionListener(e -> {
            String name = fileList.getSelectedValue();
            if (name == null) {
                statusLabel.setText(Messages.get("status.list.selectToLoad"));
                return;
            }
            loadReferenceListCsv(referenceListPath(name));
            dialog.dispose();
        });
        JButton closeButton = new JButton(Messages.get("button.close"));
        closeButton.addActionListener(e -> dialog.dispose());
        JPanel actions = new JPanel(new GridLayout(1, 3, 6, 0));
        actions.add(saveButton);
        actions.add(loadButton);
        actions.add(closeButton);

        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setSize(420, 380);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void leaveFullscreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        device.setFullScreenWindow(null);
        dispose();
        setUndecorated(false);
        setExtendedState(JFrame.NORMAL);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void refreshReferenceFileList(DefaultListModel<String> listModel) {
        listModel.clear();
        try (Stream<Path> files = Files.list(REFERENCE_LIST_DIRECTORY)) {
            files.filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".csv"))
                .sorted()
                .map(path -> path.getFileName().toString().replaceFirst("(?i)\\.csv$", ""))
                .forEach(listModel::addElement);
        } catch (IOException ex) {
            statusLabel.setText(Messages.get("status.referenceListFolder.unreadable"));
        }
    }

    private Path referenceListPath(String name) {
        String filename = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return REFERENCE_LIST_DIRECTORY.resolve(filename.endsWith(".csv") ? filename : filename + ".csv");
    }

    private void saveReferenceListCsv(Path file) {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("X_mm,Y_mm,Z_mm");
            writer.newLine();
            for (Vector3 point : dro.getReferenceList()) {
                writer.write(String.format(java.util.Locale.US, "%.3f,%.3f,%.3f", point.x, point.y, point.z));
                writer.newLine();
            }
            statusLabel.setText(Messages.get("status.list.saved", file.getFileName()));
        } catch (IOException ex) {
            statusLabel.setText(Messages.get("status.referenceList.notSaved"));
        }
    }

    private void loadReferenceListCsv(Path file) {
        if (!Files.exists(file)) {
            statusLabel.setText(Messages.get("status.referenceList.noneSaved"));
            return;
        }
        try {
            List<Vector3> points = readReferenceListCsv(file);
            dro.replaceReferenceList(points);
            referenceListIndex = points.isEmpty() ? -1 : 0;
            updateReferenceListHighlight();
            updateReferenceIndexLabel();
            refreshDisplay();
            statusLabel.setText(Messages.get("status.referenceList.loaded", points.size()));
        } catch (IOException | NumberFormatException ex) {
            statusLabel.setText(Messages.get("status.referenceList.invalidCsv"));
        }
    }

    private List<Vector3> readReferenceListCsv(Path file) throws IOException {
        List<Vector3> points = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("X_mm")) {
                    continue;
                }
                String[] values = line.trim().split(",");
                if (values.length != 3) {
                    throw new NumberFormatException();
                }
                points.add(new Vector3(
                    Double.parseDouble(values[0].trim()),
                    Double.parseDouble(values[1].trim()),
                    Double.parseDouble(values[2].trim())
                ));
            }
        }
        return points;
    }

    private void showToolCompensationDialog() {
        JComboBox<String> valueTypeSelector = new JComboBox<>(new String[]{Messages.get("combo.diameter"), Messages.get("combo.radius")});
        valueTypeSelector.setSelectedIndex(toolValueIsDiameter ? 0 : 1);
        JTextField valueField = new JTextField(String.format(java.util.Locale.US, "%.3f",
            toolValueIsDiameter ? toolRadiusMm * 2.0 : toolRadiusMm));
        selectedToolValueField = valueField;
        boolean[] lastDiameterSelected = {toolValueIsDiameter};
        valueTypeSelector.addActionListener(e -> {
            boolean diameterSelected = valueTypeSelector.getSelectedIndex() == 0;
            if (diameterSelected != lastDiameterSelected[0]) {
                try {
                    double enteredValue = Double.parseDouble(valueField.getText().trim().replace(',', '.'));
                    double convertedValue = diameterSelected ? enteredValue * 2.0 : enteredValue / 2.0;
                    valueField.setText(String.format(java.util.Locale.US, "%.3f", convertedValue));
                } catch (NumberFormatException ex) {
                    // keep current text if it isn't a parseable number yet
                }
                lastDiameterSelected[0] = diameterSelected;
            }
        });
        JCheckBox enabledCheckBox = new JCheckBox(Messages.get("checkbox.toolCompensation"), toolCompensationEnabled);
        JPanel fieldsPanel = new JPanel(new GridLayout(3, 1, 0, 8));
        fieldsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        fieldsPanel.add(valueTypeSelector);
        fieldsPanel.add(valueField);
        fieldsPanel.add(enabledCheckBox);

        JDialog dialog = new JDialog(this, Messages.get("dialog.toolCompensation.title"), false);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(fieldsPanel, BorderLayout.CENTER);

        JButton okButton = new JButton(Messages.get("button.ok"));
        JButton cancelButton = new JButton(Messages.get("button.cancel"));
        JPanel dialogActions = new JPanel(new GridLayout(1, 2, 6, 0));
        dialogActions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        dialogActions.add(okButton);
        dialogActions.add(cancelButton);
        dialog.add(dialogActions, BorderLayout.SOUTH);

        Runnable applyAndClose = () -> {
            try {
                double enteredValueMm = Double.parseDouble(valueField.getText().trim().replace(',', '.'));
                if (enteredValueMm <= 0) {
                    throw new NumberFormatException();
                }
                toolValueIsDiameter = valueTypeSelector.getSelectedIndex() == 0;
                toolRadiusMm = toolValueIsDiameter ? enteredValueMm / 2.0 : enteredValueMm;
                toolCompensationEnabled = enabledCheckBox.isSelected();
                toolCompensationButton.setText(toolCompensationEnabled
                    ? toolValueIsDiameter ? Messages.get("button.toolCompensation.diameterOn") : Messages.get("button.toolCompensation.radiusOn")
                    : Messages.get("button.toolCompensation.off"));
                statusLabel.setText(toolCompensationEnabled
                    ? Messages.get("status.tool.offset", String.format(java.util.Locale.US, "%.3f", toolRadiusMm))
                    : Messages.get("status.tool.off"));
                refreshDisplay();
                selectedToolValueField = null;
                submitToolValueAction = null;
                dialog.dispose();
            } catch (NumberFormatException ex) {
                statusLabel.setText(Messages.get("status.tool.invalidDiameter"));
            }
        };
        submitToolValueAction = applyAndClose;
        okButton.addActionListener(e -> applyAndClose.run());
        cancelButton.addActionListener(e -> {
            selectedToolValueField = null;
            submitToolValueAction = null;
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(okButton);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                selectedToolValueField = null;
                submitToolValueAction = null;
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        valueField.requestFocusInWindow();
    }

    private void showCalibrationDialog() {
        JComboBox<String> axisSelector = new JComboBox<>(new String[]{"X", "Y", "Z"});
        JTextField displayedField = new JTextField("100.000");
        JTextField measuredField = new JTextField("100.000");
        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel(Messages.get("label.axis")));
        panel.add(axisSelector);
        panel.add(new JLabel(Messages.get("label.displayedMm")));
        panel.add(displayedField);
        panel.add(new JLabel(Messages.get("label.measuredMm")));
        panel.add(measuredField);

        int option = JOptionPane.showConfirmDialog(this, panel, Messages.get("dialog.calibration.title"),
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            double displayed = Double.parseDouble(displayedField.getText().trim().replace(',', '.'));
            double measured = Double.parseDouble(measuredField.getText().trim().replace(',', '.'));
            if (displayed <= 0 || measured <= 0) {
                throw new NumberFormatException();
            }
            String axis = (String) axisSelector.getSelectedItem();
            double factor = measured / displayed;
            dro.setLinearFactor(axis, factor);
            statusLabel.setText(Messages.get("status.calibration.factor", axis, String.format(java.util.Locale.US, "%.8f", factor)));
            refreshDisplay();
        } catch (NumberFormatException ex) {
            statusLabel.setText(Messages.get("status.calibration.invalid"));
        }
    }

    private void showSerialConnectionDialog() {
        String[] ports;
        try {
            ports = SerialDroReceiver.availablePortNames();
        } catch (Throwable ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, Messages.get("status.serial.portsError", ex),
                Messages.get("dialog.connectionError.title"), JOptionPane.ERROR_MESSAGE);
            ports = new String[0];
        }
        JComboBox<String> portSelector = new JComboBox<>(ports);
        JButton connectButton = new JButton(Messages.get("button.connect"));
        JButton disconnectButton = new JButton(Messages.get("button.disconnect"));
        JPanel panel = new JPanel(new GridLayout(2, 1, 6, 6));
        panel.add(portSelector);
        JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
        actions.add(connectButton);
        actions.add(disconnectButton);
        panel.add(actions);

        JDialog dialog = new JDialog(this, Messages.get("dialog.serial.title"), false);
        dialog.add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        connectButton.addActionListener(e -> {
            String portName = (String) portSelector.getSelectedItem();
            if (portName == null) {
                statusLabel.setText(Messages.get("status.serial.noPortSelected"));
                return;
            }
            if (connectSerialPort(portName)) {
                dialog.dispose();
            }
        });
        disconnectButton.addActionListener(e -> {
            serialReceiver.close();
            statusLabel.setText(Messages.get("status.serial.disconnected"));
            dialog.dispose();
        });
        dialog.setVisible(true);
    }

    public void connectFirstAvailableSerialPort() {
        String[] ports = SerialDroReceiver.availablePortNames();
        if (ports.length == 0) {
            statusLabel.setText(Messages.get("status.serial.noPortFound"));
            return;
        }
        String rememberedPort = preferences.get("serialPort", "");
        if (!rememberedPort.isEmpty()) {
            for (String port : ports) {
                if (rememberedPort.equals(port) && connectSerialPort(port)) {
                    return;
                }
            }
        }
        connectSerialPort(ports[0]);
    }

    public boolean connectToSerialPort(String portName) {
        return connectSerialPort(portName);
    }

    private boolean connectSerialPort(String portName) {
        try {
            serialReceiver.connect(portName, position -> SwingUtilities.invokeLater(() -> {
                dro.setMachinePosition(position);
                refreshDisplay();
            }));
            preferences.put("serialPort", portName);
            statusLabel.setText(Messages.get("status.serial.connected", portName));
            return true;
        } catch (Throwable ex) {
            String message = Messages.get("status.serial.connectFailed", portName, ex);
            statusLabel.setText(message);
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, message, Messages.get("dialog.connectionError.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private JPanel createTouchNumpad() {
        JPanel keypad = new JPanel(new BorderLayout());
        keypad.setBackground(new Color(23, 29, 36));
        keypad.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(96, 114, 130), 2), Messages.get("panel.keypad"), 0, 0,
                new Font(Font.SANS_SERIF, Font.BOLD, 14), new Color(203, 225, 255)),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JPanel keysPanel = new JPanel(new GridLayout(5, 3, 6, 6));
        keysPanel.setBackground(new Color(23, 29, 36));
        String[] keys = {"7", "8", "9", "4", "5", "6", "1", "2", "3", "-", "0", ".", "CLR", "REF", "ENT"};
        for (String key : keys) {
            JButton button = new JButton(key);
            button.setFocusPainted(false);
            button.setBackground(new Color(54, 66, 79));
            button.setForeground(new Color(240, 245, 250));
            configureFillButtonFont(button);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(124, 142, 158), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            button.addActionListener(e -> handleKeypadInput(key));
            keysPanel.add(button);
        }

        keypad.add(keysPanel, BorderLayout.CENTER);

        return keypad;
    }

    private JPanel createIstInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setPreferredSize(new Dimension(0, 104));
        panel.setBackground(new Color(23, 29, 36));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(96, 114, 130), 2), Messages.get("panel.istInput"), 0, 0,
                new Font(Font.SANS_SERIF, Font.BOLD, 14), new Color(203, 225, 255)),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JPanel fields = new JPanel(new GridLayout(1, 3, 6, 0));
        fields.setBackground(new Color(23, 29, 36));
        fields.add(createIstInputField("X", xKeypadDisplay));
        fields.add(createIstInputField("Y", yKeypadDisplay));
        fields.add(createIstInputField("Z", zKeypadDisplay));
        panel.add(fields, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createIstInputField(String axis, JTextField field) {
        JPanel inputField = new JPanel(new BorderLayout(4, 0));
        inputField.setBackground(new Color(23, 29, 36));
        JButton axisButton = new JButton(axis);
        axisButton.setFocusPainted(false);
        configureFillButtonFont(axisButton);
        axisButton.addActionListener(e -> selectInputAxis(axis));
        inputField.add(axisButton, BorderLayout.WEST);

        field.setEditable(true);
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        field.setBackground(new Color(12, 18, 23));
        field.setForeground(new Color(208, 239, 255));
        field.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                selectInputAxis(axis);
            }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                selectInputAxis(axis);
            }
        });
        field.addActionListener(e -> applyKeyboardIstTarget(axis, field));
        inputField.add(field, BorderLayout.CENTER);
        return inputField;
    }

    private JButton createFixedSideButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setPreferredSize(new Dimension(120, 46));
        button.setMinimumSize(new Dimension(120, 46));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        button.setBackground(new Color(54, 66, 79));
        button.setForeground(new Color(240, 245, 250));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(124, 142, 158), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        return button;
    }

    private void addButton(Container container, String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        button.setBackground(new Color(54, 66, 79));
        button.setForeground(new Color(240, 245, 250));
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(124, 142, 158), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        button.addActionListener(e -> action.run());
        container.add(button);
    }

    private void refreshDisplay() {
        Vector3 actual = dro.getWorkPosition();
        Vector3 target = getActiveReferencePoint();
        Vector3 difference = target == null ? new Vector3(0, 0, 0) : target.subtract(actual);
        Vector3 displayed = switch (mainDisplayMode) {
            case DIFF -> difference;
            case IST -> actual;
        };

        xLabel.setText(String.format(java.util.Locale.US, "%07.3f", displayed.x));
        yLabel.setText(String.format(java.util.Locale.US, "%07.3f", displayed.y));
        zLabel.setText(String.format(java.util.Locale.US, "%07.3f", displayed.z));
        targetXLabel.setText(formatActionValue(target == null ? 0 : target.x));
        targetYLabel.setText(formatActionValue(target == null ? 0 : target.y));
        targetZLabel.setText(formatActionValue(target == null ? 0 : target.z));
        istXLabel.setText(formatActionValue(actual.x));
        istYLabel.setText(formatActionValue(actual.y));
        istZLabel.setText(formatActionValue(actual.z));
        actionXLabel.setText(formatActionValue(difference.x));
        actionYLabel.setText(formatActionValue(difference.y));
        actionZLabel.setText(formatActionValue(difference.z));
        updateAxisFontSizes();
    }

    private String formatActionValue(double value) {
        return String.format(java.util.Locale.US, "%07.3f", value);
    }

    private Vector3 getActiveReferencePoint() {
        List<Vector3> points = dro.getReferenceList();
        if (referenceListIndex < 0 || referenceListIndex >= points.size()) {
            return null;
        }
        Vector3 reference = points.get(referenceListIndex).copy();
        if (toolCompensationEnabled) {
            reference.x -= toolRadiusMm;
            reference.y -= toolRadiusMm;
        }
        return reference;
    }

    private void handleKeypadInput(String key) {
        if ("REF".equals(key)) {
            showReferenceListDialog();
            return;
        }
        if (selectedReferenceField != null) {
            handleReferenceKeypadInput(key);
            return;
        }
        if (selectedToolValueField != null) {
            if ("CLR".equals(key)) {
                selectedToolValueField.setText("");
            } else if ("ENT".equals(key)) {
                if (submitToolValueAction != null) {
                    submitToolValueAction.run();
                }
                return;
            } else if (".".equals(key) && selectedToolValueField.getText().contains(".")) {
                return;
            } else if ("-".equals(key) && !selectedToolValueField.getText().isEmpty()) {
                return;
            } else if (!"REF".equals(key)) {
                selectedToolValueField.setText(selectedToolValueField.getText() + key);
            }
            selectedToolValueField.requestFocusInWindow();
            return;
        }
        if ("CLR".equals(key)) {
            keypadInput.setLength(0);
        } else if ("ENT".equals(key)) {
            applyKeypadTarget();
            return;
        } else if (".".equals(key) && keypadInput.indexOf(".") >= 0) {
            return;
        } else if ("-".equals(key) && keypadInput.length() > 0) {
            return;
        } else {
            keypadInput.append(key);
        }
        getSelectedInputDisplay().setText(keypadInput.toString());
    }

    private void applyKeypadTarget() {
        if (keypadInput.isEmpty() || "-".contentEquals(keypadInput) || ".".contentEquals(keypadInput)) {
            statusLabel.setText(Messages.get("status.value.enter"));
            return;
        }

        try {
            double value = Double.parseDouble(keypadInput.toString());
            applyIstCoordinate(selectedInputAxis, value);
            keypadInput.setLength(0);
            getSelectedInputDisplay().setText("");
        } catch (NumberFormatException ex) {
            statusLabel.setText(Messages.get("status.value.invalid"));
        }
    }

    private void applyKeyboardIstTarget(String axis, JTextField field) {
        try {
            applyIstCoordinate(axis, Double.parseDouble(field.getText().trim().replace(',', '.')));
            field.setText("");
        } catch (NumberFormatException ex) {
            statusLabel.setText(Messages.get("status.value.invalid"));
        }
    }

    private void applyIstCoordinate(String axis, double value) {
        Vector3 target = dro.getWorkPosition();
        switch (axis) {
            case "X" -> target.x = value;
            case "Y" -> target.y = value;
            case "Z" -> target.z = value;
            default -> throw new IllegalStateException("Unknown axis");
        }
        dro.setWorkPosition(target);
        statusLabel.setText(Messages.get("status.ist.set", axis, String.format(java.util.Locale.US, "%.3f", value)));
        refreshDisplay();
    }

    private void selectInputAxis(String axis) {
        selectedInputAxis = axis;
        selectedReferenceField = null;
        keypadInput.setLength(0);
        updateIstInputHighlight();
    }

    private void updateIstInputHighlight() {
        Color inactive = new Color(12, 18, 23);
        Color active = new Color(83, 91, 99);
        xKeypadDisplay.setBackground("X".equals(selectedInputAxis) ? active : inactive);
        yKeypadDisplay.setBackground("Y".equals(selectedInputAxis) ? active : inactive);
        zKeypadDisplay.setBackground("Z".equals(selectedInputAxis) ? active : inactive);
    }

    private JTextField getSelectedInputDisplay() {
        return switch (selectedInputAxis) {
            case "X" -> xKeypadDisplay;
            case "Y" -> yKeypadDisplay;
            case "Z" -> zKeypadDisplay;
            default -> throw new IllegalStateException("Unknown axis");
        };
    }

    private void showReferenceListDialog() {
        if (referenceListDialog != null && referenceListDialog.isDisplayable()) {
            if (referenceListDialog.isVisible()) {
                referenceListDialog.dispose();
            } else {
                referenceListDialog.setVisible(true);
                referenceListDialog.toFront();
                referenceListDialog.requestFocus();
            }
            return;
        }

        JDialog dialog = new JDialog(this, Messages.get("dialog.referenceList.title"), false);
        referenceListDialog = dialog;
    dialog.setUndecorated(true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.getContentPane().setBackground(new Color(23, 29, 36));

        final int indexColumnWidth = 70;

        JTextField xField = new JTextField();
        JTextField yField = new JTextField();
        JTextField zField = new JTextField();
        referenceXField = xField;
        referenceYField = yField;
        referenceZField = zField;
        JButton xSelectButton = new JButton("X");
        JButton ySelectButton = new JButton("Y");
        JButton zSelectButton = new JButton("Z");
        Font referenceInputFont = new Font(Font.MONOSPACED, Font.BOLD, 28);
        xField.setFont(referenceInputFont);
        yField.setFont(referenceInputFont);
        zField.setFont(referenceInputFont);
        xSelectButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        ySelectButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        zSelectButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));

        DefaultTableModel pointModel = new DefaultTableModel(new Object[]{"", "X", "Y", "Z"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        List<Vector3> initialPoints = dro.getReferenceList();
        for (int i = 0; i < initialPoints.size(); i++) {
            pointModel.addRow(referenceTableRow(i, initialPoints.get(i)));
        }
        JTable pointTable = new JTable(pointModel);
        referencePointView = pointTable;
        pointTable.setShowGrid(false);
        pointTable.setIntercellSpacing(new Dimension(0, 0));
        pointTable.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
        pointTable.setRowHeight(40);
        pointTable.setBackground(Color.WHITE);
        pointTable.setForeground(Color.BLACK);
        pointTable.setSelectionBackground(new Color(232, 132, 45));
        pointTable.setSelectionForeground(new Color(20, 24, 28));
        pointTable.setRowSelectionAllowed(true);
        pointTable.setColumnSelectionAllowed(false);
        pointTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pointTable.setFillsViewportHeight(true);
        DefaultTableCellRenderer leftAlignedRenderer = new DefaultTableCellRenderer();
        leftAlignedRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        for (int column = 0; column < pointTable.getColumnCount(); column++) {
            pointTable.getColumnModel().getColumn(column).setCellRenderer(leftAlignedRenderer);
        }
        TableColumn indexColumn = pointTable.getColumnModel().getColumn(0);
        indexColumn.setResizable(false);
        indexColumn.setMinWidth(indexColumnWidth);
        indexColumn.setMaxWidth(indexColumnWidth);
        indexColumn.setPreferredWidth(indexColumnWidth);

        JTableHeader pointTableHeader = pointTable.getTableHeader();
        pointTableHeader.setLayout(null);
        pointTableHeader.setReorderingAllowed(false);
        pointTableHeader.setResizingAllowed(false);
        pointTableHeader.setBackground(new Color(23, 29, 36));
        pointTableHeader.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(232, 132, 45)),
            BorderFactory.createEmptyBorder(0, 0, 6, 0)
        ));
        pointTableHeader.setPreferredSize(new Dimension(pointTableHeader.getPreferredSize().width, 105));
        JPanel xHeaderCell = createReferenceHeaderCell(xSelectButton, xField);
        JPanel yHeaderCell = createReferenceHeaderCell(ySelectButton, yField);
        JPanel zHeaderCell = createReferenceHeaderCell(zSelectButton, zField);
        pointTableHeader.add(xHeaderCell);
        pointTableHeader.add(yHeaderCell);
        pointTableHeader.add(zHeaderCell);
        JTextField indexHeaderField = new JTextField();
        indexHeaderField.setHorizontalAlignment(SwingConstants.CENTER);
        indexHeaderField.setFont(referenceInputFont);
        indexHeaderField.setEditable(false);
        indexHeaderField.setFocusable(false);
        referenceTableIndexField = indexHeaderField;
        JButton indexCaption = new JButton(Messages.get("index.caption"));
        indexCaption.setFocusable(false);
        indexCaption.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        JPanel indexHeaderCell = createReferenceHeaderCell(indexCaption, indexHeaderField);
        pointTableHeader.add(indexHeaderCell);
        Runnable syncHeaderFieldBounds = () -> {
            int bottomInset = pointTableHeader.getInsets().bottom;
            Rectangle indexRect = pointTableHeader.getHeaderRect(0);
            Rectangle xRect = pointTableHeader.getHeaderRect(1);
            Rectangle yRect = pointTableHeader.getHeaderRect(2);
            Rectangle zRect = pointTableHeader.getHeaderRect(3);
            indexHeaderCell.setBounds(indexRect.x, indexRect.y, indexRect.width, indexRect.height - bottomInset);
            xHeaderCell.setBounds(xRect.x, xRect.y, xRect.width, xRect.height - bottomInset);
            yHeaderCell.setBounds(yRect.x, yRect.y, yRect.width, yRect.height - bottomInset);
            zHeaderCell.setBounds(zRect.x, zRect.y, zRect.width, zRect.height - bottomInset);
        };
        syncHeaderFieldBounds.run();
        pointTableHeader.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                syncHeaderFieldBounds.run();
            }
        });
        pointTable.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(ChangeEvent event) {
                syncHeaderFieldBounds.run();
            }

            @Override
            public void columnAdded(TableColumnModelEvent event) { }

            @Override
            public void columnRemoved(TableColumnModelEvent event) { }

            @Override
            public void columnMoved(TableColumnModelEvent event) { }

            @Override
            public void columnSelectionChanged(ListSelectionEvent event) { }
        });
        pointTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && pointTable.getSelectedRow() >= 0) {
                int selectedIndex = pointTable.getSelectedRow();
                Vector3 point = dro.getReferenceList().get(selectedIndex);
                referenceListIndex = selectedIndex;
                xField.setText(formatActionValue(point.x));
                yField.setText(formatActionValue(point.y));
                zField.setText(formatActionValue(point.z));
                statusLabel.setText(Messages.get("status.referencePoint.active", selectedIndex + 1));
                updateReferenceIndexLabel();
                refreshDisplay();
            }
        });
        JPanel tableContainer = new JPanel(new BorderLayout());
        tableContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        tableContainer.setBackground(new Color(23, 29, 36));
        tableContainer.add(new JScrollPane(pointTable), BorderLayout.CENTER);
        dialog.add(tableContainer, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(1, 7, 6, 0));
        actions.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        actions.setBackground(Color.BLACK);
        JButton addButton = new JButton(Messages.get("button.pointAdd"));
        addButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        addButton.addActionListener(e -> {
            try {
                Vector3 point = createReferencePoint(xField, yField, zField);
                dro.addReferenceListPoint(point);
                pointModel.addRow(referenceTableRow(pointModel.getRowCount(), point));
                xField.setText("");
                yField.setText("");
                zField.setText("");
                selectReferenceField(xField);
            } catch (NumberFormatException ex) {
                statusLabel.setText(Messages.get("status.xyzInvalid"));
            }
        });
        saveReferencePointAction = addButton::doClick;
        JButton previousButton = createEmptyCoordinateSubmitButton(Messages.get("button.usePrevious"), EmptyCoordinateMode.PREVIOUS, addButton);
        JButton zeroButton = createEmptyCoordinateSubmitButton(Messages.get("button.useZero"), EmptyCoordinateMode.ZERO, addButton);
        JButton updateButton = new JButton(Messages.get("button.change"));
        updateButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        updateButton.addActionListener(e -> {
            int selected = pointTable.getSelectedRow();
            if (selected < 0) {
                statusLabel.setText(Messages.get("status.referencePoint.selectToChange"));
                return;
            }
            try {
                Vector3 point = new Vector3(parseCoordinate(xField), parseCoordinate(yField), parseCoordinate(zField));
                dro.updateReferenceListPoint(selected, point);
                setReferenceTableRow(pointModel, selected, point);
                referenceListIndex = selected;
                statusLabel.setText(Messages.get("status.referencePoint.changed", selected + 1));
                updateReferenceIndexLabel();
                refreshDisplay();
            } catch (NumberFormatException ex) {
                statusLabel.setText(Messages.get("status.xyzInvalid"));
            }
        });
        JButton removeButton = new JButton(Messages.get("button.delete"));
        removeButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        removeButton.addActionListener(e -> {
            int selected = pointTable.getSelectedRow();
            if (selected >= 0) {
                dro.removeReferenceListPoint(selected);
                pointModel.removeRow(selected);
                for (int i = selected; i < pointModel.getRowCount(); i++) {
                    setReferenceTableRow(pointModel, i, dro.getReferenceList().get(i));
                }
                if (referenceListIndex >= pointModel.getRowCount()) {
                    referenceListIndex = pointModel.getRowCount() - 1;
                }
                updateReferenceIndexLabel();
            }
        });
        JButton clearButton = new JButton(Messages.get("button.clear"));
        clearButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        clearButton.addActionListener(e -> {
            dro.clearReferenceList();
            pointModel.setRowCount(0);
            xField.setText("");
            yField.setText("");
            zField.setText("");
            pointTable.clearSelection();
            referenceListIndex = -1;
            selectedReferenceField = null;
            updateReferenceIndexLabel();
        });
        JButton startButton = new JButton(Messages.get("button.start"));
        startButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        startButton.addActionListener(e -> {
            if (!dro.getReferenceList().isEmpty()) {
                referenceListIndex = 0;
                setDisplayMode(MainDisplayMode.DIFF);
                statusLabel.setText(Messages.get("status.referenceList.started"));
                updateReferenceIndexLabel();
                refreshDisplay();
                dialog.dispose();
            }
        });
        dialog.getRootPane().setDefaultButton(addButton);
        actions.add(addButton);
        actions.add(previousButton);
        actions.add(zeroButton);
        actions.add(updateButton);
        actions.add(removeButton);
        actions.add(clearButton);
        actions.add(startButton);
        dialog.add(actions, BorderLayout.SOUTH);
        selectReferenceField(xField);
        xSelectButton.addActionListener(e -> selectReferenceField(xField));
        ySelectButton.addActionListener(e -> selectReferenceField(yField));
        zSelectButton.addActionListener(e -> selectReferenceField(zField));
        xField.addFocusListener(createReferenceFieldFocusListener(xField));
        yField.addFocusListener(createReferenceFieldFocusListener(yField));
        zField.addFocusListener(createReferenceFieldFocusListener(zField));
        xField.addActionListener(e -> handleReferenceKeypadInput("ENT"));
        yField.addActionListener(e -> handleReferenceKeypadInput("ENT"));
        zField.addActionListener(e -> saveReferencePointAction.run());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                clearReferenceEditorState();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                clearReferenceEditorState();
            }
        });
        dialog.setSize(menuPanel.getSize());
        dialog.setLocation(menuPanel.getLocationOnScreen());
        dialog.setVisible(true);
        updateReferenceListHighlight();
        updateReferenceIndexLabel();
        SwingUtilities.invokeLater(() -> selectReferenceField(xField));
    }

    private void syncReferenceListDialogBounds() {
        if (referenceListDialog != null && referenceListDialog.isVisible() && menuPanel.isShowing()) {
            referenceListDialog.setSize(menuPanel.getSize());
            referenceListDialog.setLocation(menuPanel.getLocationOnScreen());
        }
    }

    private JPanel createReferenceHeaderCell(JComponent caption, JTextField field) {
        JPanel cell = new JPanel(new BorderLayout(0, 4));
        cell.setBackground(new Color(23, 29, 36));
        cell.add(caption, BorderLayout.NORTH);
        cell.add(field, BorderLayout.CENTER);
        return cell;
    }

    private JButton createEmptyCoordinateSubmitButton(String label, EmptyCoordinateMode mode, JButton addButton) {
        JButton button = new JButton(label);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        button.addActionListener(e -> {
            emptyCoordinateMode = mode;
            addButton.doClick();
            emptyCoordinateMode = EmptyCoordinateMode.FREE;
        });
        return button;
    }

    private Vector3 createReferencePoint(JTextField xField, JTextField yField, JTextField zField) {
        Vector3 previousPoint = dro.getReferenceList().isEmpty() ? null
            : dro.getReferenceList().get(dro.getReferenceList().size() - 1);
        return new Vector3(
            resolveReferenceCoordinate(xField, previousPoint == null ? 0 : previousPoint.x),
            resolveReferenceCoordinate(yField, previousPoint == null ? 0 : previousPoint.y),
            resolveReferenceCoordinate(zField, previousPoint == null ? 0 : previousPoint.z)
        );
    }

    private double resolveReferenceCoordinate(JTextField field, double previousValue) {
        String text = field.getText().trim();
        if (!text.isEmpty()) {
            return Double.parseDouble(text.replace(',', '.'));
        }
        return switch (emptyCoordinateMode) {
            case ZERO -> 0.0;
            case PREVIOUS -> previousValue;
            case FREE -> throw new NumberFormatException("Leerer Wert");
        };
    }

    private void clearReferenceEditorState() {
        selectedReferenceField = null;
        referenceXField = null;
        referenceYField = null;
        referenceZField = null;
        saveReferencePointAction = null;
        referencePointView = null;
        referenceTableIndexField = null;
        referenceListDialog = null;
        referenceEntryStep = 0;
    }

    private void selectReferenceField(JTextField field) {
        selectedReferenceField = field;
        referenceEntryStep = field == referenceXField ? 0 : field == referenceYField ? 1 : 2;
        updateReferenceInputHighlight();
        field.requestFocusInWindow();
        field.selectAll();
    }

    private void updateReferenceInputHighlight() {
        Color inactive = Color.WHITE;
        Color active = new Color(190, 190, 190);
        referenceXField.setBackground(selectedReferenceField == referenceXField ? active : inactive);
        referenceYField.setBackground(selectedReferenceField == referenceYField ? active : inactive);
        referenceZField.setBackground(selectedReferenceField == referenceZField ? active : inactive);
    }

    private FocusAdapter createReferenceFieldFocusListener(JTextField field) {
        return new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                selectReferenceField(field);
            }
        };
    }

    private void handleReferenceKeypadInput(String key) {
        if ("CLR".equals(key)) {
            selectedReferenceField.setText("");
        } else if ("ENT".equals(key)) {
            advanceReferenceField();
        } else if ("REF".equals(key)) {
            statusLabel.setText(Messages.get("status.referenceList.editing"));
        } else if (".".equals(key) && !selectedReferenceField.getText().contains(".")) {
            selectedReferenceField.setText(selectedReferenceField.getText() + key);
        } else if ("-".equals(key) && selectedReferenceField.getText().isEmpty()) {
            selectedReferenceField.setText(key);
        } else if (!".".equals(key) && !"-".equals(key)) {
            selectedReferenceField.setText(selectedReferenceField.getText() + key);
        }
    }

    private void advanceReferenceField() {
        if (referenceEntryStep == 0) {
            selectReferenceField(referenceYField);
        } else if (referenceEntryStep == 1) {
            selectReferenceField(referenceZField);
        } else {
            saveReferencePointAction.run();
            selectReferenceField(referenceXField);
        }
    }

    private void configureFillButtonFont(JButton button) {
        button.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                resizeFontToFit(button, button.getText(), Font.SANS_SERIF, Font.BOLD, 8, 6);
            }
        });
    }

    private void configureFillLabelFont(JLabel label) {
        label.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                resizeFontToFit(label, label.getText(), Font.SANS_SERIF, Font.BOLD, 4, 4);
            }
        });
    }

    private void resizeFontToFit(JComponent component, String text, String fontName, int style, int horizontalPadding,
            int verticalPadding) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int availableWidth = component.getWidth() - component.getInsets().left - component.getInsets().right - horizontalPadding;
        int availableHeight = component.getHeight() - component.getInsets().top - component.getInsets().bottom - verticalPadding;
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        Font baseFont = new Font(fontName, style, 1);
        for (int size = availableHeight; size > 1; size--) {
            Font candidate = baseFont.deriveFont((float) size);
            FontMetrics metrics = component.getFontMetrics(candidate);
            if (metrics.stringWidth(text) <= availableWidth && metrics.getHeight() <= availableHeight) {
                component.setFont(candidate);
                return;
            }
        }
    }

    private JLabel createInputLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(new Color(220, 236, 255));
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        return label;
    }

    private double parseCoordinate(JTextField field) {
        return Double.parseDouble(field.getText().trim().replace(',', '.'));
    }

    private Object[] referenceTableRow(int index, Vector3 point) {
        return new Object[]{String.format("%3d", index + 1), formatActionValue(point.x), formatActionValue(point.y),
            formatActionValue(point.z)};
    }

    private void setReferenceTableRow(DefaultTableModel model, int rowIndex, Vector3 point) {
        Object[] row = referenceTableRow(rowIndex, point);
        for (int column = 0; column < row.length; column++) {
            model.setValueAt(row[column], rowIndex, column);
        }
    }

    private void browseReferencePoint(int direction) {
        List<Vector3> points = dro.getReferenceList();
        if (points.isEmpty() || referenceListIndex < 0) {
            statusLabel.setText(Messages.get("status.referenceList.startFirst"));
            return;
        }
        referenceListIndex = Math.max(0, Math.min(points.size() - 1, referenceListIndex + direction));
        statusLabel.setText(Messages.get("status.referencePoint.of", referenceListIndex + 1, points.size()));
        updateReferenceListHighlight();
        updateReferenceIndexLabel();
        refreshDisplay();
    }

    private void updateReferenceListHighlight() {
        if (referencePointView != null && referenceListIndex >= 0
                && referenceListIndex < referencePointView.getRowCount()
                && referencePointView.getSelectedRow() != referenceListIndex) {
            referencePointView.setRowSelectionInterval(referenceListIndex, referenceListIndex);
            referencePointView.scrollRectToVisible(referencePointView.getCellRect(referenceListIndex, 0, true));
        }
    }

    private void setDisplayMode(MainDisplayMode mode) {
        mainDisplayMode = mode;
        Color activeColor = new Color(230, 145, 48);
        Color inactiveColor = new Color(54, 66, 79);
        if (istModeButton != null) {
            istModeButton.setBackground(mode == MainDisplayMode.IST ? activeColor : inactiveColor);
        }
        if (diffModeButton != null) {
            diffModeButton.setBackground(mode == MainDisplayMode.DIFF ? activeColor : inactiveColor);
        }
        refreshDisplay();
    }

    private void updateAxisFontSizes() {
        int panelWidth = getContentPane().getWidth();
        int base = Math.max(2, Math.min(4, panelWidth / 280));
        xLabel.setDisplayScale(base);
        yLabel.setDisplayScale(base);
        zLabel.setDisplayScale(base);
        actionXLabel.setDisplayScale(base);
        actionYLabel.setDisplayScale(base);
        actionZLabel.setDisplayScale(base);
        istXLabel.setDisplayScale(base);
        istYLabel.setDisplayScale(base);
        istZLabel.setDisplayScale(base);
    }

    private String fmt(double value) {
        return fmt.format(value);
    }
}
