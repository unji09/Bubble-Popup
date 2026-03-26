export interface RegularOrderRequest {
  menuId: number;
  quantity: number;
  price: number;
}

export interface RegularOrderResponse {
  menuId?: number;
  menuName?: string;
  costPrice?: number;
  recommendedPrice?: number;
  maxSellingPrice?: number;
  sellingPrice: number;
  stock?: number;
  orderId?: number;
  status?: string;
  message?: string;
}
