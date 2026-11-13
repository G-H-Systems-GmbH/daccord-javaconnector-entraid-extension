---
title: JavaConnector Extension Microsoft Entra ID
---

# daccord JavaConnector Extension – Microsoft Entra ID

The extension connects daccord through the JavaConnector to **Microsoft Entra ID** (formerly Azure AD). It reads users, groups and memberships through the **Microsoft Graph API** and writes changes from daccord provisioning back: creating, changing and deleting users and groups, granting and revoking memberships.

Version 2.0 is for **daccord 2.3** (Java 21, Tomcat 11). For daccord 2.2 there is the 1.0 line. Provisioning from daccord runs through the provisioning extension **daccord-provisioning-entraid-extension 2.0**, which is shipped together with this extension.

## Contents

- [Features](#features)
- [Installation in daccord](#installation-in-daccord)
- [Connection](#connection)
- [Data collection](#data-collection)
- [Provisioning](#provisioning)
- [Custom commands](#custom-commands)
- [Testing](#testing)
- [Error handling](#error-handling)
- [Technology](#technology)
- [Building](#building)

## Features

- **Data collection:** users, groups and any other Graph resource as nodes, relationships (e.g. group memberships) as relations; preview of 10 entries
- **Subqueries:** attributes from a second resource per object, e.g. the groups of a user
- **Paging** through `@odata.nextLink`, **whitelist/blacklist** and **skipproperties**
- **Provisioning:** all 19 actions for users, groups and memberships
- **Synced accounts** (hybrid with AD): read-only fields are recognised and skipped depending on the policy
- **Account names:** strategies `standalone`, `shared` and `unique` (consecutive number if the name is taken)
- **Custom commands:** setting MFA methods (`set_authenticationmethods`), free Graph requests for tests (`graphrequest`, can be disabled)
- **Authentication** with OAuth2 client credentials (MSAL4J), connection pool with renewed sign-in on an expired token, proxy support

## Installation in daccord

### Prerequisites

- daccord 2.3 with JavaConnector
- An **app registration** in Entra ID with client ID, client secret and these **application permissions** (with admin consent):

| Purpose | Permission |
|---|---|
| Data collection | `User.Read.All`, `Group.Read.All` |
| Creating and changing users | `User.ReadWrite.All` |
| Groups and memberships | `Group.ReadWrite.All` or `GroupMember.ReadWrite.All` |
| MFA methods | `UserAuthenticationMethod.ReadWrite.All` |

`signInActivity` and MFA reports need an **Entra ID P1** licence; without it the whole request to `/users` fails as soon as the field is selected.

### Files

The extension and its dependencies go into the extension directory of the JavaConnector:

```
/opt/GuH/daccord-java-connector/res/extensions/
```

| File | Note |
|---|---|
| `daccord-javaconnector-entraid-extension-2.0.x.jar` | the regular JAR, **not** the `-runnable.jar` |
| `inchorus-msgraph-plugin-2.0.x.jar` | |
| `msal4j-*.jar`, `azure-json-*.jar` | sign-in to Entra ID |
| `jackson-core-2.*.jar`, `jackson-databind-2.*.jar`, `jackson-annotations-2.*.jar`, `jackson-dataformat-xml-2.*.jar` | only if no other extension brings them already |

daccord ships Apache HttpClient 5. Restart the RuntimeEngine afterwards so that the JavaConnector loads the extension (restarting the gadget alone is not possible at the moment). A missing dependency shows up as `ClassNotFoundException` in `catalina.out`, often only on the connection test or the first collection.

If the libraries are not found although the JARs are present, or are loaded in the wrong order (e.g. `ClassCastException`), they belong in the shared library directory of the RuntimeEngine – see article **KB1005 – RuntimeEngine: Zusätzliche Libraries einbinden**. Use this as an exception only: the JARs are then visible to all web applications.

### Creating the collector

Create a collector with the JavaConnector in the daccord frontend and enter as class:

```
de.guh.extension.connector.entraid.EntraIDJavaConnectorExtension
```

The connection parameters (`gp_param*`) belong to the collector, the collection parameters (`np_param*`, `rp_param*`) to the node and relation definitions. The values correspond to the example files in `examples/`.

## Connection

| Parameter | Meaning | Example |
|---|---|---|
| `gp_param1` | authority URL | `https://login.microsoftonline.com/` |
| `gp_param2` | tenant ID | |
| `gp_param3` | tenant domain, default for the `userPrincipalName` | `yourdomain.onmicrosoft.com` |
| `gp_param4` | client ID | |
| `gp_param5` | scope | `https://graph.microsoft.com/.default` |
| `gp_param6` | connected AD domain | `YOURDOMAIN` |
| `gp_param7`–`gp_param10` | proxy: host, port, SSL proxy (`true`/`false`), user (optional) | |
| `gp_param11` | `true` enables the custom command `graphrequest` (tests only) | `false` |
| `gp_pwparam1` | client secret (encrypted by daccord) | |
| `gp_pwparam2` | proxy password (optional) | |

With an SSL proxy (`gp_param9 = true`) the proxy's certificate and host name are verified against the JVM's truststore. A certificate of its own (e.g. of a company CA) has to be imported there.

Template: `examples/gpconnect.xml`

## Data collection

### Nodes (`np_param*`, preview `cp_param*`)

| Parameter | Meaning | Example |
|---|---|---|
| `np_param1` | object type | `user` |
| `np_param2` | Graph resource | `users` |
| `np_param3` | unique attribute | `id` |
| `np_param4` | `$select`, comma separated; dots read nested fields | `id,displayName,mail` |
| `np_param5` | `$filter` (optional) | `accountEnabled eq true` |
| `np_param6` | `$expand` (optional) | |
| `np_param7` | resource of the subquery, `$id` is replaced by the unique attribute (optional) | `users/$id/memberOf/microsoft.graph.group` |
| `np_param8` | `$select` of the subquery | `displayName` |
| `np_param9` | `$filter` of the subquery (optional) | |
| `np_param10` | prefix of the attributes from the subquery | `memberof-` |
| `np_param11` | `false`: do not send `$select`, e.g. for report endpoints | |

Graph drops fields in `$select` it does not know without an error. Templates: `examples/gpnodecollection.xml` (users), `examples/gpnodecollection-group.xml` (groups), `examples/gpnodepreview*.xml`.

### Relations (`rp_param*`)

| Parameter | Meaning |
|---|---|
| `rp_param1` | object type |
| `rp_param2` | Graph resource of the source objects |
| `rp_param3` | `$select` of the source objects |
| `rp_param4` | unique attribute of the source |
| `rp_param5` | `$filter` (optional) |
| `rp_param6` | resource of the target objects (subquery with `$id`) |
| `rp_param7` | `$select` of the target objects |
| `rp_param8` | `$filter` of the target objects (optional) |
| `rp_param9` | prefix of the target attributes |

One relation line is written per pair of source and target. Templates: `examples/gprelationcollection.xml`, `examples/gprelationpreview.xml`.

### Filters in Graph

| Possible | Not possible |
|---|---|
| `eq`, `ne`, `and`, `or`, `not` | `contains()` |
| `startsWith(displayName,'Max')` | filters on lists such as `proxyAddresses`, `assignedLicenses` |
| `endsWith(...)` only partly | filters on navigation properties such as `memberOf` |

Examples: `accountEnabled eq true and userType eq 'Member'`, `startsWith(surname,'Mül')`, `department eq 'IT' or department eq 'Sales'`. Details: [Microsoft Graph – query parameters](https://learn.microsoft.com/en-us/graph/query-parameters).

### Whitelist, blacklist, skipproperties

Nodes and relations can be filtered by rules, e.g. only the users of one group:

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

### How values arrive

The provisioning extension sends two kinds of values:

| Kind | arrives as | Examples |
|---|---|---|
| **identifiers and steering values** (named variables) | `pp_config_<name>`, always in lowercase | `accountuniqueid`, `strategy`, `domainname`, `command` |
| **Graph attributes** (data XML) | `pp_data_<name>` | `givenName`, `department`, `PASSWORD` |

Graph attributes go into the request to Graph as they are; a dot builds a nested object (`passwordProfile.forceChangePasswordNextSignIn`). Any field Graph knows can be set this way without changing the extension. For backward compatibility with the provisioning extension 1.0, steering values are read as `pp_data_` as well.

### Actions

| Action | Graph | Required | Optional (default) |
|---|---|---|---|
| createAccount | `POST /users` | `accountname` or attribute `userPrincipalName` | `strategy` (`standalone`), `domainname` (`gp_param3`), `upnbase`, `upnlimit` (20), `accountenabled` (`true`), `forcechangepasswordnextsignin` (`true`) |
| modifyAccount | `PATCH /users/{id}` | `accountuniqueid` | `givenname`, `surname`, policy for synced accounts |
| deleteAccount | `DELETE /users/{id}` | `accountuniqueid` | |
| modifyAccountPassword | `PATCH /users/{id}` | `accountuniqueid`, attribute `PASSWORD` | `forcechangepasswordnextsignin` (`true`) |
| modifyAccountStatus | `PATCH /users/{id}` | `accountuniqueid`, attribute `accountEnabled` | |
| modifyAccountType | `PATCH /users/{id}` | `accountuniqueid`, `accounttype` (`Member`/`Guest`) | |
| renameAccount | `PATCH /users/{id}` | `accountuniqueid` | `accountname` (sets `mailNickname` and `userPrincipalName`), `domainname` |
| moveAccount | `PATCH /users/{id}` | `accountuniqueid` | Entra ID has no OUs; attributes such as `department` are changed |
| reconcileAccount | `GET /users/{id}` | `accountuniqueid` | `reconcileattributes` (fields of the collection) |
| customAccountCommand | see [Custom commands](#custom-commands) | `command` | |
| createSecurityObject | `POST /groups` | `securityobjectname` or attribute `displayName` | `mailnickname`, `mailenabled` (`false`), `securityenabled` (`true`) |
| modifySecurityObject, renameSecurityObject, moveSecurityObject | `PATCH /groups/{id}` | `securityobjectuniqueid` | `securityobjectname` (sets `displayName`) |
| deleteSecurityObject | `DELETE /groups/{id}` | `securityobjectuniqueid` | |
| reconcileSecurityObject | `GET /groups/{id}` | `securityobjectuniqueid` | `reconcileattributes` |
| customSecurityObjectCommand | see [Custom commands](#custom-commands) | `command` | |
| createPermission | `POST /groups/{id}/members/$ref` | `accountuniqueid`, `securityobjectuniqueid` | |
| deletePermission | `DELETE /groups/{id}/members/{id}/$ref` | `accountuniqueid`, `securityobjectuniqueid` | |

There is an example file for every action in `examples/`, with all optional parameters as comment.

### Account names (`strategy`)

| Strategy | Behaviour if the `userPrincipalName` is taken |
|---|---|
| `standalone` | error from Graph |
| `shared` | the existing account is used, nothing is created |
| `unique` | a consecutive number is appended (`max.mustermann2@…`), up to `upnlimit` attempts; the base is `upnbase`, otherwise `givenName.surname` |

Graph makes new accounts visible only after some seconds. Two creations shortly after each other can therefore pick the same number; the second one then fails with `ObjectConflict`.

### Synced accounts

Accounts synchronised from an on-premises AD are mostly read-only in Entra ID. The extension asks for the sync state live and acts according to `syncedaccount_policy`:

| Policy | modify, rename, move | modifyAccountStatus |
|---|---|---|
| `skipattributes` (default) | writes only the fields of `syncedaccount_cloudattributes` (`usageLocation,preferredLanguage`), nothing else | `ERROR_ACCOUNT_IS_SYNCED_STATUSCHANGE` |
| `fail` | `ERROR_ACCOUNT_IS_SYNCED` | `ERROR_ACCOUNT_IS_SYNCED_STATUSCHANGE` |
| `ignore` | writes everything | writes |

## Custom commands

The action `customcommand` runs a **named command** that is not tied to a change of an object. The command is chosen with `command`:

| Command | Effect | Parameters |
|---|---|---|
| `set_authenticationmethods` | registers MFA methods on a user | `accountuniqueid`, `authenticationmethods`, optional `authenticationmethod_allowedphonetypes` (`mobile`) |
| `graphrequest` | sends any request to Graph – **for tests and analysis only** | `httpmethod`, `resource`, optional `body` |

`authenticationmethods` reads `phone=+49 170 1234567;email=max.mustermann@example.com`. A `phone=` without a number takes the user's `mobilePhone`. The methods are written one by one; if one fails, the earlier ones stay registered.

`graphrequest` can do anything the app registration is allowed to. It is therefore blocked (`ERROR_GRAPHREQUEST_DISABLED`) unless `gp_param11` is `true`.

The provisioning extension passes `command` and every `request_*` value of the task; a new command needs a change of this extension only. Templates: `examples/custom-*.xml`.

## Testing

The test runs with the `-runnable.jar` against a **test tenant** – **not** in the project directory. The JAR contains all dependencies and calls the extension directly, without daccord. Afterwards the same path is checked through daccord (see below).

### Prerequisites for the test

| What | Where from |
|---|---|
| Entra ID test tenant with app registration | tenant ID, client ID, client secret, permissions as under [Prerequisites](#prerequisites) |
| encrypted client secret | from the collector of a daccord system (`gp_pwparam1`) together with its `decryptionhash` (`config.xml` of the JavaConnector), **or** encrypted yourself with the test hash `inchorus2012!` (inchorus Security Plugin) |
| a test group in Entra ID | note its object ID |

### Preparation

```bash
mvn clean package
mkdir -p ~/entratest && cp examples/*.xml ~/entratest/
cp target/daccord-javaconnector-entraid-extension-2.0.0-runnable.jar ~/entratest/
cd ~/entratest
```

Enter tenant ID, tenant domain, client ID and the encrypted secret in `gpconnect.xml`. Replace `YOUR_…` in the task files – take the object IDs (`YOUR_USER_OBJECT_ID`, `YOUR_GROUP_OBJECT_ID`) from the output after creation.

### Call

```bash
java -Dlog4j2.level=DEBUG [-Ddecryptionhash=<hash>] -jar daccord-javaconnector-entraid-extension-2.0.0-runnable.jar \
  <action> gpconnect.xml [<file>.xml] [outputdirectory]
```

Without `-Dlog4j2.level=DEBUG` there is no output. `-Ddecryptionhash` is only needed if the secret is encrypted with the hash of a daccord system. For provisioning actions the response of the extension is in the output: `<success>true</success>` (with `<objectuniqueid>` on creation) or `<success>false</success><error>…</error>`.

### Test sequence

| # | Action | File | Expected | Check in Entra ID |
|---|---|---|---|---|
| 1 | `connect` | – | connection successful | – |
| 2 | `getnodepreview` | `gpnodepreview.xml` | 10 users with groups | – |
| 3 | `getnodedata` | `gpnodecollection.xml`, output directory | CSV with all users | count matches |
| 4 | `getnodedata` | `gpnodecollection-group.xml`, output directory | CSV with all groups | – |
| 5 | `getrelationdata` | `gprelationcollection.xml`, output directory | user–group pairs | – |
| 6 | `createaccount` | `account-create.xml` | `success`, `objectuniqueid` | user exists → enter the object ID |
| 7 | `modifyaccount` | `account-modify.xml` | `success` | `jobTitle`, `department` |
| 8 | `modifyaccountpassword` | `account-password.xml` | `success` | sign-in with the new password |
| 9 | `modifyaccountstatus` | `account-status.xml` | `success` | account disabled |
| 10 | `modifyaccounttype` | `account-type.xml` | `success` | `userType` = `Guest` |
| 11 | `renameaccount` | `account-rename.xml` | `success` | new `mailNickname`, `userPrincipalName` |
| 12 | `moveaccount` | `account-move.xml` | `success` | `department`, `officeLocation` |
| 13 | `reconcileaccount` | `account-reconcile.xml` | `success`, fields of the user | – |
| 14 | `createsecurityobject` | `securityobject-create.xml` | `success`, `objectuniqueid` | group exists → enter the object ID |
| 15 | `createpermission` | `permission-create.xml` | `success` | user is member |
| 16 | `deletepermission` | `permission-delete.xml` | `success` | membership removed |
| 17 | `modifysecurityobject`, `renamesecurityobject` | `securityobject-modify.xml`, `securityobject-rename.xml` | `success` | `description`, `displayName` |
| 18 | `customaccountcommand` | `custom-set-authenticationmethods.xml` | `success` | authentication methods of the user |
| 19 | `customaccountcommand` | `custom-graphrequest.xml` with `gp_param11 = false` | `ERROR_GRAPHREQUEST_DISABLED` | – |
| 20 | `customaccountcommand` | `custom-graphrequest.xml` with `gp_param11 = true` | `success`, response of Graph | – |
| 21 | `deletesecurityobject` | `securityobject-delete.xml` | `success` | group deleted |
| 22 | `deleteaccount` | `account-delete.xml` | `success` | user deleted |

Counter-checks: `strategy = unique` with a name that is taken (a number is appended), modify on an account synced from AD (`syncedaccount_policy`).

### Through daccord

A successful test with the `-runnable.jar` does not mean that all JARs are present in daccord – see [Files](#files). Therefore afterwards:

1. Create the collector in daccord, run `connect` and a collection in the frontend.
2. Trigger provisioning through the provisioning extension Entra ID (request or lifecycle), see its README.

## Error handling

Every provisioning action returns `<response><success>false</success><error>…</error></response>` instead of an exception. `error` holds one of the following codes or the error message of Graph.

| Code | Cause |
|---|---|
| `ERROR_MISSING_ACCOUNTUNIQUEID`, `ERROR_MISSING_SECURITYOBJECTUNIQUEID` | ID missing |
| `ERROR_MISSING_USERPRINCIPALNAME` | neither `accountname` nor attribute `userPrincipalName` |
| `ERROR_MISSING_SECURITYOBJECTNAME` | group without a name |
| `ERROR_MISSING_PASSWORD`, `ERROR_MISSING_ACCOUNTENABLED`, `ERROR_MISSING_ACCOUNTTYPE` | required value of the action missing |
| `ERROR_NO_FREE_USERPRINCIPALNAME` | `unique`: all `upnlimit` attempts taken |
| `ERROR_ACCOUNT_IS_SYNCED`, `ERROR_ACCOUNT_IS_SYNCED_STATUSCHANGE` | synced account, see the policies above |
| `ERROR_MISSING_COMMAND`, `ERROR_UNKNOWN_COMMAND` | custom command without or with an unknown `command` |
| `ERROR_GRAPHREQUEST_DISABLED` | `graphrequest` without `gp_param11 = true` |
| `ERROR_MISSING_HTTPMETHOD`, `ERROR_MISSING_RESOURCE` | `graphrequest` incomplete |
| `ERROR_MISSING_AUTHENTICATIONMETHODS`, `ERROR_INVALID_AUTHENTICATIONMETHOD`, `ERROR_UNSUPPORTED_AUTHENTICATIONMETHOD`, `ERROR_UNSUPPORTED_PHONETYPE`, `ERROR_MISSING_AUTHENTICATIONMETHOD_VALUE`, `ERROR_MISSING_MOBILEPHONE` | `set_authenticationmethods` with invalid values |
| `ERROR_NO_RESPONSE` | Graph not reachable |

Provisioning writes to the log of the JavaConnector (`/opt/GuH/daccord-java-connector/logs/javaconnector.log`), not to the log of the collector.

## Technology

| | |
|---|---|
| Language | Java 21 |
| Build | Maven, Shade plugin for the `-runnable.jar` |
| Platform | daccord 2.3, connector-core 2.3, gadget-core 4.x (Jakarta) |
| Graph access | inchorus MSGraph Plugin 2.0 (MSAL4J, Apache HttpClient 5) |
| Logging | Log4j 2 |

## Building

```bash
mvn clean package
```

Result in `target/`: the JAR for daccord and the `-runnable.jar` for tests.
