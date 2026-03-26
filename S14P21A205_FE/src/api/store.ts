import client from "./client";

export interface StoreResponse {
  location: string;
  popupName: string;
  menu: string;
  day: number;
  playableday: number;
  playableFromDay?: number;
}

export interface StoreMenuResponse {
  menuId: number;
  menuName: string;
  ingredientPrice: number;
  discount: number;
  recommendedPrice: number;
  maxSellingPrice: number;
}

export interface StoreMenuListResponse {
  menus: StoreMenuResponse[];
}

export interface LocationItem {
  locationId: number;
  locationName: string;
  rent: number;
  interiorCost: number;
  discount: number;
}

export interface LocationListResponse {
  locations: LocationItem[];
}

export interface UpdateStoreLocationResponse {
  locationId: number;
  balance: number;
}

export async function getStore() {
  const { data } = await client.get<StoreResponse>("/api/stores");
  return data;
}

export async function getStoreMenus() {
  const { data } = await client.get<StoreMenuListResponse>("/api/stores/menus");
  return data;
}

export async function getLocationList() {
  const { data } = await client.get<LocationListResponse>("/api/stores/locations");
  return data;
}

export async function updateStoreLocation(locationId: number) {
  const { data } = await client.patch<UpdateStoreLocationResponse>("/api/stores/location", {
    locationId,
  });
  return data;
}
