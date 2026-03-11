from __future__ import annotations
from dataclasses import dataclass
from typing import Dict, List, Optional, Set, Tuple

from tree_sitter import Language, Parser
import os
import javalang
import tree_sitter_java
import numpy as np
from collections import deque
import re
import json
import ast
import pandas as pd
import json
import time

def build_java_parser() -> Parser:
    JAVA_LANGUAGE = Language(tree_sitter_java.language())
    parser = Parser(JAVA_LANGUAGE)
    return parser


def node_text(code_bytes: bytes, node: Node) -> str:
    return code_bytes[node.start_byte:node.end_byte].decode("utf-8")


@dataclass
class Scope:
    parent: Optional["Scope"]
    symbols: Dict[str, str]  # var_name -> type_name

    def define(self, name: str, type_name: str) -> None:
        self.symbols[name] = type_name

    def resolve(self, name: str) -> Optional[str]:
        cur: Optional[Scope] = self
        while cur is not None:
            if name in cur.symbols:
                return cur.symbols[name]
            cur = cur.parent
        return None


def extract_declared_type_name(code: bytes, type_node: Optional[Node]) -> Optional[str]:
    if type_node is None:
        return None

    t = type_node.type

    if t in ("type_identifier", "identifier"):
        return node_text(code, type_node)

    if t == "scoped_type_identifier":
        return node_text(code, type_node)

    if t == "generic_type":
        base = type_node.child_by_field_name("type")
        base_name = extract_declared_type_name(code, base)
        return base_name

    if t == "array_type":
        element = type_node.child_by_field_name("element")
        return extract_declared_type_name(code, element)

    if t == "annotated_type":
        inner = type_node.child_by_field_name("type")
        return extract_declared_type_name(code, inner)

    if t == "integral_type" or t == "floating_point_type" or t == "boolean_type" or t == "void_type":
        return None

    return node_text(code, type_node).strip() or None


def collect_declarations_into_scope(code: bytes, node: Node, scope: Scope) -> None:
    nt = node.type

    # 形参：formal_parameter / receiver_parameter
    if nt == "formal_parameter":
        type_node = node.child_by_field_name("type")
        name_node = node.child_by_field_name("name")
        type_name = extract_declared_type_name(code, type_node)
        if type_name and name_node and name_node.type == "identifier":
            scope.define(node_text(code, name_node), type_name)
        return

    # enhanced_for_statement: (type identifier : expr)
    if nt == "enhanced_for_statement":
        type_node = node.child_by_field_name("type")
        name_node = node.child_by_field_name("name")
        type_name = extract_declared_type_name(code, type_node)
        if type_name and name_node and name_node.type == "identifier":
            scope.define(node_text(code, name_node), type_name)
        return

    # 局部变量声明：local_variable_declaration
    if nt == "local_variable_declaration":
        type_node = node.child_by_field_name("type")
        type_name = extract_declared_type_name(code, type_node)
        if not type_name:
            type_text = node_text(code, type_node) if type_node else ""
            if type_text == "var":
                type_name = "var"  
        # declarator(s)
        for child in node.children:
            if child.type == "variable_declarator":
                name_node = child.child_by_field_name("name")
                init_node = child.child_by_field_name("value")
                if name_node and name_node.type == "identifier":
                    var_name = node_text(code, name_node)
                    final_type = type_name

                    if final_type == "var" and init_node is not None:
                        inferred = infer_type_from_initializer_new(code, init_node)
                        if inferred:
                            final_type = inferred

                    if final_type and final_type != "var":
                        scope.define(var_name, final_type)
        return

    if nt == "field_declaration":
        type_node = node.child_by_field_name("type")
        type_name = extract_declared_type_name(code, type_node)
        if not type_name:
            return
        for child in node.children:
            if child.type == "variable_declarator":
                name_node = child.child_by_field_name("name")
                if name_node and name_node.type == "identifier":
                    scope.define(node_text(code, name_node), type_name)
        return



def infer_type_from_initializer_new(code: bytes, init_node: Node) -> Optional[str]:
    # 仅做 new 表达式推断：new X(...)
    # object_creation_expression: type + arguments + body?
    if init_node.type == "object_creation_expression":
        tnode = init_node.child_by_field_name("type")
        return extract_declared_type_name(code, tnode)
    return None


def resolve_owner_type_for_field_access(
    code: bytes,
    field_access_node: Node,
    scope: Scope,
    current_class_name: str,
) -> Optional[str]:
    obj = field_access_node.child_by_field_name("object")
    if obj is None:
        return None

    # this.x
    if obj.type in ("this", "this_expression"):
        return current_class_name
    if obj.type in ("super", "super_expression"):
        return current_class_name  # 或者 "super"

    # a.x
    if obj.type == "identifier":
        name = node_text(code, obj)
        t = scope.resolve(name)
        if t is not None:
            return t
        return name
    if obj.type in ("type_identifier", "scoped_type_identifier"):
        return node_text(code, obj)
    return None


def extract_field_name_from_field_access(code: bytes, field_access_node: Node) -> Optional[str]:
    name_node = field_access_node.child_by_field_name("name")
    if name_node is None:
        # 有的 grammar 里 field 可能叫 field
        name_node = field_access_node.child_by_field_name("field")
    if name_node is None:
        return None
    if name_node.type == "identifier":
        return node_text(code, name_node)
    return node_text(code, name_node).strip() or None


def collect_field_accesses_in_subtree(code: bytes, root: Node) -> List[Node]:
    out: List[Node] = []
    stack = [root]
    while stack:
        n = stack.pop()
        if n.type == "field_access":
            out.append(n)
        # 注意：这里也可以把 array_access / method_invocation 的情况加进去
        for c in reversed(n.children):
            stack.append(c)
    return out


def extract_left_field_accesses(code: bytes, root: Node) -> List[Node]:
    left_nodes: List[Node] = []
    stack = [root]
    while stack:
        n = stack.pop()
        nt = n.type

        if nt == "assignment_expression":
            left = n.child_by_field_name("left")
            if left is not None:
                left_nodes.extend(collect_field_accesses_in_subtree(code, left))

        if nt == "update_expression":
            # ++x / x++ / --x / x--
            arg = n.child_by_field_name("argument")
            if arg is not None:
                left_nodes.extend(collect_field_accesses_in_subtree(code, arg))

        for c in reversed(n.children):
            stack.append(c)

    return left_nodes


@dataclass
class AnalysisResult:
    all_class_fields: List[str]         
    left_class_fields: List[str]    
    ast_root_type: str              


def analyze_java_with_treesitter(
    java_code: str,
    current_class_name: str,
) -> AnalysisResult:
    code_bytes = java_code.encode("utf-8")
    parser = build_java_parser()
    tree = parser.parse(code_bytes)
    root = tree.root_node

    global_scope = Scope(parent=None, symbols={})

    all_fields: Set[str] = set()
    left_field_nodes = extract_left_field_accesses(code_bytes, root)
    left_ranges: Set[Tuple[int, int]] = {(n.start_byte, n.end_byte) for n in left_field_nodes}
    left_fields: Set[str] = set()

    def is_scope_boundary(node: Node) -> bool:
        return node.type in (
            "program",
            "class_declaration",
            "interface_declaration",
            "enum_declaration",
            "method_declaration",
            "constructor_declaration",
            "block",
            "for_statement",
            "enhanced_for_statement",
            "while_statement",
            "do_statement",
            "try_statement",
            "catch_clause",
            "switch_block",
            "lambda_expression",
        )

    def walk(node: Node, scope: Scope) -> None:
        if node is not root and is_scope_boundary(node):
            scope = Scope(parent=scope, symbols={})
        collect_declarations_into_scope(code_bytes, node, scope)
        if node.type == "field_access":
            owner = resolve_owner_type_for_field_access(code_bytes, node, scope, current_class_name)
            field = extract_field_name_from_field_access(code_bytes, node)
            if owner and field:
                key = f"{owner}.{field}"
                all_fields.add(key)

                if (node.start_byte, node.end_byte) in left_ranges:
                    left_fields.add(key)

        # DFS
        for c in node.children:
            walk(c, scope)

    walk(root, global_scope)

    return AnalysisResult(
        all_class_fields=sorted(all_fields),
        left_class_fields=sorted(left_fields),
        ast_root_type=root.type,
    )


