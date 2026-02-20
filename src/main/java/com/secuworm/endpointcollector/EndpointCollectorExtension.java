package com.secuworm.endpointcollector;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.secuworm.endpointcollector.application.ExportService;
import com.secuworm.endpointcollector.application.FilterService;
import com.secuworm.endpointcollector.application.ScanService;
import com.secuworm.endpointcollector.burpadapter.EndpointContextMenuItemsProvider;
import com.secuworm.endpointcollector.burpadapter.HistoryProvider;
import com.secuworm.endpointcollector.burpadapter.RepeaterSender;
import com.secuworm.endpointcollector.infra.ExtensionLogger;
import com.secuworm.endpointcollector.presentation.ScanController;
import com.secuworm.endpointcollector.presentation.TabView;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class EndpointCollectorExtension implements BurpExtension {
    public static final String EXTENSION_NAME = "Link Radar";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName(EXTENSION_NAME);

        ExtensionLogger logger = new ExtensionLogger(api, "[Link Radar]");
        TabView tabView = new TabView();
        HistoryProvider historyProvider = new HistoryProvider(api, logger);
        FilterService filterService = new FilterService();
        ExportService exportService = new ExportService();
        RepeaterSender repeaterSender = new RepeaterSender(api, logger);
        ScanService scanService = new ScanService(logger);
        ScanController scanController = new ScanController(
            tabView,
            scanService,
            historyProvider,
            filterService,
            exportService,
            repeaterSender,
            logger
        );

        api.userInterface().registerSuiteTab(EXTENSION_NAME, tabView.getRootComponent());
        api.userInterface().registerContextMenuItemsProvider(
            new EndpointContextMenuItemsProvider(scanController, logger)
        );
        registerHotKeyIfSupported(api, scanController, logger);
        api.userInterface().applyThemeToComponent(tabView.getRootComponent());
        logger.info("Extension loaded.");
    }

    private void registerHotKeyIfSupported(MontoyaApi api, ScanController scanController, ExtensionLogger logger) {
        try {
            Object userInterface = api.userInterface();
            Method registerWithString = null;
            Method registerWithHotKey = null;

            for (Method method : userInterface.getClass().getMethods()) {
                if (!"registerHotKeyHandler".equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 3) {
                    continue;
                }
                if (String.class.equals(parameterTypes[1])) {
                    registerWithString = method;
                    break;
                }
                if ("burp.api.montoya.ui.hotkey.HotKey".equals(parameterTypes[1].getName())) {
                    registerWithHotKey = method;
                }
            }

            Method registerMethod = registerWithString != null ? registerWithString : registerWithHotKey;
            if (registerMethod == null) {
                logger.info("Hotkey registration skipped: unsupported Montoya runtime.");
                return;
            }

            Class<?>[] types = registerMethod.getParameterTypes();
            Object context = resolveContext(types[0]);
            if (context == null) {
                logger.info("Hotkey registration skipped: no HTTP_MESSAGE_EDITOR context.");
                return;
            }

            Object handler = createHotKeyHandlerProxy(userInterface, types[2], scanController, logger);
            if (handler == null) {
                logger.info("Hotkey registration skipped: handler proxy unavailable.");
                return;
            }

            if (String.class.equals(types[1])) {
                registerMethod.invoke(userInterface, context, "Ctrl+G", handler);
                logger.info("Hotkey registered: Ctrl+G (HTTP_MESSAGE_EDITOR, string signature).");
                return;
            }

            Object hotKey = createHotKey(types[1]);
            if (hotKey == null) {
                logger.info("Hotkey registration skipped: HotKey factory unavailable.");
                return;
            }
            registerMethod.invoke(userInterface, context, hotKey, handler);
            logger.info("Hotkey registered: Ctrl+G (HTTP_MESSAGE_EDITOR, HotKey signature).");
        } catch (Throwable throwable) {
            logger.info("Hotkey registration skipped: " + throwable.getClass().getSimpleName());
        }
    }

    private Object createHotKeyHandlerProxy(
        Object userInterface,
        Class<?> hotKeyHandlerClass,
        ScanController scanController,
        ExtensionLogger logger
    ) {
        try {
            ClassLoader classLoader = userInterface.getClass().getClassLoader();
            InvocationHandler invocationHandler = (proxy, method, args) -> {
                if (!"handle".equals(method.getName()) || args == null || args.length == 0 || args[0] == null) {
                    return null;
                }
                handleHotKeyEvent(scanController, logger, args[0]);
                return null;
            };
            return Proxy.newProxyInstance(classLoader, new Class<?>[]{hotKeyHandlerClass}, invocationHandler);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private Object resolveContext(Class<?> contextType) {
        try {
            if (!contextType.isEnum()) {
                return null;
            }
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object context = Enum.valueOf((Class<? extends Enum>) contextType, "HTTP_MESSAGE_EDITOR");
            return context;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private Object createHotKey(Class<?> hotKeyClass) {
        try {
            Method factoryMethod = hotKeyClass.getMethod("hotKey", String.class, String.class);
            return factoryMethod.invoke(null, "Send to Link Radar", "Ctrl+G");
        } catch (Throwable throwable) {
            return null;
        }
    }

    private void handleHotKeyEvent(ScanController scanController, ExtensionLogger logger, Object hotKeyEvent) {
        try {
            List<?> selectedMessages = readSelectedMessages(hotKeyEvent);
            List<Object> targets = new ArrayList<>();
            if (selectedMessages != null) {
                targets.addAll(selectedMessages);
            }
            if (targets.isEmpty()) {
                Object editorRequestResponse = readMessageEditorRequestResponse(hotKeyEvent);
                if (editorRequestResponse != null) {
                    targets.add(editorRequestResponse);
                }
            }
            if (targets.isEmpty()) {
                Object directRequestResponse = readDirectRequestResponse(hotKeyEvent);
                if (directRequestResponse != null) {
                    targets.add(directRequestResponse);
                }
            }
            if (targets.isEmpty()) {
                scanController.notifyStatus("No selected messages.");
                logger.info("Hotkey event contained no selectable request/response.");
                return;
            }
            @SuppressWarnings("rawtypes")
            List rawTargets = targets;
            scanController.scanSelectedMessages(rawTargets);
        } catch (Throwable throwable) {
            scanController.notifyStatus("Hotkey action failed.");
            logger.error("hotkey action failed (" + hotKeyEvent.getClass().getName() + "): " + throwable.getClass().getSimpleName() + " - " + throwable.getMessage());
        }
    }

    private List<?> readSelectedMessages(Object hotKeyEvent) {
        String[] methodNames = new String[]{
            "selectedRequestResponses",
            "selectedMessages",
            "requestResponses"
        };
        for (String methodName : methodNames) {
            try {
                Object selected = invokeNoArg(hotKeyEvent, methodName);
                if (selected == null) {
                    continue;
                }
                if (selected instanceof List) {
                    return (List<?>) selected;
                }
                if (selected instanceof Collection) {
                    return new ArrayList<>((Collection<?>) selected);
                }
            } catch (Throwable throwable) {
                continue;
            }
        }
        return null;
    }

    private Object readMessageEditorRequestResponse(Object hotKeyEvent) {
        try {
            Object optional = invokeNoArg(hotKeyEvent, "messageEditorRequestResponse");
            if (optional == null) {
                return null;
            }
            if (optional instanceof Optional) {
                Optional<?> value = (Optional<?>) optional;
                if (!value.isPresent()) {
                    return null;
                }
                Object messageEditor = value.get();
                Object requestResponse = invokeNoArg(messageEditor, "requestResponse");
                if (requestResponse != null) {
                    return requestResponse;
                }
                return null;
            }
            return invokeNoArg(optional, "requestResponse");
        } catch (Throwable throwable) {
            return null;
        }
    }

    private Object readDirectRequestResponse(Object hotKeyEvent) {
        String[] directMethods = new String[]{
            "requestResponse",
            "requestResponseAtCaret"
        };
        for (String methodName : directMethods) {
            try {
                Object value = invokeNoArg(hotKeyEvent, methodName);
                if (value != null) {
                    return value;
                }
            } catch (Throwable throwable) {
                continue;
            }
        }
        return null;
    }

    private Object invokeNoArg(Object target, String methodName) throws Exception {
        if (target == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }
}
