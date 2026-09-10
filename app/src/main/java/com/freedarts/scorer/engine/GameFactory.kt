package com.freedarts.scorer.engine

import com.freedarts.scorer.engine.games.AroundTheClockGame
import com.freedarts.scorer.engine.games.BermudaGame
import com.freedarts.scorer.engine.games.Bobs27Game
import com.freedarts.scorer.engine.games.CountUpGame
import com.freedarts.scorer.engine.games.CricketGame
import com.freedarts.scorer.engine.games.GotchaGame
import com.freedarts.scorer.engine.games.KillerGame
import com.freedarts.scorer.engine.games.OneTwentyOneGame
import com.freedarts.scorer.engine.games.RoundTheWorldGame
import com.freedarts.scorer.engine.games.RandomCheckoutGame
import com.freedarts.scorer.engine.games.SegmentTrainingGame
import com.freedarts.scorer.engine.games.ShanghaiGame
import com.freedarts.scorer.engine.games.X01Game
import com.freedarts.scorer.model.GameMode
import com.freedarts.scorer.model.GameSettings
import com.freedarts.scorer.model.Player

object GameFactory {
    fun create(players: List<Player>, settings: GameSettings, seed: Long = System.currentTimeMillis()): DartGame = when (settings.mode) {
        GameMode.X01 -> X01Game(players, settings, seed)
        GameMode.CRICKET -> CricketGame(players, settings, seed)
        GameMode.AROUND_THE_CLOCK -> AroundTheClockGame(players, settings, seed)
        GameMode.COUNT_UP -> CountUpGame(players, settings, seed)
        GameMode.RANDOM_CHECKOUT -> RandomCheckoutGame(players, settings, seed)
        GameMode.BOBS_27 -> Bobs27Game(players, settings, seed)
        GameMode.SEGMENT_TRAINING -> SegmentTrainingGame(players, settings, seed)
        GameMode.SHANGHAI -> ShanghaiGame(players, settings, seed)
        GameMode.GOTCHA -> GotchaGame(players, settings, seed)
        GameMode.BERMUDA -> BermudaGame(players, settings, seed)
        GameMode.KILLER -> KillerGame(players, settings, seed)
        GameMode.ROUND_THE_WORLD -> RoundTheWorldGame(players, settings, seed)
        GameMode.ONE_TWENTY_ONE -> OneTwentyOneGame(players, settings, seed)
    }
}
