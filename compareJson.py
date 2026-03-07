import argparse
import json
import os
from typing import Dict, List, Set

from openpyxl import Workbook


CategoryMap = Dict[str, List[str]]
CountMap = Dict[str, int]
MetricMap = Dict[str, float]


def find_json_files(directory: str) -> Set[str]:
    json_files: Set[str] = set()
    for root, _, files in os.walk(directory):
        for file_name in files:
            if file_name.lower().endswith(".json"):
                rel_path = os.path.relpath(os.path.join(root, file_name), directory)
                json_files.add(rel_path)
    return json_files


def is_nullable(value) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, int):
        return value == 1
    if isinstance(value, str):
        normalized = value.strip().lower()
        return normalized in {
            "1",
            "true",
            "nullable",
            "@nullable",
            "javax.annotation.nullable",
            "org.jetbrains.annotations.nullable",
        }
    return False


def empty_categories() -> CategoryMap:
    return {
        "only_a_param": [],
        "only_a_return": [],
        "only_b_param": [],
        "only_b_return": [],
        "both_param": [],
        "both_return": [],
    }


def to_count_map(categories: CategoryMap) -> CountMap:
    return {
        "only_a_param": len(categories["only_a_param"]),
        "only_a_return": len(categories["only_a_return"]),
        "only_b_param": len(categories["only_b_param"]),
        "only_b_return": len(categories["only_b_return"]),
        "both_param": len(categories["both_param"]),
        "both_return": len(categories["both_return"]),
    }


def safe_div(numerator: int, denominator: int) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


def calculate_metrics(counts: CountMap) -> MetricMap:
    # Baseline: project A
    # TP: both, FP: only B, FN: only A
    tp_param = counts["both_param"]
    fp_param = counts["only_b_param"]
    fn_param = counts["only_a_param"]

    tp_return = counts["both_return"]
    fp_return = counts["only_b_return"]
    fn_return = counts["only_a_return"]

    tp_all = tp_param + tp_return
    fp_all = fp_param + fp_return
    fn_all = fn_param + fn_return

    return {
        "precision": safe_div(tp_all, tp_all + fp_all),
        "recall": safe_div(tp_all, tp_all + fn_all),
        "param_precision": safe_div(tp_param, tp_param + fp_param),
        "param_recall": safe_div(tp_param, tp_param + fn_param),
        "return_precision": safe_div(tp_return, tp_return + fp_return),
        "return_recall": safe_div(tp_return, tp_return + fn_return),
    }


def compare_json_files(file_a: str, file_b: str, rel_json_path: str) -> CategoryMap:
    result = empty_categories()

    with open(file_a, "r", encoding="utf-8") as fa, open(file_b, "r", encoding="utf-8") as fb:
        content_a = json.load(fa)
        content_b = json.load(fb)

    top_keys = sorted(set(content_a.keys()) | set(content_b.keys()))
    for top_key in top_keys:
        methods_a = content_a.get(top_key, {})
        methods_b = content_b.get(top_key, {})
        method_names = sorted(set(methods_a.keys()) | set(methods_b.keys()))

        for method_name in method_names:
            method_a = methods_a.get(method_name, {})
            method_b = methods_b.get(method_name, {})

            ret_a = is_nullable(method_a.get("return", 0))
            ret_b = is_nullable(method_b.get("return", 0))
            return_location = f"{rel_json_path}::{top_key}::{method_name}::return"

            if ret_a and ret_b:
                result["both_return"].append(return_location)
            elif ret_a and not ret_b:
                result["only_a_return"].append(return_location)
            elif ret_b and not ret_a:
                result["only_b_return"].append(return_location)

            params_a = method_a.get("parameters", {}) or {}
            params_b = method_b.get("parameters", {}) or {}
            param_names = sorted(set(params_a.keys()) | set(params_b.keys()))

            for param_name in param_names:
                nullable_a = is_nullable(params_a.get(param_name, 0))
                nullable_b = is_nullable(params_b.get(param_name, 0))
                param_location = f"{rel_json_path}::{top_key}::{method_name}::param::{param_name}"

                if nullable_a and nullable_b:
                    result["both_param"].append(param_location)
                elif nullable_a and not nullable_b:
                    result["only_a_param"].append(param_location)
                elif nullable_b and not nullable_a:
                    result["only_b_param"].append(param_location)

    return result


def build_excel(
    output_file: str,
    per_sample_rows: List[Dict[str, object]],
    per_sample_metric_rows: List[Dict[str, object]],
    overall_metrics: MetricMap,
) -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = "json文件比对情况"
    ws.append(["样例名称", "不同参数", "不同返回", "不同注解", "相同参数", "相同返回", "相同注解"])

    for row in per_sample_rows:
        ws.append(
            [
                row["sample_name"],
                row["different_param"],
                row["different_return"],
                row["different_total"],
                row["same_param"],
                row["same_return"],
                row["same_total"],
            ]
        )

    metric_ws = wb.create_sheet("准召率")
    metric_ws.append(["样例名称", "准确率", "召回率", "参数准确率", "参数召回率", "返回准确率", "返回召回率"])

    for row in per_sample_metric_rows:
        metric_ws.append(
            [
                row["sample_name"],
                row["precision"],
                row["recall"],
                row["param_precision"],
                row["param_recall"],
                row["return_precision"],
                row["return_recall"],
            ]
        )

    metric_ws.append(
        [
            "TOTAL",
            overall_metrics["precision"],
            overall_metrics["recall"],
            overall_metrics["param_precision"],
            overall_metrics["param_recall"],
            overall_metrics["return_precision"],
            overall_metrics["return_recall"],
        ]
    )

    wb.save(output_file)


def compare_folders(folder_a: str, folder_b: str, output_file: str) -> Dict[str, object]:
    files_a = find_json_files(folder_a)
    files_b = find_json_files(folder_b)
    common_files = sorted(files_a & files_b)
    per_sample_rows: List[Dict[str, object]] = []
    per_sample_metric_rows: List[Dict[str, object]] = []

    summary: Dict[str, object] = {
        "categories": empty_categories(),
        "only_in_a_files": sorted(files_a - files_b),
        "only_in_b_files": sorted(files_b - files_a),
    }

    categories = summary["categories"]
    for rel_file in common_files:
        file_a = os.path.join(folder_a, rel_file)
        file_b = os.path.join(folder_b, rel_file)
        per_file_result = compare_json_files(file_a, file_b, rel_file)
        for key in categories:
            categories[key].extend(per_file_result[key])

        counts = to_count_map(per_file_result)
        metrics = calculate_metrics(counts)

        different_param = counts["only_a_param"] + counts["only_b_param"]
        different_return = counts["only_a_return"] + counts["only_b_return"]
        same_param = counts["both_param"]
        same_return = counts["both_return"]

        per_sample_rows.append(
            {
                "sample_name": file_b,
                "different_param": different_param,
                "different_return": different_return,
                "different_total": different_param + different_return,
                "same_param": same_param,
                "same_return": same_return,
                "same_total": same_param + same_return,
            }
        )

        per_sample_metric_rows.append(
            {
                "sample_name": file_b,
                "precision": metrics["precision"],
                "recall": metrics["recall"],
                "param_precision": metrics["param_precision"],
                "param_recall": metrics["param_recall"],
                "return_precision": metrics["return_precision"],
                "return_recall": metrics["return_recall"],
            }
        )

    overall_counts = to_count_map(categories)
    overall_metrics = calculate_metrics(overall_counts)
    build_excel(output_file, per_sample_rows, per_sample_metric_rows, overall_metrics)

    summary["output_file"] = output_file
    summary["overall_metrics"] = overall_metrics
    return summary


def print_report(report: Dict[str, object], show_details: bool) -> None:
    categories: CategoryMap = report["categories"]

    ordered = [
        ("only_a_param", "Only project A has @Nullable on parameters"),
        ("only_a_return", "Only project A has @Nullable on returns"),
        ("only_b_param", "Only project B has @Nullable on parameters"),
        ("only_b_return", "Only project B has @Nullable on returns"),
        ("both_param", "Both projects have @Nullable on parameters"),
        ("both_return", "Both projects have @Nullable on returns"),
    ]

    print("Nullable comparison result:")
    for key, title in ordered:
        print(f"- {title}: {len(categories[key])}")

    if report["only_in_a_files"]:
        print("\nJSON files only in project A:")
        for path in report["only_in_a_files"]:
            print(f"- {path}")

    if report["only_in_b_files"]:
        print("\nJSON files only in project B:")
        for path in report["only_in_b_files"]:
            print(f"- {path}")

    if not show_details:
        return

    for key, title in ordered:
        print(f"\n[{title}]")
        if not categories[key]:
            print("(none)")
            continue
        for location in categories[key]:
            print(location)


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Compare @Nullable in two projects' JSON files by parameter/return positions."
    )
    parser.add_argument("project_a", help="Path to project A folder")
    parser.add_argument("project_b", help="Path to project B folder")
    parser.add_argument(
        "--show-details",
        action="store_true",
        help="Print all matched locations for each category",
    )
    parser.add_argument(
        "--output",
        default="compare_jsonall_XR_jdbc_0306.xlsx",
        help="Excel output filename (default: compare_jsonall_XR_jdbc_0306.xlsx)",
    )
    return parser


if __name__ == "__main__":
    args = build_arg_parser().parse_args()
    print(f"Project A (initial annotations): {args.project_a}")
    print(f"Project B (generated annotations): {args.project_b}")

    report = compare_folders(args.project_a, args.project_b, args.output)
    print_report(report, show_details=args.show_details)

    overall = report["overall_metrics"]
    print("\nA as baseline:")
    print(f"- Precision: {overall['precision']:.6f}")
    print(f"- Recall: {overall['recall']:.6f}")
    print(f"- Param Precision: {overall['param_precision']:.6f}")
    print(f"- Param Recall: {overall['param_recall']:.6f}")
    print(f"- Return Precision: {overall['return_precision']:.6f}")
    print(f"- Return Recall: {overall['return_recall']:.6f}")
    print(f"\nExcel saved to: {report['output_file']}")
