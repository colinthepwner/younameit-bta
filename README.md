# You Name It!

**A mod for [Better than Adventure!](https://betterthanadventure.net/) `8.0.1`**

Every block in the game becomes a full tool and armour set — pickaxe, axe, shovel, hoe, sword,
helmet, chestplate, leggings, boots. Not a hand-written list of them: the mod walks the block and
item registries as the game loads and generates a set for everything it finds, so blocks added by
*other* mods get sets too, with no patch and no compatibility list.

On a vanilla 8.0.1 install that's **810 sets — 7,290 items — generated in about 180 ms** at startup.

```
Scanned 16384 block slots and 32768 item slots: 810 sets
  (520 from blocks, 290 from loose items)
Generated 810 material sets (7290 items) in 179 ms.
```

## Requirements

| | |
|---|---|
| **Better than Adventure!** | `8.0.1` (release channel) |
| **Mod loader** | Babric / Fabric loader `0.18.4-bta.11`+ |
| **[HalpLibe](https://github.com/Turnip-Labs/bta-halplibe)** | `6.1.0`+ — **required**, not bundled |
| Java | 17+ |
| [ModMenu](https://github.com/Turnip-Labs/bta-modmenu) | optional |

HalpLibe is deliberately *not* shaded into the jar, so it won't shadow a newer copy you already
have installed.

## Install

1. Grab `younameit-BTA8.0.X-obf.jar` from the [Releases page](../../releases).
2. Drop it in your instance's `.minecraft/mods/` folder, next to HalpLibe.
3. Launch.

## The textures are traced from vanilla, not hand-drawn

This is the part that makes the whole thing work. Painting 7,290 sprites by hand is off the table,
and recolouring a single template sprite looks obviously wrong — a stone pickaxe and a diamond
pickaxe are not the same picture in two colours.

So the mod reads two of BTA's *own* sprites for the same tool in different materials — say
`tool_pickaxe_iron.png` against `tool_pickaxe_diamond.png` — and diffs them:

- pixels that **differ** between the two are the material, and get repainted from the new block's
  palette;
- pixels that are **byte-identical** don't depend on the material at all — a tool's wooden handle,
  an armour icon's dark outline, the near-white specular highlight — and are left where the artist
  put them.

Everything per-tool then falls out for free: the sword's shorter offset grip, the pickaxe's split
upper prongs, exactly which armour pixels gleam. It also means a resource pack that redraws the
vanilla tools is *followed* rather than fought, since the mod is reading whatever sprites are
actually loaded.

A few details that took some doing:

- **Outlines come from the sprite's own darkest shade**, not from geometry. Treating "body pixel
  touching a non-body pixel" as an edge marks *every* pixel of a thin part as outline, which flattens
  the pickaxe's 1–2px prongs into a solid blob. Banding on luminance instead reproduces vanilla's
  lit centre.
- **Armour gets no second outline.** Its shared-pixel ring already is the outline; adding another
  squeezed the texture down to a sliver.
- **Highlights are shared pixels, not bright body pixels.** White stays white whatever the armour
  is made of, so highlights land in the material-independent bucket right alongside the black
  outline. Splitting that bucket by brightness recovers them exactly where they were drawn.
- **Holes are filled by dilation**, not by copying the nearest opaque pixel — nearest-opaque turns
  mostly-transparent sources like feathers and saplings into checkerboard noise.

## Balance

Blocks are rated on **hardness and blast resistance**, with the tier capped at
`requiredHarvestLevel + 1` — so cobblestone can reach stone tier but never iron, and a block you
can punch out doesn't hand you a diamond-tier pickaxe.

Loose items (feathers, bones, string) have no hardness to read, so they're sorted into an
archetype — `SOFT`, `PLANT`, `FOOD`, `BONE`, `GLASS`, `WOOD`, `STONE`, `METAL`, `GEM`, `UNKNOWN` —
by BTA's own block material first, then by English name keywords, then by generic signals. Anything
unrecognised lands in `UNKNOWN` and is capped at wood tier, which is the safe direction to be wrong
in for a modded item nobody's taught it about.

For scale, against vanilla's `wood 64 / stone 128 / iron 384 / diamond 1536` durability ladder:

| Material | Tier | Durability | Efficiency | Armour |
|---|---|---|---|---|
| Dirt | 0 | 7 | 1.20 | 37 dur / 7% |
| Oak planks | 0 | 64 | 2.00 | 150 dur / 20% |
| Cobblestone | 1 | 128 | 4.00 | 220 dur / 32% |
| Block of iron | 2 | 499 | 6.90 | 494 dur / 52% |

Other touches: a tool is faster on **its own** block; sets made of fireproof materials grant fire
and lava immunity as a full-set bonus; and recipes are suppressed for any material that some
*existing* recipe already turns into gear — checked against the live recipe table, so other mods
are deferred to as well as vanilla.

## Config

Written to `config/younameit.properties` on first launch.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Master switch |
| `generateTools` | `true` | Generate the 5 tools |
| `generateArmor` | `true` | Generate the 4 armour pieces |
| `generateRecipes` | `true` | Register crafting recipes |
| `ownBlockBonus` | `1.6` | Speed multiplier on a tool's own block |
| `fireResistantSetBonus` | `true` | Full-set fire/lava immunity for fireproof materials |
| `fuelReducesFireDamage` | `true` | Sets made of furnace fuel take *less* fire damage |
| `skipUntexturedBlocks` | `true` | Skip blocks whose texture can't be resolved |
| `maxVariantsPerBlock` | `16` | Cap when splitting a block into metadata variants |
| `itemIdBase` | `14000` | First item id handed out |
| `itemIdMax` | `32767` | Ceiling, so huge packs degrade instead of overrunning |
| `blockIdHeadroom` | `1024` | Ids left clear above the highest block, for later mods |

`fuelReducesFireDamage` is the literal reading — burnable material, *better* fire protection. Flip
it if you'd rather flammable things be worse in a fire.

## Building

```bash
./gradlew build
```

Produces three jars in `build/libs/`:

| Jar | |
|---|---|
| `younameit-<version>+8.0.1.jar` | the normal build |
| `younameit-BTA8.0.X-obf.jar` | ProGuard-renamed, the one that ships in Releases |
| `younameit-<version>+8.0.1-sources.jar` | sources |

The obfuscated build is a **renamer only** — no shrinking, no optimising. Shrinking would decide the
item subclasses are unreachable, and optimising would move code a mixin injects into. Two things
stay readable on purpose: the entrypoint classes named in `fabric.mod.json`, and the entire mixin
package, because Mixin matches `@Shadow` members to their target *by name* and a renamed one is a
silent no-match that drops the injection rather than a loud error.

## License

[CC0 1.0 Universal](LICENSE) — public domain. Do whatever you like with it.
