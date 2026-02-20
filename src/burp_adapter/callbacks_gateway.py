class CallbacksGateway(object):
    MIME_TYPE_MAP = {
        "HTML": "text/html",
        "JSON": "application/json",
        "script": "application/javascript",
        "TEXT": "text/plain",
        "unknown": "text/plain",
    }

    def __init__(self, callbacks, helpers=None):
        self._callbacks = callbacks
        self._helpers = helpers if helpers is not None else callbacks.getHelpers()

    def get_proxy_history(self):
        if self._callbacks is None:
            return []
        items = self._callbacks.getProxyHistory()
        if items is None:
            return []
        return list(items)

    def get_request_url(self, message_info):
        if message_info is None or self._helpers is None:
            return ""
        try:
            request_info = self._helpers.analyzeRequest(message_info)
            url = request_info.getUrl()
            if url is None:
                return ""
            return str(url)
        except Exception:
            return ""

    def get_response_content_type(self, response_bytes):
        if response_bytes is None or self._helpers is None:
            return ""
        try:
            response_info = self._helpers.analyzeResponse(response_bytes)
        except Exception:
            return ""

        headers = response_info.getHeaders()
        for header in headers:
            header_text = str(header)
            lower_header = header_text.lower()
            if not lower_header.startswith("content-type:"):
                continue
            parts = header_text.split(":", 1)
            if len(parts) != 2:
                continue
            value = parts[1].strip().split(";", 1)[0].strip().lower()
            if value != "":
                return value

        stated = response_info.getStatedMimeType()
        if stated in self.MIME_TYPE_MAP:
            return self.MIME_TYPE_MAP[stated]

        inferred = response_info.getInferredMimeType()
        if inferred in self.MIME_TYPE_MAP:
            return self.MIME_TYPE_MAP[inferred]

        return ""

    def get_response_body_bytes(self, response_bytes):
        if response_bytes is None:
            return None
        if self._helpers is None:
            return response_bytes
        try:
            response_info = self._helpers.analyzeResponse(response_bytes)
            body_offset = response_info.getBodyOffset()
            return response_bytes[body_offset:]
        except Exception:
            return response_bytes

    def decode_response_body(self, response_bytes):
        body_bytes = self.get_response_body_bytes(response_bytes)
        if body_bytes is None:
            return ""
        return self.decode_bytes(body_bytes)

    def decode_bytes(self, payload):
        if payload is None:
            return ""

        python_bytes = self._to_python_bytes(payload)
        if python_bytes is not None:
            try:
                return python_bytes.decode("utf-8")
            except Exception:
                try:
                    return python_bytes.decode("latin-1")
                except Exception:
                    pass

        if self._helpers is not None:
            try:
                return self._helpers.bytesToString(payload)
            except Exception:
                pass

        try:
            return str(payload)
        except Exception:
            return ""

    def _to_python_bytes(self, payload):
        if isinstance(payload, bytearray):
            return bytes(payload)
        if isinstance(payload, bytes):
            return payload
        if isinstance(payload, str):
            try:
                return payload.encode("latin-1")
            except Exception:
                return None
        try:
            return bytes(bytearray(payload))
        except Exception:
            return None
