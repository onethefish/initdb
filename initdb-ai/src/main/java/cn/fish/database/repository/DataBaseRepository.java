package cn.fish.database.repository;

import com.zaxxer.hikari.HikariDataSource;

public interface DataBaseRepository {

    void test(String url, String username, String password);

    HikariDataSource get(String id);

    HikariDataSource add(String id, String url, String username, String password);

    void remove(String id);

    void removeAll();
}
