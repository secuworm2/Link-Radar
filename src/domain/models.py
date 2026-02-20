import time


class EndpointCandidate(object):
    def __init__(self, raw_value, source_url, content_type, match_type):
        self.raw_value = raw_value
        self.source_url = source_url
        self.content_type = content_type
        self.match_type = match_type


class EndpointRecord(object):
    def __init__(
        self,
        endpoint_url,
        host,
        source_url,
        count=1,
        first_seen_at=None,
        last_seen_at=None,
    ):
        now_ms = int(time.time() * 1000)
        self.endpoint_url = endpoint_url
        self.host = host
        self.source_url = source_url
        self.count = count
        self.first_seen_at = first_seen_at if first_seen_at is not None else now_ms
        self.last_seen_at = last_seen_at if last_seen_at is not None else now_ms


class ScanResult(object):
    def __init__(
        self,
        total_items=0,
        processed_items=0,
        total_candidates=0,
        unique_endpoints=0,
        error_count=0,
        duration_ms=0,
    ):
        self.total_items = total_items
        self.processed_items = processed_items
        self.total_candidates = total_candidates
        self.unique_endpoints = unique_endpoints
        self.error_count = error_count
        self.duration_ms = duration_ms
