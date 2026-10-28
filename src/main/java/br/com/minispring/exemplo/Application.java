package br.com.minispring.exemplo;

import br.com.minispring.framework.MiniApplication;

public final class Application {
    public static void main(String[] args) throws Exception {

        try (var app = MiniApplication.run(Application.class)) {
            app.await();
        }
    }
}
