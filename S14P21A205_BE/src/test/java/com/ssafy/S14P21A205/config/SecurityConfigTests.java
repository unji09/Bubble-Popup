package com.ssafy.S14P21A205.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.S14P21A205.action.service.ActionService;
import com.ssafy.S14P21A205.action.repository.ActionRepository;
import com.ssafy.S14P21A205.auth.dto.AuthTokenResponse;
import com.ssafy.S14P21A205.auth.service.AuthService;
import com.ssafy.S14P21A205.auth.service.JwtTokenService;
import com.ssafy.S14P21A205.game.day.dto.GameDayStartResponse;
import com.ssafy.S14P21A205.game.day.policy.CaptureRatePolicy;
import com.ssafy.S14P21A205.game.day.policy.PopulationPolicy;
import com.ssafy.S14P21A205.game.day.policy.RentPolicy;
import com.ssafy.S14P21A205.game.day.resolver.EnvironmentScheduleResolver;
import com.ssafy.S14P21A205.game.day.resolver.EventEffectResolver;
import com.ssafy.S14P21A205.game.day.resolver.EventScheduleResolver;
import com.ssafy.S14P21A205.game.day.resolver.NewsRankingResolver;
import com.ssafy.S14P21A205.game.day.resolver.TrafficDelayResolver;
import com.ssafy.S14P21A205.game.day.scheduler.SeasonDayClosingScheduler;
import com.ssafy.S14P21A205.game.day.service.GameDayReportService;
import com.ssafy.S14P21A205.game.day.service.GameDayStartService;
import com.ssafy.S14P21A205.game.day.service.GameDayStateService;
import com.ssafy.S14P21A205.game.day.service.SeasonDayClosingService;
import com.ssafy.S14P21A205.game.day.state.repository.GameDayStoreStateRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.SeasonWeatherRedisRepository;
import com.ssafy.S14P21A205.game.environment.repository.WeatherRepository;
import com.ssafy.S14P21A205.game.news.repository.NewsReportRepository;
import com.ssafy.S14P21A205.game.news.repository.NewsArticleRepository;
import com.ssafy.S14P21A205.game.news.service.AiNewsGenerator;
import com.ssafy.S14P21A205.game.news.service.NewsDataSaver;
import com.ssafy.S14P21A205.game.news.service.NewsService;
import com.ssafy.S14P21A205.game.news.service.SparkNewsDataService;
import com.ssafy.S14P21A205.game.season.dto.GameWaitingResponse;
import com.ssafy.S14P21A205.game.season.dto.GameWaitingStatus;
import com.ssafy.S14P21A205.game.season.dto.ParticipationResponse;
import com.ssafy.S14P21A205.game.season.dto.SeasonJoinResponse;
import com.ssafy.S14P21A205.game.season.repository.DailyReportRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRecordRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRankingRedisRepository;
import com.ssafy.S14P21A205.game.season.repository.SeasonRepository;
import com.ssafy.S14P21A205.game.season.service.ParticipationService;
import com.ssafy.S14P21A205.game.season.scheduler.SeasonStartScheduler;
import com.ssafy.S14P21A205.game.season.service.SeasonFinalRankingService;
import com.ssafy.S14P21A205.game.season.service.SeasonJoinService;
import com.ssafy.S14P21A205.game.season.service.SeasonLifecycleService;
import com.ssafy.S14P21A205.game.season.service.SeasonRankingService;
import com.ssafy.S14P21A205.game.season.service.SeasonSummaryService;
import com.ssafy.S14P21A205.game.season.service.SeasonWaitingService;
import com.ssafy.S14P21A205.order.service.OrderService;
import com.ssafy.S14P21A205.shop.repository.ItemUserRepository;
import com.ssafy.S14P21A205.shop.service.ShopService;
import com.ssafy.S14P21A205.store.repository.StoreRepository;
import com.ssafy.S14P21A205.user.entity.User;
import com.ssafy.S14P21A205.store.service.StoreService;
import com.ssafy.S14P21A205.user.service.UserService;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "app.jwt.secret=test-jwt-secret-key-that-is-long-enough-1234"
})
@AutoConfigureMockMvc
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ActionService actionService;

    @MockitoBean
    private ActionRepository actionRepository;

    @MockitoBean
    private GameDayStartService gameDayStartService;

    @MockitoBean
    private RentPolicy rentPolicy;

    @MockitoBean
    private PopulationPolicy populationPolicy;

    @MockitoBean
    private CaptureRatePolicy captureRatePolicy;

    @MockitoBean
    private EventScheduleResolver eventScheduleResolver;

    @MockitoBean
    private EventEffectResolver eventEffectResolver;

    @MockitoBean
    private EnvironmentScheduleResolver environmentScheduleResolver;

    @MockitoBean
    private NewsRankingResolver newsRankingResolver;

    @MockitoBean
    private TrafficDelayResolver trafficDelayResolver;

    @MockitoBean
    private GameDayStateService gameDayStateService;

    @MockitoBean
    private GameDayReportService gameDayReportService;

    @MockitoBean
    private ShopService shopService;

    @MockitoBean
    private StoreService storeService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private SeasonRepository seasonRepository;

    @MockitoBean
    private StoreRepository storeRepository;

    @MockitoBean
    private DailyReportRepository dailyReportRepository;

    @MockitoBean
    private SeasonRankingRecordRepository seasonRankingRecordRepository;

    @MockitoBean
    private GameDayStoreStateRedisRepository gameDayStoreStateRedisRepository;

    @MockitoBean
    private SeasonRankingRedisRepository seasonRankingRedisRepository;

    @MockitoBean
    private SeasonRankingService seasonRankingService;

    @MockitoBean
    private SeasonSummaryService seasonSummaryService;

    @MockitoBean
    private SeasonJoinService seasonJoinService;

    @MockitoBean
    private ParticipationService participationService;

    @MockitoBean
    private SeasonWaitingService seasonWaitingService;

    @MockitoBean
    private SeasonLifecycleService seasonLifecycleService;

    @MockitoBean
    private SeasonStartScheduler seasonStartScheduler;

    @MockitoBean
    private SeasonDayClosingScheduler seasonDayClosingScheduler;

    @MockitoBean
    private SeasonDayClosingService seasonDayClosingService;

    @MockitoBean
    private SeasonFinalRankingService seasonFinalRankingService;

    @MockitoBean
    private ItemUserRepository itemUserRepository;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private WeatherRepository weatherRepository;

    @MockitoBean
    private SeasonWeatherRedisRepository seasonWeatherRedisRepository;

    @MockitoBean
    private NewsReportRepository newsReportRepository;

    @MockitoBean
    private NewsArticleRepository newsArticleRepository;

    @MockitoBean
    private NewsService newsService;

    @MockitoBean
    private NewsDataSaver newsDataSaver;

    @MockitoBean
    private SparkNewsDataService sparkNewsDataService;

    @MockitoBean
    private AiNewsGenerator aiNewsGenerator;

    @Test
    void startDayAllowsAuthenticatedRequest() throws Exception {
        when(gameDayStartService.startDay(any()))
                .thenReturn(new GameDayStartResponse(
                        "10:00",
                        "22:00",
                        Map.of(),
                        "SUNNY",
                        BigDecimal.ONE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO,
                        List.of(),
                        10_000_000,
                        100,
                        null,
                        null
                ));

        mockMvc.perform(get("/game/day/start")
                        .with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk());
    }

    @Test
    void startDayRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/game/day/start"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserAllowsAuthenticatedRequest() throws Exception {
        when(userService.getUser(any())).thenReturn(new User("test@example.com", "tester"));

        mockMvc.perform(get("/users")
                        .with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk());
    }

    @Test
    void waitingStatusAllowsUnauthenticatedRequest() throws Exception {
        when(seasonWaitingService.getWaitingStatus())
                .thenReturn(new GameWaitingResponse(GameWaitingStatus.WAITING, 4, null, 300));

        mockMvc.perform(get("/game/waiting"))
                .andExpect(status().isOk());
    }

    @Test
    void currentParticipationAllowsAuthenticatedRequest() throws Exception {
        when(participationService.getCurrentParticipation(1))
                .thenReturn(new ParticipationResponse(true, true, 15L, "cookie store", 3));

        mockMvc.perform(get("/game/seasons/current/participation")
                        .with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk());
    }

    @Test
    void currentParticipationRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/game/seasons/current/participation"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authLoginAllowsUnauthenticatedRequest() throws Exception {
        when(authService.startLogin(any(), any(), any())).thenReturn(URI.create("/oauth2/authorization/google"));

        mockMvc.perform(get("/auth/login"))
                .andExpect(status().isFound());
    }

    @Test
    void authRefreshAllowsUnauthenticatedRequest() throws Exception {
        when(authService.refresh(anyString()))
                .thenReturn(new AuthTokenResponse("access-token", "refresh-token", "Bearer", 3600, 86400));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void authLogoutAllowsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingEndpointReturnsCommonNotFoundResponse() throws Exception {
        mockMvc.perform(get("/missing-endpoint")
                        .with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COMMON-003"))
                .andExpect(jsonPath("$.path").value("/missing-endpoint"));
    }

    @Test
    void joinCurrentSeasonAllowsAuthenticatedRequest() throws Exception {
        when(seasonJoinService.joinCurrentSeason(any(), any()))
                .thenReturn(new SeasonJoinResponse(156L, "cookie store", 7_000_000));

        mockMvc.perform(post("/game/seasons/current/join")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":3,\"storeName\":\"cookie store\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void joinCurrentSeasonRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post("/game/seasons/current/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locationId\":3,\"storeName\":\"cookie store\"}"))
                .andExpect(status().isUnauthorized());
    }
}
