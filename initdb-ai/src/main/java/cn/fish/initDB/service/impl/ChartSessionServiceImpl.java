package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChartSession;
import cn.fish.initDB.repository.ChartSessionRepository;
import cn.fish.initDB.service.ChartSessionService;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChartSessionServiceImpl implements ChartSessionService {

    @Autowired
    private ChartSessionRepository chartSessionRepository;

    @Override
    public ChartSession add(ChartSession chartSession) {
        chartSession.setSessionId(IdUtil.simpleUUID());
        chartSessionRepository.add(chartSession);
        return chartSession;
    }
}
