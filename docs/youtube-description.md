I am Godzilla in Minecraft VR. I rise out of Tokyo Bay, smash the city with my own hands, burn it with atomic breath, and after three minutes Mothra arrives to stop me.

Played on a Meta Quest 3 with Vivecraft, Minecraft 26.3 and the Patrix 32x resource pack.

HOW IT WAS MADE
The whole game was made by AI: Claude (Anthropic) running in Claude Code. I described what I wanted, answered a few questions, and went to bed. One Claude coordinated a team of Claude Sonnet agents overnight, each building its own part of the game in parallel and testing it in a desktop Minecraft client it could screenshot, before everything was merged and played through together.

- Tokyo: a Python program builds the city block by block. 255 buildings with real floors, walls and windows (so they break apart properly), streets, an elevated railway, a port, and landmarks: Tokyo Tower, the Ferris wheel, the National Diet Building, Tokyo Station, Senso-ji with its pagoda, the twin towers and Rainbow Bridge. Same city every run, rebuilt in a fraction of a second when you play again.
- Being Godzilla: you are scaled up 16 times, about 29 blocks tall. Your hands are the controllers, so swinging an arm through a building sends the blocks flying. Feet crush streets, the tail sweeps, the right trigger fires the atomic breath, A roars.
- Destruction: every hit throws real blocks as debris (tens of thousands can be in the air at once). Buildings check their own support: cut the bottom out and the top pancakes down, chop one side and a tower topples that way. Fuel tanks chain-explode.
- The city fights back: people run and scream, cars, buses and trains drive and explode, and the military arrives in waves: warships, tanks, helicopters, jets and maser cannons. None of them can hurt Godzilla.
- Mothra: a boss with wind gusts, poison scale clouds and dive attacks. Swat her out of the air mid-dive. She is the only thing that can kill you.
- Sound and music: every roar, explosion, scream and the whole soundtrack were synthesised from scratch in Python. Nothing is taken from the films.

It is a Fabric mod for Minecraft Java, with the city generator and sound tools alongside it.
Source code: https://github.com/TengilOurLiberator/godzilla-in-minecraft

A fan project. Not affiliated with Toho, Mojang or Microsoft. Godzilla and Mothra are trademarks of Toho Co., Ltd.

#Godzilla #Minecraft #VR #Quest3 #Vivecraft #ClaudeAI
