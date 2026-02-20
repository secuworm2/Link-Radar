import threading

try:
    from java.awt.event import ActionListener
    from java.io import File
    from java.lang import Runnable
    from javax.swing import JFileChooser
    from javax.swing import SwingUtilities
    from javax.swing.event import DocumentListener
except ImportError:
    ActionListener = object
    File = None
    JFileChooser = None
    Runnable = object
    SwingUtilities = None
    DocumentListener = object

from application.export_service import ExportService
from application.filter_service import FilterService
from infra.config import EXPORT_DEFAULT_FILENAME


class ScanController(object):
    def __init__(
        self,
        tab_view,
        scan_service,
        history_provider,
        filter_service=None,
        export_service=None,
        logger=None,
    ):
        self._tab_view = tab_view
        self._scan_service = scan_service
        self._history_provider = history_provider
        self._filter_service = (
            filter_service if filter_service is not None else FilterService()
        )
        self._export_service = (
            export_service if export_service is not None else ExportService()
        )
        self._logger = logger

        self._components = self._tab_view.get_components()
        self._lock = threading.Lock()
        self._is_scanning = False
        self._stop_requested = False
        self._scan_thread = None
        self._all_records = []
        self._filtered_records = []

        self._bind_actions()
        self._set_controls_scanning(False)

    def start_scan(self, scope_type, selected_items=None):
        with self._lock:
            if self._is_scanning:
                self._set_status("Scan already running.")
                return False
            self._is_scanning = True
            self._stop_requested = False

        self._set_controls_scanning(True)
        self._set_status("Scanning started.")

        thread = threading.Thread(
            target=self._run_scan,
            args=(scope_type, selected_items),
            name="endpoint-scan-worker",
        )
        thread.daemon = True
        with self._lock:
            self._scan_thread = thread
        thread.start()
        return True

    def scan_selected_messages(self, selected_items):
        items = self._to_list(selected_items)
        if len(items) == 0:
            self._set_status("No selected messages.")
            return False
        return self.start_scan(scope_type="selected", selected_items=items)

    def notify_status(self, message):
        self._set_status(message)

    def stop_scan(self):
        with self._lock:
            if not self._is_scanning:
                self._set_status("No scan in progress.")
                return False
            self._stop_requested = True

        self._set_status("Stop requested.")
        return True

    def _run_scan(self, scope_type, selected_items):
        scan_result = None
        failed = False
        stopped = False

        try:
            history_items = self._history_provider.get_decoded_history_items(
                scope_type=scope_type,
                selected_items=selected_items,
            )
            scan_result = self._scan_service.scan(
                history_items=history_items,
                progress_callback=self._on_scan_progress,
                should_stop=self._is_stop_requested,
            )
            records = self._scan_service.get_records()
            self._set_all_records(records)
            self._run_on_ui(self._apply_filter_and_render)
        except Exception as exc:
            failed = True
            self._log_error("scan failed: %s" % exc)
        finally:
            with self._lock:
                stopped = self._stop_requested
                self._is_scanning = False
                self._stop_requested = False
                self._scan_thread = None

        self._set_controls_scanning(False)

        if failed:
            self._set_status("Scan failed.")
            return

        if scan_result is None:
            self._set_status("Scan stopped.")
            return

        if stopped:
            self._set_status(
                "Stopped: %d/%d, errors=%d, unique=%d"
                % (
                    scan_result.processed_items,
                    scan_result.total_items,
                    scan_result.error_count,
                    scan_result.unique_endpoints,
                )
            )
            return

        self._set_status(
            "Completed: %d/%d, errors=%d, unique=%d"
            % (
                scan_result.processed_items,
                scan_result.total_items,
                scan_result.error_count,
                scan_result.unique_endpoints,
            )
        )

    def _on_scan_progress(self, progress):
        self._set_status(
            "Scanning: %d/%d, errors=%d, unique=%d"
            % (
                progress.get("processed_items", 0),
                progress.get("total_items", 0),
                progress.get("error_count", 0),
                progress.get("unique_endpoints", 0),
            )
        )

    def _is_stop_requested(self):
        with self._lock:
            return self._stop_requested

    def _bind_actions(self):
        scan_button = self._components.get("scan_button")
        stop_button = self._components.get("stop_button")
        export_button = self._components.get("export_button")
        search_field = self._components.get("search_field")

        if scan_button is not None:
            scan_button.addActionListener(_ButtonAction(self._on_scan_clicked))
        if stop_button is not None:
            stop_button.addActionListener(_ButtonAction(self._on_stop_clicked))
        if export_button is not None:
            export_button.addActionListener(_ButtonAction(self._on_export_clicked))
        if search_field is not None:
            search_field.addActionListener(_ButtonAction(self._on_search_changed))
            document = search_field.getDocument()
            if document is not None:
                document.addDocumentListener(
                    _SearchDocumentListener(self._on_search_changed)
                )

    def _on_scan_clicked(self):
        self.start_scan(scope_type="all", selected_items=None)

    def _on_stop_clicked(self):
        self.stop_scan()

    def _on_search_changed(self):
        self._apply_filter_and_render()

    def _on_export_clicked(self):
        records = self._get_filtered_records()
        if len(records) == 0:
            self._set_status("No records to export.")
            return

        file_path = self._choose_export_path()
        if file_path is None:
            self._set_status("Export canceled.")
            return

        success, error_message = self._export_service.export_csv(file_path, records)
        if not success:
            self._set_status("Export failed: %s" % (error_message or "unknown error"))
            self._log_error("export failed: %s" % error_message)
            return

        self._set_status("Exported %d records: %s" % (len(records), file_path))

    def _set_controls_scanning(self, scanning):
        def update():
            scan_button = self._components.get("scan_button")
            stop_button = self._components.get("stop_button")
            if scan_button is not None:
                scan_button.setEnabled(not scanning)
            if stop_button is not None:
                stop_button.setEnabled(scanning)

        self._run_on_ui(update)

    def _set_status(self, message):
        self._run_on_ui(lambda: self._tab_view.set_status(message))

    def _apply_filter_and_render(self):
        keyword = self._tab_view.get_search_keyword()
        records = self._get_all_records()
        filtered = self._filter_service.filter(records, keyword)
        self._set_filtered_records(filtered)
        self._tab_view.set_records(filtered)
        self._set_filter_status(keyword, len(filtered), len(records))

    def _set_filter_status(self, keyword, matched_count, total_count):
        normalized_keyword = (keyword or "").strip()
        if normalized_keyword == "":
            self._set_status("Records: %d" % matched_count)
            return
        self._set_status("Filtered: %d/%d" % (matched_count, total_count))

    def _run_on_ui(self, fn):
        if SwingUtilities is None:
            fn()
            return
        if SwingUtilities.isEventDispatchThread():
            fn()
            return
        SwingUtilities.invokeLater(_Runnable(fn))

    def _log_error(self, message):
        if self._logger is None:
            return
        self._logger.error(message)

    def _set_all_records(self, records):
        with self._lock:
            self._all_records = list(records or [])

    def _get_all_records(self):
        with self._lock:
            return list(self._all_records)

    def _set_filtered_records(self, records):
        with self._lock:
            self._filtered_records = list(records or [])

    def _get_filtered_records(self):
        with self._lock:
            return list(self._filtered_records)

    def _choose_export_path(self):
        if JFileChooser is None:
            return EXPORT_DEFAULT_FILENAME

        chooser = JFileChooser()
        if File is not None:
            chooser.setSelectedFile(File(EXPORT_DEFAULT_FILENAME))
        result = chooser.showSaveDialog(self._tab_view.get_root_component())
        if result != JFileChooser.APPROVE_OPTION:
            return None

        selected_file = chooser.getSelectedFile()
        if selected_file is None:
            return None
        return str(selected_file.getAbsolutePath())

    def _to_list(self, value):
        if value is None:
            return []
        try:
            return list(value)
        except Exception:
            return [value]


class _ButtonAction(ActionListener):
    def __init__(self, callback):
        self._callback = callback

    def actionPerformed(self, event):
        self._callback()


class _Runnable(Runnable):
    def __init__(self, fn):
        self._fn = fn

    def run(self):
        self._fn()


class _SearchDocumentListener(DocumentListener):
    def __init__(self, callback):
        self._callback = callback

    def insertUpdate(self, event):
        self._callback()

    def removeUpdate(self, event):
        self._callback()

    def changedUpdate(self, event):
        self._callback()
