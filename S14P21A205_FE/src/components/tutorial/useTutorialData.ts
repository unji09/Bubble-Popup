import { useEffect, useState } from "react";
import { getLocationList, type LocationItem } from "../../api/store";
import { getStoreMenus, type StoreMenuResponse } from "../../api/store";
import { getShopItems, type ShopItemResponse } from "../../api/shop";
import { getDiscountLabel } from "../../utils/dashboardItems";
import {
  MOCK_LOCATIONS,
  MOCK_MENUS,
  MOCK_ITEMS,
  LOCATION_EXTRA,
  MENU_EMOJI,
  type MockLocation,
  type MockMenu,
  type MockItem,
} from "./mockData";

// ─── 변환: API → Tutorial 형태 (실제 대시보드와 동일한 로직) ───

function toMockLocation(api: LocationItem): MockLocation {
  const extra = LOCATION_EXTRA[api.locationName] ?? {
    grade: "B",
    tags: [],
    description: "",
  };
  return {
    id: api.locationId,
    name: api.locationName,
    rent: api.rent,
    interiorCost: api.interiorCost,
    grade: extra.grade,
    tags: extra.tags,
    description: extra.description,
  };
}

function toMockMenu(api: StoreMenuResponse): MockMenu {
  return {
    id: api.menuId,
    name: api.menuName,
    emoji: MENU_EMOJI[api.menuName] ?? "🍽️",
    originPrice: api.ingredientPrice,
    recommendedPrice: api.recommendedPrice,
    maxSellingPrice: api.maxSellingPrice,
  };
}

// 실제 대시보드 getGroupMeta와 동일
function getGroupMeta(category: string) {
  if (category === "RENT") {
    return {
      label: "임대료 할인권",
      icon: "🏠",
      desc: (discountLabel: string) =>
        `일일 임대료 ${discountLabel.replace(" 할인", "")} 감소`,
    };
  }
  return {
    label: "원재료 할인권",
    icon: "🌿",
    desc: (discountLabel: string) =>
      `원재료 구매 비용 ${discountLabel.replace(" 할인", "")} 감소`,
  };
}

function toMockItem(api: ShopItemResponse): MockItem {
  const meta = getGroupMeta(api.category);
  const discountLabel = getDiscountLabel(api.discountRate);
  return {
    id: api.itemId,
    name: api.itemName,
    group: api.category,
    desc: meta.desc(discountLabel),
    discount: discountLabel,
    price: api.point,
  };
}

function buildItemGroups(items: MockItem[]) {
  const groups: { group: string; label: string; icon: string; items: MockItem[] }[] = [];
  const seen = new Map<string, typeof groups[number]>();

  for (const item of items) {
    const meta = getGroupMeta(item.group);
    const existing = seen.get(item.group);
    if (existing) {
      existing.items.push(item);
    } else {
      const entry = { group: item.group, label: meta.label, icon: meta.icon, items: [item] };
      seen.set(item.group, entry);
      groups.push(entry);
    }
  }

  return groups;
}

// ─── Hook ───

export interface TutorialData {
  locations: MockLocation[];
  menus: MockMenu[];
  items: MockItem[];
  itemGroups: ReturnType<typeof buildItemGroups>;
  loading: boolean;
}

export function useTutorialData(): TutorialData {
  const [locations, setLocations] = useState<MockLocation[]>(MOCK_LOCATIONS);
  const [menus, setMenus] = useState<MockMenu[]>(MOCK_MENUS);
  const [items, setItems] = useState<MockItem[]>(MOCK_ITEMS);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function fetchAll() {
      const results = await Promise.allSettled([
        getLocationList(),
        getStoreMenus(),
        getShopItems(),
      ]);

      if (cancelled) return;

      // 지역
      if (results[0].status === "fulfilled") {
        const apiLocations = results[0].value.locations;
        if (apiLocations.length > 0) {
          setLocations(apiLocations.map(toMockLocation));
        }
      }

      // 메뉴
      if (results[1].status === "fulfilled") {
        const apiMenus = results[1].value.menus;
        if (apiMenus.length > 0) {
          setMenus(apiMenus.map(toMockMenu));
        }
      }

      // 아이템
      if (results[2].status === "fulfilled") {
        const apiItems = results[2].value.items;
        if (apiItems.length > 0) {
          setItems(apiItems.map(toMockItem));
        }
      }

      setLoading(false);
    }

    fetchAll();
    return () => { cancelled = true; };
  }, []);

  return {
    locations,
    menus,
    items,
    itemGroups: buildItemGroups(items),
    loading,
  };
}
