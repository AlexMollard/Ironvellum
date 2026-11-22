"""Bound v2 collection pages by the encoded result size, including snapshot metadata."""
from copy import deepcopy
from hashlib import sha256

from .contract import MAX_RESULT, encode, require


def _token(scope, offset):
    digest = sha256(encode([*scope, offset])).hexdigest()
    return f"{offset}:{digest}"


# The SDK normally sends both text and structured content. A single large row may fit only once.
STRUCTURED_PAGE_TEXT = "Read this page in structuredContent."


def _wire_size(request_id, payload, text):
    result = dict(content=[dict(type="text", text=text)], structuredContent=payload, isError=False, resultType="complete")
    return len(encode(dict(jsonrpc="2.0", id=request_id, result=result)))


def bounded_page(file_hash, tool, args, metadata, rows, project, request_id):
    fields = sorted(args.get("fields", ()))
    scope = [file_hash, tool, args.get("id"), args.get("kind"), args.get("routineId"), args["view"], fields]
    start = int(args.get("offset", 0))
    total = len(rows)
    if "cursor" in args:
        cursor = args["cursor"]
        start = int(cursor.split(":", 1)[0])
        require(0 < start < total and _token(scope, start) == cursor, "invalid_cursor")
    require(start <= total, "invalid_offset")

    result = dict(view=args["view"], items=[], total=total, nextCursor=None)
    empty = dict(snapshot=metadata, **result)
    full_size = _wire_size(request_id, empty, encode(empty).decode())
    structured_size = _wire_size(request_id, empty, STRUCTURED_PAGE_TEXT)
    for index in range(start, min(start + int(args.get("limit", 20)), total)):
        row = project(rows[index])
        if fields:
            row = {key: row[key] for key in fields}
        end = index + 1
        # Count each compact item and comma once. Replacing JSON null by a quoted cursor costs
        # len(token) - 2 bytes; the cursor contains only ASCII digits, a colon and hex characters.
        raw = encode(row)
        comma = int(index != start)
        added = len(raw) + comma
        # The text copy escapes quotes and backslashes in the JSON representation of this item.
        text_added = len(encode(raw.decode())) - 2 + comma
        cursor_bytes = len(str(end)) + 1 + 64 - 2 if end < total else 0
        full_size += added + text_added
        if full_size + 2 * cursor_bytes + (2 if end < total else 0) > MAX_RESULT:
            if index == start:
                require(structured_size + added + cursor_bytes <= MAX_RESULT, "result_limit")
                result["items"].append(deepcopy(row))
            break
        result["items"].append(deepcopy(row))
    end = start + len(result["items"])
    if end < total:
        result["nextCursor"] = _token(scope, end)
    return result
