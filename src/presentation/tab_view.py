from java.awt import BorderLayout
from java.awt import Dimension
from java.awt import FlowLayout
from javax.swing import JButton
from javax.swing import JLabel
from javax.swing import JPanel
from javax.swing import JScrollPane
from javax.swing import JTable
from javax.swing import JTextField

from presentation.table_model import EndpointTableModel


class TabView(object):
    def __init__(self):
        self.root_panel = JPanel(BorderLayout())

        self.scan_button = JButton("Scan")
        self.stop_button = JButton("Stop")
        self.search_label = JLabel("Search:")
        self.search_field = JTextField(24)
        self.export_button = JButton("Export CSV")
        self.status_label = JLabel("Ready")

        self.table_model = EndpointTableModel()
        self.result_table = JTable(self.table_model)
        self.result_table.setFillsViewportHeight(True)
        self.result_table.setAutoCreateRowSorter(True)

        self._build_layout()

    def _build_layout(self):
        top_panel = JPanel(FlowLayout(FlowLayout.LEFT))
        top_panel.add(self.scan_button)
        top_panel.add(self.stop_button)
        top_panel.add(self.search_label)
        top_panel.add(self.search_field)
        top_panel.add(self.export_button)

        table_scroll = JScrollPane(self.result_table)
        table_scroll.setPreferredSize(Dimension(900, 420))

        bottom_panel = JPanel(BorderLayout())
        bottom_panel.add(self.status_label, BorderLayout.WEST)

        self.root_panel.add(top_panel, BorderLayout.NORTH)
        self.root_panel.add(table_scroll, BorderLayout.CENTER)
        self.root_panel.add(bottom_panel, BorderLayout.SOUTH)

    def get_root_component(self):
        return self.root_panel

    def set_status(self, message):
        text = message if message else ""
        self.status_label.setText(text)

    def set_records(self, records):
        self.table_model.set_records(records)
        self.set_status("Records: %d" % self.table_model.getRowCount())

    def clear_records(self):
        self.table_model.clear()
        self.set_status("Records: 0")

    def get_search_keyword(self):
        return self.search_field.getText().strip()

    def get_components(self):
        return {
            "scan_button": self.scan_button,
            "stop_button": self.stop_button,
            "search_field": self.search_field,
            "export_button": self.export_button,
            "status_label": self.status_label,
            "result_table": self.result_table,
            "table_model": self.table_model,
        }
