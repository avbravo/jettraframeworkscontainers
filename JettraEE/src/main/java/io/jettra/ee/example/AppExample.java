package io.jettra.ee.example;

import io.jettra.core.inject.annotation.Inject;
import io.jettra.core.server.Page;
import io.jettra.ee.JettraEE;
import io.jettra.ee.health.HealthCheck;
import io.jettra.ee.health.HealthCheckResponse;
import io.jettra.ee.health.Liveness;
import io.jettra.ee.health.Readiness;
import io.jettra.flux.pages.FluxBaseHandler;
import io.jettra.openapi.annotations.ApiResponse;
import io.jettra.openapi.annotations.Operation;
import io.jettra.openapi.annotations.Tag;
import io.jettra.rest.annotations.Consumes;
import io.jettra.rest.annotations.GET;
import io.jettra.rest.annotations.POST;
import io.jettra.rest.annotations.Path;
import io.jettra.rest.annotations.Produces;
import io.jettra.rest.core.Response;
import io.jettra.scoped.ApplicationScoped;
import io.jettra.server.config.JettraConfigProperty;
import io.jettra.validation.Valid;
import io.jettra.validation.constraints.Email;
import io.jettra.validation.constraints.NotBlank;
import io.jettra.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Ejemplo completo de aplicación construida sobre JettraEE basada 100% en componentes nativos Jettra.
 * Demuestra:
 * 1. Jettra REST (@Path, @GET, @POST, @Produces, @Consumes)
 * 2. Jettra CDI (@Inject, @ApplicationScoped)
 * 3. Jettra Config (@JettraConfigProperty)
 * 4. Jettra Health (@Liveness, @Readiness)
 * 5. Jettra OpenAPI (@Operation, @Tag, Swagger UI)
 * 6. Jettra Validation (@Valid, @NotBlank, @Email, @Size)
 * 7. JettraFlux (@Page, UI Reactiva)
 */
public class AppExample {

    // --- 1. DTO con Jettra Validation ---
    public static class ClienteDTO {
        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
        private String nombre;

        @NotBlank(message = "El email es requerido")
        @Email(message = "Formato de email inválido")
        private String email;

        public ClienteDTO() {}

        public ClienteDTO(String nombre, String email) {
            this.nombre = nombre;
            this.email = email;
        }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    // --- 2. Servicio de Negocio con Jettra CDI y Config ---
    @ApplicationScoped
    public static class ClienteService {

        @Inject
        @JettraConfigProperty(name = "app.descuento.default")
        private int descuentoPorDefecto = 10;

        public List<ClienteDTO> listarClientes() {
            return List.of(
                    new ClienteDTO("Empresa Alfa", "contacto@alfa.com"),
                    new ClienteDTO("Beta Logistics", "info@beta.com")
            );
        }

        public List<ClienteDTO> obtenerClientesRespaldo() {
            return List.of(new ClienteDTO("Modo Respaldo / Cache", "cache@local.net"));
        }

        public int getDescuentoPorDefecto() {
            return descuentoPorDefecto;
        }
    }

    // --- 3. Recurso Jettra REST con Jettra OpenAPI ---
    @Path("/clientes")
    @Produces("application/json")
    @Consumes("application/json")
    @Tag(name = "Clientes", description = "Gestión de clientes y servicios corporativos")
    public static class ClienteResource {

        @Inject
        private ClienteService clienteService;

        @GET
        @Operation(summary = "Listar clientes", description = "Retorna todos los clientes registrados")
        @ApiResponse(responseCode = "200", description = "Lista de clientes obtenida correctamente")
        public Response getClientes() {
            List<ClienteDTO> list = clienteService.listarClientes();
            return Response.ok(list).build();
        }

        @POST
        @Operation(summary = "Registrar nuevo cliente", description = "Crea un cliente con validación Jettra")
        @ApiResponse(responseCode = "201", description = "Cliente creado exitosamente")
        public Response crearCliente(@Valid ClienteDTO cliente) {
            return Response.status(201).entity(Map.of(
                    "mensaje", "Cliente registrado con éxito",
                    "descuentoAplicado", clienteService.getDescuentoPorDefecto() + "%",
                    "cliente", cliente
            )).build();
        }
    }

    // --- 4. Jettra Health (Liveness & Readiness) ---
    @Liveness
    @ApplicationScoped
    public static class AppLivenessCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("ServicioActivo")
                    .up()
                    .withData("hilosVirtuales", "OK")
                    .build();
        }
    }

    @Readiness
    @ApplicationScoped
    public static class AppReadinessCheck implements HealthCheck {
        @Override
        public HealthCheckResponse call() {
            return HealthCheckResponse.named("BaseDeDatos")
                    .up()
                    .withData("conexion", "OK")
                    .build();
        }
    }

    // --- 5. Página de Interfaz Reactiva JettraFlux ---
    @Page(path = "/inicio")
    public static class InicioPage extends FluxBaseHandler {
        @Override
        protected String getTitle() {
            return "Bienvenido a JettraEE";
        }

        @Override
        protected io.jettra.flux.core.Widget buildUI(com.sun.net.httpserver.HttpExchange exchange, Map<String, String> params, String currentTheme) {
            return io.jettra.flux.widgets.Center.of(
                    io.jettra.flux.widgets.Column.of(
                            io.jettra.flux.widgets.Header.of(1, "JettraEE - Servidor Reactivo"),
                            io.jettra.flux.widgets.Paragraph.of("Microservicios con Jettra Stack Nativo y JettraFlux.")
                    )
            );
        }
    }

    // --- 6. Método Principal de Arranque ---
    public static void main(String[] args) {
        JettraEE.start(AppExample.class, args);
    }
}
