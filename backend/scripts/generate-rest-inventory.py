#!/usr/bin/env python3
"""Generate a source-backed REST/frontend endpoint inventory.

The collector deliberately uses only Python's standard library.  It is not a
Java or JavaScript compiler: it tokenises comments/strings, tracks balanced
member bodies, and preserves the source expressions it cannot evaluate.  This
makes the result reproducible without adding a build dependency while keeping
uncertain expressions visible rather than guessing.

Typical invocation (run from the repository root)::

    python3 backend/scripts/generate-rest-inventory.py \
      --backend-root backend \
      --frontend-root bw-frontend/frontend \
      --output-dir docs/reviews/rust-migration \
      --openapi docs/reviews/rust-migration/openapi.json

The output directory is required.  Existing files with the same names are
replaced atomically only after all inputs have been read.  No business data,
request payloads, credentials, or generated fixture values are collected.
"""

from __future__ import annotations

import argparse
import bisect
import csv
import hashlib
import json
import os
import posixpath
import re
import shlex
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Iterator, Optional


HTTP_METHODS = {"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"}
JAVA_HTTP_ANNOTATIONS = HTTP_METHODS
JAVA_BINDING_ANNOTATIONS = {
    "RestQuery",
    "RestHeader",
    "RestPath",
    "RestForm",
    "RestCookie",
    "RestMatrixParam",
    "PathParam",
    "QueryParam",
    "HeaderParam",
    "CookieParam",
    "FormParam",
    "RequestBody",
    "BeanParam",
    "MultipartForm",
    "Context",
}
JS_EXTENSIONS = {".js", ".mjs", ".cjs", ".vue", ".ts", ".tsx", ".jsx"}


class InventoryError(RuntimeError):
    pass


def compact(value: object) -> str:
    """Serialize one TSV cell without literal tabs or line breaks."""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def aggregate_sha256(paths: Iterable[Path], root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(set(paths), key=lambda p: p.as_posix()):
        digest.update(rooted_relative(path, root).encode("utf-8"))
        digest.update(b"\0")
        digest.update(bytes.fromhex(sha256_file(path)))
        digest.update(b"\0")
    return digest.hexdigest()


def rooted_relative(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.resolve().as_posix()


def line_number(text: str, positions: list[int], offset: int) -> int:
    return bisect.bisect_right(positions, offset)


def line_positions(text: str) -> list[int]:
    starts = [0]
    starts.extend(match.end() for match in re.finditer("\n", text))
    return starts


def mask_code(text: str) -> str:
    """Mask comments and quoted literals while preserving all other positions.

    Newlines are retained so offsets and source line numbers remain exact.
    Java text blocks and JavaScript template strings are treated as literals;
    an interpolation inside a template is intentionally not parsed as code.
    """
    out = list(text)
    state = "code"
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if state == "code":
            if c == "/" and nxt == "/":
                out[i] = out[i + 1] = " "
                i += 2
                state = "line_comment"
                continue
            if c == "/" and nxt == "*":
                out[i] = out[i + 1] = " "
                i += 2
                state = "block_comment"
                continue
            if text.startswith('"""', i):
                out[i : i + 3] = [" "] * 3
                i += 3
                state = "text_block"
                continue
            if c in "'\"`":
                out[i] = " "
                state = {"'": "char", '"': "string", "`": "template"}[c]
                i += 1
                continue
            i += 1
            continue
        if state == "line_comment":
            if c == "\n":
                state = "code"
            else:
                out[i] = " "
            i += 1
            continue
        if state == "block_comment":
            if c == "*" and nxt == "/":
                out[i] = out[i + 1] = " "
                i += 2
                state = "code"
            else:
                if c != "\n":
                    out[i] = " "
                i += 1
            continue
        if state == "text_block":
            if text.startswith('"""', i):
                out[i : i + 3] = [" "] * 3
                i += 3
                state = "code"
            else:
                if c != "\n":
                    out[i] = " "
                i += 1
            continue
        # string, char, and template literals
        if c == "\\":
            out[i] = " "
            if i + 1 < n:
                if text[i + 1] != "\n":
                    out[i + 1] = " "
                i += 2
            else:
                i += 1
            continue
        terminator = {"char": "'", "string": '"', "template": "`"}[state]
        if c == terminator:
            out[i] = " "
            i += 1
            state = "code"
        else:
            if c != "\n":
                out[i] = " "
            i += 1
    return "".join(out)


def skip_ws(masked: str, index: int) -> int:
    while index < len(masked) and masked[index].isspace():
        index += 1
    return index


def balanced_end(masked: str, opening: int, opening_char: str) -> int:
    """Return the exclusive end of a balanced pair in masked source."""
    close_char = {"(": ")", "{": "}", "[": "]"}[opening_char]
    depth = 0
    for index in range(opening, len(masked)):
        char = masked[index]
        if char == opening_char:
            depth += 1
        elif char == close_char:
            depth -= 1
            if depth == 0:
                return index + 1
    raise InventoryError(f"unbalanced {opening_char!r} at offset {opening}")


def split_top_level(text: str, separator: str = ",") -> list[str]:
    """Split an expression while respecting quotes and nested delimiters."""
    parts: list[str] = []
    start = 0
    stack: list[str] = []
    quote: Optional[str] = None
    escaped = False
    pairs = {"(": ")", "[": "]", "{": "}"}
    for index, char in enumerate(text):
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in "'\"`":
            quote = char
        elif char in pairs:
            stack.append(pairs[char])
        elif stack and char == stack[-1]:
            stack.pop()
        elif not stack and char == separator:
            parts.append(text[start:index].strip())
            start = index + 1
    parts.append(text[start:].strip())
    return [part for part in parts if part]


def split_plus_expression(text: str) -> list[str]:
    parts: list[str] = []
    start = 0
    stack: list[str] = []
    quote: Optional[str] = None
    escaped = False
    pairs = {"(": ")", "[": "]", "{": "}"}
    for index, char in enumerate(text):
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in "'\"`":
            quote = char
        elif char in pairs:
            stack.append(pairs[char])
        elif stack and char == stack[-1]:
            stack.pop()
        elif not stack and char == "+":
            parts.append(text[start:index].strip())
            start = index + 1
    parts.append(text[start:].strip())
    return [part for part in parts if part]


def parse_literal(value: str) -> Optional[str]:
    value = value.strip()
    if len(value) < 2 or value[0] not in "'\"`" or value[-1] != value[0]:
        return None
    quote = value[0]
    body = value[1:-1]
    # Java and JS escapes relevant to route/header names.  Unknown escapes are
    # preserved and recorded by the caller as a non-literal expression.
    def replace(match: re.Match[str]) -> str:
        token = match.group(1)
        mapping = {"n": "\n", "r": "\r", "t": "\t", "b": "\b", "f": "\f", "\\": "\\", '"': '"', "'": "'", "`": "`"}
        if token in mapping:
            return mapping[token]
        if token.startswith("u") and len(token) == 5:
            try:
                return chr(int(token[1:], 16))
            except ValueError:
                return "\\" + token
        return "\\" + token
    return re.sub(r"\\(u[0-9a-fA-F]{4}|.)", replace, body)


def annotation_records(text: str, masked: str, start: int, end: int) -> list[dict[str, object]]:
    records: list[dict[str, object]] = []
    ann_re = re.compile(r"@([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)")
    for match in ann_re.finditer(masked, start, end):
        name = match.group(1).split(".")[-1]
        cursor = skip_ws(masked, match.end())
        args = ""
        record_end = match.end()
        if cursor < end and masked[cursor] == "(":
            balanced = balanced_end(masked, cursor, "(")
            if balanced > end:
                continue
            args = text[cursor + 1 : balanced - 1]
            record_end = balanced
        records.append({"name": name, "args": args, "start": match.start(), "end": record_end})
    return records


def annotation_value(args: str, preferred: str = "value") -> Optional[str]:
    fields = split_top_level(args)
    first: Optional[str] = None
    for field_text in fields:
        if "=" in field_text:
            left, right = field_text.split("=", 1)
            if left.strip() == preferred:
                return right.strip()
        elif first is None:
            first = field_text.strip()
    return first


def collect_string_constants(java_files: list[Path]) -> dict[str, str]:
    constants: dict[str, str] = {}
    assignment = re.compile(
        r"\b(?:public|protected|private)?\s*(?:static\s+)?final\s+String\s+([A-Za-z_$][\w$]*)\s*=\s*([^;]+);"
    )
    for path in java_files:
        source = path.read_text(encoding="utf-8")
        masked = mask_code(source)
        for match in assignment.finditer(masked):
            constants.setdefault(match.group(1), source[match.start(2) : match.end(2)].strip())
    return constants


def resolve_expression(expression: Optional[str], constants: dict[str, str], unresolved: list[str]) -> Optional[str]:
    if expression is None:
        return None
    expression = expression.strip()
    if not expression:
        return ""
    # Remove a Java annotation's named array wrapper only for simple scalar use.
    pieces = split_plus_expression(expression)
    values: list[str] = []
    for piece in pieces:
        literal = parse_literal(piece)
        if literal is not None:
            values.append(literal)
            continue
        key = piece.rsplit(".", 1)[-1].strip()
        if key in constants and constants[key] != expression:
            nested = resolve_expression(constants[key], constants, unresolved)
            if nested is not None:
                values.append(nested)
                continue
        unresolved.append(expression)
        return "{{" + expression.replace("\n", " ").strip() + "}}"
    return "".join(values)


def extract_operation(annotations: list[dict[str, object]]) -> tuple[str, str, str]:
    summary = ""
    description = ""
    raw = ""
    for annotation in annotations:
        if annotation["name"] != "Operation":
            continue
        args = str(annotation["args"])
        raw = re.sub(r"\s+", " ", args).strip()
        for field_text in split_top_level(args):
            if "=" not in field_text:
                continue
            key, value = field_text.split("=", 1)
            parsed = parse_literal(value.strip()) or value.strip()
            if key.strip() == "summary":
                summary = parsed
            elif key.strip() == "description":
                description = parsed
    return summary, description, raw


def has_annotation(annotations: list[dict[str, object]], *names: str) -> bool:
    wanted = set(names)
    return any(str(item["name"]) in wanted for item in annotations)


def normalize_path(value: str) -> str:
    value = value.strip()
    if not value:
        return "/"
    if not value.startswith("/"):
        value = "/" + value
    value = re.sub(r"/{2,}", "/", value)
    return value


def join_path(*parts: str) -> str:
    result = ""
    for part in parts:
        part = part.strip()
        if not part:
            continue
        if not result:
            result = normalize_path(part)
        else:
            result = result.rstrip("/") + "/" + part.lstrip("/")
    return normalize_path(result or "/")


def route_key(method: str, path: str) -> str:
    # Query strings are request bindings, not route identity.  Keep literal
    # spaces in source URLs out of reconciliation while preserving case.
    path = path.split("?", 1)[0].split("#", 1)[0].strip()
    path = re.sub(r"\$\{[^}]*\}", "{}", path)
    path = re.sub(r"\{[^}]*\}", "{}", path)
    path = normalize_path(path)
    if len(path) > 1:
        path = path.rstrip("/")
    return method.upper() + " " + path


def find_brace_depth(masked: str) -> list[int]:
    depths = [0] * (len(masked) + 1)
    depth = 0
    for index, char in enumerate(masked):
        depths[index] = depth
        if char == "{":
            depth += 1
        elif char == "}":
            depth = max(0, depth - 1)
    depths[len(masked)] = depth
    return depths


def scan_java_file(path: Path, backend_root: Path, constants: dict[str, str], provenance: dict[str, list[str]]) -> list[dict[str, object]]:
    source = path.read_text(encoding="utf-8")
    masked = mask_code(source)
    positions = line_positions(source)
    class_matches = list(re.finditer(r"\bclass\s+([A-Za-z_$][\w$]*)", masked))
    if not class_matches:
        provenance["unparsed_java"].append(rooted_relative(path, backend_root))
        return []
    # Controller files normally contain one top-level class.  Pick the first
    # class and use brace depth to avoid nested helper declarations.
    class_match = class_matches[0]
    class_open = masked.find("{", class_match.end())
    if class_open < 0:
        provenance["unparsed_java"].append(rooted_relative(path, backend_root))
        return []
    class_end = balanced_end(masked, class_open, "{")
    class_annotations = annotation_records(source, masked, max(0, class_match.start() - 4096), class_match.start())
    class_path_expr = None
    for annotation in class_annotations:
        if annotation["name"] == "Path":
            class_path_expr = annotation_value(str(annotation["args"]))
            break
    unresolved: list[str] = []
    class_path = resolve_expression(class_path_expr, constants, unresolved) or "/"
    class_name = str(class_match.group(1))
    class_jwt = has_annotation(class_annotations, "JWT")
    class_admin = has_annotation(class_annotations, "RequireAdmin")
    class_roles = [str(a["args"]).strip() for a in class_annotations if a["name"] in {"RolesAllowed", "PermitAll", "DenyAll"}]
    depths = find_brace_depth(masked)
    class_depth = depths[class_open + 1]
    candidates: list[dict[str, object]] = []
    visibility = re.compile(r"\b(?:public|protected|private)\b")
    for match in visibility.finditer(masked, class_open + 1, class_end - 1):
        if depths[match.start()] != class_depth:
            continue
        # A method's parameter list is the first top-level parenthesis before
        # its body/semicolon.  Fields and constructor calls therefore do not
        # become endpoints accidentally.
        cursor = match.end()
        paren = masked.find("(", cursor, class_end)
        if paren < 0:
            continue
        delimiter_search = re.search(r"[;{}]", masked[cursor:paren])
        if delimiter_search:
            continue
        try:
            params_end = balanced_end(masked, paren, "(")
        except InventoryError:
            provenance["parse_limitations"].append(f"unbalanced Java parameters: {rooted_relative(path, backend_root)}:{line_number(source, positions, paren)}")
            continue
        after = skip_ws(masked, params_end)
        # Throws clauses may contain identifiers but no opening body until `{`.
        body_open = masked.find("{", after, class_end)
        semicolon = masked.find(";", after, class_end)
        if body_open < 0 or (semicolon >= 0 and semicolon < body_open):
            continue
        try:
            body_end = balanced_end(masked, body_open, "{")
        except InventoryError:
            provenance["parse_limitations"].append(f"unbalanced Java body: {rooted_relative(path, backend_root)}:{line_number(source, positions, body_open)}")
            continue
        before_paren = masked[cursor:paren]
        name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", before_paren)
        if not name_match:
            continue
        method_name = name_match.group(1)
        return_type = before_paren[: name_match.start()].strip()
        # Remove modifiers and annotations from the return type.  Keep generic
        # text exactly enough for migration review.
        return_type = re.sub(r"\b(?:static|final|synchronized|abstract|native|default)\b", " ", return_type)
        return_type = re.sub(r"@[A-Za-z_$][\w$]*(?:\([^)]*\))?", " ", return_type)
        return_type = re.sub(r"\s+", " ", return_type).strip()
        candidates.append({
            "start": match.start(),
            "body_open": body_open,
            "body_end": body_end,
            "method_name": method_name,
            "return_type": return_type,
            "params": source[paren + 1 : params_end - 1],
        })
    candidates.sort(key=lambda item: int(item["start"]))
    records: list[dict[str, object]] = []
    previous_end = class_open + 1
    for candidate in candidates:
        member_start = int(candidate["start"])
        annotations = annotation_records(source, masked, previous_end, member_start)
        previous_end = int(candidate["body_end"])
        http = next((str(a["name"]).upper() for a in annotations if str(a["name"]).upper() in JAVA_HTTP_ANNOTATIONS), None)
        method_path_expr = None
        for annotation in annotations:
            if annotation["name"] == "Path":
                method_path_expr = annotation_value(str(annotation["args"]))
                break
        if not http and method_path_expr is None:
            continue
        if http is None:
            http = "UNKNOWN"
            provenance["parse_limitations"].append(
                f"@Path method has no HTTP verb: {rooted_relative(path, backend_root)}:{line_number(source, positions, member_start)}"
            )
        method_path = resolve_expression(method_path_expr, constants, unresolved) or "/"
        full_path = join_path("/api", class_path, method_path)
        operation_summary, operation_description, operation_raw = extract_operation(annotations)
        body = source[int(candidate["body_open"]) : int(candidate["body_end"])]
        request_bindings = parse_java_bindings(str(candidate["params"]), constants, unresolved)
        binding_names = " ".join(item["annotation"] + " " + item["raw"] for item in request_bindings)
        auth_jwt = class_jwt or has_annotation(annotations, "JWT")
        auth_admin = class_admin or has_annotation(annotations, "RequireAdmin")
        roles = class_roles + [str(a["args"]).strip() for a in annotations if a["name"] in {"RolesAllowed", "PermitAll", "DenyAll"}]
        capability = infer_java_capabilities(body, request_bindings, operation_summary, operation_description, annotations, str(candidate["return_type"]))
        source_relative = rooted_relative(path, backend_root)
        record = {
            "method": http,
            "path": full_path,
            "endpoint_key": route_key(http, full_path),
            "controller_file": source_relative,
            "controller_class": class_name,
            "method_name": candidate["method_name"],
            "line": line_number(source, positions, member_start),
            "class_path": class_path,
            "method_path": method_path,
            "auth_jwt": auth_jwt,
            "require_admin": auth_admin,
            "roles": roles,
            "request_bindings": request_bindings,
            "request_binding_summary": binding_names,
            "return_type": candidate["return_type"],
            "response_shape": infer_response_shape(str(candidate["return_type"]), body),
            "operation_summary": operation_summary,
            "operation_description": operation_description,
            "operation_raw": operation_raw,
            "annotations": annotations_to_json(annotations),
            "capability": capability,
            "source_sha256": sha256_file(path),
            "unresolved_expressions": sorted(set(unresolved)),
        }
        records.append(record)
    if unresolved:
        provenance["unresolved_java_expressions"].extend(
            f"{rooted_relative(path, backend_root)}: {expression}" for expression in sorted(set(unresolved))
        )
    return records


def parse_java_bindings(params: str, constants: dict[str, str], unresolved: list[str]) -> list[dict[str, object]]:
    bindings: list[dict[str, object]] = []
    for raw in split_top_level(params):
        if not raw:
            continue
        annotations: list[dict[str, object]] = []
        masked = mask_code(raw)
        for annotation in annotation_records(raw, masked, 0, len(raw)):
            if annotation["name"] in JAVA_BINDING_ANNOTATIONS:
                annotations.append(annotation)
        plain = re.sub(r"@[A-Za-z_$][\w$]*(?:\([^)]*\))?", " ", raw)
        plain = re.sub(r"\s+", " ", plain).strip()
        parameter_name = ""
        name_match = re.search(r"([A-Za-z_$][\w$]*)\s*$", plain)
        if name_match:
            parameter_name = name_match.group(1)
        if annotations:
            for annotation in annotations:
                annotation_name = str(annotation["name"])
                expression = annotation_value(str(annotation["args"]))
                resolved = resolve_expression(expression, constants, unresolved)
                bindings.append({
                    "parameter": parameter_name,
                    "type_and_name": plain,
                    "annotation": annotation_name,
                    "key_expression": expression or "",
                    "key": resolved if resolved is not None else "",
                    "raw": re.sub(r"\s+", " ", raw).strip(),
                })
        else:
            # JAX-RS/RESTEasy treats the one unannotated complex parameter as
            # the request body.  Retain primitive ambiguity in the raw field.
            bindings.append({
                "parameter": parameter_name,
                "type_and_name": plain,
                "annotation": "UNANNOTATED_BODY",
                "key_expression": "",
                "key": "",
                "raw": re.sub(r"\s+", " ", raw).strip(),
            })
    return bindings


def annotations_to_json(annotations: list[dict[str, object]]) -> list[dict[str, object]]:
    return [{"name": a["name"], "args": re.sub(r"\s+", " ", str(a["args"])).strip()} for a in annotations]


def infer_response_shape(return_type: str, body: str) -> dict[str, object]:
    wrapper = "Response<T>" if re.search(r"\bResponse\s*<", return_type) else "direct"
    generic = ""
    response_match = re.search(r"\bResponse\s*<(.+)>", return_type)
    if response_match:
        generic = response_match.group(1).strip()
    return {
        "wrapper": wrapper,
        "payload_type": generic,
        "uses_response_result": bool(re.search(r"\bResponseResult\.(?:success|error)\s*\(", body)),
        "returns_void_success": bool(re.search(r"ResponseResult\.success\s*\(\s*\)", body)),
        "raw_return_type": return_type,
    }


def infer_java_capabilities(body: str, bindings: list[dict[str, object]], summary: str, description: str, annotations: list[dict[str, object]], return_type: str) -> dict[str, object]:
    evidence: list[str] = []
    lower = (body + " " + summary + " " + description + " " + return_type).lower()
    binding_text = " ".join(json.dumps(item, ensure_ascii=False) for item in bindings).lower()
    annotation_text = " ".join(str(item["name"]) + " " + str(item["args"]) for item in annotations).lower()
    upload_terms = ["@restform", "@multipartform", "fileupload", "multipartformdatainput", "inputstream", "upload", "上传", "导入", "import"]
    export_terms = ["streamingoutput", "content-disposition", "workbook", ".xlsx", ".csv", "download", "export", "导出", "下载", "outputstream", "byte[]"]
    upload_hits = [term for term in upload_terms if term in (lower + " " + binding_text + " " + annotation_text)]
    export_hits = [term for term in export_terms if term in (lower + " " + annotation_text)]
    # The method name alone is not evidence: hits come from the body,
    # OpenAPI operation text, binding annotations, or return type.
    upload = bool(upload_hits)
    export = bool(export_hits)
    evidence.extend("upload:" + hit for hit in upload_hits)
    evidence.extend("export:" + hit for hit in export_hits)
    return {
        "upload": upload,
        "export": export,
        "evidence": sorted(set(evidence)),
        "confidence": "static-token-evidence" if evidence else "none",
    }


@dataclass
class JsFunction:
    name: str
    start: int
    end: int
    line: int
    exported: bool = False


@dataclass
class JsCall:
    file: Path
    line: int
    kind: str
    method: str
    url_expr: str
    url: str
    endpoint_key: str
    function_name: str
    function_line: int
    api_module: str
    api_export: str
    upload: bool
    export: bool
    evidence: list[str] = field(default_factory=list)
    raw: str = ""


def js_string_or_expression(expr: str) -> tuple[str, str, list[str]]:
    """Return display expression, best static URL, and unresolved details."""
    expr = expr.strip().rstrip(",").strip()
    literal = parse_literal(expr)
    if literal is not None:
        return expr, literal, []
    if expr.startswith("`") and expr.endswith("`"):
        literal_template = parse_literal(expr)
        return expr, literal_template or expr, [] if literal_template else [expr]
    return expr, expr, [expr]


def js_template_route(value: str) -> str:
    return re.sub(r"\$\{([^}]*)\}", lambda m: "{" + m.group(1).strip() + "}", value)


def js_functions(source: str, positions: list[int]) -> list[JsFunction]:
    masked = mask_code(source)
    functions: list[JsFunction] = []
    patterns = [
        re.compile(r"\bexport\s+(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>"),
        re.compile(r"\bexport\s+(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\("),
        re.compile(r"\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>"),
        re.compile(r"\b(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\("),
    ]
    found: set[int] = set()
    for pattern in patterns:
        for match in pattern.finditer(masked):
            if match.start() in found:
                continue
            found.add(match.start())
            after = skip_ws(masked, match.end())
            if after < len(masked) and masked[after] == "{":
                try:
                    end = balanced_end(masked, after, "{")
                except InventoryError:
                    end = len(source)
            else:
                # Expression-bodied arrow function ends at the next newline;
                # this is sufficient for the API wrapper files and records the
                # expression as source evidence.
                newline = source.find("\n", after)
                end = len(source) if newline < 0 else newline
            prefix = masked[max(0, match.start() - 12) : match.start()]
            exported = bool(re.search(r"\bexport\s*$", prefix)) or bool(re.match(r"\s*export\b", masked[match.start() : match.end()]))
            functions.append(JsFunction(str(match.group(1)), match.start(), end, line_number(source, positions, match.start()), exported))
    functions.sort(key=lambda item: item.start)
    # Add a synthetic top-level owner for direct calls outside a function.
    return functions


def nearest_js_function(functions: list[JsFunction], offset: int) -> Optional[JsFunction]:
    containing = [function for function in functions if function.start <= offset < function.end]
    if not containing:
        return None
    return max(containing, key=lambda item: item.start)


def js_property(config: str, key: str) -> Optional[str]:
    # Property values may be strings, template strings, or expressions.  Read
    # until a top-level comma/closing brace rather than relying on one-line
    # annotation-style regexes.
    masked = mask_code(config)
    match = re.search(r"\b" + re.escape(key) + r"\s*:", masked)
    if not match:
        return None
    cursor = match.end()
    while cursor < len(config) and config[cursor].isspace():
        cursor += 1
    start = cursor
    stack: list[str] = []
    quote: Optional[str] = None
    escaped = False
    pairs = {"(": ")", "[": "]", "{": "}"}
    while cursor < len(config):
        char = config[cursor]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char in "'\"`":
            quote = char
        elif char in pairs:
            stack.append(pairs[char])
        elif stack and char == stack[-1]:
            stack.pop()
        elif not stack and char in ",\n":
            break
        cursor += 1
    return config[start:cursor].strip().rstrip("}").strip()


def parse_js_call_url(expr: str, provenance: dict[str, list[str]]) -> tuple[str, str, list[str]]:
    display, value, unresolved = js_string_or_expression(expr)
    if value.startswith("`"):
        value = js_template_route(value)
    # Strip common quote artefacts and turn template interpolation into a
    # path pattern; retain the original expression in url_expr.
    if value.startswith(("'", '"')):
        parsed = parse_literal(value)
        if parsed is not None:
            value = parsed
    if "${" in value:
        value = js_template_route(value)
    if not value.startswith("/"):
        unresolved.append("non-root URL expression: " + expr)
    if unresolved:
        provenance["unresolved_js_expressions"].extend(unresolved)
    return display, value, sorted(set(unresolved))


def find_js_calls(path: Path, frontend_root: Path, provenance: dict[str, list[str]]) -> tuple[list[JsCall], list[JsFunction]]:
    source = path.read_text(encoding="utf-8")
    masked = mask_code(source)
    positions = line_positions(source)
    functions = js_functions(source, positions)
    calls: list[JsCall] = []
    call_re = re.compile(r"\b(axios|fetch)\s*(?:\.\s*(get|post|put|delete|patch|request))?\s*\(")
    for match in call_re.finditer(masked):
        opening = masked.find("(", match.start(), match.end())
        try:
            end = balanced_end(masked, opening, "(")
        except InventoryError:
            provenance["parse_limitations"].append(f"unbalanced JS call: {rooted_relative(path, frontend_root)}:{line_number(source, positions, match.start())}")
            continue
        arguments = source[opening + 1 : end - 1]
        callee = str(match.group(1))
        method_suffix = match.group(2)
        method = method_suffix.upper() if method_suffix else "UNKNOWN"
        url_expr = ""
        raw_call = source[match.start() : end]
        if method_suffix and callee == "axios":
            first = split_top_level(arguments)[0] if split_top_level(arguments) else ""
            url_expr = first
        elif callee == "fetch":
            args = split_top_level(arguments)
            url_expr = args[0] if args else ""
            options = args[1] if len(args) > 1 else ""
            method_value = js_property(options, "method")
            if method_value:
                parsed_method = parse_literal(method_value)
                method = (parsed_method or method_value).upper()
        else:
            config = arguments.strip()
            if config.startswith("{") and config.endswith("}"):
                url_expr = js_property(config[1:-1], "url") or ""
                method_value = js_property(config[1:-1], "method")
                parsed_method = parse_literal(method_value or "")
                method = (parsed_method or method_value or "UNKNOWN").upper()
            else:
                url_expr = split_top_level(arguments)[0] if arguments else ""
        if not url_expr:
            provenance["parse_limitations"].append(f"HTTP call URL not statically found: {rooted_relative(path, frontend_root)}:{line_number(source, positions, match.start())}")
            continue
        url_expr_display, url, unresolved = parse_js_call_url(url_expr, provenance)
        owner = nearest_js_function(functions, match.start())
        owner_name = owner.name if owner else "<top-level>"
        owner_line = owner.line if owner else line_number(source, positions, match.start())
        owner_text = source[owner.start : owner.end] if owner else raw_call
        lower_owner = owner_text.lower()
        evidence: list[str] = []
        upload_hits = [token for token in ("formdata", "multipart", "upload", "file", "import") if token in lower_owner]
        export_hits = [token for token in ("export", "download", "xlsx", "csv", "blob", "responseType", "file") if token.lower() in lower_owner]
        if upload_hits:
            evidence.extend("upload:" + token for token in upload_hits)
        if export_hits:
            evidence.extend("export:" + token for token in export_hits)
        api_module = rooted_relative(path, frontend_root)
        # Only files under common/api are wrappers; consumers are annotated
        # after import resolution below.
        api_export = owner.name if owner and "/common/api/" in "/" + api_module else ""
        calls.append(JsCall(
            file=path,
            line=line_number(source, positions, match.start()),
            kind="axios" if callee == "axios" else "fetch",
            method=method,
            url_expr=url_expr_display,
            url=url,
            endpoint_key=route_key(method, url),
            function_name=owner_name,
            function_line=owner_line,
            api_module=api_module if api_export else "",
            api_export=api_export,
            upload=bool(upload_hits),
            export=bool(export_hits),
            evidence=sorted(set(evidence + ["unresolved:" + item for item in unresolved])),
            raw=raw_call,
        ))
    return calls, functions


def resolve_import_target(importer: Path, specifier: str, frontend_root: Path) -> Optional[Path]:
    specifier = specifier.split("?", 1)[0]
    if specifier.startswith("@/"):
        candidate = frontend_root / "src" / specifier[2:]
    elif specifier.startswith("."):
        candidate = (importer.parent / specifier).resolve()
    else:
        return None
    options = [candidate]
    if candidate.suffix == "":
        options.extend([candidate.with_suffix(ext) for ext in (".js", ".mjs", ".ts", ".tsx", ".vue")])
        options.append(candidate / "index.js")
    return next((option for option in options if option.is_file()), None)


def parse_js_imports(source: str) -> list[tuple[str, str]]:
    masked = mask_code(source)
    # Import declarations in this frontend are single-line; support a
    # multiline declaration by limiting the match to a semicolon/newline.
    pattern = re.compile(r"\bimport\s+([\s\S]*?)\s+from\s*([\"'])(.*?)\2", re.MULTILINE)
    imports: list[tuple[str, str]] = []
    for match in pattern.finditer(source):
        imports.append((source[match.start(1) : match.end(1)].strip(), source[match.start(3) : match.end(3)]))
    return imports


def consumer_rows(
    source_files: list[Path], frontend_root: Path, api_exports: dict[Path, set[str]], provenance: dict[str, list[str]]
) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for path in source_files:
        source = path.read_text(encoding="utf-8")
        positions = line_positions(source)
        own_api = "/common/api/" in "/" + rooted_relative(path, frontend_root)
        if own_api:
            continue
        imports = parse_js_imports(source)
        for spec, module_spec in imports:
            target = resolve_import_target(path, module_spec, frontend_root)
            if not target or target not in api_exports:
                continue
            named: dict[str, str] = {}
            namespace = ""
            brace = re.search(r"\{(.*)\}", spec, re.S)
            if brace:
                for part in split_top_level(brace.group(1)):
                    if not part:
                        continue
                    names = re.split(r"\s+as\s+", part.strip())
                    named[names[-1].strip()] = names[0].strip()
            star = re.search(r"\*\s+as\s+([A-Za-z_$][\w$]*)", spec)
            if star:
                namespace = star.group(1)
            for local, exported in named.items():
                if exported not in api_exports[target]:
                    provenance["unresolved_imports"].append(f"{rooted_relative(path, frontend_root)} imports missing {exported} from {rooted_relative(target, frontend_root)}")
                    continue
                call_pattern = re.compile(r"\b" + re.escape(local) + r"\s*\(")
                for call in call_pattern.finditer(mask_code(source)):
                    if call.start() <= source.find("from", call.start() - 100, call.start() + 100):
                        continue
                    symbol = nearest_symbol_name(source, call.start())
                    rows.append({
                        "api_module": rooted_relative(target, frontend_root),
                        "api_export": exported,
                        "consumer_file": rooted_relative(path, frontend_root),
                        "consumer_line": line_number(source, positions, call.start()),
                        "consumer_symbol": symbol,
                        "consumer_kind": "imported_api_call",
                        "evidence": f"import {{{exported}{' as ' + local if local != exported else ''}}} from {module_spec}; call {local}()",
                    })
            if namespace:
                for exported in sorted(api_exports[target]):
                    call_pattern = re.compile(r"\b" + re.escape(namespace) + r"\s*\.\s*" + re.escape(exported) + r"\s*\(")
                    for call in call_pattern.finditer(mask_code(source)):
                        rows.append({
                            "api_module": rooted_relative(target, frontend_root),
                            "api_export": exported,
                            "consumer_file": rooted_relative(path, frontend_root),
                            "consumer_line": line_number(source, positions, call.start()),
                            "consumer_symbol": nearest_symbol_name(source, call.start()),
                            "consumer_kind": "namespace_api_call",
                            "evidence": f"{namespace}.{exported}() imported from {module_spec}",
                        })
    return rows


def nearest_symbol_name(source: str, offset: int) -> str:
    prefix = source[:offset]
    matches = list(re.finditer(r"(?:function\s+|(?:const|let|var)\s+)([A-Za-z_$][\w$]*)\s*(?:=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>)?", prefix))
    return matches[-1].group(1) if matches else "<top-level>"


def read_openapi(path: Path, provenance: dict[str, list[str]]) -> set[str]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise InventoryError(f"cannot parse OpenAPI input {path}: {error}") from error
    paths = document.get("paths")
    if not isinstance(paths, dict):
        raise InventoryError(f"OpenAPI input {path} has no object paths")
    keys: set[str] = set()
    for route, item in paths.items():
        if not isinstance(item, dict):
            continue
        for method in item:
            method_upper = str(method).upper()
            if method_upper in HTTP_METHODS:
                keys.add(route_key(method_upper, str(route)))
    if not keys:
        provenance["parse_limitations"].append("OpenAPI paths object contained no HTTP operations")
    return keys


def write_tsv(path: Path, header: list[str], rows: Iterable[dict[str, object]]) -> None:
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_MINIMAL)
        writer.writerow(header)
        for row in rows:
            writer.writerow([row.get(column, "") if isinstance(row.get(column, ""), str) else compact(row.get(column, "")) for column in header])
    os.replace(temporary, path)


def source_sha_map(paths: Iterable[Path], root: Path) -> dict[str, str]:
    return {rooted_relative(path, root): sha256_file(path) for path in sorted(set(paths), key=lambda item: item.as_posix())}


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    script_root = Path(__file__).resolve().parents[1]
    repo_root = script_root.parent
    parser.add_argument("--backend-root", type=Path, default=script_root, help="backend project root")
    parser.add_argument("--frontend-root", type=Path, default=repo_root / "bw-frontend" / "frontend", help="frontend project root")
    parser.add_argument("--output-dir", type=Path, required=True, help="directory for inventory evidence")
    parser.add_argument("--openapi", type=Path, help="optional OpenAPI JSON used for route/method reconciliation")
    args = parser.parse_args(argv)
    backend_root = args.backend_root.resolve()
    frontend_root = args.frontend_root.resolve()
    output_dir = args.output_dir.resolve()
    if not backend_root.is_dir():
        raise InventoryError(f"missing backend root: {backend_root}")
    if not frontend_root.is_dir():
        raise InventoryError(f"missing frontend root: {frontend_root}")
    controller_root = backend_root / "src" / "main" / "java" / "com" / "nip" / "controller"
    api_root = frontend_root / "src" / "common" / "api"
    if not controller_root.is_dir():
        raise InventoryError(f"missing controller source root: {controller_root}")
    if not api_root.is_dir():
        raise InventoryError(f"missing frontend API root: {api_root}")
    controller_files = sorted(controller_root.rglob("*Controller.java"))
    api_files = sorted(api_root.glob("*.js"))
    if not controller_files:
        raise InventoryError(f"no *Controller.java files under {controller_root}")
    if not api_files:
        raise InventoryError(f"no API modules under {api_root}")
    if args.openapi and not args.openapi.is_file():
        raise InventoryError(f"missing OpenAPI input: {args.openapi}")

    provenance: dict[str, list[str]] = {
        "unparsed_java": [],
        "parse_limitations": [],
        "unresolved_java_expressions": [],
        "unresolved_js_expressions": [],
        "unresolved_imports": [],
    }
    all_java_files = sorted((backend_root / "src" / "main" / "java").rglob("*.java"))
    constants = collect_string_constants(all_java_files)
    rest_records: list[dict[str, object]] = []
    for path in controller_files:
        rest_records.extend(scan_java_file(path, backend_root, constants, provenance))
    if not rest_records:
        raise InventoryError("controller scan produced no endpoints")

    frontend_files = sorted(path for path in (frontend_root / "src").rglob("*") if path.is_file() and path.suffix in JS_EXTENSIONS)
    api_exports: dict[Path, set[str]] = {}
    all_js_calls: list[JsCall] = []
    functions_by_file: dict[Path, list[JsFunction]] = {}
    for path in frontend_files:
        calls, functions = find_js_calls(path, frontend_root, provenance)
        all_js_calls.extend(calls)
        functions_by_file[path] = functions
        if path.parent == api_root:
            api_exports[path] = {function.name for function in functions if function.exported}
    if len(api_exports) != len(api_files):
        missing = [rooted_relative(path, frontend_root) for path in api_files if path not in api_exports]
        provenance["parse_limitations"].append("API module export parse incomplete: " + ", ".join(missing))
        for path in api_files:
            api_exports.setdefault(path, set())
    consumer_records = consumer_rows(frontend_files, frontend_root, api_exports, provenance)

    # Attach static consumer status to each backend route.  Matching is done on
    # normalised route keys so query strings and interpolated path variables do
    # not hide a real consumer; the original expression remains in the detail.
    consumers_by_endpoint: dict[str, list[dict[str, object]]] = {}
    for call in all_js_calls:
        if not call.url.startswith("/api"):
            continue
        consumers_by_endpoint.setdefault(call.endpoint_key, []).append({"kind": call.kind, "file": rooted_relative(call.file, frontend_root), "line": call.line, "api_export": call.api_export or None})
    for row in consumer_records:
        # Resolve the module call's route later through all_js_calls; this row
        # only links a real consumer to the exported wrapper.
        pass
    for record in rest_records:
        details = consumers_by_endpoint.get(str(record["endpoint_key"]), [])
        record["static_consumer_count"] = len(details)
        record["static_consumer_status"] = "STATIC_CONSUMER" if details else "NO_STATIC_CONSUMER"
        record["static_consumers"] = details

    api_call_rows: list[dict[str, object]] = []
    for call in all_js_calls:
        source_relative = rooted_relative(call.file, frontend_root)
        if call.api_export:
            api_call_rows.append({
                "api_module": call.api_module,
                "api_export": call.api_export,
                "api_line": call.function_line,
                "call_line": call.line,
                "consumer_file": source_relative,
                "consumer_line": call.line,
                "consumer_symbol": call.function_name,
                "consumer_kind": "api_module_request",
                "method": call.method,
                "url_expr": call.url_expr,
                "url": call.url,
                "endpoint_key": call.endpoint_key,
                "matched_backend_endpoint": call.endpoint_key if call.endpoint_key in {str(item["endpoint_key"]) for item in rest_records} else "UNMATCHED",
                "capability": {"upload": call.upload, "export": call.export, "evidence": call.evidence},
                "evidence": call.raw,
            })
        else:
            api_call_rows.append({
                "api_module": "",
                "api_export": "",
                "api_line": "",
                "call_line": call.line,
                "consumer_file": source_relative,
                "consumer_line": call.line,
                "consumer_symbol": call.function_name,
                "consumer_kind": "direct_http_request",
                "method": call.method,
                "url_expr": call.url_expr,
                "url": call.url,
                "endpoint_key": call.endpoint_key,
                "matched_backend_endpoint": call.endpoint_key if call.endpoint_key in {str(item["endpoint_key"]) for item in rest_records} else "UNMATCHED",
                "capability": {"upload": call.upload, "export": call.export, "evidence": call.evidence},
                "evidence": call.raw,
            })
    # Every exported API wrapper gets a row, including wrappers without a
    # statically extractable request, so absence is explicit rather than silent.
    seen_exports = {(row["api_module"], row["api_export"]) for row in api_call_rows if row["api_module"]}
    for module_path, exports in sorted(api_exports.items(), key=lambda item: item[0].as_posix()):
        module_relative = rooted_relative(module_path, frontend_root)
        for export_name in sorted(exports):
            if (module_relative, export_name) not in seen_exports:
                api_call_rows.append({
                    "api_module": module_relative,
                    "api_export": export_name,
                    "api_line": next((f.line for f in functions_by_file.get(module_path, []) if f.name == export_name), ""),
                    "call_line": "",
                    "consumer_file": module_relative,
                    "consumer_line": "",
                    "consumer_symbol": export_name,
                    "consumer_kind": "api_export_without_static_http",
                    "method": "",
                    "url_expr": "",
                    "url": "",
                    "endpoint_key": "",
                    "matched_backend_endpoint": "UNRESOLVED",
                    "capability": {"upload": False, "export": False, "evidence": []},
                    "evidence": "No statically extractable axios/fetch call in export body",
                })
    api_call_rows.extend({
        "api_module": row["api_module"], "api_export": row["api_export"], "api_line": "", "call_line": "", "consumer_file": row["consumer_file"], "consumer_line": row["consumer_line"], "consumer_symbol": row["consumer_symbol"], "consumer_kind": row["consumer_kind"], "method": "", "url_expr": "", "url": "", "endpoint_key": "", "matched_backend_endpoint": "UNRESOLVED", "capability": {}, "evidence": row["evidence"]
    } for row in consumer_records)

    rest_header = [
        "method", "path", "endpoint_key", "controller_file", "controller_class", "method_name", "line", "class_path", "method_path", "auth_jwt", "require_admin", "roles", "request_bindings", "request_binding_summary", "return_type", "response_shape", "operation_summary", "operation_description", "operation_raw", "annotations", "capability", "static_consumer_count", "static_consumer_status", "static_consumers", "source_sha256",
    ]
    frontend_header = [
        "api_module", "api_export", "api_line", "call_line", "consumer_file", "consumer_line", "consumer_symbol", "consumer_kind", "method", "url_expr", "url", "endpoint_key", "matched_backend_endpoint", "capability", "evidence",
    ]
    file_header = ["record_type", "source_file", "line", "symbol", "method", "path", "endpoint_key", "relation", "capability", "evidence"]
    file_rows: list[dict[str, object]] = []
    for record in rest_records:
        file_rows.append({
            "record_type": "backend_controller", "source_file": record["controller_file"], "line": record["line"], "symbol": record["controller_class"] + "." + record["method_name"], "method": record["method"], "path": record["path"], "endpoint_key": record["endpoint_key"], "relation": "rest_endpoint", "capability": record["capability"], "evidence": record["operation_raw"],
        })
    for row in api_call_rows:
        if row["endpoint_key"]:
            file_rows.append({
                "record_type": "frontend_api" if row["api_module"] else "frontend_direct_http", "source_file": row["consumer_file"], "line": row["consumer_line"], "symbol": row["consumer_symbol"], "method": row["method"], "path": row["url"], "endpoint_key": row["endpoint_key"], "relation": row["consumer_kind"], "capability": row["capability"], "evidence": row["evidence"],
            })

    output_dir.mkdir(parents=True, exist_ok=True)
    openapi_keys: Optional[set[str]] = None
    crosscheck: dict[str, object] = {"status": "not-requested"}
    if args.openapi:
        openapi_keys = read_openapi(args.openapi.resolve(), provenance)
        static_keys = {str(record["endpoint_key"]) for record in rest_records}
        missing_in_static = sorted(openapi_keys - static_keys)
        static_not_in_openapi = sorted(static_keys - openapi_keys)
        crosscheck = {"status": "pass" if not missing_in_static and not static_not_in_openapi else "mismatch", "openapi_operations": len(openapi_keys), "static_operations": len(static_keys), "missing_in_static": missing_in_static, "static_not_in_openapi": static_not_in_openapi}
        if missing_in_static:
            provenance["openapi_omissions"] = missing_in_static
        if static_not_in_openapi:
            provenance["openapi_static_only"] = static_not_in_openapi

    all_source_files = controller_files + api_files + frontend_files + all_java_files
    if args.openapi:
        all_source_files.append(args.openapi.resolve())
    commit = ""
    try:
        commit_result = subprocess.run(["git", "rev-parse", "HEAD"], cwd=repo_root, check=True, capture_output=True, text=True)
        commit = commit_result.stdout.strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise InventoryError(f"cannot obtain source commit with git rev-parse HEAD: {error}") from error
    metadata = {
        "generator": rooted_relative(Path(__file__), repo_root),
        "generator_sha256": sha256_file(Path(__file__).resolve()),
        "command": shlex.join([str(item) for item in (sys.argv if argv is None else [sys.argv[0], *argv])]),
        "generated_at_utc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source_commit": commit,
        "source_sha256": {
            "controllers": source_sha_map(controller_files, backend_root),
            "frontend_api_modules": source_sha_map(api_files, frontend_root),
            "frontend_source": source_sha_map(frontend_files, frontend_root),
            "all_java_sources_sha256": aggregate_sha256(all_java_files, backend_root),
            "controllers_sha256": aggregate_sha256(controller_files, backend_root),
            "frontend_api_sha256": aggregate_sha256(api_files, frontend_root),
        },
        "inputs": {"backend_root": str(backend_root), "frontend_root": str(frontend_root), "controller_root": rooted_relative(controller_root, backend_root), "api_root": rooted_relative(api_root, frontend_root), "openapi": str(args.openapi.resolve()) if args.openapi else None},
        "counts": {"controller_files": len(controller_files), "rest_endpoints": len(rest_records), "frontend_source_files": len(frontend_files), "frontend_api_modules": len(api_files), "frontend_http_calls": len(all_js_calls), "frontend_consumer_rows": len(consumer_records), "file_endpoint_rows": len(file_rows)},
        "expected_scope": {"controller_files": 62, "frontend_api_modules": 28},
        "openapi_crosscheck": crosscheck,
        "static_analysis_limitations": {
            "summary": "Balanced lexical source scan; no Java/JavaScript compiler or LSP was available. Unknown expressions are preserved as {{...}} and listed below.",
            "not_proof_of_runtime_activity": True,
            "consumer_status_meaning": "STATIC_CONSUMER means a source call matched by normalized method/path; NO_STATIC_CONSUMER means no such source call was found, not that the endpoint is unused at runtime.",
            "unresolved_expressions": provenance,
        },
    }
    write_tsv(output_dir / "rest-endpoints.tsv", rest_header, rest_records)
    write_tsv(output_dir / "frontend-callers.tsv", frontend_header, api_call_rows)
    write_tsv(output_dir / "file-endpoints.tsv", file_header, file_rows)
    metadata_path = output_dir / "rest-inventory-metadata.json"
    temporary = metadata_path.with_name(metadata_path.name + ".tmp")
    temporary.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, metadata_path)

    if args.openapi and crosscheck.get("status") != "pass":
        print(json.dumps({"openapi_crosscheck": crosscheck}, ensure_ascii=False), file=sys.stderr)
        return 3
    print(json.dumps({"counts": metadata["counts"], "output_dir": str(output_dir), "openapi_crosscheck": crosscheck}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except InventoryError as error:
        print(f"generate-rest-inventory: error: {error}", file=sys.stderr)
        raise SystemExit(2)
