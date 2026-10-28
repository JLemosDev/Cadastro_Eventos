package br.com.minispring.exemplo.repository;

import br.com.minispring.framework.annotation.Repository;
import br.com.minispring.exemplo.model.Pessoa;
import java.sql.*;
import java.util.*;

@Repository
public final class SqlitePessoaRepository implements PessoaRepository {
    private final Database db;
    public SqlitePessoaRepository(Database db) { this.db = db; }
    public Pessoa create(String nome, String email, long eventoId) {
        return db.run(c -> {
            try (var s = c.prepareStatement("INSERT INTO pessoas(nome,email,evento_id) VALUES(?,?,?)")) {
                s.setString(1, nome); s.setString(2, email); s.setLong(3, eventoId); s.executeUpdate();
            }
            try (var s = c.createStatement(); var r = s.executeQuery("SELECT last_insert_rowid()")) { r.next(); return new Pessoa(r.getLong(1), nome, email, eventoId); }
        });
    }
    public List<Pessoa> findAll() {
        return db.run(c -> { var list = new ArrayList<Pessoa>(); try (var s = c.createStatement(); var r = s.executeQuery("SELECT * FROM pessoas ORDER BY id")) { while (r.next()) list.add(row(r)); } return list; });
    }
    public Optional<Pessoa> findById(long id) {
        return db.run(c -> { try (var s = c.prepareStatement("SELECT * FROM pessoas WHERE id=?")) { s.setLong(1, id); try (var r = s.executeQuery()) { return r.next() ? Optional.of(row(r)) : Optional.empty(); } } });
    }
    public Optional<Pessoa> update(long id, String nome, String email, long eventoId) {
        return db.run(c -> { try (var s = c.prepareStatement("UPDATE pessoas SET nome=?,email=?,evento_id=? WHERE id=?")) {
            s.setString(1, nome); s.setString(2, email); s.setLong(3, eventoId); s.setLong(4, id);
            return s.executeUpdate() == 0 ? Optional.empty() : Optional.of(new Pessoa(id, nome, email, eventoId));
        } });
    }
    public boolean delete(long id) { return db.run(c -> { try (var s = c.prepareStatement("DELETE FROM pessoas WHERE id=?")) { s.setLong(1, id); return s.executeUpdate() > 0; } }); }
    public long countByEvento(long id) { return db.run(c -> { try (var s = c.prepareStatement("SELECT COUNT(*) FROM pessoas WHERE evento_id=?")) { s.setLong(1, id); try (var r = s.executeQuery()) { r.next(); return r.getLong(1); } } }); }
    private Pessoa row(ResultSet r) throws SQLException { return new Pessoa(r.getLong("id"), r.getString("nome"), r.getString("email"), r.getLong("evento_id")); }
}
