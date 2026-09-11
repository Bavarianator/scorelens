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

/** "First to n" oder "Best of n" (Legs bzw. Sets). */
@Serializable
enum class WinMode { FIRST_TO, BEST_OF }

/**
 * Ausbullen wie bei Autodarts: OFF = Spieler 1 beginnt, NORMAL = ein Dart pro Spieler, näher am Bull beginnt,
 * OFFICIAL = wie NORMAL, bei Gleichstand wird in umgekehrter Reihenfolge erneut geworfen.
 */
@Serializable
enum class BullOff { OFF, NORMAL, OFFICIAL }

@Serializable
enum class HitMode { ANY, SINGLE, DOUBLE, TRIPLE }

/** Cricket-Wertung. TACTICS ist eine alte Einstellung (Board Tactics, Wertung Standard) und bleibt lesbar. */
@Serializable
enum class CricketVariant { STANDARD, CUT_THROAT, TACTICS, NO_SCORE }

/** Cricket-Zahlen: 15–20 + Bull, 10–20 + Bull oder sieben zufällige, bis zum ersten Treffer verdeckte Zahlen. */
@Serializable
enum class CricketBoard { CRICKET, TACTICS, HIDDEN }

/** Reihenfolge der Ziele (Around the Clock, Round the World). */
@Serializable
enum class TargetOrder { UP, DOWN, RANDOM }

/** 121: Was bei einem verfehlten Ziel passiert. */
@Serializable
enum class FailMode { SOFT, HARD_RESET, SAFEHOUSE }

@Serializable
data class GameSettings(
    val mode: GameMode = GameMode.X01,

    // X01
    val baseScore: Int = 501,
    val inMode: InMode = InMode.STRAIGHT,
    val outMode: OutMode = OutMode.DOUBLE,
    val bullMode: BullMode = BullMode.B25_50,
    val matchMode: MatchMode = MatchMode.LEGS,
    /** FIRST_TO: [legs]/[sets] Siege nötig. BEST_OF: [legs]/[sets] ist die Gesamtzahl, Mehrheit gewinnt. */
    val winMode: WinMode = WinMode.FIRST_TO,
    /** Anzahl Legs ("First to" bzw. "Best of"), bei Sets: Legs pro Set. */
    val legs: Int = 1,
    val sets: Int = 1,
    /** 0 = unbegrenzt. */
    val maxRounds: Int = 0,

    // Startspieler (alle Modi)
    /** Ausbullen vor dem Spiel. */
    val bullOff: BullOff = BullOff.OFF,
    /** Zufälliger Startspieler (ohne Bull-off). */
    val randomStarter: Boolean = false,

    // Cricket
    val cricketVariant: CricketVariant = CricketVariant.STANDARD,
    val cricketBoard: CricketBoard = CricketBoard.CRICKET,

    // Around the Clock / Segment Training / Killer
    val hitMode: HitMode = HitMode.ANY,
    val includeBull: Boolean = true,
    /** Alte Einstellung; entspricht [targetOrder] = RANDOM. */
    val randomOrder: Boolean = false,
    val targetOrder: TargetOrder = TargetOrder.UP,
    /** Around the Clock: Treffer, die pro Zahl nötig sind (1–3). */
    val hitsRequired: Int = 1,

    // Count Up / Shanghai / Random Checkout (Legs) / Round the World
    val rounds: Int = 8,

    // Random Checkout
    val checkoutMin: Int = 41,
    val checkoutMax: Int = 170,
    /** Runden (Aufnahmen) pro Leg: 1, 2, 3, 6 oder 9. */
    val checkoutRounds: Int = 1,

    // Segment Training
    val trainingSegment: Int = 20,
    /** Ende nach [hitCount] Treffern (true) oder Darts (false). */
    val endAfterHits: Boolean = false,
    val hitCount: Int = 30,

    // Bob's 27
    val allowNegative: Boolean = false,

    // Gotcha
    val gotchaTarget: Int = 301,

    // Killer
    val killerLives: Int = 3,
    val killerHitMode: HitMode = HitMode.ANY,

    // 121
    val attempts: Int = 10,
    /** 9 oder 6 Darts pro Versuch. */
    val dartsPerAttempt: Int = 9,
    val failMode: FailMode = FailMode.SOFT,
    /** Ziel steigt nach einem Erfolg um 1, 3 oder 5. */
    val step: Int = 1,
    /** Safehouse: nach jedem n-ten Erfolg wird das Ziel gesichert. */
    val safehouseEvery: Int = 1,
) {
    /** Nötige Leg-Siege (pro Set) nach [winMode]. */
    val legsToWin: Int get() = if (winMode == WinMode.BEST_OF) legs / 2 + 1 else legs
    val setsToWin: Int get() = if (winMode == WinMode.BEST_OF) sets / 2 + 1 else sets

    /** Cricket-Zahlen unter Berücksichtigung der alten Einstellung TACTICS. */
    val effectiveCricketBoard: CricketBoard get() = if (cricketVariant == CricketVariant.TACTICS) CricketBoard.TACTICS else cricketBoard
    /** Cricket-Wertung (TACTICS = Standard). */
    val cricketScoring: CricketVariant get() = if (cricketVariant == CricketVariant.TACTICS) CricketVariant.STANDARD else cricketVariant
    /** Reihenfolge unter Berücksichtigung der alten Einstellung [randomOrder]. */
    val effectiveOrder: TargetOrder get() = if (randomOrder) TargetOrder.RANDOM else targetOrder

    fun withModeDefaults(newMode: GameMode): GameSettings = when (newMode) {
        GameMode.COUNT_UP -> copy(mode = newMode, rounds = 8)
        GameMode.SHANGHAI -> copy(mode = newMode, rounds = 7)
        GameMode.RANDOM_CHECKOUT -> copy(mode = newMode, rounds = 10)
        GameMode.SEGMENT_TRAINING -> copy(mode = newMode, rounds = 10)
        GameMode.ROUND_THE_WORLD -> copy(mode = newMode, rounds = 20)
        else -> copy(mode = newMode)
    }
}
