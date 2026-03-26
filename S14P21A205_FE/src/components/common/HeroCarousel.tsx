import { useState, useEffect, useCallback, useRef, type ComponentType } from "react";
import { Link } from "react-router-dom";

const INTERVAL = 6000;

type LocationGrade = "S" | "A" | "B";

interface LocationSpot {
  name: string;
  x: string;
  y: string;
  grade: LocationGrade;
  d: number;
  active?: boolean;
}

const avatarColors = [
  "bg-primary",
  "bg-primary/70",
  "bg-primary/50",
  "bg-slate-400",
  "bg-primary/60",
  "bg-slate-500",
  "bg-primary/80",
  "bg-slate-400",
];

interface AnimatedAvatar {
  id: number;
  color: string;
}

/* ─── Slide 1: 스토어 카드 ─── */
const storeCards = [
  { name: "성수 버블티 하우스", emoji: "🧋", rev: "₩234만", cust: "187명", rank: "3위" },
  { name: "홍대 떡볶이 팝업", emoji: "🍽️", rev: "₩312만", cust: "245명", rank: "1위" },
  { name: "강남 마라꼬치 스탠드", emoji: "🍢", rev: "₩198만", cust: "142명", rank: "5위" },
  { name: "명동 닭강정 팝업", emoji: "🍗", rev: "₩267만", cust: "203명", rank: "2위" },
];

function StoreSlide() {
  const [idx, setIdx] = useState(0);
  useEffect(() => { const t = setInterval(() => setIdx((i) => (i+1) % storeCards.length), 3000); return () => clearInterval(t); }, []);
  return (
    <div className="h-full flex items-center justify-center px-4 relative overflow-hidden cursor-pointer" onClick={() => setIdx((i) => (i+1) % storeCards.length)}>
      <div className="absolute top-[45%] left-1/2 -translate-x-1/2 -translate-y-1/2 w-[250px] h-[160px] bg-primary/12 rounded-full blur-[50px]" />
      <div className="relative w-full max-w-[500px] h-full flex items-center justify-center">
        {storeCards.map((card, i) => {
          const off = ((i - idx + storeCards.length) % storeCards.length);
          const p = off===0?{x:0,s:1,z:30,o:1,r:0}:off===1?{x:200,s:.85,z:20,o:.5,r:4}:off===storeCards.length-1?{x:-200,s:.85,z:20,o:.5,r:-4}:{x:off>1?320:-320,s:.7,z:10,o:0,r:6};
          return (
            <div key={i} className="absolute left-1/2 w-[320px] transition-all duration-500 ease-out pointer-events-none"
              style={{ transform:`translateX(calc(-50% + ${p.x}px)) scale(${p.s}) rotate(${p.r}deg)`, zIndex:p.z, opacity:p.o }}>
              <div className="bg-white rounded-2xl shadow-xl border border-slate-100 overflow-hidden">
                <div className="h-[88px] bg-primary relative overflow-hidden">
                  <div className="absolute inset-0 opacity-[0.07]" style={{ backgroundImage:"repeating-linear-gradient(45deg,transparent,transparent 8px,rgba(255,255,255,.3) 8px,rgba(255,255,255,.3) 16px)" }} />
                  <div className="relative h-full flex items-center px-5 gap-4">
                    <div className="w-12 h-12 bg-white/20 backdrop-blur rounded-xl flex items-center justify-center text-3xl">{card.emoji}</div>
                    <div className="text-white flex-1 min-w-0">
                      <p className="text-[10px] font-bold uppercase tracking-widest opacity-60">My Popup Store</p>
                      <p className="text-base font-bold truncate">{card.name}</p>
                    </div>
                  </div>
                </div>
                <div className="grid grid-cols-3 divide-x divide-slate-100">
                  {[["매출",card.rev,false],["고객",card.cust,false],["순위",card.rank,true]].map(([l,v,pr],j) => (
                    <div key={j} className="px-4 py-4 text-center">
                      <p className="text-[10px] text-slate-400 font-bold uppercase">{l as string}</p>
                      <p className={`text-[15px] font-black font-countdown mt-1 ${pr?"text-primary":"text-slate-800"}`}>{v as string}</p>
                    </div>
                  ))}
                </div>
                <div className="px-5 pb-4 pt-1"><div className="flex items-end gap-[3px] h-7">
                  {[30,38,35,48,42,55,50,60,56,68,65,78].map((h,j) => <div key={j} className="flex-1 bg-primary/15 rounded-t-sm" style={{ height:`${h}%` }} />)}
                </div></div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ─── Slide 2: 지역 ─── */
function LocationSlide() {
  const [hov, setHov] = useState<string|null>(null);
  const spots: LocationSpot[] = [
    { name:"홍대", x:"15%", y:"30%", grade:"A", d:0 },{ name:"여의도", x:"13%", y:"55%", grade:"B", d:.1 },
    { name:"명동", x:"35%", y:"26%", grade:"S", d:.15 },{ name:"이태원", x:"38%", y:"50%", grade:"A", d:.2 },
    { name:"서울숲/성수", x:"58%", y:"24%", grade:"S", d:.25, active:true },{ name:"신도림", x:"72%", y:"40%", grade:"B", d:.3 },
    { name:"강남", x:"50%", y:"68%", grade:"S", d:.35 },{ name:"잠실", x:"78%", y:"60%", grade:"A", d:.4 },
  ];
  const gc: Record<LocationGrade, string> = { S:"bg-rose-400", A:"bg-amber-400", B:"bg-slate-400" };
  const lines = [["15%","30%","35%","26%"],["35%","26%","58%","24%"],["58%","24%","72%","40%"],["72%","40%","78%","60%"],["50%","68%","78%","60%"],["38%","50%","50%","68%"],["38%","50%","58%","24%"],["13%","55%","38%","50%"]];
  return (
    <div className="h-full relative px-8 py-8">
      <svg className="absolute inset-0 w-full h-full pointer-events-none">
        <path d="M 0,48% Q 25%,40% 50%,45% Q 75%,50% 100%,42%" fill="none" stroke="#93c5fd" strokeWidth="18" strokeLinecap="round" opacity=".08" />
        <path d="M 0,48% Q 25%,40% 50%,45% Q 75%,50% 100%,42%" fill="none" stroke="#60a5fa" strokeWidth="1.5" strokeDasharray="6 4" opacity=".2">
          <animate attributeName="stroke-dashoffset" from="0" to="-20" dur="2s" repeatCount="indefinite" />
        </path>
      </svg>
      <svg className="absolute inset-0 w-full h-full pointer-events-none" style={{ opacity:.08 }}>
        {lines.map((l,i) => <line key={i} x1={l[0]} y1={l[1]} x2={l[2]} y2={l[3]} stroke="#334155" strokeDasharray="4 3" strokeWidth="1.5" />)}
      </svg>
      {spots.map((s) => {
        const isH = hov===s.name, isA = !!s.active;
        return (
          <div key={s.name} className="absolute flex flex-col items-center cursor-pointer"
            style={{ left:s.x, top:s.y, transform:"translate(-50%,-50%)", animation:`nodeAppear .5s ease-out ${s.d}s both` }}
            onMouseEnter={() => setHov(s.name)} onMouseLeave={() => setHov(null)}>
            {isA && <div className="absolute w-12 h-12 rounded-xl bg-primary/20 animate-ping" style={{ animationDuration:"2s" }} />}
            <div className={`w-9 h-9 rounded-xl ${gc[s.grade]} flex items-center justify-center text-white text-[11px] font-black shadow-lg transition-transform duration-200 ${isH||isA?"scale-125":""}`}>{s.grade}</div>
            <span className={`mt-1 text-[10px] font-bold px-2 py-0.5 rounded-md transition-all ${isA?"bg-slate-800 text-white shadow-lg":isH?"bg-white text-slate-800 shadow-md":"bg-white/80 text-slate-500 shadow-sm"}`}>{s.name}</span>
          </div>
        );
      })}
    </div>
  );
}

/* ─── Slide 3: 랭킹 (가로 바 차트) ─── */
function RankingSlide() {
  const [hov, setHov] = useState<number|null>(null);
  const data = [
    { rank:1, name:"김사장", revenue:"324%", w:"95%", color:"bg-[#F5C542]", medal:"🥇" },
    { rank:2, name:"이대표", revenue:"287%", w:"82%", color:"bg-[#B8C4CE]", medal:"🥈" },
    { rank:3, name:"박대표", revenue:"251%", w:"68%", color:"bg-[#CD8032]", medal:"🥉" },
    { rank:4, name:"최점장", revenue:"218%", w:"58%", color:"bg-slate-200" },
    { rank:5, name:"정사장", revenue:"195%", w:"48%", color:"bg-slate-200" },
  ];

  return (
    <div className="h-full flex flex-col px-8 py-5 justify-center">
      <div className="flex items-center gap-2 mb-4">
        <div className="w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse" />
        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Live ROI Ranking</span>
      </div>
      <div className="flex flex-col gap-2.5">
        {data.map((d, i) => (
          <div key={d.rank} className="flex items-center gap-3 cursor-pointer group"
            style={{ animation: `fadeUp 0.4s ease-out ${i * 0.08}s both` }}
            onMouseEnter={() => setHov(d.rank)} onMouseLeave={() => setHov(null)}>
            {/* Rank */}
            <div className="w-8 text-center shrink-0">
              {d.medal ? <span className="text-lg">{d.medal}</span> : <span className="text-sm font-bold text-slate-300">{d.rank}</span>}
            </div>
            {/* Name */}
            <span className={`w-16 text-[12px] font-bold shrink-0 transition-colors ${hov===d.rank ? "text-slate-900" : "text-slate-500"}`}>{d.name}</span>
            {/* Bar */}
            <div className="flex-1 h-8 bg-slate-50 rounded-lg overflow-hidden relative">
              <div className={`h-full ${d.color} rounded-lg transition-all duration-300 flex items-center justify-end pr-3 ${hov===d.rank ? "brightness-105" : ""}`}
                style={{ width: d.w }}>
                <span className={`text-[11px] font-bold font-countdown ${d.rank <= 3 ? "text-white" : "text-slate-500"} drop-shadow-sm`}>{d.revenue}</span>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ─── Slide 4: 실제 데이터 기반 ─── */
function DataSlide() {
  const items = [
    { icon: "👥", value: "1.2M+", label: "유동인구 데이터", color: "text-indigo-500" },
    { icon: "📊", value: "8개 지역", label: "실시간 상권 분석", color: "text-emerald-500" },
    { icon: "📈", value: "실시간", label: "경제 시뮬레이션", color: "text-amber-500" },
  ];

  return (
    <div className="h-full flex flex-col items-center justify-center px-8 gap-5">
      <p className="text-[11px] font-bold text-slate-400 uppercase tracking-[0.2em]">Powered by Real Data</p>
      <p className="text-xl font-bold text-slate-800 text-center leading-snug">서울시 공공 데이터 기반<br />경영 시뮬레이션</p>

      <div className="flex gap-4 w-full max-w-[400px]">
        {items.map((item, i) => (
          <div key={i} className="flex-1 bg-white/80 backdrop-blur-sm rounded-2xl border border-white p-4 text-center"
            style={{ animation: `fadeUp 0.5s ease-out ${i * 0.1}s both` }}>
            <span className="text-2xl block mb-2">{item.icon}</span>
            <p className={`text-lg font-black font-countdown ${item.color}`}>{item.value}</p>
            <p className="text-[10px] font-bold text-slate-400 mt-1">{item.label}</p>
          </div>
        ))}
      </div>

      <div className="flex gap-2">
        {["공공데이터 API", "빅데이터 분석", "실시간 랭킹"].map((t, i) => (
          <span key={i} className="text-[10px] font-medium text-slate-400 bg-white/60 px-2.5 py-1 rounded-full border border-white/80">{t}</span>
        ))}
      </div>
    </div>
  );
}

/* ─── Config ─── */
interface SlideConfig { badge:string; title:string; desc:string; gradient:string; accentColor:string; component:ComponentType }
const slides: SlideConfig[] = [
  { badge:"Popup Store Simulation", title:"나만의 팝업스토어를 운영하세요", desc:"메뉴 선택부터 가격 전략까지, 경영의 모든 것을 체험해보세요.", gradient:"from-[#F0F7F0] to-[#FEFCE8]", accentColor:"text-emerald-600", component:StoreSlide },
  { badge:"Seoul Hot Place", title:"서울 핫플에서 최고의 입지를 선점하세요", desc:"성수, 홍대, 강남, 명동 — 8개 인기 지역에서 가게를 열어보세요.", gradient:"from-[#EEF2FF] to-[#F0F9FF]", accentColor:"text-indigo-600", component:LocationSlide },
  { badge:"Real-time Ranking", title:"실시간 랭킹으로 치열하게 경쟁하세요", desc:"다른 플레이어와 ROI를 비교하고 1위에 도전하세요.", gradient:"from-[#FFFBEB] to-[#FEF3C7]", accentColor:"text-amber-600", component:RankingSlide },
  { badge:"Real Data Driven", title:"실제 데이터 기반 경영 시뮬레이션", desc:"서울시 공공 데이터와 빅데이터 분석으로 현실감 있는 경영을 체험하세요.", gradient:"from-[#F0FDFA] to-[#ECFDF5]", accentColor:"text-teal-600", component:DataSlide },
];

/* ─── Carousel ─── */
export default function HeroCarousel() {
  const [cur, setCur] = useState(0);
  const [prog, setProg] = useState(0);
  const next = useCallback(() => { setCur((p) => (p+1) % slides.length); setProg(0); }, []);

  useEffect(() => {
    const pT = setInterval(() => setProg((p) => Math.min(p + (100/(INTERVAL/50)), 100)), 50);
    const sT = setInterval(next, INTERVAL);
    return () => { clearInterval(pT); clearInterval(sT); };
  }, [cur, next]);

  const goTo = (i:number) => { setCur(i); setProg(0); };
  const sl = slides[cur]; const C = sl.component;

  return (
    <div className="relative w-full aspect-[16/10] rounded-[32px] shadow-soft overflow-hidden border border-white/40 select-none">
      <style>{`
        @keyframes fadeUp { from { opacity:0; transform:translateY(14px); } to { opacity:1; transform:translateY(0); } }
        @keyframes nodeAppear { from { opacity:0; transform:translate(-50%,-50%) scale(.4); } to { opacity:1; transform:translate(-50%,-50%) scale(1); } }
        @keyframes floatBadge { 0%,100% { transform:translateY(0); } 50% { transform:translateY(-5px); } }
      `}</style>
      <div className={`absolute inset-0 bg-gradient-to-br ${sl.gradient} transition-all duration-500`} />
      <div key={cur} className="absolute inset-0 bottom-[92px]" style={{ animation:"fadeUp .5s ease-out" }}><C /></div>
      <div className="absolute bottom-0 left-0 right-0 bg-white px-5 py-3 md:px-6 z-30">
        <div key={cur} style={{ animation:"fadeUp .4s ease-out" }}>
          <span className={`text-[10px] font-bold uppercase tracking-widest ${sl.accentColor} mb-0.5 block`}>{sl.badge}</span>
          <h2 className="text-[17px] md:text-lg font-bold leading-tight tracking-tight text-slate-900">{sl.title}</h2>
          <p className="text-slate-400 mt-0.5 text-[12px]">{sl.desc}</p>
        </div>
        <div className="flex gap-2 mt-2">
          {slides.map((_,i) => (
            <button key={i} onClick={() => goTo(i)} className="h-1.5 rounded-full transition-all duration-300 overflow-hidden bg-slate-100" style={{ width:i===cur?28:7 }}>
              {i===cur && <div className="h-full rounded-full bg-primary" style={{ width:`${prog}%`, transition:"width 50ms linear" }} />}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

/* ─── CTA ─── */
export function HeroCTA({ seasonNumber, status }: { seasonNumber: number | null; status: "WAITING" | "IN_PROGRESS" | null }) {
  const isActive = status === "IN_PROGRESS" && seasonNumber != null;
  const label = seasonNumber ? `Season ${seasonNumber}` : "Season";

  return (
    <div className="bg-white/90 backdrop-blur-sm rounded-[32px] shadow-xl p-10 lg:p-12 border border-white flex flex-col gap-8 relative overflow-hidden">
      <div className="absolute top-0 right-0 w-32 h-32 bg-primary/10 rounded-bl-full opacity-50 -mr-8 -mt-8" />
      <div className="space-y-4 z-10">
        <span className="text-primary font-bold tracking-wider text-sm uppercase">
          {isActive ? `${label} Open` : "Coming Soon"}
        </span>
        <h2 className="text-4xl md:text-5xl font-bold leading-[1.15]">
          {isActive ? <>지금 바로<br />시작하세요!</> : <>다음 시즌을<br />준비 중이에요</>}
        </h2>
        <p className="text-gray-500 text-lg font-light leading-relaxed">
          {isActive
            ? <>나만의 전략으로 최고의 수익을 달성하고<br />실시간 랭킹에 도전해보세요.</>
            : <>곧 새로운 시즌이 시작됩니다.<br />잠시만 기다려 주세요!</>}
        </p>
      </div>
      <div className="space-y-5 z-10 pt-4">
        <Link to="/login" className={`w-full h-[72px] text-white text-xl font-bold rounded-2xl shadow-lg hover:shadow-xl transition-all transform hover:-translate-y-1 flex items-center justify-center gap-3 group ${
          isActive ? "bg-primary hover:bg-primary-dark" : "bg-slate-400 hover:bg-slate-500"
        }`}>
          {isActive ? "게임 참여하러 가기" : "로그인하고 대기하기"}
          <span className="text-2xl group-hover:translate-x-1 transition-transform">→</span>
        </Link>
        <p className="text-center text-sm text-slate-400 font-medium">
          {isActive ? `${label} 진행 중` : "다음 시즌 대기 중"}
        </p>
      </div>
    </div>
  );
}

/* ─── Animated Avatars ─── */
export function AnimatedParticipants({ count }: { count: number }) {
  const [items, setItems] = useState<AnimatedAvatar[]>(() =>
    avatarColors.slice(0, 5).map((color, index) => ({ id: index, color })),
  );
  const nextItemId = useRef(5);

  useEffect(() => {
    const t = setInterval(() => {
      setItems((prev) => {
        const nextColor = avatarColors[nextItemId.current % avatarColors.length];
        const nextItem = { id: nextItemId.current, color: nextColor };
        nextItemId.current++;
        return [...prev.slice(1), nextItem];
      });
    }, 2200);
    return () => clearInterval(t);
  }, []);

  return (
    <div className="flex items-center justify-center gap-4 opacity-70">
      <div className="relative h-8 w-[140px] overflow-visible">
        <style>{`
          @keyframes avatarEnter {
            from { transform: translateX(12px); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
          }
        `}</style>
        {items.map((item, i) => (
          <div key={item.id}
            className={`absolute w-7 h-7 rounded-full ${item.color} border-2 border-white`}
            style={{
              left: i * 22,
              zIndex: 5 - i,
              transition: "left 0.5s ease-out",
              animation: i === items.length - 1 ? "avatarEnter 0.5s ease-out" : undefined,
            }}
          />
        ))}
      </div>
      <span className="text-sm text-gray-500 font-medium">{count.toLocaleString()}명이 참여 중</span>
    </div>
  );
}

