import csv
import sys


class ExportService(object):
    HEADER = ["Endpoint", "Host", "SourceURL", "Count"]
    IS_PY2 = sys.version_info[0] == 2

    def export_csv(self, file_path, records):
        if file_path is None or str(file_path).strip() == "":
            return False, "File path is empty."

        try:
            rows = self._to_rows(records)
            if self.IS_PY2:
                file_handle = open(file_path, "wb")
            else:
                file_handle = open(file_path, "w", newline="")
            try:
                writer = csv.writer(file_handle)
                writer.writerow(self.HEADER)
                for row in rows:
                    writer.writerow(row)
            finally:
                file_handle.close()
            return True, ""
        except Exception as exc:
            return False, str(exc)

    def _to_rows(self, records):
        rows = []
        for record in records or []:
            endpoint_url = self._read_value(record, "endpoint_url")
            host = self._read_value(record, "host")
            source_url = self._read_value(record, "source_url")
            count = self._read_value(record, "count")
            rows.append([endpoint_url, host, source_url, count])
        return rows

    def _read_value(self, record, key):
        if record is None:
            return ""
        if isinstance(record, dict):
            value = record.get(key, "")
        else:
            value = getattr(record, key, "")
        if value is None:
            return ""
        return value
