package br.com.minispring.exemplo.repository;

import br.com.minispring.framework.annotation.Repository;
import br.com.minispring.exemplo.model.*;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public final class SqliteEventoRepository implements EventoRepository {
    private final Database db;
    private static final String SELECT = "SELECT e.*, (SELECT COUNT(*) FROM pessoas p WHERE p.evento_id=e.id) AS inscritos FROM eventos e ";
    public SqliteEventoRepository(Database db) { this.db = db; }
    public Evento create(String titulo, int capacidade) { return create(new EventoInput(titulo, capacidade)); }
    public Evento create(EventoInput input) {
        return db.run(c -> {
            try (var s = c.prepareStatement("INSERT INTO eventos(titulo,capacidade,data,horario,local,categoria,descricao) VALUES(?,?,?,?,?,?,?)")) {
                bind(s, input); s.executeUpdate();
            }
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT last_insert_rowid()")) { r.next(); return get(c, r.getLong(1)).orElseThrow(); }
        });
    }
    public Optional<Evento> update(long id, String titulo, int capacidade) { return update(id, new EventoInput(titulo, capacidade)); }
    public Optional<Evento> update(long id, EventoInput input) {
        return db.run(c -> {
            try (var s = c.prepareStatement("UPDATE eventos SET titulo=?,capacidade=?,data=?,horario=?,local=?,categoria=?,descricao=? WHERE id=?")) {
                bind(s, input); s.setLong(8, id); if (s.executeUpdate() == 0) return Optional.empty();
            }
            return get(c, id);
        });
    }
    private void bind(PreparedStatement s, EventoInput i) throws SQLException {
        s.setString(1, i.titulo()); s.setInt(2, i.capacidade()); s.setString(3, i.data()); s.setString(4, i.horario());
        s.setString(5, i.local()); s.setString(6, i.categoria()); s.setString(7, i.descricao());
    }
    public List<Evento> findAll() { return db.run(c -> { try (var s = c.createStatement(); var r = s.executeQuery(SELECT + "ORDER BY e.id")) { return rows(r); } }); }
    public Optional<Evento> findById(long id) { return db.run(c -> get(c, id)); }
    private Optional<Evento> get(Connection c, long id) throws SQLException {
        try (var s = c.prepareStatement(SELECT + "WHERE e.id=?")) { s.setLong(1, id); try (var r = s.executeQuery()) { return r.next() ? Optional.of(row(r)) : Optional.empty(); } }
    }
    public boolean delete(long id) { return db.run(c -> { try (var s = c.prepareStatement("DELETE FROM eventos WHERE id=?")) { s.setLong(1, id); return s.executeUpdate() > 0; } }); }

    public Pagina<Evento> page(String titulo, int page, int size, String sort, String categoria, String periodo) {
        return db.run(c -> {
            String where = "WHERE (instr(normalize(e.titulo),?)>0 OR CAST(e.id AS TEXT)=?) AND (?='' OR normalize(e.categoria)=?) ";
            String today = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();
            where += switch (periodo) {
                case "futuros" -> "AND e.data>='" + today + "' ";
                case "passados" -> "AND e.data<>'' AND e.data<'" + today + "' ";
                case "sem-data" -> "AND e.data='' ";
                default -> "";
            };
            String order = switch (sort) {
                case "newest" -> "e.id DESC"; case "title" -> "normalize(e.titulo), e.id";
                case "participants", "capacidade" -> "e.capacidade DESC, e.id"; case "inscritos" -> "inscritos DESC, e.id";
                case "data" -> "e.data='', e.data, e.horario, e.id"; default -> "e.id";
            };
            long total;
            try (var s = c.prepareStatement("SELECT COUNT(*) FROM eventos e " + where)) {
                filters(s, titulo, categoria); try (var r = s.executeQuery()) { r.next(); total = r.getLong(1); }
            }
            long pages = Math.max(1, (total + size - 1) / size);
            int actualPage = (int)Math.min(page, pages);
            List<Evento> items;
            try (var s = c.prepareStatement(SELECT + where + "ORDER BY " + order + " LIMIT ? OFFSET ?")) {
                filters(s, titulo, categoria); s.setInt(5, size); s.setLong(6, (long)(actualPage - 1) * size);
                try (var r = s.executeQuery()) { items = rows(r); }
            }
            Pagina.Resumo resumo;
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*), COALESCE(SUM(capacidade),0), (SELECT COUNT(*) FROM pessoas) FROM eventos")) {
                r.next(); resumo = new Pagina.Resumo(r.getLong(1), r.getLong(2), r.getLong(3));
            }
            return new Pagina<>(items, total, actualPage, size, pages, resumo);
        });
    }
    private void filters(PreparedStatement s, String titulo, String categoria) throws SQLException {
        s.setString(1, Database.normalize(titulo)); s.setString(2, titulo); s.setString(3, categoria); s.setString(4, Database.normalize(categoria));
    }
    private List<Evento> rows(ResultSet r) throws SQLException { var list = new ArrayList<Evento>(); while (r.next()) list.add(row(r)); return list; }
    private Evento row(ResultSet r) throws SQLException {
        return new Evento(r.getLong("id"), r.getString("titulo"), r.getInt("capacidade"), r.getInt("inscritos"), r.getString("data"), r.getString("horario"), r.getString("local"), r.getString("categoria"), r.getString("descricao"));
    }
}
