package br.com.minispring.framework;

import br.com.minispring.framework.context.*;
import br.com.minispring.framework.web.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import java.util.List;

/** Bootstrap explícito: configuração -> scanning -> IoC -> rotas -> Jetty. */
public final class MiniApplication implements AutoCloseable {
    private final Server server;
    private final ServerConnector connector;
    private final ApplicationContext context;

    private MiniApplication(Server server, ServerConnector connector, ApplicationContext context) {
        this.server = server;
        this.connector = connector;
        this.context = context;
    }

    public static MiniApplication run(Class<?> applicationClass) throws Exception {

        var config = AppConfig.load();
        return start(applicationClass.getPackageName(), config, Integer.parseInt(config.get("server.port")));
    }

    public static MiniApplication start(String basePackage, AppConfig config, int port) throws Exception {

        var context = new ApplicationContext(ComponentScanner.scan(basePackage), config);

        var router = new Router(context.beans());

        var server = new Server();
        var connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(port); 
        connector.setIdleTimeout(30_000);
        server.addConnector(connector);

        var handler = new ServletContextHandler();
        handler.setContextPath("/");
        handler.addServlet(new ServletHolder(new DispatcherServlet(router, List.of(new LoggingInterceptor()))), "/*");
        server.setHandler(handler);
        server.setStopTimeout(5_000);

        server.setStopAtShutdown(true);

        try { server.start(); }
        catch (Exception e) {
            try { server.stop(); } catch (Exception cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        System.out.println("\nMini Spring em http://127.0.0.1:" + connector.getLocalPort());
        router.descriptions().forEach(route -> System.out.println("  " + route));
        return new MiniApplication(server, connector, context);
    }

    public int port() { return connector.getLocalPort(); }
    public ApplicationContext context() { return context; }

    public void await() throws InterruptedException { server.join(); }
    @Override public void close() throws Exception { server.stop(); }
}
