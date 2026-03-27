import client, { type AppRequestConfig } from "./client";

export type ShopItemCategory = "INGREDIENT" | "RENT";

export interface ShopItemResponse {
  itemId: number;
  itemName: string;
  category: ShopItemCategory;
  point: number;
  discountRate: number;
}

export interface ShopItemListResponse {
  items: ShopItemResponse[];
}

export interface PurchasedItemResponse {
  itemId: number;
  discountRate: number;
}

export interface PurchasedItemListResponse {
  items: PurchasedItemResponse[];
}

export interface PurchaseItemsRequest {
  itemId: number[];
}

export interface PurchaseItemResponse {
  itemId: number;
  itemName: string;
  category: ShopItemCategory;
  discountRate: number;
  usedPoints: number;
}

export interface PurchaseItemsResponse {
  purchasedItems: PurchaseItemResponse[];
  usedPoints: number;
  remainingPoints: number;
}

interface RawPurchaseItemResponse {
  itemId: number;
  itemName: string;
  category: ShopItemCategory;
  discountRate: number;
  usedPoints: number;
}

interface RawPurchaseItemsResponse {
  purchasedItems: RawPurchaseItemResponse[];
  UsedPoints: number;
  remainingPoints: number;
}

export async function getShopItems() {
  const { data } = await client.get<ShopItemListResponse>("/api/shop/items");
  return data;
}

export async function getPurchasedItems() {
  const { data } = await client.get<PurchasedItemListResponse>("/api/shop/purchased");
  return data;
}

export async function purchaseItems(payload: PurchaseItemsRequest) {
  const config: AppRequestConfig = {
    suppressGlobalErrorHandling: true,
  };
  const { data } = await client.post<RawPurchaseItemsResponse>(
    "/api/shop/purchase",
    payload,
    config,
  );

  return {
    purchasedItems: data.purchasedItems.map((item) => ({
      itemId: item.itemId,
      itemName: item.itemName,
      category: item.category,
      discountRate: item.discountRate,
      usedPoints: item.usedPoints,
    })),
    usedPoints: data.UsedPoints,
    remainingPoints: data.remainingPoints,
  } satisfies PurchaseItemsResponse;
}
