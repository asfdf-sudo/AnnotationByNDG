from tree_sitter import Language, Parser
import os
import javalang
import tree_sitter_java
import numpy as np
from collections import deque
import re
#from openai import OpenAI
import json


def parse_java_file(parser, file_path):
    with open(file_path, 'r', encoding='utf-8') as file:
        source_code = file.read().encode("utf8")
    tree = parser.parse(source_code)
    root_node = tree.root_node
    result = {}
    current_class = None
    # 遍历AST节点
    def traverse(node):
        nonlocal current_class
        if node.type == 'class_declaration':
            class_name_node = node.child_by_field_name('name')
            if class_name_node:
                current_class = source_code[class_name_node.start_byte:class_name_node.end_byte].decode('utf-8')
                result[current_class] = {}
        elif node.type == 'method_declaration':
            if current_class is None:
                return
            method_name_node = node.child_by_field_name('name')
            if not method_name_node:
                return
            method_name = source_code[method_name_node.start_byte:method_name_node.end_byte].decode('utf-8')
            result[current_class][method_name] = {
                "return": 0,
                "parameters": {}
            }
            # 检查方法注解（返回值注解）
            for modifiers_node in node.children:
                if modifiers_node.type == "modifiers":
                    for child in modifiers_node.children:
                        if child.type in ['marker_annotation', 'annotation']:
                            annotation_text = source_code[child.start_byte:child.end_byte].decode('utf-8')
                            if '@Nullable' in annotation_text:
                                result[current_class][method_name]["return"] = 1
                                break
            # 检查参数注解
            parameters_node = node.child_by_field_name('parameters')
            if parameters_node:
                for child in parameters_node.children:
                    if child.type == 'formal_parameter':
                        param_name_node = child.child_by_field_name('name')
                        if param_name_node:
                            param_name = source_code[param_name_node.start_byte:param_name_node.end_byte].decode('utf-8')
                            result[current_class][method_name]["parameters"][param_name] = 0
                            for param_modifiers_node in child.children:
                                if param_modifiers_node:
                                    for modifier_child in param_modifiers_node.children:
                                        if modifier_child.type in ['marker_annotation', 'annotation']:
                                            annotation_text = source_code[modifier_child.start_byte:modifier_child.end_byte].decode('utf-8')
                                            if '@Nullable' in annotation_text:
                                                result[current_class][method_name]["parameters"][param_name] = 1
                                                break
        # 递归遍历子节点
        for child in node.children:
            traverse(child)
    traverse(root_node)
    return result

def one_file(java_file_path, output_file, parser):
    if not os.path.exists(java_file_path):
        print(f"错误: 文件 {java_file_path} 不存在")
        return
    annotation_data = parse_java_file(parser, java_file_path)
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(annotation_data, f, indent=4, ensure_ascii=False)
    print(f"注解分析完成，结果已保存到 {output_file}")

def mul_files(file_path, parser):
    for root, dirs, files in os.walk(file_path):
        for file in files:
            if file.endswith(".java"):
                dest_file_path = os.path.join(root, file)
                output_file = os.path.join(root, file[:-5]+".json")
                one_file(dest_file_path, output_file, parser)



JAVA_LANGUAGE = Language(tree_sitter_java.language())
parser = Parser(JAVA_LANGUAGE)
dest_dir = r"file_path"
for dic in os.listdir(dest_dir):
    print("````````")
    dic_path = os.path.join(dest_dir, dic)
    print(dic_path)
    if os.path.isdir(dic_path):
        mul_files(dic_path, parser)
# mul_files(dest_dir, parser)
