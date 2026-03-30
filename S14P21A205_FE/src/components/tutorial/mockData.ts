// ─── 지역 ───
export interface MockLocation {
  id: number;
  name: string;
  rent: number;
  interiorCost: number;
  grade: string;
  tags: string[];
  description: string;
}

// API에 없는 보조 데이터 (grade, tags, description)
export const LOCATION_EXTRA: Record<string, { grade: string; tags: string[]; description: string }> = {
  "잠실": { grade: "B+", tags: ["축제", "스포츠"], description: "대형 콘서트와 스포츠 행사가 많은 지역" },
  "신도림": { grade: "C+", tags: ["교통", "직장인"], description: "교통 허브, 직장인 유동인구" },
  "여의도": { grade: "B", tags: ["금융", "벚꽃"], description: "금융 중심지, 벚꽃축제 명소" },
  "이태원": { grade: "A-", tags: ["글로벌", "맛집"], description: "외국인 관광객과 다양한 문화" },
  "서울숲/성수": { grade: "A", tags: ["트렌드", "카페"], description: "MZ세대 핫플레이스" },
  "강남": { grade: "A", tags: ["비즈니스", "쇼핑"], description: "대한민국 대표 상권" },
  "명동": { grade: "S", tags: ["관광", "쇼핑"], description: "외국인 관광 1번지" },
  "홍대": { grade: "B+", tags: ["예술", "클럽"], description: "젊은 문화와 예술의 거리" },
};

// API 실패 시 폴백용
export const MOCK_LOCATIONS: MockLocation[] = [
  { id: 1, name: "잠실", rent: 450000, interiorCost: 315000, grade: "B+", tags: ["축제", "스포츠"], description: "대형 콘서트와 스포츠 행사가 많은 지역" },
  { id: 2, name: "신도림", rent: 250000, interiorCost: 175000, grade: "C+", tags: ["교통", "직장인"], description: "교통 허브, 직장인 유동인구" },
  { id: 3, name: "여의도", rent: 400000, interiorCost: 280000, grade: "B", tags: ["금융", "벚꽃"], description: "금융 중심지, 벚꽃축제 명소" },
  { id: 4, name: "이태원", rent: 500000, interiorCost: 350000, grade: "A-", tags: ["글로벌", "맛집"], description: "외국인 관광객과 다양한 문화" },
  { id: 5, name: "서울숲/성수", rent: 700000, interiorCost: 490000, grade: "A", tags: ["트렌드", "카페"], description: "MZ세대 핫플레이스" },
  { id: 6, name: "강남", rent: 600000, interiorCost: 420000, grade: "A", tags: ["비즈니스", "쇼핑"], description: "대한민국 대표 상권" },
  { id: 7, name: "명동", rent: 800000, interiorCost: 560000, grade: "S", tags: ["관광", "쇼핑"], description: "외국인 관광 1번지" },
  { id: 8, name: "홍대", rent: 350000, interiorCost: 245000, grade: "B+", tags: ["예술", "클럽"], description: "젊은 문화와 예술의 거리" },
];

// ─── 메뉴 ───
export interface MockMenu {
  id: number;
  name: string;
  emoji: string;
  originPrice: number;
  recommendedPrice: number;
  maxSellingPrice: number;
}

// API에 없는 보조 데이터 (emoji)
// 실제 PlayPage MENU_EMOJI_MAP과 동일
export const MENU_EMOJI: Record<string, string> = {
  "빵": "🍞",
  "마라꼬치": "🍢",
  "젤리": "🍬",
  "떡볶이": "🍽️",
  "햄버거": "🍔",
  "아이스크림": "🍨",
  "닭강정": "🍗",
  "타코": "🌮",
  "핫도그": "🌭",
  "버블티": "🧋",
};

// API 실패 시 폴백용
export const MOCK_MENUS: MockMenu[] = [
  { id: 1, name: "빵", emoji: "🍞", originPrice: 1200, recommendedPrice: 2400, maxSellingPrice: 4800 },
  { id: 2, name: "마라꼬치", emoji: "🍢", originPrice: 1800, recommendedPrice: 3600, maxSellingPrice: 7200 },
  { id: 3, name: "젤리", emoji: "🍬", originPrice: 800, recommendedPrice: 1600, maxSellingPrice: 3200 },
  { id: 4, name: "떡볶이", emoji: "🍽️", originPrice: 1500, recommendedPrice: 3000, maxSellingPrice: 6000 },
  { id: 5, name: "햄버거", emoji: "🍔", originPrice: 2500, recommendedPrice: 5000, maxSellingPrice: 10000 },
  { id: 6, name: "아이스크림", emoji: "🍨", originPrice: 900, recommendedPrice: 1800, maxSellingPrice: 3600 },
  { id: 7, name: "닭강정", emoji: "🍗", originPrice: 2200, recommendedPrice: 4400, maxSellingPrice: 8800 },
  { id: 8, name: "타코", emoji: "🌮", originPrice: 2000, recommendedPrice: 4000, maxSellingPrice: 8000 },
  { id: 9, name: "핫도그", emoji: "🌭", originPrice: 1000, recommendedPrice: 2000, maxSellingPrice: 4000 },
  { id: 10, name: "버블티", emoji: "🧋", originPrice: 1300, recommendedPrice: 2600, maxSellingPrice: 5200 },
];

// ─── 아이템 ───
export interface MockItem {
  id: number;
  name: string;
  group: string;
  desc: string;
  discount: string;
  price: number;
}

// API 실패 시 폴백용 — 실제 대시보드와 동일한 형식
export const MOCK_ITEMS: MockItem[] = [
  { id: 1, name: "원재료값 할인권(대)", group: "INGREDIENT", desc: "원재료 구매 비용 20% 감소", discount: "20% 할인", price: 30 },
  { id: 2, name: "임대료 할인권(대)", group: "RENT", desc: "일일 임대료 20% 감소", discount: "20% 할인", price: 30 },
  { id: 3, name: "원재료값 할인권(소)", group: "INGREDIENT", desc: "원재료 구매 비용 5% 감소", discount: "5% 할인", price: 10 },
  { id: 4, name: "임대료 할인권(소)", group: "RENT", desc: "일일 임대료 5% 감소", discount: "5% 할인", price: 10 },
];

// ─── 이벤트 ───
export type EventCategory =
  | "celebrity" | "holiday" | "subsidy"
  | "price_down" | "price_up"
  | "disaster_earthquake" | "disaster_flood" | "disaster_typhoon" | "disaster_fire"
  | "disease" | "policy" | "festival";

export interface MockEvent {
  category: EventCategory;
  name: string;
  /** 인게임 알림에 표시되는 title (name과 다를 경우) */
  alertTitle?: string;
  description: string;
  isPositive: boolean;
  timing: string;
  duration: string;
}

// 실제 PlayPage EVENT_DISPLAY_MAP과 동일한 title/description ($LOC → 서울숲/성수)
const LOC = "서울숲/성수";

export const MOCK_EVENTS: MockEvent[] = [
  // 호재
  { category: "celebrity", name: "연예인 등장", description: `${LOC}에 유명인이 나타났습니다.`, isPositive: true, timing: "즉시", duration: "당일" },
  { category: "holiday", name: "대체 공휴일", description: "정부가 내일을 대체 공휴일로 지정했습니다.", isPositive: true, timing: "다음날", duration: "당일" },
  { category: "subsidy", name: "정부 지원금", description: "소상공인 긴급 지원금이 지급되었습니다.", isPositive: true, timing: "즉시", duration: "당일" },
  { category: "price_down", name: "원가 하락", alertTitle: "버블티 원가 하락", description: "내일부터 버블티 원재료값이 하락할 예정입니다.", isPositive: true, timing: "즉시", duration: "시즌 끝" },
  // 축제
  { category: "festival", name: "축제 개최", description: `${LOC}에서 축제가 열리고 있습니다.`, isPositive: true, timing: "즉시", duration: "당일" },
  // 악재
  { category: "price_up", name: "원가 상승", alertTitle: "버블티 원가 상승", description: "내일부터 버블티 원재료값이 상승할 예정입니다.", isPositive: false, timing: "다음날", duration: "시즌 끝" },
  { category: "disaster_earthquake", name: "지진 발생", description: `${LOC} 인근에서 지진이 발생했습니다.`, isPositive: false, timing: "즉시", duration: "당일" },
  { category: "disaster_flood", name: "홍수 발생", description: `${LOC} 일대가 침수되었습니다.`, isPositive: false, timing: "즉시", duration: "당일" },
  { category: "disaster_typhoon", name: "태풍 접근", description: `${LOC} 지역에 태풍이 접근하고 있습니다.`, isPositive: false, timing: "즉시", duration: "당일" },
  { category: "disaster_fire", name: "화재 발생", description: `${LOC} 인근에서 화재가 발생했습니다.`, isPositive: false, timing: "즉시", duration: "당일" },
  { category: "disease", name: "감염병 발생", description: `${LOC} 일대에 감염병이 확산되고 있습니다.`, isPositive: false, timing: "즉시", duration: "당일" },
  { category: "policy", name: "정책 변경", description: "내일부터 일회용품 사용 규제 등 정부 방침이 변경될 예정입니다.", isPositive: false, timing: "다음날", duration: "시즌 끝" },
];

export const EVENT_ICON_MAP: Record<EventCategory, string> = {
  celebrity: "🎤",
  holiday: "🗓️",
  subsidy: "💰",
  price_down: "📉",
  price_up: "📈",
  disaster_earthquake: "🌍",
  disaster_flood: "🌊",
  disaster_typhoon: "🌪️",
  disaster_fire: "🔥",
  disease: "🦠",
  policy: "📋",
  festival: "🎪",
};

// ─── 축제 ───
export interface MockFestival {
  locationName: string;
  festivalName: string;
}

export const MOCK_FESTIVALS: MockFestival[] = [
  { locationName: "잠실", festivalName: "서울세계불꽃축제" },
  { locationName: "잠실", festivalName: "프로야구 경기" },
  { locationName: "잠실", festivalName: "대형 콘서트" },
  { locationName: "신도림", festivalName: "디큐브시티 공연" },
  { locationName: "신도림", festivalName: "테크노마트 게임·전자 행사" },
  { locationName: "신도림", festivalName: "G밸리 위크 행사" },
  { locationName: "여의도", festivalName: "서울세계불꽃축제" },
  { locationName: "여의도", festivalName: "여의도 벚꽃축제" },
  { locationName: "여의도", festivalName: "한강 재즈 페스티벌" },
  { locationName: "이태원", festivalName: "이태원 지구촌 축제" },
  { locationName: "이태원", festivalName: "이태원 할로윈 거리행사" },
  { locationName: "이태원", festivalName: "이태원 글로벌 빌리지 페스티벌" },
  { locationName: "서울숲/성수", festivalName: "서울숲 봄꽃축제" },
  { locationName: "서울숲/성수", festivalName: "서울숲 재즈페스티벌" },
  { locationName: "서울숲/성수", festivalName: "서울숲 플리마켓" },
  { locationName: "강남", festivalName: "강남페스티벌" },
  { locationName: "강남", festivalName: "코엑스 문화행사" },
  { locationName: "강남", festivalName: "코엑스 국제전시" },
  { locationName: "명동", festivalName: "명동 글로벌 쇼핑 페스티벌" },
  { locationName: "명동", festivalName: "명동 둘레길 걷기 축제" },
  { locationName: "명동", festivalName: "명동 거리 불꽃 행사" },
  { locationName: "홍대", festivalName: "홍대 할로윈 축제" },
  { locationName: "홍대", festivalName: "홍대 거리예술제" },
  { locationName: "홍대", festivalName: "잔다리페스타" },
];

// ─── 홍보 옵션 (실제 PlayPage PROMOTION_OPTION_META와 동일) ───
export const MOCK_PROMOTION_OPTIONS = [
  { id: "INFLUENCER", icon: "📣", name: "인플루언서 홍보", price: 500000, multiplier: 1.2 },
  { id: "SNS", icon: "📱", name: "SNS 홍보", price: 300000, multiplier: 1.15 },
  { id: "LEAFLET", icon: "📰", name: "전단지 배포", price: 100000, multiplier: 1.1 },
  { id: "FRIEND", icon: "🫶", name: "지인 소개", price: 0, multiplier: 1.05 },
];

// ─── 뉴스 ───
// Day 1: 트렌드 1개(메인) + 개막 소식 1개 + 팁 2개 = 총 4개 (AiNewsGenerator 프롬프트 기반)
// 규칙: 제목 10자 이내, 본문 100~150자, 뉴스 보도체(~했다/~밝혔다), 한국어만, 이모지·마크다운 금지
export const MOCK_NEWS_ITEMS = [
  {
    id: 1,
    title: "떡볶이 열풍 확산",
    content: "명동과 홍대 일대에서 매콤한 떡볶이 냄새가 골목마다 퍼지고 있다. 점심시간이면 떡볶이 매장 앞에 긴 줄이 늘어서고, 인근에서는 달콤한 버블티를 손에 든 행인도 부쩍 늘었다. 업계 관계자는 \"소셜미디어 반응이 심상치 않다\"며 \"이 흐름이 당분간 이어질 것\"이라고 전망했다.",
  },
  {
    id: 2,
    title: "팝업 주간 막 올라",
    content: "서울 전역에 팝업스토어 열풍이 불고 있다. 홍대, 강남, 성수, 여의도 등 주요 상권에 야심 찬 점주들이 속속 모여들며 저마다의 개성을 담은 매장을 준비하고 있다. 거리마다 다양한 음식 냄새가 퍼지는 가운데 관계자는 \"올 시즌은 그 어느 때보다 치열한 경쟁이 예상된다\"고 전했다.",
  },
  {
    id: 3,
    title: "발품 파는 게 답이다",
    content: "한 선배 점주가 신규 점주들에게 따뜻한 조언을 건넸다. \"지역마다 분위기가 정말 다르더라고요. 번화한 곳은 손님이 많은 대신 경쟁도 만만치 않고, 조용한 곳은 나름의 매력이 있어요. 매장을 열기 전에 여러 곳을 둘러보면서 자기 스타일에 맞는 동네를 찾아보세요.\"",
  },
  {
    id: 4,
    title: "뉴스 확인은 필수",
    content: "한 관계자가 신규 점주들에게 귀띔했다. \"매일 나오는 뉴스에 요즘 어떤 메뉴가 뜨고 있는지, 어느 동네의 유동인구가 많은지 정보가 담겨 있어요. 시간 날 때 한번 훑어보시면 흐름을 읽는 데 도움이 됩니다. 지난 시즌에 잘된 점주들도 뉴스를 꼼꼼히 챙겨 봤다더라고요.\"",
  },
];

// ─── AI 뉴스 ───
// Day 2: 트렌드 1개(메인) + 메뉴별 매장 현황 1개 + 지역별 매장 현황 1개 + 매출 1위 1개 = 총 4개
// generateTrendNews + generateMenuEntryNews + generateAreaEntryNews + generateTopStoreNews
export const MOCK_AI_NEWS_ITEMS = [
  {
    id: 101,
    title: "버블티 열기 여전",
    content: "이틀째 성수와 홍대 거리에서 버블티를 손에 든 행인이 눈에 띄게 늘었다. 쫀득한 타피오카 펄과 달콤한 밀크티 향이 골목을 채우는 가운데, 인근에서는 떡볶이 매장 앞에도 여전히 줄이 이어지고 있다. 관계자는 \"디저트류 수요가 시즌 초반 집중되는 양상\"이라고 분석했다.",
  },
  {
    id: 102,
    title: "떡볶이 매장 급증세",
    content: "떡볶이를 선택한 매장이 빠르게 늘어나면서 도매시장에서는 원재료 수급에 대한 우려의 목소리가 나오고 있다. 한 유통업계 관계자는 \"주문량이 갑자기 늘었다\"며 가격 인상 가능성을 시사했다. 반면 타코나 핫도그 등 틈새 메뉴를 택한 점주들은 여유로운 표정이었다.",
  },
  {
    id: 103,
    title: "강남 상권 과열 조짐",
    content: "강남 일대에 팝업 매장이 빠르게 늘어나면서 상가 임대 시장이 들썩이고 있다. 인근 중개업소 관계자는 \"문의가 하루에도 수십 건\"이라며 임대료 인상 가능성을 시사했다. 반면 신도림 등 한산한 지역에서는 안정적인 운영 환경을 누리는 점주들도 있는 것으로 전해졌다.",
  },
  {
    id: 104,
    title: "명동 매출 왕 등극",
    content: "명동에 위치한 한 떡볶이 매장이 첫날 매출 왕에 올랐다. 아침부터 매장 앞에 긴 줄이 늘어섰고, 주방은 쉴 틈 없이 분주했다. 점주는 \"유동인구가 많은 명동의 저력을 체감했다\"고 소감을 밝혔다. 인근 점주들 사이에서도 화제가 되고 있다.",
  },
];

// ─── DAY 1 뉴스 랭킹 (CozyNewspaper rankings prop 형태) ───
// Day 1은 아직 매출 데이터가 없으므로 유동인구 순위 + 오픈 현장 사진
export const MOCK_NEWS_RANKINGS_DAY1 = [
  {
    title: "유동인구 순위",
    eyebrow: "Foot Traffic Ranking",
    items: [
      { rank: 1, name: "명동", change: "0.0%", positive: true },
      { rank: 2, name: "강남", change: "0.0%", positive: true },
      { rank: 3, name: "홍대", change: "0.0%", positive: true },
    ],
  },
  {
    title: "오픈 현장",
    eyebrow: "",
    imageSrc: undefined as string | undefined, // bubbleNewsImage를 PrepStep에서 주입
    caption: "개점 첫날, 도심 한복판에 긴 대기 행렬",
    captionDetail: "초기 반응이 기대치를 웃돌면서 첫날 흥행 가능성에 관심이 쏠리고 있다.",
    meta: ["DAY 1 현장 스케치"],
    imageAlt: "개점 첫날, 팝업 숍 앞에 모여든 방문객들",
  },
];

// ─── DAY 2 뉴스 랭킹 ───
// Day 2부터는 전날 데이터가 있으므로 유동인구 순위 + 지역 매출 순위
export const MOCK_NEWS_RANKINGS_DAY2 = [
  {
    title: "유동인구 순위",
    eyebrow: "Foot Traffic Ranking",
    items: [
      { rank: 1, name: "명동", change: "+2.1%", positive: true },
      { rank: 2, name: "강남", change: "+1.5%", positive: true },
      { rank: 3, name: "홍대", change: "-0.3%", positive: false },
    ],
  },
  {
    title: "지역 매출 순위",
    eyebrow: "Revenue Ranking",
    items: [
      { rank: 1, name: "명동", change: "₩4,200,000", positive: true },
      { rank: 2, name: "강남", change: "₩3,850,000", positive: true },
      { rank: 3, name: "홍대", change: "₩2,900,000", positive: true },
    ],
  },
];

// ─── 리포트 ───
export const MOCK_REPORT = {
  revenue: 850000,
  totalCost: 620000,
  netProfit: 230000,
  visitors: 142,
  salesQuantity: 187,
  remainingStock: 63,
  wastedStock: 0,
  reputation: 3.8,
};

export const MOCK_PROFIT_CHART = [
  { day: 1, value: 230000 },
  { day: 2, value: 180000 },
  { day: 3, value: -50000 },
  { day: 4, value: 320000 },
  { day: 5, value: 150000 },
  { day: 6, value: 0, isFuture: true },
  { day: 7, value: 0, isFuture: true },
];

// ─── 최종 랭킹 (Podium + RankingList) ───
export const MOCK_PODIUM_ENTRIES = [
  { rank: 1, nickname: "트렌드세터", storeName: "맛있는 팝업", locationName: "강남", menuName: "햄버거", roi: 42.5, totalRevenue: 5200000, rewardPoints: 30, isBankrupt: false, isMe: false },
  { rank: 2, nickname: "서울왕", storeName: "서울의 맛", locationName: "명동", menuName: "닭강정", roi: 38.2, totalRevenue: 4800000, rewardPoints: 20, isBankrupt: false, isMe: false },
  { rank: 3, nickname: "나", storeName: "내 팝업", locationName: "홍대", menuName: "버블티", roi: 35.0, totalRevenue: 4500000, rewardPoints: 10, isBankrupt: false, isMe: true },
];

export const MOCK_RANKING_LIST_ENTRIES = [
  { rank: 1, nickname: "트렌드세터", storeName: "맛있는 팝업", locationName: "강남", menuName: "햄버거", roi: 42.5, totalRevenue: 5200000, rewardPoints: 30, isBankrupt: false },
  { rank: 2, nickname: "서울왕", storeName: "서울의 맛", locationName: "명동", menuName: "닭강정", roi: 38.2, totalRevenue: 4800000, rewardPoints: 20, isBankrupt: false },
  { rank: 3, nickname: "나", storeName: "내 팝업", locationName: "홍대", menuName: "버블티", roi: 35.0, totalRevenue: 4500000, rewardPoints: 10, isBankrupt: false, isMe: true },
  { rank: 4, nickname: "핫플주인", storeName: "트렌디 버블", locationName: "이태원", menuName: "버블티", roi: 31.8, totalRevenue: 4200000, rewardPoints: 5, isBankrupt: false },
  { rank: 5, nickname: "장사왕", storeName: "맛나라", locationName: "잠실", menuName: "떡볶이", roi: 28.5, totalRevenue: 3900000, rewardPoints: 5, isBankrupt: false },
  { rank: 6, nickname: "빵순이", storeName: "빵이 좋아", locationName: "서울숲/성수", menuName: "빵", roi: 25.0, totalRevenue: 3600000, rewardPoints: 5, isBankrupt: false },
  { rank: 7, nickname: "마라맨", storeName: "마라천국", locationName: "신도림", menuName: "마라꼬치", roi: 22.1, totalRevenue: 3300000, rewardPoints: 5, isBankrupt: false },
  { rank: 8, nickname: "타코킹", storeName: "타코파티", locationName: "여의도", menuName: "타코", roi: 18.7, totalRevenue: 3000000, rewardPoints: 5, isBankrupt: false },
  { rank: 9, nickname: "초보사장", storeName: "도전팝업", locationName: "홍대", menuName: "핫도그", roi: 15.3, totalRevenue: 2700000, rewardPoints: 5, isBankrupt: false },
  { rank: 10, nickname: "신입생", storeName: "첫 팝업", locationName: "잠실", menuName: "젤리", roi: 12.0, totalRevenue: 2400000, rewardPoints: 0, isBankrupt: true },
];

// ─── 긴급 발주 목 데이터 ───
export const MOCK_EMERGENCY_MENU_ITEMS = MOCK_MENUS.map((m) => ({
  menuId: m.id,
  name: m.name,
  ingredientPrice: m.originPrice,
  ingredientDiscountMultiplier: 1,
  emoji: m.emoji,
  recommendedPrice: m.recommendedPrice,
  maxSellingPrice: m.maxSellingPrice,
}));

// ─── 이동 지역 (실제 PlayPage LOCATION_ICON_MAP + getMoveCost와 동일) ───
const LOCATION_ICON_MAP: Record<string, string> = {
  "잠실": "🎡",
  "신도림": "🚉",
  "여의도": "💼",
  "이태원": "🌍",
  "서울숲/성수": "🌳",
  "강남": "💎",
  "명동": "🛍️",
  "홍대": "🎸",
};

export const MOCK_MOVE_REGIONS = MOCK_LOCATIONS.map((loc) => ({
  id: loc.id,
  name: loc.name,
  rent: loc.rent,
  moveCost: Math.round(loc.rent * 7 * 0.1),
  trafficRank: null as number | null,
  icon: LOCATION_ICON_MAP[loc.name] ?? "📍",
}));

// ─── 재고 타임라인 ───
export const MOCK_STOCK_TIMELINE = [
  { day: 1, ordered: 250, sold: 187, wasted: 0, remaining: 63, isOrderDay: true },
  { day: 2, ordered: 0, sold: 55, wasted: 0, remaining: 8, isOrderDay: false },
  { day: 3, ordered: 300, sold: 210, wasted: 8, remaining: 90, isOrderDay: true },
  { day: 4, ordered: 0, sold: 80, wasted: 0, remaining: 10, isOrderDay: false },
  { day: 5, ordered: 200, sold: 170, wasted: 10, remaining: 30, isOrderDay: true },
  { day: 6, ordered: 0, sold: 28, wasted: 0, remaining: 2, isOrderDay: false },
  { day: 7, ordered: 150, sold: 140, wasted: 2, remaining: 10, isOrderDay: true },
];

