import client from "./client";

// --- 타입 정의 ---

export interface ActionStatusResponse {
  discountUsed: boolean;
  emergencyUsed: boolean;
  donationUsed: boolean;
  promotionUsed: boolean;
}

export type PromotionType = "INFLUENCER" | "SNS" | "LEAFLET" | "FRIEND";

export interface PromotionPriceItem {
  promotionType: PromotionType;
  promotionPrice: number;
}

export interface PromotionPriceResponse {
  promotion: PromotionPriceItem[];
}

export interface DiscountResponse {
  previousPrice: number;
  newPrice: number;
  priceRange: string;
  priceRangeMultiplier: number;
  message: string;
}

export interface EmergencyOrderResponse {
  orderId: number;
  quantity: number;
  totalCost: number;
  arrivedTime: string;
  message: string;
}

export interface ActionResponse {
  actionType: string;
  cost: number;
  message: string;
}

export interface DonationResponse {
  quantity: number;
  captureRateBonus: number;
  message: string;
}

// --- API 함수 ---

/** 액션 사용 현황 조회 */
export async function getActionStatus() {
  const { data } = await client.get<ActionStatusResponse>("/api/actions/status");
  return data;
}

/** 홍보 가격 조회 */
export async function getPromotionPrice() {
  const { data } = await client.get<PromotionPriceResponse>("/api/actions/promotion/price");
  return data;
}

/** 할인 적용 */
export async function postDiscount(discountValue: number) {
  const { data } = await client.post<DiscountResponse>("/api/actions/discount", { discountValue });
  return data;
}

/** 긴급발주 */
export async function postEmergencyOrder(menuId: number, quantity: number, salePrice: number) {
  const { data } = await client.post<EmergencyOrderResponse>("/api/actions/emergency-order", { menuId, quantity, salePrice });
  return data;
}

/** 홍보하기 */
export async function postPromotion(promotionType: PromotionType) {
  const { data } = await client.post<ActionResponse>("/api/actions/promotion", { promotionType });
  return data;
}

/** 나눔(기부) */
export async function postDonation(quantity: number) {
  const { data } = await client.post<DonationResponse>("/api/actions/donation", { quantity });
  return data;
}
