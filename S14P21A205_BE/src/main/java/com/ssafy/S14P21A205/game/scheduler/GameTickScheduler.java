package com.ssafy.S14P21A205.game.scheduler;

import com.ssafy.S14P21A205.game.runtime.service.GameRuntimeControlStateHolder;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameTickScheduler {

    private static final Logger log = LoggerFactory.getLogger(GameTickScheduler.class);

    private final List<GameTickTask> gameTickTasks;
    private final GameRuntimeControlStateHolder runtimeControlStateHolder;

    /*
    10초마다 runTick 실행
    여기서 스프링이 주입한 틱 작업들의 execute() 호출하는 구조
     */
    @Scheduled(fixedRateString = "${app.game.tick.fixed-rate-ms:10000}")
    public void runTick() {
        if (runtimeControlStateHolder.isPaused()) {
            return;
        }
        for (GameTickTask gameTickTask : gameTickTasks) {
            try {
                gameTickTask.execute();
            } catch (Exception e) {
                log.error("Game tick task failed. taskName={}", gameTickTask.taskName(), e);
            }
        }
    }
}
