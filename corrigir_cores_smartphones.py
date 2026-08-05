#!/usr/bin/env python3
"""Uniformiza os frames dos smartphones a partir da cor do ícone do item.

O ícone ``item/<cor>_smartphone.png`` é a fonte de verdade. O script detecta
o tom escuro do contorno do item e aplica sua matiz/saturação aos frames 3D e
da GUI, preservando luminosidade, sombras, alpha e as partes fixas da arte.

Por segurança, a execução padrão grava em uma pasta separada. Use
``--in-place`` somente depois de conferir a saída.
"""

from __future__ import annotations

import argparse
import colorsys
from collections import Counter
from pathlib import Path

from PIL import Image


CORES = (
    "black", "blue", "brown", "cyan", "gray", "green", "light_blue",
    "light_gray", "lime", "magenta", "orange", "pink", "purple", "red",
    "white", "yellow",
)


def argumentos() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Corrige os frames 3D e da GUI usando o contorno dos itens."
    )
    parser.add_argument(
        "--raiz",
        type=Path,
        default=Path("common/src/main/resources/assets/cobblemon_smartphone/textures"),
        help="pasta textures do mod (padrão: %(default)s)",
    )
    parser.add_argument(
        "-o", "--saida", type=Path, default=None,
        help="pasta de saída; padrão: <raiz>/smartphones_corrigidos",
    )
    parser.add_argument(
        "--in-place", action="store_true",
        help="substitui os PNGs originais; não pode ser usado com --saida",
    )
    parser.add_argument(
        "--limiar", type=int, default=8,
        help="variação mínima RGB para considerar uma posição parte do frame",
    )
    return parser.parse_args()


def ler(caminho: Path) -> Image.Image:
    try:
        return Image.open(caminho).convert("RGBA")
    except FileNotFoundError as erro:
        raise SystemExit(f"Arquivo não encontrado: {caminho}") from erro


def cor_do_contorno(imagem: Image.Image, nome: str = "") -> tuple[int, int, int]:
    """Escolhe a cor escura mais frequente na borda externa do ícone."""
    largura, altura = imagem.size
    cores: Counter[tuple[int, int, int]] = Counter()
    for y in range(altura):
        for x in range(largura):
            r, g, b, a = imagem.getpixel((x, y))
            if not a or min(x, y, largura - 1 - x, altura - 1 - y) > 4:
                continue
            saturacao = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)[2]
            if saturacao >= 0.08:
                cores[(r, g, b)] += 1

    if not cores:
        # Preto, branco e cinza não têm saturação; nesses casos a cor neutra
        # mais frequente da própria borda é a referência correta.
        for y in range(altura):
            for x in range(largura):
                r, g, b, a = imagem.getpixel((x, y))
                if a and min(x, y, largura - 1 - x, altura - 1 - y) <= 4:
                    cores[(r, g, b)] += 1

    if not cores:
        raise SystemExit("O item não possui pixels opacos suficientes para detectar o contorno")

    base = min(cores, key=lambda cor: (-cores[cor], sum(cor)))

    # O amarelo sofre uma distorção comum em sombras rasterizadas: o pixel
    # mais escuro do contorno tende a ficar laranja, embora os realces do
    # próprio item confirmem que a cor é amarela. Mantemos a luminosidade e a
    # saturação do contorno, mas usamos a matiz dos pixels coloridos mais
    # claros. As outras cores não precisam desse ajuste.
    if nome == "yellow":
        mais_clara = max(
            cores,
            key=lambda cor: colorsys.rgb_to_hls(*(canal / 255 for canal in cor))[1],
        )
        matiz = colorsys.rgb_to_hls(*(canal / 255 for canal in mais_clara))[0]
        _, luminosidade, saturacao = colorsys.rgb_to_hls(*(canal / 255 for canal in base))
        rgb = colorsys.hls_to_rgb(matiz, luminosidade, saturacao)
        return tuple(round(canal * 255) for canal in rgb)  # type: ignore[return-value]

    # Em geral o contorno é a cor escura dominante. O desempate pela
    # luminosidade evita escolher o brilho claro do frame.
    return base


def mascara_frame(imagens: list[Image.Image], limiar: int) -> set[tuple[int, int]]:
    """Encontra posições que mudam entre as variantes de uma mesma arte."""
    if not imagens:
        return set()
    largura, altura = imagens[0].size
    mascara: set[tuple[int, int]] = set()
    for y in range(altura):
        for x in range(largura):
            pixels = [imagem.getpixel((x, y)) for imagem in imagens]
            if max(pixel[3] for pixel in pixels) == 0:
                continue
            variacao = max(max(pixel[canal] for pixel in pixels) - min(pixel[canal] for pixel in pixels) for canal in range(3))
            if variacao >= limiar:
                mascara.add((x, y))
    return mascara


def recolorir(imagem: Image.Image, mascara: set[tuple[int, int]], referencia: tuple[int, int, int]) -> Image.Image:
    matiz, _, saturacao = colorsys.rgb_to_hls(*(canal / 255 for canal in referencia))
    saida = imagem.copy()
    pixels = saida.load()
    for x, y in mascara:
        r, g, b, a = pixels[x, y]
        if not a:
            continue
        _, luminosidade, _ = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        nr, ng, nb = colorsys.hls_to_rgb(matiz, luminosidade, saturacao)
        pixels[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return saida


def grupo(raiz: Path, pasta_relativa: str, prefixo: str, sufixo: str) -> list[tuple[str, Path]]:
    pasta = raiz / pasta_relativa
    arquivos = []
    for cor in CORES:
        caminho = pasta / f"{prefixo}{cor}{sufixo}.png"
        if caminho.exists():
            arquivos.append((cor, caminho))
    return arquivos


def main() -> None:
    args = argumentos()
    if args.in_place and args.saida:
        raise SystemExit("Use --in-place ou --saida, não os dois")
    if args.limiar < 1:
        raise SystemExit("--limiar deve ser maior que zero")

    raiz = args.raiz.resolve()
    saida = raiz if args.in_place else (args.saida or raiz / "smartphones_corrigidos").resolve()
    grupos = (
        grupo(raiz, "item", "", "_smartphone_3d"),
        grupo(raiz, "gui", "smartphone_", ""),
        grupo(raiz, "gui", "large_smartphone_", ""),
    )
    total = 0
    for arquivos in grupos:
        if not arquivos:
            continue
        imagens = [ler(caminho) for _, caminho in arquivos]
        tamanhos = {imagem.size for imagem in imagens}
        if len(tamanhos) != 1:
            raise SystemExit(f"As imagens do grupo não têm o mesmo tamanho: {arquivos[0][1]}")
        mascara = mascara_frame(imagens, args.limiar)
        for (cor, origem), imagem in zip(arquivos, imagens):
            referencia = cor_do_contorno(ler(raiz / "item" / f"{cor}_smartphone.png"), cor)
            destino = origem if args.in_place else saida / origem.relative_to(raiz)
            destino.parent.mkdir(parents=True, exist_ok=True)
            recolorir(imagem, mascara, referencia).save(destino)
            total += 1
            print(f"{cor:11} {origem.relative_to(raiz)} -> #{referencia[0]:02X}{referencia[1]:02X}{referencia[2]:02X}")
    if not total:
        raise SystemExit("Nenhum frame encontrado")
    print(f"\nPronto: {total} arquivos corrigidos em {saida}")


if __name__ == "__main__":
    main()
