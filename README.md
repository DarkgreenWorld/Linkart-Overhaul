
![Linkart banner](https://github.com/melontini/Linkart/assets/104443436/f48430ab-c0f5-49c2-ad7e-6ced0f0d8a34)

[![Linkart Modrinth](https://img.shields.io/badge/Linkart%20Overhaul-Modrinth-1bd96a?logo=modrinth&logoColor=bluegreen)](https://modrinth.com/mod/linkart-overhaul)
[![CurseForge](https://img.shields.io/badge/Linkart%20Overhaul-CurseForge-f16436?logo=curseforge&logoColor=orange)](https://www.curseforge.com/minecraft/mc-mods/linkart-overhaul)
[![Linkart GitHub](https://img.shields.io/badge/%E2%80%8B-GitHub-gray?logo=github&logoColor=black&labelColor=white)](https://github.com/DarkgreenWorld/Linkart-Overhaul)

[![Connector](https://raw.githubusercontent.com/Sinytra/.github/main/badges/connector/compacter.svg)](https://modrinth.com/mod/connector)

## An updated Linkart fork for 26.3+

#### Links to linkart Refabricated
[![Linkart Modrinth](https://img.shields.io/badge/Linkart%20Refabricated-Modrinth-1bd96a?logo=modrinth&logoColor=bluegreen)](https://modrinth.com/mod/linkart-refabricated) [![Linkart GitHub](https://img.shields.io/badge/%E2%80%8B-GitHub-gray?logo=github&logoColor=black&labelColor=white)](https://github.com/Flatkat/Linkart-Refabricated)
#### Links to the original mod:
[![Linkart Modrinth](https://img.shields.io/modrinth/dt/sc4Mu9Zu?logo=modrinth&label=modrinth)](https://modrinth.com/mod/linkart) [![Linkart CurseForge](https://cf.way2muchnoise.eu/title/622736.svg)](https://www.curseforge.com/minecraft/mc-mods/linkart-updated) [![Linkart GitHub](https://img.shields.io/badge/%E2%80%8B-GitHub-gray?logo=github&logoColor=black&labelColor=white)](https://github.com/constellation-mc/Linkart)

### FAQ:

> What's the difference between Linkart Refabricated and Linkart Overhaul?

This fork includes numerous fixes and optimizations to the mod, as follows:
1. Improved the minecart group algorithm, with full compatibility with the "Minecart Improvements" experimental data pack. Minecart groups now perform well at higher speeds and no longer jitter, speed up and slow down erratically, struggle to accelerate, or disconnect. It also introduces scientific, predictable "disconnection" behavior.
2. Minecart groups now have correct collision behavior, instead of only the "lead car" having real collision.
3. Minecart groups are no longer unidirectional; you can pull or push the entire minecart group from either end. In addition, coupling minecarts no longer requires distinguishing between lead cars and trailing cars.
4. After enabling the "Minecart Improvements" experimental data pack, minecarts behave well physically after flying off slopes.
5. A brand-new configuration file with comments. MidlightLib is no longer used at present.
6. Some bugs were fixed.

> How do I link minecarts together?

Just prepare a bunch of chains, press SHIFT + right-click on one minecart, then press SHIFT + right-click on another nearby minecart, and the two minecarts will be coupled together.

> Forge version?

Not by me. Although 1.20 version of the original mod works with Sinytra Connector, so you may as well try.

### Requires <img alt="Fabric API icon" src="https://cdn.modrinth.com/data/P7dR8mSH/icon.png" width="40" height="40"> [Fabric API](https://modrinth.com/mod/fabric-api)