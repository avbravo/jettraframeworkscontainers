package io.jettra.ee.test;

import io.jettra.ee.security.db.JettraSecurityDB;
import io.jettra.ee.security.entity.JCredential;
import io.jettra.ee.security.entity.JUser;
import io.jettra.ee.security.repository.JCredentialRepository;
import io.jettra.ee.security.repository.JCredentialRepositoryImpl;
import io.jettra.ee.security.repository.JRoleRepository;
import io.jettra.ee.security.repository.JRoleRepositoryImpl;
import io.jettra.ee.security.repository.JUserRepository;
import io.jettra.ee.security.repository.JUserRepositoryImpl;
import io.jettra.ee.security.repository.JettraSecurityDBInitializer;
import io.jettra.ee.security.service.JettraSecurityService;
import io.jettra.test.annotation.BeforeAll;
import io.jettra.test.annotation.DisplayName;
import io.jettra.test.annotation.Test;

import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.jettra.test.core.JettraAssert.*;

public class JettraSecurityDBTest {

    private static JUserRepository userRepo;
    private static JCredentialRepository credRepo;
    private static JRoleRepository roleRepo;
    private static JettraSecurityService securityService;

    @BeforeAll
    public static void setUp() {
        JettraSecurityDBInitializer.initializeIfEmpty();
        userRepo = new JUserRepositoryImpl();
        credRepo = new JCredentialRepositoryImpl();
        roleRepo = new JRoleRepositoryImpl();
        securityService = new JettraSecurityService(userRepo, credRepo, roleRepo);
    }

    @Test
    @DisplayName("Inicialización automática de usuario admin y roles maestros")
    public void testMasterRecordsInitialization() {
        Optional<JUser> adminOpt = userRepo.findByUsername("admin");
        assertTrue(adminOpt.isPresent(), "El usuario 'admin' debe existir");

        JUser admin = adminOpt.get();
        assertEquals("admin", admin.username());
        assertTrue(admin.active());
        assertNotNull(admin.jRoles());
        assertTrue(admin.jRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.name())), "Debe tener rol ADMIN");

        Optional<JCredential> credOpt = credRepo.findByUsername("admin");
        assertTrue(credOpt.isPresent(), "La credencial de 'admin' debe existir");

        String expectedHash = JettraSecurityDBInitializer.hashPassword("admin");
        assertEquals(expectedHash, credOpt.get().passwordHash(), "La contraseña debe estar hasheada con SHA-256");
    }

    @Test
    @DisplayName("Autenticación exitosa y emisión de token JWT con JettraSecurityService")
    public void testAuthenticationAndJwtGeneration() {
        Optional<String> tokenOpt = securityService.authenticate("admin", "admin");
        assertTrue(tokenOpt.isPresent(), "La autenticación debe ser exitosa");

        String token = tokenOpt.get();
        assertNotNull(token);
        assertFalse(token.isBlank());

        // Validar token emitido
        Optional<JUser> authUserOpt = securityService.validateTokenAndGetUser(token);
        assertTrue(authUserOpt.isPresent(), "El token debe resolver al usuario 'admin'");
        assertEquals("admin", authUserOpt.get().username());
    }

    @Test
    @DisplayName("Autenticación fallida con credenciales incorrectas")
    public void testAuthenticationFailure() {
        Optional<String> tokenOpt = securityService.authenticate("admin", "password_invalido");
        assertTrue(tokenOpt.isEmpty(), "La autenticación debe fallar ante contraseña errónea");

        Optional<String> nonExistent = securityService.authenticate("usuario_inexistente", "123");
        assertTrue(nonExistent.isEmpty(), "La autenticación debe fallar ante usuario inexistente");
    }

    @Test
    @DisplayName("Lectura compatible de archivos de seguridad del paquete server")
    public void testLegacyServerHeaderCompatibility() throws Exception {
        Path baseDir = Files.createTempDirectory("jettra-security-db-legacy");
        Path collectionDir = Files.createDirectories(baseDir.resolve("jcredential"));
        Path dataFile = collectionDir.resolve("legacy.jdb");
        UUID roleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        io.jettra.server.autentification.entity.JRole legacyRole =
                new io.jettra.server.autentification.entity.JRole(roleId, "ADMIN", true);
        io.jettra.server.autentification.entity.JUser legacyUser =
                new io.jettra.server.autentification.entity.JUser(
                        userId, "legacy", "*", "legacy@jettra.io", "", true,
                        Set.of(legacyRole), Set.of("*"));
        io.jettra.server.autentification.entity.JCredential legacyCredential =
                new io.jettra.server.autentification.entity.JCredential(
                        credentialId, legacyUser, "legacy", "legacy-password-hash", true, Instant.EPOCH);

        try {
            try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(dataFile))) {
                output.writeObject(new io.jettra.server.db.security.CompactHeader());
                output.writeObject(legacyCredential);
            }

            JettraSecurityDB database = new JettraSecurityDB(baseDir.toString());
            List<JCredential> credentials = database.findAll(JCredential.class);
            assertEquals(1, credentials.size());
            assertEquals(credentialId, credentials.getFirst().id());
            assertEquals("legacy", credentials.getFirst().jUser().username());
            assertEquals("ADMIN", credentials.getFirst().jUser().jRoles().iterator().next().name());
            assertEquals(Optional.of(credentials.getFirst()), database.findById(JCredential.class, "legacy"));
        } finally {
            Files.deleteIfExists(dataFile);
            Files.deleteIfExists(collectionDir);
            Files.deleteIfExists(baseDir);
        }
    }

    @Test
    @DisplayName("Creación, búsqueda y autenticación de un nuevo usuario en JettraSecurityDB")
    public void testNewUserRegistrationAndAuthentication() {
        String testUser = "operador_" + System.currentTimeMillis();
        String testPass = "OperadorPass2026!";

        JUser created = securityService.registerUser(
                testUser,
                testPass,
                testUser + "@empresa.com",
                "555-1234",
                Set.of("USER", "MANAGER")
        );

        assertNotNull(created);
        assertEquals(testUser, created.username());

        // Verificar persistencia en repositorios
        Optional<JUser> found = userRepo.findByUsername(testUser);
        assertTrue(found.isPresent());
        assertTrue(found.get().jRoles().stream().anyMatch(r -> "MANAGER".equalsIgnoreCase(r.name())));

        // Autenticar con el nuevo usuario
        Optional<String> tokenOpt = securityService.authenticate(testUser, testPass);
        assertTrue(tokenOpt.isPresent(), "El nuevo usuario debe autenticarse exitosamente");

        Optional<JUser> tokenUser = securityService.validateTokenAndGetUser(tokenOpt.get());
        assertTrue(tokenUser.isPresent());
        assertEquals(testUser, tokenUser.get().username());

        // Limpiar
        securityService.deleteUser(testUser);
        assertTrue(userRepo.findByUsername(testUser).isEmpty());
        assertTrue(credRepo.findByUsername(testUser).isEmpty());
    }
}
