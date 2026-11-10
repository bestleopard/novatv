# NovaPlay — lecteur IPTV pour Android (téléphone + Android TV)

Lecteur seulement : l'app ne fournit aucune chaîne ni aucun contenu. Tu ajoutes ta propre playlist (compte Xtream Codes ou lien M3U).

## Fonctions
- **Playlists** : Xtream Codes (serveur / utilisateur / mot de passe, connexion vérifiée) et liens M3U/M3U8, plusieurs playlists possibles.
- **TV en direct** : catégories, recherche, favoris (★).
- **Films** : catégories, recherche, affiches, reprise de lecture automatique.
- **Séries** (Xtream) : saisons, épisodes, reprise par épisode.
- **Téléchargement hors ligne** : films et épisodes téléchargés dans le stockage privé de l'app, avec notification de progression. Lecture sans connexion depuis l'écran « Téléchargements », accessible même si la playlist ne charge pas.
- **Android TV** : icône dans le lanceur TV, navigation complète à la télécommande (focus visible), lecteur contrôlable au D-pad.

## Obtenir l'APK sans Android Studio (GitHub Actions)
1. Crée un dépôt sur github.com (privé conseillé).
2. Envoie tout le contenu de ce dossier dans le dépôt (bouton *Add file → Upload files*, ou `git push`).
3. Onglet **Actions** → le workflow « Build APK » se lance tout seul (≈ 5-8 min).
4. Ouvre l'exécution terminée → section **Artifacts** → télécharge `NovaPlay-apk` (zip contenant `app-debug.apk` et `app-release.apk`).

## Avec Android Studio
Ouvrir le dossier → laisser Gradle synchroniser → *Build → Build APK(s)*.

## Installation
- **Téléphone** : ouvrir l'APK, autoriser « sources inconnues ».
- **Android TV / box** : copier l'APK sur clé USB et l'ouvrir avec un gestionnaire de fichiers, ou utiliser l'app *Downloader* / `adb install app-release.apk`.

## Notes techniques
- Kotlin, Jetpack Compose, Media3 (ExoPlayer) pour la lecture (TS, HLS, MP4, MKV).
- Téléchargements : `DownloadManager` Media3 + service au premier plan (dossier `Android/data/com.novaplay.app/files/downloads`).
- Les téléchargements sont supprimés si l'app est désinstallée.
- Les APK sont signés avec la clé debug : pour une mise à jour, garde le même mode de compilation (sinon désinstaller avant).
