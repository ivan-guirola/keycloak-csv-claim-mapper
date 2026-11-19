package io.github.ivanguirola.keycloak.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.junit.jupiter.api.Test;
import org.keycloak.broker.oidc.KeycloakOIDCIdentityProvider;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityProviderMapper;
import org.keycloak.broker.saml.SAMLEndpoint;
import org.keycloak.dom.saml.v2.assertion.AssertionType;
import org.keycloak.dom.saml.v2.assertion.AttributeStatementType;
import org.keycloak.dom.saml.v2.assertion.AttributeType;
import org.keycloak.models.IdentityProviderMapperModel;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.IdentityProviderSyncMode;
import org.keycloak.models.UserModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.JsonWebToken;

class CsvClaimMapperTest {

    private final CsvClaimMapper mapper = new CsvClaimMapper();
    private final Map<String, List<String>> stored = new HashMap<>();

    @Test
    void hasAStableId() {
        assertEquals("csv-claim-to-attribute-mapper", mapper.getId());
    }

    @Test
    void isRegisteredAsAKeycloakProvider() {
        boolean found = ServiceLoader.load(IdentityProviderMapper.class).stream()
                .anyMatch(provider -> provider.type() == CsvClaimMapper.class);

        assertTrue(found, "missing entry in META-INF/services");
    }

    @Test
    void isOfferedForOidcProviders() {
        List<String> providers = Arrays.asList(mapper.getCompatibleProviders());

        assertTrue(providers.contains("oidc"));
        assertTrue(providers.contains("keycloak-oidc"));
    }

    @Test
    void isOfferedForSamlProviders() {
        assertTrue(Arrays.asList(mapper.getCompatibleProviders()).contains("saml"));
    }

    @Test
    void documentsItsOptions() {
        List<String> names = mapper.getConfigProperties().stream()
                .map(ProviderConfigProperty::getName)
                .toList();

        assertEquals(List.of("claim", "user.attribute", "separator"), names);
    }

    @Test
    void worksInEverySyncMode() {
        for (IdentityProviderSyncMode mode : IdentityProviderSyncMode.values()) {
            assertTrue(mapper.supportsSyncMode(mode), mode.name());
        }
    }

    @Test
    void aNewUserGetsOneValuePerItem() {
        BrokeredIdentityContext context = contextWithClaim("distributors", "1, 2,3");

        mapper.preprocessFederatedIdentity(null, null, model("distributors", "distributors", null), context);

        assertEquals(List.of("1", "2", "3"), context.getAttributes().get("distributors"));
    }

    @Test
    void usesTheConfiguredSeparator() {
        BrokeredIdentityContext context = contextWithClaim("groups", "sales;support");

        mapper.preprocessFederatedIdentity(null, null, model("groups", "teams", ";"), context);

        assertEquals(List.of("sales", "support"), context.getAttributes().get("teams"));
    }

    @Test
    void aNewUserWithoutTheClaimGetsNoAttribute() {
        BrokeredIdentityContext context = contextWithClaim("other", "x");

        mapper.preprocessFederatedIdentity(null, null, model("distributors", "distributors", ","), context);

        assertFalse(context.getAttributes().containsKey("distributors"));
    }

    @Test
    void anExistingUserIsUpdated() {
        stored.put("distributors", List.of("9"));
        BrokeredIdentityContext context = contextWithClaim("distributors", "1,2");

        mapper.updateBrokeredUser(null, null, user(), model("distributors", "distributors", ","), context);

        assertEquals(List.of("1", "2"), stored.get("distributors"));
    }

    @Test
    void anExistingUserLosesTheAttributeWhenTheClaimIsGone() {
        stored.put("distributors", List.of("9"));
        BrokeredIdentityContext context = contextWithClaim("other", "x");

        mapper.updateBrokeredUser(null, null, user(), model("distributors", "distributors", ","), context);

        assertFalse(stored.containsKey("distributors"));
    }

    @Test
    void doesNothingWithoutAnAttributeName() {
        BrokeredIdentityContext context = contextWithClaim("distributors", "1,2");

        mapper.preprocessFederatedIdentity(null, null, model("distributors", " ", ","), context);
        mapper.updateBrokeredUser(null, null, user(), model("distributors", null, ","), context);

        assertTrue(context.getAttributes().isEmpty());
        assertTrue(stored.isEmpty());
    }

    @Test
    void aSamlAttributeIsSplitIntoOneValuePerItem() {
        BrokeredIdentityContext context = contextWithSamlAttribute("distributors", null, "1, 2,3");

        mapper.preprocessFederatedIdentity(null, null, model("distributors", "distributors", ","), context);

        assertEquals(List.of("1", "2", "3"), context.getAttributes().get("distributors"));
    }

    @Test
    void aSamlAttributeIsAlsoFoundByItsFriendlyName() {
        BrokeredIdentityContext context = contextWithSamlAttribute("urn:oid:1.2.3", "teams", "sales;support");

        mapper.preprocessFederatedIdentity(null, null, model("teams", "teams", ";"), context);

        assertEquals(List.of("sales", "support"), context.getAttributes().get("teams"));
    }

    @Test
    void everyValueOfASamlAttributeIsSplit() {
        BrokeredIdentityContext context = contextWithSamlAttribute("distributors", null, "1,2", "3");

        mapper.preprocessFederatedIdentity(null, null, model("distributors", "distributors", ","), context);

        assertEquals(List.of("1", "2", "3"), context.getAttributes().get("distributors"));
    }

    @Test
    void anExistingUserIsUpdatedFromASamlAttribute() {
        stored.put("distributors", List.of("9"));
        BrokeredIdentityContext context = contextWithSamlAttribute("distributors", null, "1,2");

        mapper.updateBrokeredUser(null, null, user(), model("distributors", "distributors", ","), context);

        assertEquals(List.of("1", "2"), stored.get("distributors"));
    }

    @Test
    void anExistingUserLosesTheAttributeWhenTheSamlAttributeIsGone() {
        stored.put("distributors", List.of("9"));
        BrokeredIdentityContext context = contextWithSamlAttribute("other", null, "x");

        mapper.updateBrokeredUser(null, null, user(), model("distributors", "distributors", ","), context);

        assertFalse(stored.containsKey("distributors"));
    }

    private static BrokeredIdentityContext contextWithSamlAttribute(String name, String friendlyName, Object... values) {
        AttributeType attribute = new AttributeType(name);
        attribute.setFriendlyName(friendlyName);
        for (Object value : values) {
            attribute.addAttributeValue(value);
        }
        AttributeStatementType statement = new AttributeStatementType();
        statement.addAttribute(new AttributeStatementType.ASTChoiceType(attribute));
        AssertionType assertion = new AssertionType("assertion", now());
        assertion.addStatement(statement);
        IdentityProviderModel provider = new IdentityProviderModel();
        provider.setEnabled(true);
        BrokeredIdentityContext context = new BrokeredIdentityContext("ana", provider);
        context.getContextData().put(SAMLEndpoint.SAML_ASSERTION, assertion);
        return context;
    }

    private static XMLGregorianCalendar now() {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(new GregorianCalendar());
        } catch (DatatypeConfigurationException error) {
            throw new IllegalStateException(error);
        }
    }

    private static BrokeredIdentityContext contextWithClaim(String claim, Object value) {
        JsonWebToken token = new JsonWebToken();
        token.setOtherClaims(claim, value);
        IdentityProviderModel provider = new IdentityProviderModel();
        provider.setEnabled(true);
        BrokeredIdentityContext context = new BrokeredIdentityContext("ana", provider);
        context.getContextData().put(KeycloakOIDCIdentityProvider.VALIDATED_ID_TOKEN, token);
        return context;
    }

    private static IdentityProviderMapperModel model(String claim, String attribute, String separator) {
        Map<String, String> config = new HashMap<>();
        config.put("claim", claim);
        config.put("user.attribute", attribute);
        config.put("separator", separator);
        IdentityProviderMapperModel model = new IdentityProviderMapperModel();
        model.setConfig(config);
        return model;
    }

    @SuppressWarnings("unchecked")
    private UserModel user() {
        return (UserModel) Proxy.newProxyInstance(
                UserModel.class.getClassLoader(), new Class<?>[] {UserModel.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAttribute" -> stored.put((String) args[0], (List<String>) args[1]);
                        case "removeAttribute" -> stored.remove((String) args[0]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                    return null;
                });
    }
}
