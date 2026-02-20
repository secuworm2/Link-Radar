import time

try:
    from urlparse import urlparse
except ImportError:
    from urllib.parse import urlparse

from domain.endpoint_extractor import EndpointExtractor
from domain.endpoint_normalizer import EndpointNormalizer
from domain.endpoint_repository import EndpointRepository
from domain.models import ScanResult
from infra.config import MAX_RESPONSE_BYTES
from infra.config import SCAN_BATCH_SIZE


class ScanService(object):
    def __init__(
        self,
        extractor=None,
        normalizer=None,
        repository=None,
        callbacks_gateway=None,
        batch_size=SCAN_BATCH_SIZE,
        max_response_bytes=MAX_RESPONSE_BYTES,
        logger=None,
    ):
        self._extractor = extractor if extractor is not None else EndpointExtractor()
        self._normalizer = normalizer if normalizer is not None else EndpointNormalizer()
        self._repository = repository if repository is not None else EndpointRepository()
        self._callbacks_gateway = callbacks_gateway
        self._batch_size = batch_size
        self._max_response_bytes = max_response_bytes
        self._logger = logger

    def scan(self, history_items, progress_callback=None, should_stop=None):
        started_at = int(time.time() * 1000)
        items = self._as_list(history_items)
        self._repository.clear()
        total_items = len(items)
        processed_items = 0
        total_candidates = 0
        error_count = 0

        for batch in self._iter_batches(items):
            if self._should_stop_requested(should_stop):
                break
            for item in batch:
                if self._should_stop_requested(should_stop):
                    break
                try:
                    payload = self._extract_payload(item)
                    if payload is None:
                        continue

                    response_text = payload.get("response_text") or ""
                    if not self._is_within_size_limit(response_text):
                        continue

                    content_type = payload.get("content_type") or ""
                    source_url = payload.get("source_url") or ""
                    candidates = self._extractor.extract(
                        response_text=response_text,
                        content_type=content_type,
                        source_url=source_url,
                    )
                    total_candidates += len(candidates)

                    for candidate in candidates:
                        endpoint_url = self._normalizer.normalize(candidate, source_url)
                        if endpoint_url is None:
                            continue
                        host = self._extract_host(endpoint_url)
                        self._repository.upsert(
                            endpoint_url=endpoint_url,
                            host=host,
                            source_url=source_url,
                        )
                except Exception as exc:
                    error_count += 1
                    self._log_error("scan item failed: %s" % exc)
                finally:
                    processed_items += 1

            self._notify_progress(
                progress_callback=progress_callback,
                total_items=total_items,
                processed_items=processed_items,
                error_count=error_count,
                unique_endpoints=self._repository.size(),
            )

        duration_ms = int(time.time() * 1000) - started_at
        return ScanResult(
            total_items=total_items,
            processed_items=processed_items,
            total_candidates=total_candidates,
            unique_endpoints=self._repository.size(),
            error_count=error_count,
            duration_ms=duration_ms,
        )

    def get_records(self):
        return list(self._repository.get_all())

    def _extract_payload(self, item):
        if item is None:
            return None
        if isinstance(item, dict):
            return item
        if self._callbacks_gateway is None:
            raise ValueError("callbacks_gateway is required for raw history items")

        response = item.getResponse()
        if response is None:
            return None
        return {
            "item": item,
            "source_url": self._callbacks_gateway.get_request_url(item),
            "content_type": self._callbacks_gateway.get_response_content_type(response),
            "response_text": self._callbacks_gateway.decode_response_body(response),
        }

    def _is_within_size_limit(self, response_text):
        try:
            response_size = len((response_text or "").encode("utf-8"))
        except Exception:
            response_size = len(response_text or "")
        return response_size <= self._max_response_bytes

    def _extract_host(self, endpoint_url):
        parsed = urlparse(endpoint_url)
        return parsed.netloc or ""

    def _iter_batches(self, items):
        batch_size = self._batch_size if self._batch_size > 0 else len(items) or 1
        index = 0
        while index < len(items):
            yield items[index : index + batch_size]
            index += batch_size

    def _notify_progress(
        self,
        progress_callback,
        total_items,
        processed_items,
        error_count,
        unique_endpoints,
    ):
        if progress_callback is None:
            return
        try:
            progress_callback(
                {
                    "total_items": total_items,
                    "processed_items": processed_items,
                    "error_count": error_count,
                    "unique_endpoints": unique_endpoints,
                }
            )
        except Exception as exc:
            self._log_error("progress callback failed: %s" % exc)

    def _as_list(self, value):
        if value is None:
            return []
        try:
            return list(value)
        except Exception:
            return [value]

    def _should_stop_requested(self, should_stop):
        if should_stop is None:
            return False
        try:
            return bool(should_stop())
        except Exception as exc:
            self._log_error("stop callback failed: %s" % exc)
            return False

    def _log_error(self, message):
        if self._logger is None:
            return
        self._logger.error(message)
