import os
import shutil
import run_npe_new
import os
import re
import subprocess
import sys
from pathlib import Path
from openpyxl import Workbook
from typing import Dict
import time
import pandas as pd
import FieldToClass
def copy_and_modify_java_files(source_dir, dest_dir):
    if os.path.exists(dest_dir):
        shutil.rmtree(dest_dir)
    shutil.copytree(source_dir, dest_dir)
    i=0
    # with open(f"Lognew/log_tmp_askai.txt", "w", encoding="utf-8") as file:
    #     file.write("===================ai======================"+'\n')
    #     file.close()
    logs_static = []
    totken = []
    func_count = 0
    now = time.localtime(time.time())
    for dic in os.listdir(dest_dir):
        dic_path = os.path.join(dest_dir, dic)
        print(dic_path)
        start_time = time.time()
        if os.path.isdir(dic_path):
            funcnam=0
            i= i+1
            edge_sum=0
            param_ann=0
            return_ann=0 
            anno_su=0
            prompt_tokens_para=0
            completion_tokens_para=0
            total_tokens_para=0
            prompt_token=0
            completion_token=0
            total_token=0 
            prompt_tokens_retur=0
            completion_tokens_retur=0
            total_tokens_retur=0
            start = time.perf_counter()
            missed_files  = []
            func_name_list = {}
            class_name = ["RowSetFactory"]
            func_to_code ={} 
            func_param_annotation={}
            func_return_annotation = {}
            field_name = {}
            field_anno = {} 
            N = 0 
            func_param_list = {}
            func_param_id = {} 
            func_type = {} 
            func_param_shunxv = {}
            print(f"-------------------------收集{i}组数据--------------------")
            for root, dirs, files in os.walk(dic_path):
                for file in files:
                    if file.endswith(".java"):
                        dest_file_path = os.path.join(root, file)
                        #if file != "IO.java":
                        if True:
                            # try:
                            with open(dest_file_path, "r", encoding="utf-8") as f:
                                java_code = f.read()
                            func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation, field_name, field_anno, N, func_param_list, func_param_id, func_type, func_param_shunxv = run_npe_new.search_node(java_code, func_name_list, class_name, func_to_code, func_param_annotation, func_return_annotation, field_name, field_anno, N, func_param_list, func_param_id, func_type, func_param_shunxv)

                func_count = func_count + len(func_name_list.keys())
            field_to_class = {}
            for root, dirs, files in os.walk(dic_path):
                for file in files:
                    if file.endswith(".java"):
                        dest_file_path = os.path.join(root, file)
                        #if file != "IO.java":
                            # try:
                        with open(dest_file_path, "r", encoding="utf-8") as f:
                            java_code = f.read()
                            tmp = FieldToClass.extract_class_field_type_mapping(java_code, class_name)
                            for key in tmp:
                                field_to_class[key] = tmp[key] 
            print(f"-------------------------完成收集{i}组数据--------------------")
            with open(f"Lognew/log_searchnode{now}.txt", "a", encoding="utf-8") as file:
                file.write("********************class信息********************\n")
                file.write(str(class_name))
                file.write("\n")
                file.write("********************field信息********************\n")
                file.write(str(field_name))
                file.write("\n")
                file.write("********************参数信息********************\n")
                file.write(str(func_param_id))
                file.write("\n")
                file.write("********************func信息********************\n")
                file.write(str(func_name_list))
                file.write("\n")
                file.close()
            print("++++++++++++++", field_anno)
            dependency_map = [[0 for i in range(N+20)] for j in range(N+20)] 
            print(field_to_class)
            func_name_list, class_name, func_to_code, field_name, field_anno, N, dependency_map, func_param_list, func_param_annotation, func_return_annotation, func_param_id, func_param_shunxv = run_npe_new.create_map(java_code=java_code, func_name_list=func_name_list, class_name=class_name, func_to_code=func_to_code, func_param_annotation=func_param_annotation, func_return_annotation=func_return_annotation, field_name=field_name, field_anno=field_anno, N=N, func_param_list=func_param_list, func_param_id=func_param_id, dependency_map=dependency_map, func_param_shunxv=func_param_shunxv, txt_path=f"Lognew/log_searchnode{now}.txt", field_to_class=field_to_class)
            with open(f"Lognew/log_searchnode{now}.txt", "a", encoding="utf-8") as file:
                file.write("********************field_annotation信息********************\n")
                file.write(str(field_anno))
                file.write("\n")
                file.write("********************func_param_annotation信息********************\n")
                file.write(str(func_param_annotation))
                file.write("\n")
                file.write("********************func_return_annotation信息********************\n")
                file.write(str(func_return_annotation))
                file.write("\n")
                file.close()
            for root, dirs, files in os.walk(dic_path):
                for file in files:
                    if file.endswith(".java"):
                        dest_file_path = os.path.join(root, file)
                        if file != "IO.java":
                            # try:
                            with open(dest_file_path, "r", encoding="utf-8") as f:
                                java_code = f.read()
                            print(f"write-------------------------{i}-------{dest_file_path}-------------")
                            run_npe_new.add_annotation(java_code, func_param_annotation, func_return_annotation, dest_file_path)
        print(func_count)
        end_time = time.time()
        elapsed_time = end_time - start_time
        df_existing = pd.read_excel('Lognew\\time.xlsx')
        add_data = {"name":[dic], "time":[elapsed_time]}
        df_new = pd.DataFrame(add_data)
        df_combined = pd.concat([df_existing, df_new], ignore_index=True)
        with pd.ExcelWriter('Lognew\\time.xlsx', engine='openpyxl', mode='a', if_sheet_exists='replace') as writer:
            df_combined.to_excel(writer, sheet_name='Sheet1', index=False)
    print(func_count)
if __name__ == "__main__":
    source_folder = r"file_path"  # 替换为源文件夹路径
    dest_folder = r"file_path"  # 替换为目标文件夹路径
    copy_and_modify_java_files(source_folder, dest_folder)
    print("-------------Finished--------------。")