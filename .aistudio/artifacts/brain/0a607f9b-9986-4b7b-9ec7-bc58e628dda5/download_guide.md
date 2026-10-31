# Guide de Résolution & Téléchargement — Photo Coach v1.0.1

### Pourquoi voyez-vous un point d'exclamation (`!`) ?

Dans l'interface web de Google AI Studio / VS Code :
1. **Clic gauche direct :** Lorsque vous cliquez directement avec le bouton gauche de la souris sur un fichier `.apk`, l'éditeur tente de l'ouvrir dans un onglet comme s'il s'agissait de code texte. Comme un fichier APK est un binaire volumineux, l'éditeur ne peut pas l'afficher et affiche un avertissement ou une icône d'erreur avec un **point d'exclamation (`!`)**.
2. **Limite de taille du navigateur :** Certains navigateurs web signalent d'un point d'exclamation les fichiers binaires dépassant 50 Mo lors d'une tentative de prévisualisation.

---

### Solution 1 : Le bon geste dans l'explorateur (Clic Droit)

Pour télécharger le fichier sans l'ouvrir dans l'éditeur :
1. Dans la colonne de gauche (arborescence des fichiers), faites un **clic droit** (ou cliquez sur les trois petits points `⋮` qui apparaissent en survolant le fichier) sur le fichier désiré.
2. Cliquez sur **Download** (ou **Télécharger**).
3. Ne faites **pas** de simple clic gauche pour l'ouvrir.

---

### Solution 2 : Versions allégées disponibles (Sans alerte de taille)

Pour éviter tout blocage lié au poids du fichier binaire, plusieurs formats ont été mis à disposition à la racine du projet et dans le dossier `app/` :

| Fichier | Format | Taille | Empreinte SHA-256 | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`PhotoCoach-v1.0.1-arm64.zip`** | Archive ZIP | **27 Mo** | `812bbc7cefa6b353988a8d4e78fcd747f1f684ea5d61da73d5d5ad277b660ae2` | **Recommandé** : Très léger, téléchargeable sans aucune restriction de navigateur. Il suffit de le décompresser sur PC ou smartphone. |
| **`PhotoCoach-v1.0.1-arm64-debug.apk`** | APK direct (ARM64) | **37 Mo** | `e2688e7eeceba290f72e01c2c700e80dae8780fc43c197206a5134d81d7e8f0a` | Optimisé pour les smartphones Android récents (processeurs 64 bits), signé et prêt à installer. |
| **`PhotoCoach-v1.0.1-debug.apk`** | APK universel | **68 Mo** | `3aaf2bfb8ee62ce76abd1750c534bb905116bd1963736987ff2d81264c1d8fb2` | Version multi-architectures complète (arm64, armv7, x86). |

---

### Solution 3 : Télécharger via le menu natif de Google AI Studio

Si l'explorateur web continue d'afficher un blocage sur les fichiers :
1. Regardez dans le menu supérieur droit de l'interface AI Studio (icône de paramètres / roue crantée ou menu à trois points `⋮` du projet).
2. Cliquez sur **Export Project as ZIP** ou **Generate / Download APK**.
3. Cette fonction native de la plateforme télécharge directement l'archive complète de votre projet sur votre ordinateur.
