#!/usr/bin/env python3
"""
Tradutor automatico de lang files para Minecraft mods.

APIs suportadas:
    - DeepL (gratis: 500k chars/mes)
    - OpenAI (pago por uso)
    - Google Cloud Translate (gratis: 500k chars/mes)
    - LibreTranslate (gratis, self-hosted)

Uso:
    python translate_lang.py --api deepl --api-key YOUR_KEY
    python translate_lang.py --api openai --api-key YOUR_KEY
    python translate_lang.py --api libretranslate --url http://localhost:5000
"""

import json
import sys
import time
import argparse
import os
from pathlib import Path
from collections import OrderedDict
import urllib.request
import urllib.error

SCRIPT_DIR = Path(__file__).parent
LANG_DIR = SCRIPT_DIR.parent / "common" / "src" / "main" / "resources" / "assets" / "cobblemon_smartphone" / "lang"


# ============================================================================
# API Implementations
# ============================================================================

class DeepLTranslator:
    BASE_URL_FREE = "https://api-free.deepl.com/v2/translate"
    BASE_URL_PRO = "https://api.deepl.com/v2/translate"

    def __init__(self, api_key):
        self.api_key = api_key
        self.is_pro = api_key.endswith(":fx") is False and len(api_key) > 30
        self.base_url = self.BASE_URL_PRO if self.is_pro else self.BASE_URL_FREE

    def translate_batch(self, texts, target_lang):
        data = json.dumps({
            "text": texts,
            "target_lang": target_lang.upper().replace("_", "-"),
            "source_lang": "EN",
        }).encode("utf-8")

        req = urllib.request.Request(self.base_url, data=data, headers={
            "Authorization": f"DeepL-Auth-Key {self.api_key}",
            "Content-Type": "application/json",
        })

        with urllib.request.urlopen(req, timeout=60) as resp:
            result = json.loads(resp.read())
            return [t["text"] for t in result["translations"]]


class OpenAITranslator:
    BASE_URL = "https://api.openai.com/v1/chat/completions"

    def __init__(self, api_key, model="gpt-4o-mini"):
        self.api_key = api_key
        self.model = model

    def translate_batch(self, texts, target_lang):
        lang_names = {
            "pt_br": "portugues brasileiro",
            "zh_cn": "chines simplificado",
            "es_es": "espanhol",
            "fr_fr": "frances",
            "de_de": "alemao",
            "ja_jp": "japones",
            "ko_kr": "coreano",
        }
        lang_name = lang_names.get(target_lang, target_lang)
        translations_json = json.dumps({str(i): t for i, t in enumerate(texts)}, ensure_ascii=False)

        prompt = (
            "Voce e um tradutor profissional para o jogo Minecraft/Cobblemon.\n"
            "Traduza as seguintes chaves do ingles para " + lang_name + ".\n\n"
            "REGRAS:\n"
            "- Termos Pokemon, PC, GPS, Ender Chest, PokeDevem ser mantidos em ingles\n"
            "- Shiny, EV/IV, TM manter em ingles\n"
            "- Placeholders %s, %d, %1$s NAO devem ser traduzidos\n"
            "- Traducoes devem ser naturais, nao literais\n\n"
            "CHAVES:\n" + translations_json + "\n\n"
            "Retorne APENAS JSON no formato: {\"0\": \"traducao\", \"1\": \"traducao\"}\n"
            "Sem explicacoes, apenas o JSON."
        )

        data = json.dumps({
            "model": self.model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.3,
            "max_tokens": 4000,
        }).encode("utf-8")

        req = urllib.request.Request(self.BASE_URL, data=data, headers={
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        })

        with urllib.request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read())
            content = result["choices"][0]["message"]["content"].strip()
            if content.startswith("```"):
                content = content.split("\n", 1)[1]
            if content.endswith("```"):
                content = content.rsplit("```", 1)[0]
            translations = json.loads(content.strip())
            return [translations[str(i)] for i in range(len(texts))]


class LibreTranslator:
    def __init__(self, base_url="http://localhost:5000"):
        self.base_url = base_url.rstrip("/")

    def translate_batch(self, texts, target_lang):
        lang_map = {"pt_br": "pt", "zh_cn": "zh", "es_es": "es", "fr_fr": "fr"}
        api_lang = lang_map.get(target_lang, target_lang.split("_")[0])
        results = []
        for text in texts:
            data = json.dumps({"q": text, "source": "en", "target": api_lang}).encode("utf-8")
            req = urllib.request.Request(f"{self.base_url}/translate", data=data, headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                result = json.loads(resp.read())
                results.append(result["translatedText"])
            time.sleep(0.1)
        return results


class GoogleTranslator:
    BASE_URL = "https://translation.googleapis.com/language/translate/v2"

    def __init__(self, api_key):
        self.api_key = api_key

    def translate_batch(self, texts, target_lang):
        lang_map = {"pt_br": "pt-BR", "zh_cn": "zh-CN", "es_es": "es", "fr_fr": "fr"}
        api_lang = lang_map.get(target_lang, target_lang.replace("_", "-"))
        data = json.dumps({"q": texts, "target": api_lang, "source": "en", "format": "text"}).encode("utf-8")
        req = urllib.request.Request(f"{self.BASE_URL}?key={self.api_key}", data=data, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            result = json.loads(resp.read())
            return [t["translatedText"] for t in result["data"]["translations"]]


# ============================================================================
# Core Logic
# ============================================================================

def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=OrderedDict)

def save_json(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def get_missing_keys(base, target):
    return sorted(set(base.keys()) - set(target.keys()))

def create_translator(args):
    api = args.api.lower()
    if api == "deepl":
        key = args.api_key or os.environ.get("DEEPL_API_KEY")
        if not key:
            print("  Erro: DeepL requer --api-key ou DEEPL_API_KEY"); sys.exit(1)
        return DeepLTranslator(key)
    elif api == "openai":
        key = args.api_key or os.environ.get("OPENAI_API_KEY")
        if not key:
            print("  Erro: OpenAI requer --api-key ou OPENAI_API_KEY"); sys.exit(1)
        return OpenAITranslator(key, model=args.model or "gpt-4o-mini")
    elif api == "libretranslate":
        url = args.url or os.environ.get("LIBRETRANSLATE_URL", "http://localhost:5000")
        return LibreTranslator(url)
    elif api == "google":
        key = args.api_key or os.environ.get("GOOGLE_TRANSLATE_API_KEY")
        if not key:
            print("  Erro: Google requer --api-key ou GOOGLE_TRANSLATE_API_KEY"); sys.exit(1)
        return GoogleTranslator(key)
    else:
        print(f"  API desconhecida: {api}"); print("  APIs: deepl, openai, libretranslate, google"); sys.exit(1)

def translate_file(base, target_path, translator, target_lang, batch_size=25, dry_run=False):
    target = load_json(target_path)
    missing = get_missing_keys(base, target)

    if not missing:
        print(f"  {target_path.name}: Nenhuma chave faltando!")
        return

    print(f"\n  {target_path.name}: {len(missing)} chaves para traduzir")
    translations = OrderedDict(target)

    for i in range(0, len(missing), batch_size):
        batch_keys = missing[i:i + batch_size]
        batch_texts = [base[key] for key in batch_keys]
        batch_num = i // batch_size + 1
        total_batches = (len(missing) + batch_size - 1) // batch_size

        print(f"  Lote {batch_num}/{total_batches} ({len(batch_keys)} chaves)...")

        if dry_run:
            print(f"    [DRY RUN] Pularia traducao")
            continue

        try:
            translated = translator.translate_batch(batch_texts, target_lang)
            for key, value in zip(batch_keys, translated):
                translations[key] = value
            save_json(target_path, translations)
            print(f"    Salvo! ({len(translations)} chaves total)")
            if i + batch_size < len(missing):
                time.sleep(1)
        except Exception as e:
            print(f"    ERRO: {e}")
            print(f"    Progresso salvo ate aqui ({len(translations)} chaves)")
            break

    final = load_json(target_path)
    still_missing = len(get_missing_keys(base, final))
    print(f"\n  Resultado: {target_path.name} agora tem {len(final)} chaves (faltam {still_missing})")


def main():
    parser = argparse.ArgumentParser(description="Tradutor automatico de lang files")
    parser.add_argument("--api", required=True, choices=["deepl", "openai", "libretranslate", "google"])
    parser.add_argument("--api-key", default=None, help="API key (ou variavel de ambiente)")
    parser.add_argument("--model", default=None, help="Modelo para OpenAI (default: gpt-4o-mini)")
    parser.add_argument("--url", default=None, help="URL para LibreTranslate")
    parser.add_argument("--base", default="en_us.json", help="Arquivo base (default: en_us.json)")
    parser.add_argument("--target", default=None, help="Arquivo alvo (default: todos)")
    parser.add_argument("--batch-size", type=int, default=25, help="Tamanho do lote")
    parser.add_argument("--dry-run", action="store_true", help="Simula sem traduzir")
    parser.add_argument("--base-dir", type=Path, default=None, help="Diretorio dos lang files")
    args = parser.parse_args()

    lang_dir = args.base_dir if args.base_dir else LANG_DIR
    base_path = lang_dir / args.base
    if not base_path.exists():
        print(f"  Base nao encontrado: {base_path}"); sys.exit(1)

    base = load_json(base_path)
    translator = create_translator(args)

    if args.target:
        targets = [lang_dir / args.target]
    else:
        targets = sorted(f for f in lang_dir.glob("*.json") if f.name != args.base)

    print(f"  Base: {args.base} ({len(base)} chaves)")
    print(f"  API: {args.api}")
    print(f"  Arquivos: {len(targets)}")

    for target_path in targets:
        if target_path.exists():
            translate_file(base, target_path, translator, args.target or target_path.stem, args.batch_size, args.dry_run)

    print("\n  Concluido!")


if __name__ == "__main__":
    main()
