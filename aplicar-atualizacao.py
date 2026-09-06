#!/usr/bin/env python3
"""Apply the delivered ZIP to a clean clone of its known original commit."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
from datetime import datetime, timezone

BASE = "82fea41d024d1b22c942840a230ecfd069b29e7c"
REMOVED = (
    "src/main/java/com/ofertas/bot/BotScheduler.java",
    "src/main/java/com/ofertas/bot/controller/TestController.java",
    "src/main/java/com/ofertas/bot/event/OfertaAprovadaEvent.java",
    "src/main/java/com/ofertas/bot/listener/TelegramNotificationListener.java",
    "src/main/java/com/ofertas/bot/model/PromocaoHistorico.java",
    "src/main/java/com/ofertas/bot/repository/PromocaoHistoricoRepository.java",
    "src/main/java/com/ofertas/bot/util/UrlNormalizer.java",
)


def git(repository, *args):
    return subprocess.run(
        ["git", "-C", str(repository), *args], check=True,
        capture_output=True, text=True, encoding="utf-8",
    ).stdout.strip()


def checked_path(root, relative):
    parts = PurePosixPath(relative)
    if (not relative or parts.is_absolute() or "\\" in relative or ":" in relative
            or any(part in ("..", ".git") for part in parts.parts)):
        raise ValueError("Caminho inválido no pacote.")
    candidate = root.joinpath(*parts.parts)
    for path in (candidate, *candidate.parents):
        if path == root:
            break
        if path.is_symlink():
            raise ValueError("O pacote e seus destinos não podem conter links simbólicos.")
    return candidate


def apply(repository):
    source = Path(__file__).resolve().parent
    destination = repository.resolve(strict=True)
    if source == destination:
        raise ValueError("Extraia o pacote em uma pasta separada do clone de destino.")
    if Path(git(destination, "rev-parse", "--show-toplevel")).resolve() != destination:
        raise ValueError("Informe a raiz do clone Git do bot-ofertas.")
    if git(destination, "status", "--porcelain"):
        raise ValueError("O clone deve estar limpo. Preserve suas alterações antes de aplicar.")
    if git(destination, "rev-parse", "HEAD") != BASE:
        raise ValueError("O clone está em outro commit. Integre suas mudanças manualmente em uma branch.")
    manifest = json.loads((source / "arquivos-atualizacao.json").read_text(encoding="utf-8"))
    if manifest["base_commit"] != BASE or not manifest["files"]:
        raise ValueError("Manifesto incompatível com esta atualização.")
    prepared = []
    seen = set()
    for record in manifest["files"]:
        relative = record["path"]
        original = checked_path(source, relative)
        target = checked_path(destination, relative)
        if relative in seen or (target.exists() and not target.is_file()):
            raise ValueError("Entrada duplicada ou destino incompatível no pacote.")
        seen.add(relative)
        if hashlib.sha256(original.read_bytes()).hexdigest() != record["sha256"]:
            raise ValueError("Arquivo alterado ou corrompido: " + relative)
        prepared.append((original, target))
    removed = [checked_path(destination, relative) for relative in REMOVED]
    if any(path.exists() and not path.is_file() for path in removed):
        raise ValueError("Componente antigo possui um destino incompatível.")
    branch = "codex/ofertas-confiaveis-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S-%f")
    git(destination, "switch", "-c", branch)
    for original, target in prepared:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, target)
    for path in removed:
        path.unlink(missing_ok=True)
    print("Atualização aplicada na branch " + branch)
    print("Confira git status --short e git diff --stat antes de fazer commit e push.")
    print("O script não fez commit, push, merge nem envio ao Telegram.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repositorio", required=True, type=Path)
    args = parser.parse_args()
    try:
        apply(args.repositorio)
    except subprocess.CalledProcessError:
        print("Não foi possível executar a operação Git. Confira o clone e as permissões locais.", file=sys.stderr)
        return 1
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("Atualização interrompida: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
