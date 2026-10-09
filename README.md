![Linkart banner](https://github.com/melontini/Linkart/assets/104443436/f48430ab-c0f5-49c2-ad7e-6ced0f0d8a34)

[![Linkart Modrinth](https://img.shields.io/badge/Linkart%20Overhaul-Modrinth-1bd96a?logo=modrinth&logoColor=bluegreen)](https://modrinth.com/mod/linkart-overhaul) 
[![CurseForge](https://img.shields.io/badge/Linkart%20Overhaul-CurseForge-f16436?logo=curseforge&logoColor=orange)](https://www.curseforge.com/minecraft/mc-mods/linkart-overhaul) 
[![GitHub](https://img.shields.io/badge/GitHub-gray?logo=github&logoColor=black&labelColor=white)](https://github.com/DarkgreenWorld/Linkart-Overhaul)

[![Connector](https://raw.githubusercontent.com/Sinytra/.github/main/badges/connector/compacter.svg)](https://www.curseforge.com/minecraft/mc-mods/sinytra-connector)

Requires <img alt="Fabric API icon" src="https://cdn.modrinth.com/data/P7dR8mSH/icon.png" width="20" height="20"> [Fabric API](https://modrinth.com/mod/fabric-api)

# An updated Linkart fork for 26.3+

**Links to linkart Refabricated:**

[![Linkart Modrinth](https://img.shields.io/badge/Linkart%20Refabricated-Modrinth-1bd96a?logo=modrinth&logoColor=bluegreen)](https://modrinth.com/mod/linkart-refabricated) 
[](https://github.com/Flatkat/Linkart-Refabricated)

**Links to the original mod:**

[![Linkart Modrinth](https://img.shields.io/modrinth/dt/sc4Mu9Zu?logo=modrinth&label=modrinth)](https://modrinth.com/mod/linkart) 
[![Linkart CurseForge](https://cf.way2muchnoise.eu/title/622736.svg)](https://www.curseforge.com/minecraft/mc-mods/linkart-updated) 
[![GitHub](https://img.shields.io/badge/GitHub-gray?logo=github&logoColor=black&labelColor=white)](https://github.com/constellation-mc/Linkart)

### FAQ:

> **What's the difference between Linkart Refabricated and Linkart Overhaul?**

This fork includes numerous fixes and optimizations to the mod, as follows:

1. Improved the minecart group algorithm, with full compatibility with the "Minecart Improvements" experimental data pack. Minecart groups now perform well at higher speeds and no longer jitter, speed up and slow down erratically, struggle to accelerate, or disconnect. It also introduces scientific, predictable "disconnection" behavior. See the comments in the config file for the detailed algorithm.

2. Minecart groups now have correct collision behavior, instead of only the "lead car" having real collision.

3. Minecart groups are no longer unidirectional; you can pull or push the entire minecart group from either end. In addition, coupling minecarts no longer requires distinguishing between lead cars and trailing cars.

4. After enabling the "Minecart Improvements" experimental data pack, minecarts behave well physically after flying off slopes.

5. Integration with [Better Minecart With Furnace](https://www.curseforge.com/minecraft/mc-mods/better-minecart-with-furnace): This mod reads the thrust value and maximum acceleration set by that mod, but replaces that mod's speed algorithm with its own.

6. Without the aforementioned mod installed, this mod enables its own powered minecart acceleration limit by default to ensure its behavior when pulling minecart groups. You can change this in the config file.

7. Integration with [Flash Carts Enhanced](https://www.curseforge.com/minecraft/mc-mods/flash-carts-enhanced): That mod allows minecarts using different physics logic to coexist. Therefore, this mod disables coupling between minecarts that use different physics logic to avoid uncontrollable consequences.

8. A brand-new configuration file with comments. MidlightLib is no longer used at present, so there is temporarily no in-game graphical configuration screen.

9. Fixed some edge-case bugs.

> **How do I link minecarts together?**

Just prepare a bunch of chains, press SHIFT + right-click on one minecart, then press SHIFT + right-click on another nearby minecart, and the two minecarts will be coupled together.

> **Forge version?**

Not by me. Although 1.20 version of the original mod works with Sinytra Connector, so you may as well try.
