import os
import shutil
import time
def copy_file_to_subdirs(src_file, target_parent):
    if not os.path.isfile(src_file):
        raise FileNotFoundError(f"源文件 '{src_file}' 不存在或不是文件。")
    if not os.path.isdir(target_parent):
        raise NotADirectoryError(f"目标目录 '{target_parent}' 不存在或不是目录。")
    for entry in os.listdir(target_parent):
        entry_path = os.path.join(target_parent, entry)
        if os.path.isdir(entry_path):
            try:
                shutil.copy2(src_file, entry_path)
                print(f"成功复制到子目录：'{entry_path}'")
            except Exception as e:
                print(f"复制到 '{entry_path}' 失败：{str(e)}")

import os
import subprocess
import sys
from pathlib import Path
from typing import Dict


def count_errors(log_path):
    """统计日志文件中的ERROR行数"""
    try:
        with open(log_path, 'r') as f:
            return sum(1 for line in f if 'ERROR' in line)
    except FileNotFoundError:
        print(f"错误：日志文件 {log_path} 未找到")
        return 0
    except Exception as e:
        print(f"读取日志文件时发生错误: {e}")
        return 0

from openpyxl import Workbook
def process_maven_projects(root_dir):
    total_errors = 0
    processed_projects = 0
    data = []
    name = []
    tim = []
    # 遍历根目录下的所有子目录
    for project_dir in os.listdir(root_dir):
        project_path = Path(root_dir) / project_dir
        if not project_path.is_dir():
            continue
        start = time.perf_counter()
        os.chdir(rf"{root_dir}\{project_dir}")
        log_file = rf'C:\Users\Asfdf\Desktop\annoation\0307logs_after\{project_dir}_output.log'
        cmd = ['mvn', 'clean', 'install', '>', log_file, '2>&1']
    
        print(f"正在执行Maven构建,日志输出到 {log_file}...")
        process = subprocess.run(cmd, shell=True)
        end = time.perf_counter()
        tim.append([project_dir, end-start])
        # 输出构建结果
        if process.returncode == 0:
            print("Maven构建成功完成")
        else:
            print(f"Maven构建失败，退出码: {process.returncode}")

        # 统计错误数量
        error_count = count_errors(log_file)
        print(f"日志文件中发现 {error_count} 个ERROR级别错误")
        data.append(error_count)
        name.append(project_dir)
    print(data)
    # 创建Workbook对象
    os.chdir(root_dir)
    wb = Workbook()
    ws = wb.active
    ws.append(["样例名称", "错误数目"])
    for i in range(len(data)):
        ws.append([name[i], data[i]])
    wb.save(f"result_0307_org.xlsx")
    return tim

import sys    
if __name__ == "__main__":

    root_directory = r"\path"
    process_maven_projects(root_directory)
