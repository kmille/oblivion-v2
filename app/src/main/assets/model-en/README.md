# Vosk English model — à télécharger

Ce dossier doit contenir le modèle **Vosk small English** pour faire fonctionner
le trigger Voice Wipe en mode anglais.

## Téléchargement

1. Va sur https://alphacephei.com/vosk/models
2. Télécharge **vosk-model-small-en-us-0.15** (~40 MB)
   - Lien direct : https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
3. Dézippe le fichier
4. Copie **le contenu** du dossier extrait (pas le dossier lui-même) dans ce dossier
   (`app/src/main/assets/model-en/`).

## Structure attendue

Après extraction, ce dossier doit contenir au minimum :

```
model-en/
├── am/
│   └── final.mdl
├── conf/
│   ├── mfcc.conf
│   └── model.conf
├── graph/
│   ├── Gr.fst (ou HCLr.fst)
│   ├── words.txt
│   ├── disambig_tid.int
│   └── phones/
│       └── word_boundary.int
├── ivector/
│   └── ...
└── README (optionnel)
```

## Important : aplatir la structure

L'app Oblivion s'attend à trouver `final.mdl` **à la racine** de `model-en/`,
pas dans `model-en/am/final.mdl`.

Si après extraction tu as `model-en/am/final.mdl`, déplace **tous les fichiers
de `am/`, `conf/`, `graph/`, `ivector/`** à la racine de `model-en/` (comme c'est
déjà fait dans `model-fr/`).

Pour comparer la structure attendue, regarde `app/src/main/assets/model-fr/` —
le modèle français y est déjà aplati.

## Une fois fait

- Recompile l'APK (`./gradlew assembleRelease`)
- Dans Voice Wipe, sélectionne **English** dans la carte "Voice recognition language"
- Configure ta phrase-clé en anglais
- Active le trigger

## Sans le modèle

Si tu ne télécharges pas le modèle anglais, l'app fonctionne normalement avec
le modèle français. Le sélecteur affiche "English" comme option, mais si tu
sélectionnes English sans avoir installé le modèle, le service Voice Wipe
n'arrivera pas à démarrer (log : `unpack failed for language='en'`).

## Licence Vosk

Les modèles Vosk small sont sous licence **Apache 2.0** — utilisation
commerciale autorisée.
