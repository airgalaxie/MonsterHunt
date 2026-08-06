# MonsterHuntReloaded

English | [Deutsch](#deutsch)

MonsterHuntReloaded is a modernized Paper 26.2 version of the classic
MonsterHunt plugin. It requires Java 25 and can be built with `./gradlew build`.
The finished plugin JAR is placed directly in `target/`.

## Data storage

High scores are currently stored without any additional dependencies in
`plugins/MonsterHuntReloaded/highscores.yml`.

## Scoring monsters

There is exactly one monster list under `points.entities` in `config.yml`.
A kill only awards points if the killed entity implements Paper's `Enemy`
interface, can damage the player through its normal combat behavior, and its
Minecraft key is explicitly included in this list. Entities not listed award no
points; there is no default value. Harmless slimes of the smallest size do not
count despite their entity type.

The default list contains the actual monsters available in Paper 26.2:

`blaze`, `bogged`, `breeze`, `cave_spider`, `creaking`, `creeper`, `drowned`,
`elder_guardian`, `ender_dragon`, `enderman`, `endermite`, `evoker`, `ghast`,
`guardian`, `hoglin`, `husk`, `illusioner`, `magma_cube`, `parched`,
`phantom`, `piglin`, `piglin_brute`, `pillager`, `ravager`, `shulker`,
`silverfish`, `skeleton`, `slime`, `spider`, `stray`, `vex`, `vindicator`,
`warden`, `witch`, `wither`, `wither_skeleton`, `zoglin`, `zombie`,
`zombie_villager`, and `zombified_piglin`.

Although `sulfur_cube`, `camel_husk`, and `zombie_nautilus` are entity types in
Minecraft 26.2, they do not implement Paper's `Enemy` classification and are
therefore deliberately not counted as actual monsters.

Although `giant` implements Paper's `Enemy` interface, it has no active attack
AI in regular vanilla gameplay and therefore does not count either.

## Disconnecting and reconnecting

Disconnecting is neither penalized nor treated as giving up. Participation and
score are paused unchanged based on the player's UUID. If the player reconnects
during the same hunt, they resume from exactly that state. No additional points
or deductions are applied while they are disconnected.

If the hunt ends while the player is offline, their frozen score is included in
the final results once. The return teleport and, where applicable, a winner's
reward are recorded in `reconnect.yml` and processed on the player's next login.
The final results are not recalculated when the player reconnects.

## Planned optional JDBC drivers

External database integration is intentionally not enabled yet. A future
implementation is intended to load JDBC drivers exclusively from the server's
`/drivers/` directory; drivers will not be bundled in the plugin JAR. Planned
examples include:

- `/drivers/sqlite-jdbc-<version>.jar`
- `/drivers/mysql-connector-j-<version>.jar`
- `/drivers/postgresql-<version>.jar`

These files are supplied by the server operator and must match the database in
use. Until an isolated class loader, connection pooling, schema migrations, and
a clean fallback have been implemented, the plugin ignores this directory. This
allows the current version to run without third-party drivers.

## Versioning

Java, Paper, and test versions are managed centrally in
`gradle/libs.versions.toml`. In accordance with the current Paper documentation,
the dependency uses `26.2.build.+`. By contrast, `plugin.yml` contains the API
declaration supported by Paper: `api-version: '26.2'`.

## Acknowledgements

Special thanks to [matejdro](https://github.com/matejdro), the original creator
of MonsterHunt, and [erlendir](https://github.com/erlendir), who maintained and
updated it for Bukkit/Spigot 1.9–1.12.x.

## License

MonsterHuntReloaded is an adaptation of MonsterHunt by matejdro, later
maintained for Bukkit/Spigot 1.9–1.12.x by erlendir.

This adapted version is licensed under the
[Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International
License](https://creativecommons.org/licenses/by-nc-sa/4.0/). The original
MonsterHunt code was published under
[CC BY-NC-SA 3.0 Unported](https://creativecommons.org/licenses/by-nc-sa/3.0/).
See [LICENSE.md](LICENSE.md) for details.

---

## Deutsch

[English](#monsterhuntreloaded) | Deutsch

MonsterHuntReloaded ist eine modernisierte Paper-26.2-Version des klassischen
MonsterHunt-Plugins. Es benötigt Java 25 und wird mit `./gradlew build` gebaut.
Das fertige Plugin-JAR liegt anschließend direkt unter `target/`.

### Datenhaltung

Highscores werden aktuell ohne zusätzliche Abhängigkeit in
`plugins/MonsterHuntReloaded/highscores.yml` gespeichert.

### Gewertete Monster

Es gibt genau eine Monsterliste unter `points.entities` in der `config.yml`.
Ein Kill gibt nur dann Punkte, wenn die getötete Entity in Paper das Interface
`Enemy` implementiert, dem Spieler durch ihr normales Kampfverhalten Schaden
zufügen kann und ihr Minecraft-Key ausdrücklich in dieser Liste steht. Nicht
gelistete Entities geben keine Punkte; es existiert kein Defaultwert. Harmlose
Slimes der kleinsten Größe werden trotz ihres Entity-Typs nicht gewertet.

Die Standardliste umfasst die in Paper 26.2 vorhandenen echten Monster:

`blaze`, `bogged`, `breeze`, `cave_spider`, `creaking`, `creeper`, `drowned`,
`elder_guardian`, `ender_dragon`, `enderman`, `endermite`, `evoker`, `ghast`,
`guardian`, `hoglin`, `husk`, `illusioner`, `magma_cube`, `parched`,
`phantom`, `piglin`, `piglin_brute`, `pillager`, `ravager`, `shulker`,
`silverfish`, `skeleton`, `slime`, `spider`, `stray`, `vex`, `vindicator`,
`warden`, `witch`, `wither`, `wither_skeleton`, `zoglin`, `zombie`,
`zombie_villager` und `zombified_piglin`.

`sulfur_cube`, `camel_husk` und `zombie_nautilus` sind zwar Entity-Typen aus
Minecraft 26.2, implementieren aber nicht Papers `Enemy`-Klassifikation und
werden deshalb bewusst nicht als echte Monster gewertet.

`giant` implementiert zwar Papers `Enemy`-Interface, besitzt im regulären
Vanilla-Spiel aber keine aktive Angriffs-KI und wird deshalb ebenfalls nicht
gewertet.

### Disconnect und Reconnect

Ein Disconnect wird weder bestraft noch als Aufgabe gewertet. Teilnahme und
Punktestand werden anhand der UUID unverändert pausiert. Verbindet sich der
Spieler während derselben Jagd erneut, wird genau dieser Stand fortgeführt.
Während der Pause entstehen keine zusätzlichen Punkte oder Abzüge.

Endet die Jagd während der Spieler offline ist, wird sein eingefrorener Stand
einmalig in die Endwertung einbezogen. Rückteleport und gegebenenfalls eine
Gewinnerbelohnung werden in `reconnect.yml` vorgemerkt und erst beim nächsten
Login ausgeführt. Die Endwertung wird beim Reconnect nicht neu berechnet.

### Geplante optionale JDBC-Treiber

Eine externe Datenbankanbindung ist bewusst noch nicht aktiviert. Die spätere
Implementierung soll JDBC-Treiber ausschließlich aus einem Serververzeichnis
`/drivers/` laden; Treiber werden nicht in das Plugin-JAR eingebettet. Vorgesehen
sind beispielsweise:

- `/drivers/sqlite-jdbc-<version>.jar`
- `/drivers/mysql-connector-j-<version>.jar`
- `/drivers/postgresql-<version>.jar`

Die Dateien stammen vom Serverbetreiber und müssen zur verwendeten Datenbank
passen. Bis ein isolierter Classloader, Connection-Pooling, Schema-Migrationen
und ein sauberer Fallback implementiert sind, ignoriert das Plugin dieses
Verzeichnis. Dadurch bleibt die aktuelle Version ohne fremde Treiber lauffähig.

### Versionierung

Java-, Paper- und Testversionen werden zentral in
`gradle/libs.versions.toml` verwaltet. Für die Abhängigkeit wird gemäß aktueller
Paper-Dokumentation `26.2.build.+` verwendet. In `plugin.yml` steht dagegen die
von Paper erlaubte API-Angabe `api-version: '26.2'`.

### Danksagung

Besonderer Dank gilt [matejdro](https://github.com/matejdro), dem ursprünglichen
Entwickler von MonsterHunt, sowie [erlendir](https://github.com/erlendir), der
das Plugin für Bukkit/Spigot 1.9–1.12.x gepflegt und aktualisiert hat.

### Lizenz

MonsterHuntReloaded ist eine Bearbeitung von MonsterHunt, das ursprünglich von
matejdro entwickelt und später von erlendir für Bukkit/Spigot 1.9–1.12.x
gepflegt wurde.

Diese bearbeitete Version steht unter der
[Creative Commons Namensnennung – Nicht kommerziell – Weitergabe unter gleichen
Bedingungen 4.0 International](https://creativecommons.org/licenses/by-nc-sa/4.0/deed.de).
Der ursprüngliche MonsterHunt-Code wurde unter
[CC BY-NC-SA 3.0 Unported](https://creativecommons.org/licenses/by-nc-sa/3.0/deed.de)
veröffentlicht. Einzelheiten stehen in der [LICENSE.md](LICENSE.md).
