package io.github.ivanguirola.keycloak.csv;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.keycloak.broker.oidc.KeycloakOIDCIdentityProviderFactory;
import org.keycloak.broker.oidc.OIDCIdentityProviderFactory;
import org.keycloak.broker.oidc.mappers.AbstractClaimMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.saml.SAMLEndpoint;
import org.keycloak.broker.saml.SAMLIdentityProviderFactory;
import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderSyncMode;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;

public class CsvClaimMapper extends AbstractClaimMapper {

    public static final String ID = "csv-claim-to-attribute-mapper";

    static final String USER_ATTRIBUTE = "user.attribute";
    static final String SEPARATOR = "separator";
    static final String DEFAULT_SEPARATOR = ",";

    private static final String[] COMPATIBLE_PROVIDERS = {
        KeycloakOIDCIdentityProviderFactory.PROVIDER_ID,
        OIDCIdentityProviderFactory.PROVIDER_ID,
        SAMLIdentityProviderFactory.PROVIDER_ID
    };

    private static final Set<IdentityProviderSyncMode> SYNC_MODES = EnumSet.allOf(IdentityProviderSyncMode.class);

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES = List.of(
            property(
                    CLAIM,
                    "Claim or SAML attribute",
                    "OpenID Connect: name of the claim; use a dot to reach a nested claim, for example"
                            + " 'address.locality'. SAML: Name or FriendlyName of the attribute in the assertion.",
                    null),
            property(USER_ATTRIBUTE, "User attribute name", "User attribute that receives one value per item.", null),
            property(
                    SEPARATOR,
                    "Separator",
                    "Text that separates the items inside the value. It is read literally.",
                    DEFAULT_SEPARATOR));

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String[] getCompatibleProviders() {
        return COMPATIBLE_PROVIDERS.clone();
    }

    @Override
    public String getDisplayCategory() {
        return "Attribute Importer";
    }

    @Override
    public String getDisplayType() {
        return "Delimited Value to Attribute";
    }

    @Override
    public String getHelpText() {
        return "Splits a claim or SAML attribute such as \"1,2,3\" and stores each item as a value of a user attribute.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public boolean supportsSyncMode(IdentityProviderSyncMode syncMode) {
        return SYNC_MODES.contains(syncMode);
    }

    @Override
    public void preprocessFederatedIdentity(
            KeycloakSession session,
            RealmModel realm,
            IdentityProviderMapperModel mapperModel,
            BrokeredIdentityContext context) {
        String attribute = attribute(mapperModel);
        if (attribute == null) {
            return;
        }
        List<String> values = values(mapperModel, context);
        if (!values.isEmpty()) {
            context.setUserAttribute(attribute, values);
        }
    }

    @Override
    public void updateBrokeredUser(
            KeycloakSession session,
            RealmModel realm,
            UserModel user,
            IdentityProviderMapperModel mapperModel,
            BrokeredIdentityContext context) {
        String attribute = attribute(mapperModel);
        if (attribute == null) {
            return;
        }
        List<String> values = values(mapperModel, context);
        if (values.isEmpty()) {
            user.removeAttribute(attribute);
        } else {
            user.setAttribute(attribute, values);
        }
    }

    private static List<String> values(IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        String separator = mapperModel.getConfig().get(SEPARATOR);
        if (separator == null || separator.isEmpty()) {
            separator = DEFAULT_SEPARATOR;
        }
        return ValueSplitter.split(source(mapperModel, context), separator);
    }

    private static Object source(IdentityProviderMapperModel mapperModel, BrokeredIdentityContext context) {
        if (context.getContextData().get(SAMLEndpoint.SAML_ASSERTION) instanceof AssertionType assertion) {
            return samlValues(assertion, mapperModel.getConfig().get(CLAIM));
        }
        return getClaimValue(mapperModel, context);
    }

    private static List<Object> samlValues(AssertionType assertion, String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        String wanted = name.trim();
        return assertion.getAttributeStatements().stream()
                .flatMap(statement -> statement.getAttributes().stream())
                .map(AttributeStatementType.ASTChoiceType::getAttribute)
                .filter(attribute -> attribute != null
                        && (wanted.equals(attribute.getName()) || wanted.equals(attribute.getFriendlyName())))
                .flatMap(attribute -> attribute.getAttributeValue().stream())
                .filter(Objects::nonNull)
                .toList();
    }

    private static String attribute(IdentityProviderMapperModel mapperModel) {
        String attribute = mapperModel.getConfig().get(USER_ATTRIBUTE);
        return attribute == null || attribute.isBlank() ? null : attribute.trim();
    }

    private static ProviderConfigProperty property(String name, String label, String help, String defaultValue) {
        ProviderConfigProperty property = new ProviderConfigProperty();
        property.setName(name);
        property.setLabel(label);
        property.setHelpText(help);
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue(defaultValue);
        return property;
    }
}
