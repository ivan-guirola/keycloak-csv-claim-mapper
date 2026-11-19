# Keycloak CSV claim mapper

[![CI](https://github.com/ivan-guirola/keycloak-csv-claim-mapper/actions/workflows/ci.yml/badge.svg)](https://github.com/ivan-guirola/keycloak-csv-claim-mapper/actions/workflows/ci.yml)

An identity provider mapper for Keycloak that splits a delimited value into a multivalued user attribute. The same mapper works with OpenID Connect claims and with SAML attributes.

Some identity providers send a list as a single string, in a claim:

```json
{ "distributors": "1, 2,3" }
```

or in a SAML attribute:

```xml
<saml:Attribute Name="distributors">
  <saml:AttributeValue>1, 2,3</saml:AttributeValue>
</saml:Attribute>
```

The built-in attribute importer stores that string as one value. This mapper stores three:

```
distributors = 1
distributors = 2
distributors = 3
```

Built against Keycloak 26.6 and Java 17.

## Scope

- **OpenID Connect and SAML.** One mapper, offered for OpenID Connect, Keycloak OpenID Connect and SAML identity providers. It reads the claim from the tokens or the attribute from the assertion, depending on the provider it is attached to.
- **The target attribute must accept several values.** See below.

## Install

```
mvn verify
```

Copy `target/keycloak-csv-claim-mapper.jar` to the `providers/` directory of the server and run `kc.sh build`.

To try it locally, `docker compose up` starts Keycloak on http://localhost:8080 (admin / admin) with the JAR mounted.

## Use

In the admin console, open the identity provider, go to **Mappers → Add mapper** and choose **Delimited Value to Attribute**.

| Option | Default | Description |
| --- | --- | --- |
| Claim or SAML attribute | | OpenID Connect: name of the claim; a dot reaches a nested claim, for example `address.locality`. SAML: `Name` or `FriendlyName` of the attribute. |
| User attribute name | | User attribute that receives one value per item. |
| Separator | `,` | Text that separates the items. It is read literally, so `.` and `\|` need no escaping. |

With OpenID Connect, the claim is looked up in the access token, the ID token and the UserInfo response, like the built-in mappers. With SAML, every `AttributeValue` of the attribute is read and split.

## The target attribute

Keycloak validates brokered users against the realm user profile, so the result depends on how the target attribute is declared in **Realm settings → User profile**. Checked on Keycloak 26.6:

| Target attribute | Result |
| --- | --- |
| Declared with **Multivalued** on | Works: one value per item. |
| Declared with **Multivalued** off | The first sign-in stops at the *Update Account Information* page and cannot be completed. |
| Not declared, unmanaged attributes disabled (the default) | The sign-in works, but the attribute does not appear on the user. |
| Not declared, unmanaged attributes enabled | Works: one value per item. |

Declare the attribute as multivalued. It is the only setup that both works and keeps the attribute under the control of the user profile.

## Behaviour

- Items are trimmed; empty and repeated items are dropped, keeping the first occurrence.
- A claim that is already a JSON array is accepted, and each element is split as well.
- With sync mode **Force**, the attribute is replaced on every sign-in and removed when the value stops arriving. With **Import**, it is set only when the user is created.

## Disclaimer

This project was created for educational purposes or to support my day-to-day work.

## License

MIT
