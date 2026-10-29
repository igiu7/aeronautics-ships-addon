# Aeronautics Ships Addon

An addon for **Create Aeronautics / Create Simulated** that runs on **Sable** (NeoForge 1.21.1).

## What it does

**Sail thrust.** Every sail on a ship (a Sable sub-level) produces a small thrust toward its front.
Sails placed directly on the ship count, and so do sails on contraptions (e.g. a mechanical bearing) placed on the ship.
Sail frames and propellers from Aeronautics get no thrust from wind.
Total thrust = `baseThrustPerSail × sail_count^sailCountExponent × wind_multiplier`.

**Sail front.**
- Regular Create sail: the front is the side *facing away from the player* at the moment of placement (Create stores the direction anyway). A wrench rotates it.
- Symmetric Sail: gets a new property, `front_positive`.
- Every sail (including ones placed against another sail with the placement helper) gets its front toward the direction the placing player is looking. On a ship, the direction is calculated in the ship's coordinate space.
  **Wrench on a flat face** flips front/back. Wrench on an edge rotates as usual, and Shift+wrench picks the block up.
- After placing or flipping, you get a message with the direction. When you look at a sail, you see a golden arrow on its front and text under the crosshair showing the direction and the current wind multiplier.

**Sea of Thieves-style wind.**
- There is one global wind per dimension (with a sky). Every 15 minutes it picks a new direction and gradually turns toward it (1.5°/s). Rain and storms strengthen it.
- Dense white wind streaks appear only around nearby ship sails (the server sends sail positions every second, including sails on bearings). They wave and sometimes loop (custom renderer, no extra libraries).
- The thrust multiplier depends on the angle between the **sail's front and the wind**:
  wind at the sail's back gives up to ×2.5, wind from the side gives ~×1, and wind straight into the front gives ×0.15 (at full wind strength; at weak wind it tends toward ×1).

**Sails on a bearing (like yards in SoT).** The ship always travels in the direction the sail was facing when the contraption was assembled (i.e. the "ship's front"). Rotating the bearing doesn't change the thrust direction, only how much wind you catch.
Set the bearing so the sails' front is as downwind as possible. Rotation beyond `maxSailTurnAngle` (90°) gives no further benefit, so you can't sail upwind by turning the sails backwards.
Rotated sails don't heel the ship over: Sable's aerodynamic torque for sails is disabled (`sailAerodynamicTorque = 0`), while drag and drift remain.

## Rudder

The **Rudder** block is a Create kinetic block. It's in the Aeronautics Valoris tab, with the recipe `andesite casing + shaft / log + planks / log + planks` (`AS / LP / LP`).

- **The shaft enters from the top.** Connect it to a **Steering Wheel** from Create Simulated (shaft down to the rudder, through gearboxes if needed).
- The Steering Wheel turns the shaft by as many degrees as you turn the wheel. The rudder counts the shaft rotation and scales it:
  `deflection = shaft_rotation / shaftDegreesForFullDeflection × maxDeflection`. By default, ±180° of wheel gives ±45° of fin.
  If you set a different angle limit on the wheel (scroll), set the same value in the `shaftDegreesForFullDeflection` config.
  Gearing that changes speed (e.g. a large and small cogwheel) also changes the scale.
- **Placement:** the fin always points toward the player, i.e. the side of the block you're looking at it from. The ship's front is the opposite side (where the bracket to the hull is).
- It works only **in water** (the rudder block or the block below it in water). It turns even from 0 m/s.
- Turning: `ω = maxTurnRate × effectiveness × (s + (1−s) × v / (v + speedForHalfTurnRate))`, where `s = stationaryTurnFactor` (35% of turning is available while stationary).
  The default maxTurnRate is 40°/s. Faster means a faster turn, but the turning circle radius `r = v/ω` grows with speed.
  Effectiveness is highest at 45° deflection. Torque is limited (`torquePerSpeed × rudder_count × (1 + v)`), so heavy ships respond more slowly.
- In reverse, the rudder works the opposite way and more weakly.
- If the wheel turns the ship the wrong way: set `invertSteering = true` in the `[rudder]` section of the server config.
- The rudder tracks the wheel's exact rotation (like a Mechanical Bearing), so it doesn't drift out of sync after many turns. If the fin isn't straight when the wheel is at 0°, click the rudder with a **wrench** (without Shift) to center it.
- A rudder on a mechanical bearing also works (the bearing's rotation is the deflection), but a shaft from a Steering Wheel is recommended.

## Anchor (Anchor Winch)

The **Anchor Winch** block has the recipe: `chain` on top, `planks / log / planks` in the middle, `ingot / iron block / ingot` on the bottom.

- **Placement:** stand on deck and look toward the side of the ship where the anchor should go. The beam with the rope wheel sticks out that way. It's best placed right at the edge of the deck.
- **Right click:**
  - anchor stowed → drop. The anchor falls on a rope (`dropSpeed`, 8 blocks/s) below the rope wheel and travels with the ship.
  - anchor falling or resting on the seabed → raise. This takes `depth / raiseSpeed` (1.5 blocks/s), e.g. 15 blocks takes 10 s. The time is shown above the bar.
  - while raising → drop again.
- **On the seabed:** the anchor stays still, this ship's sails stop pushing, and the ship is **completely stopped**. Horizontal speed and rotation are damped, and the ship is pulled back over the spot where the anchor caught, so wind and sails can't move it. Vertical motion and tilt are left to physics (High Seas / Sable).
- If the rope (`maxRopeLength`, 128) runs out before reaching the bottom, the anchor just hangs and doesn't hold.
- The anchor is an entity. The rope is drawn from its eye to the winch's rope wheel and follows the ship.
- Destroying the winch removes the anchor.

## Stability

- **Stabilizer** (ships with sails or a rudder that float on water): straightens out roll and pitch and damps swaying, like heavy ballast in the keel. It doesn't affect horizontal rotation, so turning works normally. Up to 3° of tilt is left alone (High Seas waves).
- **Keel** (when the rudder is in water): sideways slip is damped at the center of mass, and part of it is converted into forward motion. In a turn, the ship follows its bow instead of sliding sideways and heeling over.
- Settings are in the `[stability]` section.

## Commands

- `/wind` shows the current wind.
- `/wind set <heading> [strength]` (op) sets and locks the wind. Heading is the direction the wind blows toward: 0 = N, 90 = E, 180 = S, 270 = W. Strength is 0–1.
- `/wind random` (op) unlocks the wind and rolls a new one.

## Configuration

- `serverconfig/aeronauticsvaloris-server.toml` (in the world folder) holds thrust, multipliers, and wind behavior.
  To start, try adjusting `baseThrustPerSail`, `windMaxMultiplier`, and `applyThrustAtCenterOfMass`.
- `config/aeronauticsvaloris-client.toml` holds the streaks, HUD, and arrow.

Both files can also be edited in-game: Mods → Aeronautics Valoris → Config.

## Building

1. Copy the contents into your MDK project (keep your own `gradlew` and `gradle/wrapper`).
   If your MDK uses a different version of the `net.neoforged.moddev` plugin, keep yours.
2. Sable and its API are downloaded from `maven.blamejared.com` (set `sable_version` in `gradle.properties` to the version from your instance).
   If the maven doesn't work, put the Sable jars (+ sable-companion, veil) in `libs/`.
3. For `runClient`, put the Create, Create Aeronautics (bundled), and Sable jars in `run-mods/`.
4. `./gradlew build` → `build/libs/aeronauticsvaloris-1.0.0.jar`.

Required in-game: Create, Create Simulated/Aeronautics, Sable.
