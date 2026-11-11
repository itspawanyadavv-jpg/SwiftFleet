export type RiderStatus = 'OFFLINE' | 'IDLE' | 'BUSY';

export type OrderStatus =
  | 'PENDING_DISPATCH'
  | 'ASSIGNED'
  | 'PICKED_UP'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED';

export interface LatLng {
  lat: number;
  lng: number;
}

export interface StoreConfig {
  storeName: string;
  storeLat: number;
  storeLng: number;
  dispatchRadiusKm: number;
  deliveryGeofenceMeters: number;
  orderLockTtlSeconds: number;
  otpMaxAttempts: number;
  storeMode: string;
  drcSynced: boolean;
  drcApiUrl: string;
}

export interface Rider {
  id: string;
  name: string;
  vehicleNumber: string;
  phone: string;
  status: RiderStatus;
  lat: number;
  lng: number;
  speedKmh: number;
  bearingDeg: number;
  activeOrderId: string | null;
  updatedAt: string;
}

export interface NearbyRiderMatch {
  riderId: string;
  riderName: string;
  vehicleNumber: string;
  status: RiderStatus;
  distanceKm: number;
  lat: number;
  lng: number;
}

export interface DeliveryOrder {
  id: string;
  customerName: string;
  customerAddress: string;
  itemsSummary: string;
  storeLat: number;
  storeLng: number;
  dropLat: number;
  dropLng: number;
  status: OrderStatus;
  assignedRiderId: string | null;
  assignedRiderName: string | null;
  deliveryOtp: string;
  otpAttempts: number;
  otpLocked: boolean;
  remainingKm: number;
  etaMinutes: number;
  routeSource: string;
  routePolyline: LatLng[];
  candidateRiders: NearbyRiderMatch[];
  createdAt: string;
  assignedAt: string | null;
  pickedUpAt: string | null;
  deliveredAt: string | null;
}

export interface GpsTelemetryEvent {
  riderId: string;
  orderId: string;
  lat: number;
  lng: number;
  speedKmh: number;
  bearingDeg: number;
  remainingKm: number;
  etaMinutes: number;
  orderStatus: OrderStatus;
  insideDeliveryGeofence: boolean;
  distanceToDropMeters: number;
  timestamp: string;
}

export interface DispatchAlertEvent {
  orderId: string;
  targetRiderId: string;
  customerName: string;
  customerAddress: string;
  itemsSummary: string;
  storeLat: number;
  storeLng: number;
  dropLat: number;
  dropLng: number;
  distanceToStoreKm: number;
  timestamp: string;
}

export interface RaceConditionAttempt {
  riderId: string;
  riderName: string;
  httpStatus: number;
  won: boolean;
  message: string;
}

export interface RaceConditionReport {
  orderId: string;
  winnerRiderId: string | null;
  winnerRiderName: string | null;
  lockMechanism: string;
  elapsedMs: number;
  attempts: RaceConditionAttempt[];
}

export interface InspectorLogEntry {
  seq: number;
  category: string;
  command: string;
  detail: string;
  latencyMs: number;
  backendMode: string;
  timestamp: string;
}

export interface HealthInfo {
  status: string;
  service: string;
  storeMode: string;
  redisLive: boolean;
  timestamp: string;
}

