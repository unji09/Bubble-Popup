import { DASHBOARD_SELECTED_ITEMS_STORAGE_KEY } from "../constants";

export type DashboardItemCategory = "INGREDIENT" | "RENT";

export interface DashboardSelectedItem {
  id: number;
  name: string;
  category: DashboardItemCategory;
  point: number;
  discountMultiplier: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object";
}

export function normalizeDiscountMultiplier(value: number) {
  if (!Number.isFinite(value)) {
    return 1;
  }

  if (value > 1) {
    return Math.max(0, 1 - value / 100);
  }

  return Math.max(0, value);
}

function toSelectedItem(value: unknown): DashboardSelectedItem | null {
  if (!isRecord(value)) {
    return null;
  }

  const { id, name, category, point, discountMultiplier, discountRate } = value;
  const normalizedMultiplier = normalizeDiscountMultiplier(
    typeof discountMultiplier === "number"
      ? discountMultiplier
      : typeof discountRate === "number"
        ? discountRate
        : Number.NaN,
  );

  if (
    typeof id !== "number" ||
    typeof name !== "string" ||
    (category !== "INGREDIENT" && category !== "RENT") ||
    typeof point !== "number"
  ) {
    return null;
  }

  return {
    id,
    name,
    category,
    point,
    discountMultiplier: normalizedMultiplier,
  };
}

export function getStoredSelectedDashboardItems() {
  try {
    const stored = localStorage.getItem(DASHBOARD_SELECTED_ITEMS_STORAGE_KEY);

    if (!stored) {
      return [] as DashboardSelectedItem[];
    }

    const parsed = JSON.parse(stored);

    if (!Array.isArray(parsed)) {
      return [] as DashboardSelectedItem[];
    }

    return parsed
      .map((value) => toSelectedItem(value))
      .filter((value): value is DashboardSelectedItem => value !== null);
  } catch {
    return [] as DashboardSelectedItem[];
  }
}

export function getStoredSelectedDashboardItemIds() {
  return getStoredSelectedDashboardItems().map((item) => item.id);
}

export function setStoredSelectedDashboardItems(items: DashboardSelectedItem[]) {
  try {
    localStorage.setItem(DASHBOARD_SELECTED_ITEMS_STORAGE_KEY, JSON.stringify(items));
  } catch {
    // Ignore storage write failures and keep the in-memory state.
  }

  return items;
}

export function clearStoredSelectedDashboardItems() {
  try {
    localStorage.removeItem(DASHBOARD_SELECTED_ITEMS_STORAGE_KEY);
  } catch {
    // Ignore storage write failures and keep the in-memory state.
  }
}

export function hydrateSelectedDashboardItems(
  selectedIds: number[],
  allItems: DashboardSelectedItem[],
) {
  const itemMap = new Map(allItems.map((item) => [item.id, item]));

  return selectedIds
    .map((id) => itemMap.get(id))
    .filter((item): item is DashboardSelectedItem => Boolean(item));
}

export function toggleSelectedDashboardItem(
  selectedIds: number[],
  item: DashboardSelectedItem,
  allItems: DashboardSelectedItem[],
) {
  const itemMap = new Map(allItems.map((entry) => [entry.id, entry]));
  const sanitizedIds = selectedIds.filter((id) => itemMap.has(id));

  if (sanitizedIds.includes(item.id)) {
    return sanitizedIds.filter((id) => id !== item.id);
  }

  const nextIds = sanitizedIds.filter((id) => itemMap.get(id)?.category !== item.category);

  if (nextIds.length >= 2) {
    return sanitizedIds;
  }

  return [...nextIds, item.id];
}

export function getSelectedDiscountMultiplier(
  items: DashboardSelectedItem[],
  category: DashboardItemCategory,
) {
  return items.find((item) => item.category === category)?.discountMultiplier ?? 1;
}

export function getSelectedDiscountPercent(
  items: DashboardSelectedItem[],
  category: DashboardItemCategory,
) {
  return Math.max(
    0,
    Math.round((1 - getSelectedDiscountMultiplier(items, category)) * 100),
  );
}

export function applyDiscount(amount: number, discountMultiplier: number) {
  return Math.round(amount * discountMultiplier);
}

export function getDiscountLabel(discountMultiplier: number) {
  return `${Math.max(0, Math.round((1 - discountMultiplier) * 100))}% 할인`;
}
