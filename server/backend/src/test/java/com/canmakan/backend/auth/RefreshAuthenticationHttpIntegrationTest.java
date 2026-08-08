package com.canmakan.backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.canmakan.backend.shared.security.JwtService;
import com.canmakan.backend.shared.security.JwtProperties;
import com.canmakan.backend.user.AuthenticationAccountView;
import com.canmakan.backend.user.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
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
class RefreshAuthenticationHttpIntegrationTest {

    private static final String COOKIE_NAME = "canmakan_refresh";
    private static final String EXACT_PASSWORD = "  Exact Password1!  ";
    private static final String PASSWORD_HASH =
        new BCryptPasswordEncoder(10).encode(EXACT_PASSWORD);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Value("${app.security.jwt.signing-secret}")
    private String testSigningSecret;

    @MockitoBean
    private UserAccountRepository userAccountRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        mockMvc = MockMvcBuilders
            .webAppContextSetup(applicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
    }

    @Test
    void loginPersistsOnlyTheHashAndSupportsIndependentDeviceSessions() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));

        MvcResult firstLogin = login()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.tokenHash").doesNotExist())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE,
                containsString("Path=/api/auth")
            ))
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE,
                containsString("SameSite=Strict")
            ))
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE,
                containsString("Max-Age=604800")
            ))
            .andReturn();
        String firstRawToken = refreshCookieValue(firstLogin);
        RefreshToken firstStoredToken = refreshTokenRepository.findAllByUserId(12L).getFirst();

        assertEquals(43, firstRawToken.length());
        assertEquals(
            RefreshTokenService.hashToken(firstRawToken),
            firstStoredToken.getTokenHash()
        );
        assertNotEquals(firstRawToken, firstStoredToken.getTokenHash());
        assertFalse(firstLogin.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("Secure"));

        MvcResult secondLogin = login().andExpect(status().isOk()).andReturn();
        String secondRawToken = refreshCookieValue(secondLogin);

        assertNotEquals(firstRawToken, secondRawToken);
        assertEquals(2L, refreshTokenRepository.countByUserId(12L));
    }

    @Test
    void refreshAtomicallyRotatesTheSessionAndUsesCurrentDatabaseRole() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        String oldRawToken = refreshCookieValue(
            login().andExpect(status().isOk()).andReturn()
        );
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "ADMIN")));

        MvcResult refreshResult = refresh(oldRawToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
            .andExpect(jsonPath("$.user.userId").value(12))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andReturn();
        String newRawToken = refreshCookieValue(refreshResult);
        String accessToken = accessTokenFrom(refreshResult);

        assertNotEquals(oldRawToken, newRawToken);
        assertFalse(refreshTokenRepository.existsByTokenHash(
            RefreshTokenService.hashToken(oldRawToken)
        ));
        assertTrue(refreshTokenRepository.existsByTokenHash(
            RefreshTokenService.hashToken(newRawToken)
        ));
        assertEquals(1L, refreshTokenRepository.countByUserId(12L));
        assertEquals(12L, jwtService.extractUserId(accessToken));
        assertFalse(jwtService.decodeAccessToken(accessToken).getClaims().containsKey("role"));

        assertRefreshUnauthorized(refresh(oldRawToken));
        assertEquals(1L, refreshTokenRepository.countByUserId(12L));
    }

    @Test
    void refreshIgnoresAnExpiredBearerAndRotatesTheValidCookieSession() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        String oldRawToken = refreshCookieValue(
            login().andExpect(status().isOk()).andReturn()
        );

        MvcResult refreshResult = refresh(oldRawToken, "Bearer " + expiredAccessToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())))
            .andReturn();

        assertNotEquals(oldRawToken, refreshCookieValue(refreshResult));
        assertRefreshUnauthorized(refresh(oldRawToken));
    }

    @Test
    void refreshIgnoresAMalformedBearerAndUsesTheValidCookieSession() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        String rawToken = refreshCookieValue(
            login().andExpect(status().isOk()).andReturn()
        );

        refresh(rawToken, "Bearer malformed-value")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken", not(emptyOrNullString())));
    }

    @Test
    void allRefreshCredentialAndCurrentAccountFailuresShareOneResponse() throws Exception {
        String missingCookieBody = assertRefreshUnauthorized(
            mockMvc.perform(post("/api/auth/refresh"))
        );
        String unknownTokenBody = assertRefreshUnauthorized(refresh("U".repeat(43)));

        String expiredRawToken = "E".repeat(43);
        saveSession(expiredRawToken, Instant.now().minusSeconds(1));
        String expiredBody = assertRefreshUnauthorized(refresh(expiredRawToken));
        assertSessionDeleted(expiredRawToken);

        String inactiveRawToken = "I".repeat(43);
        String missingAccountRawToken = "M".repeat(43);
        String unsupportedRoleRawToken = "R".repeat(43);
        saveSession(inactiveRawToken, Instant.now().plusSeconds(60));
        saveSession(missingAccountRawToken, Instant.now().plusSeconds(60));
        saveSession(unsupportedRoleRawToken, Instant.now().plusSeconds(60));
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", false, "USER")))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "UNSUPPORTED")));

        String inactiveBody = assertRefreshUnauthorized(refresh(inactiveRawToken));
        String missingAccountBody = assertRefreshUnauthorized(refresh(missingAccountRawToken));
        String unsupportedRoleBody = assertRefreshUnauthorized(refresh(unsupportedRoleRawToken));

        List.of(
            unknownTokenBody,
            expiredBody,
            inactiveBody,
            missingAccountBody,
            unsupportedRoleBody
        ).forEach(body -> assertEquals(missingCookieBody, body));
        assertEquals("{\"message\":\"Authentication required.\"}", missingCookieBody);
        assertSessionDeleted(inactiveRawToken);
        assertSessionDeleted(missingAccountRawToken);
        assertSessionDeleted(unsupportedRoleRawToken);
    }

    @Test
    void logoutRevokesTheRefreshSessionButLeavesTheShortLivedAccessJwtValid() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        MvcResult loginResult = login().andExpect(status().isOk()).andReturn();
        String rawRefreshToken = refreshCookieValue(loginResult);
        String accessToken = accessTokenFrom(loginResult);

        assertLogoutNoContent(logout(rawRefreshToken));
        assertSessionDeleted(rawRefreshToken);
        assertRefreshUnauthorized(refresh(rawRefreshToken));

        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(12));
    }

    @Test
    void logoutRevokesOnlyThePresentedSessionAndPreservesAnotherDeviceSession()
            throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        String sessionA = refreshCookieValue(login().andExpect(status().isOk()).andReturn());
        String sessionB = refreshCookieValue(login().andExpect(status().isOk()).andReturn());

        assertLogoutNoContent(logout(sessionA));

        assertSessionDeleted(sessionA);
        assertTrue(refreshTokenRepository.existsByTokenHash(
            RefreshTokenService.hashToken(sessionB)
        ));
        assertEquals(1L, refreshTokenRepository.countByUserId(12L));
        assertRefreshUnauthorized(refresh(sessionA));

        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        refresh(sessionB).andExpect(status().isOk());
        assertEquals(1L, refreshTokenRepository.countByUserId(12L));
    }

    @Test
    void logoutIsIdempotentForMissingBlankUnknownRevokedAndExpiredCredentials()
            throws Exception {
        String currentToken = "L".repeat(43);
        String expiredToken = "X".repeat(43);
        saveSession(currentToken, Instant.now().plusSeconds(60));
        saveSession(expiredToken, Instant.now().minusSeconds(1));

        assertLogoutNoContent(logout(currentToken));
        assertLogoutNoContent(logout(currentToken));
        assertLogoutNoContent(logout("U".repeat(43)));
        assertLogoutNoContent(logout(""));
        assertLogoutNoContent(mockMvc.perform(post("/api/auth/logout")));
        assertLogoutNoContent(logout(expiredToken));

        assertSessionDeleted(currentToken);
        assertSessionDeleted(expiredToken);
        assertEquals(0L, refreshTokenRepository.countByUserId(12L));
    }

    @Test
    void logoutDoesNotRequireAnActiveAccountOrCurrentRoleLookup() throws Exception {
        String inactiveAccountToken = "N".repeat(43);
        saveSession(inactiveAccountToken, Instant.now().plusSeconds(60));
        when(userAccountRepository.findAuthenticationAccountById(12L))
            .thenReturn(Optional.of(account(12L, "user@example.com", false, "UNSUPPORTED")));

        assertLogoutNoContent(logout(inactiveAccountToken));

        assertSessionDeleted(inactiveAccountToken);
        verify(userAccountRepository, never()).findAuthenticationAccountById(12L);
    }

    @Test
    void logoutIgnoresAnExpiredBearerAndRevokesTheValidCookieSession() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        String rawToken = refreshCookieValue(login().andExpect(status().isOk()).andReturn());

        assertLogoutNoContent(logout(rawToken, "Bearer " + expiredAccessToken()));

        assertSessionDeleted(rawToken);
        assertRefreshUnauthorized(refresh(rawToken));
    }

    @Test
    void logoutIgnoresAMalformedBearerAndRevokesTheValidCookieSession() throws Exception {
        when(userAccountRepository.findAuthenticationAccountByEmail("user@example.com"))
            .thenReturn(Optional.of(account(12L, "user@example.com", true, "USER")));
        String rawToken = refreshCookieValue(login().andExpect(status().isOk()).andReturn());

        assertLogoutNoContent(logout(rawToken, "Bearer malformed-value"));

        assertSessionDeleted(rawToken);
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"user@example.com","password":"  Exact Password1!  "}
                """));
    }

    private ResultActions refresh(String rawToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
            .cookie(new Cookie(COOKIE_NAME, rawToken)));
    }

    private ResultActions refresh(String rawToken, String authorization) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .cookie(new Cookie(COOKIE_NAME, rawToken)));
    }

    private ResultActions logout(String rawToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
            .cookie(new Cookie(COOKIE_NAME, rawToken)));
    }

    private ResultActions logout(String rawToken, String authorization) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
            .header(HttpHeaders.AUTHORIZATION, authorization)
            .cookie(new Cookie(COOKIE_NAME, rawToken)));
    }

    private MvcResult assertLogoutNoContent(ResultActions resultActions) throws Exception {
        MvcResult result = resultActions
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE,
                containsString(COOKIE_NAME + "=")
            ))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE,
                containsString("Path=/api/auth")
            ))
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE,
                containsString("SameSite=Strict")
            ))
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE,
                containsString("Max-Age=0")
            ))
            .andReturn();
        assertFalse(result.getResponse().getHeader(HttpHeaders.SET_COOKIE).contains("Secure"));
        return result;
    }

    private String assertRefreshUnauthorized(ResultActions resultActions) throws Exception {
        return resultActions
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication required."))
            .andReturn().getResponse().getContentAsString();
    }

    private void saveSession(String rawToken, Instant expiry) {
        refreshTokenRepository.saveAndFlush(new RefreshToken(
            12L,
            RefreshTokenService.hashToken(rawToken),
            expiry
        ));
    }

    private void assertSessionDeleted(String rawToken) {
        assertFalse(refreshTokenRepository.existsByTokenHash(
            RefreshTokenService.hashToken(rawToken)
        ));
    }

    private static String refreshCookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String cookiePrefix = COOKIE_NAME + "=";
        return setCookie.substring(cookiePrefix.length(), setCookie.indexOf(';'));
    }

    private static String accessTokenFrom(MvcResult result) throws Exception {
        return OBJECT_MAPPER
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
    }

    private String expiredAccessToken() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .subject("12")
            .issuedAt(now.minus(2, ChronoUnit.MINUTES))
            .expiresAt(now.minus(1, ChronoUnit.MINUTES))
            .id(UUID.randomUUID().toString())
            .build();
        SecretKey signingKey = new SecretKeySpec(
            Base64.getDecoder().decode(testSigningSecret),
            "HmacSHA256"
        );
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static AuthenticationAccountView account(
            Long userId,
            String email,
            boolean active,
            String roleName) {
        return new TestAuthenticationAccount(userId, email, PASSWORD_HASH, active, roleName);
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
