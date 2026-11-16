#!/usr/bin/env bash
# Gera alerta-predio.zip na raiz, sem target/, .env, .git e node_modules
set -euo pipefail

cd "$(dirname "$0")/.."
DESTINO="alerta-predio.zip"
rm -f "$DESTINO"

if command -v zip >/dev/null 2>&1; then
  # Link temporário para os caminhos saírem com o prefixo alerta-predio/
  TMP="$(mktemp -d)"
  trap 'rm -rf "$TMP"' EXIT
  ln -s "$PWD" "$TMP/alerta-predio"
  (cd "$TMP" && zip -qr "$OLDPWD/$DESTINO" alerta-predio \
    -x 'alerta-predio/target/*' 'alerta-predio/*/target/*' \
       'alerta-predio/.git/*' \
       'alerta-predio/node_modules/*' 'alerta-predio/*/node_modules/*' \
       'alerta-predio/.env' 'alerta-predio/logs/*' \
       'alerta-predio/.idea/*' 'alerta-predio/.vscode/*' \
       "alerta-predio/$DESTINO")
elif command -v python3 >/dev/null 2>&1; then
  python3 - "$DESTINO" <<'PY'
import os, sys, zipfile
destino = sys.argv[1]
pastas_fora = {"target", ".git", "node_modules", "logs", ".idea", ".vscode"}
with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as z:
    for raiz, pastas, arquivos in os.walk("."):
        pastas[:] = [p for p in pastas if p not in pastas_fora]
        for nome in arquivos:
            caminho = os.path.normpath(os.path.join(raiz, nome))
            if caminho in (".env", destino):
                continue
            z.write(caminho, os.path.join("alerta-predio", caminho))
PY
else
  echo "Nem 'zip' nem 'python3' encontrados. No Windows, rode: powershell -File scripts/empacotar.ps1" >&2
  exit 1
fi

echo "$DESTINO gerado com $(unzip -Z1 "$DESTINO" 2>/dev/null | wc -l || echo '?') arquivos ($(du -h "$DESTINO" | cut -f1))."
