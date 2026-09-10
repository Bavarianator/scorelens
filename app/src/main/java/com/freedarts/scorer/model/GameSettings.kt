package com.freedarts.scorer.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameMode(val title: String, val category: Category) {
    X01("X01", Category.COMPETITIVE),
    CRICKET("Cricket / Tactics", Category.COMPETITIVE),
    AROUND_THE_CLOCK("Around the Clock", Category.PRACTICE),
    ROUND_THE_WORLD("Round the World", Category.PRACTICE),
    COUNT_UP("Count Up", Category.PRACTICE),
    RANDOM_CHECKOUT("Random Checkout", Category.PRACTICE),
    BOBS_27("Bob's 27", Category.PRACTICE),
    SEGMENT_TRAINING("Segment Training", Category.PRACTICE),
    ONE_TWENTY_ONE("121", Category.PRACTICE),
    SHANGHAI("Shanghai", Category.PARTY),
    GOTCHA("Gotcha", Category.PARTY),
    BERMUDA("Bermuda", Category.PARTY),
    KILLER("Killer", Category.PARTY);

    enum class Category { COMPETITIVE, PRACTICE, PARTY }
}

@Serializable
enum class InMode { STRAIGHT, DOUBLE, MASTER }

@Serializable
enum class OutMode { STRAIGHT, DOUBLE, MASTER }

@Serializable
enum class BullMode { B25_50, B50_50 }

@Serializable
enum class MatchMode { LEGS, SETS }

@Serializable
enum class HitMode { ANY, SINGLE, DOUBLE, TRIPLE }

@Serializable
enum class CricketVariant { STANDARD, CUT_THROAT, TACTICS }

@Serializable
data class GameSettings(
    val mode: GameMode = GameMode.X01,

    // X01
    val baseScore: Int = 501,
    val inMode: InMode = InMode.STRAIGHT,
    val outMode: OutMode = OutMode.DOUBLE,
    val bullMode: BullMode = BullMode.B25_50,
    val matchMode: MatchMode = MatchMode.LEGS,
    /** Anzahl Legs, die zum Sieg (bzw. Set-Sieg) nötig sind ("First to"). */
    val legs: Int = 1,
    val sets: Int = 1,
    /** 0 = unbegrenzt. */
    val maxRounds: Int = 0,

    // Cricket
    val cricketVariant: CricketVariant = CricketVariant.STANDARD,

    // Around the Clock / Segment Training
    val hitMode: HitMode = HitMode.ANY,
    val includeBull: Boolean = true,
    val randomOrder: Boolean = false,

    // Count Up / Shanghai / Segment Training / Random Checkout
    val rounds: Int = 8,

    // Random Checkout
    val checkoutMin: Int = 41,
    val checkoutMax: Int = 170,

    // Segment Training
    val trainingSegment: Int = 20,

    // Gotcha
    val gotchaTarget: Int = 301,

    // Killer
    val killerLives: Int = 3,

    // 121
    val attempts: Int = 10,
) {
    fun withModeDefaults(newMode: GameMode): GameSettings = when (newMode) {
        GameMode.COUNT_UP -> copy(mode = newMode, rounds = 8)
        GameMode.SHANGHAI -> copy(mode = newMode, rounds = 7)
        GameMode.RANDOM_CHECKOUT -> copy(mode = newMode, rounds = 10)
        GameMode.SEGMENT_TRAINING -> copy(mode = newMode, rounds = 10)
        GameMode.ROUND_THE_WORLD -> copy(mode = newMode, rounds = 20)
        else -> copy(mode = newMode)
    }
}
