package br.com.minispring.exemplo.repository;

import br.com.minispring.framework.annotation.Component;
import br.com.minispring.framework.annotation.Value;
import br.com.minispring.framework.web.HttpException;
import com.fasterxml.jackson.databind.*;
import org.sqlite.Function;
import java.nio.file.*;
import java.sql.*;
import java.text.Normalizer;
import java.util.Locale;

/** Conexões curtas e transações; as restrições também são garantidas pelo SQLite. */
@Component
public final class Database {
    private final String url;
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }

    public Database(@Value("data.directory") String directory) {
        Path root = Path.of(directory).toAbsolutePath();
        try {
            Files.createDirectories(root);
            Class.forName("org.sqlite.JDBC");
            url = "jdbc:sqlite:" + root.resolve("cadastro-eventos.db");
            run(c -> {
                try (var s = c.createStatement()) {
                    s.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS eventos (
                          id INTEGER PRIMARY KEY AUTOINCREMENT, titulo TEXT NOT NULL CHECK(length(trim(titulo)) BETWEEN 1 AND 200),
                          capacidade INTEGER NOT NULL CHECK(capacidade BETWEEN 1 AND 5000),
                          data TEXT NOT NULL DEFAULT '', horario TEXT NOT NULL DEFAULT '', local TEXT NOT NULL DEFAULT '',
                          categoria TEXT NOT NULL DEFAULT '', descricao TEXT NOT NULL DEFAULT '')
                        """);
                    s.execute("""
                        CREATE TABLE IF NOT EXISTS pessoas (
                          id INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT NOT NULL, email TEXT NOT NULL,
                          evento_id INTEGER NOT NULL REFERENCES eventos(id) ON DELETE RESTRICT)
                        """);
                    s.execute("CREATE INDEX IF NOT EXISTS pessoas_evento ON pessoas(evento_id)");
                    s.execute("CREATE INDEX IF NOT EXISTS eventos_data ON eventos(data)");
                    for (String action : new String[]{"INSERT", "UPDATE OF evento_id"}) {
                        String name = action.startsWith("INSERT") ? "inscricao_insert" : "inscricao_update";
                        s.execute("CREATE TRIGGER IF NOT EXISTS " + name + " BEFORE " + action + " ON pessoas BEGIN "
                            + "SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM eventos WHERE id=NEW.evento_id) THEN RAISE(ABORT,'EVENT_NOT_FOUND') END; "
                            + "SELECT CASE WHEN (SELECT COUNT(*) FROM pessoas WHERE evento_id=NEW.evento_id AND id<>NEW.id) >= "
                            + "(SELECT capacidade FROM eventos WHERE id=NEW.evento_id) THEN RAISE(ABORT,'EVENT_FULL') END; END");
                    }
                    s.execute("""
                        CREATE TRIGGER IF NOT EXISTS capacidade_update BEFORE UPDATE OF capacidade ON eventos
                        WHEN NEW.capacidade < (SELECT COUNT(*) FROM pessoas WHERE evento_id=NEW.id)
                        BEGIN SELECT RAISE(ABORT,'CAPACITY_BELOW_ENROLLMENTS'); END
                        """);
                }
                migrate(c, root);
                return null;
            });
        } catch (Exception e) { throw new IllegalStateException("Não foi possível abrir ou migrar o banco em " + root, e); }
    }

    public synchronized <T> T run(Work<T> work) {
        try (Connection c = DriverManager.getConnection(url)) {
            try (var s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=10000");
            }
            Function.create(c, "normalize", new Function() {
                protected void xFunc() throws SQLException { result(normalize(value_text(0))); }
            });
            c.setAutoCommit(false);
            try {
                T result = work.run(c); c.commit(); return result;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        } catch (SQLException e) {
            String message = e.getMessage();
            if (message.contains("EVENT_NOT_FOUND")) throw new HttpException(422, "O evento informado não existe");
            if (message.contains("EVENT_FULL")) throw new HttpException(409, "O evento atingiu sua capacidade máxima");
            if (message.contains("CAPACITY_BELOW_ENROLLMENTS")) throw new HttpException(409, "A capacidade não pode ser menor que o número de inscritos");
            if (message.contains("FOREIGN KEY")) throw new HttpException(409, "Existem pessoas vinculadas a este evento. Remova ou transfira as inscrições antes de excluir");
            throw new IllegalStateException("Falha ao acessar o banco de dados", e);
        }
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void migrate(Connection c, Path root) throws SQLException {
        try (var s = c.prepareStatement("SELECT value FROM metadata WHERE key='json_migrated'")) {
            try (var r = s.executeQuery()) { if (r.next()) return; }
        }
        var json = new ObjectMapper();
        try {
            Path events = root.resolve("eventos.json"), people = root.resolve("pessoas.json");
            JsonNode eventSnapshot = Files.exists(events) ? json.readTree(events.toFile()) : null;
            JsonNode personSnapshot = Files.exists(people) ? json.readTree(people.toFile()) : null;
            for (JsonNode snapshot : new JsonNode[]{eventSnapshot, personSnapshot}) {
                if (snapshot != null && (!snapshot.isObject() || !snapshot.path("items").isArray()))
                    throw new IllegalStateException("Arquivo JSON de migração inválido; os arquivos originais foram preservados");
            }
            if (eventSnapshot != null) {
                try (var s = c.prepareStatement("INSERT INTO eventos(id,titulo,capacidade,data,horario,local,categoria,descricao) VALUES(?,?,?,?,?,?,?,?)")) {
                    for (var item : eventSnapshot.path("items")) {
                        s.setLong(1, item.path("id").asLong()); s.setString(2, item.path("titulo").asText());
                        s.setInt(3, item.has("capacidade") ? item.path("capacidade").asInt() : item.path("participantes").asInt());
                        int index = 4;
                        for (String field : new String[]{"data", "horario", "local", "categoria", "descricao"}) s.setString(index++, item.path(field).asText(""));
                        s.executeUpdate();
                    }
                }
                preserveSequence(c, "eventos", eventSnapshot.path("sequence").asLong());
            }
            if (personSnapshot != null) {
                try (var s = c.prepareStatement("INSERT INTO pessoas(id,nome,email,evento_id) VALUES(?,?,?,?)")) {
                    for (var item : personSnapshot.path("items")) {
                        s.setLong(1, item.path("id").asLong()); s.setString(2, item.path("nome").asText());
                        s.setString(3, item.path("email").asText()); s.setLong(4, item.path("eventoId").asLong()); s.executeUpdate();
                    }
                }
                preserveSequence(c, "pessoas", personSnapshot.path("sequence").asLong());
            }
            try (var s = c.createStatement()) { s.executeUpdate("INSERT INTO metadata VALUES('json_migrated','1')"); }
        } catch (java.io.IOException e) { throw new IllegalStateException("Falha na migração dos JSONs; originais preservados", e); }
    }

    private void preserveSequence(Connection c, String table, long sequence) throws SQLException {
        try (var s = c.prepareStatement("UPDATE sqlite_sequence SET seq=MAX(seq,?) WHERE name=?")) {
            s.setLong(1, sequence); s.setString(2, table);
            if (s.executeUpdate() == 0) {
                try (var insert = c.prepareStatement("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)")) {
                    insert.setString(1, table); insert.setLong(2, sequence); insert.executeUpdate();
                }
            }
        }
    }
}
