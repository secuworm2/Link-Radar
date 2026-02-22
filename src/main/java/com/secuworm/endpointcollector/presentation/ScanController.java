package com.secuworm.endpointcollector.presentation;

import burp.api.montoya.ui.swing.SwingUtils;
import com.secuworm.endpointcollector.application.ExportService;
import com.secuworm.endpointcollector.application.FilterService;
import com.secuworm.endpointcollector.application.ScanService;
import com.secuworm.endpointcollector.burpadapter.HistoryItemPayload;
import com.secuworm.endpointcollector.burpadapter.HistoryProvider;
import com.secuworm.endpointcollector.burpadapter.RepeaterSender;
import com.secuworm.endpointcollector.domain.EndpointRecord;
import com.secuworm.endpointcollector.domain.ScanResult;
import com.secuworm.endpointcollector.infra.AppConfig;
import com.secuworm.endpointcollector.infra.ExtensionLogger;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScanController {
    private final TabView tabView;
    private final ScanService scanService;
    private final HistoryProvider historyProvider;
    private final FilterService filterService;
    private final ExportService exportService;
    private final RepeaterSender repeaterSender;
    private final SwingUtils swingUtils;
    private final ExtensionLogger logger;
    private final Object lock = new Object();

    private volatile boolean unloading;
    private boolean isScanning;
    private Thread scanThread;
    private List<EndpointRecord> allRecords = new ArrayList<>();
    private List<EndpointRecord> filteredRecords = new ArrayList<>();

    public ScanController(
        TabView tabView,
        ScanService scanService,
        HistoryProvider historyProvider,
        FilterService filterService,
        ExportService exportService,
        RepeaterSender repeaterSender,
        SwingUtils swingUtils,
        ExtensionLogger logger
    ) {
        this.tabView = tabView;
        this.scanService = scanService;
        this.historyProvider = historyProvider;
        this.filterService = filterService;
        this.exportService = exportService;
        this.repeaterSender = repeaterSender;
        this.swingUtils = swingUtils;
        this.logger = logger;

        bindActions();
        setControlsScanning(false);
    }

    public boolean startScan(String scopeType, List<?> selectedItems) {
        synchronized (lock) {
            if (unloading) {
                return false;
            }
            if (isScanning) {
                notifyStatus("Scan already running.");
                return false;
            }
            isScanning = true;
        }

        setControlsScanning(true);
        notifyStatus("Scanning started.");

        Thread worker = new Thread(() -> runScan(scopeType, selectedItems), "endpoint-scan-worker");
        worker.setDaemon(true);
        synchronized (lock) {
            scanThread = worker;
        }
        worker.start();
        return true;
    }

    public boolean scanSelectedMessages(List<?> selectedItems) {
        List<?> items = selectedItems == null ? new ArrayList<>() : new ArrayList<>(selectedItems);
        if (items.isEmpty()) {
            notifyStatus("No selected messages.");
            return false;
        }
        return startScan("selected", items);
    }

    public void notifyStatus(String message) {
        if (unloading) {
            return;
        }
        runOnUi(() -> tabView.setStatus(message));
    }

    public void onExtensionUnloaded() {
        Thread worker;
        synchronized (lock) {
            unloading = true;
            worker = scanThread;
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void runScan(String scopeType, List<?> selectedItems) {
        ScanResult scanResult = null;
        boolean failed = false;

        try {
            List<HistoryItemPayload> historyItems = historyProvider.getDecodedHistoryItems(scopeType, selectedItems);
            scanResult = scanService.scan(
                historyItems,
                (totalItems, processedItems, errorCount, uniqueEndpoints) ->
                    notifyStatus("Scanning: " + processedItems + "/" + totalItems + ", errors=" + errorCount + ", unique=" + uniqueEndpoints),
                this::isUnloadRequested
            );

            List<EndpointRecord> records = scanService.getRecords();
            synchronized (lock) {
                allRecords = new ArrayList<>(records);
            }
            runOnUi(this::applyFilterAndRender);
        } catch (Exception ex) {
            failed = true;
            logError("scan failed: " + ex.getMessage());
        } finally {
            synchronized (lock) {
                isScanning = false;
                scanThread = null;
            }
        }

        if (unloading) {
            return;
        }
        setControlsScanning(false);

        if (failed) {
            notifyStatus("Scan failed.");
            return;
        }
        if (scanResult == null) {
            notifyStatus("Scan stopped.");
            return;
        }
        notifyStatus(
            "Completed: " + scanResult.getProcessedItems() + "/" + scanResult.getTotalItems()
                + ", errors=" + scanResult.getErrorCount()
                + ", unique=" + scanResult.getUniqueEndpoints()
        );
    }

    private boolean isUnloadRequested() {
        if (unloading) {
            return true;
        }
        return Thread.currentThread().isInterrupted();
    }

    private void bindActions() {
        tabView.getExportButton().addActionListener(event -> onExportClicked());
        tabView.getSendToRepeaterButton().addActionListener(event -> onSendToRepeaterClicked());
        tabView.getSearchField().addActionListener(event -> applyFilterAndRender());
        tabView.getSearchField().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilterAndRender();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilterAndRender();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilterAndRender();
            }
        });
    }

    private void onExportClicked() {
        List<EndpointRecord> records;
        synchronized (lock) {
            records = new ArrayList<>(filteredRecords);
        }
        if (records.isEmpty()) {
            notifyStatus("No records to export.");
            return;
        }

        String filePath = chooseExportPath();
        if (filePath == null) {
            notifyStatus("Export canceled.");
            return;
        }

        ExportService.ExportResult result = exportService.exportCsv(filePath, records);
        if (!result.isSuccess()) {
            notifyStatus("Export failed: " + (result.getErrorMessage() == null ? "unknown error" : result.getErrorMessage()));
            logError("export failed: " + result.getErrorMessage());
            return;
        }
        notifyStatus("Exported " + records.size() + " records: " + filePath);
    }

    private void onSendToRepeaterClicked() {
        List<EndpointRecord> selectedRecords = tabView.getSelectedRecords();
        if (selectedRecords.isEmpty()) {
            notifyStatus("No records selected.");
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (EndpointRecord selectedRecord : selectedRecords) {
            RepeaterSender.SendResult sendResult = repeaterSender.sendToRepeater(selectedRecord);
            if (sendResult.isSuccess()) {
                successCount += 1;
            } else {
                failureCount += 1;
                logError("send to repeater failed for " + selectedRecord.getEndpointUrl());
            }
        }

        if (failureCount == 0) {
            notifyStatus("Sent to Repeater: " + successCount + " request(s).");
            return;
        }
        notifyStatus("Sent to Repeater: success=" + successCount + ", failed=" + failureCount);
    }

    private void setControlsScanning(boolean scanning) {
        runOnUi(() -> {
            tabView.getSendToRepeaterButton().setEnabled(!scanning);
        });
    }

    private void applyFilterAndRender() {
        String keyword = tabView.getSearchKeyword();
        List<EndpointRecord> records;
        synchronized (lock) {
            records = new ArrayList<>(allRecords);
        }
        List<EndpointRecord> filtered = filterService.filter(records, keyword);
        synchronized (lock) {
            filteredRecords = new ArrayList<>(filtered);
        }
        tabView.setRecords(filtered);
        setFilterStatus(keyword, filtered.size(), records.size());
    }

    private void setFilterStatus(String keyword, int matchedCount, int totalCount) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            notifyStatus("Records: " + matchedCount);
            return;
        }
        notifyStatus("Filtered: " + matchedCount + "/" + totalCount);
    }

    private String chooseExportPath() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(AppConfig.EXPORT_DEFAULT_FILENAME));
        int result = chooser.showSaveDialog(resolveParentComponent());
        if (result != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File selectedFile = chooser.getSelectedFile();
        if (selectedFile == null) {
            return null;
        }
        return selectedFile.getAbsolutePath();
    }

    private java.awt.Component resolveParentComponent() {
        if (swingUtils != null) {
            try {
                java.awt.Frame frame = swingUtils.suiteFrame();
                if (frame != null) {
                    return frame;
                }
            } catch (Exception ignored) {
            }
        }
        return tabView.getRootComponent();
    }

    private void runOnUi(Runnable runnable) {
        if (unloading) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        SwingUtilities.invokeLater(runnable);
    }

    private void logError(String message) {
        if (logger != null) {
            logger.error(message);
        }
    }
}
