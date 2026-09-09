# Minecraft Manhunt Plugin

<img width="1920" height="1080" alt="minecraftss" src="https://github.com/user-attachments/assets/2dda56b5-f04e-4de8-a4e7-ac80f8ecb5bd" />

this is a minecraft plugin i built for me and my friends to play manhunt. the plugin itself is very
straightforward. server ops assign runners and hunters, hunters receive a compass that
tracks the runners, and "Runner's Luck" boosts enderman, blaze, and piglin drops.

## Features

- hunter compass updated once per second
- right-click target cycling for multiple runners
- last known runner locations across the Overworld, Nether, and End
- tracker compass restored when a hunter dies/respawns
- live world regeneration with a random or specific seed
- configurable drop boosts for runners
- tab completed role management commands

## Requirements

- Paper 1.21.11
- Java 21 or newer

other Paper 1.21 releases may work but 1.21.11 is the version this project builds and tests against.

## Installation

1. download `manhunt-plugin-1.1.0.jar` from the latest GitHub release, or build it locally.
2. place the JAR in your Paper server's `plugins` directory.
3. restart the server.
4. use the commands below as an operator.

## Commands

| Command | Description                                  |
| --- |----------------------------------------------|
| `/manhunt r <player>` | set an online runner                         |
| `/manhunt h <player>` | set an online hunter and give them a tracker |
| `/manhunt status` | show the current roles                       |
| `/manhunt reset` | clear all roles and trackers                 |
| `/manhunt resetworld` | immediately generate a fresh random world |
| `/manhunt resetworld <seed>` | immediately generate a fresh world from a numeric seed |

all commands require `manhunt.admin`, which defaults to server operators.

world resets generate a new managed Overworld, Nether, and End, then moves every online player to the new spawn.
manhunt roles are also cleared during the reset.

## Config

paper creates `plugins/ManhuntPlugin/config.yml` on first launch:

```yaml
compass:
  display-name: "&ctracker"
  description: "&7right-click to cycle runners"
  update-interval-ticks: 20

runners-luck:
  enderman-extra-drop-chance: 0.80
  blaze-extra-drop-chance: 0.80
  piglin-pearl-chance: 0.20
```

the enderman and blaze values are an extra chance applied only when the normal
drop does not appear. with a roughly 50% vanilla drop rate, the defaults produce
about a 90% total success rate. chances are between `0.0` and `1.0`.

## Build

the Maven Wrapper downloads the correct Maven version automatically:

```powershell
.\mvnw.cmd clean package
```

on macOS or Linux:

```bash
bash ./mvnw clean package
```

the resulting plugin is written to `target/manhunt-plugin-1.1.0.jar`. tests can be run
with `bash ./mvnw test` or `.\mvnw.cmd test`.

## License

released under the [MIT License](LICENSE).
