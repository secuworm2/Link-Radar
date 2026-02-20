package com.secuworm.endpointcollector.burpadapter;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import com.secuworm.endpointcollector.infra.ExtensionLogger;
import com.secuworm.endpointcollector.presentation.ScanController;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EndpointContextMenuItemsProvider implements ContextMenuItemsProvider {
    private final ScanController scanController;
    private final ExtensionLogger logger;

    public EndpointContextMenuItemsProvider(ScanController scanController, ExtensionLogger logger) {
        this.scanController = scanController;
        this.logger = logger;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (event == null) {
            return null;
        }

        JMenuItem item = new JMenuItem("Send to Link Radar");
        item.addActionListener(actionEvent -> {
            try {
                onSendToExtension(event.selectedRequestResponses());
            } catch (Exception ex) {
                logError("context menu action failed: " + ex.getMessage());
            }
        });
        return Collections.singletonList(item);
    }

    private void onSendToExtension(List<HttpRequestResponse> selectedMessages) {
        List<HttpRequestResponse> items = selectedMessages == null
            ? new ArrayList<>()
            : new ArrayList<>(selectedMessages);
        if (items.isEmpty()) {
            scanController.notifyStatus("No selected messages.");
            return;
        }
        scanController.scanSelectedMessages(items);
    }

    private void logError(String message) {
        if (logger != null) {
            logger.error(message);
        }
    }
}
