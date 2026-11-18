# Maaltijdboxen aan huis – technisch plan

Dit document beschrijft het ontwerp van de backend voor een platform dat iedere week maaltijdboxen samenstelt en aan huis bezorgt. De applicatie wordt opgebouwd met Spring Boot 4.0.8, Spring Cloud 2025.1.3 en Java 21. De Angular-frontend maakt gebruik van deze backend.

> **Huidige stand:** week 8 – analysefase. De implementatie is nog niet gestart; dit document legt het voorgestelde ontwerp vast.
>
> De gemaakte keuzes en de bijbehorende motivatie zijn terug te vinden in [BESLISSINGEN.md](BESLISSINGEN.md).

De projectstructuur ziet er op hoofdlijnen als volgt uit:

```text
examenopdracht/
├── architectuur/workspace.dsl   # Structurizr-model waaruit de diagrammen worden opgebouwd
├── frontend/                    # Angular-applicatie
├── backend/                     # Spring Cloud-backend met meerdere modules
├── README.md                    # Dit technisch plan
└── BESLISSINGEN.md
```

---

## 1. Overzicht van de architectuur

De architectuur is als code uitgewerkt in [`architectuur/workspace.dsl`](./architectuur/workspace.dsl). Het functionele diagram staat hier:

![Functioneel overzicht](./architectuur/img/Architectuur-functioneel.svg)

### Betekenis van de verbindingen

| Weergave                              | Uitleg                                                                                                            |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Doorgetrokken pijl met `Feign (sync)` | Synchrone communicatie. De aanroepende service heeft het antwoord nodig om verder te kunnen.                      |
| Verbinding met `AMQP (async)`         | Asynchrone berichtenuitwisseling via RabbitMQ. Problemen bij de verwerking mogen een bestelling niet tegenhouden. |
| Grijze stippellijn                    | Communicatie voor infrastructuur, zoals registratie bij Eureka of het ophalen van configuratie.                   |

### Uitgangspunten

1. De frontend communiceert uitsluitend met de gateway. Alleen URL's onder `/api/**` worden doorgestuurd. Aanvragen naar andere paden leveren een `404` op.
2. Iedere service beheert een eigen database. Onderlinge verwijzingen gebeuren uitsluitend met ID's; er worden dus geen foreign keys of tabellen tussen services gedeeld.
3. Is een antwoord noodzakelijk om een proces voort te zetten, dan gebruiken we synchrone communicatie via Feign. Verwerking die na de bevestiging kan plaatsvinden, verloopt asynchroon.
4. De paden onder `/internal/**` zijn gereserveerd voor communicatie tussen services. De gateway stuurt deze endpoints niet door.

---

## 2. Services en verantwoordelijkheden

Elke service krijgt een afgebakende verantwoordelijkheid en beheert de gegevens die daarbij horen.

| Service                    | Gegevens in de eigen database                                                          | Reden voor deze indeling                                                                                                                                                                                                                                                  |
| -------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`recipe-service`**       | `Recipe`, `Ingredient`, `RecipeIngredient` (hoeveelheid per standaard aantal personen) | Het weekaanbod kan veranderen en wordt beheerd door andere actoren dan voorraad en bestellingen. Deze service is bovendien de enige bron voor ingrediënt-ID's.                                                                                                            |
| **`order-service`**        | `Order`, `OrderLine`                                                                   | Deze service beheert het volledige verloop en de status van bestellingen. Van andere services bewaart hij alleen ID's en snapshots.                                                                                                                                       |
| **`inventory-service`**    | `StockItem`, `StockReservation`                                                        | Voorraadbeheer heeft een eigen levenscyclus, met aanvullingen en verbruik. Reserveringen moeten atomair kunnen gebeuren. `StockReservation` bevat `orderId`, `ingredientId` en hoeveelheid, zodat bij een annulering duidelijk is welke voorraad moet worden vrijgegeven. |
| **`delivery-service`**     | `DeliverySlot`, `DeliveryRun`, `DeliveryStop`                                          | Tijdsloten, capaciteit en bezorgritten vormen een afzonderlijk domein. Met `DeliveryStop` (order-ID, adres en status) kan een bestelling aan een rit worden gekoppeld en als afgehandeld worden aangeduid.                                                                |
| **`notification-service`** | Geen eigen domeingegevens                                                              | Deze service verwerkt meldingen en is een pure consument van events. Als hij tijdelijk niet beschikbaar is, mogen de andere processen daar geen hinder van ondervinden.                                                                                                   |

De ondersteunende infrastructuur bestaat uit `discovery-service` (Eureka), `config-service`, `gateway` en RabbitMQ.

### Gegevens die order-service zelf bijhoudt

Voor deze toepassing is geen afzonderlijke `customer-service` voorzien. Een klant wordt binnen de opdracht voorgesteld door een ID en enkele naam- en adresgegevens. Er is geen uitgebreid klantenbeheer nodig.

Daarom bewaart `order-service` bepaalde gegevens als onderdeel van de bestelling:

| Veld of snapshot in `Order`                                                           | Waarom dit wordt bijgehouden                                                                                                                                                                                                                |
| ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `customerId` (afkomstig uit header `X-User-Id`)                                       | Hiermee kan de applicatie de bestellingen van één klant ophalen.                                                                                                                                                                            |
| `customerName` en `deliveryAddress` (momentopname bij het plaatsen van de bestelling) | De bestelling moet achteraf dezelfde klant- en adresgegevens blijven tonen, ook wanneer de klant zijn adres later wijzigt. Het bezorgadres wordt via het event aan delivery doorgegeven, zodat daarvoor geen extra serviceaanroep nodig is. |
| `recipeName` in `OrderLine` (momentopname)                                            | De bestellijst blijft correct, ook als een recept later uit het aanbod verdwijnt. De order-service hoeft daarvoor niet opnieuw contact op te nemen met recipe-service.                                                                      |

Voorraadgegevens, de beschikbare capaciteit van tijdsloten en de volledige receptinformatie worden niet gekopieerd. Die informatie wordt op het moment van bestellen opgevraagd bij de verantwoordelijke service.

### Levenscyclus van een bestelling

Een bestelling doorloopt de volgende statussen:

```text
PLACED ──(checks geslaagd)──> CONFIRMED ──(delivery.started)──> IN_DELIVERY ──(order.delivered)──> DELIVERED
   │                              │
   └──────────── annuleren (PLACED of CONFIRMED) ──────────────> CANCELLED
```

De status `PLACED` bestaat alleen tijdelijk binnen de transactie van `POST /api/orders`. Wanneer een van de synchrone controles faalt, wordt de transactie teruggedraaid en ontvangt de klant een `409`-antwoord. Als alle controles slagen, wordt de bestelling opgeslagen met status `CONFIRMED` en publiceert order-service het event `order.confirmed`.

### Berichten via RabbitMQ

De communicatie via RabbitMQ gebruikt de volgende exchanges en routing keys:

| Exchange          | Routing key        | Producer         | Consumers                         |
| ----------------- | ------------------ | ---------------- | --------------------------------- |
| `order.events`    | `order.confirmed`  | order-service    | inventory, delivery, notification |
| `order.events`    | `order.cancelled`  | order-service    | inventory, delivery, notification |
| `delivery.events` | `delivery.started` | delivery-service | order, notification               |
| `delivery.events` | `order.delivered`  | delivery-service | order, notification               |

Het bericht `order.confirmed` bevat alle informatie die de ontvangende services nodig hebben: `orderId`, klant-ID, naam, adres, `slotId`, het aantal personen en de lijst met ingrediënt-ID's en bijbehorende hoeveelheden. De consumers hoeven daardoor geen bijkomende gegevens op te vragen. Elk event krijgt via de headers ook een `traceId` mee.

---

## 3. Uitwerking van de user stories

### US1 – Het weekmenu raadplegen

1. De frontend stuurt via de gateway een aanvraag naar recipe-service: `GET /api/recipes?week=2026-W47`.
2. Recipe-service haalt de gegevens uit de eigen database en geeft een lijst met DTO's terug. Die bevat onder meer de naam, bereidingstijd, het standaard aantal personen en de allergenen.
3. Dit verloopt volledig synchroon. Het gaat om een leesaanvraag waarbij de frontend het resultaat meteen nodig heeft. Andere services hoeven hiervoor niet aangesproken te worden.

### US2 – Een maaltijdbox samenstellen en bestellen

1. De klant maakt in de frontend een bestelling aan. Via de gateway komt de aanvraag bij order-service terecht: `POST /api/orders`. De aanvraag bevat de gekozen recepten, het aantal personen en het gewenste leveringsslot. De klant en diens rol worden uit de headers gehaald.
2. Voor elk gekozen recept vraagt order-service de ingrediënten op bij recipe-service. Dit gebeurt synchroon via Feign, want zonder de ingrediëntenlijst kan niet worden berekend wat de bestelling nodig heeft. Als een recept niet bestaat, volgt een `404`.
3. Order-service berekent de benodigde hoeveelheden met de formule `hoeveelheid × aantal personen / basisaantal personen`. Daarna vraagt hij via `POST /internal/stock/check` aan inventory-service om de voorraad te controleren. Ook dit is een synchrone Feign-aanroep: de bestelling kan pas worden aanvaard wanneer het resultaat bekend is. Bij onvoldoende voorraad volgt `409 INSUFFICIENT_STOCK`, met vermelding van de ontbrekende ingrediënten.
4. Vervolgens controleert order-service bij delivery-service het gekozen slot via `GET /internal/delivery-slots/{id}`. Ook deze controle gebeurt synchroon met Feign. Een onbekend of volzet slot resulteert in `409 SLOT_FULL`.
5. Wanneer beide controles positief zijn, slaat order-service de bestelling op met status `CONFIRMED`. De API antwoordt met `201` en de aangemaakte bestelling.
6. Daarna publiceert order-service asynchroon het event `order.confirmed`. Drie consumers verwerken dit bericht:
   - inventory-service maakt een `StockReservation` aan en vermindert de beschikbare voorraad;
   - delivery-service voegt de bestelling als `DeliveryStop` aan het gekozen slot toe en verhoogt de bezetting;
   - notification-service registreert de melding dat de bestelling bevestigd is.
7. Als een van deze drie consumers tijdelijk uitvalt, blijft de bestelling toch geslaagd. Zodra de betrokken service opnieuw beschikbaar is, kan die het bericht uit de durable queue verwerken.

### US3 – Eigen bestellingen bekijken

1. De frontend vraagt via de gateway bij order-service de bestellingen op met `GET /api/orders` (gefilterd op `X-User-Id`) of vraagt één bestelling op via `GET /api/orders/{id}`.
2. Order-service leest de gevraagde informatie uit de eigen database. Voor deze leesacties zijn geen andere services nodig; de afhandeling is synchroon.
3. De status blijft actueel doordat order-service zelf de events `delivery.started` en `order.delivered` consumeert. Op basis daarvan past hij de status in zijn eigen database aan. Een aparte aanvraag bij delivery-service om de status op te vragen is dus niet nodig.

### US4 – De voorraad opvolgen en aanvullen

1. Een medewerker raadpleegt inventory-service via de gateway met `GET /api/stock`. Met `GET /api/stock?lowOnly=true` worden alleen de voorraaditems getoond die onder hun drempelwaarde zitten.
2. Voor het aanvullen van een ingrediënt gebruikt de medewerker `PATCH /api/stock/{ingredientId}/replenish`, met bijvoorbeeld deze request body: `{ "quantity": 50 }`.
3. De verwerking blijft binnen inventory-service en gebeurt synchroon. Alleen gebruikers met de rol `MEDEWERKER` mogen deze actie uitvoeren. Bij een andere rol wordt `403` teruggegeven.

### US5 – Een bezorgronde uitvoeren

1. De chauffeur haalt via de gateway bij delivery-service de ritten voor een bepaalde datum op met `GET /api/delivery-runs?date=...`. Het resultaat toont de rit en de bijbehorende stops.
2. Met `PATCH /api/delivery-runs/{id}/start` geeft de chauffeur aan dat de rit vertrekt. Delivery-service zet de rit op onderweg en publiceert het event `delivery.started`. Dit gebeurt asynchroon; order-service verwerkt het event en wijzigt de betrokken bestellingen naar `IN_DELIVERY`.
3. Zodra een bestelling is afgegeven, meldt de chauffeur dit via `PATCH /api/delivery-runs/{id}/orders/{orderId}/delivered`. De stop wordt als geleverd gemarkeerd en delivery-service publiceert het event `order.delivered`.
4. Order-service verwerkt dat event en verandert de status van de bestelling naar `DELIVERED`. Notification-service brengt de klant vervolgens op de hoogte.
5. De chauffeur hoeft niet te wachten tot alle consumers hun werk hebben afgerond. De actie is geslaagd zodra delivery-service de wijziging heeft opgeslagen.

### US6 – Een bestelling annuleren

1. De klant verstuurt via de gateway naar order-service de aanvraag `PATCH /api/orders/{id}/cancel`.
2. Order-service controleert of de bestelling nog geannuleerd mag worden. Dat kan alleen bij status `PLACED` of `CONFIRMED`, en de klant mag uitsluitend een eigen bestelling annuleren. Bij status `IN_DELIVERY` of `DELIVERED` volgt `409 ORDER_NOT_CANCELLABLE`.
3. Als annuleren is toegestaan, wordt de status aangepast naar `CANCELLED`. De API antwoordt met `200`.
4. Vervolgens verstuurt order-service het event `order.cancelled` asynchroon. De consumers voeren elk hun eigen actie uit:
   - inventory-service maakt de bijbehorende `StockReservation` opnieuw vrij;
   - delivery-service verwijdert de stop uit de rit en geeft de gereserveerde plaats in het tijdslot terug;
   - notification-service stuurt een melding naar de klant.
5. Het antwoord op de annuleringsaanvraag is niet afhankelijk van de verwerking door deze consumers. De bestelling is geannuleerd; de verdere afhandeling gebeurt op de achtergrond.

---

## 4. Eerste overzicht van de API

Alle publieke aanvragen verlopen via de gateway op `http://localhost:8080`. De gebruikte headers zijn `X-User-Id` en `X-User-Role`. Mogelijke rollen zijn `KLANT`, `MEDEWERKER` en `CHAUFFEUR`.

### Publieke endpoints

| Service   | Methode en pad                                             | Vereiste rol | Functie                                                  | Succes-/resultaatcodes |
| --------- | ---------------------------------------------------------- | ------------ | -------------------------------------------------------- | ---------------------- |
| recipe    | `GET /api/recipes?week=`                                   | iedereen     | Weekmenu opvragen (US1)                                  | 200                    |
| recipe    | `GET /api/recipes/{id}`                                    | iedereen     | Receptdetails inclusief ingrediënten                     | 200 / 404              |
| recipe    | `POST /api/recipes`                                        | MEDEWERKER   | Recept toevoegen (CRUD, week 9)                          | 201 / 400              |
| recipe    | `PUT /api/recipes/{id}`                                    | MEDEWERKER   | Bestaand recept aanpassen                                | 200 / 404              |
| recipe    | `DELETE /api/recipes/{id}`                                 | MEDEWERKER   | Recept verwijderen                                       | 204 / 404              |
| order     | `POST /api/orders`                                         | KLANT        | Een bestelling plaatsen (US2)                            | 201 / 400 / 409        |
| order     | `GET /api/orders`                                          | KLANT        | Eigen bestellingen raadplegen (US3)                      | 200                    |
| order     | `GET /api/orders/{id}`                                     | KLANT        | Bestelgegevens en status bekijken                        | 200 / 404              |
| order     | `PATCH /api/orders/{id}/cancel`                            | KLANT        | Bestelling annuleren (US6)                               | 200 / 404 / 409        |
| inventory | `GET /api/stock`                                           | MEDEWERKER   | Voorraad bekijken (US4); filter `?lowOnly=true` mogelijk | 200                    |
| inventory | `PATCH /api/stock/{ingredientId}/replenish`                | MEDEWERKER   | Voorraad aanvullen (US4)                                 | 200 / 404 / 400        |
| delivery  | `GET /api/delivery-slots?date=`                            | KLANT        | Leveringsslots met beschikbare capaciteit opvragen       | 200                    |
| delivery  | `GET /api/delivery-runs?date=`                             | CHAUFFEUR    | Eigen bezorgritten bekijken (US5)                        | 200                    |
| delivery  | `GET /api/delivery-runs/{id}`                              | CHAUFFEUR    | Stops van een specifieke rit opvragen                    | 200 / 404              |
| delivery  | `PATCH /api/delivery-runs/{id}/start`                      | CHAUFFEUR    | Een bezorgrit starten                                    | 200 / 404              |
| delivery  | `PATCH /api/delivery-runs/{id}/orders/{orderId}/delivered` | CHAUFFEUR    | Een levering afvinken (US5)                              | 200 / 404              |

### Interne endpoints

Deze endpoints zijn uitsluitend bedoeld voor communicatie tussen services en zijn niet bereikbaar via de gateway.

| Service   | Endpoint                            | Aangeroepen door |
| --------- | ----------------------------------- | ---------------- |
| recipe    | `GET /internal/recipes/{id}`        | order-service    |
| inventory | `POST /internal/stock/check`        | order-service    |
| delivery  | `GET /internal/delivery-slots/{id}` | order-service    |

### Algemene API-afspraken

- Endpointnamen gebruiken meervoudsvormen en staan onder `/api`. Acties die niet onder de gewone CRUD-bewerkingen vallen, zoals annuleren of een rit starten, worden als `PATCH` op een subpad aangeboden.
- We hanteren de volgende statuscodes: `201` voor aanmaken, `200` voor ophalen of aanpassen, `204` voor verwijderen, `400` voor een ongeldige request body, `403` bij een niet-toegelaten rol, `404` wanneer een resource niet bestaat en `409` wanneer voorraad, slotcapaciteit of de huidige status een actie verhindert.
- De API geeft nooit rechtstreeks database-entities terug. Alle responses worden opgebouwd uit DTO's.
- Voor fouten gebruiken alle services hetzelfde JSON-formaat. Een voorbeeld:

```json
{
  "code": "INSUFFICIENT_STOCK",
  "message": "Onvoldoende voorraad voor: arborio (nodig 600 g, beschikbaar 450 g)",
  "timestamp": "2026-11-17T10:15:30Z",
  "path": "/api/orders"
}
```

De mogelijke foutcodes zijn `VALIDATION_ERROR`, `NOT_FOUND`, `FORBIDDEN`, `INSUFFICIENT_STOCK`, `SLOT_FULL`, `ORDER_NOT_CANCELLABLE` en `SERVICE_UNAVAILABLE`.

---

## 5. Afbakening en bewuste beperkingen

Om de opdracht beheersbaar te houden, worden een aantal onderdelen bewust vereenvoudigd.

| Keuze                                                                                                                                                  | Gevolg of beperking                                                                                                                                      |
| ------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Er wordt geen volwaardige authenticatie gebouwd. Gebruiker en rol worden via headers aangeleverd.                                                      | De backend vertrouwt de inhoud van deze headers. Er zijn geen tokens of loginfunctionaliteiten.                                                          |
| Er komt geen customer-service. Klantgegevens worden als snapshot in `Order` opgeslagen.                                                                | Een adreswijziging achteraf verandert de gegevens van reeds geplaatste bestellingen niet.                                                                |
| Bezorgritten worden niet automatisch geoptimaliseerd. Een `DeliveryRun` bestaat uit de bestellingen van een tijdslot.                                  | De stops volgen de volgorde waarin ze werden ingevoerd.                                                                                                  |
| Er worden geen echte e-mails verstuurd. Notification-service schrijft alleen een logregel.                                                             | Er is geen retrymechanisme of opslag van verzonden meldingen.                                                                                            |
| Betalingen en prijzen vallen buiten de opdracht.                                                                                                       | Bestellingen zijn in deze toepassing gratis.                                                                                                             |
| De voorraadcontrole tijdens het plaatsen van een bestelling controleert alleen de beschikbaarheid. De eigenlijke reservering gebeurt later asynchroon. | Twee klanten kunnen op hetzelfde moment nog dezelfde laatste voorraad als beschikbaar te zien krijgen. Dit aandachtspunt staat ook in `BESLISSINGEN.md`. |
| Events worden pas na het opslaan van de gegevens gepubliceerd; er wordt geen outbox-pattern gebruikt.                                                  | Als de service crasht tussen het opslaan en het publiceren, kan het event verloren gaan. Voor deze opdracht wordt dat risico bewust aanvaard.            |
| De volledige omgeving draait lokaal met Docker Compose.                                                                                                | Er wordt geen infrastructuur voor een productieomgeving voorzien.                                                                                        |

---

## 6. Applicatie starten

Dit onderdeel wordt tijdens week 14 verder aangevuld. Het beoogde resultaat is dat het commando `docker compose up -d`, uitgevoerd vanuit de map `backend/`, alle onderdelen opstart. De Swagger UI zal bereikbaar zijn via de gateway.

---

## 7. Planning per week

| Week | Geplande werkzaamheden                                                                                                |
| ---- | --------------------------------------------------------------------------------------------------------------------- |
| 8    | De grenzen tussen services bepalen, het domeinmodel uitwerken en de eerste API-opzet vastleggen (dit document).       |
| 9    | Vijf services voorzien van CRUD-functionaliteit en een eigen database; US1 en US4 realiseren.                         |
| 10   | Tests toevoegen, `openapi.yaml` vastleggen en tag `week10-spec` aanmaken.                                             |
| 11   | Eureka en Feign integreren en US2 werkend opleveren.                                                                  |
| 12   | Config-service, gateway en CORS configureren; tag `week12-api` plaatsen.                                              |
| 13   | RabbitMQ met drie consumers toevoegen, samen met Resilience4j en Micrometer Tracing.                                  |
| 14   | Docker Compose afronden, JaCoCo-dekking van minstens 70% behalen, springdoc toevoegen en tag `week14-final` plaatsen. |
