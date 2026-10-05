package io.jettra.ee;

import io.jettra.cdi.config.JettraConfig;
import io.jettra.ee.core.ClassScanner;
import io.jettra.ee.core.IO;
import io.jettra.ee.health.HealthCheck;
import io.jettra.ee.health.Liveness;
import io.jettra.ee.health.Readiness;
import io.jettra.ee.health.Startup;
import io.jettra.ee.integration.FluxIntegration;
import io.jettra.ee.jakarta.cdi.JettraCDIContainer;
import io.jettra.ee.jakarta.rest.RestDispatcher;
import io.jettra.ee.microprofile.health.HealthEndpointHandler;
import io.jettra.ee.microprofile.metrics.MetricsEndpointHandler;
import io.jettra.ee.microprofile.openapi.OpenApiEndpointHandler;
import io.jettra.ee.server.JettraEEServer;
import io.jettra.ee.server.WebResourceManager;
import io.jettra.openapi.annotations.OpenAPIDefinition;
import io.jettra.openapi.annotations.SecurityScheme;
import io.jettra.openapi.annotations.SecuritySchemes;
import io.jettra.rest.annotations.Path;
import io.jettra.rest.client.RestClient;
import io.jettra.rest.client.RestClientBuilder;
import io.jettra.scoped.ApplicationScoped;
import io.jettra.scoped.RequestScoped;
import io.jettra.scoped.SessionScoped;
import io.jettra.scoped.Singleton;
import io.jettra.scoped.ViewScoped;

import java.net.URI;
import java.util.*;

/**
 * Fachada principal y Builder moderno para JettraEE basado 100% en componentes nativos Jettra.
 * Permite iniciar el servidor con autodescubrimiento o configuración fluida programática.
 */
public class JettraEE {

    public static final String VERSION = "1.0.0-SNAPSHOT";

    public static void main(String[] args) {
        if (args != null && args.length > 0 && ("shell".equalsIgnoreCase(args[0]) || "--shell".equalsIgnoreCase(args[0]))) {
            io.jettra.ee.security.shell.JettraSecurityShell.main(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        printBanner();
        int port = 8080;
        String contextPath = "/";
        String webappRoot = null;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                } else if ("--context-path".equals(args[i]) && i + 1 < args.length) {
                    contextPath = args[++i];
                } else if ("--webapp-root".equals(args[i]) && i + 1 < args.length) {
                    webappRoot = args[++i];
                }
            }
        }

        try {
            var cfg = JettraConfig.getInstance();
            port = cfg.getOptional("server.port", Integer.class).orElse(port);
            contextPath = cfg.getOptional("server.contextpath", String.class).orElse(contextPath);
            webappRoot = cfg.getOptional("server.webapp.root", String.class).orElse(webappRoot);
        } catch (Throwable ignored) {}

        JettraEE.builder()
                .port(port)
                .contextPath(contextPath)
                .webappRoot(webappRoot)
                .scanClasspath()
                .build()
                .start();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Inicia JettraEE escaneando el paquete de la clase principal indicada.
     */
    public static JettraEEServer start(Class<?> appClass, String[] args) {
        if (args != null && args.length > 0 && ("shell".equalsIgnoreCase(args[0]) || "--shell".equalsIgnoreCase(args[0]))) {
            io.jettra.ee.security.shell.JettraSecurityShell.main(Arrays.copyOfRange(args, 1, args.length));
            System.exit(0);
        }
        printBanner();
        String basePackage = appClass.getPackageName();

        int port = 8080;
        String contextPath = "/";
        String webappRoot = null;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                } else if ("--context-path".equals(args[i]) && i + 1 < args.length) {
                    contextPath = args[++i];
                } else if ("--webapp-root".equals(args[i]) && i + 1 < args.length) {
                    webappRoot = args[++i];
                }
            }
        }

        try {
            var cfg = JettraConfig.getInstance();
            port = cfg.getOptional("server.port", Integer.class).orElse(port);
            contextPath = cfg.getOptional("server.contextpath", String.class).orElse(contextPath);
            webappRoot = cfg.getOptional("server.webapp.root", String.class).orElse(webappRoot);
        } catch (Throwable ignored) {}

        JettraEEServer server = builder()
                .port(port)
                .contextPath(contextPath)
                .webappRoot(webappRoot)
                .registerOpenApiClass(appClass)
                .scanPackages(basePackage)
                .build();

        server.start();
        return server;
    }

    public static void printBanner() {
        System.out.println(IO.CYAN + IO.BOLD);
        System.out.println("     _      _   _             _____ _____ ");
        System.out.println("    | |    | | | |           |  ___|  ___|");
        System.out.println("    | | ___| |_| |_ _ __ __ _| |__ | |__  ");
        System.out.println(" _  | |/ _ \\ __| __| '__/ _` |  __||  __| ");
        System.out.println("| |_| |  __/ |_| |_| | | (_| | |___| |___ ");
        System.out.println(" \\___/ \\___|\\__|\\__|_|  \\__,_\\____/\\____/ ");
        System.out.println(IO.RESET);
        System.out.println(IO.GREEN + " >> Jettra Stack Native Server (Ultra-lightweight) [v" + VERSION + "]" + IO.RESET);
        System.out.println(IO.PURPLE + " >> Built-in JettraCDI, JettraRest, JettraFlux & JettraRules" + IO.RESET);
        System.out.println(IO.YELLOW + " >> Java 25 Virtual Threads Engine (Project Loom)" + IO.RESET);
        System.out.println();
    }

    public static class Builder {
        private int port = 8080;
        private String contextPath = "/";
        private String webappRoot = null;
        private final List<String> scanPackages = new ArrayList<>();
        private final List<Class<?>> manualResources = new ArrayList<>();
        private final List<Class<? extends HealthCheck>> manualHealthChecks = new ArrayList<>();
        private final Map<String, Class<?>> manualFluxPages = new LinkedHashMap<>();
        private String appTitle = "JettraEE API";
        private String appVersion = "1.0.0";

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder contextPath(String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        public Builder webappRoot(String webappRoot) {
            this.webappRoot = webappRoot;
            return this;
        }

        public Builder title(String title) {
            this.appTitle = title;
            return this;
        }

        public Builder version(String version) {
            this.appVersion = version;
            return this;
        }

        public Builder scanPackages(String... packages) {
            if (packages != null) {
                this.scanPackages.addAll(Arrays.asList(packages));
            }
            return this;
        }

        public Builder scanClasspath() {
            this.scanPackages.add("");
            return this;
        }

        public Builder registerResource(Class<?> resourceClass) {
            this.manualResources.add(resourceClass);
            return this;
        }

        public Builder registerHealthCheck(Class<? extends HealthCheck> checkClass) {
            this.manualHealthChecks.add(checkClass);
            return this;
        }

        private final List<Class<?>> manualOpenApiClasses = new ArrayList<>();

        public Builder registerOpenApiClass(Class<?> clazz) {
            if (clazz != null) {
                this.manualOpenApiClasses.add(clazz);
            }
            return this;
        }

        public Builder registerFluxPage(String path, Class<?> pageClass) {
            this.manualFluxPages.put(path, pageClass);
            return this;
        }

        public JettraEEServer build() {
            // Inicializar CDI
            JettraCDIContainer.initialize();

            RestDispatcher restDispatcher = new RestDispatcher(contextPath);
            HealthEndpointHandler healthHandler = new HealthEndpointHandler();
            MetricsEndpointHandler metricsHandler = new MetricsEndpointHandler();
            OpenApiEndpointHandler openApiHandler = new OpenApiEndpointHandler(restDispatcher, appTitle, appVersion);
            FluxIntegration fluxIntegration = new FluxIntegration();

            // 0. Inicializar Subsistema JettraSecurityDB y registrar en CDI y REST
            try {
                io.jettra.ee.security.repository.JettraSecurityDBInitializer.initializeIfEmpty();
            } catch (Throwable t) {
                IO.warn("No se pudo auto-inicializar JettraSecurityDB: " + t.getMessage());
            }

            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JRoleRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JUserRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JCredentialRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.repository.JAccreditationRepositoryImpl.class);
            JettraCDIContainer.getInstance().registerBean(io.jettra.ee.security.service.JettraSecurityService.class);

            restDispatcher.registerResource(io.jettra.ee.security.rest.SecurityAuthController.class);
            restDispatcher.registerResource(io.jettra.ee.security.rest.SecurityUserController.class);
            openApiHandler.registerScannedClass(io.jettra.ee.security.rest.SecurityAuthController.class);
            openApiHandler.registerScannedClass(io.jettra.ee.security.rest.SecurityUserController.class);

            // Autodescubrimiento si hay paquetes definidos
            if (!scanPackages.isEmpty()) {
                ClassScanner scanner = new ClassScanner(scanPackages);

                // 1. Descubrir recursos Jettra REST (@Path)
                Set<Class<?>> restClasses = scanner.findAnnotatedClasses(Path.class);
                for (Class<?> rc : restClasses) {
                    restDispatcher.registerResource(rc);
                }

                // 2. Descubrir beans CDI nativos
                Set<Class<?>> appScoped = scanner.findAnnotatedClasses(ApplicationScoped.class);
                Set<Class<?>> reqScoped = scanner.findAnnotatedClasses(RequestScoped.class);
                Set<Class<?>> sessionScoped = scanner.findAnnotatedClasses(SessionScoped.class);
                Set<Class<?>> viewScoped = scanner.findAnnotatedClasses(ViewScoped.class);
                Set<Class<?>> singletons = scanner.findAnnotatedClasses(Singleton.class);
                for (Class<?> bc : appScoped) JettraCDIContainer.getInstance().registerBean(bc);
                for (Class<?> bc : reqScoped) JettraCDIContainer.getInstance().registerBean(bc);
                for (Class<?> bc : sessionScoped) JettraCDIContainer.getInstance().registerBean(bc);
                for (Class<?> bc : viewScoped) JettraCDIContainer.getInstance().registerBean(bc);
                for (Class<?> bc : singletons) JettraCDIContainer.getInstance().registerBean(bc);

                // 3. Descubrir HealthChecks
                Set<Class<?>> liveness = scanner.findAnnotatedClasses(Liveness.class);
                Set<Class<?>> readiness = scanner.findAnnotatedClasses(Readiness.class);
                Set<Class<?>> startup = scanner.findAnnotatedClasses(Startup.class);
                for (Class<?> hc : liveness) {
                    if (HealthCheck.class.isAssignableFrom(hc)) healthHandler.registerCheck((Class<? extends HealthCheck>) hc);
                }
                for (Class<?> hc : readiness) {
                    if (HealthCheck.class.isAssignableFrom(hc)) healthHandler.registerCheck((Class<? extends HealthCheck>) hc);
                }
                for (Class<?> hc : startup) {
                    if (HealthCheck.class.isAssignableFrom(hc)) healthHandler.registerCheck((Class<? extends HealthCheck>) hc);
                }

                // 4. Descubrir Rest Clients (@RestClient)
                Set<Class<?>> restClients = scanner.findAnnotatedClasses(RestClient.class);
                for (Class<?> clientInterface : restClients) {
                    try {
                        Object proxy = RestClientBuilder.create(clientInterface);
                        JettraCDIContainer.getInstance().registerSingleton((Class<Object>) clientInterface, proxy);
                        IO.info("Jettra RestClient registrado: " + clientInterface.getSimpleName());
                    } catch (Exception e) {
                        IO.warn("No se pudo auto-registrar RestClient " + clientInterface.getSimpleName() + ": " + e.getMessage());
                    }
                }

                // 5. Descubrir Páginas JettraFlux
                Set<Class<? extends io.jettra.flux.pages.FluxBaseHandler>> fluxPages = scanner.findSubclassesOf(io.jettra.flux.pages.FluxBaseHandler.class);
                for (Class<?> fp : fluxPages) {
                    if (fp.isAnnotationPresent(io.jettra.core.server.Page.class)) {
                        io.jettra.core.server.Page pageAnn = fp.getAnnotation(io.jettra.core.server.Page.class);
                        fluxIntegration.registerPage(pageAnn.path(), fp);
                    }
                }

                // 6. Descubrir anotaciones OpenAPI (@OpenAPIDefinition, @SecurityScheme, @SecuritySchemes)
                Set<Class<?>> openApiDefs = scanner.findAnnotatedClasses(OpenAPIDefinition.class);
                openApiHandler.registerScannedClasses(openApiDefs);
                Set<Class<?>> secSchemeClasses = scanner.findAnnotatedClasses(SecurityScheme.class);
                openApiHandler.registerScannedClasses(secSchemeClasses);
                Set<Class<?>> secSchemesClasses = scanner.findAnnotatedClasses(SecuritySchemes.class);
                openApiHandler.registerScannedClasses(secSchemesClasses);
            }

            // Descubrir páginas JettraFlux compiladas desde META-INF/jettra/page.classes
            loadDiscoveredPagesFromMetadata(fluxIntegration);

            // Registros manuales adicionales
            for (Class<?> r : manualResources) {
                restDispatcher.registerResource(r);
            }
            for (Class<? extends HealthCheck> hc : manualHealthChecks) {
                healthHandler.registerCheck(hc);
            }
            for (Map.Entry<String, Class<?>> entry : manualFluxPages.entrySet()) {
                fluxIntegration.registerPage(entry.getKey(), entry.getValue());
            }
            for (Class<?> c : manualOpenApiClasses) {
                openApiHandler.registerScannedClass(c);
            }

            WebResourceManager webResourceManager = new WebResourceManager(webappRoot);
            return new JettraEEServer(port, contextPath, restDispatcher, healthHandler, metricsHandler, openApiHandler, fluxIntegration, webResourceManager);
        }

        private void loadDiscoveredPagesFromMetadata(FluxIntegration fluxIntegration) {
            try {
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                if (classLoader == null) classLoader = JettraEE.class.getClassLoader();
                java.util.Enumeration<java.net.URL> resources = classLoader.getResources("META-INF/jettra/page.classes");
                while (resources.hasMoreElements()) {
                    java.net.URL url = resources.nextElement();
                    try (java.io.InputStream is = url.openStream();
                         java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith("#")) continue;
                            String[] parts = line.split("=", 2);
                            if (parts.length == 2) {
                                String className = parts[0].trim();
                                String path = parts[1].trim();
                                try {
                                    Class<?> clazz = Class.forName(className, true, classLoader);
                                    fluxIntegration.registerPage(path, clazz);
                                } catch (ClassNotFoundException e) {
                                    IO.warn("No se pudo cargar pagina JettraFlux descubierta: " + className);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                IO.warn("Error cargando META-INF/jettra/page.classes: " + e.getMessage());
            }
        }
    }
}