export interface ApiFieldIssue {
  field: string;
  issue: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  correlationId: string | null;
  details: ApiFieldIssue[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Role = 'CUSTOMER' | 'ADMIN' | 'SERVICE';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  roles: Role[];
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface Profile {
  fullName: string;
  phone: string | null;
  addressLine1: string | null;
  city: string | null;
  postcode: string | null;
}

export interface UserResponse {
  id: string;
  email: string;
  roles: Role[];
  profile: Profile | null;
}

export interface Price {
  amount: number;
  currency: string;
}

export interface Category {
  id: string;
  name: string;
  description: string | null;
  parentId: string | null;
}

export interface CategoryRequest {
  name: string;
  description?: string;
  parentId?: string;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string | null;
  categoryId: string;
  categoryName: string;
  active: boolean;
  price: Price;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  sku: string;
  name: string;
  description?: string;
  categoryId: string;
  price: Price;
  active: boolean;
}

export interface ProductQuery {
  q?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  active?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export interface Stock {
  productId: string;
  availableQuantity: number;
  reservedQuantity: number;
  version: number;
}

export interface AdjustmentRequest {
  delta: number;
  reason: string;
  reference?: string;
}

export interface CartItem {
  id: string;
  productId: string;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Cart {
  id: string;
  customerId: string;
  items: CartItem[];
  subtotal: number;
  discountCode: string | null;
  discountAmount: number;
  total: number;
  currency: string;
}

export interface Discount {
  id: string;
  code: string;
  type: 'PERCENT' | 'FIXED';
  value: number;
  minimumOrderTotal: number;
  active: boolean;
  validFrom: string | null;
  validTo: string | null;
}

export interface DiscountRequest {
  code: string;
  type: 'PERCENT' | 'FIXED';
  value: number;
  minimumOrderTotal: number;
  active: boolean;
}

export type SimulatedOutcome = 'SUCCESS' | 'DECLINED' | 'TIMEOUT';

export interface OrderItem {
  productId: string;
  sku: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface OrderHistory {
  fromStatus: string | null;
  toStatus: string;
  reason: string | null;
  changedAt: string;
}

export interface Order {
  id: string;
  customerId: string;
  status: string;
  items: OrderItem[];
  subtotal: number;
  discountAmount: number;
  totalAmount: number;
  currency: string;
  reservationRef: string | null;
  paymentRef: string | null;
  failureReason: string | null;
  createdAt: string;
  statusHistory: OrderHistory[];
}

export interface PaymentAttempt {
  attemptNumber: number;
  outcome: string;
  simulatedDelayMs: number;
  attemptedAt: string;
}

export interface Payment {
  id: string;
  orderId: string;
  customerId: string;
  amount: number;
  currency: string;
  status: string;
  failureReason: string | null;
  createdAt: string;
  attempts: PaymentAttempt[];
}
