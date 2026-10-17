package tg.tataroyale.core

enum class CardKind { TROOP, BUILDING, SPELL }

/** Ce qu'une unité est capable d'attaquer. */
enum class Targets { GROUND, AIR_AND_GROUND, BUILDINGS }

enum class Rarity(val label: String) {
    COMMUNE("Commune"), RARE("Rare"), EPIQUE("Épique"), LEGENDAIRE("Légendaire")
}

/**
 * Définition statique d'une carte. Les distances sont en cases, les vitesses
 * en cases/seconde et les durées en secondes.
 */
data class CardDef(
    val id: String,
    val name: String,
    val emoji: String,
    val cost: Int,
    val kind: CardKind,
    val description: String,
    val culture: String,
    val rarity: Rarity = Rarity.COMMUNE,
    val hp: Int = 0,
    val damage: Int = 0,
    val hitSpeed: Float = 1f,
    val range: Float = 0.6f,
    val speed: Float = 1f,
    val count: Int = 1,
    val targets: Targets = Targets.GROUND,
    val flying: Boolean = false,
    val splashRadius: Float = 0f,
    val radius: Float = 0.45f,
    val mass: Float = 1f,
    /** 0 = attaque au corps à corps. */
    val projectileSpeed: Float = 0f,
    /** Durée de vie d'un bâtiment (0 = illimitée). */
    val lifetime: Float = 0f,
    val deployTime: Float = 1f,
    val sightRange: Float = 5.5f,
    val spellRadius: Float = 0f,
    val spellDelay: Float = 0f,
    val towerDamageFactor: Float = 0.35f,
) {
    val isSpell get() = kind == CardKind.SPELL
    val hitsAir get() = targets == Targets.AIR_AND_GROUND
    val isTank get() = hp >= 1500 || targets == Targets.BUILDINGS
}

object Cards {
    val LUTTEUR = CardDef(
        id = "lutteur", name = "Lutteur Evala", emoji = "🤼", cost = 3, kind = CardKind.TROOP,
        description = "Lutteur robuste qui encaisse les coups et bouscule les troupes au sol.",
        culture = "L'Evala est la fête de lutte traditionnelle du peuple kabyè, dans la région de la Kara (nord du Togo). C'est un rite de passage pour les jeunes hommes.",
        hp = 950, damage = 120, hitSpeed = 1.1f, range = 0.6f, speed = 1.0f, radius = 0.5f, mass = 3f,
    )

    val CHASSEURS = CardDef(
        id = "chasseurs", name = "Chasseurs kabyè", emoji = "🏹", cost = 3, kind = CardKind.TROOP,
        description = "Deux archers qui atteignent les cibles au sol comme dans les airs.",
        culture = "Dans les monts de la Kara, l'arc et la chasse rituelle accompagnent les grandes fêtes initiatiques.",
        hp = 260, damage = 85, hitSpeed = 1.0f, range = 5f, speed = 1.0f, count = 2,
        targets = Targets.AIR_AND_GROUND, projectileSpeed = 9f, radius = 0.4f,
    )

    val AGOJIE = CardDef(
        id = "agojie", name = "Amazones Agojié", emoji = "🗡️", cost = 4, kind = CardKind.TROOP,
        rarity = Rarity.RARE,
        description = "Trois guerrières rapides et redoutables au corps à corps.",
        culture = "Les Agojié formaient le régiment de femmes soldats du royaume du Dahomey, voisin du Togo, du XVIIe au XIXe siècle.",
        hp = 420, damage = 115, hitSpeed = 0.9f, range = 0.6f, speed = 1.4f, count = 3, radius = 0.4f,
    )

    val ZANGBETO = CardDef(
        id = "zangbeto", name = "Zangbéto", emoji = "🌾", cost = 6, kind = CardKind.TROOP,
        rarity = Rarity.EPIQUE,
        description = "Gardien massif de raphia. Il ignore les troupes et fonce sur les bâtiments.",
        culture = "Le Zangbéto est le « gardien de la nuit » de la tradition vodoun des Ewé, Mina et Fon : une haute silhouette de raphia qui tournoie pendant les cérémonies.",
        hp = 3300, damage = 210, hitSpeed = 1.5f, range = 0.8f, speed = 0.65f,
        targets = Targets.BUILDINGS, radius = 0.75f, mass = 10f, sightRange = 7f,
    )

    val ELEPHANT = CardDef(
        id = "elephant", name = "Éléphant de Fazao", emoji = "🐘", cost = 5, kind = CardKind.TROOP,
        rarity = Rarity.RARE,
        description = "Charge lourde qui frappe toutes les troupes au sol autour de lui.",
        culture = "Le parc national de Fazao-Malfakassa, au centre du Togo, abrite les derniers éléphants de savane du pays.",
        hp = 2000, damage = 230, hitSpeed = 1.6f, range = 0.7f, speed = 0.8f, splashRadius = 1.3f,
        radius = 0.7f, mass = 8f,
    )

    val PRETRE = CardDef(
        id = "pretre", name = "Prêtre vodoun", emoji = "🔮", cost = 5, kind = CardKind.TROOP,
        rarity = Rarity.EPIQUE,
        description = "Lance des sorts de zone sur les troupes au sol et volantes.",
        culture = "Le vodoun est né dans le golfe du Bénin. Le Togo et le Bénin en sont le berceau, et il est célébré chaque 10 janvier.",
        hp = 620, damage = 140, hitSpeed = 1.4f, range = 5.5f, speed = 1.0f, splashRadius = 1.4f,
        targets = Targets.AIR_AND_GROUND, projectileSpeed = 7f,
    )

    val CALAOS = CardDef(
        id = "calaos", name = "Calaos", emoji = "🐦", cost = 3, kind = CardKind.TROOP,
        description = "Deux grands oiseaux volants qui piquent sur tout ce qui bouge.",
        culture = "Le calao, reconnaissable à son bec massif, peuple les forêts et savanes d'Afrique de l'Ouest.",
        hp = 300, damage = 95, hitSpeed = 1.0f, range = 1.4f, speed = 1.5f, count = 2, flying = true,
        targets = Targets.AIR_AND_GROUND, projectileSpeed = 12f, radius = 0.4f,
    )

    val ZEMIDJAN = CardDef(
        id = "zemidjan", name = "Zémidjan", emoji = "🛵", cost = 4, kind = CardKind.TROOP,
        rarity = Rarity.RARE,
        description = "Moto-taxi ultra rapide qui file droit sur les bâtiments.",
        culture = "Les « zems » (zémidjan : « emmène-moi vite » en fon) sont les mototaxis emblématiques de Lomé et de Cotonou.",
        hp = 1350, damage = 210, hitSpeed = 1.5f, range = 0.7f, speed = 2.0f,
        targets = Targets.BUILDINGS, radius = 0.55f, mass = 4f, sightRange = 7f,
    )

    val LION = CardDef(
        id = "lion", name = "Lion de la Kéran", emoji = "🦁", cost = 4, kind = CardKind.TROOP,
        description = "Fauve rapide qui frappe fort les troupes au sol.",
        culture = "Le parc national de la Kéran, dans la région des Savanes, est l'un des grands espaces sauvages du nord du Togo.",
        hp = 1100, damage = 185, hitSpeed = 1.2f, range = 0.6f, speed = 1.5f, radius = 0.55f, mass = 4f,
    )

    val CASE = CardDef(
        id = "case", name = "Case Tamberma", emoji = "🛖", cost = 4, kind = CardKind.BUILDING,
        rarity = Rarity.RARE,
        description = "Bâtiment défensif en terre crue qui tire sur les troupes ennemies.",
        culture = "Les takienta, maisons-forteresses en terre des Batammariba, font du Koutammakou un site classé au patrimoine mondial de l'UNESCO.",
        hp = 1150, damage = 95, hitSpeed = 0.9f, range = 6f, speed = 0f, lifetime = 35f,
        targets = Targets.AIR_AND_GROUND, projectileSpeed = 11f, radius = 0.9f, mass = 100f, sightRange = 6f,
    )

    val XEVIOSO = CardDef(
        id = "xevioso", name = "Foudre de Xêvioso", emoji = "⚡", cost = 4, kind = CardKind.SPELL,
        rarity = Rarity.EPIQUE,
        description = "La foudre s'abat sur une petite zone et inflige de lourds dégâts.",
        culture = "Xêvioso (Hêbiesso chez les Ewé) est la divinité vodoun du tonnerre et de la justice.",
        damage = 560, spellRadius = 2.2f, spellDelay = 0.4f,
    )

    val FEU = CardDef(
        id = "feu", name = "Feu de brousse", emoji = "🔥", cost = 2, kind = CardKind.SPELL,
        description = "Embrase une large zone : idéal contre les petites troupes.",
        culture = "Les feux de brousse marquent la saison sèche dans les savanes du nord, quand souffle l'harmattan.",
        damage = 240, spellRadius = 3f, spellDelay = 0.7f,
    )

    val ALL: List<CardDef> = listOf(
        LUTTEUR, CHASSEURS, AGOJIE, ZANGBETO, ELEPHANT, PRETRE,
        CALAOS, ZEMIDJAN, LION, CASE, XEVIOSO, FEU,
    )

    val DEFAULT_DECK: List<CardDef> = listOf(LUTTEUR, CHASSEURS, AGOJIE, ZANGBETO, PRETRE, CALAOS, XEVIOSO, FEU)

    const val DECK_SIZE = 8

    fun byId(id: String): CardDef? = ALL.firstOrNull { it.id == id }
}
