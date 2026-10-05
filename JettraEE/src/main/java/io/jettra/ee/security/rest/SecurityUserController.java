package io.jettra.ee.security.rest;

import io.jettra.core.inject.annotation.Inject;
import io.jettra.ee.security.entity.JRole;
import io.jettra.ee.security.entity.JUser;
import io.jettra.ee.security.service.JettraSecurityService;
import io.jettra.openapi.annotations.ApiResponse;
import io.jettra.openapi.annotations.Operation;
import io.jettra.openapi.annotations.Tag;
import io.jettra.rest.annotations.*;
import io.jettra.rest.annotations.accreditation.RolesAllowed;
import io.jettra.rest.core.Response;

import java.util.*;

/**
 * Controlador REST para administración de usuarios en JettraSecurityDB.
 * Protegido mediante @RolesAllowed("ADMIN").
 * Componente 100% nativo Jettra sin dependencias de Jakarta EE ni MicroProfile.
 */
@Path("/api/security/users")
@Produces("application/json")
@Consumes("application/json")
@Tag(name = "Administración de Usuarios", description = "Gestión de usuarios y credenciales en JettraSecurityDB")
@RolesAllowed("ADMIN")
public class SecurityUserController {

    @Inject
    private JettraSecurityService securityService;

    public static class CreateUserRequest {
        private String username;
        private String password;
        private String email;
        private String phone;
        private Set<String> roles;

        public CreateUserRequest() {}

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
    }

    private void ensureService() {
        if (securityService == null) {
            securityService = new JettraSecurityService();
        }
    }

    @GET
    @Operation(summary = "Listar todos los usuarios", description = "Requiere rol ADMIN. Retorna la lista de usuarios en JettraSecurityDB.")
    @ApiResponse(responseCode = "200", description = "Lista obtenida con éxito")
    @ApiResponse(responseCode = "403", description = "Acceso denegado (Requiere ADMIN)")
    public Response listUsers() {
        ensureService();
        List<JUser> users = securityService.listUsers();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JUser u : users) {
            List<String> roles = new ArrayList<>();
            if (u.jRoles() != null) {
                for (JRole r : u.jRoles()) {
                    if (r != null && r.name() != null) roles.add(r.name());
                }
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.id().toString());
            map.put("username", u.username());
            map.put("email", u.email());
            map.put("phone", u.phone());
            map.put("active", u.active());
            map.put("roles", roles);
            result.add(map);
        }
        return Response.ok(result).build();
    }

    @POST
    @Operation(summary = "Crear nuevo usuario", description = "Requiere rol ADMIN. Registra un usuario y su credencial en JettraSecurityDB.")
    @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o usuario duplicado")
    public Response createUser(CreateUserRequest request) {
        ensureService();
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            return Response.status(400).entity(Map.of("error", "Username y password son obligatorios")).build();
        }

        try {
            JUser created = securityService.registerUser(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    request.getPhone(),
                    request.getRoles()
            );
            return Response.status(201).entity(Map.of(
                    "mensaje", "Usuario creado con éxito",
                    "id", created.id().toString(),
                    "username", created.username()
            )).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{username}")
    @Operation(summary = "Buscar usuario por username", description = "Requiere rol ADMIN.")
    @ApiResponse(responseCode = "200", description = "Usuario encontrado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    public Response getUser(@PathParam("username") String username) {
        ensureService();
        Optional<JUser> userOpt = securityService.findUser(username);
        if (userOpt.isEmpty()) {
            return Response.status(404).entity(Map.of("error", "Usuario no encontrado: " + username)).build();
        }
        JUser u = userOpt.get();
        List<String> roles = new ArrayList<>();
        if (u.jRoles() != null) {
            for (JRole r : u.jRoles()) {
                if (r != null && r.name() != null) roles.add(r.name());
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.id().toString());
        map.put("username", u.username());
        map.put("email", u.email());
        map.put("phone", u.phone());
        map.put("active", u.active());
        map.put("roles", roles);
        return Response.ok(map).build();
    }

    @DELETE
    @Path("/{username}")
    @Operation(summary = "Eliminar usuario", description = "Requiere rol ADMIN. El usuario 'admin' no puede ser eliminado.")
    @ApiResponse(responseCode = "200", description = "Usuario eliminado")
    @ApiResponse(responseCode = "400", description = "Operación no permitida")
    public Response deleteUser(@PathParam("username") String username) {
        ensureService();
        if ("admin".equalsIgnoreCase(username)) {
            return Response.status(400).entity(Map.of("error", "El usuario 'admin' no puede ser eliminado")).build();
        }
        try {
            securityService.deleteUser(username);
            return Response.ok(Map.of("mensaje", "Usuario eliminado: " + username)).build();
        } catch (Exception e) {
            return Response.status(400).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
