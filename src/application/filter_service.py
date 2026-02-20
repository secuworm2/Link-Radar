class FilterService(object):
    def filter(self, records, keyword):
        items = list(records or [])
        normalized_keyword = self._normalize(keyword)
        if normalized_keyword == "":
            return items

        filtered = []
        for record in items:
            if self._matches(record, normalized_keyword):
                filtered.append(record)
        return filtered

    def _matches(self, record, keyword):
        endpoint_url = self._read_value(record, "endpoint_url")
        host = self._read_value(record, "host")
        source_url = self._read_value(record, "source_url")

        haystack = "%s %s %s" % (endpoint_url, host, source_url)
        return keyword in self._normalize(haystack)

    def _read_value(self, record, key):
        if record is None:
            return ""
        if isinstance(record, dict):
            value = record.get(key, "")
        else:
            value = getattr(record, key, "")
        if value is None:
            return ""
        return str(value)

    def _normalize(self, value):
        if value is None:
            return ""
        return str(value).strip().lower()
