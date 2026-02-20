class HistoryProvider(object):
    def __init__(self, callbacks_gateway):
        self._callbacks_gateway = callbacks_gateway

    def get_history_items(self, scope_type, selected_items=None):
        if scope_type == "selected":
            items = self._to_list(selected_items)
        else:
            items = self._callbacks_gateway.get_proxy_history()
        return self._filter_items_with_response(items)

    def get_decoded_history_items(self, scope_type, selected_items=None):
        items = self.get_history_items(scope_type, selected_items)
        decoded_items = []
        for item in items:
            response = item.getResponse()
            decoded_items.append(
                {
                    "item": item,
                    "source_url": self._callbacks_gateway.get_request_url(item),
                    "content_type": self._callbacks_gateway.get_response_content_type(
                        response
                    ),
                    "response_text": self._callbacks_gateway.decode_response_body(
                        response
                    ),
                }
            )
        return decoded_items

    def _filter_items_with_response(self, items):
        filtered = []
        for item in items:
            if item is None:
                continue
            try:
                response = item.getResponse()
            except Exception:
                continue
            if response is None:
                continue
            try:
                response_size = len(response)
            except Exception:
                response_size = 1
            if response_size <= 0:
                continue
            filtered.append(item)
        return filtered

    def _to_list(self, value):
        if value is None:
            return []
        try:
            return list(value)
        except Exception:
            return [value]
