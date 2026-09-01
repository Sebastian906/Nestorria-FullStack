"""Custom exceptions for ai-service.

RequestException is prepared for future HTTP client calls.
Currently unused but establishes the contract.
"""

from typing import Any

class RequestException(Exception):
    """Raised when an external HTTP request fails.

    Attributes:
        status_code: HTTP status code from the external service.
        detail: Human-readable error message.
        response_body: Raw response body, if available.
    """

    def __init__(
        self,
        message: str = "External request failed",
        status_code: int = 502,
        response_body: Any = None,
    ):
        self.status_code = status_code
        self.detail = message
        self.response_body = response_body
        super().__init__(self.detail)