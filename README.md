---
title: JavaConnector Extension Microsoft Entra ID
---

# daccord JavaConnector Extension – Microsoft Entra ID

Die Extension verbindet daccord über den JavaConnector mit **Microsoft Entra ID** (früher Azure AD). Sie liest Benutzer, Gruppen und Mitgliedschaften über die **Microsoft Graph API** ein und schreibt Änderungen aus dem daccord-Provisioning zurück: Benutzer und Gruppen anlegen, ändern, löschen sowie Mitgliedschaften vergeben und entziehen.

Version 2.0 ist für **daccord 2.3** (Java 21, Tomcat 11). Für daccord 2.2 gibt es die Linie 1.0. Das Provisioning aus daccord heraus läuft über die Provisioning Extension **daccord-provisioning-entraid-extension 2.0**, die zusammen mit dieser Extension ausgeliefert wird.

## Inhalt

- [Funktionsumfang](#funktionsumfang)
- [Installation in daccord](#installation-in-daccord)
- [Verbindung](#verbindung)
- [Datensammlung](#datensammlung)
- [Provisioning](#provisioning)
- [Custom Commands](#custom-commands)
- [Testen](#testen)
- [Fehlerbehandlung](#fehlerbehandlung)
- [Technik](#technik)
- [Bauen](#bauen)

## Funktionsumfang

- **Datensammlung:** Benutzer, Gruppen und beliebige weitere Graph-Ressourcen als Knoten, Beziehungen (z.B. Gruppenmitgliedschaften) als Relationen; Vorschau mit 10 Einträgen
- **Unterabfragen:** Attribute aus einer zweiten Ressource je Objekt anhängen, z.B. die Gruppen eines Benutzers
- **Paging** über `@odata.nextLink`, **Whitelist/Blacklist** und **Skipproperties**
- **Provisioning:** alle 19 Aktionen für Benutzer, Gruppen und Mitgliedschaften
- **Synchronisierte Konten** (Hybrid mit AD): schreibgeschützte Felder werden erkannt und je nach Regel übersprungen
- **Kontonamen:** Strategien `standalone`, `shared` und `unique` (fortlaufende Nummer bei belegtem Namen)
- **Custom Commands:** MFA-Methoden setzen (`set_authenticationmethods`), freie Graph-Aufrufe für Tests (`graphrequest`, abschaltbar)
- **Anmeldung** per OAuth2 Client Credentials (MSAL4J), Verbindungspool mit erneuter Anmeldung bei abgelaufenem Token, Proxy-Unterstützung

## Installation in daccord

### Voraussetzungen

- daccord 2.3 mit JavaConnector
- Eine **App-Registrierung** in Entra ID mit Client-ID, Client-Secret und diesen **Anwendungsberechtigungen** (Application permissions, mit Administratorzustimmung):

| Zweck | Berechtigung |
|---|---|
| Datensammlung | `User.Read.All`, `Group.Read.All` |
| Benutzer anlegen und ändern | `User.ReadWrite.All` |
| Gruppen und Mitgliedschaften | `Group.ReadWrite.All` bzw. `GroupMember.ReadWrite.All` |
| MFA-Methoden | `UserAuthenticationMethod.ReadWrite.All` |

Für `signInActivity` und MFA-Berichte ist eine Lizenz **Entra ID P1** nötig; ohne sie scheitert der gesamte Aufruf an `/users`, sobald das Feld abgefragt wird.

### Dateien

Die Extension und ihre Abhängigkeiten gehören in das Extension-Verzeichnis des JavaConnectors:

```
/opt/GuH/daccord-java-connector/res/extensions/
```

| Datei | Hinweis |
|---|---|
| `daccord-javaconnector-entraid-extension-2.0.x.jar` | das normale JAR, **nicht** das `-runnable.jar` |
| `inchorus-msgraph-plugin-2.0.x.jar` | |
| `msal4j-*.jar`, `azure-json-*.jar` | Anmeldung bei Entra ID |
| `jackson-core-2.*.jar`, `jackson-databind-2.*.jar`, `jackson-annotations-2.*.jar`, `jackson-dataformat-xml-2.*.jar` | nur, wenn keine andere Extension sie schon mitbringt |

Apache HttpClient 5 bringt daccord mit. Danach die RuntimeEngine neu starten, damit der JavaConnector die Extension lädt (ein Neustart nur des Gadgets ist derzeit nicht möglich). Fehlt eine Abhängigkeit, steht eine `ClassNotFoundException` in `catalina.out`, oft erst beim Verbindungstest oder der ersten Sammlung.

Werden die Bibliotheken trotz vorhandener JARs nicht gefunden oder in der falschen Reihenfolge geladen (z.B. `ClassCastException`), gehören sie in das gemeinsame Bibliotheksverzeichnis der RuntimeEngine – siehe Artikel **KB1005 – RuntimeEngine: Zusätzliche Libraries einbinden**. Nur im Ausnahmefall verwenden: Die JARs sind dann für alle Webapplikationen sichtbar.

### Collector anlegen

Im daccord-Frontend einen Collector mit dem JavaConnector anlegen und als Klasse eintragen:

```
de.guh.extension.connector.entraid.EntraIDJavaConnectorExtension
```

Die Verbindungsparameter (`gp_param*`) stehen am Collector, die Parameter der Sammlungen (`np_param*`, `rp_param*`) an den Knoten- bzw. Relationsdefinitionen. Die Werte entsprechen den Example-Dateien in `examples/`.

## Verbindung

| Parameter | Bedeutung | Beispiel |
|---|---|---|
| `gp_param1` | Authority-URL | `https://login.microsoftonline.com/` |
| `gp_param2` | Tenant-ID | |
| `gp_param3` | Tenant-Domain, Standard für den `userPrincipalName` | `yourdomain.onmicrosoft.com` |
| `gp_param4` | Client-ID | |
| `gp_param5` | Scope | `https://graph.microsoft.com/.default` |
| `gp_param6` | verbundene AD-Domäne | `YOURDOMAIN` |
| `gp_param7`–`gp_param10` | Proxy: Host, Port, SSL-Proxy (`true`/`false`), Benutzer (optional) | |
| `gp_param11` | `true` schaltet den Custom Command `graphrequest` frei (nur für Tests) | `false` |
| `gp_pwparam1` | Client-Secret (wird von daccord verschlüsselt) | |
| `gp_pwparam2` | Proxy-Kennwort (optional) | |

Bei einem SSL-Proxy (`gp_param9 = true`) werden Zertifikat und Hostname des Proxys gegen den Truststore der JVM geprüft. Ein eigenes Zertifikat (z.B. einer Firmen-CA) muss dort importiert sein.

Vorlage: `examples/gpconnect.xml`

## Datensammlung

### Knoten (`np_param*`, Vorschau `cp_param*`)

| Parameter | Bedeutung | Beispiel |
|---|---|---|
| `np_param1` | Objekttyp | `user` |
| `np_param2` | Graph-Ressource | `users` |
| `np_param3` | eindeutiges Attribut | `id` |
| `np_param4` | `$select`, kommagetrennt; Punkte lesen verschachtelte Felder | `id,displayName,mail` |
| `np_param5` | `$filter` (optional) | `accountEnabled eq true` |
| `np_param6` | `$expand` (optional) | |
| `np_param7` | Ressource der Unterabfrage, `$id` wird durch das eindeutige Attribut ersetzt (optional) | `users/$id/memberOf/microsoft.graph.group` |
| `np_param8` | `$select` der Unterabfrage | `displayName` |
| `np_param9` | `$filter` der Unterabfrage (optional) | |
| `np_param10` | Präfix der Attribute aus der Unterabfrage | `memberof-` |
| `np_param11` | `false`: `$select` nicht senden, z.B. für Berichts-Endpunkte | |

Felder in `$select`, die Graph nicht kennt, verwirft Graph ohne Fehlermeldung. Vorlagen: `examples/gpnodecollection.xml` (Benutzer), `examples/gpnodecollection-group.xml` (Gruppen), `examples/gpnodepreview*.xml`.

### Relationen (`rp_param*`)

| Parameter | Bedeutung |
|---|---|
| `rp_param1` | Objekttyp |
| `rp_param2` | Graph-Ressource der Quellobjekte |
| `rp_param3` | `$select` der Quellobjekte |
| `rp_param4` | eindeutiges Attribut der Quelle |
| `rp_param5` | `$filter` (optional) |
| `rp_param6` | Ressource der Zielobjekte (Unterabfrage mit `$id`) |
| `rp_param7` | `$select` der Zielobjekte |
| `rp_param8` | `$filter` der Zielobjekte (optional) |
| `rp_param9` | Präfix der Zielattribute |

Je Paar aus Quelle und Ziel entsteht eine Relationszeile. Vorlagen: `examples/gprelationcollection.xml`, `examples/gprelationpreview.xml`.

### Filter in Graph

| Möglich | Nicht möglich |
|---|---|
| `eq`, `ne`, `and`, `or`, `not` | `contains()` |
| `startsWith(displayName,'Max')` | Filter auf Listen wie `proxyAddresses`, `assignedLicenses` |
| `endsWith(...)` nur eingeschränkt | Filter auf Navigationseigenschaften wie `memberOf` |

Beispiele: `accountEnabled eq true and userType eq 'Member'`, `startsWith(surname,'Mül')`, `department eq 'IT' or department eq 'Sales'`. Details: [Microsoft Graph – Query-Parameter](https://learn.microsoft.com/en-us/graph/query-parameters).

### Whitelist, Blacklist, Skipproperties

Knoten und Relationen lassen sich über Regeln filtern, z.B. nur Benutzer einer Gruppe:

```xml
<whitelist>
    <condition>
        <group_nexus>OR</group_nexus>
        <group_internal_nexus>AND</group_internal_nexus>
        <groups>
            <group>
                <rules>
                    <rule>
                        <attribute>memberof-displayName</attribute>
                        <operator>EQUALS</operator>
                        <value>dac-admin</value>
                    </rule>
                </rules>
            </group>
        </groups>
    </condition>
</whitelist>
```

## Provisioning

### Wie die Werte ankommen

Die Provisioning Extension schickt zwei Arten von Werten:

| Art | kommt an als | Beispiele |
|---|---|---|
| **IDs und Steuerwerte** (benannte Variablen) | `pp_config_<name>`, immer kleingeschrieben | `accountuniqueid`, `strategy`, `domainname`, `command` |
| **Graph-Attribute** (Daten-XML) | `pp_data_<name>` | `givenName`, `department`, `PASSWORD` |

Graph-Attribute gehen unverändert in den Aufruf an Graph; ein Punkt erzeugt ein verschachteltes Objekt (`passwordProfile.forceChangePasswordNextSignIn`). So lässt sich jedes Feld setzen, das Graph kennt, ohne die Extension zu ändern. Steuerwerte werden zur Rückwärtskompatibilität mit der Provisioning Extension 1.0 auch als `pp_data_` gelesen.

### Aktionen

| Aktion | Graph | Pflicht | Optional (Standard) |
|---|---|---|---|
| createAccount | `POST /users` | `accountname` oder Attribut `userPrincipalName` | `strategy` (`standalone`), `domainname` (`gp_param3`), `upnbase`, `upnlimit` (20), `accountenabled` (`true`), `forcechangepasswordnextsignin` (`true`) |
| modifyAccount | `PATCH /users/{id}` | `accountuniqueid` | `givenname`, `surname`, Regeln für synchronisierte Konten |
| deleteAccount | `DELETE /users/{id}` | `accountuniqueid` | |
| modifyAccountPassword | `PATCH /users/{id}` | `accountuniqueid`, Attribut `PASSWORD` | `forcechangepasswordnextsignin` (`true`) |
| modifyAccountStatus | `PATCH /users/{id}` | `accountuniqueid`, Attribut `accountEnabled` | |
| modifyAccountType | `PATCH /users/{id}` | `accountuniqueid`, `accounttype` (`Member`/`Guest`) | |
| renameAccount | `PATCH /users/{id}` | `accountuniqueid` | `accountname` (setzt `mailNickname` und `userPrincipalName`), `domainname` |
| moveAccount | `PATCH /users/{id}` | `accountuniqueid` | Entra ID kennt keine OUs; geändert werden Attribute wie `department` |
| reconcileAccount | `GET /users/{id}` | `accountuniqueid` | `reconcileattributes` (Felder der Sammlung) |
| customAccountCommand | siehe [Custom Commands](#custom-commands) | `command` | |
| createSecurityObject | `POST /groups` | `securityobjectname` oder Attribut `displayName` | `mailnickname`, `mailenabled` (`false`), `securityenabled` (`true`) |
| modifySecurityObject, renameSecurityObject, moveSecurityObject | `PATCH /groups/{id}` | `securityobjectuniqueid` | `securityobjectname` (setzt `displayName`) |
| deleteSecurityObject | `DELETE /groups/{id}` | `securityobjectuniqueid` | |
| reconcileSecurityObject | `GET /groups/{id}` | `securityobjectuniqueid` | `reconcileattributes` |
| customSecurityObjectCommand | siehe [Custom Commands](#custom-commands) | `command` | |
| createPermission | `POST /groups/{id}/members/$ref` | `accountuniqueid`, `securityobjectuniqueid` | |
| deletePermission | `DELETE /groups/{id}/members/{id}/$ref` | `accountuniqueid`, `securityobjectuniqueid` | |

Zu jeder Aktion gibt es eine Example-Datei in `examples/` mit allen optionalen Parametern als Kommentar.

### Kontonamen (`strategy`)

| Strategie | Verhalten bei belegtem `userPrincipalName` |
|---|---|
| `standalone` | Fehler aus Graph |
| `shared` | das vorhandene Konto wird verwendet, nichts angelegt |
| `unique` | eine fortlaufende Nummer wird angehängt (`max.mustermann2@…`), bis `upnlimit` Versuche; Basis ist `upnbase`, sonst `givenName.surname` |

Graph macht neue Konten erst nach einigen Sekunden sichtbar. Zwei Anlagen kurz hintereinander können deshalb dieselbe Nummer wählen; die zweite scheitert dann mit `ObjectConflict`.

### Synchronisierte Konten

Konten, die aus einem lokalen AD synchronisiert werden, sind in Entra ID größtenteils schreibgeschützt. Die Extension fragt den Sync-Status live ab und verhält sich nach `syncedaccount_policy`:

| Regel | modify, rename, move | modifyAccountStatus |
|---|---|---|
| `skipattributes` (Standard) | schreibt nur die Felder aus `syncedaccount_cloudattributes` (`usageLocation,preferredLanguage`), sonst nichts | `ERROR_ACCOUNT_IS_SYNCED_STATUSCHANGE` |
| `fail` | `ERROR_ACCOUNT_IS_SYNCED` | `ERROR_ACCOUNT_IS_SYNCED_STATUSCHANGE` |
| `ignore` | schreibt alles | schreibt |

## Custom Commands

Die Aktion `customcommand` führt einen **benannten Befehl** aus, der nicht zu einer Änderung eines Objekts gehört. Der Befehl wird mit `command` gewählt:

| Befehl | Wirkung | Parameter |
|---|---|---|
| `set_authenticationmethods` | hinterlegt MFA-Methoden an einem Benutzer | `accountuniqueid`, `authenticationmethods`, optional `authenticationmethod_allowedphonetypes` (`mobile`) |
| `graphrequest` | sendet einen beliebigen Aufruf an Graph – **nur für Tests und Analyse** | `httpmethod`, `resource`, optional `body` |

`authenticationmethods` hat die Form `phone=+49 170 1234567;email=max.mustermann@example.com`. Ein `phone=` ohne Nummer nimmt die `mobilePhone` des Benutzers. Die Methoden werden einzeln geschrieben; scheitert eine, bleiben die vorherigen bestehen.

`graphrequest` kann alles, was die App-Registrierung darf. Er ist deshalb gesperrt (`ERROR_GRAPHREQUEST_DISABLED`), solange `gp_param11` nicht `true` ist.

Die Provisioning Extension reicht `command` und alle `request_*`-Werte des Tasks durch; ein neuer Befehl braucht nur eine Änderung dieser Extension. Vorlagen: `examples/custom-*.xml`.

## Testen

Der Test läuft mit dem `-runnable.jar` gegen einen **Test-Tenant** – **nicht** im Projektverzeichnis. Das JAR enthält alle Abhängigkeiten und ruft die Extension direkt auf, ohne daccord. Danach wird derselbe Weg über daccord geprüft (siehe unten).

### Voraussetzungen für den Test

| Was | Woher |
|---|---|
| Entra ID-Test-Tenant mit App-Registrierung | Tenant-ID, Client-ID, Client-Secret, Berechtigungen wie unter [Voraussetzungen](#voraussetzungen) |
| verschlüsseltes Client-Secret | aus dem Collector eines daccord-Systems (`gp_pwparam1`) zusammen mit dessen `decryptionhash` (`config.xml` des JavaConnectors), **oder** selbst verschlüsselt mit dem Test-Hash `inchorus2012!` (inchorus Security Plugin) |
| eine Testgruppe in Entra ID | Object-ID notieren |

### Vorbereitung

```bash
mvn clean package
mkdir -p ~/entratest && cp examples/*.xml ~/entratest/
cp target/daccord-javaconnector-entraid-extension-2.0.0-runnable.jar ~/entratest/
cd ~/entratest
```

In `gpconnect.xml` Tenant-ID, Tenant-Domain, Client-ID und das verschlüsselte Secret eintragen. In den Auftragsdateien `YOUR_…` ersetzen – die Object-IDs (`YOUR_USER_OBJECT_ID`, `YOUR_GROUP_OBJECT_ID`) nach dem Anlegen aus der Ausgabe übernehmen.

### Aufruf

```bash
java -Dlog4j2.level=DEBUG [-Ddecryptionhash=<hash>] -jar daccord-javaconnector-entraid-extension-2.0.0-runnable.jar \
  <aktion> gpconnect.xml [<datei>.xml] [ausgabeverzeichnis]
```

Ohne `-Dlog4j2.level=DEBUG` gibt es keine Ausgabe. `-Ddecryptionhash` ist nur nötig, wenn das Secret mit dem Hash eines daccord-Systems verschlüsselt ist. Bei Provisioning-Aktionen steht die Antwort der Extension in der Ausgabe: `<success>true</success>` (mit `<objectuniqueid>` beim Anlegen) bzw. `<success>false</success><error>…</error>`.

### Testablauf

| # | Aktion | Datei | Erwartet | Prüfen in Entra ID |
|---|---|---|---|---|
| 1 | `connect` | – | Verbindung erfolgreich | – |
| 2 | `getnodepreview` | `gpnodepreview.xml` | 10 Benutzer mit Gruppen | – |
| 3 | `getnodedata` | `gpnodecollection.xml`, Ausgabeverzeichnis | CSV mit allen Benutzern | Anzahl stimmt |
| 4 | `getnodedata` | `gpnodecollection-group.xml`, Ausgabeverzeichnis | CSV mit allen Gruppen | – |
| 5 | `getrelationdata` | `gprelationcollection.xml`, Ausgabeverzeichnis | Benutzer–Gruppe-Paare | – |
| 6 | `createaccount` | `account-create.xml` | `success`, `objectuniqueid` | Benutzer vorhanden → Object-ID eintragen |
| 7 | `modifyaccount` | `account-modify.xml` | `success` | `jobTitle`, `department` |
| 8 | `modifyaccountpassword` | `account-password.xml` | `success` | Anmeldung mit neuem Kennwort |
| 9 | `modifyaccountstatus` | `account-status.xml` | `success` | Konto gesperrt |
| 10 | `modifyaccounttype` | `account-type.xml` | `success` | `userType` = `Guest` |
| 11 | `renameaccount` | `account-rename.xml` | `success` | `mailNickname`, `userPrincipalName` neu |
| 12 | `moveaccount` | `account-move.xml` | `success` | `department`, `officeLocation` |
| 13 | `reconcileaccount` | `account-reconcile.xml` | `success`, Felder des Benutzers | – |
| 14 | `createsecurityobject` | `securityobject-create.xml` | `success`, `objectuniqueid` | Gruppe vorhanden → Object-ID eintragen |
| 15 | `createpermission` | `permission-create.xml` | `success` | Benutzer ist Mitglied |
| 16 | `deletepermission` | `permission-delete.xml` | `success` | Mitgliedschaft entfernt |
| 17 | `modifysecurityobject`, `renamesecurityobject` | `securityobject-modify.xml`, `securityobject-rename.xml` | `success` | `description`, `displayName` |
| 18 | `customaccountcommand` | `custom-set-authenticationmethods.xml` | `success` | Authentifizierungsmethoden des Benutzers |
| 19 | `customaccountcommand` | `custom-graphrequest.xml` mit `gp_param11 = false` | `ERROR_GRAPHREQUEST_DISABLED` | – |
| 20 | `customaccountcommand` | `custom-graphrequest.xml` mit `gp_param11 = true` | `success`, Antwort von Graph | – |
| 21 | `deletesecurityobject` | `securityobject-delete.xml` | `success` | Gruppe gelöscht |
| 22 | `deleteaccount` | `account-delete.xml` | `success` | Benutzer gelöscht |

Gegenproben: `strategy = unique` mit einem bereits belegten Namen (Nummer wird angehängt), modify an einem aus AD synchronisierten Konto (`syncedaccount_policy`).

### Über daccord

Ein erfolgreicher Test mit dem `-runnable.jar` heißt nicht, dass in daccord alle JARs vorhanden sind – siehe [Dateien](#dateien). Deshalb danach:

1. Collector in daccord anlegen, `connect` und eine Sammlung im Frontend ausführen.
2. Provisioning über die Provisioning Extension Entra ID auslösen (Antrag bzw. Lebenszyklus), siehe deren README.

## Fehlerbehandlung

Jede Provisioning-Aktion liefert `<response><success>false</success><error>…</error></response>` statt einer Exception. `error` enthält entweder einen der folgenden Codes oder die Fehlermeldung von Graph.

| Code | Ursache |
|---|---|
| `ERROR_MISSING_ACCOUNTUNIQUEID`, `ERROR_MISSING_SECURITYOBJECTUNIQUEID` | ID fehlt |
| `ERROR_MISSING_USERPRINCIPALNAME` | weder `accountname` noch Attribut `userPrincipalName` |
| `ERROR_MISSING_SECURITYOBJECTNAME` | Gruppe ohne Namen |
| `ERROR_MISSING_PASSWORD`, `ERROR_MISSING_ACCOUNTENABLED`, `ERROR_MISSING_ACCOUNTTYPE` | Pflichtwert der Aktion fehlt |
| `ERROR_NO_FREE_USERPRINCIPALNAME` | `unique`: alle `upnlimit` Versuche belegt |
| `ERROR_ACCOUNT_IS_SYNCED`, `ERROR_ACCOUNT_IS_SYNCED_STATUSCHANGE` | synchronisiertes Konto, siehe Regeln oben |
| `ERROR_MISSING_COMMAND`, `ERROR_UNKNOWN_COMMAND` | Custom Command ohne bzw. mit unbekanntem `command` |
| `ERROR_GRAPHREQUEST_DISABLED` | `graphrequest` ohne `gp_param11 = true` |
| `ERROR_MISSING_HTTPMETHOD`, `ERROR_MISSING_RESOURCE` | `graphrequest` unvollständig |
| `ERROR_MISSING_AUTHENTICATIONMETHODS`, `ERROR_INVALID_AUTHENTICATIONMETHOD`, `ERROR_UNSUPPORTED_AUTHENTICATIONMETHOD`, `ERROR_UNSUPPORTED_PHONETYPE`, `ERROR_MISSING_AUTHENTICATIONMETHOD_VALUE`, `ERROR_MISSING_MOBILEPHONE` | `set_authenticationmethods` mit ungültigen Werten |
| `ERROR_NO_RESPONSE` | Graph nicht erreichbar |

Das Provisioning schreibt in das Log des JavaConnectors (`/opt/GuH/daccord-java-connector/logs/javaconnector.log`), nicht in das Log des Collectors.

## Technik

| | |
|---|---|
| Sprache | Java 21 |
| Build | Maven, Shade-Plugin für das `-runnable.jar` |
| Plattform | daccord 2.3, connector-core 2.3, gadget-core 4.x (Jakarta) |
| Graph-Zugriff | inchorus MSGraph Plugin 2.0 (MSAL4J, Apache HttpClient 5) |
| Logging | Log4j 2 |

## Bauen

```bash
mvn clean package
```

Ergebnis in `target/`: das JAR für daccord und das `-runnable.jar` für Tests.
