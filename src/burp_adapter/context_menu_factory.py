try:
    from burp import IContextMenuFactory
    from burp import IContextMenuInvocation
    from java.awt.event import ActionListener
    from java.util import ArrayList
    from javax.swing import JMenuItem
except ImportError:
    IContextMenuFactory = object
    IContextMenuInvocation = object
    ActionListener = object
    ArrayList = None
    JMenuItem = None


class EndpointContextMenuFactory(IContextMenuFactory):
    MENU_LABEL = "Send to Endpoint Collector"

    def __init__(self, scan_controller, logger=None):
        self._scan_controller = scan_controller
        self._logger = logger

    def createMenuItems(self, invocation):
        if not self._is_proxy_history_context(invocation):
            return None
        if JMenuItem is None:
            return None

        menu_item = JMenuItem(self.MENU_LABEL)
        menu_item.addActionListener(_ContextMenuAction(self, invocation))

        if ArrayList is None:
            return [menu_item]
        menu_items = ArrayList()
        menu_items.add(menu_item)
        return menu_items

    def on_send_to_extension(self, invocation):
        selected_messages = self._get_selected_messages(invocation)
        if len(selected_messages) == 0:
            self._scan_controller.notify_status("No selected messages.")
            return
        self._scan_controller.scan_selected_messages(selected_messages)

    def _is_proxy_history_context(self, invocation):
        if invocation is None:
            return False

        proxy_history_context = getattr(
            IContextMenuInvocation, "CONTEXT_PROXY_HISTORY", None
        )
        if proxy_history_context is None:
            return False

        try:
            return invocation.getInvocationContext() == proxy_history_context
        except Exception as exc:
            self._log_error("context inspection failed: %s" % exc)
            return False

    def _get_selected_messages(self, invocation):
        if invocation is None:
            return []
        try:
            selected = invocation.getSelectedMessages()
        except Exception as exc:
            self._log_error("selected messages read failed: %s" % exc)
            return []

        if selected is None:
            return []
        try:
            return list(selected)
        except Exception:
            return [selected]

    def _log_error(self, message):
        if self._logger is None:
            return
        self._logger.error(message)


class _ContextMenuAction(ActionListener):
    def __init__(self, menu_factory, invocation):
        self._menu_factory = menu_factory
        self._invocation = invocation

    def actionPerformed(self, event):
        self._menu_factory.on_send_to_extension(self._invocation)
