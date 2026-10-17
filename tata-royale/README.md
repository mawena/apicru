# 🛖 Tata Royale — La bataille des takienta

Jeu Android de stratégie en temps réel inspiré de *Clash Royale*, avec un univers **togolais et ouest-africain** :
les tours sont des **takienta** (maisons-forteresses du Koutammakou), la tour principale est la **chefferie**
sous son parasol royal, l'élixir est remplacé par la **noix de kola**, la monnaie par les **cauris**, et l'arène
est coupée en deux par le fleuve **Mono**.

## Gameplay

- Arène de 18 × 32 cases, deux couloirs, deux ponts.
- Chaque camp a 2 takienta et 1 chefferie. Une takienta détruite rapporte 1 couronne, la chefferie 3 (victoire immédiate).
- Kola : +1 toutes les 2,8 s (max 10), **kola ×2** pendant la dernière minute.
- 3 minutes de combat puis 1 minute de **prolongation** en cas d'égalité (la première tour détruite gagne).
- Main de 4 cartes + carte suivante, deck de 8 cartes à choisir parmi 12.
- Glissez une carte sur votre moitié de terrain, ou touchez-la puis touchez l'arène. Quand une takienta
  adverse tombe, une « poche » de déploiement s'ouvre dans ce couloir.
- Adversaire IA (défense, contre-attaques, sorts sur les groupes, achèvement des tours) dont la difficulté
  augmente avec les arènes : Marché d'Adawlato (Lomé) → Rives du Mono → Monts de la Kara → Koutammakou → Cour royale de Notsé.

## Les cartes

| Carte | Kola | Rôle | Inspiration |
|---|---|---|---|
| 🤼 Lutteur Evala | 3 | Mini-tank au corps à corps | Lutte initiatique kabyè (Kara) |
| 🏹 Chasseurs kabyè | 3 | 2 archers, air et sol | Chasse rituelle des monts Kabyè |
| 🗡️ Amazones Agojié | 4 | 3 guerrières rapides | Régiment féminin du Dahomey |
| 🌾 Zangbéto | 6 | Gros tank, bâtiments uniquement | Gardien de la nuit vodoun |
| 🐘 Éléphant de Fazao | 5 | Dégâts de zone au sol | Parc de Fazao-Malfakassa |
| 🔮 Prêtre vodoun | 5 | Sorts de zone à distance | Berceau du vodoun (golfe du Bénin) |
| 🐦 Calaos | 3 | 2 unités volantes | Oiseau emblématique des savanes |
| 🛵 Zémidjan | 4 | Moto-taxi rapide, bâtiments uniquement | Les « zems » de Lomé |
| 🦁 Lion de la Kéran | 4 | Fauve rapide et puissant | Parc national de la Kéran |
| 🛖 Case Tamberma | 4 | Bâtiment défensif (35 s) | Takienta des Batammariba (UNESCO) |
| ⚡ Foudre de Xêvioso | 4 | Sort : gros dégâts, petite zone | Divinité vodoun du tonnerre |
| 🔥 Feu de brousse | 2 | Sort : grande zone | Saison sèche et harmattan |

Le menu **Lexique & culture** du jeu explique chaque référence.

## Architecture

```
tata-royale/
├── core/   Moteur de jeu en Kotlin pur (sans dépendance Android), testé sur JVM
│   ├── Cards.kt     Catalogue des cartes
│   ├── Arena.kt     Géométrie de l'arène, ponts, déplacements
│   ├── Entities.kt  Tours, troupes, projectiles, main de cartes
│   ├── Battle.kt    Simulation (pas fixe de 1/30 s), combats, couronnes, fin de partie
│   └── BotAi.kt     Intelligence artificielle de l'adversaire
└── app/    Application Android (Kotlin, sans dépendance AndroidX)
    ├── MainActivity.kt   Village : trophées, cauris, arène, lexique
    ├── DeckActivity.kt   Composition du deck (appui long = fiche de la carte)
    ├── BattleActivity.kt Lancement d'un combat contre l'IA
    ├── GameView.kt       Rendu Canvas (takienta, fleuve, effets) + glisser-déposer
    └── Profile.kt        Progression sauvegardée (SharedPreferences)
```

Tout le rendu est dessiné au Canvas avec des formes et des emojis : le projet n'a besoin d'aucune image.

## Compiler et lancer

Prérequis : Android Studio (Ladybug ou plus récent) ou le SDK Android avec la plateforme 35, et JDK 17+.

```bash
cd tata-royale
./gradlew :core:test          # tests du moteur de jeu
./gradlew :app:assembleDebug  # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug   # installation sur un appareil ou un émulateur connecté
```

Ou ouvrez le dossier `tata-royale/` dans Android Studio puis lancez la configuration `app`.

## Pistes d'évolution

- Niveaux de cartes améliorables avec les cauris, coffres (« calebasses ») à ouvrir.
- Sprites dessinés à la main (motifs kente, adinkra) et musique (djembé, gankogui).
- Multijoueur en ligne, clans (« collectivités »), défis hebdomadaires.
- Nouvelles cartes : Griot au djembé (accélère les troupes), Masques Egungun, Guerriers ashanti…
