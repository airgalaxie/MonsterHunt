# MonsterHuntReloaded

English | [Deutsch](#deutsch)

MonsterHuntReloaded is a modernized Paper 26.2 version of the classic
MonsterHunt plugin. It requires Java 25 and can be built with `./gradlew build`.
The finished plugin JAR is placed directly in `target/`.

## Data storage

SQLite is the primary store for players, hunt history, results, pending reconnect
actions, and high scores. The database defaults to
`plugins/MonsterHuntReloaded/data/monsterhunt.db`. Configuration remains YAML.
Existing `highscores.yml` and `reconnect.yml` files are imported once and renamed
to `.imported`; they are never used as a fallback store.

The plugin first accepts an SQLite JDBC driver already registered at runtime.
Paper loads and caches the pinned Xerial driver declared in `plugin.yml` as the
fallback. Startup fails explicitly when no compatible driver is available.

Consistent SQLite backups are enabled by default every 24 hours, verified with
`PRAGMA quick_check`, and retained according to `storage.sqlite.backup` in
`config.yml`. Per-kill history can grow considerably and is therefore disabled
by default under `storage.history.kill-events.enabled`. Hunt summaries and final
player results are always retained.

## Schedule

`schedule.start-time` and `schedule.end-time` use the quoted Minecraft in-game
clock format `HH:mm`, not raw world ticks or real-world time. The defaults
`'19:00'` and `'05:00'` run the hunt through the Minecraft night. By contrast,
`schedule.signup-minutes` is a real-world duration; the default one-minute signup
period begins about 72 Minecraft minutes before the hunt starts. Existing
configurations with numeric tick values must be changed to quoted `HH:mm` values;
invalid schedule values prevent the plugin from starting.

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
reward are recorded as pending actions in SQLite and processed on the player's
next login. The final results are not recalculated when the player reconnects.

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

SQLite ist der Primärspeicher für Spieler, Hunt-Historie, Ergebnisse,
ausstehende Reconnect-Aktionen und Highscores. Die Datenbank liegt standardmäßig
unter `plugins/MonsterHuntReloaded/data/monsterhunt.db`; die Konfiguration bleibt
in YAML. Vorhandene `highscores.yml` und `reconnect.yml` werden einmal importiert,
anschließend in `.imported` umbenannt und nie als stiller Fallback verwendet.

Zuerst wird ein zur Laufzeit bereits registrierter SQLite-JDBC-Treiber geprüft.
Als Fallback lädt und zwischenspeichert Paper die festgelegte Xerial-Version aus
der `plugin.yml`. Ist kein kompatibler Treiber verfügbar, schlägt der Start mit
einer eindeutigen Fehlermeldung fehl.

Konsistente SQLite-Sicherungen sind standardmäßig alle 24 Stunden aktiv, werden
mit `PRAGMA quick_check` geprüft und gemäß `storage.sqlite.backup` in der
`config.yml` aufbewahrt. Die potentiell umfangreiche Historie einzelner Kills ist
unter `storage.history.kill-events.enabled` standardmäßig deaktiviert. Hunt- und
Ergebnis-Historie wird immer gespeichert.

### Zeitplan

`schedule.start-time` und `schedule.end-time` verwenden die in Anführungszeichen
gesetzte Minecraft-Uhrzeit im Format `HH:mm`, keine Welt-Ticks und keine reale
Uhrzeit. Mit den Standardwerten `'19:00'` und `'05:00'` läuft die Jagd während
der Minecraft-Nacht. `schedule.signup-minutes` ist dagegen eine reale Dauer: Die
standardmäßige Anmeldezeit von einer Minute beginnt ungefähr 72 Minecraft-Minuten
vor dem Start der Jagd. In vorhandenen Konfigurationen müssen numerische
Tickwerte durch `HH:mm`-Werte in Anführungszeichen ersetzt werden; bei ungültigen
Zeitangaben startet das Plugin nicht.

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
Gewinnerbelohnung werden als ausstehende Aktionen in SQLite vorgemerkt und erst
beim nächsten Login ausgeführt. Die Endwertung wird beim Reconnect nicht neu
berechnet.

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
