package cn.fish.initDB.repository.impl;

import cn.fish.initDB.entity.ChartSession;
import cn.fish.initDB.repository.ChartSessionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Repository;

@Repository
public class ChartSessionRepositoryImpl implements ChartSessionRepository {


    private static final Cache<String, ChartSession> CHART_SESSION = Caffeine.newBuilder()
                                                                             .maximumSize(128) // 最大支持128个会话
                                                                             .build();
    @Override
    public void add(ChartSession chartSession) {
        CHART_SESSION.put(chartSession.getSessionId(), chartSession);
    }

    @Override
    public void remove(ChartSession chartSession) {
        CHART_SESSION.invalidate(chartSession.getSessionId());
    }
}
