import com.pico.miniautochess.rules.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/**
 * Runs the production Battle, never a parallel combat implementation.
 * Star allocations are frozen against the V1.7.1 base-panel formula so parameter
 * changes cannot silently change the opposing army or its purchase budget.
 */
public class BalanceTournament {
    static final LinkedHashMap<String, List<String>> COMPS = new LinkedHashMap<>();
    static {
        COMPS.put("forest_support", List.of("forest_ranger","moss_guard","forest_blessing","forest_witch","dawn_priest","ember_acolyte","copper_guard"));
        COMPS.put("rune_mage", List.of("rune_apprentice","rune_archer","rune_sage","rune_sentinel","forest_witch","core_mage","dawn_oracle"));
        COMPS.put("guard5", List.of("copper_guard","moss_guard","flame_fighter","rune_sentinel","ember_breaker","rune_sage","dawn_archer"));
        COMPS.put("archer_support", List.of("forest_ranger","rune_archer","dawn_archer","ember_acolyte","dawn_priest","forest_blessing","moss_guard"));
        COMPS.put("ember4", List.of("ember_acolyte","flame_fighter","core_mage","ember_breaker","rune_sage","rune_apprentice","copper_guard"));
        COMPS.put("dawn4", List.of("copper_guard","dawn_priest","dawn_archer","dawn_oracle","moss_guard","rune_sage","core_mage"));
        COMPS.put("mixed", List.of("copper_guard","moss_guard","forest_ranger","rune_archer","ember_acolyte","dawn_oracle","core_mage"));
    }
    static final Map<String, Float> OLD_POWER = Map.ofEntries(
        Map.entry("copper_guard",220f),Map.entry("forest_ranger",140f),Map.entry("rune_apprentice",300f),
        Map.entry("ember_acolyte",250f),Map.entry("dawn_priest",360f),Map.entry("moss_guard",340f),
        Map.entry("rune_archer",220f),Map.entry("flame_fighter",260f),Map.entry("dawn_archer",290f),
        Map.entry("forest_blessing",480f),Map.entry("rune_sage",360f),Map.entry("core_mage",400f),
        Map.entry("dawn_oracle",330f),Map.entry("forest_witch",250f),Map.entry("rune_sentinel",450f),
        Map.entry("ember_breaker",190f));
    static int games;
    static final Map<String, int[]> ALLOCATION = new HashMap<>();
    static int[] stars(String key, int budget) {
        return ALLOCATION.computeIfAbsent(key+budget, ignored -> {
            int[] best = null; double bestScore = -1; int bestDiff = 100;
            for (int code=0; code<2187; code++) {
                int n=code,cost=0; double score=0; int[] s=new int[7];
                for(int i=0;i<7;i++) {
                    s[i]=n%3+1; n/=3;
                    HeroDef h=Catalog.INSTANCE.hero(COMPS.get(key).get(i));
                    cost+=h.getCost()*Catalog.INSTANCE.copies(s[i]);
                    score+=(h.getHealth()+h.getAttack()/h.getInterval()*12+OLD_POWER.get(h.getId())*3)
                        *Catalog.INSTANCE.starMultiplier(s[i]);
                }
                int diff=Math.abs(cost-budget);
                if(diff<=1 && (diff<bestDiff || (diff==bestDiff && score>bestScore))) {
                    best=s; bestDiff=diff; bestScore=score;
                }
            }
            if(best==null) throw new AssertionError("No budget allocation: "+key+"/"+budget);
            return best;
        });
    }
    static List<UnitSnapshot> army(String key,int budget,boolean right,int seed) {
        Random r=new Random(seed);
        List<Integer> front=new ArrayList<>(),rear=new ArrayList<>();
        // Layout 0: standard front; 1: spread; 2: staggered; 3: random two rows.
        int layout=Math.floorMod(seed,4);
        if(layout==0) {
            front.addAll(List.of(3,4,2,5,1,6,0,7));
            rear.addAll(List.of(26,29,24,31,27,28,25,30));
        } else if(layout==1) {
            front.addAll(List.of(0,7,2,5,3,4,1,6));
            rear.addAll(List.of(24,31,26,29,25,30,27,28));
        } else {
            for(int row=0;row<2;row++) for(int col=0;col<8;col++) front.add(row*8+col);
            for(int row=2;row<4;row++) for(int col=0;col<8;col++) rear.add(row*8+col);
            Collections.shuffle(front,r);Collections.shuffle(rear,r);
        }
        int fi=0,ri=0,uid=right?100:1; int[] s=stars(key,budget);
        List<UnitSnapshot> result=new ArrayList<>();
        for(int i=0;i<7;i++) {
            String id=COMPS.get(key).get(i);
            int slot=Catalog.INSTANCE.hero(id).getJob()==Job.GUARD?front.get(fi++):rear.get(ri++);
            if(right) slot=slot/8*8+7-slot%8;
            result.add(new UnitSnapshot(uid++,id,s[i],slot));
        }
        if(result.stream().map(UnitSnapshot::getSlot).distinct().count()!=7) throw new AssertionError("Overlap");
        if(Math.abs(result.stream().mapToInt(UnitSnapshot::getValue).sum()-budget)>1) throw new AssertionError("Budget");
        return result;
    }
    static BattleSnapshot fight(List<UnitSnapshot> a,List<UnitSnapshot> b) {
        Battle battle=new Battle(games++,new Army(0,a,false),new Army(1,b,false),Catalog.BATTLE_LIMIT);
        for(int i=0;i<401 && battle.getOutcome()==null;i++) battle.step();
        if(battle.getOutcome()==null) throw new AssertionError("Unfinished");
        return battle.snapshot();
    }
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args[0]); int first=Integer.parseInt(args[1]),last=Integer.parseInt(args[2]);
        if(!Files.isDirectory(dir)) throw new AssertionError("Missing output folder");
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(dir.resolve("lineups.csv")));
            PrintWriter defs=new PrintWriter(Files.newBufferedWriter(dir.resolve("armies.csv")))) {
            out.println("budget,left_comp,right_comp,left_gold,right_gold,seed,outcome,wall_seconds,left_alive,right_alive");
            defs.println("comp,budget,gold,heroes_and_stars");
            for(int budget:List.of(36,48,60)) {
                for(String a:COMPS.keySet()) {
                    List<UnitSnapshot> ref=army(a,budget,false,first);
                    defs.printf("%s,%d,%d,%s%n",a,budget,ref.stream().mapToInt(UnitSnapshot::getValue).sum(),
                        String.join("|",ref.stream().map(u->u.getHeroId()+":"+u.getStar()).toList()));
                    for(String b:COMPS.keySet()) if(!a.equals(b)) for(int seed=first;seed<=last;seed++) {
                        List<UnitSnapshot> left=army(a,budget,false,seed),right=army(b,budget,true,seed);
                        BattleSnapshot s=fight(left,right);
                        out.printf(Locale.US,"%d,%s,%s,%d,%d,%d,%s,%.3f,%d,%d%n",
                            budget,a,b,left.stream().mapToInt(UnitSnapshot::getValue).sum(),
                            right.stream().mapToInt(UnitSnapshot::getValue).sum(),seed,s.getOutcome(),s.getWallElapsed(),
                            s.getFighters().stream().filter(f->f.getTeam()==0 && f.getHealth()>0).count(),
                            s.getFighters().stream().filter(f->f.getTeam()==1 && f.getHealth()>0).count());
                    }
                }
                System.out.println("BUDGET_DONE "+budget+" games="+games);
            }
        }
        System.out.println("COMPLETE games="+games);
    }
}
