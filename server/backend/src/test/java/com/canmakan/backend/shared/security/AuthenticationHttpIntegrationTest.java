package com.canmakan.backend.shared.security;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.auth.repository.RefreshTokenRepository;
import com.canmakan.backend.dietaryprofile.dto.CreateSelfProfileRequest;
import com.canmakan.backend.dietaryprofile.dto.SelfProfileResponse;
import com.canmakan.backend.dietaryprofile.exception.SelfProfileAlreadyExistsException;
import com.canmakan.backend.dietaryprofile.service.DietaryProfileService;
import com.canmakan.backend.user.AuthenticationAccountView;
import com.canmakan.backend.user.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthenticationHttpIntegrationTest {

    private static final String EXACT_PASSWORD = "  Exact Password1!  ";
    private static final String PASSWORD_HASH =
        new BCryptPasswordEncoder(10).encode(EXACT_PASSWORD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    @MockitoBean
    private DietaryProfileService dietaryProfileService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(applicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
    }

    @AfterEach
    void cleanUpRefreshSessions() {
        refreshTokenRepository.deleteAll();
    }

    @Test
    void userLoginReturnsAccessTokenAndServerDerivedUserIdentity() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));

        MvcResult result = login("  USER@EXAMPLE.COM  ", EXACT_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.userId").value(12))
            .andExpect(jsonPath("$.user.email").value("user@example.com"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andReturn();

        verify(userAccountRepository).findAuthenticationAccountByEmail("user@example.com");
        String accessToken = accessTokenFrom(result);
        assertEquals(12L, jwtService.extractUserId(accessToken));
        assertFalse(jwtService.decodeAccessToken(accessToken).getClaims().containsKey("role"));
    }

    @Test
    void adminLoginReturnsServerDerivedAdminIdentity() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("admin@example.com"))
            .thenReturn(Optional.of(account(1L, "admin@example.com", true, "ADMIN")));

        login("admin@example.com", EXACT_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.userId").value(1))
            .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void passwordIsMatchedExactlyWithoutNormalization() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));

        login("user@example.com", EXACT_PASSWORD)
            .andExpect(status().isOk());
        login("user@example.com", EXACT_PASSWORD.strip())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message")
                .value("Invalid credentials or account unavailable."));
    }

    @Test
    void missingEmailWrongPasswordAndInactiveAccountShareOneFailureResponse() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("missing@example.com"))
            .thenReturn(Optional.empty());
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        when(userAccountRepository.findAuthenticationAccountByEmail("inactive@example.com"))
            .thenReturn(Optional.of(account(13L, "inactive@example.com", false, "USER")));

        String missingBody = login("missing@example.com", EXACT_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String wrongPasswordBody = login("user@example.com", "Wrong Password1!")
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String inactiveBody = login("inactive@example.com", EXACT_PASSWORD)
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertEquals(missingBody, wrongPasswordBody);
        assertEquals(missingBody, inactiveBody);
        assertEquals("{\"message\":\"Invalid credentials or account unavailable.\"}", missingBody);
    }

    @Test
    void malformedLoginAndClientSuppliedRoleAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid login request."));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "user@example.com",
                      "password": "Password1!",
                      "role": "ADMIN"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Invalid login request."));
    }

    @Test
    void loginIgnoresAnIrrelevantMalformedBearerHeader() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));

        mockMvc.perform(post("/api/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-value")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\""
                    + EXACT_PASSWORD + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.userId").value(12));
    }

    @Test
    void loginInfrastructureFailureReturnsSafeServerError() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenThrow(new DataAccessResourceFailureException("internal database unavailable"));

        login("user@example.com", EXACT_PASSWORD)
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message")
                .value("Authentication request could not be completed."))
            .andExpect(content().string(not(containsString("internal database"))));
    }

    @Test
    void meReturnsCurrentUserAndAdminDatabaseIdentity() throws Exception {
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        when(userAccountRepository.findAuthenticationAccountById(1L))
            .thenReturn(Optional.of(account(1L, "admin@example.com", true, "ADMIN")));

        mockMvc.perform(get("/api/auth/me").header(
                HttpHeaders.AUTHORIZATION,
                bearer(jwtService.issueAccessToken(12L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(12))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(get("/api/auth/me").header(
                HttpHeaders.AUTHORIZATION,
                bearer(jwtService.issueAccessToken(1L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(1))
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void meUsesCurrentDatabaseRoleInsteadOfTokenTimeRole() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        MvcResult loginResult = login("user@example.com", EXACT_PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andReturn();
        String accessToken = accessTokenFrom(loginResult);

        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "ADMIN")));

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void missingMalformedExpiredAndTamperedTokensReturnStableUnauthorizedJson() throws Exception {
        String validToken = jwtService.issueAccessToken(12L);

        assertSecurityUnauthorized(mockMvc.perform(get("/api/auth/me")));
        assertSecurityUnauthorized(mockMvc.perform(get("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")));
        assertSecurityUnauthorized(mockMvc.perform(get("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken()))));
        assertSecurityUnauthorized(mockMvc.perform(get("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(tamper(validToken)))));
    }

    @Test
    void missingInactiveAndUnsupportedCurrentAccountsFailClosed() throws Exception {
        String token = jwtService.issueAccessToken(12L);
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(account(12L, "user@example.com", false, "USER")))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "ROLE_APP_USER")));

        assertSecurityUnauthorized(mockMvc.perform(get("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))));
        assertSecurityUnauthorized(mockMvc.perform(get("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))));
        assertSecurityUnauthorized(mockMvc.perform(get("/api/auth/me")
            .header(HttpHeaders.AUTHORIZATION, bearer(token))));
    }

    @Test
    void bearerAccountReloadInfrastructureFailureReturnsSafeServerError() throws Exception {
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenThrow(new DataAccessResourceFailureException("internal database unavailable"));

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.issueAccessToken(12L))))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message")
                .value("Authentication request could not be completed."))
            .andExpect(content().string(not(containsString("internal database"))));
    }

    @Test
    void publicEndpointBypassDoesNotIncludeUnknownAuthRoutes() throws Exception {
        assertSecurityUnauthorized(mockMvc.perform(post("/api/auth/sensitive-operation")
            .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-value")));
    }

    @Test
    void userReceivesForbiddenForAdminPatternWhileAdminPassesRoleCheck() throws Exception {
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        mockMvc.perform(get("/api/admin/not-implemented")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.issueAccessToken(12L))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied."));

        when(userAccountRepository.findAuthenticationAccountById(1L))
            .thenReturn(Optional.of(account(1L, "admin@example.com", true, "ADMIN")));
        mockMvc.perform(get("/api/admin/not-implemented")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwtService.issueAccessToken(1L))))
            .andExpect(status().isNotFound());
    }

    @Test
    void healthAndRegistrationRemainPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-value"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-value")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void selfProfileSetupRequiresAuthenticationAndUserRole() throws Exception {
        mockMvc.perform(post("/api/profiles/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileName\":\"Person Name\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required."));

        when(userAccountRepository.findAuthenticationAccountById(1L))
            .thenReturn(Optional.of(account(1L, "admin@example.com", true, "ADMIN")));
        mockMvc.perform(post("/api/profiles/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearer(jwtService.issueAccessToken(1L))
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileName\":\"Admin Name\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("Access denied."));

        verifyNoInteractions(dietaryProfileService);
    }

    @Test
    void userSelfProfileSetupUsesOnlyAuthenticatedPrincipalIdentity() throws Exception {
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        when(dietaryProfileService.createSelfProfile(
                eq(12L), any(CreateSelfProfileRequest.class)))
            .thenReturn(new SelfProfileResponse(
                77L,
                "Person Name",
                "SELF",
                true,
                Map.of(2L, "STRICT_AVOID")
            ));

        mockMvc.perform(post("/api/profiles/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearer(jwtService.issueAccessToken(12L))
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "profileName": "Person Name",
                      "restrictions": {"2": "STRICT_AVOID"}
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.profileId").value(77))
            .andExpect(jsonPath("$.profileName").value("Person Name"))
            .andExpect(jsonPath("$.relationship").value("SELF"))
            .andExpect(jsonPath("$.restrictions.2").value("STRICT_AVOID"));

        verify(dietaryProfileService).createSelfProfile(
            eq(12L),
            eq(new CreateSelfProfileRequest(
                "Person Name",
                Map.of(2L, "STRICT_AVOID")
            ))
        );
    }

    @Test
    void selfProfileSetupRejectsClientSuppliedUserIdentity() throws Exception {
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));

        mockMvc.perform(post("/api/profiles/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearer(jwtService.issueAccessToken(12L))
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "profileName": "Person Name",
                      "userId": 999
                    }
                    """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(dietaryProfileService);
    }

    @Test
    void duplicateSelfProfileSetupReturnsConflict() throws Exception {
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        when(dietaryProfileService.createSelfProfile(
                eq(12L), any(CreateSelfProfileRequest.class)))
            .thenThrow(new SelfProfileAlreadyExistsException());

        mockMvc.perform(post("/api/profiles/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearer(jwtService.issueAccessToken(12L))
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"profileName\":\"Person Name\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                .value("A SELF profile already exists for this account."));
    }

    @Test
    void unsupportedSelfProfileSeverityReturnsBadRequest() throws Exception {
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        when(dietaryProfileService.createSelfProfile(
                eq(12L), any(CreateSelfProfileRequest.class)))
            .thenThrow(new IllegalArgumentException(
                "Restriction severity must be STRICT_AVOID or INTOLERANCE."
            ));

        mockMvc.perform(post("/api/profiles/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    bearer(jwtService.issueAccessToken(12L))
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "profileName": "Person Name",
                      "restrictions": {"2": "NONSENSE"}
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                .value("Restriction severity must be STRICT_AVOID or INTOLERANCE."));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private void assertSecurityUnauthorized(ResultActions resultActions) throws Exception {
        resultActions
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    private String expiredToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .subject("12")
            .issuedAt(now.minus(2, ChronoUnit.MINUTES))
            .expiresAt(now.minus(1, ChronoUnit.MINUTES))
            .id(UUID.randomUUID().toString())
            .build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtProperties.signingKey()));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String accessTokenFrom(MvcResult result) throws Exception {
        return OBJECT_MAPPER
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
    }

    private static String tamper(String token) {
        String[] parts = token.split("\\.");
        char firstSignatureCharacter = parts[2].charAt(0);
        char replacement = firstSignatureCharacter == 'A' ? 'B' : 'A';
        parts[2] = replacement + parts[2].substring(1);
        return String.join(".", parts);
    }

    private static AuthenticationAccountView account(
            Long userId,
            String email,
            boolean active,
            String roleName) {
        return new TestAuthenticationAccount(
            userId,
            email,
            PASSWORD_HASH,
            active,
            roleName
        );
    }

    private record TestAuthenticationAccount(
        Long userId,
        String email,
        String passwordHash,
        Boolean active,
        String roleName
    ) implements AuthenticationAccountView {

        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getEmail() {
            return email;
        }

        @Override
        public String getPasswordHash() {
            return passwordHash;
        }

        @Override
        public Boolean getActive() {
            return active;
        }

        @Override
        public String getRoleName() {
            return roleName;
        }
    }
}
