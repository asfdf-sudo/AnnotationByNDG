import os
import pandas as pd
from pathlib import Path
from tree_sitter import Language, Parser
import re
import tree_sitter_java

# 加载Java语法
def load_java_parser():
    """加载Java语法解析器"""
    # 请确保已经编译了Java语法库
    # 如果还没有，请按照上述注释中的步骤操作
    JAVA_LANGUAGE = Language(tree_sitter_java.language())

    # 创建解析器
    parser = Parser(JAVA_LANGUAGE)
    return parser

def count_nullable_annotations(file_content, parser):
    """通过AST遍历统计函数参数和返回值上的@Nullable注解"""
    try:
        tree = parser.parse(bytes(file_content, 'utf-8'))
        root_node = tree.root_node
        
        nullable_count_param = 0
        nullable_count_return = 0
        
        # 遍历AST树
        def traverse_node(node):
            nonlocal nullable_count_param, nullable_count_return
            # 1. 检查当前节点是否为注解节点
            if node.type == 'modifiers' and "@Nullable" in bytes(file_content, 'utf-8')[node.start_byte: node.end_byte].decode():
                if True:
                    # 检查注解的位置：参数上或返回值上
                    parent = node.parent
                    if parent:
                        # 2. 检查是否是函数参数上的注解
                        # 参数注解：注解节点的父节点是modifiers，且祖父节点是formal_parameter
                        #print(parent.parent.type)
                        if (parent.type == 'formal_parameter'):
                            nullable_count_param += 1
                            # print(f"找到参数上的@Nullable注解: {annotation_text}")
                        
                        # 3. 检查是否是函数返回值上的注解
                        # 返回值注解：注解节点的父节点是modifiers，且祖父节点是method_declaration
                        elif (parent.type == 'method_declaration'):
                            # 进一步验证：这个注解应该在返回类型之前
                            # 找到方法声明节点的子节点，检查注解是否在返回类型之前
                            nullable_count_return += 1
                                # print(f"找到返回值上的@Nullable注解: {annotation_text}")
            
            # 递归遍历子节点
            for child in node.children:
                traverse_node(child)
        
        # 开始遍历
        traverse_node(root_node)
        
        return nullable_count_param, nullable_count_return
    except Exception as e:
        print(f"AST遍历统计注解时出错: {e}")
        # 出错时回退到简单方法
        return 0

def count_functions_with_ast(file_content, parser):
    """使用AST统计函数数量（排除无参void函数）"""
    """改进的AST遍历统计函数数量"""
    try:
        tree = parser.parse(bytes(file_content, 'utf-8'))
        root_node = tree.root_node
        
        function_count = 0
        
        # 使用栈进行深度优先遍历
        stack = [root_node]
        
        while stack:
            node = stack.pop()
            
            # 如果是方法声明节点
            if node.type == 'method_declaration':
                return_type = ""
                has_parameters = False
                is_constructor = False
                
                # 遍历子节点
                for child in node.children:
                    # 检查是否是构造方法（方法名与类名相同的情况）
                    if child.type == 'identifier':
                        # 这里我们简单判断，实际可能需要更多上下文
                        pass
                    
                    # 获取返回类型
                    elif child.type == 'type':
                        return_type = child.text.decode('utf-8').strip().lower()
                    
                    # 检查参数
                    elif child.type == 'formal_parameters':
                        # 检查参数列表是否为空
                        # 统计参数节点数量（排除括号）
                        param_count = 0
                        for param_child in child.children:
                            if param_child.type in ['formal_parameter', 'spread_parameter']:
                                param_count += 1
                        
                        has_parameters = param_count > 0
                
                # 构造方法没有返回类型，应该被计入
                if not return_type:
                    # 可能是构造方法，应该计入函数数量
                    function_count += 1
                elif return_type == 'void' and not has_parameters:
                    # 无参void函数，排除
                    pass
                else:
                    # 其他函数，计入
                    function_count += 1
            
            # 将子节点加入栈中（深度优先）
            stack.extend(node.children)
        
        return function_count
    except Exception as e:
        print(f"AST遍历时出错: {e}")
        return count_functions_simple(file_content)

def count_functions_simple(file_content):
    """简单的函数统计方法（备选方案）"""
    # 这个方法使用正则表达式，不如AST准确但更简单
    function_patterns = [
        # 匹配public/protected/private static/native等修饰符的方法
        r'(public|protected|private|static|\s) +[\w\<\>\[\]]+\s+(\w+) *\([^\)]*\) *\{',
        # 匹配没有修饰符的方法
        r'^\s*[\w\<\>\[\]]+\s+(\w+) *\([^\)]*\) *\{',
    ]
    
    count = 0
    for pattern in function_patterns:
        matches = re.findall(pattern, file_content, re.MULTILINE)
        # 过滤掉void且无参数的函数
        for match in matches:
            if isinstance(match, tuple):
                method_def = match[0] if len(match) > 1 else match
            else:
                method_def = match
                
            # 检查是否为void且无参数
            if 'void' in method_def and '()' in method_def:
                continue
            count += len(matches)
    
    return count

def analyze_java_project(project_path, parser=None):
    """分析一个Java项目"""
    project_name = os.path.basename(project_path)
    total_nullable = 0
    total_functions = 0
    total_nullable_param = 0
    total_nullable_return = 0
    
    print(f"正在分析项目: {project_name}")
    
    # 遍历项目中的所有Java文件
    for root, dirs, files in os.walk(project_path):
        for file in files:
            if file.endswith('.java'):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                        content = f.read()
                    
                    # 统计@Nullable注解
                    nullable_count_param, nullable_count_return = count_nullable_annotations(content, parser)
                    total_nullable_param += nullable_count_param
                    total_nullable_return += nullable_count_return
                    total_nullable = total_nullable + nullable_count_param + nullable_count_return
                    
                    # 统计函数数量
                    if parser:
                        function_count = count_functions_with_ast(content, parser)
                    else:
                        function_count = count_functions_simple(content)
                    total_functions += function_count
                    
                except Exception as e:
                    print(f"  处理文件 {file_path} 时出错: {e}")
                    continue
    
    # 计算比例
    ratio = total_nullable / total_functions if total_functions > 0 else 0
    
    return {
        'project_name': project_name,
        'nullable_count_param':total_nullable_param,
        'nullable_count_return':total_nullable_return,
        'nullable_count': total_nullable,
        'function_count': total_functions,
        'nullable_ratio': ratio
    }

def analyze_java_projects(root_folder, output_file='java_analysis.xlsx'):
    """分析所有Java项目"""
    projects_data = []
    
    # 尝试加载AST解析器
    parser = None
    try:
        parser = load_java_parser()
        print("使用AST解析器统计函数")
    except Exception as e:
        print(f"无法加载AST解析器，使用简单统计方法: {e}")
        print("注意：要使用AST解析器，请先编译Java语法库")
    
    # 获取所有子文件夹（每个子文件夹代表一个Java项目）
    root_path = Path(root_folder)
    if not root_path.exists():
        print(f"错误：文件夹 {root_folder} 不存在")
        return
    
    # 查找所有子文件夹
    project_folders = []
    for item in root_path.iterdir():
        if item.is_dir():
            project_folders.append(item)
    
    if not project_folders:
        print(f"警告：在 {root_folder} 中没有找到子文件夹")
        # 尝试将当前文件夹作为项目
        project_folders = [root_path]
    
    print(f"找到 {len(project_folders)} 个项目")
    
    # 分析每个项目
    for project_folder in project_folders:
        try:
            result = analyze_java_project(str(project_folder), parser)
            projects_data.append(result)
            print(f"  完成: {result['project_name']} - "
                  f"@Nullable: {result['nullable_count']}, "
                  f"函数: {result['function_count']}, "
                  f"比例: {result['nullable_ratio']:.4f}")
        except Exception as e:
            print(f"分析项目 {project_folder} 时出错: {e}")
    
    # 保存到Excel文件
    if projects_data:
        df = pd.DataFrame(projects_data)
        
        # 按项目名称排序
        df = df.sort_values('project_name')
        
        # 计算总计
        total_row = {
            'project_name': '总计',
            'nullable_count_param': df['nullable_count_param'].sum(),
            'nullable_count_return': df['nullable_count_return'].sum(),
            'nullable_count': df['nullable_count'].sum(),
            'function_count': df['function_count'].sum(),
            'nullable_ratio': df['nullable_count'].sum() / df['function_count'].sum() 
            if df['function_count'].sum() > 0 else 0
        }
        
        # 添加总计行
        df_total = pd.DataFrame([total_row])
        df = pd.concat([df, df_total], ignore_index=True)
        
        # 保存到Excel
        df.to_excel(output_file, index=False)
        print(f"\n分析完成！结果已保存到 {output_file}")
        
        # 显示统计摘要
        print("\n统计摘要:")
        print(f"总项目数: {len(projects_data)}")
        print(f"总@Nullable注解数: {total_row['nullable_count']}")
        print(f"总函数数: {total_row['function_count']}")
        print(f"总比例: {total_row['nullable_ratio']:.4f}")
        
        return df
    else:
        print("没有分析到任何数据")
        return None

def main():
    """主函数"""
    import sys
    
    folder_path = r"C:\Users\Asfdf\Desktop\data"
    
    # 验证路径
    if not os.path.exists(folder_path):
        print(f"错误：路径 {folder_path} 不存在")
        return
    
    # 执行分析
    analyze_java_projects(folder_path)

if __name__ == "__main__":
    main()