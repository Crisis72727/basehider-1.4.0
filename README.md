# BaseHider 1.4.0

Paper-Plugin für persönliche Base-Zonen.

## Zonen

- 0–20 Blöcke: versteckt, grüne Nachricht beim Betreten
- mehr als 20 bis unter 25: blau, weiterhin versteckt
- 25–30: gelb, sichtbar
- mehr als 30: sichtbar

Die Zone eines Spielers wird ausschließlich mit seiner eigenen Base berechnet. Deshalb lösen fremde Bases keine Chat-Nachrichten aus. OPs und Nicht-OPs benutzen dieselbe Logik.

## Farben

- Grün: `Du bist jetzt unsichtbar.`
- Blau: `Du bist gleich sichtbar.`
- Gelb: `Du bist gleich unsichtbar.`
- Rot: `Du bist jetzt sichtbar.`

Versteckte Spieler werden mit `hidePlayer(...)` für alle anderen Spieler ausgeblendet. Dadurch verschwinden sie auch aus Tablist und Vanilla-Locator-Bar. Beim Sichtbarwerden werden sie wieder eingeblendet.

## Befehle

- `/setbase`
- `/removebase`

## Build

Benötigt Java 21 und Maven:

```bash
mvn clean package
```

Ausgabe:

```text
target/BaseHider-1.4.0.jar
```
