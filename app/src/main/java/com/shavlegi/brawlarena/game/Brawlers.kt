package com.shavlegi.brawlarena.game

import com.shavlegi.brawlarena.R

/** Weapon behaviour: how the brawler's shots travel. */
enum class WeaponKind { SHOTGUN, RIFLE, LOBBER }

/**
 * Static definition of a brawler. All numbers are in world units:
 * arena is 1700 x 1100, fighter radius is 26.
 */
data class BrawlerDef(
    val id: String,
    val nameRes: Int,
    val descRes: Int,
    /** ARGB color of the fighter's body. */
    val color: Long,
    val hp: Int,
    /** World units per second. */
    val speed: Float,
    /** Attack range in world units. */
    val range: Float,
    /** Damage of one pellet / bullet / bomb. */
    val damage: Int,
    /** Projectiles per normal shot (5 for shotgun, 1 otherwise). */
    val pellets: Int,
    /** Total spread of a multi-pellet shot, in degrees. */
    val spreadDeg: Float,
    val cooldownMs: Long,
    /** Projectile speed (units/s) for shotgun / rifle. Unused for lobber. */
    val projectileSpeed: Float,
    /** Flight time of a lobbed bomb, in ms. Unused for shotgun / rifle. */
    val flightMs: Long,
    /** Damage of the super attack. */
    val superDamage: Int,
    val weapon: WeaponKind
) {
    companion object {
        val GROM = BrawlerDef(
            id = "grom",
            nameRes = R.string.brawler_grom_name,
            descRes = R.string.brawler_grom_desc,
            color = 0xFFFF8A3D,
            hp = 5200,
            speed = 250f,
            range = 280f,
            damage = 430,
            pellets = 5,
            spreadDeg = 40f,
            cooldownMs = 850L,
            projectileSpeed = 950f,
            flightMs = 0L,
            superDamage = 500,
            weapon = WeaponKind.SHOTGUN
        )

        val HUNTER = BrawlerDef(
            id = "hunter",
            nameRes = R.string.brawler_hunter_name,
            descRes = R.string.brawler_hunter_desc,
            color = 0xFF4C8DFF,
            hp = 3600,
            speed = 270f,
            range = 720f,
            damage = 1150,
            pellets = 1,
            spreadDeg = 0f,
            cooldownMs = 1100L,
            projectileSpeed = 1400f,
            flightMs = 0L,
            superDamage = 2600,
            weapon = WeaponKind.RIFLE
        )

        val GRENADIER = BrawlerDef(
            id = "grenadier",
            nameRes = R.string.brawler_grenadier_name,
            descRes = R.string.brawler_grenadier_desc,
            color = 0xFF4CAF50,
            hp = 4300,
            speed = 240f,
            range = 560f,
            damage = 1300,
            pellets = 1,
            spreadDeg = 0f,
            cooldownMs = 1300L,
            projectileSpeed = 0f,
            flightMs = 550L,
            superDamage = 2600,
            weapon = WeaponKind.LOBBER
        )

        val ALL: List<BrawlerDef> = listOf(GROM, HUNTER, GRENADIER)

        fun byId(id: String): BrawlerDef = ALL.firstOrNull { it.id == id } ?: GROM
    }
}

/** Trophy change by final placement (1 = winner). Total trophies never go below 0. */
fun trophiesForPlacement(placement: Int): Int = when (placement) {
    1 -> 10
    2 -> 6
    3 -> 3
    4 -> 0
    5 -> -2
    else -> -4
}
