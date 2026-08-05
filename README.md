# MonsterHuntReloaded

MonsterHuntReloaded ist eine modernisierte Paper-26.2-Version des klassischen
MonsterHunt-Plugins. Es benötigt Java 25 und wird mit `./gradlew build` gebaut.
Das Plugin-JAR liegt anschließend unter `build/libs/`.

## Datenhaltung

Highscores werden aktuell ohne zusätzliche Abhängigkeit in
`plugins/MonsterHuntReloaded/highscores.yml` gespeichert.

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
