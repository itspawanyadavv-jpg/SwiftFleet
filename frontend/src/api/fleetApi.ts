import type {
  DeliveryOrder,
  HealthInfo,
  InspectorLogEntry,
  NearbyRiderMatch,
  RaceConditionReport,
  Rider,
  StoreConfig,
} from '../types/fleet';

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/+$/, '');

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options?.headers || {}),
    },
    ...options,
  });

  if (!res.ok) {
    let code = `HTTP_${res.status}`;
    let message = `Request failed (${res.status})`;
    try {
      const payload = await res.json();
      if (payload?.code) code = payload.code;
      if (payload?.message) message = payload.message;
    } catch {
      // ignore non-json error bodies
    }
    throw new ApiError(res.status, code, message);
  }

  return res.json() as Promise<T>;
}

export const fleetApi = {
  getHealth: () => request<HealthInfo>('/api/health'),
  getConfig: () => request<StoreConfig>('/api/config'),
  syncDrcStore: (url?: string) =>
    request<StoreConfig>('/api/config/sync-drc', {
      method: 'POST',
      body: JSON.stringify({ url }),
    }),
  listRiders: () => request<Rider[]>('/api/riders'),
  nearbyRiders: (lat: number, lng: number, radiusKm?: number) => {
    const qs = new URLSearchParams({ lat: String(lat), lng: String(lng) });
    if (radiusKm) qs.set('radiusKm', String(radiusKm));
    return request<NearbyRiderMatch[]>(`/api/riders/nearby?${qs.toString()}`);
  },
  setRiderOnline: (riderId: string, online: boolean) =>
    request<Rider>(`/api/riders/${encodeURIComponent(riderId)}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ online }),
    }),
  updateRiderLocation: (
    riderId: string,
    lat: number,
    lng: number,
    speedKmh?: number,
    bearingDeg?: number
  ) =>
    request<{ rider: Rider }>(`/api/riders/${encodeURIComponent(riderId)}/location`, {
      method: 'POST',
      body: JSON.stringify({ lat, lng, speedKmh, bearingDeg }),
    }),
  listOrders: () => request<DeliveryOrder[]>('/api/orders'),
  getOrder: (orderId: string) => request<DeliveryOrder>(`/api/orders/${encodeURIComponent(orderId)}`),
  createOrder: (payload: {
    customerName: string;
    customerAddress: string;
    itemsSummary: string;
    dropLat: number;
    dropLng: number;
  }) =>
    request<DeliveryOrder>('/api/orders', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  acceptOrder: (orderId: string, riderId: string) =>
    request<DeliveryOrder>(`/api/orders/${encodeURIComponent(orderId)}/accept`, {
      method: 'POST',
      body: JSON.stringify({ riderId }),
    }),
  pickupOrder: (orderId: string, riderId: string) =>
    request<DeliveryOrder>(`/api/orders/${encodeURIComponent(orderId)}/pickup`, {
      method: 'POST',
      body: JSON.stringify({ riderId }),
    }),
  completeOrder: (orderId: string, riderId: string, otp: string) =>
    request<DeliveryOrder>(`/api/orders/${encodeURIComponent(orderId)}/complete`, {
      method: 'POST',
      body: JSON.stringify({ riderId, otp }),
    }),
  simulateOrderRide: (orderId: string, autoCompleteOtp = false) =>
    request<{ orderId: string; simulating: boolean }>(
      `/api/orders/${encodeURIComponent(orderId)}/simulate`,
      {
        method: 'POST',
        body: JSON.stringify({ autoCompleteOtp }),
      }
    ),
  simulateRaceCondition: (orderId?: string) =>
    request<RaceConditionReport>('/api/demo/race-condition', {
      method: 'POST',
      body: JSON.stringify({ orderId }),
    }),
  quickStartDemo: () =>
    request<DeliveryOrder>('/api/demo/quick-start', {
      method: 'POST',
    }),
  resetDemo: () =>
    request<{ reset: boolean; riders: Rider[] }>('/api/demo/reset', {
      method: 'POST',
    }),
  getInspectorLogs: () => request<InspectorLogEntry[]>('/api/inspector'),
};

