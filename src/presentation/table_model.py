from javax.swing.table import AbstractTableModel


class EndpointTableModel(AbstractTableModel):
    COLUMNS = ["Endpoint", "Host", "Source URL", "Count"]

    def __init__(self):
        AbstractTableModel.__init__(self)
        self._rows = []

    def getColumnCount(self):
        return len(self.COLUMNS)

    def getRowCount(self):
        return len(self._rows)

    def getColumnName(self, column_index):
        return self.COLUMNS[column_index]

    def getValueAt(self, row_index, column_index):
        row = self._rows[row_index]
        if column_index == 0:
            return row["endpoint_url"]
        if column_index == 1:
            return row["host"]
        if column_index == 2:
            return row["source_url"]
        if column_index == 3:
            return row["count"]
        return ""

    def isCellEditable(self, row_index, column_index):
        return False

    def set_records(self, records):
        self._rows = self._to_rows(records)
        self.fireTableDataChanged()

    def clear(self):
        self._rows = []
        self.fireTableDataChanged()

    def get_record(self, row_index):
        if row_index < 0 or row_index >= len(self._rows):
            return None
        return self._rows[row_index]

    def _to_rows(self, records):
        rows = []
        for record in records or []:
            row = self._to_row(record)
            if row is None:
                continue
            rows.append(row)
        return rows

    def _to_row(self, record):
        if record is None:
            return None
        if isinstance(record, dict):
            return {
                "endpoint_url": record.get("endpoint_url", ""),
                "host": record.get("host", ""),
                "source_url": record.get("source_url", ""),
                "count": record.get("count", 0),
            }
        endpoint_url = getattr(record, "endpoint_url", "")
        host = getattr(record, "host", "")
        source_url = getattr(record, "source_url", "")
        count = getattr(record, "count", 0)
        return {
            "endpoint_url": endpoint_url,
            "host": host,
            "source_url": source_url,
            "count": count,
        }
