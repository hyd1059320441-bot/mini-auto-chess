import com.pico.miniautochess.rules.*;
import java.util.*;
import java.io.*;

/** Same policy at all four seats, continuing after the human dies for champion counts. */
public class SeatFairnessAudit {
    public static void main(String[] args) throws Exception {
        int count = Integer.parseInt(args[1]);
        int[] champions = new int[4], wins = new int[4], matches = new int[4];
        try (PrintWriter out = new PrintWriter(args[0])) {
            out.println("seed,champion,rounds,seconds");
            for (int i = 0; i < count; i++) {
                GameEngine game = new GameEngine(i + 1001, true,
                    List.of(AiStyle.FLEX, AiStyle.FLEX, AiStyle.FLEX, AiStyle.FLEX), false);
                game.start();
                Phase prior = game.getPhase();
                while (game.getPhase() != Phase.FINISHED && game.getElapsed() < 9000 && game.getRound() < 120) {
                    game.advance(Catalog.STEP);
                    game.assertInvariants();
                    if (prior == Phase.BATTLE && game.getPhase() != Phase.BATTLE) {
                        for (RoundResult result : game.snapshot().getResults()) {
                            matches[result.getPlayerId()]++;
                            if (result.getResult().equals("胜利")) wins[result.getPlayerId()]++;
                        }
                    }
                    prior = game.getPhase();
                }
                if (game.getPhase() != Phase.FINISHED) throw new AssertionError("Not finished " + i);
                MatchSnapshot end = game.snapshot();
                int winner = end.getRanking().get(0).getPlayerId();
                champions[winner]++;
                out.printf(Locale.US, "%d,%d,%d,%.1f%n", i + 1001, winner, game.getRound(), game.getElapsed());
            }
        }
        System.out.println("games=" + count + " champions=" + Arrays.toString(champions));
        System.out.println("battle_wins=" + Arrays.toString(wins) + " battle_matches=" + Arrays.toString(matches));
    }
}
