# MonsterHuntReloaded

MonsterHuntReloaded ist eine modernisierte Paper-26.2-Version des klassischen
MonsterHunt-Plugins. Es benötigt Java 25 und wird mit `./gradlew build` gebaut.
Das fertige Plugin-JAR liegt anschließend direkt unter `target/`.

## Datenhaltung

Highscores werden aktuell ohne zusätzliche Abhängigkeit in
`plugins/MonsterHuntReloaded/highscores.yml` gespeichert.

## Gewertete Monster

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

## Disconnect und Reconnect

Ein Disconnect wird weder bestraft noch als Aufgabe gewertet. Teilnahme und
Punktestand werden anhand der UUID unverändert pausiert. Verbindet sich der
Spieler während derselben Jagd erneut, wird genau dieser Stand fortgeführt.
Während der Pause entstehen keine zusätzlichen Punkte oder Abzüge.

Endet die Jagd während der Spieler offline ist, wird sein eingefrorener Stand
einmalig in die Endwertung einbezogen. Rückteleport und gegebenenfalls eine
Gewinnerbelohnung werden in `reconnect.yml` vorgemerkt und erst beim nächsten
Login ausgeführt. Die Endwertung wird beim Reconnect nicht neu berechnet.

## Geplante optionale JDBC-Treiber

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

## Versionierung

Java-, Paper- und Testversionen werden zentral in
`gradle/libs.versions.toml` verwaltet. Für die Abhängigkeit wird gemäß aktueller
Paper-Dokumentation `26.2.build.+` verwendet. In `plugin.yml` steht dagegen die
von Paper erlaubte API-Angabe `api-version: '26.2'`.
