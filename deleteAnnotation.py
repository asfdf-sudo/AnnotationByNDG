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
import sys
from pathlib import Path

def build_parser() -> Parser:
    JAVA_LANGUAGE = Language(tree_sitter_java.language())
    parser = Parser(JAVA_LANGUAGE)
    return parser


def is_class_field_annotation(annotation_node) -> bool:
    p = getattr(annotation_node, "parent", None)
    while p is not None:
        if p.type == "field_declaration":
            return True
        p = getattr(p, "parent", None)
    return False

def collect_annotation_nodes_to_remove(root):
    annotations = []
    stack = [root]

    while stack:
        node = stack.pop()
        if node.type in ("annotation", "marker_annotation"):
            # 只删除非字段注解；字段注解保留
            if not is_class_field_annotation(node):
                annotations.append(node)

        for child in node.children:
            stack.append(child)

    return annotations

def remove_nodes_from_code(code: str, nodes):
    code_bytes = code.encode("utf-8")

    nodes = sorted(nodes, key=lambda n: n.start_byte, reverse=True)

    code_list = bytearray(code_bytes)

    for node in nodes:
        start = node.start_byte
        end = node.end_byte

        while end < len(code_list) and code_list[end] in (ord(' '), ord('\t')):
            end += 1
        if end < len(code_list) and code_list[end] == ord('\n'):
            end += 1

        del code_list[start:end]

    return code_list.decode("utf-8")


def process_java_file(file_path: Path, parser: Parser, in_place=True):
    code = file_path.read_text(encoding="utf-8")
    tree = parser.parse(code.encode("utf-8"))
    root = tree.root_node

    annotations_to_remove = collect_annotation_nodes_to_remove(root)

    if not annotations_to_remove:
        return False

    new_code = remove_nodes_from_code(code, annotations_to_remove)

    if in_place:
        file_path.write_text(new_code, encoding="utf-8")
    else:
        new_path = file_path.with_suffix(".no_annotation.java")
        new_path.write_text(new_code, encoding="utf-8")

    return True

def process_project(project_path: Path, in_place=True):
    parser = build_parser()
    total = 0
    modified = 0

    for java_file in project_path.rglob("*.java"):
        total += 1
        changed = process_java_file(java_file, parser, in_place=in_place)
        if changed:
            modified += 1
            print(f"✔ Removed annotations (except field annotations): {java_file}")

    print("\n====== DONE ======")
    print(f"Total java files: {total}")
    print(f"Modified files  : {modified}")


# ----------------------------------------
# 主函数
# ----------------------------------------
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python remove_java_annotations.py <java_project_path> [--copy]")
        print("")
        print("Default: modify files in place")
        print("--copy  : generate *.no_annotation.java files")
        sys.exit(1)

    project_dir = Path(sys.argv[1])
    if not project_dir.exists():
        print("Project path does not exist.")
        sys.exit(1)

    in_place = True
    if len(sys.argv) > 2 and sys.argv[2] == "--copy":
        in_place = False


    process_project(project_dir, in_place=in_place)
