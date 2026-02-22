package com.secuworm.endpointcollector.presentation;

import com.secuworm.endpointcollector.domain.EndpointRecord;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

public class TabView {
    private static final String LICENSE_TEXT = "Copyright © 2026 SECUWORM. All rights reserved.";

    private final JPanel rootPanel;
    private final JTextField searchField;
    private final JCheckBox regexCheckBox;
    private final JButton exportButton;
    private final JButton sendToRepeaterButton;
    private final JLabel statusLabel;
    private final EndpointTableModel tableModel;
    private final JTable resultTable;

    public TabView() {
        rootPanel = new JPanel(new BorderLayout());
        searchField = new JTextField(24);
        regexCheckBox = new JCheckBox("Regex");
        exportButton = new JButton("Export CSV");
        sendToRepeaterButton = new JButton("Send to Repeater");
        statusLabel = new JLabel("Ready");
        tableModel = new EndpointTableModel();
        resultTable = new JTable(tableModel);
        resultTable.setFillsViewportHeight(true);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel topPanel = new JPanel(new BorderLayout());

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.add(new JLabel("Search:"));
        leftPanel.add(searchField);
        leftPanel.add(regexCheckBox);
        leftPanel.add(exportButton);
        leftPanel.add(sendToRepeaterButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.add(new JLabel(LICENSE_TEXT));

        topPanel.add(leftPanel, BorderLayout.WEST);
        topPanel.add(rightPanel, BorderLayout.EAST);

        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setPreferredSize(new Dimension(900, 420));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        rootPanel.add(topPanel, BorderLayout.NORTH);
        rootPanel.add(tableScroll, BorderLayout.CENTER);
        rootPanel.add(bottomPanel, BorderLayout.SOUTH);

        bindRepeaterShortcut();
    }

    public JPanel getRootComponent() {
        return rootPanel;
    }

    public JTextField getSearchField() {
        return searchField;
    }

    public JButton getExportButton() {
        return exportButton;
    }

    public JCheckBox getRegexCheckBox() {
        return regexCheckBox;
    }

    public JButton getSendToRepeaterButton() {
        return sendToRepeaterButton;
    }

    public void setStatus(String message) {
        statusLabel.setText(message == null ? "" : message);
    }

    public void setRecords(List<EndpointRecord> records) {
        tableModel.setRecords(records);
        setStatus("Records: " + tableModel.getRowCount());
    }

    public String getSearchKeyword() {
        return searchField.getText() == null ? "" : searchField.getText().trim();
    }

    public boolean isRegexSearchEnabled() {
        return regexCheckBox.isSelected();
    }

    public EndpointRecord getSelectedRecord() {
        int selectedViewRow = resultTable.getSelectedRow();
        if (selectedViewRow < 0) {
            return null;
        }
        int selectedModelRow = resultTable.convertRowIndexToModel(selectedViewRow);
        return tableModel.getRecord(selectedModelRow);
    }

    public List<EndpointRecord> getSelectedRecords() {
        int[] selectedViewRows = resultTable.getSelectedRows();
        List<EndpointRecord> selectedRecords = new ArrayList<>();
        if (selectedViewRows == null || selectedViewRows.length == 0) {
            return selectedRecords;
        }
        for (int selectedViewRow : selectedViewRows) {
            int selectedModelRow = resultTable.convertRowIndexToModel(selectedViewRow);
            EndpointRecord record = tableModel.getRecord(selectedModelRow);
            if (record != null) {
                selectedRecords.add(record);
            }
        }
        return selectedRecords;
    }

    private void bindRepeaterShortcut() {
        KeyStroke shortcut = KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK);
        String actionKey = "send-to-repeater";

        AbstractAction action = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                sendToRepeaterButton.doClick();
            }
        };

        rootPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shortcut, actionKey);
        rootPanel.getActionMap().put(actionKey, action);
    }
}
