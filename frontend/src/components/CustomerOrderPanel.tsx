import { useState } from 'react';
import {
  CheckCircle2,
  Flame,
  KeyRound,
  Link2,
  MapPin,
  PackagePlus,
  Swords,
} from 'lucide-react';
import type {
  DeliveryOrder,
  LatLng,
  OrderStatus,
  RaceConditionReport,
  StoreConfig,
} from '../types/fleet';

interface CustomerOrderPanelProps {
  config: StoreConfig | null;
  orders: DeliveryOrder[];
  activeOrder: DeliveryOrder | null;
  onSelectOrder: (orderId: string) => void;
  customDrop: LatLng;
  onPickPresetDrop: (latLng: LatLng, address: string) => void;
  raceReport: RaceConditionReport | null;
  busyAction: string | null;
  onCreateOrder: (payload: {
    customerName: string;
    customerAddress: string;
    itemsSummary: string;
    dropLat: number;
    dropLng: number;
  }) => void;
  onSimulateRaceCondition: () => void;
  onSyncDrc: (url: string) => Promise<void>;
}

const STEPS: { key: OrderStatus; label: string }[] = [
  { key: 'PENDING_DISPATCH', label: 'Geo Match' },
  { key: 'ASSIGNED', label: 'Lock Claimed' },
  { key: 'OUT_FOR_DELIVERY', label: 'On Road' },
  { key: 'DELIVERED', label: 'OTP Verified' },
];

export function CustomerOrderPanel({
  config,
  orders,
  activeOrder,
  onSelectOrder,
  customDrop,
  onPickPresetDrop,
  raceReport,
  busyAction,
  onCreateOrder,
  onSimulateRaceCondition,
  onSyncDrc,
}: CustomerOrderPanelProps) {
  const [customerName, setCustomerName] = useState('Aarushi Verma');
  const [customerAddress, setCustomerAddress] = useState('Shipra Suncity, Indirapuram');
  const [itemsSummary, setItemsSummary] = useState('Fresh Milk 1L, Brown Bread, Eggs (6)');
  const [drcUrl, setDrcUrl] = useState('https://13-206-156-137.sslip.io');
  const [drcStatus, setDrcStatus] = useState<string | null>(null);

  const hubLat = config?.storeLat ?? 28.646705;
  const hubLng = config?.storeLng ?? 77.444478;

  const presets = [
    { label: 'Shipra Suncity (1.2 km)', address: 'Shipra Suncity, Indirapuram', lat: hubLat + 0.0082, lng: hubLng + 0.0068 },
    { label: 'Vaishali Sec 4 (1.8 km)', address: 'Vaishali Sector 4, Ghaziabad', lat: hubLat - 0.0115, lng: hubLng - 0.0092 },
    { label: 'Ahinsa Khand (0.9 km)', address: 'Ahinsa Khand II, Indirapuram', lat: hubLat + 0.0055, lng: hubLng - 0.0065 },
  ];

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onCreateOrder({
      customerName,
      customerAddress,
      itemsSummary,
      dropLat: customDrop.lat,
      dropLng: customDrop.lng,
    });
  };

  const handleDrcSync = async () => {
    setDrcStatus(null);
    try {
      await onSyncDrc(drcUrl);
      setDrcStatus('Synced deliveryRadiusKm from DRC Store API!');
    } catch (err) {
      setDrcStatus(err instanceof Error ? err.message : 'DRC sync failed');
    }
  };

  const activeStepIdx = activeOrder
    ? STEPS.findIndex((s) => s.key === (activeOrder.status === 'PICKED_UP' ? 'OUT_FOR_DELIVERY' : activeOrder.status))
    : -1;

  return (
    <div className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <PackagePlus size={16} color="#34d399" />
          <span style={{ fontWeight: 700, fontSize: 14 }}>Customer Tracking & Chaos Lab</span>
        </div>

        {orders.length > 0 && (
          <select
            value={activeOrder?.id || ''}
            onChange={(e) => onSelectOrder(e.target.value)}
            className="mono"
            style={{
              padding: '5px 10px',
              borderRadius: 8,
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              fontSize: 12,
            }}
          >
            {orders.map((o) => (
              <option key={o.id} value={o.id}>
                {o.id} · {o.status}
              </option>
            ))}
          </select>
        )}
      </div>

      {/* Active Order Stepper + Customer OTP Display */}
      {activeOrder && (
        <div
          style={{
            background: 'var(--bg-elevated)',
            borderRadius: 12,
            padding: 12,
            border: '1px solid var(--border-subtle)',
            display: 'flex',
            flexDirection: 'column',
            gap: 10,
          }}
        >
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
            {STEPS.map((step, idx) => {
              const reached = idx <= activeStepIdx;
              return (
                <div
                  key={step.key}
                  style={{
                    padding: '6px 8px',
                    borderRadius: 8,
                    textAlign: 'center',
                    fontSize: 11,
                    fontWeight: 600,
                    background: reached ? 'rgba(16, 185, 129, 0.16)' : 'rgba(15, 22, 38, 0.6)',
                    border: `1px solid ${reached ? 'rgba(16, 185, 129, 0.45)' : 'var(--border-subtle)'}`,
                    color: reached ? '#34d399' : 'var(--text-muted)',
                  }}
                >
                  {step.label}
                </div>
              );
            })}
          </div>

          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: 10,
              padding: '10px 12px',
              borderRadius: 10,
              background: 'rgba(9, 15, 29, 0.85)',
              border: '1px solid var(--border-subtle)',
            }}
          >
            <div>
              <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>
                Customer Handshake OTP (Share with Rider)
              </div>
              <div style={{ fontSize: 12, color: '#cbd5e1', marginTop: 2 }}>
                {activeOrder.assignedRiderName
                  ? `Rider: ${activeOrder.assignedRiderName}`
                  : `Pinged ${activeOrder.candidateRiders.length} nearby idle rider(s)`}
              </div>
            </div>

            <div
              className="mono"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '6px 12px',
                borderRadius: 10,
                background:
                  activeOrder.status === 'DELIVERED'
                    ? 'rgba(16, 185, 129, 0.16)'
                    : 'rgba(245, 158, 11, 0.16)',
                border: `1px solid ${
                  activeOrder.status === 'DELIVERED'
                    ? 'rgba(16, 185, 129, 0.45)'
                    : 'rgba(245, 158, 11, 0.45)'
                }`,
                color: activeOrder.status === 'DELIVERED' ? '#34d399' : '#fbbf24',
                fontSize: 18,
                fontWeight: 800,
                letterSpacing: '0.2em',
              }}
            >
              {activeOrder.status === 'DELIVERED' ? <CheckCircle2 size={16} /> : <KeyRound size={15} />}
              {activeOrder.deliveryOtp}
            </div>
          </div>
        </div>
      )}

      {/* Concurrency Race Condition Chaos Tester */}
      <div
        style={{
          padding: 12,
          borderRadius: 12,
          background: 'rgba(245, 158, 11, 0.07)',
          border: '1px solid rgba(245, 158, 11, 0.28)',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: '#fbbf24', display: 'flex', alignItems: 'center', gap: 6 }}>
              <Swords size={15} />
              Concurrency Lock Chaos Test
            </div>
            <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
              Fires 2 threads via CountDownLatch at the exact same millisecond to test Redis SET NX EX
            </div>
          </div>

          <button
            className="btn btn-amber"
            onClick={onSimulateRaceCondition}
            disabled={busyAction !== null}
          >
            <Flame size={14} />
            Simulate Race Condition
          </button>
        </div>

        {raceReport && (
          <div
            style={{
              background: 'rgba(9, 15, 29, 0.85)',
              borderRadius: 10,
              padding: 10,
              border: '1px solid var(--border-subtle)',
              fontSize: 12,
            }}
          >
            <div className="mono" style={{ fontSize: 11, color: '#cbd5e1', marginBottom: 6 }}>
              {raceReport.orderId} · {raceReport.lockMechanism} · {raceReport.elapsedMs} ms
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
              {raceReport.attempts.map((attempt) => (
                <div
                  key={attempt.riderId}
                  style={{
                    padding: '7px 9px',
                    borderRadius: 8,
                    background: attempt.won ? 'rgba(16, 185, 129, 0.14)' : 'rgba(244, 63, 94, 0.12)',
                    border: `1px solid ${attempt.won ? 'rgba(16, 185, 129, 0.4)' : 'rgba(244, 63, 94, 0.35)'}`,
                  }}
                >
                  <div style={{ fontWeight: 700, color: attempt.won ? '#34d399' : '#fda4af' }}>
                    {attempt.riderName} — HTTP {attempt.httpStatus}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--text-secondary)', marginTop: 2 }}>
                    {attempt.message}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Create New Order Form */}
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {presets.map((p) => (
            <button
              key={p.label}
              type="button"
              className="btn"
              style={{ padding: '5px 10px', fontSize: 11 }}
              onClick={() => {
                setCustomerAddress(p.address);
                onPickPresetDrop({ lat: p.lat, lng: p.lng }, p.address);
              }}
            >
              <MapPin size={11} />
              {p.label}
            </button>
          ))}
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
          <input
            value={customerName}
            onChange={(e) => setCustomerName(e.target.value)}
            placeholder="Customer Name"
            style={{
              padding: '8px 10px',
              borderRadius: 9,
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              fontSize: 12,
            }}
          />
          <input
            value={customerAddress}
            onChange={(e) => setCustomerAddress(e.target.value)}
            placeholder="Drop Address"
            style={{
              padding: '8px 10px',
              borderRadius: 9,
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              fontSize: 12,
            }}
          />
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <input
            value={itemsSummary}
            onChange={(e) => setItemsSummary(e.target.value)}
            placeholder="Items Summary"
            style={{
              flex: 1,
              padding: '8px 10px',
              borderRadius: 9,
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              fontSize: 12,
            }}
          />
          <button type="submit" className="btn btn-primary" disabled={busyAction !== null}>
            <PackagePlus size={14} />
            Dispatch Order
          </button>
        </div>
      </form>

      {/* Optional DRC Store Integration */}
      <div
        style={{
          paddingTop: 10,
          borderTop: '1px solid var(--border-subtle)',
          display: 'flex',
          flexDirection: 'column',
          gap: 6,
        }}
      >
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <Link2 size={14} color="#818cf8" />
          <input
            value={drcUrl}
            onChange={(e) => setDrcUrl(e.target.value)}
            placeholder="Optional DRC Store URL"
            className="mono"
            style={{
              flex: 1,
              padding: '6px 9px',
              borderRadius: 8,
              background: 'var(--bg-elevated)',
              border: '1px solid var(--border-subtle)',
              color: 'var(--text-primary)',
              fontSize: 11,
            }}
          />
          <button
            type="button"
            className="btn"
            style={{ padding: '6px 10px', fontSize: 11 }}
            onClick={handleDrcSync}
            disabled={busyAction !== null}
          >
            Sync DRC Radius
          </button>
        </div>
        {drcStatus && (
          <div style={{ fontSize: 11, color: 'var(--text-secondary)' }}>{drcStatus}</div>
        )}
      </div>
    </div>
  );
}
