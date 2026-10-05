package io.jettra.ee.jakarta.cdi;

import io.jettra.cdi.JettraCDI;

/**
 * Adaptador de compatibilidad para el contenedor CDI en JettraEE.
 * Delega completamente en la implementación nativa y autónoma de JettraCDI.
 */
public class JettraCDIContainer {

    private static final JettraCDIContainer INSTANCE = new JettraCDIContainer();

    public JettraCDIContainer() {
    }

    public static JettraCDIContainer getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
        // Inicializa el contenedor JettraCDI nativo
        JettraCDI.current();
    }

    public void registerBean(Class<?> clazz) {
        JettraCDI.current().registerBean(clazz);
    }

    public <T> void registerSingleton(Class<T> clazz, T instance) {
        JettraCDI.current().registerSingleton(clazz, instance);
    }

    public <T> T getBean(Class<T> clazz) {
        return JettraCDI.current().getBean(clazz);
    }

    public Object getBeanByName(String name) {
        return JettraCDI.current().getBeanByName(name);
    }

    public <T> T createInstance(Class<T> clazz) {
        return JettraCDI.current().createInstance(clazz);
    }

    public void inject(Object instance) {
        JettraCDI.current().inject(instance);
    }

    public void invokePreDestroyAll() {
        JettraCDI.current().clearAll();
    }

    public void clearRequestScope() {
        JettraCDI.current().clearRequestScope();
    }
}
