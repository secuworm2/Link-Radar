import re

from domain.models import EndpointCandidate


class EndpointExtractor(object):
    SUPPORTED_CONTENT_TYPES = set(
        [
            "text/html",
            "application/json",
            "application/javascript",
            "text/javascript",
            "text/plain",
        ]
    )
    ABSOLUTE_URL_PATTERN = re.compile(r"https?://[^\s\"'<>]+", re.IGNORECASE)
    RELATIVE_URL_PATTERN = re.compile(r"(?<![A-Za-z0-9_<])/(?!/)[^\s\"'<>]+")
    NOISE_PREFIXES = ("javascript:", "mailto:")
    TRAILING_TRIM_CHARS = ".,;:!?)\\]}"

    def extract(self, response_text, content_type, source_url):
        normalized_content_type = self._normalize_content_type(content_type)
        if normalized_content_type not in self.SUPPORTED_CONTENT_TYPES:
            return []
        if response_text is None:
            return []

        candidates = []
        seen = set()
        self._collect_candidates(
            candidates,
            seen,
            self.ABSOLUTE_URL_PATTERN.findall(response_text),
            "absolute",
            normalized_content_type,
            source_url,
        )
        self._collect_candidates(
            candidates,
            seen,
            self.RELATIVE_URL_PATTERN.findall(response_text),
            "relative",
            normalized_content_type,
            source_url,
        )
        return candidates

    def _collect_candidates(
        self, target, seen, values, match_type, content_type, source_url
    ):
        for value in values:
            cleaned = self._clean_match(value)
            if cleaned is None:
                continue
            lower_cleaned = cleaned.lower()
            if lower_cleaned.startswith(self.NOISE_PREFIXES):
                continue
            if match_type == "relative" and cleaned.startswith("//"):
                continue

            dedupe_key = (cleaned, match_type)
            if dedupe_key in seen:
                continue

            seen.add(dedupe_key)
            target.append(
                EndpointCandidate(
                    raw_value=cleaned,
                    source_url=source_url,
                    content_type=content_type,
                    match_type=match_type,
                )
            )

    def _clean_match(self, value):
        if value is None:
            return None
        cleaned = value.strip().strip("'\"")
        while cleaned and cleaned[-1] in self.TRAILING_TRIM_CHARS:
            cleaned = cleaned[:-1]
        if cleaned == "" or cleaned == "/":
            return None
        return cleaned

    def _normalize_content_type(self, content_type):
        if content_type is None:
            return ""
        return content_type.split(";", 1)[0].strip().lower()
