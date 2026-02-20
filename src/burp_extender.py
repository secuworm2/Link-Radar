from burp import IBurpExtender
from burp import ITab

from application.export_service import ExportService
from application.filter_service import FilterService
from application.scan_service import ScanService
from burp_adapter.callbacks_gateway import CallbacksGateway
from burp_adapter.context_menu_factory import EndpointContextMenuFactory
from burp_adapter.history_provider import HistoryProvider
from infra.logger import ExtensionLogger
from presentation.scan_controller import ScanController
from presentation.tab_view import TabView


class BurpExtender(IBurpExtender, ITab):
    TAB_CAPTION = "Endpoint Collector"

    def registerExtenderCallbacks(self, callbacks):
        self._callbacks = callbacks
        self._helpers = callbacks.getHelpers()
        self._logger = ExtensionLogger(self._callbacks, "[Endpoint Collector]")
        self._tab_view = TabView()
        self._callbacks_gateway = CallbacksGateway(self._callbacks, self._helpers)
        self._history_provider = HistoryProvider(self._callbacks_gateway)
        self._filter_service = FilterService()
        self._export_service = ExportService()
        self._scan_service = ScanService(
            callbacks_gateway=self._callbacks_gateway,
            logger=self._logger,
        )
        self._scan_controller = ScanController(
            tab_view=self._tab_view,
            scan_service=self._scan_service,
            history_provider=self._history_provider,
            filter_service=self._filter_service,
            export_service=self._export_service,
            logger=self._logger,
        )
        self._context_menu_factory = EndpointContextMenuFactory(
            scan_controller=self._scan_controller,
            logger=self._logger,
        )

        self._callbacks.setExtensionName(self.TAB_CAPTION)
        self._ui_component = self._tab_view.get_root_component()

        self._callbacks.addSuiteTab(self)
        self._callbacks.registerContextMenuFactory(self._context_menu_factory)
        self._logger.info("Extension loaded.")

    def getTabCaption(self):
        return self.TAB_CAPTION

    def getUiComponent(self):
        return self._ui_component
