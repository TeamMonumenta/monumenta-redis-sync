# Monumenta Redis Sync

## Introduction

The purpose of this plugin is to manage player data for a large number of
minecraft servers by storing it in a Redis database.

When a user logs in, instead of reading their data from the world, it instead
loads it from Redis. And, likewise, when the player logs out or their data is
saved, that data get stored into Redis.

This plugin also provides commands to transfer players between servers on a
bungeecord network, allowing a game world to be broken up across many minecraft
servers for performance or abstraction purposes.

Player data is versioned, by default saving the previous 20 versions. This
allows you to roll back a player to a previous save point. Additionally, there
is a "stash" mechanism that lets you temporarily save your current player data
and easily return to it later - or load a different player's stashed data as if
it was your own.

## Current status

This plugin is functional and is being used in production on the Monumenta
server network spanning 20+ servers and hundreds of players. It is still a
little rough around the edges, missing some nice-to-have features.

Here's a list of currently supported things:
- Save playerdata, advancements, and scoreboard data to/from Redis
- Function & command block accessible /transferserver command to move players
  between servers on the same bungeecord network
- /playerhistory to inspect a player's save history
- /playerrollback to roll them back to a previous version
- /stash [put|get|info] to temporarily save and load your playerdata
- A plugin API to use those features directly from other plugins
- A player transfer event, which allows plugins to manipulate player data prior
  to a player transferring servers
- Support for multiple different "domains". Each server in the same domain will
  use the same player data. No data transfer is possible between domains
  (useful if you have different types of servers)
- Exposes the Redis API (via Lettuce) for access by other plugins
- Also loads on Bungeecord, though currently does nothing there besides provide
  access to the Lettuce API
- An API to allow plugins to save additional information about the player for
  transfer

Planned features:
- Automatic config file creation
- API documentation
- An API for a global, plugin-accessible scoreboard
- Storage of player stats data

# Example Dependant Plugin

See the [example](example) directory for a complete example on how you might
use this plugin as a dependency of one of your plugins, either for Paper or
bungeecord.

## Dependencies

This plugin requires a Paper-based minecraft server, compiled with a specific
patch to enable high-performance access to player data save/load events. This
is a significantly more cumbersome requirement than most plugins.

The patch is available here:
https://github.com/TeamMonumenta/Paper/commit/4d1bb15aa59602f32182c20ce97cc6fd10d0c9f9

A patch already exists for 1.13 and 1.15, but other versions shouldn't be too
hard to get working too.

If you are interested in getting this working / trying it out yourself, join
the Monumenta Discord (https://discord.gg/eep9qcu) and message @Combustible.

This plugin also requires CommandAPI 4.3. Other versions might also work, worth
testing. If you try with a version that doesn't work (log errors, commands
don't work, etc.) it's not going to damage your player data.

## Download

You can download the Monumenta Redis Sync plugin from [GitHub Packages](https://github.com/TeamMonumenta/monumenta-redis-sync/packages).
