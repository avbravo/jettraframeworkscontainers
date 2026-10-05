package io.jettra.ee.test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.jettra.core.inject.annotation.ConfigProperty;
import io.jettra.core.inject.annotation.Inject;
import io.jettra.ee.JettraEE;
import io.jettra.ee.health.HealthCheck;
import io.jettra.ee.health.HealthCheckResponse;
import io.jettra.ee.health.Liveness;
import io.jettra.ee.server.JettraEEServer;
import io.jettra.rest.annotations.Consumes;
import io.jettra.rest.annotations.GET;
import io.jettra.rest.annotations.POST;
import io.jettra.rest.annotations.Path;
import io.jettra.rest.annotations.Produces;
import io.jettra.rest.annotations.QueryParam;
import io.jettra.rest.core.Response;
import io.jettra.scoped.ApplicationScoped;
import io.jettra.validation.Valid;
import io.jettra.validation.constraints.NotBlank;
import io.jettra.validation.constraints.NotNull;
import io.jettra.test.annotation.AfterAll;
import io.jettra.test.annotation.BeforeAll;
import io.jettra.test.annotation.Test;

import static io.jettra.test.core.JettraAssert.*;

public class JettraEEServerTest {

    private static JettraEEServer server;
    private static final int TEST_PORT = 18888;
    private static HttpClient client;

    // --- Clases de Prueba ---

    @ApplicationScoped
    public static class GreetingService {
        @Inject
        @ConfigProperty(name = "app.greeting.prefix", defaultValue = "Bienvenido")
        private String prefix = "Bienvenido";

        public String greet(String name) {
            return prefix + ", " + (name != null ? name : "Desconocido") + "!";
        }
    }

    public static class UserDto {
        @NotNull(message = "El nombre es obligatorio")
        @NotBlank(message = "El nombre no puede estar en blanco")
        private String name;

        private int age;

        public UserDto() {}
        public UserDto(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @Path("/users")
    @Produces("application/json")
    @Consumes("application/json")
    public static class UserResource {

        @Inject
        private GreetingService greetingService;

        @GET
        @Path("/hello")
        public Response sayHello(@QueryParam("name") String name) {
            String msg = greetingService != null ? greetingService.greet(name) : "Hola " + name;
            return Response.ok("{\"message\":\"" + msg + "\"}").build();
        }

        @POST
        public Response createUser(@Valid UserDto dto) {
            return Response.status(201).entity("{\"status\":\"created\",\"name\":\"" + dto.getName() + "\"}").build();
        }
    }

    @Liveness
    @ApplicationScoped
    public static class SystemHealthCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("SystemTestProbe")
                    .up()
                    .withData("memory", "ok")
                    .build();
        }
    }

    @BeforeAll
    public static void setUp() {
        client = HttpClient.newHttpClient();

        server = JettraEE.builder()
                .port(TEST_PORT)
                .contextPath("/")
                .registerResource(UserResource.class)
                .registerHealthCheck(SystemHealthCheck.class)
                .build();

        server.start();
    }

    @AfterAll
    public static void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testServerIsRunning() {
        assertTrue(server.isRunning(), "El servidor JettraEE debería estar en ejecución");
    }

    @Test
    public void testJettraRestGetEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/users/hello?name=Carlos"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Bienvenido, Carlos!"), "La respuesta debe contener el saludo inyectado por CDI");
    }

    @Test
    public void testJettraRestPostWithValidationSuccess() throws Exception {
        String jsonPayload = "{\"name\":\"Maria\",\"age\":28}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("created"));
        assertTrue(response.body().contains("Maria"));
    }

    @Test
    public void testJettraRestPostValidationFailure() throws Exception {
        // Objeto inválido sin nombre (@NotNull, @NotBlank)
        String invalidPayload = "{\"name\":\"\",\"age\":18}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(invalidPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode(), "Debe retornar 400 Bad Request por violación de restricciones");
        assertTrue(response.body().contains("Validation Failed"));
    }

    @Test
    public void testHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/q/health"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), "El estado general debe ser UP");
        assertTrue(response.body().contains("SystemTestProbe"), "Debe listar la sonda registrada");
    }

    @Test
    public void testMetricsEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/q/metrics"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("base_memory_usedHeap_bytes"), "Debe contener métricas Prometheus");
        assertTrue(response.body().contains("vendor_http_requests_total"), "Debe contener métricas de peticiones de JettraEE");
    }

    @Test
    public void testOpenApiEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/q/openapi"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"openapi\":\"3.1.0\""), "Debe responder con especificación OpenAPI 3.1");
        assertTrue(response.body().contains("/users"), "Debe listar el path /users");
        assertTrue(response.body().contains("\"securitySchemes\""), "Debe contener components.securitySchemes");
        assertTrue(response.body().contains("\"BearerAuth\""), "Debe contener el esquema BearerAuth");
        assertTrue(response.body().contains("\"security\""), "Debe contener requerimientos de seguridad globales");

        HttpRequest uiReq = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + "/q/swagger-ui"))
                .GET()
                .build();
        HttpResponse<String> uiRes = client.send(uiReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, uiRes.statusCode());
        assertTrue(uiRes.body().contains("Swagger UI"));
        assertTrue(uiRes.body().contains("persistAuthorization: true"));
    }
}
