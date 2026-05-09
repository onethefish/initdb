package cn.fish.database.repository;

import javax.sql.DataSource;

public interface DataBaseRepository {

    void test(String url, String username, String password);

    DataSource get(String id);

    DataSource add(String id, String url, String username, String password);

    void remove(String id);

    void removeAll();
}
