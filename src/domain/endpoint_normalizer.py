import re

try:
    from urlparse import urlparse
except ImportError:
    from urllib.parse import urlparse

try:
    STRING_TYPES = (basestring,)
except NameError:
    STRING_TYPES = (str,)


class EndpointNormalizer(object):
    SUPPORTED_SCHEMES = set(["http", "https"])
    INVALID_PREFIXES = ("javascript:", "mailto:", "data:")
    WHITESPACE_PATTERN = re.compile(r"\s")

    def normalize(self, candidate, base_url):
        raw_value = self._get_raw_value(candidate)
        if raw_value is None:
            return None

        cleaned_value = raw_value.strip().strip("'\"")
        if cleaned_value == "":
            return None
        if self.WHITESPACE_PATTERN.search(cleaned_value):
            return None

        lower_value = cleaned_value.lower()
        if lower_value.startswith(self.INVALID_PREFIXES):
            return None

        parsed = urlparse(cleaned_value)
        if parsed.scheme:
            return self._normalize_absolute(cleaned_value, parsed)
        return self._normalize_relative(cleaned_value, base_url)

    def _normalize_absolute(self, cleaned_value, parsed):
        scheme = parsed.scheme.lower()
        if scheme not in self.SUPPORTED_SCHEMES:
            return None
        if parsed.netloc == "":
            return None
        return cleaned_value

    def _normalize_relative(self, cleaned_value, base_url):
        if cleaned_value.startswith("//"):
            return None

        parsed_base = urlparse(base_url or "")
        scheme = parsed_base.scheme.lower()
        if scheme not in self.SUPPORTED_SCHEMES:
            return None
        if parsed_base.netloc == "":
            return None

        normalized_path = cleaned_value
        if not normalized_path.startswith("/"):
            normalized_path = "/" + normalized_path

        return "%s://%s%s" % (scheme, parsed_base.netloc, normalized_path)

    def _get_raw_value(self, candidate):
        if candidate is None:
            return None
        if isinstance(candidate, STRING_TYPES):
            return candidate
        if hasattr(candidate, "raw_value"):
            return candidate.raw_value
        return None
