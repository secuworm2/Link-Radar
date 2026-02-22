package com.secuworm.endpointcollector.burpadapter;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.InvocationType;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.secuworm.endpointcollector.infra.ExtensionLogger;
import com.secuworm.endpointcollector.presentation.ScanController;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
        if (!event.isFrom(
            InvocationType.PROXY_HISTORY,
            InvocationType.MESSAGE_EDITOR_REQUEST,
            InvocationType.MESSAGE_EDITOR_RESPONSE,
            InvocationType.MESSAGE_VIEWER_REQUEST,
            InvocationType.MESSAGE_VIEWER_RESPONSE
        )) {
            return null;
        }

        JMenuItem item = new JMenuItem("Send to Link Radar");
        item.addActionListener(actionEvent -> {
            try {
                onSendToExtension(readTargets(event));
            } catch (Exception ex) {
                logError("context menu action failed: " + ex.getMessage());
            }
        });
        return Collections.singletonList(item);
    }

    private List<HttpRequestResponse> readTargets(ContextMenuEvent event) {
        List<HttpRequestResponse> targets = new ArrayList<>();
        List<HttpRequestResponse> selected = event.selectedRequestResponses();
        if (selected != null && !selected.isEmpty()) {
            targets.addAll(selected);
            return targets;
        }

        Optional<MessageEditorHttpRequestResponse> editor = event.messageEditorRequestResponse();
        if (!editor.isPresent()) {
            return targets;
        }

        HttpRequestResponse requestResponse = editor.get().requestResponse();
        if (requestResponse != null) {
            targets.add(requestResponse);
        }
        return targets;
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
