package com.ssafy.S14P21A205.game.news.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.S14P21A205.game.news.dto.MenuMentionCount;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AiNewsGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiNewsGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> STYLES = List.of(
            "현장 르포: 오감 묘사로 현장감 있게",
            "인터뷰: 관계자 2~3명 코멘트 활용",
            "분석: 차분하고 권위 있는 해설 톤",
            "속보: 짧고 긴박한 문장 위주"
    );

    private static final String SYSTEM_PROMPT =
            "너는 '버블팝업' 팝업스토어 음식게임 전문 기자다. "
            + "게임 이름은 반드시 '버블팝업'이다. 다른 이름을 만들어내지 마. "
            + "출력: {\"title\":\"제목\",\"content\":\"본문\"} JSON만. 다른 텍스트 금지. "
            + "제목은 핵심 정보를 담아 독자가 제목만 읽어도 기사 내용을 파악할 수 있게 써. "
            + "예: 지역명+현상, 메뉴명+변동, 매장명+성과 등 구체적 사실 포함. "
            + "제목 50자 이내. 본문 150~250자 이내로 짧게. "
            + "순수 한국어만 사용(영어·한자·아랍어·외국어 절대 금지). "
            + "숫자 직접 쓰지 말고 간접 표현. 괄호·이모지·따옴표 금지. "
            + "마크다운 금지(**, *, #, ```, - 등 절대 쓰지 마). "
            + "백슬래시 사용 금지. 줄바꿈 넣지 마. "
            + "짧은 문장 위주, 자연스럽고 읽기 쉬운 한국어로 써. "
            + "가상의 인물명·매장명·브랜드명을 만들어내지 마.";

    private final RestClient restClient;
    private final String model;

    public AiNewsGenerator(
            @Value("${GMS_BASE_URL:https://gms.ssafy.io/gmsapi}") String baseUrl,
            @Value("${GMS_KEY:}") String apiKey,
            @Value("${GMS_MODEL:gpt-4.1-nano}") String model) {
        this.model = model;
        log.info("GMS AI base-url={}, model={}", baseUrl, model);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private String getRandomStyle() {
        return STYLES.get(ThreadLocalRandom.current().nextInt(STYLES.size()));
    }

    // ---- Trend News ----

    public NewsGenerationResult generateTrendNews(long seasonId, int day, List<MenuMentionCount> rankedMenus) {
        String style = getRandomStyle();
        String rankingText = IntStream.range(0, rankedMenus.size())
                .mapToObj(i -> (i + 1) + "위 " + rankedMenus.get(i).menuName())
                .collect(Collectors.joining(", "));

        String prompt = "인기메뉴: %s. 순위 직접 언급 말고 인기도 간접 표현. 상위 메뉴 모두 언급. 제목에 인기 메뉴명과 트렌드 포함. 스타일: %s"
                .formatted(rankingText, style);
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI trend news failed day {}", day, e);
            return fallbackTrendNews(day, rankedMenus);
        }
    }

    private NewsGenerationResult fallbackTrendNews(int day, List<MenuMentionCount> rankedMenus) {
        String topMenu = rankedMenus.isEmpty() ? "음식" : rankedMenus.get(0).menuName();
        return new NewsGenerationResult(
                "거리에서 감지된 새로운 미식 트렌드",
                "최근 버블팝업 상권 곳곳에서 " + topMenu + "을(를) 찾는 발걸음이 부쩍 늘었다는 소식이 들려오고 있다. "
                + "업계 관계자들은 \"SNS에서의 반응이 심상치 않다\"며 주목하고 있으며, "
                + "일부 점주들은 이미 관련 메뉴 도입을 검토 중인 것으로 알려졌다.");
    }

    // ---- Menu Entry News ----

    public NewsGenerationResult generateMenuEntryNews(long seasonId, int day, List<Map<String, Object>> ranking) {
        String style = getRandomStyle();
        String rankingText = ranking.stream()
                .map(item -> item.get("name") + " " + item.get("storeCount") + "개")
                .collect(Collectors.joining(", "));

        String prompt = "메뉴별 매장수: %s. 많은 메뉴는 원재료비 상승, 적은 메뉴는 틈새 기회. 상위·하위 대비. 제목에 1위 메뉴명과 매장 동향 포함. 스타일: %s"
                .formatted(rankingText, style);
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI menu entry news failed day {}", day, e);
            String topMenu = ranking.isEmpty() ? "음식" : (String) ranking.get(0).get("name");
            return new NewsGenerationResult(
                    topMenu + " 원재료 수급에 빨간불?",
                    "최근 " + topMenu + " 관련 매장이 빠르게 늘어나면서 도매시장에서는 원재료 수급에 대한 우려의 목소리가 나오고 있다. "
                    + "한 유통업계 관계자는 \"주문량이 갑자기 늘었다\"며 가격 인상 가능성을 시사했다.");
        }
    }

    // ---- Area Entry News ----

    public NewsGenerationResult generateAreaEntryNews(long seasonId, int day, List<Map<String, Object>> ranking) {
        String style = getRandomStyle();
        String rankingText = ranking.stream()
                .map(item -> item.get("name") + " " + item.get("storeCount") + "개")
                .collect(Collectors.joining(", "));

        String prompt = "지역별 매장수: %s. 밀집 지역은 임대료 폭등, 한산한 지역은 안정. 상위·하위 대비. 제목에 1위 지역명과 입점 현황 포함. 스타일: %s"
                .formatted(rankingText, style);
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI area entry news failed day {}", day, e);
            String topArea = ranking.isEmpty() ? "지역" : (String) ranking.get(0).get("name");
            return new NewsGenerationResult(
                    topArea + " 상권, 과열 조짐 감지",
                    topArea + " 일대에 팝업 매장이 빠르게 늘어나면서 상가 임대 시장이 들썩이고 있다. "
                    + "인근 중개업소 관계자는 \"문의가 하루에도 수십 건\"이라며 임대료 인상 가능성을 시사했다.");
        }
    }

    // ---- Event Preview News ----

    public NewsGenerationResult generateEventPreviewNews(long seasonId, int day, List<Map<String, Object>> eventData) {
        String style = getRandomStyle();
        String festivalName = (String) eventData.get(0).get("festivalName");
        String locationName = (String) eventData.get(0).get("locationName");

        String prompt;
        if (locationName != null && !locationName.isEmpty()) {
            prompt = "내일 %s에서 '%s' 축제. 축제지역·명칭 언급, 유동인구·매출 기대감. 스타일: %s"
                    .formatted(locationName, festivalName, style);
        } else {
            prompt = "내일 '%s' 축제. 축제명 언급, 유동인구·매출 기대감. 스타일: %s"
                    .formatted(festivalName, style);
        }
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI event preview news failed day {}", day, e);
            return new NewsGenerationResult(
                    "'" + festivalName + "' 개최 소식에 상권 들썩",
                    "내일 '" + festivalName + "' 행사가 열린다는 소식이 전해지면서 상권가에 기대감이 퍼지고 있다. "
                    + "발 빠른 점주들은 이미 손님맞이 준비에 나선 것으로 보인다.");
        }
    }

    // ---- Top Store News ----

    public NewsGenerationResult generateTopStoreNews(long seasonId, int day, String storeName, String menuName,
            int revenue, int salesCount) {
        String style = getRandomStyle();
        String prompt = "매출1위: %s(%s), 매출%d원, %d개. 사장님 인터뷰 포함. 제목에 매장명과 매출 1위 사실 포함. 스타일: %s"
                .formatted(storeName, menuName, revenue, salesCount, style);
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI top store news failed day {}", day, e);
            return new NewsGenerationResult(
                    "'" + storeName + "' 앞 긴 줄… 무슨 일이?",
                    "오늘 " + storeName + " 앞에는 아침부터 긴 줄이 늘어섰다. " + menuName + "을(를) 맛보려는 손님들로 "
                    + "매장은 문전성시를 이뤘고, 인근 점주들 사이에서도 화제가 됐다.");
        }
    }

    // ---- Cumulative Sales News ----

    public NewsGenerationResult generateCumulativeSalesNews(long seasonId, int day, String storeName, String menuName,
            long totalSales, int milestone) {
        String style = getRandomStyle();
        String prompt = "%s 매장 %s 누적%d개(마일스톤:%d개). 단골 코멘트 포함. 제목에 매장명과 판매 마일스톤 포함. 스타일: %s"
                .formatted(storeName, menuName, totalSales, milestone, style);
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI cumulative sales news failed day {}", day, e);
            return new NewsGenerationResult(
                    "'" + storeName + "', 놀라운 판매 기록 달성",
                    storeName + "이(가) 조용히 놀라운 기록을 세웠다. " + menuName + " 누적 판매량이 업계에서도 보기 드문 수준에 도달한 것이다. "
                    + "같은 메뉴를 취급하는 인근 매장들도 이 소식에 촉각을 곤두세우고 있다.");
        }
    }

    // ---- Migration News ----

    public NewsGenerationResult generateMigrationNews(long seasonId, int day, List<Map<String, Object>> changes) {
        String style = getRandomStyle();
        String changesText = changes.stream()
                .map(item -> {
                    long change = ((Number) item.get("change")).longValue();
                    return item.get("name") + (change > 0 ? "+" : "") + change;
                })
                .collect(Collectors.joining(", "));

        String prompt = "지역별 매장이동: %s(+유입,-유출). 유입=경쟁심화, 유출=기회. 제목에 이동이 큰 지역명과 변화 방향 포함. 스타일: %s"
                .formatted(changesText, style);
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI migration news failed day {}", day, e);
            return new NewsGenerationResult(
                    "팝업 매장 대이동, 상권 판도 바뀌나",
                    "버블팝업 상권에 지각변동이 일어나고 있다. 일부 지역에서는 팝업 매장이 빠르게 빠져나가는 반면, "
                    + "특정 지역으로 점주들이 대거 몰리는 양상이 포착됐다.");
        }
    }

    // ---- Day 1 Guide News ----

    public NewsGenerationResult generateIntroNews(long seasonId) {
        String style = getRandomStyle();
        String prompt = "버블팝업 팝업스토어 주간 개막. 홍대·강남·성수·여의도·잠실·이태원·명동·건대입구 8곳 상권. 경쟁과 설렘 분위기. 스타일: %s"
                .formatted(style);
        try {
            return callAi(prompt);
        } catch (Exception e) {
            log.error("AI intro news failed for season {}", seasonId, e);
            return new NewsGenerationResult(
                    "서울 팝업 주간 개막, 버블팝업 축제의 막이 오르다",
                    "서울 전역에 팝업스토어 열풍이 불고 있다. 홍대, 강남, 성수, 여의도 등 주요 상권에 야심 찬 점주들이 속속 모여들며 저마다의 개성을 담은 매장을 준비하고 있다. "
                    + "업계 관계자는 \"올 시즌은 그 어느 때보다 치열한 경쟁이 예상된다\"며 \"트렌드를 읽고 발 빠르게 대응하는 점주가 살아남을 것\"이라고 전망했다. "
                    + "거리마다 풍기는 다양한 음식 냄새와 설렘 가득한 분위기 속에서, 과연 누가 이번 시즌의 주인공이 될지 귀추가 주목된다.");
        }
    }

    private static final List<TipTemplate> TIP_TEMPLATES = List.of(
            new TipTemplate(
                    "선배→신규 조언톤. 지역마다 임대료·유동인구 다르니 여러곳 둘러보라. 번화가는 경쟁치열, 한적한곳은 여유. 스타일: %s",
                    "선배 점주의 한마디, 발품을 팔아보는 건 어떨까요",
                    "한 선배 점주가 이제 막 문을 연 신규 점주들에게 따뜻한 조언을 건넸다. \"지역마다 분위기가 정말 다르더라고요. "
                    + "번화한 곳은 손님이 많은 대신 경쟁도 만만치 않고, 조용한 곳은 나름의 매력이 있어요. "
                    + "매장을 열기 전에 여러 곳을 둘러보면서 자기 스타일에 맞는 동네를 찾아보는 건 어떨까요?\""
            ),
            new TipTemplate(
                    "관계자→신규 조언톤. 매일 뉴스에 인기메뉴·활기지역 정보 있으니 챙겨보면 도움. 스타일: %s",
                    "오늘의 뉴스, 한번 챙겨 보시는 건 어떨까요",
                    "한 관계자가 신규 점주들에게 귀띔했다. \"매일 나오는 뉴스에 요즘 어떤 메뉴가 뜨고 있는지, 어느 동네가 활기찬지 정보가 담겨 있어요. "
                    + "바쁘시겠지만 시간 날 때 한번 훑어보시면 흐름을 읽는 데 도움이 될 수도 있습니다. "
                    + "지난 시즌에 잘된 점주들도 뉴스를 꼼꼼히 챙겨 봤다는 이야기가 있더라고요.\""
            ),
            new TipTemplate(
                    "선배→신규 조언톤. 할인 걸면 손님 유인 가능. 너무 깎으면 손해니 적당히. 스타일: %s",
                    "할인을 한번 걸어보는 건 어떨까요",
                    "한 선배 점주가 넌지시 조언했다. \"손님이 뜸할 때 할인을 한번 걸어보는 것도 방법이에요. "
                    + "가격이 내려가면 지나가던 분들도 한번쯤 들러보거든요. "
                    + "다만 너무 많이 깎으면 남는 게 없으니, 적당한 선에서 조절해 보시는 게 좋을 것 같아요.\""
            ),
            new TipTemplate(
                    "선배→신규 조언톤. 홍보하면 매장 알리기 좋다. 방법마다 비용·효과 다르니 상황맞게. 스타일: %s",
                    "매장을 알리고 싶다면 홍보를 해보시는 건 어떨까요",
                    "한 선배 점주가 경험을 나눴다. \"처음엔 손님이 매장을 모르니까 홍보를 해보는 것도 나쁘지 않아요. "
                    + "여러 가지 방법이 있는데 각각 느낌이 다르더라고요. "
                    + "자기 상황에 맞는 걸 골라서 한번 시도해 보시면 어떨까요?\""
            ),
            new TipTemplate(
                    "선배→신규 조언톤. 재고 떨어지면 긴급발주 가능. 비용 더 들지만 손님 몰릴때 유용. 스타일: %s",
                    "재고가 떨어졌을 때, 긴급 발주라는 방법도 있어요",
                    "한 선배 점주가 귀띔했다. \"영업 중에 재고가 바닥나면 당황스럽잖아요. 그럴 때 긴급 발주라는 게 있어요. "
                    + "비용이 좀 더 들긴 하지만, 예상 밖으로 손님이 몰릴 때 알아두면 쓸모가 있더라고요. "
                    + "이런 방법도 있다는 걸 미리 알아두시면 좋을 것 같아요.\""
            ),
            new TipTemplate(
                    "선배→신규 조언톤. 나눔하면 수익 줄지만 평판 오름. 장기적으로 손님 늘어남. 스타일: %s",
                    "나눔 활동, 관심 있으시면 한번 해보시는 건 어떨까요",
                    "한 선배 점주가 경험담을 전했다. \"나눔 활동을 하면 당장은 좀 아깝게 느껴질 수 있는데, 매장 평판이 올라가더라고요. "
                    + "평판이 좋아지니까 찾아오는 분들이 조금씩 늘었어요. "
                    + "관심이 있으시면 한번 해보시는 것도 괜찮을 것 같아요.\""
            ),
            // ---- 이벤트 가이드 팁 ----
            new TipTemplate(
                    "상권분석가 인터뷰톤. 가끔 예상치 못한 호황이 찾아올 수 있다. 평소에 준비해두면 기회를 잡을 수 있다. 효과 수치 언급 금지. 스타일: %s",
                    "예상 밖의 호황, 준비된 자에게 기회가 온다",
                    "상권분석가 박 연구원은 \"가끔 예측하기 어려운 호재가 상권에 찾아온다\"고 전했다. "
                    + "\"그럴 때 준비가 안 된 매장은 기회를 그냥 흘려보내게 됩니다. "
                    + "평소에 여유를 갖고 운영하는 습관이 중요합니다.\""
            ),
            new TipTemplate(
                    "속보톤. 예기치 못한 악재가 닥칠 수 있다. 당황하지 말고 침착하게 대응하라. 구체적 효과 언급 금지. 스타일: %s",
                    "갑작스러운 악재, 침착함이 답이다",
                    "긴급 소식이다. 운영 중 예상치 못한 악재가 찾아올 수 있다. "
                    + "전문가들은 \"이런 상황에서 가장 중요한 건 당황하지 않는 것\"이라고 입을 모은다. "
                    + "어려운 날이 지나면 다시 기회가 오기 마련이다."
            ),
            new TipTemplate(
                    "분석톤. 시장 상황이 수시로 변할 수 있다. 유연한 대응이 중요. 구체적 효과 언급 금지. 스타일: %s",
                    "변화하는 시장, 유연한 대응이 관건",
                    "시장 환경은 늘 같지 않다. "
                    + "어떤 날은 운영에 유리한 조건이 생기기도 하고, 반대로 부담이 커지는 날도 있다. "
                    + "중요한 것은 상황에 맞춰 유연하게 전략을 조정하는 자세다. "
                    + "뉴스를 꾸준히 확인하면 흐름을 읽는 데 도움이 된다."
            ),
            new TipTemplate(
                    "현장르포톤. 특정 지역에서 행사가 열리면 분위기가 달라진다. 관심을 갖고 지켜보라. 구체적 효과 언급 금지. 스타일: %s",
                    "거리의 분위기가 달라질 때가 있다",
                    "가끔 특정 지역의 거리에서 평소와 다른 활기가 느껴질 때가 있다. "
                    + "지역 행사나 축제가 열리면 주변 분위기가 확 바뀌기도 한다. "
                    + "한 점주는 \"이런 날은 평소와 느낌이 다르다\"며 미소를 지었다. "
                    + "뉴스에서 행사 소식이 들리면 관심을 가져볼 만하다."
            ),
            new TipTemplate(
                    "선배→신규 조언톤. 가끔 좋은 일이 생기기도 한다. 기회를 놓치지 말라. 구체적 효과 언급 금지. 스타일: %s",
                    "가끔 찾아오는 좋은 소식, 놓치지 마세요",
                    "한 선배 점주가 웃으며 말했다. \"장사하다 보면 가끔 예상치 못한 좋은 일이 생기기도 해요. "
                    + "그럴 때 미리 준비가 되어 있으면 훨씬 잘 활용할 수 있거든요. "
                    + "평소에 여유를 두고 운영하는 습관이 이런 순간에 빛을 발하더라고요.\""
            ),
            new TipTemplate(
                    "전문가 인터뷰톤. 어려운 시기가 올 수 있다. 무리하지 말고 버텨라. 구체적 효과 언급 금지. 스타일: %s",
                    "어려운 시기를 현명하게 넘기는 법",
                    "식품경영 전문가 이 교수는 \"운영하다 보면 손님이 뚝 끊기는 시기가 올 수 있다\"고 조언했다. "
                    + "\"이럴 때 무리해서 투자하면 오히려 상황이 나빠집니다. "
                    + "잠시 숨을 고르면서 다음 기회를 준비하는 것이 현명한 방법입니다.\""
            )
    );

    private record TipTemplate(String promptTemplate, String fallbackTitle, String fallbackContent) {}

    /**
     * 6개 팁 후보 중 랜덤 2개를 뽑아 AI로 뉴스 생성.
     */
    public List<NewsGenerationResult> generateRandomTipNews(long seasonId) {
        List<TipTemplate> shuffled = new java.util.ArrayList<>(TIP_TEMPLATES);
        Collections.shuffle(shuffled);
        List<TipTemplate> picked = shuffled.subList(0, 2);

        List<NewsGenerationResult> results = new java.util.ArrayList<>();
        for (TipTemplate tip : picked) {
            String style = getRandomStyle();
            String prompt = tip.promptTemplate().formatted(style);
            try {
                results.add(callAi(prompt));
            } catch (Exception e) {
                log.error("AI tip news failed for season {}", seasonId, e);
                results.add(new NewsGenerationResult(tip.fallbackTitle(), tip.fallbackContent()));
            }
        }
        return results;
    }

    // ---- Common AI call ----

    private NewsGenerationResult callAi(String promptText) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 350);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", promptText)));

        long startTime = System.currentTimeMillis();

        String responseJson = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        long elapsed = System.currentTimeMillis() - startTime;

        JsonNode root = MAPPER.readTree(responseJson);
        String text = root.get("choices").get(0).get("message").get("content").asText();

        JsonNode usage = root.get("usage");
        if (usage != null) {
            int promptTokens = usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0;
            int completionTokens = usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0;
            int totalTokens = promptTokens + completionTokens;
            log.info("[AI] model={} | {}ms | in={} out={} total={} | speed={} tok/s",
                    model, elapsed, promptTokens, completionTokens, totalTokens,
                    completionTokens > 0 ? String.format("%.1f", completionTokens * 1000.0 / elapsed) : "N/A");
        } else {
            log.info("[AI] model={} | {}ms", model, elapsed);
        }

        return parseResponse(text);
    }

    private NewsGenerationResult parseResponse(String text) {
        String jsonStr = text.trim();

        // ```json ... ``` 블록 제거
        if (jsonStr.contains("```")) {
            jsonStr = jsonStr.replaceAll("(?s)```json?\\s*", "").replaceAll("(?s)```", "").trim();
        }

        // 1단계: 중첩 브레이스 카운팅으로 첫 번째 완전한 JSON 객체 추출
        int braceStart = jsonStr.indexOf('{');
        if (braceStart >= 0) {
            int depth = 0;
            int braceEnd = -1;
            boolean inString = false;
            boolean escaped = false;
            for (int i = braceStart; i < jsonStr.length(); i++) {
                char c = jsonStr.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                if (inString) continue;
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        braceEnd = i;
                        break;
                    }
                }
            }
            if (braceEnd > braceStart) {
                jsonStr = jsonStr.substring(braceStart, braceEnd + 1);
            }
        }

        // 2단계: 키 누락/빈 키 패턴 복원 (추출된 JSON에 적용)
        // {"":"val1", "":"val2"} → {"title":"val1","content":"val2"}
        if (jsonStr.matches("(?s)\\{\\s*\"\"\\s*:.*")) {
            jsonStr = jsonStr.replaceFirst("\\{\\s*\"\"\\s*:", "{\"title\":");
            jsonStr = jsonStr.replaceFirst(",\\s*\"\"\\s*:", ",\"content\":");
        }
        // {:"val1", :"val2"} → {"title":"val1","content":"val2"}
        else if (jsonStr.matches("(?s)\\{\\s*:.*")) {
            jsonStr = jsonStr.replaceFirst("\\{\\s*:", "{\"title\":");
            jsonStr = jsonStr.replaceFirst(",\\s*:", ",\"content\":");
        }

        // 3단계: 이중 따옴표 키 정리: ""title"" → "title", ""content"" → "content"
        jsonStr = jsonStr.replace("\"\"title\"\"", "\"title\"");
        jsonStr = jsonStr.replace("\"\"content\"\"", "\"content\"");
        // 값 부분의 이중 따옴표: , ""val → , "val / : ""val → : "val
        jsonStr = jsonStr.replaceAll(":\\s*\"\"", ":\"");
        jsonStr = jsonStr.replaceAll("\"\"\\s*}", "\"}");
        jsonStr = jsonStr.replaceAll("\"\"\\s*,", "\",");

        // 1차 파싱
        NewsGenerationResult result = tryParseJson(jsonStr);
        if (result != null) return result;

        // 이스케이프 문자 정리 후 재시도
        String cleaned = jsonStr.replace("\\n", " ").replace("\\\"", "\"").replace("\\", "");
        result = tryParseJson(cleaned);
        if (result != null) return result;

        // 정규식으로 title/content 추출 시도 (원본 텍스트 대상)
        result = tryRegexExtract(text);
        if (result != null) return result;

        // JSON 파싱 실패 시: 첫 문장을 제목, 나머지를 본문으로 분리
        log.warn("JSON parse failed, splitting raw text (first 100 chars): {}",
                text.length() > 100 ? text.substring(0, 100) + "..." : text);
        String plain = text.replaceAll("[\\r\\n]+", " ").trim();
        // JSON 잔해 제거
        plain = plain.replaceAll("\\{\\s*:?\\s*\"?", "").replaceAll("\"?\\s*}", "")
                .replaceAll("\"\\s*,\\s*:?\\s*\"?", " ").trim();
        // 첫 마침표/느낌표/물음표 기준으로 제목·본문 분리
        int splitIdx = -1;
        for (int i = 0; i < Math.min(plain.length(), 120); i++) {
            char c = plain.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '。') {
                splitIdx = i;
                break;
            }
        }
        String title;
        String content;
        if (splitIdx > 0 && splitIdx < plain.length() - 1) {
            title = plain.substring(0, splitIdx + 1).trim();
            content = plain.substring(splitIdx + 1).trim();
        } else {
            title = plain;
            content = plain;
        }
        return new NewsGenerationResult(sanitize(title), sanitize(content));
    }

    private NewsGenerationResult tryParseJson(String jsonStr) {
        try {
            JsonNode node = MAPPER.readTree(jsonStr);
            if (node.has("title") && node.has("content")) {
                String title = sanitize(node.get("title").asText().strip());
                String content = sanitize(node.get("content").asText().strip());
                // 본문이 너무 길면 마지막 완전한 문장에서 자르기
                if (content.length() > 300) {
                    content = truncateAtSentence(content, 300);
                }
                return new NewsGenerationResult(title, content);
            }
        } catch (Exception e) {
            log.warn("JSON parse failed: {}", e.getMessage());
        }
        return null;
    }

    /** 본문을 maxLen 이내에서 마지막 완전한 문장 기준으로 자르기 */
    private String truncateAtSentence(String text, int maxLen) {
        String sub = text.substring(0, maxLen);
        int lastEnd = -1;
        for (int i = sub.length() - 1; i >= 0; i--) {
            char c = sub.charAt(i);
            if (c == '.' || c == '다' || c == '!' || c == '?') {
                lastEnd = i;
                break;
            }
        }
        if (lastEnd > maxLen / 2) {
            return sub.substring(0, lastEnd + 1).strip();
        }
        return sub.strip();
    }

    /** 정규식으로 title/content 추출 시도 */
    private NewsGenerationResult tryRegexExtract(String text) {
        // "title" 또는 "제목" 키 뒤의 값 추출
        java.util.regex.Matcher titleMatcher = java.util.regex.Pattern
                .compile("(?:\"title\"|\"제목\")\\s*:\\s*\"([^\"]+)\"")
                .matcher(text);
        java.util.regex.Matcher contentMatcher = java.util.regex.Pattern
                .compile("(?:\"content\"|\"본문\")\\s*:\\s*\"([^\"]{50,})\"")
                .matcher(text);
        if (titleMatcher.find() && contentMatcher.find()) {
            return new NewsGenerationResult(
                    sanitize(titleMatcher.group(1)),
                    sanitize(contentMatcher.group(1)));
        }
        return null;
    }

    /** 영어·중국어·특수문자·마크다운·이모지 제거 후처리 */
    private String sanitize(String text) {
        // 마크다운 서식 제거
        text = text.replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1");
        text = text.replaceAll("(?m)^#{1,6}\\s*", "");
        text = text.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        text = text.replaceAll("`{1,3}([^`]*)`{1,3}", "$1");
        // 이모지 제거 (서로게이트 페어 포함)
        text = text.replaceAll("[\\x{1F000}-\\x{1FFFF}]", "");
        text = text.replaceAll("[\\x{2600}-\\x{27BF}]", "");
        text = text.replaceAll("[\\x{FE00}-\\x{FEFF}]", "");
        text = text.replaceAll("[\\x{E0020}-\\x{E007F}]", "");
        // 괄호로 감싼 이모지+텍스트 패턴 제거: (🥤 젤리의 달콤함이) → 젤리의 달콤함이
        text = text.replaceAll("\\(\\s*[^\\p{IsHangul}]*\\s+([\\p{IsHangul}].*?)\\)", "$1");
        // 영어 단어 제거
        text = text.replaceAll("[a-zA-Z]+", "");
        // 영어 제거 후 남는 빈 따옴표·콜론 정리
        text = text.replaceAll("\"\\s*\"", "").replaceAll(":\\s*:", ":").replaceAll("^[:\\s\"]+", "");
        // 중국어·일본어 문자 제거
        text = text.replaceAll("[\\u4e00-\\u9fff\\u3040-\\u309f\\u30a0-\\u30ff]+", "");
        // 아랍어·키릴 등 비한글·비숫자·비구두점 외국어 제거
        text = text.replaceAll("[\\u0600-\\u06FF\\u0400-\\u04FF]+", "");
        // 백슬래시 이스케이프 정리
        text = text.replace("\\n", " ").replace("\\\"", "\"").replace("\\\\", "");
        // 남은 단독 백슬래시 제거
        text = text.replace("\\", "");
        // 연속 공백 정리
        text = text.replaceAll("\\s{2,}", " ");
        // 빈 따옴표·괄호 정리
        text = text.replace("''", "").replace("\"\"", "").replace("()", "");
        // 앞뒤 특수문자 잔해 정리 (콜론, 따옴표, 쉼표 등)
        text = text.replaceAll("^[:\\s,\"']+", "").replaceAll("[:\\s,\"']+$", "");
        return text.strip();
    }

    public record NewsGenerationResult(String title, String content) {
    }
}
