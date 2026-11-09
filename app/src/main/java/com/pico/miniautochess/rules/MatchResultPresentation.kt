package com.pico.miniautochess.rules

data class MatchResultPresentation(val victory: Boolean, val title: String, val detail: String) {
    companion object {
        fun from(state: MatchSnapshot): MatchResultPresentation? {
            if (state.phase != Phase.FINISHED) return null
            val rank = state.ranking.first { it.playerId == 0 }.rank
            val victory = state.human.alive && rank == 1
            return MatchResultPresentation(victory, if (victory) "胜利" else "失败",
                "本局第 $rank 名 · 第 ${state.round} 回合\n赢下 ${state.human.wins} 场交锋" +
                    if (victory) "\n你是最后存活的棋手" else "\n你的生命归零，本局已结束")
        }
    }
}
