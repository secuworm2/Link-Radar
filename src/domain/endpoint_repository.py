import time

from domain.models import EndpointRecord


class EndpointRepository(object):
    def __init__(self):
        self.endpoint_map = {}

    def upsert(self, endpoint_url, host, source_url):
        now_ms = int(time.time() * 1000)
        record = self.endpoint_map.get(endpoint_url)
        if record is None:
            self.endpoint_map[endpoint_url] = EndpointRecord(
                endpoint_url=endpoint_url,
                host=host,
                source_url=source_url,
                count=1,
                first_seen_at=now_ms,
                last_seen_at=now_ms,
            )
            return self.endpoint_map[endpoint_url]

        record.count += 1
        record.last_seen_at = now_ms
        return record

    def get(self, endpoint_url):
        return self.endpoint_map.get(endpoint_url)

    def get_all(self):
        return self.endpoint_map.values()

    def size(self):
        return len(self.endpoint_map)

    def clear(self):
        self.endpoint_map = {}
