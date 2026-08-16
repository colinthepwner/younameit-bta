package net.colin.younameit.item;

import net.colin.younameit.set.Archetype;
import net.colin.younameit.set.MaterialSet;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class YniLore {
    private YniLore() {}

    private static final Archetype[] ORDER = {
            Archetype.SOFT, Archetype.PLANT, Archetype.FOOD, Archetype.BONE, Archetype.GLASS,
            Archetype.WOOD, Archetype.STONE, Archetype.METAL, Archetype.GEM, Archetype.UNKNOWN,
    };

    private static final Map<Archetype, Integer> COLUMN = new EnumMap<>(Archetype.class);

    private static final Map<String, String[]> LINES = new HashMap<>();

    static {
        for (int i = 0; i < ORDER.length; i++) COLUMN.put(ORDER[i], i);

        LINES.put("Pickaxe", new String[]{
                "A pickaxe that dents well before the stone does.",
                "It parts soil enthusiastically and rock not at all.",
                "Every swing is a negotiation between mining and a snack.",
                "It picks at stone the way something picks at a carcass.",
                "Every strike is a wager on which of the two breaks first.",
                "The pickaxe you make when you have nothing and need everything.",
                "Rock against rock, and the patient one wins.",
                "It goes into stone as though it had always been meant to.",
                "Stone yields to it without much of an argument.",
                "It mines, and the how of it is not forthcoming.",
        });
        LINES.put("Axe", new String[]{
                "Less an axe than a firmly worded suggestion to the tree.",
                "It cuts what it used to be, which cannot be comfortable.",
                "The tree is unharmed and you are getting hungry.",
                "It splits wood with a dry, unpleasant clack.",
                "Shockingly sharp, and shockingly brief.",
                "Wood against wood, settled by whoever swings harder.",
                "Heavy enough that technique becomes optional.",
                "It bites deep and comes away clean.",
                "Timber falls before it has finished registering the blow.",
                "It fells things. Ask nothing further of it.",
        });
        LINES.put("Shovel", new String[]{
                "It moves earth much the way a bare hand would.",
                "Springy, light, and forever on the verge of folding.",
                "Digging with dinner, which rather sets the tone for the day.",
                "It scrapes through soil with unsettling familiarity.",
                "Beautiful in the dirt, and not long for it.",
                "The first shovel, and the one always within reach.",
                "It does not dig quickly, it simply does not stop.",
                "Earth comes away in clean, obedient loads.",
                "Far too fine for this, and it cuts ground like nothing at all.",
                "It shifts ground by means best left unexamined.",
        });
        LINES.put("Hoe", new String[]{
                "It combs the soil rather than breaking it.",
                "Turning the earth with the very stuff that will grow back in it.",
                "Farming with food is either deep wisdom or a shortcut.",
                "It rakes furrows with a sound the field will remember.",
                "It scores the earth in perfect lines, once or twice.",
                "Plain, worn smooth, and older than the field it works.",
                "Heavy work, done heavily, and done.",
                "Furrows open ahead of it in straight, willing rows.",
                "Absurdly precious for the soil, and the soil knows it.",
                "It tills. The field has stopped asking questions.",
        });
        LINES.put("Sword", new String[]{
                "It lands with a noise closer to an apology.",
                "Green, whippy, and unlikely to settle any argument.",
                "Threatening to the appetite and to very little else.",
                "Light and jagged; it does not cut so much as tear.",
                "Appalling idea, magnificent edge, very short story.",
                "The first sword, and rarely anybody's last.",
                "It does not slice so much as settle things by sheer mass.",
                "Balanced, honed, and entirely without sentiment.",
                "It passes through with almost no sound at all.",
                "It ends fights, though the mechanism remains unclear.",
        });
        LINES.put("Helmet", new String[]{
                "It keeps the rain off and the optimism up.",
                "A woven crown that rustles when you turn your head.",
                "Wearing lunch, and hoping nothing nearby is hungrier than you.",
                "Somebody else's skull, standing in for yours.",
                "You can see out of it beautifully, right up until you cannot.",
                "A bucket with ambitions.",
                "Your neck will start complaining long before your skull does.",
                "It rings once, and you keep walking.",
                "Worth rather more than the head it is protecting.",
                "It covers the head and has not been asked to justify itself.",
        });
        LINES.put("Chestplate", new String[]{
                "Thick, warm, and no obstacle whatsoever to anything sharp.",
                "Woven close, and it smells faintly of the field.",
                "Everything that strikes it makes a wet, disappointing sound.",
                "A ribcage worn on the outside of another ribcage.",
                "Radiant, translucent, and one solid hit from confetti.",
                "Planks across the chest, which beats no planks across the chest.",
                "It stops a great deal, and slows you by very nearly as much.",
                "Fitted close and hammered flat, exactly as intended.",
                "Faceted plating that scatters light and blades with equal ease.",
                "Plating of uncertain origin that has not failed yet.",
        });
        LINES.put("Leggings", new String[]{
                "Comfortable in a way that armour is not supposed to be.",
                "They creak like a hedge with every stride.",
                "Every step is a reminder that this was a decision you made.",
                "They fit the leg unnervingly well.",
                "Walking is fine. Sitting down is a commitment.",
                "Stiff, honest, and audible from a fair distance.",
                "Every step lands like it means it.",
                "They move with the leg instead of against it.",
                "Preposterous, priceless, and genuinely effective.",
                "Leg plating that declines to be catalogued.",
        });
        LINES.put("Boots", new String[]{
                "Every landing is quieter than it has earned.",
                "Woven soles that give with the ground underfoot.",
                "Regrettable underfoot, and considerably worse in the sun.",
                "They click on stone like something following close behind.",
                "Every single step is an act of faith.",
                "Clogs, essentially, and none the worse for it.",
                "You will feel every step, and so will the ground.",
                "Properly shod at last, and it shows in the stride.",
                "Walking about on a small fortune, and it holds.",
                "Footwear that keeps its own counsel.",
        });
    }

    private static String silkLine(String piece) {
        switch (piece) {
            case "Pickaxe": return "It lifts stone out whole, as though nothing had happened to it.";
            case "Axe":     return "Whatever it takes down comes away intact.";
            case "Shovel":  return "The ground comes up in one piece, exactly as it lay.";
            case "Hoe":     return "It works the soil without ever quite disturbing it.";
            case "Sword":   return "Soft and precious, and it leaves things curiously unspoiled.";
            default:        return null;
        }
    }

    public static String describe(MaterialSet set, String pieceName, boolean tool) {
        if (tool && set.stats.silkTouch) {
            String silk = silkLine(pieceName);
            if (silk != null) return silk;
        }
        String[] byArchetype = LINES.get(pieceName);
        if (byArchetype == null) return "Made to be used, and to be used up.";

        Integer i = COLUMN.get(set.stats.archetype);
        if (i == null || i >= byArchetype.length) i = byArchetype.length - 1;
        return byArchetype[i];
    }
}
