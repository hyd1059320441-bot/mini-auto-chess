import com.pico.miniautochess.rules.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Exhaustive star/cost checks and catalog export using compiled production rules. */
public class ProgressionAudit {
    static int games;
    static BattleSnapshot fight(List<UnitSnapshot> a, List<UnitSnapshot> b) {
        Battle battle=new Battle(games++,new Army(0,a,false),new Army(1,b,false),Catalog.BATTLE_LIMIT);
        for(int i=0;i<401 && battle.getOutcome()==null;i++) battle.step();
        if(battle.getOutcome()==null) throw new AssertionError("Unfinished");
        return battle.snapshot();
    }
    static List<UnitSnapshot> trio(String hero,int star,boolean right,int layout) {
        int a=layout==0?2:1,b=layout==0?5:6,c=layout==0?3:4;
        if(right) { a=7-a;b=7-b;c=7-c; }
        int uid=right?100:1;
        return List.of(new UnitSnapshot(uid,"copper_guard",star,a),
            new UnitSnapshot(uid+1,"moss_guard",star,b),
            new UnitSnapshot(uid+2,hero,star,c+(Catalog.INSTANCE.hero(hero).getJob()==Job.GUARD?0:24)));
    }
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args[0]);
        if(!Files.isDirectory(dir)) throw new AssertionError("Missing output directory");
        List<HeroDef> heroes=Catalog.INSTANCE.getHeroList();
        int[][] layouts={{3,4},{27,28},{0,0},{3,3}};
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(dir.resolve("duels.csv")))) {
            out.println("left_id,left_star,right_id,right_star,layout,outcome,wall_seconds");
            for(HeroDef a:heroes) for(int as=1;as<=3;as++) for(HeroDef b:heroes) for(int bs=1;bs<=3;bs++)
                for(int i=0;i<layouts.length;i++) {
                    BattleSnapshot s=fight(List.of(new UnitSnapshot(1,a.getId(),as,layouts[i][0])),
                        List.of(new UnitSnapshot(100,b.getId(),bs,layouts[i][1])));
                    out.printf(Locale.US,"%s,%d,%s,%d,%d,%s,%.3f%n",a.getId(),as,b.getId(),bs,i,s.getOutcome(),s.getWallElapsed());
                }
        }
        System.out.println("DUELS_DONE "+games);
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(dir.resolve("trios.csv")))) {
            out.println("left_id,left_star,right_id,right_star,layout,outcome,wall_seconds");
            for(int star=1;star<=3;star++) for(HeroDef a:heroes) for(HeroDef b:heroes) for(int layout=0;layout<2;layout++) {
                BattleSnapshot s=fight(trio(a.getId(),star,false,layout),trio(b.getId(),star,true,layout));
                out.printf(Locale.US,"%s,%d,%s,%d,%d,%s,%.3f%n",a.getId(),star,b.getId(),star,layout,s.getOutcome(),s.getWallElapsed());
            }
        }
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(dir.resolve("heroes.csv")))) {
            out.println("id,name,faction,job,cost,star,copies,base_health,base_attack,base_armor,base_magic_resist,base_interval,range,skill,skill_name,description,base_skill_power,solo_health,solo_attack,solo_armor,solo_magic_resist,solo_interval,solo_skill_power,start_mana,max_mana,attack_mana,hit_mana,mana_per_second");
            for(HeroDef h:heroes) for(int star=1;star<=3;star++) {
                UnitSnapshot u=new UnitSnapshot(1,h.getId(),star,3);
                CombatStats s=CombatMath.INSTANCE.stats(u,List.of(u));
                out.printf(Locale.US,"%s,%s,%s,%s,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                    h.getId(),h.getName(),h.getFaction().getLabel(),h.getJob().getLabel(),h.getCost(),star,Catalog.INSTANCE.copies(star),
                    h.getHealth(),h.getAttack(),h.getArmor(),h.getMagicResist(),h.getInterval(),h.getRange(),
                    h.getSkill(),h.getSkillName(),h.getSkillDescription(),h.skillPower(star),
                    s.getMaxHealth(),s.getAttack(),s.getArmor(),s.getMagicResist(),s.getInterval(),s.getSpell(),s.getInitialMana(),
                    Catalog.MAX_MANA,Catalog.INSTANCE.manaOnAttack(h.getJob()),Catalog.INSTANCE.manaOnHit(h.getJob()),Catalog.INSTANCE.manaPerSecond(h.getJob()));
            }
        }
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(dir.resolve("traits.csv")))) {
            out.println("label,threshold,effect");
            for(TraitDef t:Catalog.INSTANCE.getTraitDefinitions()) for(int i=0;i<t.getThresholds().size();i++)
                out.printf("%s,%d,%s%n",t.getLabel(),t.getThresholds().get(i),t.getEffects().get(i));
        }
        System.out.println("PROGRESSION_COMPLETE "+games);
    }
}
