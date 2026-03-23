package cn.fish.initDB.repository;

import cn.fish.initDB.entity.ChartSession;

public interface ChartSessionRepository {

    void add(ChartSession chartSession);

    void remove(ChartSession chartSession);

}
