Vectors of Viral Disease:



* Wetness/Coldness
* Seasonal
* Infected Reservoirs
* Contagion



Vectors of Bacterial Disease:



* Open Wounds
* Enemy damage (swords, zombies, etc)
* Consumption of raw/rotten food
* Drowning



Vectors of Fungal Disease:

* Hygiene-Environment



Vectors of Parasitic Disease:

* Consumption of raw/rotten food
* Enemy damage (mosquitoes, etc)



Localize all disease attribute debuffs based on the area of infection rather than the individual virus:



* Respiratory: Mining Speed, Attack Speed, Stamina (Stamina Mod Compat)+
* Gastrointestinal: Max Saturation
* Heart/Blood/Systemic: Max Health
* Skin/Muscle: Attack Damage, Knockback Damage
* Bones: Toughness, Knockback Resistance
* Brain: Experience Gain, Max Mana (Iron Spells Compat)

Currently, when a player has multiple diseases on simultaneously, all of them provide particle effects on the player simultaneously, and presumably if in theory a player had two diseases that are both contagious to players/villagers in the way that colds/flus/rsv are now, both would be contagious at once. A seniority where only the most severely feverish disease is actively contagious/emits player particles should be in effect (when both are equally feverish it should be based on pain and then occurence [latest should take precedence]). Symptoms should no longer be frozen of the source disease when a complication begins accumulation. Debug UIs should be updated to account for the new updates, especially in regards to pathogen-organ exclusion instead of mere pathogen exclusion and account for the potential for multiple diseases of the same pathogen/organ type to occur on the player at once (such as the symptom pool, episode timer, etc).

~~Rebalance bleeding damage as it is way too strong currently~~ — Resolved: bleeding/internal bleeding/blood loss mechanic removed entirely; blood visuals now driven by flesh wounds

Fix bleeding particle effect consistency (align with majrusz)

Fix chance of cellulitis possibly being too low as it is currently

Check if immunity impacts worsening chance

Give disease mob effects special colors and re-arrange mob effect priority on the player HUD

Complication accumulation should not cease symptoms of the source disease

~~Seperate disease exclusion into organs *and* pathogen type rather than just grouped by pathogen type as it is now (sepsis should act as the organ type of whatever its source disease was)~~

~~Change the fever system: currently players can exploit by staying in a cold biome wiyh a very light cold that never gets cured~~

Sepsis should be a unique disease wherein inheritance only symptoms should serve as an additional fourth symptom representing the continuation of the source disease

~~Adding tooltips/icons for each symptom on the disease mob effect itself~~

~~Sepsis should not replace the static symptoms of its source disease~~

On-player visible wounds/infections possibility

~~Refactor sharp pain to be just pain, and mof to be just mof~~

~~Fever should not raise world temp~~

~~Sore throat should cause damage when eating~~

~~Flu should lock-in like norovirus~~

~~Some symptoms should be static: exist on the player for the entire duration of the disease rather than episodic~~

Organ failure complications should have a unique tooltip and cause dying hearts to be grey

Give all of the disease/injury related deaths proper death messages

Sleeping should provide symptom management effect for gating spam

Pigs/chickens should possibly (5% chance) get influenza per flu season

Disable norovirus in oceans

When treatment is successful it should only stop the episodes of the disease it was successfully applied to

~~Fever temperature gating is too high (current mild fever effect should be equivalent to severe fever).~~

~~Stomach cramp should stop healing from hunger~~

~~Unify post-disease viral immunity~~

~~If a player is damp yet temp is above 1.0 can the player still get sick~~

~~Make debilitating influenza cause shivering~~

~~Make the particles bigger~~

~~Drying rate should also connect to body temp~~

~~Maybe change the way reservoirs work to rather than only accumulating norovirus, whenever a player enters into infected reservoir it should randomly pick a number between 0.5 and 1.0 (inclusive), added onto the current norovirus accumulation and start adding onto it that amount gradually (in the same rate as if you are wading over infected reservoir water). What would the implications of this be? Just talk theory don't implement anything for now~~

~~When a cure is consumed while a complication is developing it should affect the source disease only first. But once the complication becomes a full disease (latches), cures should directly reduce it (as the source disease basically is absorbed by it)~~

~~Complications like pneumonia should take all the accumulation of its source disease at the moment of latching~~



~~Probably also refactor "dose" to "viral incubation"~~



~~When pneumonia latches, it currently does not add on the accumulation of the source disease~~



~~Consuming a cure while cold is accumulating but before it latches still sends the message of "your condition eases"~~



~~You do not get incubation when your current incubation runs out but you remain near a villager, you only get it at moment of contact. Ensure the fix to this is performant~~



~~"Your condition worsens" for when the disease kicks up a tier~~



~~When a disease that causes shivering cures, you get icicles on your screen for a few seconds~~



~~Divide pain relief and symptom management for lower level cures~~



~~Seperate debug UI for viruses and bacteria~~



~~All severe illnesses should cause shivering~~



~~Sepsis should have 4 tiers, the fourth (normally debilitating) should be septic shock.~~



~~Change the actual disease debuffs/attributes (for moderate severity, use as anchor):~~



* ~~Cold: -5% Attack Speed, -5% Mining Speed~~
* ~~RSV: -5% Attack Speed, -5% Mining Speed~~
* ~~Flu: -10% Attack Speed, -10% Mining Speed~~
* ~~Bronchitis: -15% Attack Speed, -15% Mining Speed~~
* ~~Pneumonia: -20% Attack Speed, -20% Mining Speed~~
* ~~Norovirus: -2 Max Saturation~~
* ~~Cellulitis: -10% Attack Damage, -10% Knockback Damage~~
* ~~Sepsis: -2 Max Health (tier 4 sepsis i.e. septic shock should also reduce body temp if cold sweat is available)~~



~~Remove all redundant code related to this~~



~~Change how the pain symptom works. It should give at tier 1:~~



~~-5% Attack Speed~~

~~-5% Attack Damage~~

~~-5% Mining Speed~~

~~-5% Knockback Damage~~

~~-5% Movement Speed~~



~~And at tier 3, it should disable the ability to sleep.~~



~~Malaise should have 3 tiers like pain, with the following tier 1 debuffs:~~



~~-10% Movement Speed~~



~~Septic shock should reduce body temperature substantially~~

