#!/usr/bin/env python3
"""Uniformiza a tonalidade dos desenhos sem alterar fundo e borda dos botões."""

from __future__ import annotations

import argparse
import colorsys
import re
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image


def argumentos() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Recolore somente os desenhos dos ícones. O fundo, a transparência "
            "e as bordas compartilhadas são detectados e preservados."
        )
    )
    parser.add_argument(
        "entrada",
        nargs="?",
        type=Path,
        default=Path.cwd(),
        help="pasta com os PNGs (padrão: pasta atual)",
    )
    parser.add_argument(
        "-o",
        "--saida",
        type=Path,
        default=None,
        help="pasta de saída (padrão: ENTRADA/icones_uniformizados)",
    )
    parser.add_argument(
        "--cor",
        default="#00F2FF",
        help="cor-base aplicada aos desenhos, em #RRGGBB (padrão: #00F2FF)",
    )
    parser.add_argument(
        "--saturacao",
        type=float,
        default=None,
        help="saturação de 0 a 100; por padrão, usa a saturação de --cor",
    )
    parser.add_argument(
        "--tolerancia",
        type=float,
        default=22,
        help=(
            "distância RGB protegida ao redor das cores do fundo/borda "
            "(padrão: 22)"
        ),
    )
    parser.add_argument(
        "--sobrescrever",
        action="store_true",
        help="permite substituir arquivos que já existam na pasta de saída",
    )
    return parser.parse_args()


def rgb_hex(valor: str) -> tuple[int, int, int]:
    texto = valor.strip().lstrip("#")
    if len(texto) != 6:
        raise ValueError("--cor deve estar no formato #RRGGBB")
    try:
        return tuple(int(texto[i : i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]
    except ValueError as erro:
        raise ValueError("--cor deve estar no formato #RRGGBB") from erro


def chave_do_par(caminho: Path) -> str:
    """Liga nomes como cloud/cloud_hover e fishnav_border/borderless."""
    return re.sub(r"_(hover|border|borderless)$", "", caminho.stem.lower())


def detectar_chrome(
    itens: list[tuple[Path, Image.Image]],
) -> tuple[set[tuple[int, int]], tuple[int, int, int, int]]:
    """Detecta a área fixa do botão comparando cada par com/sem borda."""
    pares: dict[str, list[Image.Image]] = defaultdict(list)
    todas_as_cores: Counter[tuple[int, int, int, int]] = Counter()
    for caminho, imagem in itens:
        pares[chave_do_par(caminho)].append(imagem)
        todas_as_cores.update(
            cor for cor in imagem.get_flattened_data() if cor[3] != 0
        )

    mascaras: list[set[tuple[int, int]]] = []
    for imagens in pares.values():
        if len(imagens) != 2:
            continue
        primeira, segunda = imagens
        largura, altura = primeira.size
        mascaras.append(
            {
                (x, y)
                for y in range(altura)
                for x in range(largura)
                if primeira.getpixel((x, y)) != segunda.getpixel((x, y))
            }
        )

    if not mascaras:
        raise SystemExit(
            "Não foi possível identificar pares com/sem borda pelos nomes dos arquivos"
        )

    # Protege posições que mudam em todos os pares. Também inclui pequenas
    # variações que ficam coladas ao limite externo (antialiasing da borda),
    # sem capturar diferenças internas dos desenhos.
    chrome = set.intersection(*mascaras)
    largura, altura = itens[0][1].size
    chrome_total = set.union(*mascaras)
    chrome.update(
        (x, y)
        for x, y in chrome_total
        if min(x, y, largura - 1 - x, altura - 1 - y) <= 3
    )
    fundo = todas_as_cores.most_common(1)[0][0]
    return chrome, fundo


def recolorir(
    imagem: Image.Image,
    chrome: set[tuple[int, int]],
    fundo: tuple[int, int, int, int],
    matiz: float,
    saturacao: float,
    tolerancia: float,
    clarear_sombras: bool = False,
) -> Image.Image:
    saida = imagem.copy()
    pixels = saida.load()
    largura, altura = saida.size

    def distancia2(cor1: tuple[int, ...], cor2: tuple[int, ...]) -> int:
        return sum((canal1 - canal2) ** 2 for canal1, canal2 in zip(cor1[:3], cor2[:3]))

    for y in range(altura):
        for x in range(largura):
            r, g, b, a = pixels[x, y]
            cor = (r, g, b, a)
            if a == 0:
                continue

            # Arquivos rasterizados podem ter pequenas diferenças de
            # antialiasing no mesmo fundo/borda. A tolerância evita que esses
            # pixels quase idênticos virem pontos ciano muito saturados.
            if (
                (x, y) in chrome
                or (fundo[3] == a and distancia2(cor, fundo) <= tolerancia**2)
            ):
                continue

            # Mantém a luminosidade original (volume, sombras e antialiasing),
            # mas fixa matiz e saturação para toda a coleção.
            _, luminosidade, _ = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if clarear_sombras:
                # A arte da Pokédex possui sombras muito mais escuras que as
                # dos demais ícones. Esta curva aproxima sua faixa tonal da
                # coleção sem apagar o volume do desenho.
                luminosidade = min(1.0, luminosidade * 0.75 + 60 / 255)
            nr, ng, nb = colorsys.hls_to_rgb(matiz, luminosidade, saturacao)
            pixels[x, y] = (
                round(nr * 255),
                round(ng * 255),
                round(nb * 255),
                a,
            )
    return saida


def main() -> None:
    args = argumentos()
    entrada = args.entrada.resolve()
    saida = (args.saida or entrada / "icones_uniformizados").resolve()
    arquivos = sorted(entrada.glob("*.png"))
    if not arquivos:
        raise SystemExit(f"Nenhum PNG encontrado em: {entrada}")

    rgb = rgb_hex(args.cor)
    matiz, _, saturacao_da_cor = colorsys.rgb_to_hls(*(canal / 255 for canal in rgb))
    saturacao = saturacao_da_cor if args.saturacao is None else args.saturacao / 100
    if not 0 <= saturacao <= 1:
        raise SystemExit("--saturacao deve estar entre 0 e 100")
    if args.tolerancia < 0:
        raise SystemExit("--tolerancia não pode ser negativa")

    itens: list[tuple[Path, Image.Image]] = []
    for arquivo in arquivos:
        imagem = Image.open(arquivo).convert("RGBA")
        itens.append((arquivo, imagem))

    tamanhos = {imagem.size for _, imagem in itens}
    if len(tamanhos) != 1:
        raise SystemExit("Todos os ícones precisam ter as mesmas dimensões")

    saida.mkdir(parents=True, exist_ok=True)
    chrome, fundo = detectar_chrome(itens)
    total = 0
    for origem, imagem in itens:
        destino = saida / origem.name
        if destino.exists() and not args.sobrescrever:
            raise SystemExit(
                f"O arquivo já existe: {destino}\n"
                "Use --sobrescrever para substituí-lo."
            )
        eh_pokedex = "pokedex" in origem.stem.lower()
        recolorir(
            imagem,
            chrome,
            fundo,
            matiz,
            saturacao,
            0 if eh_pokedex else args.tolerancia,
            clarear_sombras=eh_pokedex,
        ).save(destino)
        total += 1

    print(f"Pronto: {total} ícones salvos em {saida}")


if __name__ == "__main__":
    main()
