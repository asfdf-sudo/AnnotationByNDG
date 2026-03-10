from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, Set, Iterable

from tree_sitter import Parser

try:
    from tree_sitter import Node
except Exception:
    Node = object 

import tree_sitter_java
from tree_sitter import Language, Parser
import os
import javalang
import tree_sitter_java


def _node_text(src: bytes, node: Node) -> str:
    return src[node.start_byte:node.end_byte].decode("utf-8", errors="replace")


def _find_children(node: Node, type_name: str) -> List[Node]:
    return [c for c in node.children if c.type == type_name]


def _first_child(node: Node, type_name: str) -> Optional[Node]:
    for c in node.children:
        if c.type == type_name:
            return c
    return None


def _walk(node: Node) -> Iterable[Node]:
    stack = [node]
    while stack:
        cur = stack.pop()
        yield cur
        stack.extend(reversed(cur.children))


def extract_type_identifier(src: bytes, type_node: Node) -> Optional[str]:
    if type_node is None:
        return None

    t = type_node.type

    if t in ("type", "unannotated_type"):
        for c in type_node.children:
            if c.is_named:
                return extract_type_identifier(src, c)
        return None

    if t == "array_type":
        for c in type_node.children:
            if c.is_named:
                return extract_type_identifier(src, c)
        return None

    if t in ("generic_type", "parameterized_type"):
        for c in type_node.children:
            if c.is_named and c.type not in ("type_arguments", "type_parameters"):
                return extract_type_identifier(src, c)
        txt = _node_text(src, type_node)
        return txt.split("<", 1)[0].split(".")[-1].strip() or None

    if t in ("scoped_type_identifier", "scoped_identifier", "qualified_name"):
        txt = _node_text(src, type_node).strip()
        return (txt.split(".")[-1] if txt else None)

    if t in ("type_identifier", "identifier"):
        return _node_text(src, type_node).strip() or None

    if t in ("integral_type", "floating_point_type", "boolean_type", "void_type"):
        return _node_text(src, type_node).strip() or None

    if t in ("class_type", "interface_type"):
        # find identifier inside
        for c in _walk(type_node):
            if c.type in ("type_identifier", "identifier"):
                return _node_text(src, c).strip() or None
        txt = _node_text(src, type_node).strip()
        return (txt.split("<", 1)[0].split(".")[-1] if txt else None)

    # fallback: try to find identifier token anywhere inside
    for c in _walk(type_node):
        if c.type in ("type_identifier", "identifier"):
            return _node_text(src, c).strip() or None

    # last fallback: use raw text
    txt = _node_text(src, type_node).strip()
    if not txt:
        return None
    # strip generics and qualifiers
    txt = txt.split("<", 1)[0].strip()
    return txt.split(".")[-1] if txt else None


@dataclass
class FieldInfo:
    owner_class: str
    field_name: str
    field_type: str  # normalized identifier (e.g., "User")


def _get_class_name(src: bytes, class_node: Node) -> Optional[str]:
    # class_declaration / interface_declaration / enum_declaration have a name child "identifier"
    name_node = _first_child(class_node, "identifier")
    if name_node:
        return _node_text(src, name_node).strip()
    # some grammars use "type_identifier"
    name_node = _first_child(class_node, "type_identifier")
    if name_node:
        return _node_text(src, name_node).strip()
    return None


def _class_body_node(class_node: Node) -> Optional[Node]:
    # look for class_body
    for c in class_node.children:
        if c.type == "class_body":
            return c
    return None


def _extract_fields_from_class(src: bytes, class_name: str, class_body: Node) -> List[FieldInfo]:
    fields: List[FieldInfo] = []

    for n in _walk(class_body):
        if n.type != "field_declaration":
            continue

        type_node = None
        for c in n.children:
            if c.is_named and c.type in ("type", "unannotated_type", "array_type", "generic_type", "parameterized_type",
                                         "scoped_type_identifier", "type_identifier", "identifier", "class_type", "interface_type"):
                if c.type != "variable_declarator":
                    type_node = c
                    break

        normalized_type = extract_type_identifier(src, type_node) if type_node else None
        if not normalized_type:
            continue
        for vd in n.children:
            if vd.type != "variable_declarator":
                continue
            ident = _first_child(vd, "identifier") or _first_child(vd, "pattern") 
            if ident and ident.type == "identifier":
                field_name = _node_text(src, ident).strip()
            else:
                field_name = None
                for c in _walk(vd):
                    if c.type == "identifier":
                        field_name = _node_text(src, c).strip()
                        break
                if not field_name:
                    continue

            fields.append(FieldInfo(owner_class=class_name, field_name=field_name, field_type=normalized_type))

    return fields


def extract_class_field_type_mapping(
    java_code: str,
    class_names: Set[str],
    include_interfaces: bool = True,
    include_enums: bool = True
) -> Dict[str, str]:
    src = java_code.encode("utf-8")

    JAVA_LANGUAGE = Language(tree_sitter_java.language())

    # 创建解析器
    parser = Parser(JAVA_LANGUAGE)
    tree = parser.parse(src)
    root = tree.root_node

    # collect mapping
    mapping: Dict[str, str] = {}

    # find all class declarations (and optionally interface/enum)
    target_node_types = {"class_declaration"}
    if include_interfaces:
        target_node_types.add("interface_declaration")
    if include_enums:
        target_node_types.add("enum_declaration")

    for node in _walk(root):
        if node.type not in target_node_types:
            continue

        owner = _get_class_name(src, node)
        if not owner:
            continue

        body = _class_body_node(node)
        if not body:
            continue

        fields = _extract_fields_from_class(src, owner, body)
        for f in fields:
            # 是否属于另外一个类：类型在 class_names 且 != owner
            if f.field_type in class_names and f.field_type != owner:
                key = f"{f.owner_class}.{f.field_name}"
                mapping[key] = f.field_type  #加上表示终止符
    print("YYYYYYYYYYYYYYYYYYYY", mapping, class_names)
    return mapping


# # -------------------------
# # Example
# # -------------------------
# if __name__ == "__main__":
#     class_table ={'RowSetProvider', 'SqlRowSetResultSetExtractor'}
#     code = r"""
# public class SqlRowSetResultSetExtractor implements ResultSetExtractor<SqlRowSet> {

# 	private static final RowSetFactory rowSetFactory;

# 	static {
# 		try {
# 			rowSetFactory = RowSetProvider.newFactory();
# 		}
# 		catch (SQLException ex) {
# 			throw new IllegalStateException("Cannot create RowSetFactory through RowSetProvider", ex);
# 		}
# 	}

# 	protected CachedRowSet newCachedRowSet() throws SQLException {
# 		return rowSetFactory.createCachedRowSet();
# 	}

# }
#     """

#     mapping = extract_class_field_type_mapping(code, class_table)
#     for k, v in mapping.items():
#         print(k, "->", v)