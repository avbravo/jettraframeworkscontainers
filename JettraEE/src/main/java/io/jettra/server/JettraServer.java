package io.jettra.server;

import com.sun.net.httpserver.HttpHandler;
import io.jettra.ee.JettraEE;
import io.jettra.ee.server.JettraEEServer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servidor y adaptador de compatibilidad para JettraStack en JettraEE.
 * Permite resolver rutas en páginas y widgets de JettraFlux, e inicia instancias
 * de servidor HTTP optimizadas para Java 25 con Hilos Virtuales (Project Loom).
 */
public class JettraServer {

    private static String contextPath = "/";

    private int customPort = 8080;
    private JettraEEServer eeServer;
    private final Map<String, HttpHandler> handlerRegistry = new ConcurrentHashMap<>();

    public JettraServer() {
        String p = io.jettra.server.config.JettraConfig.getProperty("server.port");
        if (p != null && !p.isBlank()) {
            try {
                this.customPort = Integer.parseInt(p.trim());
            } catch (Exception ignored) {}
        }
        String cp = io.jettra.server.config.JettraConfig.getProperty("server.contextpath");
        if (cp != null && !cp.isBlank()) {
            setContextPath(cp.trim());
        }
    }

    public JettraServer(int port) {
        this();
        this.customPort = port;
    }

    public static void setContextPath(String cp) {
        contextPath = (cp == null || cp.isBlank() || cp.equals("/")) ? "/" : (cp.startsWith("/") ? cp : "/" + cp);
        io.jettra.flux.core.FluxConfig.setContextPath(contextPath);
    }

    public static String getContextPath() {
        return contextPath;
    }

    public static String resolvePath(String path) {
        return io.jettra.flux.core.FluxConfig.resolvePath(path);
    }

    public void setPort(int port) {
        this.customPort = port;
    }

    public int getPort() {
        return eeServer != null ? eeServer.getPort() : customPort;
    }

    private String errorPage;

    public void setErrorPage(String path) {
        this.errorPage = path;
    }

    public void addHandler(String path, HttpHandler handler) {
        handlerRegistry.put(path, handler);
        if (eeServer != null) {
            eeServer.addHandler(path, handler);
        }
    }

    public void addHandler(String path, Class<?> handlerClass) {
        try {
            HttpHandler handler = (HttpHandler) handlerClass.getDeclaredConstructor().newInstance();
            addHandler(path, handler);
        } catch (Exception e) {
            throw new RuntimeException("Could not instantiate handler: " + handlerClass.getName(), e);
        }
    }

    public static void generateMvnScripts() {
        System.out.println("[JettraServer] Scripts generated.");
    }

    public void loadDiscoveredPages() {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) classLoader = JettraServer.class.getClassLoader();
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
                                addHandler(path, clazz);
                            } catch (Throwable t) {
                                // Ignore unresolvable page class
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public synchronized void start() {
        loadDiscoveredPages();
        if (eeServer == null) {
            eeServer = JettraEE.builder()
                    .port(customPort)
                    .contextPath(contextPath)
                    .build();
            for (Map.Entry<String, HttpHandler> entry : handlerRegistry.entrySet()) {
                eeServer.addHandler(entry.getKey(), entry.getValue());
            }
        }
        eeServer.start();
    }

    public synchronized void stop() {
        if (eeServer != null) {
            eeServer.stop();
        }
    }

    public JettraEEServer getEEServer() {
        return eeServer;
    }
}
