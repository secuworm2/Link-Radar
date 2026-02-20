package com.secuworm.endpointcollector.burpadapter;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import com.secuworm.endpointcollector.infra.ExtensionLogger;
import com.secuworm.endpointcollector.presentation.ScanController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EndpointHotKeyHandler implements HotKeyHandler {
    private final ScanController scanController;
    private final ExtensionLogger logger;

    public EndpointHotKeyHandler(ScanController scanController, ExtensionLogger logger) {
        this.scanController = scanController;
        this.logger = logger;
    }

    @Override
    public void handle(HotKeyEvent event) {
        try {
            List<HttpRequestResponse> selectedMessages = event.selectedRequestResponses();
            List<HttpRequestResponse> targets = selectedMessages == null
                ? new ArrayList<>()
                : new ArrayList<>(selectedMessages);
            if (targets.isEmpty()) {
                Optional<HttpRequestResponse> fromEditor = event.messageEditorRequestResponse()
                    .map(messageEditor -> messageEditor.requestResponse());
                if (fromEditor.isPresent()) {
                    targets.add(fromEditor.get());
                }
            }

            if (targets.isEmpty()) {
                scanController.notifyStatus("No selected messages.");
                return;
            }
            scanController.scanSelectedMessages(targets);
        } catch (Exception ex) {
            scanController.notifyStatus("Hotkey action failed.");
            logError("hotkey action failed: " + ex.getMessage());
        }
    }

    private void logError(String message) {
        if (logger == null) {
            return;
        }
        logger.error(message);
    }
}
