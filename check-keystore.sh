#!/usr/bin/env bash
# Vérifie un keystore et produit le base64 à coller, en prouvant l'aller-retour.
#   ./check-keystore.sh tto-release.jks
set -u
KS="${1:?usage: check-keystore.sh <keystore.jks>}"

echo "== 1. le fichier =="
[ -f "$KS" ] || { echo "   introuvable: $KS"; exit 1; }
echo "   taille : $(stat -c%s "$KS") octets"
echo "   sha256 : $(sha256sum "$KS" | cut -d' ' -f1)"

echo "== 2. il s'ouvre ? (le mot de passe va être demandé) =="
if ! keytool -list -keystore "$KS"; then
    echo "   -> le keystore lui-meme est invalide ou le mot de passe est faux."
    echo "      Rien ne sert d'encoder celui-ci."
    exit 1
fi

echo "== 3. base64 + aller-retour =="
base64 -w0 "$KS" > "$KS.b64"
echo "   longueur base64 : $(wc -c < "$KS.b64") caracteres"
if [ "$(base64 -d < "$KS.b64" | sha256sum | cut -d' ' -f1)" = "$(sha256sum "$KS" | cut -d' ' -f1)" ]; then
    echo "   aller-retour OK -> colle le CONTENU de $KS.b64 dans TTO_KEYSTORE_BASE64"
else
    echo "   aller-retour KO"; exit 1
fi
