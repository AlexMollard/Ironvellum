"""Expose the six local Ironvellum development operations over MCP."""

import base64
from collections.abc import Callable
from importlib.metadata import version
import json
import logging
from typing import Annotated, Any, Literal

from mcp.server import MCPServer
from mcp.server.context import CallNext, HandlerResult, ServerRequestContext
from mcp.types import CallToolResult, ImageContent, TextContent, ToolAnnotations
from pydantic import Field

from .adb import DevelopmentError, SERIAL_PATTERN
from .operations import DevelopmentTools


_LOG = logging.getLogger(__name__)
_SERIAL = Annotated[str, Field(
    strict=True, min_length=1, max_length=128, pattern=f"^{SERIAL_PATTERN.pattern}$",
    description="Exact ADB serial from list_devices",
)]
_PREVIEW_ACTION = Literal["show", "clear"]
_PREVIEW_SCENARIO = Literal["all", "accepted", "comment", "reaction", "group_goal", "batch", "rest"]
_LOG_LIMIT = Annotated[int, Field(
    strict=True, ge=1, description="Requested line count, capped at 200",
)]
_JSON_RESULT = Annotated[CallToolResult, dict[str, Any]]
_READ_ONLY = ToolAnnotations(read_only_hint=True, open_world_hint=False)
_PREVIEW_WRITE = ToolAnnotations(
    read_only_hint=False, destructive_hint=True, idempotent_hint=False, open_world_hint=False,
)


def _json_result(data: dict, *, is_error: bool = False) -> CallToolResult:
    return CallToolResult(
        content=[TextContent(type="text", text=json.dumps(data, ensure_ascii=True))],
        structured_content=data,
        is_error=is_error,
    )


def _call(operation: Callable[..., dict | bytes], *args: str | int) -> CallToolResult:
    try:
        result = operation(*args)
        if isinstance(result, bytes):
            image = ImageContent(type="image", data=base64.b64encode(result).decode("ascii"), mime_type="image/png")
            return CallToolResult(content=[image])
        return _json_result(result)
    except DevelopmentError as error:
        return _json_result({"error": {
            "code": error.code, "message": str(error), "details": error.details,
        }}, is_error=True)
    except Exception:
        _LOG.error("Ironvellum development operation failed unexpectedly")
        return _json_result({"error": {
            "code": "internal_error", "message": "The Ironvellum development operation failed", "details": {},
        }}, is_error=True)


async def _safe_validation_errors(ctx: ServerRequestContext, call_next: CallNext) -> HandlerResult:
    result = await call_next(ctx)
    # SDK 2.3.0 includes the rejected input value in the generated tool error text.
    # Middleware sees the serialized result, so preserve the negotiated wire envelope.
    if (
        ctx.method == "tools/call" and isinstance(result, dict)
        and result.get("isError") and result.get("structuredContent") is None
    ):
        return {**result, "content": [{
            "type": "text", "text": "Tool unavailable or arguments do not match its advertised schema.",
        }]}
    return result


def create_server(tools: DevelopmentTools) -> MCPServer:
    server = MCPServer(
        "ironvellum-development",
        version=version("ironvellum-development-mcp"),
        log_level="WARNING",
        subscriptions=False,
        middleware=[_safe_validation_errors],
    )

    @server.tool(annotations=_READ_ONLY)
    def list_devices() -> _JSON_RESULT:
        """List ADB devices and their connection states. Use the exact serial for all other tools."""
        return _call(tools.list_devices)

    @server.tool(annotations=_READ_ONLY)
    def get_app_status(serial: _SERIAL) -> _JSON_RESULT:
        """Read package and foreground status, plus a live workout summary if the debug app is already running."""
        return _call(tools.get_app_status, serial)

    @server.tool(annotations=_READ_ONLY)
    def capture_screen(serial: _SERIAL) -> CallToolResult:
        """Capture the device display as PNG with Ironvellum in the foreground. Android system overlays can appear."""
        return _call(tools.capture_screen, serial)

    @server.tool(annotations=_READ_ONLY)
    def inspect_screen(serial: _SERIAL) -> _JSON_RESULT:
        """Read foreground Ironvellum UI nodes using a temporary device file. Password fields are redacted."""
        return _call(tools.inspect_screen, serial)

    @server.tool(annotations=_PREVIEW_WRITE)
    def preview_notifications(
        serial: _SERIAL, action: _PREVIEW_ACTION = "show", scenario: _PREVIEW_SCENARIO = "all",
    ) -> _JSON_RESULT:
        """Show selected sample notifications or clear all debug previews. Clear requires scenario all."""
        return _call(tools.preview_notifications, serial, action, scenario)

    @server.tool(annotations=_READ_ONLY)
    def get_diagnostics(serial: _SERIAL, limit: _LOG_LIMIT = 100) -> _JSON_RESULT:
        """Read a bounded, redacted log tail from the current Android user's Ironvellum process."""
        return _call(tools.get_diagnostics, serial, limit)

    return server
