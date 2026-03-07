from tree_sitter import Language, Parser
import os
import javalang
import tree_sitter_java
import numpy as np
from collections import deque
import re
from openai import OpenAI
import json
import ast
import pandas as pd
import json
import time
from litellm import completion
import LeftField
# 加载语言库
JAVA_LANGUAGE = Language(tree_sitter_java.language())

# 创建解析器
parser = Parser(JAVA_LANGUAGE)

class First_NullAST:
    def __init__(self,  java_code, func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation, field_name, field_anno, N, func_param_list, func_param_id, func_type, func_param_shunxv):
        self.func_param_shunxv = func_param_shunxv
        self.func_name_list = func_name_list                        # 函数名称 --> 序号
        self.code_bytes = java_code.encode("utf8")
        self.N = N                                                  # 累计序号，建图使用
        # self.func_map = func_map                                    # 更新后构建目标为依赖图包括field、func、global
        self.parsed = parser.parse(bytes(java_code, "utf8"))        # AST
        self.class_now = ""                                         
        #self.func_now = ""
        # self.func_dict = func_dict                                  # FUNC NAME --> FUNC ID
        self.class_name = class_name                                # CLASS NAME LIST 暂时不区分public/private
        self.func_to_code = func_to_code                            # FUNC NAME --> FUNC CODE
        self.java_code = java_code
        self.field_name = field_name                                # FIELD NAME LIST --> 序号
        self.field_anno = field_anno                                # FIELD ANNOTATION LIST
        self.func_param_annotation = func_param_annotation
        self.func_return_annotation = func_return_annotation
        self.func_param_list = func_param_list                      #  FUNC_NAME  --> PARAM
        self.func_param_id = func_param_id                          # parameter  --> id
        self.func_type = func_type

    # NODE --> RENTURN CLASS NAME
    def get_name(self, node, ):
        for child in node.children:
            if child.type == "identifier":
                return self.code_bytes[child.start_byte:child.end_byte].decode()
        return "UnknownClass"
    
    # 提取参数部分
    def get_parameters(self, method_node, class_func_name_now):
        parameters = []
        formal_params_node = None
        for child in method_node.children:
            if child.type == "formal_parameters":
                formal_params_node = child
                break
        if not formal_params_node:
            return []
        for param_node in formal_params_node.children:
            if param_node.type == "formal_parameter":
                param_type = "unknown"
                param_name = "unknown"
                is_varargs = False
                for child in param_node.children:
                    if child.type in ["type_identifier", "array_type", "generic_type", "void_type"]:
                        param_type = self.parse_type(child, self.code_bytes)
                    # 处理可变参数（如 String... args）
                    elif child.type == "varargs_parameter":
                        param_type = self.parse_type(child.children[0], self.code_bytes) + "..."
                        param_name = self.code_bytes[child.children[1].start_byte:child.children[1].end_byte].decode()
                    # 处理参数名
                    elif child.type == "identifier":
                        param_name = self.code_bytes[child.start_byte:child.end_byte].decode()
                #parameters.append((param_type, param_name))
                parameters.append((param_node.child_by_field_name('type').type, param_name))
                self.func_type[class_func_name_now+param_name] = param_type
                
        return parameters

    def parse_type(self, node, code_bytes):
        """递归解析复杂类型（复用返回类型解析逻辑）"""
        if node.type == "array_type":
            element_type = self.parse_type(node.children[0], code_bytes)
            dimensions = "[]" * (len(node.children[1].children) // 2)
            return f"{element_type}{dimensions}"
        elif node.type == "generic_type":
            base_type = self.parse_type(node.children[0], code_bytes)
            type_args = []
            for child in node.children:
                if child.type == "type_arguments":
                    for arg_node in child.children:
                        if arg_node.type in ["type_identifier", "generic_type"]:
                            type_args.append(self.parse_type(arg_node, code_bytes))
            return f"{base_type}<{', '.join(type_args)}>"
        else:
            return code_bytes[node.start_byte:node.end_byte].decode()
    
    # 统计函数相关信息 + 类的相关信息， 返回函数名称列表
    def count_func_name(self):
        for member in self.parsed.root_node.children:
            self.visit_func_name(member)
        return self.func_name_list, self.field_name, self.class_name, self.N, self.field_anno
    
    def visit_func_name(self, node):
        if node.type == "class_declaration":
            for cl in node.children:
                if cl.type == "identifier":
                    self.class_now = self.code_bytes[cl.start_byte : cl.end_byte].decode()
                    self.class_name.append(self.class_now)

                elif cl.type == "class_body":
                    for member in cl.children:
                        if member.type == "method_declaration":
                            func_type = member.child_by_field_name('type').type
                            name = self.get_name(node) + "." + self.code_bytes[member.start_byte : member.child_by_field_name('parameters').end_byte].decode()
                            nonnull_type = ["void_type", 'integral_type', "floating_point_type", 'boolean_type']
                            # print(func_type)
                            if self.func_name_list.get(name) is None:
                                self.func_name_list[name] = self.N
                                self.N = self.N + 1
                                # return_type = ""
                                # if member.return_type == None:
                                #     return_type = 'void'
                                # else:
                                #     return_type = member.return_type.name
                                self.func_to_code[name] = self.code_bytes[member.start_byte: member.end_byte].decode()
                                # print(self.code_bytes[member.start_byte: member.end_byte].decode())
                                if func_type in nonnull_type:
                                    self.func_return_annotation[name] = '@NotNull'
                                else:
                                    self.func_return_annotation[name] = '@NonNull'
                                param = []
                                func_para = self.get_parameters(member, name)
                                # print(func_para)
                                for i in range(len(func_para)):
                                    if func_para[i][0] in nonnull_type:
                                        param.append("@NotNull")
                                    else:
                                        param.append('@NonNull')
                                    #self.func_param_shunxv[name + '.' + func_para[i]] = i
                                    self.func_param_id[name+'.'+str(i)] = self.N
                                    self.N = self.N + 1
                                # print(param)
                                self.func_param_annotation[name] = param
                                self.func_param_list[name] = func_para
                        elif member.type == "field_declaration":
                            anno = ""
                            for fie in member.children:
                                # 考虑已经加过@Nullable注解的情况
                                if fie.type == "modifiers":
                                    for ann in fie.children:
                                        for s in ann.children:
                                            if "@Nullable" in self.code_bytes[s.start_byte: s.end_byte].decode():
                                                anno = "@Nullable"
                                elif fie.type == "array_type":
                                    if "@Nullable" in self.code_bytes[fie.start_byte: fie.end_byte].decode():
                                        anno = "@Nullable"
                                elif fie.type == "variable_declarator":
                                    name = ""
                                    value = "non"
                                    for v_name in fie.children:
                                        if v_name.type == "identifier":
                                            name = self.code_bytes[v_name.start_byte : v_name.end_byte].decode()
                                        # 考虑初始赋值为空的情况
                                        elif v_name.type == "null_literal":
                                            value = "null"
                                    if name != "":
                                        id = self.N
                                        self.N = self.N + 1
                                        self.field_name[self.class_now + "." + name] = id
                                        #if anno != "" or value == "null":
                                        if value == "null":
                                            self.field_anno[self.class_now + "." + name] = anno
                                        else:
                                            self.field_anno[self.class_now + "." + name] = "@NonNull"
                        elif member.type == "constructor_declaration":
                            name = self.get_name(node) + "." + self.code_bytes[member.start_byte : member.child_by_field_name('parameters').end_byte].decode()
                            if self.func_name_list.get(name) is None:
                                self.func_name_list[name] = self.N
                                self.N = self.N + 1
                                self.func_to_code[name] = self.code_bytes[member.start_byte: member.end_byte].decode()
                                self.func_return_annotation[name] = '@NotNull'
                                param = []
                                func_para = self.get_parameters(member, name)
                                # print(func_para)
                                nonnull_type = ["void_type", 'integral_type', "floating_point_type", 'boolean_type']
                                for i in range(len(func_para)):
                                    if func_para[i][0] in nonnull_type:
                                        param.append("@NotNull")
                                    else:
                                        param.append('@NonNull')
                                    #self.func_param_shunxv[name + '.' + func_para[i]] = i
                                    self.func_param_id[name+'.'+str(i)] = self.N
                                    self.N = self.N + 1
                                # print(param)
                                self.func_param_annotation[name] = param
                                self.func_param_list[name] = func_para
                        # 递归遍历
                        self.visit_func_name(member)
    def get_func(self):
        return self.func_name_list, self.class_name, self.func_to_code, self.func_param_annotation, self.func_return_annotation
    
    def get_field(self):
        return self.field_name, self.field_anno, self.N
    

'''
第二次遍历, 检查一下内容
1. 与类的字段操作相关的代码，用于类的字段的析取
2. 提取当前函数所在的相关依赖，借助大模型
'''
class Second_NullAST:
    def __init__(self, func_name_list, class_name, func_to_code, field_name, field_anno, N, dependency_map, func_param_list, func_param_annotation, func_return_annotation, func_param_id, func_param_shunxv, field_to_class):
        self.N = N
        self.dependency_map = dependency_map
        self.func_to_code = func_to_code
        self.class_name = class_name
        self.field_name = field_name
        self.field_anno = field_anno
        self.func_name_list = func_name_list
        self.func_param_list = func_param_list
        self.func_param_annotation = func_param_annotation
        self.func_return_annotation = func_return_annotation
        self.func_param_id = func_param_id
        self.func_param_shunxv = func_param_shunxv # dict{class_name.func_name.param_name} = i ; i=第几个参数
        self.field_to_class = field_to_class

    def create_map(self):
        
        field_allexpr = {}
        for field in self.field_name:
            field_allexpr[field] = []
        func_count = 0
        func_count_filter = 0
        input_file = r"C:\Users\Asfdf\Desktop\annoation\Lognew\log_tmp_askai.txt"
        ai_dict = {}
        with open(input_file, "r", encoding="utf-8") as file:
            lines = file.readlines()
            key = ''
            value = ''
            flag = False
            for line in lines:
                if "===================" in line[:len('======================')]:
                    ai_dict[key] = value
                    value = ''
                    flag = True
                    if '=====================' in line[-len('======================'):]:
                        flag = False
                    key = line.strip('\n').strip('=')
                elif '=====================' in line[-len('======================'):]:
                    flag = False
                    key = key + '\n' + line.strip('\n').strip('=')
                else:
                    if flag:
                        key = key + line
                    else:
                        value = value + line
            file.close()
        for func in self.func_to_code:
            with open(f"Lognew/log_tmp_annotation.txt", "w", encoding="utf-8") as file:
                file.write("********************field_annotation信息********************\n")
                file.write(str(self.field_anno))
                file.write("\n")
                file.write("********************func_param_annotation信息********************\n")
                file.write(str(self.func_param_annotation))
                file.write("\n")
                file.write("********************func_return_annotation信息********************\n")
                file.write(str(self.func_return_annotation))
                file.write("\n")
                file.write("********************dependecy_map信息********************\n")
                file.write(str(self.dependency_map))
                file.write("\n")
                file.close()
            func_code = self.func_to_code[func]
            func_ast = parser.parse(bytes(func_code, "utf8"))
            class_now = func.split('.')[0]
            print(f"*************处理函数{func}**********************")
            # ??
            func_count = func_count + 1
            field_tmp = []
            res = LeftField.analyze_java_with_treesitter(func_code, current_class_name=class_now)
            field_tmp = res.left_class_fields
            # for fn in self.field_name:
            #     na = fn.split('.')
            #     if na[-1] in func_code:
            #         field_tmp[fn] = self.field_name[fn]
            print("提取出的field字段为：")
            print(field_tmp)
            flag = True
            if field_tmp != []:
                flag = False
            for i in self.func_param_annotation[func]:
                if i != "@NotNull":
                    flag = False
            if self.func_return_annotation[func] != "@NotNull":
                flag = False
            if flag:
                continue
            func_count_filter = func_count_filter + 1
            if func in ai_dict:
                if "过滤" in ai_dict[func]:
                    ai_dict[func] = ai_dict[func].split("过滤")[0]
                try:
                    ai_resp = eval(ai_dict[func])
                    print(ai_dict[func])
                    try:
                        ai_resp = ai_resp["Result"]
                    except:
                        ai_resp = ai_resp
                    print("++++++++++++++已被处理过+++++++++++++++++++")
                except:
                    print("sssssssssss")
                    ai_resp = self.ask_ai(func_code, field_tmp, self.func_name_list, class_now, func)
            else:
                ai_resp = self.ask_ai(func_code, field_tmp, self.func_name_list, class_now, func)

            #dependency = self.openai_denpendency(func_code, self.field_name, class_now, self.func_name_list) # 改：null作为依赖之一
            dependency = ai_resp["return"]
            print(f"~返回值依赖为：{dependency}")
            if dependency["nullable"] != "false" and dependency["nullable"] != "False":
                if dependency["nullable"] == "True" or dependency["nullable"] == "true":
                    self.func_return_annotation[func] = "@Nullable"
                else:
                    for de in dependency["dependencies"]:
                        # print("YYYYYYYYYYYYYYYYYYYYY   ", self.field_to_class)
                        flag2 = ""
                        for f in self.field_to_class:
                            tmp_fi = "@"+de
                            if f in tmp_fi:
                                flag2 = self.field_to_class[f] + tmp_fi[len(f) : ]
                                break
                        if flag2 != "":
                            de = self.field_to_class[f] + tmp_fi[len(f) : ]
                            # print("YYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY         ", de)
                        if "null" in dependency["dependencies"]:
                            self.func_return_annotation[func] = "@Nullable"
                        elif de in self.func_name_list:
                            self.dependency_map[self.func_name_list[de]][self.func_name_list[func]] = 1         #  param1  if param1!=null  method(param1)
                        elif class_now + de in self.func_name_list:
                            self.dependency_map[self.func_name_list[class_now + de]][self.func_name_list[func]] = 1
                        elif de in self.field_name:
                            self.dependency_map[self.field_name[de]][self.func_name_list[func]] = 1
                        elif class_now + de in self.field_name:
                            self.dependency_map[self.field_name[class_now + de]][self.func_name_list[func]] = 1
                        else:  # ???？？？并没有考虑全局变量的问题
                            for par in self.func_param_list:
                                if de == par[1]:
                                    self.dependency_map[self.func_param_list[par]][func] = 1 # 不同函数中相同参数名的混淆问题
            else:
                self.func_return_annotation[func] = "@NotNull"

            #field_expr, prompt_tokens, completion_tokens, total_tokens = self.openai_exprnull(field_tmp, func_code, class_now, func)
            field_expr = ai_resp["fields"]
            print(f"~类的字段依赖为：{field_expr}")
            # else:
            #     # 没有任何依赖的情况
            #     resp, prompt_tokens, completion_tokens, total_tokens = self.ask_ai_return(func_code)
            #     if resp == "yes":
            #         self.func_return_annotation[func] = "@Nullable"
            
            # field_expr = self.analyze_field_modifications(func_code, func_ast, self.field_name, class_now)
            # for field in field_expr:
            #     field_allexpr[field].append(field_expr[field])
            #field_expr = eval(field_expr)
            for field in field_expr:
                # print(field_expr)
                # print(field)
                expr = field_expr[field]
                try:
                    if expr["nullable"] == "True":
                        self.field_anno[field] = "@Nullable"
                    elif expr["nullable"] == "dependencies":
                        for x in expr['dependencies']:
                            flag = False
                            for i in self.func_to_code:
                                if x == i:
                                    idx = self.func_name_list[i]
                                    self.dependency_map[idx][self.field_name[field]] = 1  # 与其他函数返回值有关
                                    flag = True
                                    break
                            if not flag:
                                flag2 = False
                                for i in self.field_name:
                                    if x == i:
                                        idx = self.field_name[i]
                                        self.dependency_map[idx][self.field_name[field]] = 1
                                        flag2 = True
                                        break
                                if not flag2:
                                    for i in self.func_param_id:
                                        if x == i:
                                            idx = self.func_param_id[i]
                                            self.dependency_map[idx][self.field_name[field]] = 1
                                            break
                except:
                    with open("Lognew\\error.txt", "a", encoding="utf-8") as file:
                        file.write("field:::    "+str(field)+"\n")
                        file.close()
                    print(dependency_json)                    
            #dependency_json, prompt_tokens, completion_tokens, total_tokens = self.ask_ai_param(class_now, func_code)
            #dependency_json = dependency_json.strip("'''").strip('java')
            dependency_json = ai_resp["parameters"]
            print(f"~参数依赖为：{dependency_json}")
            try:
                dependency_json = ast.literal_eval(dependency_json)
            except:
                #dependency_json = ast.literal_eval("{\n"+dependency_json)
                print(dependency_json)
            try:  # 将所有情况分为nullable，nonnull和notnull三类，nullable表示可以为空，nonnull表示默认状态，可以被修改为空，notnull表示一定不为空不可被修改
                for dependency_param in dependency_json:
                    if dependency_param["nullable"] == "True" or dependency_param["nullable"] == "true":
                        self.func_param_annotation[func][dependency_param["id"]] = '@Nullable'
                    elif dependency_param["nullable"] == "False" or dependency_param["nullable"] == "false":
                        self.func_param_annotation[func][dependency_param["id"]] = '@NotNull'
                    else:
                        for depen in dependency_param["dependencies"]:
                            if depen in self.field_name:
                                self.dependency_map[self.field_name[depen]][func+'.'+str(dependency_param["id"])] = 1
                            elif depen in self.func_param_list:
                                self.dependency_map[self.func_param_id[depen]][func+'.'+str(dependency_param["id"])] = 1
            except:
                with open("Lognew\\error.txt", "a", encoding="utf-8") as file:
                    file.write(str(dependency_json)+"\n")
                    file.close()
                print(dependency_json)
                    #param_anno = param_anno + 1
        with open(f"Lognew/log_tmp_askai.txt", "a", encoding="utf-8") as file:
            file.write("********************过滤器信息********************\n")
            file.write("过滤前函数数目：" + str(func_count) + '\n')
            file.write("过滤后函数数目：" + str(func_count_filter) + '\n')
            file.close()
        return self.dependency_map
    def propagate_nullable_marks(self, dependency_map):
        """
        传播@Nullable标记
        
        参数:
        dependency_map: 二维矩阵，dependency_map[x][y]==1表示x到y有边
        node_anno: 节点标记列表，node_anno[x]=="@Nullable"表示节点x有@Nullable标记
        
        返回:
        更新后的node_anno列表
        """
        n = self.N
        node_anno  = ["@NonNull" for i in range(self.N+100)]#np.full((1, n), np.nan)
        flag = [0 for i in range(self.N+100)]
        print("++++++++++",self.field_anno)
        for field in self.field_name:
            id = self.field_name[field]
            node_anno[id] = self.field_anno[field]
        #print(node_anno)
        for func in self.func_name_list:
            id = self.func_name_list[func]
            flag[id] = 1
            node_anno[id] = self.func_return_annotation[func]
        for func in self.func_param_list:
            param = self.func_param_list[func]
            for p in range(len(param)):
                param_name = func + '.' + str(p)
                id = self.func_param_id[param_name]
                node_anno[id] = self.func_param_annotation[func][p]

   
        # 使用队列进行BFS遍历
        queue = deque()
        
        # 初始化：将所有已有@Nullable标记的节点加入队列
        for i in range(n):
            if node_anno[i] == "@Nullable":
                queue.append(i)
        
        # BFS传播标记
        while queue:
            current = queue.popleft()
            # 遍历当前节点的所有邻居
            for neighbor in range(n):
                if dependency_map[current][neighbor] == 1:
                    # 如果邻居还没有@Nullable标记，则传播标记并加入队列
                    if node_anno[neighbor] == "@NonNull":
                        if flag[neighbor]:
                            node_anno[neighbor] = "@Nullable"
                            queue.append(neighbor)
                        else:
                            f = True
                            for i in range(len(dependency_map[neighbor])):   # 处理参数部分的不同逻辑
                                if dependency_map[neighbor][i]==1 and node_anno[i] != "@Nullable":
                                    f = False
                                    break
                            if f:
                                node_anno[neighbor] = "@Nullable"
                                queue.append(neighbor)                               

        for field in self.field_name:
            id = self.field_name[field]
            self.field_anno[field] = node_anno[id]
        for func in self.func_name_list:
            id = self.func_name_list[func]
            self.func_return_annotation[func] = node_anno[id]
        for func in self.func_param_list:
            param = self.func_param_list[func]
            for p in range(len(param)):
                param_name = func + '.' + str(p)
                id = self.func_param_id[param_name]
                self.func_param_annotation[func][p] = node_anno[id]
        return self.field_anno, self.func_return_annotation, self.func_param_annotation

    def ask_ai(self, func_code, field_list, func_list, class_now, func):
        start_time = time.time()
        os.environ["OPENAI_API_KEY"] = "sk-key"
        prompt=f"""
## System Role
You are an expert in Static Application Security Testing (SAST) and Data Flow Analysis for Java. Your task is to perform nullability modeling on a provided Java method.

## Task Description
Analyze the provided func_code to determine nullability and data flow dependencies for:
- Function Parameters: Usage analysis (can they be null?).
- Return Value: Data flow analysis (can it return null?).
- Class Fields: Assignment analysis (is the field modified to a null or nullable value? Or it must not be empty?).

## Input Data
- func_code: The Java method source code.
- class_now: The class name containing this method.
- field_list: A list of non-primitive class fields (format: "ClassName.fieldName").

## Output Format
Return only[think:"Your think progress", Result: a valid JSON object] . Do not include markdown formatting (like ```json) or conversational text. The JSON must adhere to this schema:
[think:"Concise reasoning for the analysis...",
Result:
{{
  "parameters": [
    {{
      "name": "paramName",
      "id": 0,                        // Index starting at 0
      "nullable": "True|False|Depend",
      "dependencies": []              // List of strings if "Depend"
    }}
  ],
  "return": {{
    "nullable": "True|False|Depend",
    "dependencies": []
  }},
  "fields": {{                         // Key is the field name
    "ClassName.fieldName": {{
        "nullable": "True|False|Depend",
        "dependencies": []
    }}
  }}
}}
]
## Modeling Rules
1. Parameter Nullability
Analyze how parameters are used within the method body. The analysis stops until the end of the method or when the parameter is reassigned.
* Notice: The judgment object of the null operation must be the parameter x itself, rather than other values within the structure of parameter x, eg if x.field!=null refers to the use of x, rather than the checking of whether x is null.x.field!=null should return False.
    eg. For if x.field ! The null statement follows a basic logic of calling the line on x to obtain the field of x, and then checking if the field is null.
- False:[ The parameter is directly dereferenced without a check (e.g., p.toString()), or passed to a method known to require non-null.] or [eg. s.method() if the parameter x is empty and this causes a null pointer exception in the function, then the parameter x corresponds to false].
- True: [And when the parameters are empty, it will not trigger a null pointer exception.] and [ The parameter is not used, or it is explicitly checked whether the parameter x was empty before usage (e.g., if (p != null)) and dereference.]
- Depend: The parameter is passed directly to another method or assigned to a field/return without dereferencing or checking.
    * Dependency Format: "ClassName.methodName.paramIndex" (e.g., "TestClass2.helperMethod.1").
Note: You only need to model non-primitive parameters, and primitive parameters will not be output. If all parameters are primitive, output "parameters": [].
Note that if both True and False are present in the function, the value of False takes precedence over True.

2. Return Value Nullability
Analyze the data flow of return statements.
- False: The method returns a primitive type or void, or the method always returns a simplified non-null object (e.g., literals like "string" or new Object()).
- True: The method has at least one path that explicitly returns null.
- Depend: The return value comes from another method call, a field, or a parameter. Dependency Format:
  * Method call: "ClassName.methodName"
  * Field access: "ClassName.fieldName"
  * Parameter: "ClassName.currentMethodName.paramIndex"

3. Class Field Modeling
Strictly analyze assignment/modification statements targeting the fields in field_list. Do not consider field usage (reading/dereferencing).
- True: The field is explicitly assigned null on any path (e.g., this.field = null).
- False: The field is assigned a known non-null value (e.g., this.field = "constant" or this.field = new Object()).
- Depend: The field is assigned a value from an external source (parameter, method return, or another field). Dependency Format:
  * From Parameter: "ClassName.currentMethodName.paramIndex"
  * From Method: "ClassName.methodName"
  * From Field: "ClassName.otherFieldName"


## Example
**Input:**
  func_code: "public String test(String s){{ if(s!=null) {{field1=TestClass2.helperMethod(1, s); return null;}} else {{field1=s; return s;}} }}",
  class_now: "TestClass",
  field_list: ["TestClass.field1"],
**Expected Output:**
think: "Param 's' is checked for null, so it allows null input; parameter "flag" is primitive thus ignored. Return can be explicitly null. Field1 is assigned the result of helperMethod OR parameter 's', so it depends on those sources.",
Result:
{{ "parameters": [ {{ "name": "s", "id": 0, "nullable": "True", "dependencies": [] }} ], "return": {{ "nullable": "True", "dependencies": [] }}, "fields": {{ "TestClass.field1": {{ "nullable": "Depend", "dependencies": [ "TestClass2.helperMethod", "TestClass.test.0" ] }} }} }}
Input:
  func_code: {func_code}
  class_now: {class_now}
  field_list: {list(field_list)}
  func_list: {list(func_list)}
    """
        response = completion(
            model="deepseek/deepseek-chat",  # DeepSeek模型
            messages=[
                {"role": "system", "content": "You are an expert in the field of program analysis. You can read in the java code and analyze it. Your main task is to read in the code and related information, determine the dependencies related to whether the method's return value is null, only return the result using a list.Only return the result, do not present the thought process"},
                {"role": "user", "content": prompt},
            ],
            api_key="sk—key",  # 或者使用环境变量
            api_base="https://api.deepseek.com",  # DeepSeek API基础URL
            temperature=0.3,  # 较低的温度以获得更确定性的输出
            response_format={"type": "json_object"},  # 要求JSON格式输出
            # max_tokens=8000
        )
        
        resp = response.choices[0].message.content
        usage = response.get("usage", {})
        prompt_tokens = usage.get("prompt_tokens", 0)
        completion_tokens = usage.get("completion_tokens", 0)
        total_tokens = usage.get("total_tokens", 0)
        end_time = time.time()
        elapsed_time = end_time - start_time
        df_existing = pd.read_excel('Lognew\\token.xlsx')
        add_data = {"prompt_tokens":[prompt_tokens], "completion_tokens":[completion_tokens], "total_tokens":[total_tokens], "time":[elapsed_time]}
        df_new = pd.DataFrame(add_data)
        df_combined = pd.concat([df_existing, df_new], ignore_index=True)
        with pd.ExcelWriter('Lognew\\token.xlsx', engine='openpyxl', mode='a', if_sheet_exists='replace') as writer:
            df_combined.to_excel(writer, sheet_name='Sheet1', index=False)
        with open(f"Lognew/log_tmp_askai.txt", "a", encoding="utf-8") as file:
            file.write(f"==================={func}======================"+'\n')
            file.write(resp+'\n')
            file.close()
        print(resp)
        if resp == "":
            return {"parameters":{}, "return": {"nullable": "False", "dependencies": []}, "fields": {}}
        try:
            return eval(resp)["Result"]
        except:
            return eval(resp+"\"}")["Result"]
            # except:
            #     return eval()
def get_name(code_bytes, node):
    for child in node.children:
        if child.type == "identifier":
            return code_bytes[child.start_byte:child.end_byte].decode()
    return "UnknownClass"
def add_annotation(code_original, func_param_annotation, func_return_annotation, path):
    code_AST = parser.parse(bytes(code_original, "utf8"))
    code_original = code_original.encode("utf8")
    code = ""
    func_name = []
    start_by = {}
    func_param_xy = {}
    for child in code_AST.root_node.children:
        if child.type == "class_declaration":
            cl_name = get_name(code_original, child)
            for cl in child.children:
                if cl.type == "class_body":
                    for member in cl.children:
                        if member.type == "method_declaration":
                            fun_name = get_name(code_original, member)
                            name = cl_name + "." + code_original[member.start_byte : member.child_by_field_name('parameters').end_byte].decode("utf8")
                            func_name.append(name)
                            param = []
                            if member.child_by_field_name('body') == None:
                                func_return_annotation[name] = "@NonNull"
                                for i in range(len(func_param_annotation[name])):
                                    func_param_annotation[name][i] = "@NonNull"
                            memtype = member.child_by_field_name('type')
                            if memtype != None  and memtype.type == 'array_type':
                                start_by[name] = memtype.child_by_field_name('dimensions').start_byte
                            else:
                                if memtype != None:
                                    start_by[name] = memtype.start_byte
                                else:
                                    start_by[name] = member.start_byte
                            for son in member.children:
                                if son.type == "formal_parameters":
                                    for p in son.children:
                                        if p.type == 'formal_parameter':
                                            param_type = p.child_by_field_name('type')
                                            if param_type != None and param_type.type == 'array_type':
                                                param.append(param_type.child_by_field_name('dimensions').start_byte)
                                            else:
                                                param.append(p.start_byte)
                            func_param_xy[name] = param
                        elif member.type == "constructor_declaration":
                            name = cl_name + "." + code_original[member.start_byte : member.child_by_field_name('parameters').end_byte].decode("utf8")
                            func_name.append(name)
                            func_return_annotation[name] = "@NonNull"
                            param = []
                            start_by[name] = member.start_byte
                            for son in member.children:
                                if son.type == "formal_parameters":
                                    for p in son.children:
                                        if p.type == 'formal_parameter':
                                            param_type = p.child_by_field_name('type')
                                            if param_type != None and param_type.type == 'array_type':
                                                param.append(param_type.child_by_field_name('dimensions').start_byte)
                                            else:
                                                param.append(p.start_byte)
                            func_param_xy[name] = param

    xy = 0
    for member in code_AST.root_node.children:
        if member.type == "package_declaration":
            xy = member.end_byte
    if func_name == []:
        with open(path, "wb") as file:
            file.write(code_original)
        return
    code = code_original[:xy].decode() + "\nimport org.checkerframework.checker.nullness.qual.Nullable;\nimport org.checkerframework.checker.nullness.qual.NonNull;\n" + code_original[xy:start_by[func_name[0]]].decode()
    # try:
    for i in range(len(func_name)):
        if func_return_annotation[func_name[i]] == "@Nullable":
            code = code + ' @Nullable ' 
        try:
            start =  start_by[func_name[i]]
        except:
             with open("missed_error_1.txt", "a") as file:
                file.write("------------start:i:--------------------\n")
                file.write(str(start)+"\n")
                file.write(str(func_name[i])+'\n')
                file.write(str(start_by)+'\n')
                file.close()
                continue
        for k in range(len(func_param_xy[func_name[i]])):
            param = func_param_xy[func_name[i]]
            code = code + code_original[start : param[k]].decode()
            try:
                if func_param_annotation[func_name[i]][k] == '@Nullable':
                    code = code + ' @Nullable '
            except:
                code = code
                with open("missed_error_1.txt", "a") as file:
                    file.write(str(func_param_annotation))
                    file.write(str(func_name[i]))
                    file.write(str(k))
                    file.close()

            start = param[k]
            if k != len(func_param_xy[func_name[i]])-1:
                code = code + code_original[param[k] : param[k+1]].decode()
                start = param[k+1]
        if i != len(func_name)-1:
            try:
                code = code + code_original[start : start_by[func_name[i+1]]].decode()
            except:
                code = code + code_original[start : ].decode()
                with open("missed_error_1.txt", "a") as file:
                    file.write("------------start:-----------------\n")
                    file.write(str(start)+"\n")
                    file.write(str(func_name[i+1])+'\n')
                    file.write(str(start_by)+'\n')
                    file.close()
        else:
            code = code + code_original[start : ].decode()
    # except:
    #     code = code_original.decode()

    with open(path, "wb") as file:
        file.write(code.encode("utf-8"))

import ast
def search_node(java_code, func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation, field_name, field_anno, N, func_param_list, func_param_id, func_type, func_param_shunxv):
    first_nullast = First_NullAST(java_code, func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation, field_name, field_anno, N, func_param_list, func_param_id, func_type, func_param_shunxv)
    first_nullast.count_func_name()
    func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation = first_nullast.get_func()
    field_name, field_anno, N = first_nullast.get_field()
    return func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation, first_nullast.field_name, first_nullast.field_anno, N, first_nullast.func_param_list, first_nullast.func_param_id, first_nullast.func_type, first_nullast.func_param_shunxv

def create_map(java_code, func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation, field_name, field_anno, N, func_param_list, func_param_id, dependency_map, func_param_shunxv, txt_path, field_to_class):
    second_nullast = Second_NullAST(func_name_list=func_name_list, class_name=class_name, func_to_code=func_to_code, field_name=field_name, field_anno=field_anno, N=N, dependency_map=dependency_map, func_param_list=func_param_list,func_param_annotation=func_param_annotation, func_return_annotation=func_return_annotation, func_param_id=func_param_id, func_param_shunxv=func_param_shunxv, field_to_class=field_to_class)
    dependency_map = second_nullast.create_map()
    with open(txt_path, "a", encoding="utf-8") as file:
        file.write("********************dependecy_map信息********************\n")
        file.write(str(dependency_map))
        file.write("\n")
        file.close()
    field_anno, func_return_annotation, func_param_annotation = second_nullast.propagate_nullable_marks(dependency_map)

    return func_name_list, class_name, func_to_code, field_name, field_anno, N, dependency_map, func_param_list, func_param_annotation, func_return_annotation, func_param_id, func_param_shunxv


