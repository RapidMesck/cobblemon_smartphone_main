#!/usr/bin/env python3
"""
Comparador de arquivos de linguagem para Minecraft mods.
Compara todos os arquivos na pasta lang contra en_us.json (base).

Uso:
    python compare_lang.py
    python compare_lang.py --base en_us.json
    python compare_lang.py --json
    python compare_lang.py --template  # gera templates para todos
"""

import json
import sys
import argparse
from pathlib import Path
from collections import OrderedDict

SCRIPT_DIR = Path(__file__).parent
LANG_DIR = SCRIPT_DIR.parent / "common" / "src" / "main" / "resources" / "assets" / "cobblemon_smartphone" / "lang"

def load_json(path: Path) -> dict:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=OrderedDict)

def compare(base: dict, target: dict) -> dict:
    base_keys = set(base.keys())
    target_keys = set(target.keys())

    missing = sorted(base_keys - target_keys)
    extra = sorted(target_keys - base_keys)
    common = sorted(base_keys & target_keys)

    return {
        "missing": missing,
        "extra": extra,
        "common_count": len(common),
        "base_count": len(base_keys),
        "target_count": len(target_keys),
        "missing_count": len(missing),
        "extra_count": len(extra),
    }

def print_report(base_name: str, target_name: str, result: dict, base: dict, target: dict):
    missing = result["missing"]
    extra = result["extra"]

    print(f"\n{'='*60}")
    print(f"  {target_name}")
    print(f"{'='*60}")
    print(f"  Base:   {base_name} ({result['base_count']} chaves)")
    print(f"  Target: {target_name} ({result['target_count']} chaves)")
    print(f"  Comuns: {result['common_count']}")
    print(f"  Faltando: {result['missing_count']}")
    print(f"  Extras: {result['extra_count']}")
    print(f"{'='*60}\n")

    if missing:
        print(f"  FALTANDO ({len(missing)}):")
        print(f"  {'-'*56}")
        for key in missing:
            value = base[key]
            display = value if len(value) <= 50 else value[:47] + "..."
            print(f"  {key}")
            print(f"    -> {display}")
        print()

    if extra:
        print(f"  EXTRAS ({len(extra)}):")
        print(f"  {'-'*56}")
        for key in extra:
            print(f"  {key}")
        print()

    if not missing and not extra:
        print("  Arquivo identico ao base!\n")

def print_summary(all_results: dict):
    print(f"\n{'='*60}")
    print(f"  RESUMO GERAL")
    print(f"{'='*60}")
    print(f"  {'Arquivo':<30} {'Base':>6} {'Target':>7} {'Falta':>6} {'Extra':>6}")
    print(f"  {'-'*56}")

    total_missing = 0
    total_extra = 0
    for name, result in sorted(all_results.items()):
        m = result["missing_count"]
        e = result["extra_count"]
        total_missing += m
        total_extra += e
        status = "OK" if m == 0 and e == 0 else f"{m} chave(s)"
        print(f"  {name:<30} {result['base_count']:>6} {result['target_count']:>7} {m:>6} {e:>6}")

    print(f"  {'-'*56}")
    print(f"  {'TOTAL':<30} {'':>6} {'':>7} {total_missing:>6} {total_extra:>6}")
    print(f"{'='*60}\n")

def generate_template(base: dict, target: dict, output: Path):
    missing = sorted(set(base.keys()) - set(target.keys()))
    template = OrderedDict()
    for key in missing:
        template[key] = ""

    with open(output, "w", encoding="utf-8") as f:
        json.dump(template, f, ensure_ascii=False, indent=2)

    return len(missing)

def main():
    parser = argparse.ArgumentParser(description="Compara arquivos de linguagem Minecraft")
    parser.add_argument("--base", default="en_us.json", help="Arquivo base (default: en_us.json)")
    parser.add_argument("--base-dir", type=Path, default=None, help="Diretorio dos lang files")
    parser.add_argument("--json", action="store_true", help="Saida em JSON")
    parser.add_argument("--template", action="store_true", help="Gera templates para todos")
    parser.add_argument("--target", default=None, help="Comparar apenas um arquivo especifico")
    args = parser.parse_args()

    lang_dir = args.base_dir if args.base_dir else LANG_DIR

    if not lang_dir.exists():
        print(f"  Diretorio nao encontrado: {lang_dir}")
        sys.exit(1)

    base_path = lang_dir / args.base
    if not base_path.exists():
        print(f"  Arquivo base nao encontrado: {base_path}")
        sys.exit(1)

    base = load_json(base_path)

    # coleta todos os arquivos JSON
    if args.target:
        targets = [lang_dir / args.target]
    else:
        targets = sorted(lang_dir.glob("*.json"))

    all_results = {}
    for target_path in targets:
        if target_path.name == args.base:
            continue
        if not target_path.is_file():
            continue

        target = load_json(target_path)
        result = compare(base, target)
        all_results[target_path.name] = result

        if args.template and result["missing_count"] > 0:
            template_dir = lang_dir / "templates"
            template_dir.mkdir(exist_ok=True)
            template_path = template_dir / f"missing_{target_path.name}"
            count = generate_template(base, target, template_path)
            print(f"  Template: {template_path} ({count} chaves)")

        if not args.json:
            print_report(args.base, target_path.name, result, base, target)

    if args.json:
        output = {"base": args.base, "files": all_results}
        print(json.dumps(output, ensure_ascii=False, indent=2))

    if not args.json:
        print_summary(all_results)

if __name__ == "__main__":
    main()
